package app.shining.server.watch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BundleTest {

    private fun week(vararg days: Int) = VendorWeek(days.map(::nov).toSet(), tehran(10, 12))

    @Test
    fun `days opening within twenty minutes of the first share its alert`() {
        val pending = listOf(
            MenuEvent("self", setOf(nov(14)), true, tehran(10, 12, 45)),
            MenuEvent("self", setOf(nov(15), nov(16)), false, tehran(10, 13, 0)),
        )
        assertEquals(tehran(10, 13, 5), bundleReadyAt(pending, mapOf("self" to week(14, 15, 16)), WATCHED))
    }

    @Test
    fun `once every vendor waiting has its whole week, there's nothing to wait for`() {
        val pending = listOf(
            MenuEvent("self", setOf(nov(17), nov(18)), false, tehran(10, 13, 15)),
            MenuEvent("kaleh", FULL_WEEK, true, tehran(10, 13, 10)),
        )
        val vendors = mapOf("self" to week(14, 15, 16, 17, 18), "kaleh" to week(14, 15, 16, 17, 18))
        assertEquals(tehran(10, 13, 15), bundleReadyAt(pending, vendors, WATCHED))
        // One of them still missing days: the window applies to both.
        assertEquals(tehran(10, 13, 30), bundleReadyAt(pending, vendors + ("kaleh" to week(14, 15)), WATCHED))
    }

    @Test
    fun `nothing pending is never ready`() {
        assertNull(bundleReadyAt(emptyList(), emptyMap(), WATCHED))
    }

    @Test
    fun `a menu that opens at night goes out right away, not in the morning`() {
        val pending = listOf(MenuEvent("self", setOf(nov(14)), true, tehran(10, 2, 25)))
        assertEquals(tehran(10, 2, 45), bundleReadyAt(pending, mapOf("self" to week(14)), WATCHED))
    }

    @Test
    fun `a batch merges each vendor's events and keeps the vendor order`() {
        val pending = listOf(
            MenuEvent("kaleh", setOf(nov(14), nov(15)), true, tehran(10, 14, 0)),
            MenuEvent("self", FULL_WEEK, true, tehran(10, 14, 2)),
            MenuEvent("kaleh", setOf(nov(16)), false, tehran(10, 14, 8)),
        )
        val batch = toBatch(WATCHED, pending)

        assertEquals(WATCHED, batch.weekStart)
        assertEquals(listOf("self", "kaleh"), batch.vendors.map { it.key })
        val kaleh = batch.vendors[1]
        assertEquals("کاله", kaleh.name)
        assertEquals(listOf(nov(14), nov(15), nov(16)), kaleh.newDays)
        assertTrue(kaleh.firstAppearance)
        assertEquals(listOf(20, 21), kaleh.selfIds)
    }
}
