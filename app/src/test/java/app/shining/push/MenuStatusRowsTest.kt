package app.shining.push

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuStatusRowsTest {

    private val status = ServerStatus(
        week = "2026-11-14",
        vendors = listOf(
            // 10:33 UTC is 14:03 in Tehran; 2026-11-10 is a Tuesday.
            VendorStatus("self", "سلف", state = "complete", firstSeenAt = "2026-11-10T10:33:00.912Z", days = List(5) { "2026-11-1${4 + it}" }),
            VendorStatus("kalana", "کلانا", state = "partial", firstSeenAt = "2026-11-10T10:33:00Z", days = listOf("2026-11-14", "2026-11-15")),
            VendorStatus("unifood", "یونی‌فود", usualArrival = UsualArrival("WEDNESDAY", "10:00")),
            VendorStatus("kaleh", "کاله"),
        ),
    )

    @Test
    fun `each vendor says how far its menu is`() {
        val rows = menuStatusRows(status) { true }
        assertEquals(
            listOf(
                MenuStatusRow("سلف", MenuState.Published, "از سه‌شنبه ۱۴:۰۳"),
                MenuStatusRow("کلانا", MenuState.Partial, "۲ روز از ۵، از سه‌شنبه ۱۴:۰۳"),
                MenuStatusRow("یونی‌فود", MenuState.Waiting, "معمولاً چهارشنبه حدود ۱۰:۰۰"),
                MenuStatusRow("کاله", MenuState.Waiting, "هنوز نیومده"),
            ),
            rows,
        )
    }

    @Test
    fun `vendors the user turned off aren't shown`() {
        assertEquals(listOf("سلف"), menuStatusRows(status) { it == "self" }.map { it.name })
    }

    @Test
    fun `times are shown in Tehran time`() {
        // 21:00 UTC on Friday 13 is 00:30 on Saturday 14 in Tehran.
        assertEquals("شنبه ۰۰:۳۰", tehranWeekdayTime("2026-11-13T21:00:00Z"))
        assertEquals(null, tehranWeekdayTime("not a time"))
    }
}
