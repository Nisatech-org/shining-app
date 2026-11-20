package app.shining.server.watch

import app.shining.server.samad.SignInNeededException
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WatcherTest {

    private class FakeSource : MenuSource {
        val selfs = mutableListOf(SelfInfo(1, "مرکزی - سلف دانشجویان آقا"), SelfInfo(24, "کلانا"))
        val days = mutableMapOf<Int, Set<LocalDate>>()
        var failing = false
        var needsSignIn = false
        var selfsCalls = 0

        override suspend fun selfs(): List<SelfInfo> {
            selfsCalls++
            if (failing) throw IOException("connect timed out")
            if (needsSignIn) throw SignInNeededException("Samad needs a person to sign in (Captcha params is invalid)")
            return selfs
        }

        override suspend fun daysWithFood(selfId: Int, weekStart: LocalDate): Set<LocalDate> =
            days[selfId].orEmpty().filter { it >= weekStart && it < weekStart.plusDays(7) }.toSet()
    }

    private class FakeNotifier : Notifier {
        val batches = mutableListOf<MenuBatch>()
        val adminMessages = mutableListOf<String>()
        override suspend fun sendMenus(batch: MenuBatch) { batches += batch }
        override suspend fun alertAdmin(message: String) { adminMessages += message }
    }

    private val source = FakeSource()
    private val notifier = FakeNotifier()
    private val stateFile = File(createTempDirectory("shining-watcher").toFile(), "state.json")
    private var now: Instant = tehran(9, 0)

    private fun watcher() = Watcher(
        source = source,
        store = StateStore(stateFile),
        notifier = notifier,
        clock = { now },
        requestGap = Duration.ZERO,
    )

    @Test
    fun `the first check only records what's there`() = runTest {
        source.days[1] = FULL_WEEK
        val watcher = watcher()

        watcher.check()

        assertTrue(watcher.state.initialized)
        assertEquals(FULL_WEEK, watcher.state.vendors.getValue("self").days)
        assertTrue(watcher.state.pending.isEmpty())
        assertEquals(setOf(1, 24), watcher.state.coveredSelfIds)
        assertEquals(setOf("self", "kalana"), watcher.watchedVendorKeys)
    }

    @Test
    fun `days opening within twenty minutes share one alert, later ones get their own`() = runTest {
        val watcher = watcher()
        watcher.check()

        now = tehran(10, 12, 40)
        watcher.check()
        // Like سلف on 2026-11-16: Saturday, then Sunday and Monday, then the rest.
        now = tehran(10, 12, 45)
        source.days[24] = setOf(nov(14))
        watcher.check()
        now = tehran(10, 13, 0)
        source.days[24] = setOf(nov(14), nov(15), nov(16))
        watcher.check()
        watcher.flush()
        assertTrue(notifier.batches.isEmpty(), "still inside the twenty minutes")
        assertTrue(buildStatusDays(watcher).isEmpty(), "phones catching up from the status don't see held days either")

        now = tehran(10, 13, 5)
        watcher.flush()
        val first = notifier.batches.single().vendors.single()
        assertEquals("kalana", first.key)
        assertEquals(listOf(nov(14), nov(15), nov(16)), first.newDays)
        assertTrue(first.firstAppearance)

        // The rest completes the week: nothing more to wait for, so it goes out at once.
        now = tehran(10, 13, 15)
        source.days[24] = FULL_WEEK
        watcher.check()
        watcher.flush()
        val second = notifier.batches.last().vendors.single()
        assertEquals(2, notifier.batches.size)
        assertEquals(listOf(nov(17), nov(18)), second.newDays)
        assertEquals(false, second.firstAppearance)
        assertTrue(watcher.state.pending.isEmpty())
        assertEquals(FULL_WEEK.map { it.toString() }.sorted(), buildStatusDays(watcher))

        // The check five minutes before the first day makes its time precise enough to learn from.
        assertEquals(listOf(ArrivalSample("kalana", WATCHED, tehran(10, 12, 45))), watcher.state.history)
    }

    private fun buildStatusDays(watcher: Watcher): List<String> =
        app.shining.server.http.buildStatus(watcher.state, now).vendors.first { it.key == "kalana" }.days

    @Test
    fun `a day nothing follows goes out after twenty minutes, and the rest when it comes`() = runTest {
        val watcher = watcher()
        watcher.check()
        now = tehran(10, 14)
        source.days[24] = setOf(nov(14), nov(15))
        watcher.check()
        now = tehran(10, 14, 19)
        watcher.flush()
        assertTrue(notifier.batches.isEmpty())
        now = tehran(10, 14, 20)
        watcher.flush()
        assertEquals(listOf(nov(14), nov(15)), notifier.batches.single().vendors.single().newDays)

        now = tehran(11, 9)
        source.days[24] = FULL_WEEK
        watcher.check()
        watcher.flush()

        val added = notifier.batches.last().vendors.single()
        assertEquals(listOf(nov(16), nov(17), nov(18)), added.newDays)
        assertEquals(false, added.firstAppearance)
    }

    @Test
    fun `the admin is alerted once after three failures and again when it recovers`() = runTest {
        val watcher = watcher()
        source.failing = true
        repeat(4) {
            watcher.check()
            now += Duration.ofMinutes(5)
        }
        assertEquals(1, notifier.adminMessages.size)
        assertEquals(4, watcher.state.failures)

        source.failing = false
        watcher.check()
        assertEquals(2, notifier.adminMessages.size)
        assertEquals(0, watcher.state.failures)
        assertEquals(false, watcher.state.adminAlerted)
    }

    @Test
    fun `the admin is asked to sign in right away, with the page, and reminded twice a day`() = runTest {
        val watcher = watcher()
        source.needsSignIn = true
        watcher.check()
        assertEquals(1, notifier.adminMessages.size, "no waiting for three failures")
        assertTrue(SIGN_IN_PAGE in notifier.adminMessages.single(), notifier.adminMessages.single())
        assertTrue(watcher.state.failures > 0, "phones still see that the server can't check")

        repeat(10) {
            now += Duration.ofMinutes(5)
            watcher.check()
        }
        assertEquals(1, notifier.adminMessages.size, "not on every check, and not the three-failures alert either")

        now += SIGN_IN_REMINDER
        watcher.check()
        assertEquals(2, notifier.adminMessages.size, "a reminder while it's still needed")

        source.needsSignIn = false
        now += Duration.ofMinutes(5)
        watcher.check()
        assertEquals(3, notifier.adminMessages.size)
        assertTrue("دوباره به سماد وصله" in notifier.adminMessages.last())
        assertEquals(0, watcher.state.failures)
    }

    @Test
    fun `the admin is warned a week and a day before the session runs out, once each`() = runTest {
        var expires: Instant? = now + Duration.ofDays(30)
        val watcher = Watcher(source, StateStore(stateFile), notifier, clock = { now }, requestGap = Duration.ZERO, sessionExpiry = { expires })
        watcher.check()
        assertTrue(notifier.adminMessages.isEmpty(), "a month left needs nothing")

        now = expires!! - Duration.ofDays(6)
        watcher.check()
        now += Duration.ofHours(1)
        watcher.check()
        assertEquals(1, notifier.adminMessages.size)
        assertTrue("6 روز دیگه" in notifier.adminMessages.single(), notifier.adminMessages.single())
        assertTrue(SIGN_IN_PAGE in notifier.adminMessages.single())

        now = expires!! - Duration.ofHours(20)
        watcher.check()
        watcher.check()
        assertEquals(2, notifier.adminMessages.size)
        assertTrue("کمتر از یک روز" in notifier.adminMessages.last())

        // The admin signed in again: a new session with its own end, so nothing until that one nears its end.
        expires = now + Duration.ofDays(360)
        watcher.check()
        assertEquals(2, notifier.adminMessages.size)
    }

    @Test
    fun `the session's end is read from the token, and anything else is unknown`() {
        fun jwt(claims: String) = "eyJhbGciOiJIUzI1NiJ9." +
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toByteArray()) + ".c2lnbmF0dXJl"
        assertEquals(Instant.parse("2027-11-03T19:17:32Z"), jwtExpiry(jwt("""{"user_name":"x","exp":1825269452}""")))
        assertEquals(null, jwtExpiry(jwt("""{"user_name":"x"}""")))
        assertEquals(null, jwtExpiry("fake-refresh-1-f6g7h8i9"))
        assertEquals(null, jwtExpiry("a.b.c"))
    }

    @Test
    fun `a new week starts over and keeps the history`() = runTest {
        val watcher = watcher()
        watcher.check()
        now = tehran(10, 13, 55)
        watcher.check()
        now = tehran(10, 14)
        source.days[24] = FULL_WEEK
        watcher.check()

        now = tehran(16, 0)
        source.days[24] = (21..25).map(::nov).toSet()
        watcher.check()

        assertEquals(nov(21), watcher.state.weekStart)
        assertEquals((21..25).map(::nov).toSet(), watcher.state.vendors.getValue("kalana").days)
        assertEquals(1, watcher.state.history.size)
        // The week before's unsent event was dropped; this week's is pending.
        assertEquals(listOf(nov(21)), watcher.state.pending.map { it.newDays.min() }.distinct())
    }

    @Test
    fun `a restart picks up the saved state`() = runTest {
        source.days[1] = FULL_WEEK
        val first = watcher()
        first.check()

        assertEquals(first.state, watcher().state)
    }
}
