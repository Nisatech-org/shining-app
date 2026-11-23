package app.shining.push

import app.shining.domain.model.KeyValueStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Remembers which vendor days of the watched week the phone has already announced, so a push and
 * the fallback check (or a push delivered twice) never notify about the same thing twice.
 * Only the current week is kept; a new week starts empty.
 */
class AnnouncedAlerts(
    private val store: KeyValueStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    @Serializable
    private data class Saved(val weekStart: String, val days: Map<String, List<String>>)

    /**
     * The part of [batch] not announced yet. A vendor with nothing announced this week counts as
     * appearing for the first time, whatever the batch says.
     */
    @Synchronized
    fun unannounced(batch: MenuBatch): MenuBatch {
        val announced = load(batch.weekStart)
        return batch.copy(
            vendors = batch.vendors.mapNotNull { vendor ->
                val before = announced[vendor.key].orEmpty()
                val fresh = vendor.newDays.filterNot { it in before }
                if (fresh.isEmpty()) null else vendor.copy(newDays = fresh, firstAppearance = before.isEmpty())
            },
        )
    }

    /** Records [batch]'s days as announced. */
    @Synchronized
    fun markAnnounced(batch: MenuBatch) {
        val announced = load(batch.weekStart).toMutableMap()
        batch.vendors.forEach { vendor ->
            announced[vendor.key] = (announced[vendor.key].orEmpty() + vendor.newDays).distinct().sorted()
        }
        store.putString(KEY, json.encodeToString(Saved.serializer(), Saved(batch.weekStart, announced)))
    }

    /** The saved record as text, for the debug screen. */
    fun describe(): String {
        val saved = store.getString(KEY)?.let { runCatching { json.decodeFromString(Saved.serializer(), it) }.getOrNull() }
            ?: return "nothing announced yet"
        return "week ${saved.weekStart}: " + saved.days.entries.joinToString { (key, days) -> "$key ${days.joinToString("+") { it.takeLast(5) }}" }
    }

    private fun load(weekStart: String): Map<String, List<String>> {
        val saved = store.getString(KEY)?.let {
            runCatching { json.decodeFromString(Saved.serializer(), it) }.getOrNull()
        }
        return if (saved?.weekStart == weekStart) saved.days else emptyMap()
    }

    private companion object {
        const val KEY = "PUSH_ANNOUNCED_DAYS"
    }
}

/**
 * The server's status as a batch of everything published so far, to compare against what was
 * announced. Used by the fallback check when a push may have been missed.
 */
fun ServerStatus.publishedBatch(): MenuBatch = MenuBatch(
    weekStart = week,
    vendors = vendors.filter { it.days.isNotEmpty() }.map {
        BatchVendor(key = it.key, name = it.name, newDays = it.days.sorted(), firstAppearance = true, selfIds = it.selfIds)
    },
)
