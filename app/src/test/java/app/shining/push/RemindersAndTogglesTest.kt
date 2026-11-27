package app.shining.push

import app.shining.order.ui.component.dayIndexFor
import app.shining.testing.FakeKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RemindersAndTogglesTest {

    @Test
    fun `a reminder is skipped only when it surely isn't needed`() {
        assertFalse("menus not out yet", shouldRemind(menusUp = false, reservedDays = 0))
        assertFalse("every day reserved", shouldRemind(menusUp = true, reservedDays = 5))
        assertTrue("days left", shouldRemind(menusUp = true, reservedDays = 3))
        assertTrue("server unknown, days left", shouldRemind(menusUp = null, reservedDays = 2))
        assertTrue("nothing could be checked", shouldRemind(menusUp = null, reservedDays = null))
    }

    @Test
    fun `settings list the server's vendors, or the built-in ones before it loads`() {
        val settings = PushSettings(FakeKeyValueStore())
        settings.setVendorEnabled("kaleh", false)

        val builtIn = menuVendorToggles(null, settings, "خوابگاه")
        assertEquals(DEFAULT_VENDORS.map { it.key }, builtIn.map { it.key })
        assertFalse(builtIn.single { it.key == "kaleh" }.enabled)

        val fromServer = menuVendorToggles(ServerStatus("2026-11-14", vendors = listOf(VendorStatus("self", "سلف"))), settings, "خوابگاه")
        assertEquals(listOf("self"), fromServer.map { it.key })
    }

    @Test
    fun `the own-cafeterias switch appears once the phone found any`() {
        val settings = PushSettings(FakeKeyValueStore())
        assertTrue(menuVendorToggles(null, settings, "خوابگاه").none { it.key == OWN_SELFS_KEY })

        settings.ownSelfNames = listOf("خوابگاه - طرشت ۳")
        val own = menuVendorToggles(null, settings, "خوابگاه").last()
        assertEquals(MenuVendorToggle(OWN_SELFS_KEY, "خوابگاه", true), own)
    }

    @Test
    fun `reserve opens on the notification's day, or the first day`() {
        val dates = listOf("2026-11-14 00:00:00", "2026-11-15 00:00:00", "2026-11-16 00:00:00")
        assertEquals(1, dayIndexFor(dates, "2026-11-15"))
        assertEquals(0, dayIndexFor(dates, "2026-11-30"))
        assertEquals(0, dayIndexFor(dates, null))
    }

    @Test
    fun `the fallback check runs Sunday to Wednesday`() {
        fun day(dayOfWeek: Int) = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran")).apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
        }
        assertTrue(MenuCheckWorker.isCheckDay(day(Calendar.SUNDAY)))
        assertTrue(MenuCheckWorker.isCheckDay(day(Calendar.MONDAY)))
        assertTrue(MenuCheckWorker.isCheckDay(day(Calendar.WEDNESDAY)))
        assertFalse(MenuCheckWorker.isCheckDay(day(Calendar.THURSDAY)))
        assertFalse(MenuCheckWorker.isCheckDay(day(Calendar.SATURDAY)))
    }

    @Test
    fun `two of the user's own cafeterias become one line`() {
        val merged = MenuCheckWorker.mergeByKey(
            listOf(
                BatchVendor(OWN_SELFS_KEY, "طرشت ۳", listOf("2026-11-14"), true, listOf(3)),
                BatchVendor(OWN_SELFS_KEY, "آزادی", listOf("2026-11-15", "2026-11-14"), true, listOf(8)),
            ),
        )
        assertEquals(1, merged.size)
        assertEquals(listOf("2026-11-14", "2026-11-15"), merged.single().newDays)
        assertEquals(listOf(3, 8), merged.single().selfIds)
    }
}
