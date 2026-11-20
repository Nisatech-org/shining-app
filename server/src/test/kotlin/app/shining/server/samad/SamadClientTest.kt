package app.shining.server.samad

import app.shining.server.testing.FakeSamad
import app.shining.server.watch.SamadMenuSource
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SamadClientTest {

    private val samad = FakeSamad()
    private val client = SamadClient(baseUrl = samad.baseUrl)

    @AfterTest
    fun stop() = samad.close()

    @Test
    fun `login, then REST calls carry the token`() {
        assertFalse(client.isLoggedIn)
        client.login("98123456", "test-password-1".toCharArray())
        assertTrue(client.isLoggedIn)

        val selfs = client.get("rest/reservations/selfs").samadPayload()
        assertTrue(selfs.toString().contains("کلانا"))
    }

    @Test
    fun `a wrong password is an auth error, not a network error`() {
        val error = assertFailsWith<SamadAuthException> { client.login("98123456", "wrong".toCharArray()) }
        assertTrue("HTTP 400" in error.message.orEmpty())
        assertFalse(client.isLoggedIn)
    }

    @Test
    fun `a call without a valid token is an auth error`() {
        assertFailsWith<SamadAuthException> { client.get("rest/reservations/selfs") }
    }

    @Test
    fun `a server error carries its status`() {
        client.login("98123456", "test-password-1".toCharArray())
        samad.failWith = 503
        val error = assertFailsWith<SamadHttpException> { client.get("rest/reservations/selfs") }
        assertEquals(503, error.status)
    }

    @Test
    fun `refresh renews the token, and a stale refresh token falls back to false`() {
        client.login("98123456", "test-password-1".toCharArray())
        assertTrue(client.refresh())
        assertEquals(1, samad.refreshes.get())

        // A second client's login replaces the fake's refresh token, so ours is stale now.
        SamadClient(baseUrl = samad.baseUrl).login("98123456", "test-password-1".toCharArray())
        assertFalse(client.refresh())
    }

    @Test
    fun `an ERROR reply is reported with Samad's message`() {
        val reply = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"code":400,"type":"ERROR","messageFa":"زمان رزرو تمام شده","payload":null}""",
        )
        val error = assertFailsWith<SamadHttpException> { reply.samadPayload() }
        assertTrue("زمان رزرو تمام شده" in error.message.orEmpty())
    }

    @Test
    fun `the menu source logs in once, and renews an expired session by itself`() = runTest {
        samad.days[24] = setOf(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 22))
        val source = SamadMenuSource(client, "98123456", "test-password-1".toCharArray())

        assertEquals(3, source.selfs().size)
        assertEquals(setOf(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 15)), source.daysWithFood(24, LocalDate.of(2026, 11, 14)))
        assertEquals(1, samad.logins.get())

        samad.expireAccessToken()
        assertEquals(3, source.selfs().size)
        assertEquals(1, samad.refreshes.get(), "renewed with the refresh token, not the password")
        assertEquals(1, samad.logins.get())
    }

    @Test
    fun `a cafeteria without a menu yet is an empty week, not a failure`() = runTest {
        // FakeSamad answers like the real one: HTTP 400 "self has no program".
        val source = SamadMenuSource(client, "98123456", "test-password-1".toCharArray())
        val program = source.program(1, LocalDate.of(2026, 11, 14))
        assertEquals(emptySet(), program.foodDays)
        assertEquals(0, program.foods)
    }
}
