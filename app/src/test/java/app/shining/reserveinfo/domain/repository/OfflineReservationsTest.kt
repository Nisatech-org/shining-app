package app.shining.reserveinfo.domain.repository

import app.shining.domain.model.LoadableData
import app.shining.reserveinfo.api.dto.ReservationInformationDto
import app.shining.reserveinfo.api.dto.ReserveDto
import app.shining.reserveinfo.api.dto.UserMealTypeDto
import app.shining.reserveinfo.api.dto.WeekDayDto
import app.shining.reserveinfo.domain.usecase.GetForgetCodeMapCacheUseCase
import app.shining.reserveinfo.domain.usecase.GetForgetCodeUseCase
import app.shining.reserveinfo.domain.usecase.LoadReservationsUseCase
import app.shining.reserveinfo.ui.model.ReservationInfoScreenUIModel
import app.shining.reserveinfo.ui.model.savedNoticeText
import app.shining.reserveinfo.ui.model.timeAgo
import app.shining.reserveinfo.ui.viewmodel.ReservationInfoViewModel
import app.shining.testing.FakeKeyValueStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Loading reservations from Samad, saving them, and showing the saved copy without internet. */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineReservationsTest {

    private val dispatcher = StandardTestDispatcher()
    private val api = FakeReserveInfoApi()
    private val storage = FakeKeyValueStore()
    private var active: String? = "401106789"
    private var now = 1_000_000_000_000L

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.repository() = ReservationInfoRepositoryImplementation(
        api = api,
        forgetCodeDao = FakeForgetCodeDao(),
        storage = storage,
        json = Json { ignoreUnknownKeys = true },
        activeAccountId = { active },
        now = { now },
        today = { "2026-11-01" },
        loadScope = this as CoroutineScope,
    )

    private fun TestScope.useCase(repository: ReservationInfoRepository = repository()) =
        LoadReservationsUseCase(repository, nextWeekStart = { NEXT_WEEK })

    private fun TestScope.viewModel(): ReservationInfoViewModel {
        val repository = repository()
        return ReservationInfoViewModel(
            loadReservations = useCase(repository),
            getForgetCodeUseCase = GetForgetCodeUseCase(repository),
            getForgetCodeMapCache = GetForgetCodeMapCacheUseCase(repository),
            now = { now },
        )
    }

    private val ReservationInfoViewModel.foods: List<String>
        get() = (reservationInfoUIModel.value as LoadableData.Loaded<ReservationInfoScreenUIModel?>)
            .data!!.reservationInfoCardUIModelList
            .flatMap { day -> day.mealRowUIModelList.map { it.foodName } }

    @Test
    fun `both weeks are loaded and saved for the account`() = runTest(dispatcher) {
        api.weeks[""] = week("2026-11-01" to "kabab")
        api.weeks[NEXT_WEEK] = week("2026-11-08" to "pizza")
        val useCase = useCase()

        val info = useCase().getOrThrow()

        assertEquals(listOf("2026-11-01", "2026-11-08"), info.dayInfoList.map { it.date })
        assertEquals(info, useCase.saved()!!.info)
    }

    @Test
    fun `without next week, this week is shown and saved alone`() = runTest(dispatcher) {
        api.weeks[""] = week("2026-11-01" to "kabab")
        val useCase = useCase()

        assertEquals(1, useCase().getOrThrow().dayInfoList.size)
        assertEquals(1, useCase.saved()!!.info.dayInfoList.size)
    }

    @Test
    fun `a failed load keeps the saved copy`() = runTest(dispatcher) {
        api.weeks[""] = week("2026-11-01" to "kabab")
        val useCase = useCase()
        useCase()

        api.reservesDown = true
        assertTrue(useCase().isFailure)
        assertEquals(1, useCase.saved()!!.info.dayInfoList.size)
    }

    @Test
    fun `reservations are saved for the account that asked, even after a switch`() =
        runTest(dispatcher) {
            api.weeks[""] = week("2026-11-01" to "kabab")
            api.duringReserves = { active = "401100000" }
            val useCase = useCase()

            useCase()

            assertNull(useCase.saved())
            active = "401106789"
            assertEquals(1, useCase.saved()!!.info.dayInfoList.size)
        }

    @Test
    fun `without internet the home screen shows the saved copy and says so`() =
        runTest(dispatcher) {
            api.weeks[""] = week("2026-11-01" to "kabab")
            useCase()()
            now += 3 * HOUR
            api.reservesDown = true
            val vm = viewModel()

            vm.onLaunch()
            advanceUntilIdle()

            assertEquals(listOf("kabab"), vm.foods)
            assertEquals(savedNoticeText(now - 3 * HOUR, now), vm.savedNotice.value)
            assertTrue(vm.savedNotice.value!!.contains("۳ ساعت پیش"))
        }

    @Test
    fun `the saved copy shows before Samad answers, then the latest replaces it`() =
        runTest(dispatcher) {
            api.weeks[""] = week("2026-11-01" to "kabab")
            useCase()()
            api.weeks[""] = week("2026-11-01" to "joojeh")
            val answer = CompletableDeferred<Unit>()
            api.duringReserves = { answer.await() }
            val vm = viewModel()

            vm.onLaunch()
            runCurrent()
            assertEquals(listOf("kabab"), vm.foods)

            answer.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf("joojeh"), vm.foods)
            assertNull(vm.savedNotice.value)
        }

    @Test
    fun `a refresh that fails falls back to the saved copy`() = runTest(dispatcher) {
        api.weeks[""] = week("2026-11-01" to "kabab")
        val vm = viewModel()
        vm.onLaunch()
        advanceUntilIdle()
        assertNull(vm.savedNotice.value)

        api.reservesDown = true
        vm.onRefresh()
        advanceUntilIdle()

        assertEquals(listOf("kabab"), vm.foods)
        assertEquals(savedNoticeText(now, now), vm.savedNotice.value)
    }

    @Test
    fun `without internet and nothing saved, the error shows`() = runTest(dispatcher) {
        api.reservesDown = true
        val vm = viewModel()

        vm.onLaunch()
        advanceUntilIdle()

        assertTrue(vm.reservationInfoUIModel.value is LoadableData.Failed)
        assertNull(vm.savedNotice.value)
    }

    @Test
    fun `time ago is written in Persian`() {
        assertEquals("همین الان", timeAgo(now, now + 30_000))
        assertEquals("۵ دقیقه پیش", timeAgo(now, now + 5 * MINUTE))
        assertEquals("۳ ساعت پیش", timeAgo(now, now + 3 * HOUR))
        assertEquals("۲ روز پیش", timeAgo(now, now + 50 * HOUR))
    }

    private companion object {
        const val NEXT_WEEK = "2026-11-07"
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE

        /** A week from Samad with one upcoming lunch on each date. */
        fun week(vararg days: Pair<String, String>) = ReservationInformationDto(
            weekDayDtoList = days.mapIndexed { i, (date, food) ->
                WeekDayDto(
                    date = date,
                    dateJStr = "1405/08/${10 + i}",
                    dayTranslated = "day$i",
                    mealTypeDtoList = listOf(
                        UserMealTypeDto(
                            name = "ناهار",
                            reserveDto = ReserveDto(id = 100 + i, foodNames = food, timeDistanceUntilToday = 1),
                        )
                    ),
                )
            }
        )
    }
}
