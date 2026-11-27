package app.shining.reserveinfo.domain.repository

import app.shining.domain.network.SamadResponse
import app.shining.reserveinfo.api.ReserveInfoApi
import app.shining.reserveinfo.api.dto.ForgetCodeDto
import app.shining.reserveinfo.api.dto.ReservationInformationDto
import app.shining.reserveinfo.db.dao.ForgetCodeDao
import app.shining.reserveinfo.db.model.ForgetCodeEntity
import app.shining.reserveinfo.domain.model.ForgetCode
import app.shining.reserveinfo.domain.model.ReservationDayInfo
import app.shining.reserveinfo.domain.model.ReservationInfo
import app.shining.reserveinfo.domain.model.ReservationMealInfo
import app.shining.testing.FakeKeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

class ReservationInfoRepositoryTest {

    private val dao = FakeForgetCodeDao()
    private val api = FakeReserveInfoApi()
    private val storage = FakeKeyValueStore()
    private var now = 1_000_000_000_000L
    private var active: String? = "401106789"
    private var today = "2026-11-01"

    private fun TestScope.repository() = ReservationInfoRepositoryImplementation(
        api = api,
        forgetCodeDao = dao,
        storage = storage,
        json = Json { ignoreUnknownKeys = true },
        activeAccountId = { active },
        now = { now },
        today = { today },
        loadScope = this as CoroutineScope,
    )

    @Test
    fun `a code is fetched once and then served from the device`() = runTest {
        api.codes[7] = "4821"
        val repository = repository()

        assertEquals("4821", repository.getForgetCode(7).getOrThrow().code)
        assertEquals("4821", repository.getForgetCode(7).getOrThrow().code)

        assertEquals(listOf(7), api.forgetCodeCalls)
        assertEquals(mapOf(7 to "4821"), repository.getMapCache())
        assertEquals("401106789", dao.rows.getValue(7).accountId)
    }

    @Test
    fun `saved codes show up after a restart without asking Samad again`() = runTest {
        api.codes[7] = "4821"
        repository().getForgetCode(7)

        val afterRestart = repository()

        assertEquals(mapOf(7 to "4821"), afterRestart.getMapCache())
        afterRestart.getForgetCode(7)
        assertEquals(listOf(7), api.forgetCodeCalls)
    }

    @Test
    fun `a code Samad marks as not valid is still kept`() = runTest {
        api.codes[7] = "4821"
        api.valid = false
        repository().getForgetCode(7)

        assertEquals(mapOf(7 to "4821"), repository().getMapCache())
    }

    @Test
    fun `codes for next week survive a week, old ones are cleaned up`() = runTest {
        api.codes[7] = "4821"
        api.codes[8] = "1234"
        repository().getForgetCode(7)
        now += 10L * DAY
        repository().getForgetCode(8)

        now += 25L * DAY
        assertEquals(mapOf(8 to "1234"), repository().getMapCache())
    }

    @Test
    fun `a failed fetch is not saved and can be retried`() = runTest {
        api.codes[7] = "4821"
        api.networkDown = true
        val repository = repository()

        assertTrue(repository.getForgetCode(7).isFailure)
        assertTrue(repository.getMapCache().isEmpty())

        api.networkDown = false
        assertEquals("4821", repository.getForgetCode(7).getOrThrow().code)
    }

