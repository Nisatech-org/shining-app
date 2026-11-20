package app.shining.server.samad

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import app.shining.server.log.Log
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Samad rejected the student number or password, or the session expired. */
open class SamadAuthException(message: String) : Exception(message)

/**
 * The server has no working Samad session, and a password login needs a captcha only a person can
 * solve. Someone has to sign in on the sign-in page; the server never tries to read the captcha.
 */
class SignInNeededException(message: String) : SamadAuthException(message)

/** Samad answered with an HTTP error or a reply we couldn't use. */
class SamadHttpException(val status: Int, message: String) : Exception(message)

/** One of Samad's login captchas: the picture (PNG, base64) and the token that goes back with the answer. */
data class SamadCaptcha(val nonce: String, val validation: String, val imageBase64: String)

/** A person's answer to a [SamadCaptcha]. Each captcha is good for one login attempt. */
data class CaptchaAnswer(val nonce: String, val validation: String, val text: String)

/**
 * Talks to Samad the same way the app does: the samad-mobile OAuth client, then REST calls with
 * the access token. It only reads; nothing here reserves or changes anything.
 */
class SamadClient(
    private val baseUrl: String = "https://setad.dining.sharif.edu/",
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .sslContext(SamadTls.sslContext)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    @Volatile
    private var authorization: String? = null

    /** Called with each new refresh token Samad hands out, so a session can outlive a restart. */
    @Volatile
    var onRefreshToken: ((String) -> Unit)? = null

    /**
     * Called with each new access token (the Authorization header value) and when it runs out.
     * Samad's last for about 180 days, so saving them keeps the session through restarts.
     */
    @Volatile
    var onAuthorization: ((String, Instant?) -> Unit)? = null

    /** The Authorization header value in use, or null before any login. */
    val currentAuthorization: String?
        get() = authorization

    /** Uses an access token saved earlier, without asking Samad; the next call shows whether it still works. */
    fun useSaved(savedAuthorization: String, savedRefreshToken: String?) {
        authorization = savedAuthorization
        if (savedRefreshToken != null) refreshToken = savedRefreshToken
    }

    @Volatile
    private var refreshToken: String? = null

    val isLoggedIn: Boolean
        get() = authorization != null

    /**
     * A password login. Since November 2026 Samad wants a [captcha] answer with it, solved by a
     * person; without one it answers "Captcha params is invalid".
     */
    fun login(username: String, password: CharArray, captcha: CaptchaAnswer? = null) {
        requestToken(
            "login",
            listOf(
                "username" to username,
                "password" to String(password),
                "grant_type" to "password",
                "scope" to "read+write",
            ) + captcha?.let {
                listOf("captchaText" to it.text, "validation" to it.validation, "nonce" to it.nonce)
            }.orEmpty(),
        )
        Log.info(TAG, "logged in", "user" to username, "withCaptcha" to (captcha != null))
    }

    /** A new login captcha, the same way the app gets one for its login screen. Needs no login. */
    fun captcha(): SamadCaptcha {
        val nonce = UUID.randomUUID().toString()
        val request = HttpRequest.newBuilder(URI.create("${baseUrl}rest/captcha"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"nonce":"$nonce"}"""))
            .build()
        val started = System.nanoTime()
        val response = http.send(request, BodyHandlers.ofString())
        Log.debug(TAG, "POST rest/captcha", "status" to response.statusCode(), "ms" to elapsedMs(started))
        if (response.statusCode() != 200) {
            throw SamadHttpException(response.statusCode(), "Samad gave no captcha: HTTP ${response.statusCode()} ${response.body().take(200)}")
        }
        val payload = runCatching { json.parseToJsonElement(response.body()) }.getOrNull()?.samadPayload() as? JsonObject
        val image = payload?.get("image")?.jsonPrimitive?.contentOrNull
        val validation = payload?.get("validation")?.jsonPrimitive?.contentOrNull
        if (image.isNullOrBlank() || validation.isNullOrBlank()) {
            throw SamadHttpException(response.statusCode(), "Samad's captcha reply had no picture.")
        }
        return SamadCaptcha(nonce, validation, image)
    }

    /**
     * Continues a session from a saved refresh token (from a login where a person solved Samad's
     * captcha). False when Samad refuses it.
     */
    fun resume(savedRefreshToken: String): Boolean {
        refreshToken = savedRefreshToken
        return refresh()
    }

    /** Renews the access token with the refresh token. False when there is none or Samad refuses it. */
    fun refresh(): Boolean {
        val token = refreshToken ?: return false.also { Log.info(TAG, "no refresh token, a full login is needed") }
        return try {
            requestToken("refresh", listOf("refresh_token" to token, "grant_type" to "refresh_token"))
            Log.info(TAG, "token refreshed")
            true
        } catch (e: SamadAuthException) {
            // Samad's own reason (error replies hold no tokens): e.g. whether it wants a captcha here too.
            Log.warn(TAG, "refresh token rejected, a full login is needed", "reason" to e.message)
            refreshToken = null
            false
        }
    }

    private fun requestToken(kind: String, params: List<Pair<String, String>>) {
        val query = params.joinToString("&") { (key, value) -> "$key=${encode(value)}" }
        val request = HttpRequest.newBuilder(URI.create("${baseUrl}oauth/token?$query"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", CLIENT_AUTHORIZATION)
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        val started = System.nanoTime()
        val response = http.send(request, BodyHandlers.ofString())
        // Only the status and timing: the URL holds the password and the reply holds tokens.
        Log.debug(TAG, "POST oauth/token", "kind" to kind, "status" to response.statusCode(), "ms" to elapsedMs(started))
        when (response.statusCode()) {
            200 -> Unit
            // Error replies hold no tokens, so Samad's own reason (e.g. a captcha is needed) is safe to show.
            400, 401 -> throw SamadAuthException(
                "Samad rejected the $kind (HTTP ${response.statusCode()})" +
                    (errorReason(response.body())?.let { ": $it" } ?: "."),
            )
            else -> throw SamadHttpException(
                response.statusCode(),
                "Samad $kind failed: HTTP ${response.statusCode()} ${response.body().take(200)}",
            )
        }
        // The reply holds the tokens, so it is read here and never printed or saved.
        val body = runCatching { json.parseToJsonElement(response.body()).jsonObject }.getOrNull()
        val token = body?.get("access_token")?.jsonPrimitive?.contentOrNull
        val type = body?.get("token_type")?.jsonPrimitive?.contentOrNull ?: "bearer"
        if (token.isNullOrBlank()) {
            throw SamadHttpException(response.statusCode(), "Login reply had no access token.")
        }
        val newAuthorization = "$type $token"
        authorization = newAuthorization
        val newRefresh = body?.get("refresh_token")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val validSeconds = body?.get("expires_in")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        // How long the session lasts without a renewal, and whether renewals hand out a new
        // refresh token. Neither is a secret; the tokens themselves are never logged.
        Log.info(
            TAG, "token issued",
            "kind" to kind,
            "accessValidSeconds" to validSeconds,
            "newRefreshToken" to (newRefresh != null && newRefresh != refreshToken),
        )
        onAuthorization?.invoke(newAuthorization, validSeconds?.let { Instant.now().plusSeconds(it) })
        if (newRefresh != null) {
            refreshToken = newRefresh
            onRefreshToken?.invoke(newRefresh)
        }
    }

    /** Samad's reason in an error reply: `message` (e.g. "Captcha params is invalid") or OAuth's `error_description`. */
    private fun errorReason(body: String): String? {
        val reply = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        return listOf("message", "error_description", "error")
            .firstNotNullOfOrNull { reply[it]?.jsonPrimitive?.contentOrNull?.takeIf { text -> text.isNotBlank() } }
            ?.take(200)
    }

    /** GETs [path] and returns the whole JSON reply. Works without logging in, as a reachability check. */
    fun get(path: String, query: Map<String, String> = emptyMap()): JsonElement {
        val queryString = query.entries.joinToString("&") { (key, value) ->
            "$key=${encode(value)}"
        }
        val uri = URI.create(baseUrl + path + if (queryString.isEmpty()) "" else "?$queryString")
        val builder = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .GET()
        authorization?.let { builder.header("Authorization", it) }
        val started = System.nanoTime()
        val response = http.send(builder.build(), BodyHandlers.ofString())
        Log.debug(
            TAG, "GET $path",
            "query" to queryString.ifEmpty { null },
            "status" to response.statusCode(),
            "ms" to elapsedMs(started),
            "bytes" to response.body().length,
        )
        return when (response.statusCode()) {
            200 -> json.parseToJsonElement(response.body())
            401 -> throw SamadAuthException("Samad says this request isn't logged in (HTTP 401).")
            else -> throw SamadHttpException(
                response.statusCode(),
                "GET $path: HTTP ${response.statusCode()} ${response.body().take(300)}",
            )
        }
    }

    /** HTTP status of a GET without logging in. Used to check the connection and certificate. */
    fun ping(path: String = "rest/users/me"): Int {
        val request = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()
        return http.send(request, BodyHandlers.discarding()).statusCode()
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

    private fun elapsedMs(startedNanos: Long) = (System.nanoTime() - startedNanos) / 1_000_000

    companion object {
        private const val TAG = "samad"

        /** The official Samad mobile app's OAuth client (samad-mobile), the same one Shining uses. */
        private const val CLIENT_AUTHORIZATION =
            "Basic c2FtYWQtbW9iaWxlOnNhbWFkLW1vYmlsZS1zZWNyZXQ="
    }
}

/**
 * The `payload` of a Samad reply (`{code, type, message, messageFa, payload}`). Fails with Samad's
 * own message when `type` is ERROR.
 */
fun JsonElement.samadPayload(): JsonElement? {
    val reply = this as? JsonObject ?: return this
    val type = reply["type"]?.jsonPrimitive?.contentOrNull
    if (type == "ERROR") {
        val message = reply["messageFa"]?.jsonPrimitive?.contentOrNull
            ?: reply["message"]?.jsonPrimitive?.contentOrNull
            ?: "unknown error"
        throw SamadHttpException(200, "Samad error: $message")
    }
    return reply["payload"]
}
