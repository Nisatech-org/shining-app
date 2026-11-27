package app.shining.order.ui.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import app.shining.account.domain.repository.AccountStore
import app.shining.domain.model.LoadableData
import app.shining.domain.model.NavigationEvent
import app.shining.domain.ui.model.ShiningInformationBoxState
import app.shining.domain.ui.model.ShiningInformationBoxUIModel
import app.shining.domain.ui.viewmodel.ShiningViewModel
import app.shining.domain.util.getNextSaturday
import app.shining.push.ReserveDeepLink
import app.shining.push.ReserveTarget
import app.shining.domain.util.getPreviousSaturday
import app.shining.order.domain.model.WeekReservableProgram
import app.shining.order.domain.model.mergeByDay
import app.shining.order.domain.model.reserveErrorMessage
import app.shining.order.domain.model.shortSelfName
import app.shining.order.domain.usecase.GetAvailableSelfsUseCase
import app.shining.order.domain.usecase.GetReservableProgramUseCase
import app.shining.order.domain.usecase.ReserveFoodUseCase
import app.shining.order.ui.model.OrderFoodDetailUIModel
import app.shining.order.ui.model.OrderFoodKey
import app.shining.order.ui.model.OrderScreenUIModel
import app.shining.order.ui.model.key
import app.shining.order.ui.model.toReservableScreenUIModel
import app.shining.order.ui.model.withFoodSelected
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch


