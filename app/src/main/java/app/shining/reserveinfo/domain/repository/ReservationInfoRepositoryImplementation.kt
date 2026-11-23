package app.shining.reserveinfo.domain.repository

import android.util.Log
import app.shining.domain.model.KeyValueStore
import app.shining.reserveinfo.api.ReserveInfoApi
import app.shining.reserveinfo.api.dto.toForgetCode
import app.shining.reserveinfo.api.dto.toReservationInfo
import app.shining.reserveinfo.db.dao.ForgetCodeDao
import app.shining.reserveinfo.domain.model.ForgetCode
import app.shining.reserveinfo.domain.model.ReservationDayInfo
import app.shining.reserveinfo.domain.model.ReservationInfo
import app.shining.reserveinfo.domain.model.SavedReservations
import app.shining.reserveinfo.domain.model.toForgetCodeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

class ReservationInfoRepositoryImplementation(
    private val api: ReserveInfoApi,
    private val forgetCodeDao: ForgetCodeDao,
    /** Holds each account's last loaded reservations. */
    private val storage: KeyValueStore,
    private val json: Json,
    /** Username of the active account. */
    private val activeAccountId: () -> String?,
    /** Current time in milliseconds, replaceable in tests. */
    private val now: () -> Long = System::currentTimeMillis,
    /** Today in Tehran as yyyy-MM-dd, the format of [ReservationDayInfo.date]. */
    private val today: () -> String = ::tehranToday,
    loadScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : ReservationInfoRepository {

    private val forgetCodeCache = ConcurrentHashMap<Int, String>()

    /** Fills [forgetCodeCache] from the database; anything reading the cache waits for it. */
    private val savedCodesLoaded: Deferred<Unit> = loadScope.async {
        runCatching {
            forgetCodeDao.deleteOldEntries(now() - FORGET_CODE_KEEP_MILLIS)
            forgetCodeDao.getAllForgetCodes().forEach {
                if (it.code.isNotBlank()) forgetCodeCache[it.reserveId] = it.code
            }
        }.onFailure { Log.e("ReservationInfoRepository", "Loading saved forget codes failed: $it") }
    }

    override suspend fun getReservationInfo(weekStartDate: String): Result<ReservationInfo> {
        return try {
            val result = api.getReserveInformation(weekStartDate)
            if (result.isSuccessful) {
                Result.success(result.payload!!.toReservationInfo())
            } else {
                Result.failure(Exception(result.getErrorMessage()))
            }
        } catch (e: Exception) {
            Log.e("ReservationInfoRepository", "Error ${e.message}")
            Result.failure(Throwable("در ارتباط با سرور به مشکل خوردیم."))
        }
    }

    override fun activeUsername(): String? = activeAccountId()

    override fun saveReservations(username: String, info: ReservationInfo) {
        val saved = SavedReservations(info = info, savedAt = now())
        storage.putString(reservationsKey(username), json.encodeToString(SavedReservations.serializer(), saved))
    }

    override fun getSavedReservations(): SavedReservations? {
        val username = activeAccountId() ?: return null
        val saved = storage.getString(reservationsKey(username))?.let {
            try {
                json.decodeFromString(SavedReservations.serializer(), it)
            } catch (e: Exception) {
                Log.e("ReservationInfoRepository", "Saved reservations are unreadable: $e")
                null
            }
        } ?: return null
        // Dates are yyyy-MM-dd, so they compare as text. A day whose date can't be read is kept.
        val today = today()
        val upcoming = saved.info.dayInfoList.filter { day ->
            val date = day.date.take(10)
            today.isEmpty() || !DATE.matches(date) || date >= today
        }
        return saved.copy(info = saved.info.copy(dayInfoList = upcoming))
    }

    override suspend fun getForgetCode(
        reserveId: Int,
        dailySale: Boolean,
    ): Result<ForgetCode> {
        savedCodesLoaded.await()
        forgetCodeCache[reserveId]?.let {
            return Result.success(ForgetCode(reserveId = reserveId, code = it, isValid = true))
        }

        return try {
            val result = api.getForgetCode(reserveId = reserveId, dailySale = dailySale)
            val forgetCode = result.payload?.toForgetCode(reserveId)
            if (result.isSuccessful && forgetCode != null && forgetCode.code.isNotBlank()) {
                // Saved whatever Samad says about its validity: the code itself never changes.
                forgetCodeCache[reserveId] = forgetCode.code
                forgetCodeDao.insertForgetCode(
                    forgetCode.toForgetCodeEntity(accountId = activeAccountId().orEmpty())
                        .copy(timestamp = now())
                )
                Result.success(forgetCode)
            } else {
                Result.failure(Exception(result.getErrorMessage()))
            }
        } catch (e: Exception) {
            Log.e("ForgetCodeRepository", "Error: $e")
            Result.failure(Throwable("در ارتباط با سرور به مشکل خوردیم."))
        }
    }

    override suspend fun getMapCache(): Map<Int, String> {
        savedCodesLoaded.await()
        return HashMap(forgetCodeCache)
    }

    // Forget codes are keyed by Samad's reservation id, which is unique across accounts, and
    // saved reservations belong to one account only, so there is nothing to copy; both only need
    // removing with the account.
    override suspend fun copyAccountData(fromUsername: String?, toUsername: String) = Unit

    override suspend fun deleteAccountData(username: String) {
        storage.remove(reservationsKey(username))
        savedCodesLoaded.await()
        forgetCodeDao.getReserveIds(username).forEach { forgetCodeCache.remove(it) }
        forgetCodeDao.deleteForAccount(username)
    }

    companion object {
        /**
         * How long a saved code is kept. Reservations go up to two weeks ahead, so this comfortably
         * outlives the meal it belongs to.
         */
        const val FORGET_CODE_KEEP_MILLIS = 30L * 24 * 60 * 60 * 1000

        private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

        private fun reservationsKey(username: String) = "saved_reservations_$username"
    }
}

/** Today's date in Tehran as yyyy-MM-dd. */
private fun tehranToday(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("Asia/Tehran") }
        .format(Date())
