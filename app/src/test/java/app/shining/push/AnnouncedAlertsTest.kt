package app.shining.push

import app.shining.testing.FakeKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncedAlertsTest {

    private val store = FakeKeyValueStore()
    private val announced = AnnouncedAlerts(store)

    private fun batch(week: String = "2026-11-14", vararg vendors: BatchVendor) = MenuBatch(week, vendors.toList())
    private fun vendor(key: String, vararg days: String, first: Boolean = true) =
        BatchVendor(key, key, days.toList(), first)

    @Test
    fun `nothing announced means everything is new`() {
        val batch = batch(vendors = arrayOf(vendor("self", "2026-11-14", "2026-11-15")))
        assertEquals(batch, announced.unannounced(batch))
    }

    @Test
    fun `days already announced are left out, and the rest counts as added days`() {
        announced.markAnnounced(batch(vendors = arrayOf(vendor("kalana", "2026-11-14", "2026-11-15"))))

        val later = announced.unannounced(
            batch(vendors = arrayOf(vendor("kalana", "2026-11-14", "2026-11-15", "2026-11-16"), vendor("self", "2026-11-14"))),
        )
        val kalana = later.vendors.single { it.key == "kalana" }
        assertEquals(listOf("2026-11-16"), kalana.newDays)
        assertFalse(kalana.firstAppearance)
        assertTrue(later.vendors.single { it.key == "self" }.firstAppearance)
    }

    @Test
    fun `the same batch twice is announced once`() {
        val batch = batch(vendors = arrayOf(vendor("self", "2026-11-14")))
        announced.markAnnounced(announced.unannounced(batch))
        assertTrue(announced.unannounced(batch).vendors.isEmpty())
    }

    @Test
    fun `a new week starts empty`() {
        announced.markAnnounced(batch(vendors = arrayOf(vendor("self", "2026-11-14"))))
        val nextWeek = batch(week = "2026-11-21", vendors = arrayOf(vendor("self", "2026-11-21")))
        assertEquals(nextWeek, announced.unannounced(nextWeek))
    }

    @Test
    fun `the server status becomes a batch of everything published`() {
        val status = ServerStatus(
            week = "2026-11-14",
            vendors = listOf(
                VendorStatus("self", "سلف", listOf(1, 2), "partial", days = listOf("2026-11-15", "2026-11-14")),
                VendorStatus("kalana", "کلانا", listOf(24)),
            ),
        )
        val batch = status.publishedBatch()
        assertEquals("2026-11-14", batch.weekStart)
        assertEquals(listOf(BatchVendor("self", "سلف", listOf("2026-11-14", "2026-11-15"), true, listOf(1, 2))), batch.vendors)
    }
}