class OrderScreenViewModel(
    private val getReservableProgramUseCase: GetReservableProgramUseCase,
    private val getAvailableSelfsUseCase: GetAvailableSelfsUseCase,
    private val reserveFoodUseCase: ReserveFoodUseCase,
    private val accountStore: AccountStore,
) : ShiningViewModel() {

    // reserve program
    val orderScreenUIModel =
        mutableStateOf<LoadableData<OrderScreenUIModel>>(LoadableData.NotLoaded)

    // foods with a reserve/cancel request in flight
    val pendingFoods = mutableStateOf<PersistentSet<OrderFoodKey>>(persistentSetOf())
    private var programRefreshJob: Job? = null

    // information box
    val showMessage = mutableStateOf(false)
    val informationBoxUIModel = mutableStateOf<ShiningInformationBoxUIModel?>(null)
    private var hideMessageJob: Job? = null

    // navigation
    val navBack: NavigationEvent = NavigationEvent()


    /** Set when Reserve was opened from a menu notification: the day to show and the foods to tag as new. */
    val reserveTarget = mutableStateOf<ReserveTarget?>(null)

    fun onLaunch() {
        ReserveDeepLink.take()?.let { reserveTarget.value = it }
        onRefresh()
    }

    fun onOrderFoodClick(detail: OrderFoodDetailUIModel) {
        val key = detail.key
        if (key in pendingFoods.value) return
        pendingFoods.value = pendingFoods.value.add(key)
        val selected = !detail.isSelected

        viewModelScope.launch {
            reserveFoodUseCase(
                foodTypeId = detail.foodTypeId,
                mealTypeId = detail.mealTypeId,
                programId = detail.programId,
                selected = selected,
            ).fold(
                onSuccess = { message ->
                    // Show the result right away; the refresh below corrects anything else the
                    // server changed (e.g. another food in the same meal).
                    (orderScreenUIModel.value as? LoadableData.Loaded)?.let {
                        orderScreenUIModel.value =
                            LoadableData.Loaded(it.data.withFoodSelected(key, selected))
                    }
                    informationBoxUIModel.value = ShiningInformationBoxUIModel(
                        message = forActiveAccount(message),
                        state = ShiningInformationBoxState.SUCCESS
                    )
                    refreshProgramSilently()
                },
                onFailure = {
                    val message = it.message ?: "در رزرو غذا خطایی پیش آمده"
                    informationBoxUIModel.value = ShiningInformationBoxUIModel(
                        // A "full" سلف مرکزی food is Samad's error; elsewhere it's real.
                        message = forActiveAccount(if (selected) reserveErrorMessage(detail.selfId, message) else message),
                        state = ShiningInformationBoxState.FAILED
                    )
                }
            )
            pendingFoods.value = pendingFoods.value.remove(key)
            showMessage()
        }
    }

    /**
     * With more than one saved account, says whose reservation a message is about, since
     * reserving for the wrong person costs real credit.
     */
    private fun forActiveAccount(message: String): String {
        val state = accountStore.state.value
        val active = state.active ?: return message
        return if (state.accounts.size > 1) "برای ${active.label}: $message" else message
    }

    /**
     * Fetches the program again without showing the loading state. A newer call cancels an older
     * one, so a slow response can never overwrite a fresher one. If it replaces a visible load
     * (e.g. the first one), it stays visible so the screen can't get stuck loading.
     */
    private fun refreshProgramSilently() {
        programRefreshJob?.cancel()
        programRefreshJob = viewModelScope.launch {
            delay(PROGRAM_REFRESH_DEBOUNCE_MS)
            getReservableScreenUIModel(
                offlineFirst = orderScreenUIModel.value is LoadableData.Loaded,
                onSuccess = {
                    orderScreenUIModel.value = LoadableData.Loaded(it)
                },
            )
        }
    }

    fun onBackClick() {
        navBack.navigate()
    }

    fun onRefresh() {
        programRefreshJob?.cancel()
        programRefreshJob = viewModelScope.launch {
            getReservableScreenUIModel(
                offlineFirst = false,
                onSuccess = {
                    orderScreenUIModel.value = LoadableData.Loaded(it)
                },
            )
        }
    }

    private fun showMessage() {
        showMessage.value = true
        hideMessageJob?.cancel()
        hideMessageJob = viewModelScope.launch {
            delay(MESSAGE_DURATION_MS)
            showMessage.value = false
        }
    }

    /**
     * Loads the previous and next week of every self the user may eat at and merges them, so each
     * day lists all its foods whatever self serves them.
     */
    private suspend fun getReservableScreenUIModel(
        offlineFirst: Boolean = false,
        onSuccess: (OrderScreenUIModel) -> Unit = {},
    ) {
        if (!offlineFirst) {
            orderScreenUIModel.value = LoadableData.Loading
        }
        val selfs = getAvailableSelfsUseCase().map { it.selfs }
        currentCoroutineContext().ensureActive()
        val selfList = selfs.getOrNull()
        if (selfList.isNullOrEmpty()) {
            // A silent refresh keeps what is on screen; a visible load shows the retry card
            // instead of spinning forever.
            if (!offlineFirst) {
                orderScreenUIModel.value = LoadableData.Failed(
                    selfs.exceptionOrNull()?.message ?: "هیچ سلفی برای شما تعریف نشده."
                )
            }
            return
        }

        val weeks = listOf(getPreviousSaturday(), getNextSaturday())
        val programs: List<Result<WeekReservableProgram>> = coroutineScope {
            selfList.flatMap { self ->
                weeks.map { week ->
                    async {
                        getReservableProgramUseCase(selfId = self.id, weekStartDate = week)
                    }
                }
            }.awaitAll()
        }

        // The repository turns every exception into a failed Result, cancellation included, so
        // check here that this load hasn't been replaced by a newer one before touching state.
        currentCoroutineContext().ensureActive()

        val loaded = programs.mapNotNull { it.getOrNull() }
        if (loaded.isEmpty()) {
            if (!offlineFirst) {
                orderScreenUIModel.value = LoadableData.Failed(
                    programs.firstNotNullOfOrNull { it.exceptionOrNull()?.message }
                        ?: "هیچ برنامه غذایی‌ای تعریف نشده است."
                )
            }
        } else {
            // With a single self there is nothing to tell apart, so its foods get no tag.
            val selfNames = if (selfList.size > 1) {
                selfList.associate { it.id to shortSelfName(it.name) }
            } else {
                emptyMap()
            }
            onSuccess(loaded.mergeByDay().toReservableScreenUIModel(selfNames))
        }
    }

    private companion object {
        const val PROGRAM_REFRESH_DEBOUNCE_MS = 300L
        const val MESSAGE_DURATION_MS = 3000L
    }
}
