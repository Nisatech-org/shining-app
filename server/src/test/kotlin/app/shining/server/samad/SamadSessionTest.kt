package app.shining.server.samad

import app.shining.server.testing.FakeSamad
import app.shining.server.watch.PASSWORD_RETRY
import app.shining.server.watch.SamadMenuSource
import app.shining.server.watch.SamadSession
import kotlinx.coroutines.test.runTest
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Samad wants a captcha for password logins, so the server runs on a session someone started on
 * the sign-in page, and keeps it alive by itself.
 */
class SamadSessionTest {

    private val samad = FakeSamad()
    private val sessionFile = File(createTempDirectory("shining-session").toFile(), "samad-session")
    private var now: Instant = Instant.parse("2026-11-15T06:00:00Z")

    @AfterTest
    fun stop() = samad.close()

    /** A session saved earlier (the way a sign-in leaves one), for tests about what comes after. */
    private fun savedSession(): String {
        var token: String? = null
        val other = SamadClient(baseUrl = samad.baseUrl).apply { onRefreshToken = { token = it } }
        samad.requireCaptcha = false
        other.login("98123456", "test-password-1".toCharArray())
        samad.requireCaptcha = true
        return token!!.also { sessionFile.writeText(it + "\n") }
    }

    private fun source(password: String = "test-password-1") = SamadMenuSource(
        SamadClient(baseUrl = samad.baseUrl), "98123456", password.toCharArray(), SamadSession(sessionFile), clock = { now },
    )

    @Test
    fun `with a saved session the server reads menus without a password login`() = runTest {
        val saved = savedSession()
        val loginsBefore = samad.logins.get()

        assertEquals(3, source().selfs().size)
        assertEquals(loginsBefore, samad.logins.get(), "no password login, so no captcha")
        assertEquals(1, samad.refreshes.get())
        assertNotEquals(saved, sessionFile.readText().trim(), "the new token from Samad replaces the saved one")
    }

    @Test
    fun `the session survives a restart and an expired access token`() = runTest {
        savedSession()
        source().selfs()
        samad.expireAccessToken()

        // A restart: a new source reads the token the last one saved.
        assertEquals(3, source().selfs().size)
        assertEquals(2, samad.refreshes.get())
    }

    @Test
    fun `without a session the server says someone has to sign in, and why`() = runTest {
        samad.requireCaptcha = true
        val source = source()
        val error = assertFailsWith<SignInNeededException> { source.selfs() }
        assertTrue("Captcha params is invalid" in error.message.orEmpty(), error.message)
        assertTrue(source.signInNeeded)
    }

    @Test
    fun `someone signs in on the page, and the server keeps that session`() = runTest {
        samad.requireCaptcha = true
        val source = source()
        assertFailsWith<SignInNeededException> { source.selfs() }

        val captcha = source.newCaptcha()
        assertTrue(captcha.imageBase64.isNotEmpty())
        // Typed on a phone with Persian digits.
        val result = source.signIn(captcha.nonce, " ۱۲۳۴۵ ")
        assertTrue(result.ok, result.message)
        assertFalse(source.signInNeeded)
        assertNotNull(SamadSession(sessionFile).load(), "the session is saved for restarts")

        assertEquals(3, source.selfs().size)
        val loginsSoFar = samad.logins.get()
        // A restart reads menus from the saved session: no new login, so no captcha.
        assertEquals(3, source().selfs().size)
        assertEquals(loginsSoFar, samad.logins.get())
    }

    @Test
    fun `a restart keeps the session with the saved access token, without a renewal`() = runTest {
        samad.requireCaptcha = true
        val source = source()
        assertTrue(source.signIn(source.newCaptcha().nonce, samad.captchaAnswer).ok)
        val until = source.sessionExpiresAt
        assertNotNull(until, "the access token's end, from Samad's expires_in")
        assertTrue(until!! < Instant.now().plusSeconds(3700) && until > Instant.now().plusSeconds(3500), until.toString())

        // A restart, or a deploy: the new process reads the saved access token.
        val restarted = source()
        assertEquals(3, restarted.selfs().size)
        assertEquals(0, samad.refreshes.get(), "Samad refuses the server's renewals, so none is needed")
        assertEquals(until, restarted.sessionExpiresAt)
    }

    @Test
    fun `a saved access token Samad refuses, or one past its end, falls back to renewing`() = runTest {
        samad.requireCaptcha = true
        val source = source()
        assertTrue(source.signIn(source.newCaptcha().nonce, samad.captchaAnswer).ok)

        samad.expireAccessToken()
        assertEquals(3, source().selfs().size, "renewed with the refresh token")
        assertEquals(1, samad.refreshes.get())

        // Saved with an end that has passed: not even tried.
        val accessFile = File(sessionFile.parentFile, "samad-session-access")
        accessFile.writeText(accessFile.readLines().first() + "\n" + now.minusSeconds(60))
        val loginsBefore = samad.logins.get()
        assertEquals(3, source().selfs().size)
        assertEquals(2, samad.refreshes.get())
        assertEquals(loginsBefore, samad.logins.get())
    }

    @Test
    fun `a wrong code fails, and each captcha is good for one try`() = runTest {
        samad.requireCaptcha = true
        val source = source()
        val captcha = source.newCaptcha()

        val wrong = source.signIn(captcha.nonce, "99999")
        assertFalse(wrong.ok)
        assertTrue("درست نبود" in wrong.message, wrong.message)
        assertFalse(source.signIn(captcha.nonce, samad.captchaAnswer).ok, "that captcha was used up")
        assertFalse(source.signIn("a-nonce-the-server-never-gave", samad.captchaAnswer).ok)
        assertFalse(source.signIn(source.newCaptcha().nonce, "  ").ok, "an empty answer isn't sent")

        val fresh = source.newCaptcha()
        now += Duration.ofMinutes(11)
        assertFalse(source.signIn(fresh.nonce, samad.captchaAnswer).ok, "a captcha left too long has expired")
        assertTrue(source.signIn(source.newCaptcha().nonce, samad.captchaAnswer).ok)
    }

    @Test
    fun `without a session the server doesn't keep failing logins on Samad`() = runTest {
        samad.requireCaptcha = true
        sessionFile.writeText("an-old-token-samad-no-longer-knows")
        val source = source()
        repeat(5) {
            assertFailsWith<SignInNeededException> { source.selfs() }
            now += Duration.ofMinutes(5)
        }
        assertEquals(1, samad.refreshes.get(), "a refused session isn't tried again")
        assertEquals(1, samad.logins.get(), "a password login without a captcha is tried rarely")

        now += PASSWORD_RETRY
        assertFailsWith<SignInNeededException> { source.selfs() }
        assertEquals(2, samad.logins.get(), "in case Samad stops asking for a captcha")

        // A new session in the file is tried.
        savedSession()
        assertEquals(3, source.selfs().size)
        assertFalse(source.signInNeeded)
    }

    @Test
    fun `a session file that can't be read is treated as none`() {
        assertEquals(null, SamadSession(File(sessionFile.parentFile, "missing")).load())
        sessionFile.writeText("  \n")
        assertEquals(null, SamadSession(sessionFile).load())
        SamadSession(sessionFile).save("token-1")
        assertNotNull(SamadSession(sessionFile).load())
    }
}
