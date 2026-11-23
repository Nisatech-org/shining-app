package app.shining.reserveinfo.ui.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import app.shining.domain.model.LoadableData
import app.shining.domain.model.NavigationEvent
import app.shining.domain.ui.model.ShiningInformationBoxState
import app.shining.domain.ui.model.ShiningInformationBoxUIModel
import app.shining.domain.ui.viewmodel.ShiningViewModel
import app.shining.reserveinfo.domain.model.ReservationInfo
import app.shining.reserveinfo.domain.usecase.GetForgetCodeMapCacheUseCase
import app.shining.reserveinfo.domain.usecase.GetForgetCodeUseCase
import app.shining.reserveinfo.domain.usecase.LoadReservationsUseCase
import app.shining.reserveinfo.ui.model.ReservationInfoScreenUIModel
import app.shining.reserveinfo.ui.model.savedNoticeText
import app.shining.reserveinfo.ui.model.toReservationInfoScreenUIModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ReservationInfoViewModel(
    private val loadReservations: LoadReservationsUseCase,
    private val getForgetCodeUseCase: GetForgetCodeUseCase,
    private val getForgetCodeMapCache: GetForgetCodeMapCacheUseCase,
    /** Current time in milliseconds, replaceable in tests. */
    private val now: () -> Long = System::currentTimeMillis,
) : ShiningViewModel() {

    val reservationInfoUIModel =
        mutableStateOf<LoadableData<ReservationInfoScreenUIModel?>>(LoadableData.NotLoaded)
    private var reservationInfo: ReservationInfo? = null

    /** Set while the list is the copy saved on the device because Samad couldn't be reached. */
    val savedNotice = mutableStateOf<String?>(null)

    val informationBoxUIModel = mutableStateOf<ShiningInformationBoxUIModel?>(null)
    val showMessage = mutableStateOf(false)

    val navBack = NavigationEvent()
    val navSettings = NavigationEvent()

    private var loadJob: Job? = null

    /** Shows the saved copy right away, if there is one, then loads the latest from Samad. */
    fun onLaunch() = load(showSavedFirst = true)

    fun onSettingsClick() {
        navSettings.navigate()
    }

    fun onBackClick() {
        navBack.navigate()
    }

    /** Shows the spinner while loading, so the user sees the button did something. */
    fun onRefresh() = load(showSavedFirst = false)

    private fun load(showSavedFirst: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val saved = loadReservations.saved()
            if (showSavedFirst && saved != null) {
                show(saved.info)
            } else {
                reservationInfoUIModel.value = LoadableData.Loading
            }

            loadReservations().fold(
                onSuccess = {
                    savedNotice.value = null
                    show(it)
                },
                onFailure = {
                    if (saved != null) {
                        savedNotice.value = savedNoticeText(saved.savedAt, now())
                        show(saved.info)
                    } else {
                        savedNotice.value = null
                        reservationInfoUIModel.value = LoadableData.Failed(
                            "در دریافت برنامه غذایی مشکلی پیش آمده."
                        )
                    }
                },
            )
        }
    }

    private suspend fun show(info: ReservationInfo) {
        reservationInfo = info
        reservationInfoUIModel.value = LoadableData.Loaded(
            info.toReservationInfoScreenUIModel(forgetCodeMap = getForgetCodeMapCache())
        )
    }

    fun onGetForgetCode(
        reserveId: Int,
        onFinish: () -> Unit,
    ) {

        viewModelScope.launch {
            getForgetCodeUseCase(reserveId = reserveId).fold(
                onSuccess = { _ ->
                    val forgetCodeMap = getForgetCodeMapCache()
                    reservationInfoUIModel.value = LoadableData.Loaded(
                        reservationInfo?.toReservationInfoScreenUIModel(forgetCodeMap)
                    )
                },
                onFailure = {
                    informationBoxUIModel.value = ShiningInformationBoxUIModel(
                        state = ShiningInformationBoxState.FAILED,
                        message = "در دریافت کد فراموشی غذای مورد نظر خطایی پیش اومده",
                    )

                    showMessage.value = true
                    viewModelScope.launch {
                        delay(3000)
                        showMessage.value = false
                    }
                }
            )
            onFinish()
        }

    }

}