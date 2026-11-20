package app.shining.server.watch

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class StateStoreTest {

    private val file = File(createTempDirectory("shining-store").toFile(), "data/state.json")

    @Test
    fun `no file means a fresh state`() {
        assertEquals(WatchState(), StateStore(file).load())
    }

    @Test
    fun `a saved state loads back the same`() {
        val state = WatchState(
            initialized = true,
            weekStart = WATCHED,
            vendors = mapOf("self" to VendorWeek(FULL_WEEK, tehran(9, 0))),
            pending = listOf(MenuEvent("kalana", setOf(nov(14)), true, tehran(10, 14))),
            history = listOf(ArrivalSample("kalana", WATCHED, tehran(10, 14))),
            coveredSelfIds = setOf(1, 24),
            lastSuccessAt = tehran(10, 14),
            failures = 2,
        )
        StateStore(file).save(state)
        assertEquals(state, StateStore(file).load())
    }
}
