package app.shining.market.api

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/*
 * The Shining server's market API (shining-backend's server/.../market/Routes.kt). These types mirror the server's
 * market/Model.kt; keep the two in step. Market prices are in toman.
 */

/** One meal that can be traded: a food at a self on a day and meal. [key] names its board. */
@Serializable
data class MealInfo(
    /** yyyy-MM-dd. */
    val date: String,
    val mealTypeId: Int,
    val mealName: String,
    val selfId: Int,
    val selfName: String,
    val foodId: Int,
    val foodName: String,
    /** Samad's own price for the reservation, shown as the app shows Samad prices. */
    val reservedPrice: Long = 0,
) {
    val key: String get() = "${date}_${mealTypeId}_${selfId}_$foodId"
}

@Serializable
data class JoinRequest(val samadAuthorization: String)

@Serializable
data class JoinResponse(val token: String, val pushTopic: String, val me: MarketMe)

@Serializable
data class MarketMe(
    val reputation: Reputation = Reputation(),
    /** The card with its middle hidden, or null before one is set. */
    val card: String? = null,
    val pushTopic: String = "",
    /** Joined with a Samad account (student number); false for a guest. */
    val verified: Boolean = true,
)

@Serializable
data class CardRequest(val card: String)

@Serializable
data class Reputation(val sales: Int = 0, val purchases: Int = 0, val problems: Int = 0)

@Serializable
data class OrderView(
    val id: Long,
    val side: String,
    val price: Long,
    val createdAt: String,
    val mine: Boolean = false,
    val reputation: Reputation = Reputation(),
    val reserveId: Int? = null,
    val verified: Boolean = false,
)

/** One listing in the flat market: a meal for sale, by whom (verified or not) and for how much. */
@Serializable
data class ListingView(
    val id: Long,
    val key: String,
    val meal: MealInfo,
    val price: Long,
    val createdAt: String = "",
    val mine: Boolean = false,
    /** The seller joined with their Samad account, so the team can find their student number if a trade goes wrong. */
    val verified: Boolean = false,
    val reputation: Reputation = Reputation(),
    /** The reservation it sells; only on the user's own listings, and only when they picked it from their reservations. */
    val reserveId: Int? = null,
)

/** Every meal on the cafeterias' menus from today on, read by the Shining server, for people without a Samad login. */
@Serializable
data class MenuResponse(val meals: List<MealInfo> = emptyList(), val updatedAt: String? = null)

@Serializable
data class BoardSummary(
    val key: String,
    val meal: MealInfo,
    val bestAsk: Long? = null,
    val bestBid: Long? = null,
    val asks: Int = 0,
    val bids: Int = 0,
    val lastPrice: Long? = null,
    val endsAt: String = "",
)

@Serializable
data class Board(
    val key: String,
    val meal: MealInfo? = null,
    val asks: List<OrderView> = emptyList(),
    val bids: List<OrderView> = emptyList(),
    val lastPrice: Long? = null,
    val priceNow: Long? = null,
    val endsAt: String? = null,
    val open: Boolean = true,
    val activeTradeId: Long? = null,
)

@Serializable
data class PlaceOrderRequest(
    val meal: MealInfo,
    val price: Long,
    val reserveId: Int? = null,
    /** The forget code, typed by the seller (or filled from their reservation). The buyer gets it once the seller confirms. */
    val code: String? = null,
)

@Serializable
data class TakeRequest(val price: Long, val reserveId: Int? = null)

@Serializable
data class TradeView(
    val id: Long,
    val key: String,
    val meal: MealInfo,
    /** buyer or seller. */
    val role: String,
    /** buy_now or sell_now. */
    val origin: String,
    val price: Long,
    /** awaiting_payment, paid, completed, expired, cancelled or disputed. */
    val status: String,
    val createdAt: String,
    val payBy: String,
    val paidAt: String? = null,
    val confirmBy: String? = null,
    val completedAt: String? = null,
    val card: String? = null,
    val note: String? = null,
    val code: String? = null,
    val reserveId: Int? = null,
    val buyerOk: Boolean? = null,
    val problem: String? = null,
    val counterpart: Reputation = Reputation(),
    val counterpartVerified: Boolean = false,
) {
    val isBuyer: Boolean get() = role == "buyer"
    val isActive: Boolean get() = status in listOf(AWAITING_PAYMENT, PAID, DISPUTED)

    companion object {
        const val AWAITING_PAYMENT = "awaiting_payment"
        const val PAID = "paid"
        const val COMPLETED = "completed"
        const val EXPIRED = "expired"
        const val CANCELLED = "cancelled"
        const val DISPUTED = "disputed"
    }
}

