package app.shining.server.watch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiffTest {

    private val now = tehran(10, 14)

    @Test
    fun `a vendor with days for the first time appears`() {
        val result = diffWeek(emptyMap(), mapOf("kalana" to setOf(nov(14), nov(15))), now)

        assertEquals(listOf(MenuEvent("kalana", setOf(nov(14), nov(15)), true, now)), result.events)
        assertEquals(VendorWeek(setOf(nov(14), nov(15)), now), result.vendors["kalana"])
    }

    @Test
    fun `new days on a known vendor are added days, and it keeps its first time`() {
        val before = mapOf("kalana" to VendorWeek(setOf(nov(14), nov(15)), tehran(9, 10)))
        val result = diffWeek(before, mapOf("kalana" to FULL_WEEK), now)

        assertEquals(listOf(MenuEvent("kalana", setOf(nov(16), nov(17), nov(18)), false, now)), result.events)
        assertEquals(VendorWeek(FULL_WEEK, tehran(9, 10)), result.vendors["kalana"])
    }

    @Test
    fun `the same days again are no event`() {
        val before = mapOf("self" to VendorWeek(FULL_WEEK, tehran(9, 10)))
        assertTrue(diffWeek(before, mapOf("self" to FULL_WEEK), now).events.isEmpty())
    }

    @Test
    fun `days never disappear when Samad returns less`() {
        val before = mapOf("self" to VendorWeek(FULL_WEEK, tehran(9, 10)))
        val result = diffWeek(before, mapOf("self" to setOf(nov(14)), "kaleh" to emptySet()), now)

        assertTrue(result.events.isEmpty())
        assertEquals(FULL_WEEK, result.vendors.getValue("self").days)
        assertTrue("kaleh" !in result.vendors)
    }
}
