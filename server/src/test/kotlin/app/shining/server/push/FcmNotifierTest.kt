package app.shining.server.push

import app.shining.server.watch.BatchVendor
import app.shining.server.watch.MenuBatch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.URLDecoder
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FcmNotifierTest {

    /** A throwaway key pair made for the test, never a real Firebase key. */
    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val pem = "-----BEGIN PRIVATE KEY-----\n" +
        Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keyPair.private.encoded) +
        "\n-----END PRIVATE KEY-----\n"
    private val account = ServiceAccount(
        project_id = "test-project",
        client_email = "sender@test-project.iam.gserviceaccount.com",
        private_key = pem,
    )

    /** Plays Google: answers token requests and sends, and records them. */
    private class FakeGoogle(var sendStatus: Int = 200) : HttpPost {
        val requests = mutableListOf<Triple<String, Map<String, String>, String>>()
        var tokensIssued = 0

        override fun post(url: String, headers: Map<String, String>, body: String): Pair<Int, String> {
            requests += Triple(url, headers, body)
            return if (url.contains("oauth2")) {
                tokensIssued++
                200 to """{"access_token":"token-$tokensIssued","expires_in":3600,"token_type":"Bearer"}"""
            } else {
                sendStatus to if (sendStatus == 200) """{"name":"projects/test-project/messages/1"}""" else """{"error":{"code":$sendStatus}}"""
            }
        }
    }

    private val batch = MenuBatch(
        weekStart = LocalDate.of(2026, 11, 14),
        vendors = listOf(BatchVendor("kalana", "کلانا", listOf(LocalDate.of(2026, 11, 14)), true)),
    )

    @Test
    fun `a menu batch goes to the menus topic as a high-priority data message`() = runTest {
        val google = FakeGoogle()
        FcmNotifier(account, google).sendMenus(batch)

        val (url, headers, body) = google.requests.last()
        assertEquals("https://fcm.googleapis.com/v1/projects/test-project/messages:send", url)
        assertEquals("Bearer token-1", headers["Authorization"])
        val message = Json.parseToJsonElement(body).jsonObject.getValue("message").jsonObject
        assertEquals("menus", message.getValue("topic").jsonPrimitive.content)
        val data = message.getValue("data").jsonObject
        assertEquals("menus", data.getValue("type").jsonPrimitive.content)
        val sent = Json.decodeFromString(MenuBatch.serializer(), data.getValue("batch").jsonPrimitive.content)
        assertEquals(batch, sent)
        assertEquals("HIGH", message.getValue("android").jsonObject.getValue("priority").jsonPrimitive.content)
    }

    @Test
    fun `admin alerts go to the admin topic`() = runTest {
        val google = FakeGoogle()
        FcmNotifier(account, google).alertAdmin("سرور به سماد وصل نمی‌شه")

        val message = Json.parseToJsonElement(google.requests.last().third).jsonObject.getValue("message").jsonObject
        assertEquals("admin", message.getValue("topic").jsonPrimitive.content)
        assertEquals("سرور به سماد وصل نمی‌شه", message.getValue("data").jsonObject.getValue("message").jsonPrimitive.content)
    }

    @Test
    fun `the access token is reused until it nearly expires`() = runTest {
        val google = FakeGoogle()
        var now = Instant.parse("2026-11-10T10:00:00Z")
        val notifier = FcmNotifier(account, google, clock = { now })

        notifier.sendMenus(batch)
        now = now.plusSeconds(3000)
        notifier.sendMenus(batch)
        assertEquals(1, google.tokensIssued)

        now = now.plusSeconds(600)
        notifier.sendMenus(batch)
        assertEquals(2, google.tokensIssued)
    }

    @Test
    fun `the token request carries a JWT signed with the account key`() = runTest {
        val google = FakeGoogle()
        FcmNotifier(account, google, clock = { Instant.ofEpochSecond(1_800_000_000) }).sendMenus(batch)

        val (url, _, form) = google.requests.first()
        assertEquals("https://oauth2.googleapis.com/token", url)
        val params = form.split("&").associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), Charsets.UTF_8) }
        assertEquals("urn:ietf:params:oauth:grant-type:jwt-bearer", params["grant_type"])

        val (header, claims, signature) = params.getValue("assertion").split(".")
        val verifier = Signature.getInstance("SHA256withRSA").apply {
            initVerify(keyPair.public as RSAPublicKey)
            update("$header.$claims".toByteArray())
        }
        assertTrue(verifier.verify(Base64.getUrlDecoder().decode(signature)))

        val decoded = Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(claims))).jsonObject
        assertEquals(account.client_email, decoded.getValue("iss").jsonPrimitive.content)
        assertEquals(FcmNotifier.FCM_SCOPE, decoded.getValue("scope").jsonPrimitive.content)
        assertEquals("1800003600", decoded.getValue("exp").jsonPrimitive.content)
    }

    @Test
    fun `a failed send throws so the watcher keeps the batch and retries`() = runTest {
        val google = FakeGoogle(sendStatus = 503)
        assertFailsWith<IOException> { FcmNotifier(account, google).sendMenus(batch) }
    }

    @Test
    fun `a key with escaped newlines, as in some JSON files, still parses`() {
        val escaped = pem.replace("\n", "\\n")
        FcmNotifier.parsePrivateKey(escaped)
    }

    @Test
    fun `the account never prints its key`() {
        assertTrue("PRIVATE" !in account.toString())
    }
}
