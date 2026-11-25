package app.shining.push

import app.shining.testing.FakeKeyValueStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PlansAndFallbackTest {

    private val week = "2026-11-14"
    private fun vendor(key: String, vararg days: String, selfIds: List<Int> = emptyList()) =
        BatchVendor(key, key, days.toList(), true, selfIds)

    @Test
    fun `the plan opens the earliest new day of the vendors shown, highlighting their cafeterias`() {
        val fresh = MenuBatch(week, listOf(vendor("kalana", "2026-11-15", selfIds = listOf(24)), vendor("self", "2026-11-14", "2026-11-15", selfIds = listOf(1, 2))))
        val plan = planAnnouncement(fresh) { true }
        assertEquals(ReserveTarget("2026-11-14", setOf(24, 1, 2)), plan.parts.single().target)
        assertTrue(plan.skipped.isEmpty())
    }

    @Test
    fun `vendors turned off are skipped and do not move the day or the highlight`() {
        val fresh = MenuBatch(week, listOf(vendor("kalana", "2026-11-15", selfIds = listOf(24)), vendor("self", "2026-11-14", selfIds = listOf(1))))
        val plan = planAnnouncement(fresh) { it != "self" }
        assertEquals(ReserveTarget("2026-11-15", setOf(24)), plan.parts.single().target)
        assertEquals(listOf("self"), plan.skipped)
    }

    @Test
    fun `nothing to show when every vendor is off`() {
        val plan = planAnnouncement(MenuBatch(week, listOf(vendor("self", "2026-11-14")))) { false }
        assertTrue(plan.parts.isEmpty())
    }

    @Test
    fun `own cafeterias - the girls self counts as the main self, dorms share one switch`() {
        val result = ownSelfsBatch(
            weekStart = week,
            selfs = listOf(2 to "مرکزی - سلف دانشجویان خانم", 3 to "خوابگاه - طرشت 3", 8 to "خوابگاه - آزادی"),
            daysBySelf = mapOf(2 to listOf("2026-11-14", "2026-11-15"), 3 to listOf("2026-11-07", "2026-11-14"), 8 to emptyList()),
            vendors = DEFAULT_VENDORS,
        )
        assertEquals(listOf("self", OWN_SELFS_KEY), result.batch.vendors.map { it.key })
        val girls = result.batch.vendors.first()
        assertEquals("سلف", girls.name)
        assertEquals(listOf(2), girls.selfIds)
        val dorm = result.batch.vendors.last()
        assertEquals("days before the week are ignored", listOf("2026-11-14"), dorm.newDays)
        assertEquals("a dorm with nothing published is not included", listOf(3), dorm.selfIds)
        assertEquals(listOf("خوابگاه - طرشت 3", "خوابگاه - آزادی"), result.ownSelfNames)
    }

    @Test
    fun `the phone checks only the cafeterias the server can't see, even when the server is down`() {
        val account = listOf(1, 2, 20, 24, 3) // the main self, the girls' self, کاله, کلانا, a dorm
        val down = ServerStatus(week, lastCheckOk = false, coveredSelfIds = listOf(1, 20, 24), vendors = DEFAULT_VENDORS)

        assertEquals("the server can't see the girls' self", listOf(2, 3), MenuCheckWorker.selfsToCheck(account, down))
        assertEquals(
            "before the server says what it covers, every vendor's cafeteria is left to it",
            listOf(3),
            MenuCheckWorker.selfsToCheck(account, ServerStatus(week, vendors = DEFAULT_VENDORS)),
        )
        assertEquals("with no status at all, only dorms", listOf(3), MenuCheckWorker.selfsToCheck(account, null))
    }

    @Test
    fun `the status repository keeps the last good status when the server is down`() = runTest {
        val store = FakeKeyValueStore()
        var reply: ServerStatus? = ServerStatus(week, vendors = listOf(VendorStatus("self", "سلف", state = "complete")))
        val repository = ServerStatusRepository(
            api = object : ServerStatusApi {
                override suspend fun status(): ServerStatus = reply ?: throw IOException("unreachable")
            },
            store = store,
        )

        assertTrue(repository.fetch().isSuccess)
        assertTrue(repository.cachedAt() != null)
        reply = null
        assertTrue(repository.fetch().isFailure)
        assertEquals("complete", repository.cached()!!.vendors.single().state)
    }

    @Test
    fun `the announced record describes itself for the debug screen`() {
        val announced = AnnouncedAlerts(FakeKeyValueStore())
        assertEquals("nothing announced yet", announced.describe())
        announced.markAnnounced(MenuBatch(week, listOf(vendor("self", "2026-11-14", "2026-11-15"))))
        assertEquals("week 2026-11-14: self 11-14+11-15", announced.describe())
    }
}
