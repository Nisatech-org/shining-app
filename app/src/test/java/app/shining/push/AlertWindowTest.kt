package app.shining.push

import app.shining.testing.FakeKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertWindowTest {

    private val week = "2026-11-21"
    private val minute = 60_000L
    private val store = FakeKeyValueStore()
    private val window = AlertWindow(store)
    private val announced = AnnouncedAlerts(store)

    private fun batch(key: String, vararg days: String) =
        MenuBatch(week, listOf(BatchVendor(key, if (key == "self") "سلف" else "خوابگاه", days.toList(), true, listOf(1))))

    /** One phone check: what it would announce, one list of days per notification. */
    private fun check(now: Long, vararg days: String, key: String = OWN_SELFS_KEY): List<String> {
        val all = batch(key, *days)
        val ready = window.ready(all, announced.unannounced(all), now)
        val fresh = announced.unannounced(ready)
        if (fresh.vendors.isNotEmpty()) announced.markAnnounced(fresh)
        return fresh.vendors.flatMap { it.newDays }
    }

    @Test
    fun `days found at the next check share the alert, later ones get their own`() {
        // A dorm opening like سلف did on 2026-11-16, as the phone's 15-minute checks saw it.
        val start = 1_000_000L
        assertTrue("held for the next check", check(start, "2026-11-21").isEmpty())
        assertEquals(
            listOf("2026-11-21", "2026-11-22", "2026-11-23"),
            check(start + 17 * minute, "2026-11-21", "2026-11-22", "2026-11-23"),
        )
        // The rest completes the week: out at once, as new days.
        assertEquals(
            listOf("2026-11-24", "2026-11-25"),
            check(start + 32 * minute, "2026-11-21", "2026-11-22", "2026-11-23", "2026-11-24", "2026-11-25"),
        )
        assertTrue("nothing new, nothing announced", check(start + 47 * minute, "2026-11-21", "2026-11-22", "2026-11-23", "2026-11-24", "2026-11-25").isEmpty())
    }

    @Test
    fun `سلف opening day by day gets one alert, when its whole week is open`() {
        // The boys' سلف on 2026-11-16, which only phones see: three checks, one alert.
        val start = 1_000_000L
        assertTrue(check(start, "2026-11-21", key = "self").isEmpty())
        assertTrue("not after the short window", check(start + 17 * minute, "2026-11-21", "2026-11-22", "2026-11-23", key = "self").isEmpty())
        assertEquals(
            listOf("2026-11-21", "2026-11-22", "2026-11-23", "2026-11-24", "2026-11-25"),
            check(start + 32 * minute, "2026-11-21", "2026-11-22", "2026-11-23", "2026-11-24", "2026-11-25", key = "self"),
        )
    }

    @Test
    fun `a سلف week that never fills up goes out an hour after its last new day`() {
        // Wednesday is a holiday.
        val start = 1_000_000L
        val days = arrayOf("2026-11-21", "2026-11-22", "2026-11-23")
        assertTrue(check(start, "2026-11-21", key = "self").isEmpty())
        assertTrue(check(start + 15 * minute, *days, key = "self").isEmpty())
        assertTrue(check(start + 45 * minute, *days, "2026-11-24", key = "self").isEmpty())
        assertTrue("an hour after Tuesday turned up, not after the first day", check(start + 100 * minute, *days, "2026-11-24", key = "self").isEmpty())
        assertEquals(
            listOf(*days, "2026-11-24"),
            check(start + 105 * minute, *days, "2026-11-24", key = "self"),
        )
    }

    @Test
    fun `a whole week found at once goes out at once`() {
        assertEquals(5, check(5_000_000L, "2026-11-21", "2026-11-22", "2026-11-23", "2026-11-24", "2026-11-25").size)
        assertEquals(5, check(5_000_000L, "2026-11-21", "2026-11-22", "2026-11-23", "2026-11-24", "2026-11-25", key = "self").size)
    }

    @Test
    fun `the whole week is Saturday to Wednesday, whatever else is there`() {
        assertTrue(isWholeWeek(week, listOf("2026-11-25", "2026-11-21", "2026-11-22", "2026-11-23", "2026-11-24", "2026-11-26")))
        assertTrue(isWholeWeek(week, listOf("2026-11-21 00:00:00", "2026-11-22", "2026-11-23", "2026-11-24", "2026-11-25")))
        assertFalse(isWholeWeek(week, listOf("2026-11-21", "2026-11-22", "2026-11-23", "2026-11-24")))
        // Across a month end: Saturday 28 November to Wednesday 2 December.
        assertTrue(isWholeWeek("2026-11-28", listOf("2026-11-28", "2026-11-29", "2026-11-30", "2026-12-01", "2026-12-02")))
        assertFalse(isWholeWeek("not a date", listOf("2026-11-21")))
    }
}
