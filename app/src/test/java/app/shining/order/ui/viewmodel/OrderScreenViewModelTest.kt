package app.shining.order.ui.viewmodel

import app.shining.account.domain.model.Account
import app.shining.account.domain.repository.AccountStore
import app.shining.account.domain.repository.ReserveInFlightTracker
import app.shining.domain.model.LoadableData
import app.shining.domain.ui.model.ShiningInformationBoxState
import app.shining.order.api.dto.reserve.ReserveProgramDto
import app.shining.order.api.dto.reserve.SelfWeekProgramDto
import app.shining.order.api.dto.reserve.UserWeekReserveDto
import app.shining.order.api.dto.self.SelfDto
import app.shining.order.domain.model.MealType
import app.shining.order.domain.model.ReserveRequestBodyData
import app.shining.order.domain.repository.OrderFoodRepository
import app.shining.order.domain.usecase.GetAvailableSelfsUseCase
import app.shining.order.domain.usecase.GetReservableProgramUseCase
import app.shining.order.domain.usecase.ReserveFoodUseCase
import app.shining.order.ui.model.OrderFoodDetailUIModel
import app.shining.order.ui.model.OrderFoodKey
import app.shining.order.ui.model.OrderScreenUIModel
import app.shining.order.ui.model.key
import app.shining.testing.FakeKeyValueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderScreenViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeOrderFoodRepository()
    private val accountStore = AccountStore(FakeKeyValueStore(), Json)
    private lateinit var viewModel: OrderScreenViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = OrderScreenViewModel(
            getReservableProgramUseCase = GetReservableProgramUseCase(repository),
            getAvailableSelfsUseCase = GetAvailableSelfsUseCase(repository),
            reserveFoodUseCase = ReserveFoodUseCase(repository, ReserveInFlightTracker()),
            accountStore = accountStore,
        )
        accountStore.upsert(Account(username = "me", password = "pw", displayName = "Me"))
        accountStore.setActive("me")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.loadSelf() {
        viewModel.onLaunch()
        advanceUntilIdle()
    }

    private fun loadedModel(): OrderScreenUIModel =
        (viewModel.orderScreenUIModel.value as LoadableData.Loaded).data

    private fun food(key: OrderFoodKey): OrderFoodDetailUIModel =
        loadedModel().orderCardUIModelList
            .flatMap { it.reserveInfoList.values.flatten() }
            .single { it.key == key }

    @Test
    fun `food is pending while its request is in flight`() = runTest(dispatcher) {
        loadSelf()
        val gate = CompletableDeferred<Result<String>>()
        repository.onReserve = { gate.await() }

        viewModel.onOrderFoodClick(food(KABAB))
        runCurrent()

        assertEquals(setOf(KABAB), viewModel.pendingFoods.value)

        gate.complete(Result.success("ok"))
        advanceUntilIdle()

        assertTrue(viewModel.pendingFoods.value.isEmpty())
    }

    @Test
    fun `success flips the food before the program is fetched again`() = runTest(dispatcher) {
        loadSelf()
        val programGate = CompletableDeferred<Unit>()
        repository.onProgram = { programGate.await(); programWith() }

        viewModel.onOrderFoodClick(food(KABAB))
        runCurrent()

        assertTrue(food(KABAB).isSelected)
        assertTrue(viewModel.pendingFoods.value.isEmpty())
        assertEquals(ShiningInformationBoxState.SUCCESS, viewModel.informationBoxUIModel.value?.state)
        programGate.complete(Unit)
    }

    @Test
    fun `background refresh replaces the optimistic state with server data`() =
        runTest(dispatcher) {
            loadSelf()
            // Server says the other food ended up reserved (e.g. it swapped the meal).
            repository.onProgram = { programWith(GHEYME_FOOD_ID) }

            viewModel.onOrderFoodClick(food(KABAB))
            advanceUntilIdle()

            assertFalse(food(KABAB).isSelected)
            assertTrue(food(GHEYME).isSelected)
        }

    @Test
    fun `failure keeps the food unchanged and shows the error`() = runTest(dispatcher) {
        loadSelf()
        repository.onReserve = { Result.failure(Throwable("اعتبار کافی نیست")) }

        viewModel.onOrderFoodClick(food(KABAB))
        advanceUntilIdle()

        assertFalse(food(KABAB).isSelected)
        assertTrue(viewModel.pendingFoods.value.isEmpty())
        assertEquals("اعتبار کافی نیست", viewModel.informationBoxUIModel.value?.message)
        assertEquals(ShiningInformationBoxState.FAILED, viewModel.informationBoxUIModel.value?.state)
    }

    @Test
    fun `a full سلف مرکزی food says it's Samad's error, a full food elsewhere says it's full`() = runTest(dispatcher) {
        val full = "ظرفیت رزرو این غذا تکمیل شده است"
        repository.onSelfs = { Result.success(listOf(SelfDto(id = 1, name = "مرکزی - سلف دانشجویان آقا"))) }
        repository.onProgram = {
            Result.success(ReserveProgramDto(listOf(listOf(dto(KABAB, KABAB_FOOD_ID, selfId = 1))), userId = 1))
        }
        loadSelf()
        repository.onReserve = { Result.failure(Throwable(full)) }

        viewModel.onOrderFoodClick(food(KABAB))
        advanceUntilIdle()

        val message = viewModel.informationBoxUIModel.value?.message.orEmpty()
        assertTrue(full in message)
        assertTrue("سلف مرکزی پر نمی‌شه" in message)
        assertFalse(food(KABAB).isSelected)
    }

    @Test
    fun `foods carry their cafeteria's short name, سلف for سلف مرکزی`() = runTest(dispatcher) {
        repository.onSelfs = {
            Result.success(
                listOf(
                    SelfDto(id = 1, name = "مرکزی - سلف دانشجویان آقا"),
                    OTHER_SELF.copy(name = "کاله پردیس مرکزی(بیرون بر)"),
                )
            )
        }
        repository.onProgram = { selfId ->
            if (selfId == 1) {
                Result.success(ReserveProgramDto(listOf(listOf(dto(KABAB, KABAB_FOOD_ID, selfId = 1))), userId = 1))
            } else {
                otherSelfProgram()
            }
        }
        loadSelf()

        assertEquals("سلف", food(KABAB).selfName)
        assertEquals("کاله", food(OTHER_LUNCH).selfName)
    }

    @Test
    fun `a single cafeteria gets no name tag`() = runTest(dispatcher) {
        repository.onSelfs = { Result.success(listOf(SELF)) }
        loadSelf()
        assertNull(food(KABAB).selfName)
    }

    @Test
    fun `a full food at another self is shown as Samad says`() = runTest(dispatcher) {
        val full = "ظرفیت رزرو این غذا تکمیل شده است"
        loadSelf()
        repository.onReserve = { Result.failure(Throwable(full)) }

        viewModel.onOrderFoodClick(food(KABAB))
        advanceUntilIdle()

        assertEquals(full, viewModel.informationBoxUIModel.value?.message)
    }

    @Test
    fun `tapping a pending food again does not send a second request`() = runTest(dispatcher) {
        loadSelf()
        val gate = CompletableDeferred<Result<String>>()
        repository.onReserve = { gate.await() }

        viewModel.onOrderFoodClick(food(KABAB))
        runCurrent()
        viewModel.onOrderFoodClick(food(KABAB))
        runCurrent()
        gate.complete(Result.success("ok"))
        advanceUntilIdle()

        assertEquals(1, repository.reserveCalls.size)
    }

    @Test
    fun `different foods can be reserved at the same time`() = runTest(dispatcher) {
        loadSelf()
        val gate = CompletableDeferred<Result<String>>()
        repository.onReserve = { gate.await() }

        viewModel.onOrderFoodClick(food(KABAB))
        viewModel.onOrderFoodClick(food(DINNER))
        runCurrent()

        assertEquals(setOf(KABAB, DINNER), viewModel.pendingFoods.value)
        gate.complete(Result.success("ok"))
        advanceUntilIdle()
        assertEquals(2, repository.reserveCalls.size)
    }

    @Test
    fun `an older refresh never overwrites a newer one`() = runTest(dispatcher) {
        loadSelf()
        val slowRefresh = CompletableDeferred<Unit>()
        val newRefresh = CompletableDeferred<Unit>()
        var programCalls = 0
        repository.onProgram = {
            programCalls++
            if (programCalls == 1) {
                // A response that arrives even though its refresh was cancelled, like the real
                // repository, which catches every exception.
                try {
                    slowRefresh.await()
                } catch (_: CancellationException) {
                }
                programWith()
            } else {
                newRefresh.await()
                programWith(KABAB_FOOD_ID, DINNER_FOOD_ID)
            }
        }

        viewModel.onOrderFoodClick(food(KABAB))
        advancePast(REFRESH_DEBOUNCE_MS)
        // Second success restarts the refresh while the first one is still waiting.
        viewModel.onOrderFoodClick(food(DINNER))
        advanceUntilIdle()

        // The stale response (nothing reserved) must not have replaced the optimistic state.
        assertTrue(food(KABAB).isSelected)
        assertTrue(food(DINNER).isSelected)

        newRefresh.complete(Unit)
        advanceUntilIdle()
        assertTrue(food(KABAB).isSelected)
        assertTrue(food(DINNER).isSelected)
        assertEquals(2, programCalls)
    }

    @Test
    fun `messages name the account once there is more than one`() = runTest(dispatcher) {
        loadSelf()

        viewModel.onOrderFoodClick(food(KABAB))
        advanceUntilIdle()
        assertEquals("ok", viewModel.informationBoxUIModel.value?.message)

        accountStore.upsert(Account(username = "ali", password = "pw", displayName = "Ali"))
        viewModel.onOrderFoodClick(food(DINNER))
        advanceUntilIdle()
        assertEquals("برای Me: ok", viewModel.informationBoxUIModel.value?.message)
    }

    @Test
    fun `a failed visible load shows the retry state instead of loading forever`() =
        runTest(dispatcher) {
            repository.onPreviousWeek = { Result.failure(Throwable("خطا")) }
            repository.onProgram = { Result.failure(Throwable("خطا")) }

            loadSelf()

            val state = viewModel.orderScreenUIModel.value
            assertTrue(state is LoadableData.Failed)
            assertEquals("خطا", (state as LoadableData.Failed).message)
        }

    @Test
    fun `failing to get the selfs shows the retry state`() = runTest(dispatcher) {
        repository.onSelfs = { Result.failure(Throwable("سلفی نیست")) }

        loadSelf()

        val state = viewModel.orderScreenUIModel.value
        assertTrue(state is LoadableData.Failed)
        assertEquals("سلفی نیست", (state as LoadableData.Failed).message)
    }

    @Test
    fun `every self's foods share one day, cheapest first, each tagged with its self`() =
        runTest(dispatcher) {
            repository.onSelfs = { Result.success(listOf(SELF, OTHER_SELF)) }
            repository.onProgram = { selfId ->
                if (selfId == SELF_ID) programWith() else otherSelfProgram()
            }

            loadSelf()

            val days = loadedModel().orderCardUIModelList
            assertEquals(1, days.size)
            val lunch = days.single().reserveInfoList.getValue(MealType.LUNCH)
            assertEquals(listOf(OTHER_LUNCH, KABAB, GHEYME), lunch.map { it.key })
            assertEquals("other", food(OTHER_LUNCH).selfName)
            assertEquals("self", food(KABAB).selfName)
        }

    @Test
    fun `days from different selfs and weeks are in date order`() = runTest(dispatcher) {
        repository.onSelfs = { Result.success(listOf(SELF, OTHER_SELF)) }
        repository.onProgram = { selfId ->
            if (selfId == SELF_ID) programWith() else otherSelfProgram(date = EARLIER_DATE)
        }

        loadSelf()

        assertEquals(
            listOf(EARLIER_DATE, DATE),
            loadedModel().orderCardUIModelList.map { it.date },
        )
    }

    @Test
    fun `a self that fails to load does not hide the others`() = runTest(dispatcher) {
        repository.onSelfs = { Result.success(listOf(SELF, OTHER_SELF)) }
        repository.onProgram = { selfId ->
            if (selfId == SELF_ID) programWith() else Result.failure(Throwable("خطا"))
        }

        loadSelf()

        assertFalse(food(KABAB).isSelected)
    }

    @Test
    fun `with a single self foods get no self tag`() = runTest(dispatcher) {
        loadSelf()

        assertNull(food(KABAB).selfName)
    }

    @Test
    fun `reserving at one self locks that day at the other self right away`() =
        runTest(dispatcher) {
            repository.onSelfs = { Result.success(listOf(SELF, OTHER_SELF)) }
            repository.onProgram = { selfId ->
                if (selfId == SELF_ID) programWith() else otherSelfProgram()
            }
            loadSelf()
            val programGate = CompletableDeferred<Unit>()
            repository.onProgram = { programGate.await(); programWith() }

            viewModel.onOrderFoodClick(food(KABAB))
            runCurrent()

            assertFalse(food(OTHER_LUNCH).canSelect)
            programGate.complete(Unit)
        }

    private fun TestScope.advancePast(ms: Long) {
        testScheduler.advanceTimeBy(ms + 1)
        runCurrent()
    }

    private class FakeOrderFoodRepository : OrderFoodRepository {
        val reserveCalls = mutableListOf<Pair<ReserveRequestBodyData, Int>>()
        var onReserve: suspend () -> Result<String> = { Result.success("ok") }
        var onSelfs: suspend () -> Result<List<SelfDto>> = { Result.success(listOf(SELF)) }

        /** Answers the previous week of the self with the given id. Empty by default. */
        var onPreviousWeek: suspend (selfId: Int) -> Result<ReserveProgramDto> = {
            Result.success(ReserveProgramDto())
        }

        /** Answers the next week of the self with the given id. */
        var onProgram: suspend (selfId: Int) -> Result<ReserveProgramDto> = { programWith() }
        private val programRequests = mutableMapOf<Int, Int>()

        override suspend fun reserveFood(
            reserveRequestBodyData: ReserveRequestBodyData,
            programId: Int,
        ): Result<String> {
            reserveCalls += reserveRequestBodyData to programId
            return onReserve()
        }

        /**
         * For each self the view model asks for the previous and then the next week. The dates
         * can't tell them apart in unit tests, so the order of the calls does.
         */
        override suspend fun getReserveProgram(
            selfId: Int,
            weekStartDate: String,
        ): Result<ReserveProgramDto> {
            val request = programRequests.getOrDefault(selfId, 0)
            programRequests[selfId] = request + 1
            return if (request % 2 == 0) onPreviousWeek(selfId) else onProgram(selfId)
        }

        override suspend fun getAvailableSelfs(): Result<List<SelfDto>> = onSelfs()
    }

    private companion object {
        const val SELF_ID = 7
        const val OTHER_SELF_ID = 8
        val SELF = SelfDto(id = SELF_ID, name = "self")
        val OTHER_SELF = SelfDto(id = OTHER_SELF_ID, name = "other")
        const val DATE = "2026-09-27"
        const val EARLIER_DATE = "2026-09-26"
        const val REFRESH_DEBOUNCE_MS = 300L

        const val KABAB_FOOD_ID = 100
        const val GHEYME_FOOD_ID = 101
        const val DINNER_FOOD_ID = 102

        val KABAB = OrderFoodKey(programId = 10, mealTypeId = 1, foodTypeId = 1)
        val GHEYME = OrderFoodKey(programId = 10, mealTypeId = 1, foodTypeId = 2)
        val DINNER = OrderFoodKey(programId = 11, mealTypeId = 5, foodTypeId = 1)
        val OTHER_LUNCH = OrderFoodKey(programId = 20, mealTypeId = 1, foodTypeId = 1)
        const val OTHER_LUNCH_FOOD_ID = 200

        fun dto(
            key: OrderFoodKey,
            foodId: Int,
            selfId: Int = SELF_ID,
            price: Int = 1000,
            date: String = DATE,
        ) = SelfWeekProgramDto(
            cancelRuleViolated = false,
            date = date,
            dayTranslated = "Sunday",
            foodId = foodId,
            foodName = "food $foodId",
            foodTypeId = key.foodTypeId,
            mealTypeId = key.mealTypeId,
            price = price,
            programId = key.programId,
            reserveRuleViolated = false,
            selfId = selfId,
            daysDifferenceWithToday = 1,
        )

        /** One day with lunch (kabab, gheyme) and dinner; [reserved] are the reserved foodIds. */
        fun programWith(vararg reserved: Int): Result<ReserveProgramDto> = Result.success(
            ReserveProgramDto(
                selfWeekProgramDtoList = listOf(
                    listOf(
                        dto(KABAB, KABAB_FOOD_ID),
                        dto(GHEYME, GHEYME_FOOD_ID),
                        dto(DINNER, DINNER_FOOD_ID),
                    )
                ),
                userId = 1,
                userWeekReserveDtos = reserved.map {
                    UserWeekReserveDto(foodId = it, programDate = DATE, selfId = SELF_ID)
                },
            )
        )

        /** A cheaper lunch at [OTHER_SELF_ID] on [date]. */
        fun otherSelfProgram(date: String = DATE): Result<ReserveProgramDto> = Result.success(
            ReserveProgramDto(
                selfWeekProgramDtoList = listOf(
                    listOf(
                        dto(
                            OTHER_LUNCH,
                            OTHER_LUNCH_FOOD_ID,
                            selfId = OTHER_SELF_ID,
                            price = 500,
                            date = date,
                        ),
                    )
                ),
                userId = 1,
            )
        )
    }
}
