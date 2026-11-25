package app.shining.market.ui

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shining.domain.ui.model.ShiningInformationBoxState
import app.shining.domain.ui.model.ShiningInformationBoxUIModel
import app.shining.market.api.Board
import app.shining.market.api.MealInfo
import app.shining.market.api.OrderView
import app.shining.market.domain.MarketMeals
import app.shining.market.domain.MarketRepository
import app.shining.market.domain.parsePrice
import app.shining.reserveinfo.domain.usecase.LoadReservationsUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class MealBoardState(
    val key: String,
    val meal: MealInfo? = null,
    val board: Board? = null,
    /** The user's own reservation of this meal: they're a seller here. */
    val reservation: MarketMeals.Sellable? = null,
    val joined: Boolean = false,
    /** The card buyers pay to, with its middle hidden; null until the seller gives one. */
    val card: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val message: ShiningInformationBoxUIModel? = null,
    /** The action running now, so its button shows a spinner. */
    val busy: String? = null,
    val joining: Boolean = false,
    val joinError: String? = null,
) {
    val isSeller: Boolean get() = reservation != null
    val myAsk: OrderView? get() = board?.asks?.firstOrNull { it.mine && (reservation == null || it.reserveId == reservation.reserveId) }
    val myBid: OrderView? get() = board?.bids?.firstOrNull { it.mine }
    val cheapestListing: OrderView? get() = board?.asks?.firstOrNull { !it.mine }
    val highestOffer: OrderView? get() = board?.bids?.firstOrNull { !it.mine }
    val open: Boolean get() = board?.open ?: meal?.let { System.currentTimeMillis() < MarketMeals.endsAt(it) } ?: true
}

/**
 * One meal's board: sellers list, buyers make offers, and a trade starts when
 * someone takes the price on the other side (Buy Now / Sell Now). Refreshes every few seconds
 * while open.
 */
class MealBoardViewModel(
    private val key: String,
    private val repository: MarketRepository,
    private val loadReservations: LoadReservationsUseCase,
) : ViewModel() {

    val state = mutableStateOf(MealBoardState(key = key))
    val price = mutableStateOf("")
    val card = mutableStateOf("")

    /** Set to a trade's id when one starts; the screen opens it and clears this. */
    val openTrade = mutableStateOf<Long?>(null)

    private var prefilled = false
    private var started = false

    fun onLaunch() {
        // Survives rotation; the loop below must start only once.
        if (started) return
        started = true
        val sold = repository.soldReservations()
        val reservation = loadReservations.saved()?.info?.dayInfoList.orEmpty()
            .let { MarketMeals.sellable(it) }
            .firstOrNull { it.meal.key == key && it.reserveId !in sold }
        val meal = repository.meal(key) ?: reservation?.meal
        state.value = state.value.copy(meal = meal, reservation = reservation, joined = repository.isJoined())
        viewModelScope.launch { loadMe() }
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(5_000)
            }
        }
    }

    suspend fun refresh() {
        repository.board(key).fold(
            onSuccess = { board ->
                state.value = state.value.copy(
                    board = board,
                    meal = state.value.meal ?: board.meal,
                    loading = false,
                    error = null,
                    joined = repository.isJoined(),
                )
                prefill(board)
            },
            onFailure = {
                state.value = state.value.copy(loading = false, error = it.message, joined = repository.isJoined())
            },
        )
    }

    private suspend fun loadMe() {
        if (!repository.isJoined()) return
        repository.me().onSuccess { state.value = state.value.copy(card = it.card) }
    }

    /** A seller's price starts at what the meal trades at right now. */
    private fun prefill(board: Board) {
        if (prefilled || price.value.isNotEmpty()) return
        prefilled = true
        val suggestion = if (state.value.isSeller) board.priceNow ?: board.asks.firstOrNull()?.price else null
        suggestion?.let { price.value = it.toString() }
    }

    fun onJoin() = viewModelScope.launch {
        state.value = state.value.copy(joining = true, joinError = null)
        repository.join().fold(
            onSuccess = {
                state.value = state.value.copy(joining = false, joined = true, card = it.card)
                refresh()
            },
            onFailure = { state.value = state.value.copy(joining = false, joinError = it.message) },
        )
    }

    fun onBuyNow() {
        val listing = state.value.cheapestListing ?: return
        action("buy") {
            repository.buyNow(key, listing.price).map { openTrade.value = it.id }
        }
    }

    fun onSellNow() {
        val offer = state.value.highestOffer ?: return
        val reservation = state.value.reservation ?: return
        action("sell") {
            ensureCard().mapCatching {
                repository.sellNow(key, offer.price, reservation.reserveId).getOrThrow()
            }.map { openTrade.value = it.id }
        }
    }

    fun onPlaceAsk() {
        val meal = state.value.meal ?: return
        val reservation = state.value.reservation ?: return
        val amount = parsePrice(price.value) ?: return show(false, "قیمت رو به تومن بنویس.")
        action("ask") {
            ensureCard().mapCatching { repository.placeAsk(meal, amount, reservation.reserveId).getOrThrow() }
                .map { board ->
                    state.value = state.value.copy(board = board)
                    show(true, "آگهیت ثبت شد. وقتی کسی بخره، خبرت می‌کنیم.")
                }
        }
    }

    fun onPlaceBid() {
        val meal = state.value.meal ?: return
        val amount = parsePrice(price.value) ?: return show(false, "قیمت رو به تومن بنویس.")
        action("bid") {
            repository.placeBid(meal, amount).map { board ->
                state.value = state.value.copy(board = board)
                show(true, "پیشنهادت ثبت شد. اگه کسی به این قیمت یا ارزون‌تر بفروشه، خبرت می‌کنیم.")
            }
        }
    }

    fun onCancelOrder(id: Long) = action("cancel") {
        repository.cancelOrder(id).map { board -> state.value = state.value.copy(board = board) }
    }

    /** Saves the typed card first if the seller has none on the server yet. */
    private suspend fun ensureCard(): Result<Unit> {
        if (state.value.card != null) return Result.success(Unit)
        if (card.value.isBlank()) return Result.failure(IllegalStateException("شماره‌ی کارتت رو بنویس تا خریدار بدونه پول رو کجا بزنه."))
        return repository.setCard(card.value).map { state.value = state.value.copy(card = it.card) }
    }

    private fun action(name: String, block: suspend () -> Result<Unit>) {
        if (state.value.busy != null) return
        viewModelScope.launch {
            state.value = state.value.copy(busy = name)
            block().onFailure { show(false, it.message ?: "یه مشکلی پیش اومد.") }
            state.value = state.value.copy(busy = null)
            if (openTrade.value == null) refresh()
        }
    }

    private fun show(success: Boolean, message: String) {
        val box = ShiningInformationBoxUIModel(
            if (success) ShiningInformationBoxState.SUCCESS else ShiningInformationBoxState.FAILED,
            message,
        )
        state.value = state.value.copy(message = box)
        viewModelScope.launch {
            delay(4_000)
            if (state.value.message == box) state.value = state.value.copy(message = null)
        }
    }
}
