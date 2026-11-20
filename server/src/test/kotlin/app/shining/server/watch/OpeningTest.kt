package app.shining.server.watch

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A menu that can be seen before it can be reserved, and the archive of every menu version. */
class OpeningTest {

    /** Menus per self: day → whether it can be reserved. */
    private class FakeSource : MenuSource {
        val selfs = listOf(SelfInfo(1, "مرکزی - سلف دانشجویان آقا"), SelfInfo(20, "کاله پردیس مرکزی(بیرون بر)"))
        val menus = mutableMapOf<Int, Map<LocalDate, Boolean>>()
        val broken = mutableSetOf<Int>()

        override suspend fun selfs() = selfs
        override suspend fun daysWithFood(selfId: Int, weekStart: LocalDate) = menus[selfId].orEmpty().keys
        override suspend fun program(selfId: Int, weekStart: LocalDate): SelfProgram {
            if (selfId in broken) throw java.io.IOException("HTTP 500 from Samad")
            val menu = menus[selfId].orEmpty()
            return SelfProgram(
                foodDays = menu.keys,
                openDays = menu.filterValues { it }.keys,
                foods = menu.size,
                signature = menu.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" },
            )
        }
    }

    private class FakeNotifier : Notifier {
        val batches = mutableListOf<MenuBatch>()
        val adminMessages = mutableListOf<String>()
        override suspend fun sendMenus(batch: MenuBatch) { batches += batch }
        override suspend fun alertAdmin(message: String) { adminMessages += message }
    }

    private val source = FakeSource()
    private val notifier = FakeNotifier()
    private val dir = createTempDirectory("shining-opening").toFile()
    private var now: Instant = tehran(8, 16)

    private fun watcher(announceStart: Boolean = false) = Watcher(
        source = source,
        store = StateStore(File(dir, "state.json")),
        notifier = notifier,
        clock = { now },
        requestGap = Duration.ZERO,
        archive = MenuArchive(File(dir, "archive")),
        announceStart = announceStart,
    )

    private fun week(open: Boolean) = FULL_WEEK.associateWith { open }

    @Test
    fun `a menu seen before it opens is not used up, users are alerted when it opens`() = runTest {
        val watcher = watcher()
        // Sunday: deployed, nothing yet.
        watcher.check()

        // Monday 10:00: سلف's menu shows up, but nothing can be reserved yet.
        now = tehran(9, 10, 0)
        source.menus[1] = week(open = false)
        watcher.check()
        assertTrue(watcher.state.pending.isEmpty(), "no user alert while it can't be reserved")
        assertEquals(1, notifier.adminMessages.size)
        assertTrue("سلف" in notifier.adminMessages.single() && "هنوز رزرو نمی‌شه" in notifier.adminMessages.single())
        assertEquals(mapOf("self" to 5), watcher.state.recentChecks.last().foodDays)
        assertEquals(emptyMap(), watcher.state.recentChecks.last().days)

        // Still closed five minutes later: the admin isn't told twice.
        now = tehran(9, 10, 5)
        watcher.check()
        assertEquals(1, notifier.adminMessages.size)

        // 10:10: reservations open. That's the alert users get.
        now = tehran(9, 10, 10)
        source.menus[1] = week(open = true)
        watcher.check()
        now = tehran(9, 10, 20)
        watcher.flush()
        val self = notifier.batches.single().vendors.single()
        assertEquals("self", self.key)
        assertTrue(self.firstAppearance)
        assertEquals(FULL_WEEK.sorted(), self.newDays)
        assertEquals(listOf(ArrivalSample("self", WATCHED, tehran(9, 10, 10))), watcher.state.history, "learns when it opened")
    }

    @Test
    fun `a menu already visible but closed at the very first check still alerts when it opens`() = runTest {
        source.menus[1] = week(open = false)
        val watcher = watcher()
        watcher.check()
        assertTrue(watcher.state.initialized)
        assertTrue(watcher.state.vendors["self"]?.days.isNullOrEmpty())

        now = tehran(9, 9, 0)
        source.menus[1] = week(open = true)
        watcher.check()
        assertEquals(listOf("self"), watcher.state.pending.map { it.vendorKey })
    }

    @Test
    fun `a day stays announced once open, even if it closes later in the week`() = runTest {
        val watcher = watcher()
        watcher.check()
        now = tehran(9, 10)
        source.menus[1] = week(open = true)
        watcher.check()
        now = tehran(9, 10, 10)
        watcher.flush()

        // Wednesday evening: reservations for next week close.
        now = tehran(11, 20)
        source.menus[1] = week(open = false)
        watcher.check()
        assertEquals(FULL_WEEK, watcher.state.vendors.getValue("self").days)
        assertTrue(watcher.state.pending.isEmpty())
        assertEquals(0, notifier.adminMessages.size, "not 'visible but closed': it was open before")
    }

