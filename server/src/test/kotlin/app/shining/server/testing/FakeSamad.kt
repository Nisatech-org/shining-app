package app.shining.server.testing

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A small stand-in for Samad on localhost, speaking the same API the app and server use:
 * `POST /oauth/token` (password and refresh grants), `POST /rest/captcha`,
 * `GET /rest/reservations/selfs` and `GET /rest/reservations/programs/v2`. Tests publish days per self, expire tokens, or make it fail.
 */
class FakeSamad(
    private val username: String = "98123456",
    private val password: String = "test-password-1",
) : AutoCloseable {

    val selfs = mutableListOf(1 to "مرکزی - سلف دانشجویان آقا", 20 to "کاله پردیس مرکزی(بیرون بر)", 24 to "کلانا")

    /** Published lunch days per self id. */
    val days = mutableMapOf<Int, Set<LocalDate>>()

    /** When set, every REST call answers this HTTP status. */
    @Volatile var failWith: Int? = null

    /** Like the real Samad since November 2026: password logins need a captcha, refreshes don't. */
    @Volatile var requireCaptcha: Boolean = false

    /** What every captcha picture says. Each captcha is good for one login attempt. */
    val captchaAnswer = "12345"

    val logins = AtomicInteger()
    val refreshes = AtomicInteger()
    val restCalls = AtomicInteger()
    val captchasIssued = AtomicInteger()

    @Volatile private var accessToken = ""
    @Volatile private var refreshToken = ""
    private val issued = AtomicInteger()

    /** Captchas given out and not used yet: nonce → validation. */
    private val openCaptchas = ConcurrentHashMap<String, String>()

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/oauth/token") { handleToken(it) }
        createContext("/rest/captcha") { handleCaptcha(it) }
        createContext("/rest/") { handleRest(it) }
        start()
    }

    /** `POST /rest/captcha {"nonce": …}`: a picture and the validation that goes back with its answer. Needs no login. */
    private fun handleCaptcha(exchange: HttpExchange) {
        val body = exchange.requestBody.readBytes().decodeToString()
        val nonce = Regex("\"nonce\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            ?: return exchange.reply(400, """{"type":"ERROR","message":"nonce is required"}""")
        val validation = "validation-${captchasIssued.incrementAndGet()}"
        openCaptchas[nonce] = validation
        exchange.reply(200, ok("""{"image":"iVBORw0KGgoAAAANSUhEUg==","validation":"$validation"}"""))
    }

    private fun captchaSolved(query: Map<String, String>): Boolean {
        val nonce = query["nonce"] ?: return false
        val validation = openCaptchas.remove(nonce) ?: return false
        return validation == query["validation"] && query["captchaText"] == captchaAnswer
    }

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}/"

    /** The last access token issued: tests check it never shows up in logs. */
    val lastAccessToken: String get() = accessToken

    /** Makes the current access token stop working, like a session that ran out. */
    fun expireAccessToken() {
        accessToken = "expired-${issued.get()}"
    }

    private fun handleToken(exchange: HttpExchange) {
        val query = parseQuery(exchange.requestURI.rawQuery)
        if (exchange.requestHeaders.getFirst("Authorization")?.startsWith("Basic ") != true) return exchange.reply(401, "{}")
        when (query["grant_type"]) {
            "password" -> {
                logins.incrementAndGet()
                if (requireCaptcha && !captchaSolved(query)) {
                    return exchange.reply(400, """{"type":"ERROR","code":"9999","message":"Captcha params is invalid","messageFa":"پارامترهای کپچا نامعتبر است","payload":""}""")
                }
                if (query["username"] != username || query["password"] != password) return exchange.reply(400, """{"error":"invalid_grant"}""")
            }

            "refresh_token" -> {
                refreshes.incrementAndGet()
                if (query["refresh_token"] != refreshToken) return exchange.reply(400, """{"error":"invalid_grant"}""")
            }

            else -> return exchange.reply(400, """{"error":"unsupported_grant_type"}""")
        }
        val n = issued.incrementAndGet()
        accessToken = "fake-access-$n-a1b2c3d4e5"
        refreshToken = "fake-refresh-$n-f6g7h8i9"
        exchange.reply(200, """{"access_token":"$accessToken","refresh_token":"$refreshToken","token_type":"bearer","expires_in":3600}""")
    }

    private fun handleRest(exchange: HttpExchange) {
        restCalls.incrementAndGet()
        failWith?.let { return exchange.reply(it, """{"error":"server error"}""") }
        if (exchange.requestHeaders.getFirst("Authorization") != "bearer $accessToken") {
            return exchange.reply(401, """{"error":"invalid_token"}""")
        }
        val query = parseQuery(exchange.requestURI.rawQuery)
        when (exchange.requestURI.path) {
            "/rest/reservations/selfs" -> exchange.reply(
                200,
                ok(selfs.joinToString(",", "[", "]") { (id, name) -> """{"id":$id,"name":"$name","type":"NORMAL"}""" }),
            )

            "/rest/reservations/programs/v2" -> {
                val selfId = query["selfId"]?.toIntOrNull() ?: return exchange.reply(400, "{}")
                val weekStart = query["weekStartDate"]?.take(10)?.let(LocalDate::parse) ?: return exchange.reply(400, "{}")
                val week = days[selfId].orEmpty().filter { it >= weekStart && it < weekStart.plusDays(7) }.sorted()
                // Like the real Samad (seen 2026-11-08): a week with no menu is an HTTP 400, not an empty list.
                if (week.isEmpty()) {
                    return exchange.reply(
                        400,
                        """{"type":"ERROR","code":10108005,"message":"self has no program","messageFa":"هیچ برنامه ی غذایی ای تعریف نشده است.","messageResource":"SELF_HAS_NO_PROGRAM"}""",
                    )
                }
                val programs = week.joinToString(",", "[", "]") { day ->
                    """[{"date":"$day 00:00:00","foodName":"غذای $selfId","foodTypeTitle":"غذای پرهزینه 1","groupId":17,"selfId":$selfId,"mealTypeId":2,"price":30000}]"""
                }
                exchange.reply(200, ok("""{"selfWeekPrograms":$programs,"userId":42,"userWeekReserves":[]}"""))
            }

            else -> exchange.reply(404, "{}")
        }
    }

    private fun ok(payload: String) = """{"code":200,"type":"SUCCESS","payload":$payload}"""

    private fun parseQuery(raw: String?): Map<String, String> = raw.orEmpty().split("&").filter { "=" in it }.associate {
        URLDecoder.decode(it.substringBefore("="), Charsets.UTF_8) to URLDecoder.decode(it.substringAfter("="), Charsets.UTF_8)
    }

    private fun HttpExchange.reply(status: Int, body: String) {
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    override fun close() = server.stop(0)
}
