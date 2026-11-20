package app.shining.server.discovery

import app.shining.server.toEnglishDigits
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class WeeksTest {

    @Test
    fun `a Tuesday belongs to the week that started on the Saturday before`() {
        // 2026-11-03 is a Tuesday.
        assertEquals(
            listOf(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 7)),
            weekStarts(LocalDate.of(2026, 11, 3)),
        )
    }

    @Test
    fun `a Saturday starts its own week`() {
        assertEquals(
            listOf(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 14)),
            weekStarts(LocalDate.of(2026, 11, 7)),
        )
    }

    @Test
    fun `a Friday is the last day of its week`() {
        assertEquals(LocalDate.of(2026, 10, 31), weekStarts(LocalDate.of(2026, 11, 6)).first())
    }

    @Test
    fun `week parameter has the time Samad expects`() {
        assertEquals("2026-11-07 00:00:00", samadWeekParam(LocalDate.of(2026, 11, 7)))
    }

    @Test
    fun `Persian digits become English`() {
        assertEquals("98123456", toEnglishDigits("۹۸۱۲۳۴۵۶"))
    }
}