@Serializable
data class PaidRequest(val note: String = "")

@Serializable
data class ConfirmRequest(
    /** Blank: the server uses the code saved with the listing. */
    val code: String = "",
)

@Serializable
data class RateRequest(val ok: Boolean)

@Serializable
data class ProblemRequest(val text: String = "")

/** Every refusal from the server: a code and a Persian message to show as is. */
@Serializable
data class ErrorBody(val error: String = "", val message: String = "")

/**
 * Calls return [Response] so refusals (409 with an [ErrorBody]) can be read. [auth] is
 * `Market <token>`; null for calls that work without joining.
 */
interface MarketApi {
    @POST("v1/market/join")
    suspend fun join(@Body body: JoinRequest): Response<JoinResponse>

    @POST("v1/market/join-guest")
    suspend fun joinGuest(@Body body: Map<String, String> = emptyMap()): Response<JoinResponse>

    @GET("v1/market/menu")
    suspend fun menu(@Query("from") from: String): Response<MenuResponse>

    @GET("v1/market/listings")
    suspend fun listings(@Header("Authorization") auth: String?, @Query("from") from: String): Response<List<ListingView>>

    @POST("v1/market/orders/{id}/buy")
    suspend fun buyListing(@Header("Authorization") auth: String, @Path("id") id: Long, @Body body: TakeRequest): Response<TradeView>

    @GET("v1/market/me")
    suspend fun me(@Header("Authorization") auth: String): Response<MarketMe>

    @PUT("v1/market/me/card")
    suspend fun setCard(@Header("Authorization") auth: String, @Body body: CardRequest): Response<MarketMe>

    @DELETE("v1/market/me")
    suspend fun leave(@Header("Authorization") auth: String): Response<Unit>

    @GET("v1/market/boards")
    suspend fun boards(@Query("from") from: String): Response<List<BoardSummary>>

    @GET("v1/market/meals/{key}")
    suspend fun board(@Header("Authorization") auth: String?, @Path("key") key: String): Response<Board>

    @POST("v1/market/meals/{key}/asks")
    suspend fun placeAsk(@Header("Authorization") auth: String, @Path("key") key: String, @Body body: PlaceOrderRequest): Response<Board>

    @POST("v1/market/meals/{key}/bids")
    suspend fun placeBid(@Header("Authorization") auth: String, @Path("key") key: String, @Body body: PlaceOrderRequest): Response<Board>

    @POST("v1/market/meals/{key}/buy-now")
    suspend fun buyNow(@Header("Authorization") auth: String, @Path("key") key: String, @Body body: TakeRequest): Response<TradeView>

    @POST("v1/market/meals/{key}/sell-now")
    suspend fun sellNow(@Header("Authorization") auth: String, @Path("key") key: String, @Body body: TakeRequest): Response<TradeView>

    @DELETE("v1/market/orders/{id}")
    suspend fun cancelOrder(@Header("Authorization") auth: String, @Path("id") id: Long): Response<Board>

    @GET("v1/market/trades")
    suspend fun trades(@Header("Authorization") auth: String): Response<List<TradeView>>

    @GET("v1/market/trades/{id}")
    suspend fun trade(@Header("Authorization") auth: String, @Path("id") id: Long): Response<TradeView>

    @POST("v1/market/trades/{id}/paid")
    suspend fun paid(@Header("Authorization") auth: String, @Path("id") id: Long, @Body body: PaidRequest): Response<TradeView>

    @POST("v1/market/trades/{id}/cancel")
    suspend fun cancelTrade(@Header("Authorization") auth: String, @Path("id") id: Long): Response<TradeView>

    @POST("v1/market/trades/{id}/confirm")
    suspend fun confirm(@Header("Authorization") auth: String, @Path("id") id: Long, @Body body: ConfirmRequest): Response<TradeView>

    @POST("v1/market/trades/{id}/code-received")
    suspend fun codeReceived(@Header("Authorization") auth: String, @Path("id") id: Long): Response<TradeView>

    @POST("v1/market/trades/{id}/rate")
    suspend fun rate(@Header("Authorization") auth: String, @Path("id") id: Long, @Body body: RateRequest): Response<TradeView>

    @POST("v1/market/trades/{id}/problem")
    suspend fun problem(@Header("Authorization") auth: String, @Path("id") id: Long, @Body body: ProblemRequest): Response<TradeView>
}
