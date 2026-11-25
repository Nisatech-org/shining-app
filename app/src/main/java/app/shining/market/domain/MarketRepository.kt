package app.shining.market.domain

import app.shining.debug.DebugLog
import app.shining.market.api.Board
import app.shining.market.api.BoardSummary
import app.shining.market.api.CardRequest
import app.shining.market.api.ConfirmRequest
import app.shining.market.api.ErrorBody
import app.shining.market.api.JoinRequest
import app.shining.market.api.MarketApi
import app.shining.market.api.MarketMe
import app.shining.market.api.MealInfo
import app.shining.market.api.PaidRequest
import app.shining.market.api.PlaceOrderRequest
import app.shining.market.api.ProblemRequest
import app.shining.market.api.RateRequest
import app.shining.market.api.TakeRequest
import app.shining.market.api.TradeView
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** A refusal or failure to show: [code] is the server's error code ("network" and "off" are the app's own). */
class MarketError(val code: String, override val message: String) : Exception(message)

/**
 * Everything the market screens do, for the active account. The server knows nothing about the
 * user until they join; then each call carries the account's market token, never a Samad token.
 */
class MarketRepository(
    private val api: MarketApi,
    private val sessions: MarketSessions,
    private val history: MarketHistory,
    private val activeUsername: () -> String?,
    /**
     * The active account's Samad Authorization value, made fresh first (by a Samad call that
     * renews it if needed). Sent to the server once, when joining.
     */
    private val samadAuthorization: suspend () -> String?,
    /** The forget code of a reservation, from Samad (or the copy on the phone). */
    private val forgetCode: suspend (reserveId: Int) -> Result<String>,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /** Meals the screens have shown, so a board nobody has traded on yet still has its details. */
    private val meals = ConcurrentHashMap<String, MealInfo>()

    fun remember(meal: MealInfo) {
        meals[meal.key] = meal
    }

    fun meal(key: String): MealInfo? = meals[key]

    fun isJoined(): Boolean = session() != null

    /** Reservations this account already sold or is selling right now, from the trades on the phone. */
    fun soldReservations(): Set<Int> {
        val username = activeUsername() ?: return emptySet()
        return history.all(username)
            .filter { !it.isBuyer && (it.isActive || it.status == TradeView.COMPLETED) }
            .mapNotNull { it.reserveId }
            .toSet()
    }

    private fun session(): MarketSession? = activeUsername()?.let(sessions::get)

    private fun auth(): String = session()?.let { "Market ${it.token}" }
        ?: throw MarketError(NOT_JOINED, "اول وارد بازار شو.")

    // Account

    suspend fun join(): Result<MarketMe> {
        val username = activeUsername() ?: return Result.failure(MarketError("no_account", "اول وارد حساب سمادت شو."))
        val authorization = try {
            samadAuthorization()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return Result.failure(MarketError("network", "به سماد وصل نشدیم. اینترنتت اوکیه؟"))
        return call { api.join(JoinRequest(authorization)) }.map { reply ->
            sessions.save(username, MarketSession(reply.token, reply.pushTopic))
            DebugLog.i(TAG, "joined the market")
            reply.me
        }
    }

    suspend fun me(): Result<MarketMe> = authed { api.me(it) }

    suspend fun setCard(card: String): Result<MarketMe> = authed { api.setCard(it, CardRequest(card)) }

    /** Leaves the market: the server deletes the account and card; the phone forgets its login. */
    suspend fun leave(): Result<Unit> = authed { api.leave(it) }.onSuccess {
        activeUsername()?.let(sessions::remove)
    }

    // Boards

    suspend fun boards(from: String = MarketMeals.today()): Result<List<BoardSummary>> =
        call { api.boards(from) }.onSuccess { list -> list.forEach { remember(it.meal) } }

    suspend fun board(key: String): Result<Board> =
        call { api.board(session()?.let { "Market ${it.token}" }, key) }.onSuccess { board -> board.meal?.let(::remember) }

    suspend fun placeAsk(meal: MealInfo, price: Long, reserveId: Int): Result<Board> =
        authed { api.placeAsk(it, meal.key, PlaceOrderRequest(meal, price, reserveId)) }

    suspend fun placeBid(meal: MealInfo, price: Long): Result<Board> =
        authed { api.placeBid(it, meal.key, PlaceOrderRequest(meal, price)) }

    suspend fun cancelOrder(id: Long): Result<Board> = authed { api.cancelOrder(it, id) }

    suspend fun buyNow(key: String, price: Long): Result<TradeView> =
        authed { api.buyNow(it, key, TakeRequest(price)) }.map(::kept)

    suspend fun sellNow(key: String, price: Long, reserveId: Int): Result<TradeView> =
        authed { api.sellNow(it, key, TakeRequest(price, reserveId)) }.map(::kept)

    // Trades

    /** The account's trades: from the server, saved on the phone; the saved copy when offline. */
    suspend fun trades(): Result<List<TradeView>> {
        val username = activeUsername() ?: return Result.success(emptyList())
        if (session() == null) return Result.success(history.all(username))
        return authed { api.trades(it) }
            .onSuccess { history.save(username, it) }
            .map { history.all(username) }
            .recoverCatching { error ->
                val saved = history.all(username)
                if (saved.isEmpty()) throw error else saved
            }
    }

    /** One trade. A bought code is saved on the phone first, then the server is told to forget it. */
    suspend fun trade(id: Long): Result<TradeView> = authed { api.trade(it, id) }.map { collect(it) }
        .recoverCatching { error ->
            activeUsername()?.let { history.get(it, id) } ?: throw error
        }

    suspend fun paid(id: Long, note: String): Result<TradeView> = authed { api.paid(it, id, PaidRequest(note)) }.map(::kept)

    suspend fun cancelTrade(id: Long): Result<TradeView> = authed { api.cancelTrade(it, id) }.map(::kept)

    /** The seller got the money: reads the forget code from Samad and sends it for the buyer. */
    suspend fun confirm(trade: TradeView): Result<TradeView> {
        val reserveId = trade.reserveId ?: return Result.failure(MarketError("no_reservation", "رزرو این غذا پیدا نشد."))
        val code = forgetCode(reserveId).getOrElse {
            return Result.failure(MarketError("no_code", "کد فراموشی رو از سماد نگرفتیم. دوباره امتحان کن."))
        }
        return authed { api.confirm(it, trade.id, ConfirmRequest(code)) }.map(::kept)
    }

    suspend fun rate(id: Long, ok: Boolean): Result<TradeView> = authed { api.rate(it, id, RateRequest(ok)) }.map(::kept)

    suspend fun problem(id: Long, text: String): Result<TradeView> =
        authed { api.problem(it, id, ProblemRequest(text)) }.map(::kept)

    private suspend fun collect(trade: TradeView): TradeView {
        keep(trade)
        if (trade.isBuyer && trade.code != null) {
            // Saved above; now the server can delete it. If this fails it's retried next time.
            authed { api.codeReceived(it, trade.id) }
                .onSuccess { DebugLog.i(TAG, "trade ${trade.id}: code saved on the phone, server told") }
        }
        return activeUsername()?.let { history.get(it, trade.id) } ?: trade
    }

    private fun keep(trade: TradeView) {
        activeUsername()?.let { history.save(it, listOf(trade)) }
    }

    /**
     * Saves [trade] and returns the phone's copy, which still holds a bought code after the
     * server has forgotten it (the server's reply to "it worked" no longer carries the code).
     */
    private fun kept(trade: TradeView): TradeView {
        keep(trade)
        return activeUsername()?.let { history.get(it, trade.id) } ?: trade
    }

    // Plumbing

    private suspend fun <T> authed(block: suspend (String) -> Response<T>): Result<T> {
        val auth = try {
            auth()
        } catch (e: MarketError) {
            return Result.failure(e)
        }
        return call { block(auth) }
    }

    private suspend fun <T> call(block: suspend () -> Response<T>): Result<T> {
        val response = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            DebugLog.w(TAG, "request failed: ${e.javaClass.simpleName}: ${e.message}")
            return Result.failure(MarketError("network", "به سرور شاینینگ وصل نشدیم. اینترنتت اوکیه؟"))
        } catch (e: Exception) {
            DebugLog.w(TAG, "request failed: ${e.javaClass.simpleName}: ${e.message}")
            return Result.failure(MarketError("unreadable", "جواب سرور رو نفهمیدیم. شاینینگ رو به‌روز کن."))
        }
        if (response.isSuccessful) {
            @Suppress("UNCHECKED_CAST")
            return Result.success(response.body() ?: Unit as T)
        }
        val error = runCatching {
            json.decodeFromString(ErrorBody.serializer(), response.errorBody()?.string().orEmpty())
        }.getOrNull()?.takeIf { it.error.isNotBlank() }
        DebugLog.w(TAG, "refused: HTTP ${response.code()} ${error?.error}")
        if (error == null) {
            // nginx or the server without the market: a 404 with no market reply.
            return Result.failure(
                if (response.code() == 404) {
                    MarketError(OFF, "بازار هنوز روشن نشده. به‌زودی!")
                } else {
                    MarketError("server", "سرور شاینینگ الان جواب نمی‌ده (${response.code()}). چند دقیقه‌ی دیگه دوباره امتحان کن.")
                },
            )
        }
        if (error.error == NOT_JOINED) {
            // The server doesn't know this login any more (the user left on another phone, say).
            activeUsername()?.let(sessions::remove)
        }
        return Result.failure(MarketError(error.error, error.message))
    }

    companion object {
        private const val TAG = "market"
        const val NOT_JOINED = "not_joined"
        const val OFF = "off"
    }
}
