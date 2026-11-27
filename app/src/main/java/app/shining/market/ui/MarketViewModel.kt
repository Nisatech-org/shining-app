package app.shining.market.ui

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shining.domain.ui.model.ShiningInformationBoxState
import app.shining.domain.ui.model.ShiningInformationBoxUIModel
import app.shining.market.api.ListingView
import app.shining.market.api.MealInfo
import app.shining.market.api.TradeView
import app.shining.market.domain.MarketMeals
import app.shining.market.domain.MarketRepository
import app.shining.market.domain.parsePrice
import app.shining.order.domain.model.shortSelfName
import app.shining.reserveinfo.domain.usecase.LoadReservationsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MarketScreenState(
    val joined: Boolean = false,
    /** Using the market without a Samad login. */
    val guest: Boolean = false,
    /** Every listing from today on, before the filters. */
    val all: List<ListingView> = emptyList(),
    /** What the filters leave: the list the screen shows. */
    val listings: List<ListingView> = emptyList(),
    /** yyyy-MM-dd to its label, for the day filter; only days that have listings. */
    val days: List<Pair<String, String>> = emptyList(),
    /** Null: every day. */
    val selectedDay: String? = null,
    /** Meal types (id to name) that have listings on the chosen day, for the meal filter. */
    val mealTypes: List<Pair<Int, String>> = emptyList(),
    /** Null: every meal. */
    val selectedMealType: Int? = null,
    /** Cafeterias (id to short name) that have listings for the chosen day and meal. */
    val selves: List<Pair<Int, String>> = emptyList(),
    /** Null: every cafeteria. */
    val selectedSelf: Int? = null,
    val activeTrades: List<TradeView> = emptyList(),
    val pastTrades: List<TradeView> = emptyList(),
    val loading: Boolean = true,
    /** Shown above everything: the market is off, or the server couldn't be reached. */
    val notice: String? = null,
    val joining: Boolean = false,
    val joinError: String? = null,
    /** The listing whose buy or cancel is running. */
    val busyId: Long? = null,
    val message: ShiningInformationBoxUIModel? = null,
)

/**
 * The sell form. The forget code is what matters most, so it's the first field: a guest types
 * it, and someone logged in with Samad can instead pick one of their reservations, which fills
 * in the meal and the code.
 */
data class SellFormState(
    val visible: Boolean = false,
    /** The user's upcoming reservations not yet on the market (none for a guest). */
    val reservations: List<MarketMeals.Sellable> = emptyList(),
    val pickedReserveId: Int? = null,
    /** The reservation whose code is being read from Samad. */
    val loadingCodeFor: Int? = null,
    /** Every meal on the menus from today on, from the server. */
    val menu: List<MealInfo> = emptyList(),
    val menuLoading: Boolean = false,
    val menuError: String? = null,
    val day: String? = null,
    val mealTypeId: Int? = null,
    val selfId: Int? = null,
    val meal: MealInfo? = null,
    val code: String = "",
    val price: String = "",
    /** The card on the server with its middle hidden; null until the seller gives one. */
    val savedCard: String? = null,
    val changingCard: Boolean = false,
    val card: String = "",
    val busy: Boolean = false,
    val error: String? = null,
) {
    val menuDays: List<String> get() = menu.map { it.date }.distinct().sorted()
    val mealsOfDay: List<MealInfo> get() = menu.filter { it.date == day }
    val mealTypes: List<Pair<Int, String>> get() = mealsOfDay.map { it.mealTypeId to it.mealName }.distinctBy { it.first }
    val selves: List<Pair<Int, String>>
        get() = mealsOfDay.filter { it.mealTypeId == mealTypeId }.map { it.selfId to shortSelfName(it.selfName) }
            .distinctBy { it.first }.sortedBy { it.second }
    val foods: List<MealInfo> get() = mealsOfDay.filter { it.mealTypeId == mealTypeId && it.selfId == selfId }
    val needsCard: Boolean get() = savedCard == null || changingCard
}