    @Test
    fun `after a start the admin gets one message saying the server sees Samad`() = runTest {
        val watcher = watcher(announceStart = true)
        watcher.check()
        now = tehran(8, 16, 5)
        watcher.check()
        val message = notifier.adminMessages.single()
        assertTrue("به سماد وصله" in message && "2026-11-14" in message && "اولین بررسی" in message)
    }

    @Test
    fun `the archive keeps a file per menu change, not per check`() = runTest {
        val watcher = watcher()
        watcher.check()
        val archive = File(dir, "archive/2026-11-14")
        assertFalse(archive.exists(), "empty menus are not archived")

        now = tehran(9, 10)
        source.menus[1] = week(open = false)
        watcher.check()
        now = tehran(9, 10, 5)
        watcher.check()
        now = tehran(9, 10, 10)
        source.menus[1] = week(open = true)
        watcher.check()

        val files = archive.listFiles().orEmpty().map { it.name }.sorted()
        assertEquals(listOf("20261109-100000-self1.json", "20261109-101000-self1.json"), files)
        val last = Json.parseToJsonElement(File(archive, files.last()).readText()).jsonObject
        assertEquals("1", last["selfId"]!!.jsonPrimitive.content)
        assertEquals(5, last["openDays"]!!.jsonArray.size)
    }

    @Test
    fun `Samad's menu is read the way the server needs it`() {
        // Shaped like a real programs/v2 reply (discovery, 2026-11-03), trimmed.
        val payload = Json.parseToJsonElement(
            """
            {"selfWeekPrograms":[
              [{"programId":1,"date":"2026-11-14","foodId":1,"foodName":"ماکارونی","foodTypeId":597,"mealTypeId":1,"price":350000,"reserveRuleViolated":false,
                "programFoodTypes":[{"foodList":[{"id":1,"averageScore":3.3,"voteCount":6311}]}]},
               {"programId":2,"date":"2026-11-14","foodId":2,"foodName":"قیمه","foodTypeId":644,"mealTypeId":1,"price":500000,"reserveRuleViolated":true}],
              [{"programId":3,"date":"2026-11-15","foodId":3,"foodName":"کباب","foodTypeId":644,"mealTypeId":1,"price":500000,"reserveRuleViolated":true}],
              [{"programId":4,"date":"2026-11-16","foodId":4,"foodName":"عدسی","foodTypeId":597,"mealTypeId":1,"price":350000}]
            ],"mealTypes":[],"userWeekReserves":[{"id":9}],"userId":42}
            """,
        )
        val program = programFrom(payload)
        assertEquals(setOf(nov(14), nov(15), nov(16)), program.foodDays)
        assertEquals(setOf(nov(14), nov(16)), program.openDays, "a day opens with any reservable food; a missing flag counts as open")
        assertEquals(4, program.foods)
        val raw = program.raw as JsonObject
        assertNull(raw["userWeekReserves"])
        assertNull(raw["userId"])
        assertNotNull(raw["selfWeekPrograms"])

        // New votes don't make it a new menu; a food becoming reservable does.
        val votes = programFrom(Json.parseToJsonElement(payload.toString().replace("6311", "6400")))
        assertEquals(program.signature, votes.signature)
        val opened = programFrom(Json.parseToJsonElement(payload.toString().replace("\"reserveRuleViolated\":true", "\"reserveRuleViolated\":false")))
        assertTrue(program.signature != opened.signature)
        assertEquals(setOf(nov(14), nov(15), nov(16)), opened.openDays)
    }

    @Test
    fun `one broken cafeteria doesn't hold up سلف's alert`() = runTest {
        val watcher = watcher()
        watcher.check()
        now = tehran(9, 10)
        source.broken += 20
        source.menus[1] = week(open = true)
        watcher.check()
        assertEquals(listOf("self"), watcher.state.pending.map { it.vendorKey })
        assertTrue(watcher.state.recentChecks.last().ok)

        // Everything failing is still a failed check, so admins hear about it.
        source.broken += 1
        now = tehran(9, 10, 5)
        watcher.check()
        assertFalse(watcher.state.recentChecks.last().ok)
        assertEquals(1, watcher.state.failures)
    }

    @Test
    fun `an empty reply has no days`() {
        val program = programFrom(Json.parseToJsonElement("""{"selfWeekPrograms":[],"mealTypes":[]}"""))
        assertTrue(program.foodDays.isEmpty())
        assertTrue(program.openDays.isEmpty())
        assertEquals(0, program.foods)
    }
}
