package app.shining.server.vendors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VendorRulesTest {

    /** The cafeterias Samad returned to the discovery tool on 2026-11-03. */
    private val discovered = mapOf(
        22 to "فست‌فود شریف",
        20 to "کاله پردیس مرکزی(بیرون بر)",
        24 to "کلانا",
        21 to "کاله پردیس شمالی(بیرون بر)",
        23 to "کلین‌فود",
        1 to "مرکزی - سلف دانشجویان آقا",
        25 to "یونی‌فود",
    )

    @Test
    fun `every discovered cafeteria maps to its vendor`() {
        val vendors = discovered.mapValues { (id, name) -> vendorFor(id, name)?.key }
        assertEquals(
            mapOf(
                22 to "fastfood", 20 to "kaleh", 24 to "kalana", 21 to "kaleh",
                23 to "cleanfood", 1 to "self", 25 to "unifood",
            ),
            vendors,
        )
    }

    @Test
    fun `a renumbered cafeteria is still found by its name`() {
        assertEquals("kalana", vendorFor(99, "کلانا")?.key)
        assertEquals("cleanfood", vendorFor(98, "کلین فود")?.key)
        assertEquals("self", vendorFor(97, "مرکزی - سلف دانشجویان خانم")?.key)
    }

    @Test
    fun `a dorm cafeteria has no vendor`() {
        assertNull(vendorFor(11, "خوابگاه - شهید احمدی روشن"))
    }
}
