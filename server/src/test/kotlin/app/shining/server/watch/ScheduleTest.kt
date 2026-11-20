package app.shining.server.watch

import app.shining.server.discovery.TEHRAN
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleTest {

    private val allVendors = setOf("self", "kaleh", "kalana")

    /**
     * Kalana published at 14:00 on the Tuesday before each of the last three weeks (the Tuesday
     * before a Saturday is four days earlier).
     */
    private val kalanaOnTuesdays = listOf(nov(7), nov(7).minusWeeks(1), nov(7).minusWeeks(2)).map { week ->
        ArrivalSample("kalana", week, week.minusDays(4).atTime(14, 0).atZone(TEHRAN).toInstant())
    }

    @Test
    fun `before Sunday it still checks hourly, and the fast pace starts at midnight`() {
        assertEquals(tehran(7, 11), nextCheckAt(tehran(7, 10), WatchState(), allVendors))
        assertEquals(tehran(8, 0), nextCheckAt(tehran(7, 23, 30), WatchState(), allVendors))
    }

    @Test
    fun `Sunday already checks every five minutes`() {
        val now = tehran(8, 16)
        assertEquals(now + Duration.ofMinutes(5), nextCheckAt(now, WatchState(), allVendors))
    }

    @Test
    fun `without a known pattern it checks every five minutes`() {
        val now = tehran(9, 10)
        assertEquals(now + Duration.ofMinutes(5), nextCheckAt(now, WatchState(), allVendors))
    }

    @Test
    fun `when every vendor has its whole week it keeps checking hourly`() {
        val state = WatchState(
            weekStart = WATCHED,
            vendors = allVendors.associateWith { VendorWeek(FULL_WEEK, tehran(9, 1)) },
        )
        assertEquals(tehran(10, 10), nextCheckAt(tehran(10, 9), state, allVendors))
    }

    @Test
    fun `a vendor's usual time is the median of its weeks`() {
        assertEquals(tehran(10, 14), usualArrival(kalanaOnTuesdays, "kalana", WATCHED))
        assertNull(usualArrival(kalanaOnTuesdays.take(2), "kalana", WATCHED))
    }

    @Test
    fun `inside the learned window it checks every five minutes`() {
        val state = WatchState(history = kalanaOnTuesdays)
        val now = tehran(10, 13)
        assertEquals(now + Duration.ofMinutes(5), nextCheckAt(now, state, setOf("kalana")))
    }

    @Test
    fun `outside the window it checks hourly, or when the window opens if that's sooner`() {
        val state = WatchState(history = kalanaOnTuesdays)
        assertEquals(tehran(9, 11), nextCheckAt(tehran(9, 10), state, setOf("kalana")))
        assertEquals(tehran(10, 12), nextCheckAt(tehran(10, 11, 30), state, setOf("kalana")))
        assertEquals(tehran(10, 20), nextCheckAt(tehran(10, 19), state, setOf("kalana")))
    }

    @Test
    fun `a vendor without a pattern keeps the fast pace even if others have one`() {
        val state = WatchState(history = kalanaOnTuesdays)
        val now = tehran(9, 10)
        assertEquals(now + Duration.ofMinutes(5), nextCheckAt(now, state, setOf("kalana", "self")))
    }

    @Test
    fun `history keeps the last eight weeks of each vendor`() {
        val many = (0L until 10L).map { ArrivalSample("self", WATCHED.minusWeeks(it), tehran(10, 14)) }
        val kept = trimHistory(many + kalanaOnTuesdays)
        assertEquals(8, kept.count { it.vendorKey == "self" })
        assertEquals(3, kept.count { it.vendorKey == "kalana" })
    }
}
