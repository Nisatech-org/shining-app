package app.shining.push

import app.shining.domain.model.KeyValueStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/**
 * Holds the phone's own finds (dorms, a self the server can't see) for a short window, like the
 * server's 20 minutes: days found soon after a vendor's first unannounced one
 * share its alert, later ones get their own. Being first to reserve is the point of the app, so the
 * wait is short. The phone checks about every 15 minutes, so a vendor is announced at the check after
 * the one that first saw it ([WINDOW_MS] is shorter than that interval on purpose), or at once when
 * its whole week (Saturday to Wednesday) is open. Only the watched week is kept.
 */
class AlertWindow(
    private val store: KeyValueStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    @Serializable
    private data class Saved(val weekStart: String, val firstSeen: Map<String, Long>)

    /**
     * The vendors of [all] (each with every day found so far) to announce at [nowMillis]. [fresh] is
     * the part not announced yet: a vendor in it starts its window when first seen, and is ready once
     * [WINDOW_MS] has passed or its whole week is open.
     */
    @Synchronized
    fun ready(all: MenuBatch, fresh: MenuBatch, nowMillis: Long): MenuBatch {
        val saved = load(all.weekStart)
        val firstSeen = fresh.vendors.map { it.key }.associateWith { saved[it] ?: nowMillis }
        val ready = all.vendors.filter { vendor ->
            val seen = firstSeen[vendor.key] ?: return@filter false
            nowMillis - seen >= WINDOW_MS || isWholeWeek(all.weekStart, vendor.newDays)
        }
        val readyKeys = ready.map { it.key }.toSet()
        store.putString(KEY, json.encodeToString(Saved.serializer(), Saved(all.weekStart, firstSeen - readyKeys)))
        return all.copy(vendors = ready)
    }

    private fun load(weekStart: String): Map<String, Long> {
        val saved = store.getString(KEY)?.let { runCatching { json.decodeFromString(Saved.serializer(), it) }.getOrNull() }
        return if (saved?.weekStart == weekStart) saved.firstSeen else emptyMap()
    }

    companion object {
        private const val KEY = "PUSH_ALERT_WINDOW"

        /** Less than the ~15 minutes between checks, so new days go out at the next check. */
        const val WINDOW_MS = 10 * 60 * 1000L
    }
}

/** True if [days] (yyyy-MM-dd) cover Saturday [weekStart] through the Wednesday after it. */
internal fun isWholeWeek(weekStart: String, days: List<String>): Boolean {
    val parts = weekStart.take(10).split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return false
    val have = days.map { it.take(10) }.toSet()
    // Plain Calendar, as in MenuAlert.kt (the app still supports Android 7). Locale.US so a Persian
    // phone still writes the digits as 0-9.
    return (0 until 5).all { offset ->
        val day = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(parts[0], parts[1] - 1, parts[2])
            add(Calendar.DAY_OF_MONTH, offset)
        }
        String.format(Locale.US, "%04d-%02d-%02d", day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH)) in have
    }
}
