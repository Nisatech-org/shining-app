package app.shining.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuAlertTest {

    // Saturday 14 to Wednesday 18 November 2026.
    private val fullWeek = listOf("2026-11-14", "2026-11-15", "2026-11-16", "2026-11-17", "2026-11-18")

    private fun batch(vararg vendors: BatchVendor) = MenuBatch(weekStart = "2026-11-14", vendors = vendors.toList())

    private fun opened(key: String, name: String) = BatchVendor(key, name, fullWeek, firstAppearance = true)

    // The vendors and their names as the server sends them.
    private val self = opened("self", "سلف")
    private val kaleh = opened("kaleh", "کاله")
    private val fastfood = opened("fastfood", "فست‌فود شریف")
    private val cleanfood = opened("cleanfood", "کلین‌فود")
    private val kalana = opened("kalana", "کلانا")
    private val unifood = opened("unifood", "یونی‌فود")

    private fun titles(vararg vendors: BatchVendor, enabled: (String) -> Boolean = { true }) =
        menuAlerts(batch(*vendors), enabled).map { it.text.title }

    @Test
    fun `a vendor's week opening is one line`() {
        val part = menuAlerts(batch(self)) { true }.single()
        assertEquals("رزرو غذای سلف هفته بعد باز شد", part.text.title)
        assertNull("no second line", part.text.body)
    }

    @Test
    fun `vendors opening together share the line while it fits`() {
        assertEquals(listOf("رزرو غذای سلف و کاله هفته بعد باز شد"), titles(self, kaleh))
    }

    @Test
    fun `too many for one line are split over as few notifications as possible`() {
        val three = menuAlerts(batch(self, kaleh, kalana)) { true }
        assertEquals(2, three.size)
        assertEquals(listOf("self", "kaleh", "kalana").sorted(), three.flatMap { part -> part.vendors.map { it.key } }.sorted())
        three.forEach { assertTrue(it.text.title, it.text.title.length <= MAX_ALERT_TITLE) }

        // All six at once (rare): no line is cut off, each vendor once.
        val all = menuAlerts(batch(self, kaleh, fastfood, cleanfood, kalana, unifood)) { true }
        all.forEach { assertTrue("${it.text.title} (${it.text.title.length})", it.text.title.length <= MAX_ALERT_TITLE) }
        assertEquals(6, all.flatMap { it.vendors }.map { it.key }.toSet().size)
        assertEquals(6, all.sumOf { it.vendors.size })
        assertTrue("${all.size} notifications", all.size <= 4)
    }

    @Test
    fun `vendors the user turned off are left out`() {
        assertEquals(listOf("رزرو غذای یونی‌فود هفته بعد باز شد"), titles(self, unifood) { it != "self" })
        assertTrue(titles(self, unifood) { false }.isEmpty())
    }

    @Test
    fun `days added to a week already announced get their own short line`() {
        val more = BatchVendor("kalana", "کلانا", listOf("2026-11-18"), firstAppearance = false)
        assertEquals(listOf("رزرو غذای سلف هفته بعد باز شد", "رزرو روزهای تازه‌ی کلانا باز شد"), titles(self, more))
    }

    @Test
    fun `lists are joined the Persian way`() {
        assertEquals("الف", persianJoin(listOf("الف")))
        assertEquals("الف و ب", persianJoin(listOf("الف", "ب")))
        assertEquals("الف، ب و ج", persianJoin(listOf("الف", "ب", "ج")))
    }
}