    @Test
    fun `an empty code is treated as a failure`() = runTest {
        api.codes[7] = ""
        val repository = repository()

        assertTrue(repository.getForgetCode(7).isFailure)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `removing an account removes its codes`() = runTest {
        api.codes[7] = "4821"
        val repository = repository()
        repository.getForgetCode(7)

        repository.deleteAccountData("401106789")

        assertTrue(repository.getMapCache().isEmpty())
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `saved reservations come back for the same account only`() = runTest {
        val repository = repository()
        repository.saveReservations("401106789", reservations("2026-11-01", "2026-11-02"))

        val saved = repository.getSavedReservations()!!
        assertEquals(listOf("2026-11-01", "2026-11-02"), saved.info.dayInfoList.map { it.date })
        assertEquals(now, saved.savedAt)

        active = "401100000"
        assertNull(repository.getSavedReservations())
    }

    @Test
    fun `saved reservations survive a restart`() = runTest {
        repository().saveReservations("401106789", reservations("2026-11-02"))

        assertEquals(1, repository().getSavedReservations()!!.info.dayInfoList.size)
    }

    @Test
    fun `days that have passed are left out of the saved copy`() = runTest {
        val repository = repository()
        repository.saveReservations(
            "401106789",
            reservations("2026-10-30", "2026-11-01", "2026-11-03", "unknown"),
        )
        today = "2026-11-02"

        assertEquals(
            listOf("2026-11-03", "unknown"),
            repository.getSavedReservations()!!.info.dayInfoList.map { it.date },
        )
    }

    @Test
    fun `removing an account removes its saved reservations`() = runTest {
        val repository = repository()
        repository.saveReservations("401106789", reservations("2026-11-02"))

        repository.deleteAccountData("401106789")

        assertNull(repository.getSavedReservations())
    }

    @Test
    fun `an unreadable saved copy counts as nothing saved`() = runTest {
        storage.putString("saved_reservations_401106789", "{not json")

        assertNull(repository().getSavedReservations())
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}

/** Reservations with one lunch on each of [dates]. */
internal fun reservations(vararg dates: String) = ReservationInfo(
    remainCredit = 0,
    dayInfoList = dates.mapIndexed { i, date ->
        ReservationDayInfo(
            name = "day$i",
            iranianDate = "1405/08/${10 + i}",
            date = date,
            mealInfo = listOf(
                ReservationMealInfo(selfText = "self", foodText = "food$i", mealText = "ناهار", price = 0, id = i + 1)
            ),
        )
    },
)

internal class FakeReserveInfoApi : ReserveInfoApi {
    val codes = mutableMapOf<Int, String>()
    val forgetCodeCalls = mutableListOf<Int>()
    var valid = true
    var networkDown = false

    /** What Samad returns for each week start; "" is this week. A missing week is an error. */
    val weeks = mutableMapOf<String, ReservationInformationDto>("" to ReservationInformationDto())
    var reservesDown = false

    /** Samad refuses the session: HTTP 401, as it reaches the repository once renewing failed. */
    var sessionRejected = false
    val reserveCalls = mutableListOf<String>()

    /** Runs while a reservations request is in flight. */
    var duringReserves: suspend () -> Unit = {}

    override suspend fun getReserveInformation(weekStartDate: String): SamadResponse<ReservationInformationDto> {
        reserveCalls += weekStartDate
        duringReserves()
        if (reservesDown) throw IOException("offline")
        if (sessionRejected) {
            throw retrofit2.HttpException(
                retrofit2.Response.error<Any>(401, "{}".toResponseBody())
            )
        }
        val week = weeks[weekStartDate]
            ?: return SamadResponse(code = 400, messageFa = "برنامه‌ای نیست", type = "ERROR")
        return SamadResponse(code = 200, payload = week, type = "SUCCESS")
    }

    override suspend fun getForgetCode(
        reserveId: Int,
        count: Int,
        dailySale: Boolean,
    ): SamadResponse<ForgetCodeDto> {
        forgetCodeCalls += reserveId
        if (networkDown) throw IOException("offline")
        return SamadResponse(
            code = 200,
            payload = ForgetCodeDto(forgotCardCode = codes.getValue(reserveId), valid = valid),
            type = "SUCCESS",
        )
    }
}

internal class FakeForgetCodeDao : ForgetCodeDao {
    val rows = linkedMapOf<Int, ForgetCodeEntity>()

    override suspend fun deleteOldEntries(before: Long) {
        rows.values.removeAll { it.timestamp < before }
    }

    override suspend fun insertForgetCode(reservation: ForgetCodeEntity) {
        rows[reservation.reserveId] = reservation
    }

    override suspend fun getForgetCodeByReserveId(reserveId: Int): ForgetCode? =
        rows[reserveId]?.let { ForgetCode(it.reserveId, it.code, it.isValid) }

    override suspend fun deleteAllForgetCodes() = rows.clear()

    override suspend fun getAllForgetCodes(): List<ForgetCodeEntity> = rows.values.toList()

    override suspend fun getReserveIds(accountId: String): List<Int> =
        rows.values.filter { it.accountId == accountId }.map { it.reserveId }

    override suspend fun deleteForAccount(accountId: String) {
        rows.values.removeAll { it.accountId == accountId }
    }
}
