package app.shining.server.push

import app.shining.server.log.Log
import app.shining.server.watch.MenuBatch
import app.shining.server.watch.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64

/** Topics the app subscribes to. */
const val MENUS_TOPIC = "menus"
const val ADMIN_TOPIC = "admin"

/** The fields of a Firebase service-account key file that sending needs. */
@Serializable
data class ServiceAccount(
    val project_id: String,
    val client_email: String,
    val private_key: String,
    val token_uri: String = "https://oauth2.googleapis.com/token",
) {
    // Keeps the private key out of logs.
    override fun toString() = "ServiceAccount(project_id=$project_id, client_email=$client_email)"

    companion object {
        fun load(file: File): ServiceAccount =
            Json { ignoreUnknownKeys = true }.decodeFromString(serializer(), file.readText())
    }
}

/** A tiny HTTP POST, so tests can stand in for Google. Returns the status and body. */
fun interface HttpPost {
    fun post(url: String, headers: Map<String, String>, body: String): Pair<Int, String>
}

class JdkHttpPost(
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(),
) : HttpPost {
    override fun post(url: String, headers: Map<String, String>, body: String): Pair<Int, String> {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(body))
        headers.forEach { (name, value) -> builder.header(name, value) }
        val response = client.send(builder.build(), BodyHandlers.ofString())
        return response.statusCode() to response.body()
    }
}

/**
 * Sends alerts through Firebase Cloud Messaging (HTTP v1 API) as data messages to a topic, so the
 * server never needs to know any user or device.
 *
 * Authentication is the standard service-account flow, done with the JDK only: sign a JWT with
 * the key's private key (RS256), trade it at Google's token endpoint for an access token, and
 * reuse that token until shortly before it expires (about an hour).
 */
class FcmNotifier(
    private val account: ServiceAccount,
    private val http: HttpPost = JdkHttpPost(),
    private val clock: () -> Instant = Instant::now,
    private val json: Json = Json,
) : Notifier {

    private val privateKey: PrivateKey = parsePrivateKey(account.private_key)

    @Volatile
    private var cachedToken: Pair<String, Instant>? = null

    override suspend fun sendMenus(batch: MenuBatch) = sendMenusTo(MENUS_TOPIC, batch)

    /** A menu message to any topic; [MENUS_TOPIC] for real alerts, [ADMIN_TOPIC] for tests. */
    suspend fun sendMenusTo(topic: String, batch: MenuBatch) = send(
        topic = topic,
        data = mapOf("type" to "menus", "batch" to json.encodeToString(MenuBatch.serializer(), batch)),
    )

    override suspend fun alertAdmin(message: String) = send(
        topic = ADMIN_TOPIC,
        data = mapOf("type" to "admin", "message" to message),
    )

    /** The request body for one message. Data values must be strings in FCM. */
    internal fun messageBody(topic: String, data: Map<String, String>): String = buildJsonObject {
        putJsonObject("message") {
            put("topic", topic)
            putJsonObject("data") { data.forEach { (key, value) -> put(key, value) } }
            putJsonObject("android") {
                // High priority wakes the app in Doze; a day later the menu news is stale anyway.
                put("priority", "HIGH")
                put("ttl", "86400s")
            }
        }
    }.toString()

    private suspend fun send(topic: String, data: Map<String, String>) = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val (status, body) = http.post(
            "https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send",
            mapOf(
                "Authorization" to "Bearer ${accessToken()}",
                "Content-Type" to "application/json; charset=UTF-8",
            ),
            messageBody(topic, data),
        )
        val ms = (System.nanoTime() - started) / 1_000_000
        if (status == 401) cachedToken = null
        if (status !in 200..299) {
            Log.warn(TAG, "FCM rejected the message", "topic" to topic, "type" to data["type"], "status" to status, "ms" to ms, "reply" to body.take(300))
            throw IOException("FCM send to '$topic' failed: HTTP $status ${body.take(300)}")
        }
        // The reply is {"name": "projects/.../messages/<id>"}: the id finds the message in Firebase.
        val messageId = runCatching { json.parseToJsonElement(body).jsonObject["name"]?.jsonPrimitive?.contentOrNull }.getOrNull()
        Log.info(TAG, "sent", "topic" to topic, "type" to data["type"], "bytes" to (data["batch"]?.length ?: 0), "ms" to ms, "id" to messageId)
    }

    private fun accessToken(): String {
        val now = clock()
        cachedToken?.let { (token, expiresAt) -> if (now.isBefore(expiresAt)) return token }

        val form = "grant_type=${encode("urn:ietf:params:oauth:grant-type:jwt-bearer")}&assertion=${encode(signedJwt(now))}"
        val (status, body) = http.post(
            account.token_uri,
            mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            form,
        )
        // Error replies from the token endpoint hold no secrets; success replies do, so they're never logged.
        if (status !in 200..299) {
            Log.warn(TAG, "Google refused the access token request", "status" to status, "reply" to body.take(300))
            throw IOException("Google token request failed: HTTP $status ${body.take(300)}")
        }
        val reply = json.parseToJsonElement(body).jsonObject
        val token = reply["access_token"]?.jsonPrimitive?.contentOrNull
            ?: throw IOException("Google token reply had no access_token")
        val lifetime = reply["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600
        cachedToken = token to now.plusSeconds((lifetime - TOKEN_MARGIN_SECONDS).coerceAtLeast(60))
        Log.info(TAG, "got a Google access token", "validSeconds" to lifetime, "account" to account.client_email)
        return token
    }

    /** A JWT asking for FCM access, signed with the service account's key. */
    internal fun signedJwt(now: Instant): String {
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val claims = buildJsonObject {
            put("iss", account.client_email)
            put("scope", FCM_SCOPE)
            put("aud", account.token_uri)
            put("iat", now.epochSecond)
            put("exp", now.epochSecond + 3600)
        }.toString()
        val unsigned = "${base64Url(header.toByteArray())}.${base64Url(claims.toByteArray())}"
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(unsigned.toByteArray())
            sign()
        }
        return "$unsigned.${base64Url(signature)}"
    }

    companion object {
        private const val TAG = "fcm"
        const val FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"

        /** Tokens are renewed this long before Google says they expire. */
        private const val TOKEN_MARGIN_SECONDS = 120L

        private fun base64Url(bytes: ByteArray): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

        /** Reads the PKCS#8 PEM key that Firebase puts in `private_key`. */
        internal fun parsePrivateKey(pem: String): PrivateKey {
            val base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\n", "")
                .filterNot { it.isWhitespace() }
            val spec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64))
            return KeyFactory.getInstance("RSA").generatePrivate(spec)
        }
    }
}
