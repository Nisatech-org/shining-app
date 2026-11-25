package app.shining.market.ui

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shining.domain.util.getNextSaturday
import app.shining.domain.util.getPreviousSaturday
import app.shining.market.api.BoardSummary
import app.shining.market.api.MealInfo
import app.shining.market.api.TradeView
import app.shining.market.domain.MarketMeals
import app.shining.market.domain.MarketRepository
import app.shining.order.domain.repository.OrderFoodRepository
import app.shining.order.domain.usecase.GetAvailableSelfsUseCase
import app.shining.reserveinfo.domain.usecase.LoadReservationsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** One meal in the Market tab: what's on its board right now. */
data class MarketRow(
    val meal: MealInfo,
    val bestAsk: Long? = null,
    val bestBid: Long? = null,
    val lastPrice: Long? = null,
    val asks: Int = 0,
    val bids: Int = 0,
    /** The user reserved this meal, so they can sell it. */
    val reservedByMe: Boolean = false,
) {
    val hasActivity: Boolean get() = asks + bids > 0
}

data class MarketScreenState(
    val joined: Boolean = false,
    /** yyyy-MM-dd to its label ("امروز", "فردا", "دوشنبه"). */
    val days: List<Pair<String, String>> = emptyList(),
    val selectedDay: String? = null,
    val rows: List<MarketRow> = emptyList(),
    val activeTrades: List<TradeView> = emptyList(),
    val pastTrades: List<TradeView> = emptyList(),
    val loading: Boolean = true,
    /** Shown above everything: the market is off, or the server couldn't be reached. */
    val notice: String? = null,
    val joining: Boolean = false,
    val joinError: String? = null,
)

/**
 * The Market tab: every meal on the user's selves' menus (from Samad, the same as Reserve), with
 * its board from the server. Buying starts here; selling starts from Home or from a meal the user
 * reserved.
 */
class MarketViewModel(
    private val repository: MarketRepository,
    private val loadReservations: LoadReservationsUseCase,
    private val getSelfs: GetAvailableSelfsUseCase,
    private val orders: OrderFoodRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val state = mutableStateOf(MarketScreenState())

    private var menu: List<MealInfo> = emptyList()
    private var boards: List<BoardSummary> = emptyList()
    private var menuLoaded = false
    private var job: Job? = null

    fun onLaunch() = if (menuLoaded) refreshBoards() else refresh()

    /** Menus, boards and trades. */
    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            state.value = state.value.copy(loading = true, joined = repository.isJoined())
            val boardsJob = async { loadBoardsAndTrades() }
            menu = loadMenu()
            menuLoaded = true
            boardsJob.await()
            rebuild(loading = false)
        }
    }

    /** Only what changes minute to minute: boards and trades. */
    fun refreshBoards() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            loadBoardsAndTrades()
            rebuild(loading = false)
        }
    }

    fun onDaySelected(date: String) {
        state.value = state.value.copy(selectedDay = date)
        rebuild(loading = state.value.loading)
    }

    fun onJoin() {
        if (state.value.joining) return
        viewModelScope.launch {
            state.value = state.value.copy(joining = true, joinError = null)
            repository.join().fold(
                onSuccess = {
                    state.value = state.value.copy(joining = false, joined = true)
                    refreshBoards()
                },
                onFailure = { state.value = state.value.copy(joining = false, joinError = it.message) },
            )
        }
    }

    private var notice: String? = null

    /** Leaves the market: the server deletes the account, card and orders; the phone forgets its login. */
    fun onLeave() {
        viewModelScope.launch {
            repository.leave().fold(
                onSuccess = {
                    state.value = state.value.copy(joined = false, activeTrades = emptyList(), pastTrades = emptyList())
                    refreshBoards()
                },
                onFailure = { state.value = state.value.copy(notice = it.message) },
            )
        }
    }

    private suspend fun loadBoardsAndTrades() {
        repository.boards().fold(
            onSuccess = {
                boards = it
                notice = null
            },
            onFailure = { notice = it.message },
        )
        if (repository.isJoined()) {
            repository.trades().onSuccess { trades ->
                state.value = state.value.copy(
                    activeTrades = trades.filter { it.isActive },
                    pastTrades = trades.filterNot { it.isActive }.take(10),
                )
            }
        } else {
            state.value = state.value.copy(activeTrades = emptyList(), pastTrades = emptyList())
        }
        state.value = state.value.copy(joined = repository.isJoined())
    }

    /** This week's and next week's menus of every self the user can reserve at, from today on. */
    private suspend fun loadMenu(): List<MealInfo> = coroutineScope {
        val selves = getSelfs().getOrNull()?.selfs.orEmpty()
        val weeks = listOf(getPreviousSaturday(), getNextSaturday())
        selves.flatMap { self ->
            weeks.map { week ->
                async {
                    orders.getReserveProgram(selfId = self.id, weekStartDate = week).getOrNull()
                        ?.selfWeekProgramDtoList?.flatten().orEmpty()
                        .mapNotNull { MarketMeals.fromMenu(it, self.name) }
                }
            }
        }.awaitAll().flatten()
    }

    private fun rebuild(loading: Boolean) {
        val time = now()
        val today = MarketMeals.today(time)
        val summaries = boards.associateBy { it.key }
        val sold = repository.soldReservations()
        val reserved = loadReservations.saved()?.info?.dayInfoList.orEmpty()
            .let { MarketMeals.sellable(it, time) }.filter { it.reserveId !in sold }.map { it.meal.key }.toSet()
        val meals = (menu + boards.map { it.meal })
            .filter { it.date >= today && time < MarketMeals.endsAt(it) }
            .distinctBy { it.key }
        meals.forEach(repository::remember)
        val days = meals.map { it.date }.distinct().sorted()
        val selected = state.value.selectedDay?.takeIf { it in days } ?: days.firstOrNull()
        val rows = meals.filter { it.date == selected }
            .map { meal ->
                val summary = summaries[meal.key]
                MarketRow(
                    meal = meal,
                    bestAsk = summary?.bestAsk,
                    bestBid = summary?.bestBid,
                    lastPrice = summary?.lastPrice,
                    asks = summary?.asks ?: 0,
                    bids = summary?.bids ?: 0,
                    reservedByMe = meal.key in reserved,
                )
            }
            .sortedWith(
                compareBy<MarketRow>({ MarketMeals.endsAt(it.meal) }, { !it.hasActivity }, { it.meal.selfName }, { it.meal.foodName }),
            )
        state.value = state.value.copy(
            days = days.map { it to MarketMeals.dayLabel(it, time) },
            selectedDay = selected,
            rows = rows,
            loading = loading,
            notice = notice,
            joined = repository.isJoined(),
        )
    }
}