/**
 * The Market tab: one flat list of every listing, whatever the food, soonest meal first and
 * then cheapest, with a day filter and a search box on top. Selling is a form on the same tab.
 * Offers (bids) still exist on the server, but this screen leaves them out while trades are few.
 */
class MarketViewModel(
    private val repository: MarketRepository,
    private val loadReservations: LoadReservationsUseCase,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val state = mutableStateOf(MarketScreenState())
    val sell = mutableStateOf(SellFormState())

    /** Set to a trade's id when one starts; the screen opens it and clears this. */
    private val _openTrade = MutableStateFlow<Long?>(null)
    val openTrade = _openTrade.asStateFlow()

    private var job: Job? = null
    private var notice: String? = null

    fun onLaunch() {
        refresh()
        MarketSellRequest.take()?.let { openSell(reserveId = it) }
    }

    fun onTradeOpened() {
        _openTrade.value = null
    }

    /** Listings and trades. A guest who hasn't got a market account yet gets one first. */
    fun refresh() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            state.value = state.value.copy(guest = repository.isGuest(), joined = repository.isJoined())
            if (repository.isGuest() && !repository.isJoined()) joinGuest()
            repository.listings().fold(
                onSuccess = { list ->
                    notice = null
                    state.value = state.value.copy(all = list)
                },
                onFailure = { notice = it.message },
            )
            loadTrades()
            rebuild()
        }
    }

    fun onDaySelected(date: String?) {
        state.value = state.value.copy(selectedDay = date)
        rebuild()
    }

    fun onMealTypeSelected(id: Int?) {
        state.value = state.value.copy(selectedMealType = id)
        rebuild()
    }

    fun onSelfSelected(id: Int?) {
        state.value = state.value.copy(selectedSelf = id)
        rebuild()
    }

    /** Joining with Samad: the one time a Samad token goes to the server (see MarketInfoSheet). */
    fun onJoin() {
        if (state.value.joining) return
        viewModelScope.launch {
            state.value = state.value.copy(joining = true, joinError = null)
            val result = if (repository.isGuest()) repository.joinGuest() else repository.join()
            result.fold(
                onSuccess = {
                    state.value = state.value.copy(joining = false, joined = true)
                    refresh()
                },
                onFailure = { state.value = state.value.copy(joining = false, joinError = it.message) },
            )
        }
    }

    private suspend fun joinGuest() {
        repository.joinGuest().onFailure { notice = it.message }
    }

    fun onLeave() {
        viewModelScope.launch {
            repository.leave().fold(
                onSuccess = {
                    state.value = state.value.copy(joined = false, activeTrades = emptyList(), pastTrades = emptyList())
                    refresh()
                },
                onFailure = { show(false, it.message ?: "یه مشکلی پیش اومد.") },
            )
        }
    }

    /** Buy Now on one listing, at the price the user saw. */
    fun onBuy(listing: ListingView) = action(listing.id) {
        if (!repository.isJoined()) return@action Result.failure(IllegalStateException("اول وارد بازار شو."))
        repository.buyListing(listing.id, listing.price).map { _openTrade.value = it.id }
    }

    fun onCancelListing(listing: ListingView) = action(listing.id) {
        repository.cancelOrder(listing.id, listing.reserveId).map { show(true, "آگهیت برداشته شد.") }
    }

    // Selling

    /** Opens the sell form; [reserveId] picks that reservation right away (from Home's "بفروش"). */
    fun openSell(reserveId: Int? = null) {
        val forSale = repository.forSaleReservations()
        val reservations = if (repository.isGuest()) {
            emptyList()
        } else {
            loadReservations.saved()?.info?.dayInfoList.orEmpty()
                .let { MarketMeals.sellable(it, now()) }
                .filter { it.reserveId !in forSale }
        }
        sell.value = SellFormState(visible = true, reservations = reservations, menuLoading = true)
        reservations.firstOrNull { it.reserveId == reserveId }?.let(::onPickReservation)
        viewModelScope.launch {
            repository.menu().fold(
                onSuccess = { menu ->
                    val upcoming = menu.filter { now() < MarketMeals.endsAt(it) }
                    sell.value = sell.value.copy(
                        menu = upcoming,
                        menuLoading = false,
                        menuError = if (upcoming.isEmpty()) "منوی سلف‌ها هنوز به سرور شاینینگ نرسیده." else null,
                    )
                    if (sell.value.day == null) onSellDay(upcoming.map { it.date }.minOrNull())
                },
                onFailure = { sell.value = sell.value.copy(menuLoading = false, menuError = it.message) },
            )
        }
        if (repository.isJoined()) {
            viewModelScope.launch {
                repository.me().onSuccess { sell.value = sell.value.copy(savedCard = it.card) }
            }
        }
    }

    fun closeSell() {
        sell.value = sell.value.copy(visible = false)
    }

    /** A reservation fills in its meal and, from Samad, its forget code. */
    fun onPickReservation(item: MarketMeals.Sellable) {
        val meal = item.meal
        sell.value = sell.value.copy(
            pickedReserveId = item.reserveId,
            meal = meal,
            day = meal.date,
            mealTypeId = meal.mealTypeId,
            selfId = meal.selfId,
            loadingCodeFor = item.reserveId,
            code = "",
            error = null,
        )
        repository.remember(meal)
        viewModelScope.launch {
            val code = repository.forgetCodeOf(item.reserveId).getOrNull()
            if (sell.value.pickedReserveId != item.reserveId) return@launch
            sell.value = sell.value.copy(
                loadingCodeFor = null,
                code = code?.let(::normalizeCode).orEmpty(),
                error = if (code == null) "کد رو از سماد نگرفتیم. خودت بنویسش." else null,
            )
        }
    }

    fun onSellDay(date: String?) {
        val form = sell.value.copy(day = date, pickedReserveId = null)
        val mealType = form.mealTypes.firstOrNull { it.first == form.mealTypeId }?.first ?: form.mealTypes.firstOrNull()?.first
        sell.value = form.copy(mealTypeId = mealType).let { it.copy(selfId = null, meal = null) }
    }

    fun onSellMealType(id: Int) {
        sell.value = sell.value.copy(mealTypeId = id, selfId = null, meal = null, pickedReserveId = null)
    }

    fun onSellSelf(id: Int) {
        val form = sell.value.copy(selfId = id, pickedReserveId = null)
        sell.value = form.copy(meal = form.foods.singleOrNull())
    }

    fun onSellMeal(meal: MealInfo) {
        sell.value = sell.value.copy(meal = meal, pickedReserveId = null)
    }

    fun onSellCode(text: String) {
        sell.value = sell.value.copy(code = normalizeCode(text).take(12), error = null)
    }

    fun onSellPrice(text: String) {
        sell.value = sell.value.copy(price = text, error = null)
    }

    fun onSellCard(text: String) {
        sell.value = sell.value.copy(card = text, error = null)
    }

    fun onChangeCard() {
        sell.value = sell.value.copy(changingCard = true)
    }

    fun onSubmitSell() {
        val form = sell.value
        if (form.busy) return
        val code = form.code
        val meal = form.meal
        val price = parsePrice(form.price)
        val problem = when {
            code.length < 3 -> "کد فراموشی غذا رو بنویس."
            meal == null -> "بگو چه غذایی رو می‌فروشی."
            price == null -> "قیمت رو به تومن بنویس."
            form.needsCard && form.card.filter { it.isDigit() || it in '۰'..'۹' }.length < 16 ->
                "شماره‌ی کارتت رو بنویس تا خریدار بدونه پول رو کجا بزنه."
            else -> null
        }
        if (problem != null) {
            sell.value = form.copy(error = problem)
            return
        }
        viewModelScope.launch {
            sell.value = sell.value.copy(busy = true, error = null)
            val result = runCatching {
                if (!repository.isJoined()) {
                    (if (repository.isGuest()) repository.joinGuest() else repository.join()).getOrThrow()
                }
                if (form.needsCard) repository.setCard(form.card).getOrThrow()
                repository.placeAsk(meal!!, price!!, code, form.pickedReserveId).getOrThrow()
            }
            result.fold(
                onSuccess = {
                    sell.value = SellFormState()
                    show(true, "آگهیت ثبت شد. وقتی کسی بخره، خبرت می‌کنیم.")
                    job?.cancel()
                    refresh()
                },
                onFailure = { sell.value = sell.value.copy(busy = false, error = it.message ?: "یه مشکلی پیش اومد.") },
            )
        }
    }

    // Plumbing

    private suspend fun loadTrades() {
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
    }

    private fun rebuild() {
        val time = now()
        val current = state.value
        val open = current.all.filter { time < MarketMeals.endsAt(it.meal) }
        val days = open.map { it.meal.date }.distinct().sorted()
        val day = current.selectedDay?.takeIf { it in days }
        // Each filter offers only what the ones before it leave, so no choice ends in an empty list.
        val ofDay = open.filter { day == null || it.meal.date == day }
        val mealTypes = ofDay.map { it.meal.mealTypeId to it.meal.mealName }.distinctBy { it.first }.sortedBy { mealOrder(it.first) }
        val mealType = current.selectedMealType?.takeIf { id -> mealTypes.any { it.first == id } }
        val ofMeal = ofDay.filter { mealType == null || it.meal.mealTypeId == mealType }
        val selves = ofMeal.map { it.meal.selfId to shortSelfName(it.meal.selfName) }.distinctBy { it.first }.sortedBy { it.second }
        val self = current.selectedSelf?.takeIf { id -> selves.any { it.first == id } }
        val shown = ofMeal
            .filter { self == null || it.meal.selfId == self }
            .sortedWith(compareBy<ListingView>({ MarketMeals.endsAt(it.meal) }, { it.price }, { it.createdAt }))
        state.value = current.copy(
            listings = shown,
            days = days.map { it to MarketMeals.dayLabel(it, time) },
            selectedDay = day,
            mealTypes = mealTypes,
            selectedMealType = mealType,
            selves = selves,
            selectedSelf = self,
            loading = false,
            notice = notice,
            joined = repository.isJoined(),
            guest = repository.isGuest(),
        )
    }

    private fun action(id: Long, block: suspend () -> Result<Unit>) {
        if (state.value.busyId != null) return
        viewModelScope.launch {
            state.value = state.value.copy(busyId = id)
            block().onFailure { show(false, it.message ?: "یه مشکلی پیش اومد.") }
            state.value = state.value.copy(busyId = null)
            if (_openTrade.value == null) {
                job?.cancel()
                refresh()
            }
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

    /** Breakfast, lunch, dinner: the order of the day (Samad's ids: 3 breakfast, 2 sahari, 1 lunch, 4 iftar, 5 dinner). */
    private fun mealOrder(id: Int): Int = when (id) {
        2 -> 0
        3 -> 1
        1 -> 2
        4 -> 3
        5 -> 4
        else -> 5
    }

    private fun normalizeCode(text: String): String = text.map { char ->
        when (char) {
            in '۰'..'۹' -> '0' + (char - '۰')
            in '٠'..'٩' -> '0' + (char - '٠')
            else -> char
        }
    }.filter { it.isLetterOrDigit() || it == '-' }.joinToString("")
}

/**
 * Home's "بفروش" asks the Market tab to open the sell form with that reservation picked. Held
 * here because the tab's view model may not exist yet.
 */
object MarketSellRequest {
    @Volatile
    private var reserveId: Int? = null

    fun set(id: Int) {
        reserveId = id
    }

    fun take(): Int? = reserveId.also { reserveId = null }
}
