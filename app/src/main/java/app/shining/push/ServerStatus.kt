package app.shining.push

import app.shining.debug.DebugLog
import app.shining.domain.model.KeyValueStore
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.http.GET

/** What the Shining server's `GET /v1/status` returns (see shining-backend's server/.../http/Status.kt). */
@Serializable
data class ServerStatus(
    /** The Saturday (yyyy-MM-dd) of the week being watched: next week. */
    val week: String,
    val updatedAt: String? = null,
    val lastCheckOk: Boolean = true,
    /** Cafeterias the server watches; the phone checks the user's others itself. */
    val coveredSelfIds: List<Int> = emptyList(),
    val vendors: List<VendorStatus> = emptyList(),
)

@Serializable
data class VendorStatus(
    val key: String,
    val name: String,
    val selfIds: List<Int> = emptyList(),
    /** none, partial or complete. */
    val state: String = "none",
    /** An ISO instant, e.g. 2026-11-10T10:30:00Z. */
    val firstSeenAt: String? = null,
    val days: List<String> = emptyList(),
    val usualArrival: UsualArrival? = null,
)

/** dayOfWeek is SATURDAY … FRIDAY, time is HH:mm in Tehran. */
@Serializable
data class UsualArrival(val dayOfWeek: String, val time: String)

/** The server finds every menu; the app only reads what it found. */
interface ServerStatusApi {
    @GET("v1/status")
    suspend fun status(): ServerStatus
}

/** The vendors the server knows about, for when its status hasn't been loaded yet. */
val DEFAULT_VENDORS = listOf(
    VendorStatus("self", "سلف", listOf(1, 2)),
    VendorStatus("kaleh", "کاله", listOf(20, 21)),
    VendorStatus("fastfood", "فست‌فود شریف", listOf(22)),
    VendorStatus("cleanfood", "کلین‌فود", listOf(23)),
    VendorStatus("kalana", "کلانا", listOf(24)),
    VendorStatus("unifood", "یونی‌فود", listOf(25)),
)

/**
 * Loads the server's status and keeps the last one on the phone, so the Home card and the
 * fallback check still have something to show or compare when the server can't be reached.
 */
class ServerStatusRepository(
    private val api: ServerStatusApi,
    private val store: KeyValueStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun fetch(): Result<ServerStatus> {
        val started = System.currentTimeMillis()
        return try {
            val status = api.status()
            store.putString(KEY, json.encodeToString(ServerStatus.serializer(), status))
            store.putString(KEY_AT, System.currentTimeMillis().toString())
            DebugLog.i(
                TAG,
                "status ok in ${System.currentTimeMillis() - started}ms: week ${status.week}, lastCheckOk=${status.lastCheckOk}, " +
                    status.vendors.joinToString { "${it.key}=${it.state}(${it.days.size})" },
            )
            Result.success(status)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.w(TAG, "status request failed after ${System.currentTimeMillis() - started}ms: ${e.javaClass.simpleName}: ${e.message}")
            Result.failure(e)
        }
    }

    fun cached(): ServerStatus? = store.getString(KEY)?.let {
        runCatching { json.decodeFromString(ServerStatus.serializer(), it) }.getOrNull()
    }

    /** When the saved status was last refreshed, for the debug screen. Null if never. */
    fun cachedAt(): Long? = store.getString(KEY_AT)?.toLongOrNull()

    private companion object {
        const val TAG = "status"
        const val KEY = "SHINING_SERVER_STATUS"
        const val KEY_AT = "SHINING_SERVER_STATUS_AT"
    }
}
