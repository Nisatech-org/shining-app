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

    private fun batch(vararg days: String) =
        MenuBatch(week, listOf(BatchVendor("self", "سلف", days.toList(), true, listOf(1))))

    /** One phone check: what it would announce. */
    private fun check(now: Long, vararg days: String): List<String> {
        val all = batch(*days)
        val ready = window.ready(all, announced.unannounced(all), now)
        val fresh = announced.unannounced(ready)
        if (fresh.vendors.isNotEmpty()) announced.markAnnounced(fresh)
        return fresh.vendors.flatMap { it.newDays }
    }

    @Test
    fun `days found at the next check share the alert, later ones get their own`() {
        // Like سلف on 2026-11-16, as the phone's 15-minute checks saw it.
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
    fun `a whole week found at once goes out at once`() {
        assertEquals(5, check(5_000_000L, "2026-11-21", "2026-11-22", "2026-11-23", "2026-11-24", "2026-11-25").size)
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
