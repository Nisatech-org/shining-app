package app.shining.server

import app.shining.server.http.DebugInfo
import app.shining.server.http.debugRoutes
import app.shining.server.watch.CheckPlan
import app.shining.server.watch.WATCHED
import app.shining.server.watch.WatchState
import app.shining.server.samad.SamadCaptcha
import app.shining.server.watch.SamadSignIn
import app.shining.server.watch.SignInResult
import app.shining.server.watch.nextCheckPlan
import app.shining.server.watch.tehran
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigAndDebugTest {

    private val base = mapOf("SAMAD_USERNAME" to "۹۸۱۲۳۴۵۶ ", "SAMAD_PASSWORD" to "pw")

    @Test
    fun `settings have safe defaults`() {
        val config = ServerConfig.fromEnv(base)
        assertEquals("98123456", config.username)
        assertEquals("127.0.0.1", config.host)
        assertEquals(8050, config.port)
        assertEquals("https://setad.dining.sharif.edu/", config.samadBaseUrl)
        assertNull(config.fcmKeyFile)
        assertNull(config.debugToken)
    }

    @Test
    fun `missing or bad settings say exactly what's wrong`() {
        assertEquals("Set SAMAD_USERNAME.", assertFailsWith<IllegalArgumentException> { ServerConfig.fromEnv(emptyMap()) }.message)
        assertTrue("isn't a file" in assertFailsWith<IllegalArgumentException> {
            ServerConfig.fromEnv(base + ("SHINING_FCM_KEY_FILE" to "/no/such/file.json"))
        }.message.orEmpty())
        assertTrue("16 characters" in assertFailsWith<IllegalArgumentException> {
            ServerConfig.fromEnv(base + ("SHINING_DEBUG_TOKEN" to "short"))
        }.message.orEmpty())
    }

    @Test
    fun `the config never prints the password or the debug token`() {
        val key = File(createTempDirectory("cfg").toFile(), "key.json").apply { writeText("{}") }
        val text = ServerConfig.fromEnv(
            base + ("SAMAD_BASE_URL" to "http://localhost:9") + ("SHINING_FCM_KEY_FILE" to key.path) + ("SHINING_DEBUG_TOKEN" to "0123456789abcdef-secret"),
        ).toString()
        assertTrue("pw" !in text.replace("http", "").replace("port", ""))
        assertTrue("0123456789abcdef-secret" !in text)
        assertTrue("http://localhost:9/" in text)
    }

    @Test
    fun `the next check explains itself`() {
        assertTrue("before Sunday" in nextCheckPlan(tehran(7, 10), WatchState(), setOf("self")).reason)
        assertTrue("no known pattern" in nextCheckPlan(tehran(9, 10), WatchState(), setOf("self")).reason)
    }

    private val info = DebugInfo.of(WatchState(weekStart = WATCHED), CheckPlan(tehran(9, 10), "self has no known pattern yet"), tehran(9, 0), tehran(9, 5))

    @Test
    fun `the debug page needs the right token, and doesn't exist without one`() = testApplication {
        application { debugRoutes("0123456789abcdef-secret") { info } }
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/debug").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/debug") { header("X-Debug-Token", "wrong") }.status)

        val response = client.get("/v1/debug") { header("X-Debug-Token", "0123456789abcdef-secret") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers["Cache-Control"])
        assertTrue("self has no known pattern yet" in response.bodyAsText())
        assertEquals(HttpStatusCode.OK, client.get("/v1/debug?token=0123456789abcdef-secret").status)
    }

    @Test
    fun `without a configured token the page is always 404`() = testApplication {
        application { debugRoutes(null, signIn = FakeSignIn()) { info } }
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/debug?token=anything-at-all-here").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/debug?signin").status)
        assertEquals(HttpStatusCode.NotFound, client.post("/v1/debug") { setBody("""{"action":"captcha"}""") }.status)
    }

    private class FakeSignIn : SamadSignIn {
        override var signInNeeded = true
        var captchas = 0

        override suspend fun newCaptcha(): SamadCaptcha = SamadCaptcha("nonce-${++captchas}", "validation", "iVBORw0KGgo=")

        override suspend fun signIn(nonce: String, text: String): SignInResult =
            if (nonce == "nonce-$captchas" && text == "12345") SignInResult(true, "سرور وارد سماد شد.").also { signInNeeded = false }
            else SignInResult(false, "کد درست نبود. یه بار دیگه.")
    }

    @Test
    fun `the sign-in page shows the captcha and signs the server in, with the token`() = testApplication {
        val signIn = FakeSignIn()
        var signedIn = 0
        application { debugRoutes("0123456789abcdef-secret", signIn = signIn, onSignedIn = { signedIn++ }) { info } }

        // The page itself holds nothing secret.
        val page = client.get("/v1/debug?signin")
        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue("ورود سرور به سماد" in page.bodyAsText())
        assertEquals("no-store", page.headers["Cache-Control"])

        // Every action needs the token, in the header only.
        assertEquals(HttpStatusCode.NotFound, client.post("/v1/debug") { setBody("""{"action":"captcha"}""") }.status)
        assertEquals(HttpStatusCode.NotFound, client.post("/v1/debug?token=0123456789abcdef-secret") { setBody("""{"action":"captcha"}""") }.status)
        assertEquals(0, signIn.captchas)

        suspend fun act(body: String) = client.post("/v1/debug") {
            header("X-Debug-Token", "0123456789abcdef-secret")
            setBody(body)
        }
        val captcha = act("""{"action":"captcha"}""")
        assertEquals(HttpStatusCode.OK, captcha.status)
        assertTrue("\"nonce\":\"nonce-1\"" in captcha.bodyAsText())
        assertTrue("\"needed\":true" in captcha.bodyAsText())

        assertTrue("\"ok\":false" in act("""{"action":"signin","nonce":"nonce-1","text":"99999"}""").bodyAsText())
        assertEquals(0, signedIn)
        assertTrue("\"ok\":true" in act("""{"action":"signin","nonce":"nonce-1","text":"12345"}""").bodyAsText())
        assertEquals(1, signedIn, "a check follows right away")

        assertEquals(HttpStatusCode.BadRequest, act("not json").status)
        assertEquals(HttpStatusCode.BadRequest, act("""{"action":"reserve-food"}""").status)
    }

    @Test
    fun `when Samad can't be reached the page says so`() = testApplication {
        val broken = object : SamadSignIn {
            override val signInNeeded = true
            override suspend fun newCaptcha(): SamadCaptcha = throw java.net.http.HttpTimeoutException("request timed out")
            override suspend fun signIn(nonce: String, text: String) = SignInResult(false, "")
        }
        application { debugRoutes("0123456789abcdef-secret", signIn = broken) { info } }
        val reply = client.post("/v1/debug") {
            header("X-Debug-Token", "0123456789abcdef-secret")
            setBody("""{"action":"captcha"}""")
        }
        assertEquals(HttpStatusCode.BadGateway, reply.status)
        assertTrue("سماد" in reply.bodyAsText())
    }
}
