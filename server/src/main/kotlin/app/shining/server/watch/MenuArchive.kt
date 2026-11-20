package app.shining.server.watch

import app.shining.server.discovery.TEHRAN
import app.shining.server.log.Log
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Keeps every version of each cafeteria's menu for the watched week, so we can see afterwards
 * exactly when a menu came out, what was on it, and when reservations opened.
 *
 * A file is written only when the menu's [SelfProgram.signature] changes (days, foods, prices,
 * open or not), not on every check: vote counts change all the time and the disk is small.
 * Files go to `archive/<week>/<yyyyMMdd-HHmmss>-self<id>.json` (Tehran time); weeks older than
 * [keepWeeks] are deleted. Nothing about any user is in them.
 */
class MenuArchive(private val dir: File, private val keepWeeks: Int = 12) {

    private val last = mutableMapOf<Pair<LocalDate, Int>, String>()
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** Saves [program] if it changed since the last check. Returns the file written, or null. */
    fun record(week: LocalDate, self: SelfInfo, program: SelfProgram, at: Instant): File? {
        val key = week to self.id
        if (last[key] == program.signature) return null
        val first = key !in last
        last[key] = program.signature
        // An empty menu is only worth a file once it replaces a real one.
        if (program.foods == 0 && first) return null
        return try {
            val weekDir = File(dir, week.toString()).apply { mkdirs() }
            val file = File(weekDir, "${at.atZone(TEHRAN).format(stamp)}-self${self.id}.json")
            val json = buildJsonObject {
                put("at", at.toString())
                put("week", week.toString())
                put("selfId", self.id)
                put("selfName", self.name)
                put("foods", program.foods)
                putJsonArray("foodDays") { program.foodDays.sorted().forEach { add(it.toString()) } }
                putJsonArray("openDays") { program.openDays.sorted().forEach { add(it.toString()) } }
                put("payload", program.raw ?: JsonNull)
            }
            file.writeText(json.toString(), Charsets.UTF_8)
            Log.info(
                "archive", "menu changed",
                "self" to self.id, "foods" to program.foods,
                "foodDays" to program.foodDays.size, "openDays" to program.openDays.size, "file" to file.name,
            )
            if (first) prune()
            file
        } catch (e: Exception) {
            // The archive is for learning; a full disk must never stop alerts.
            Log.warn("archive", "couldn't save a menu", "self" to self.id, error = e)
            null
        }
    }

    private fun prune() {
        val weeks = dir.listFiles { f -> f.isDirectory }.orEmpty().sortedByDescending { it.name }
        weeks.drop(keepWeeks).forEach { old ->
            if (old.deleteRecursively()) Log.info("archive", "old week removed", "week" to old.name)
        }
    }
}

/** Short text for logs and alerts, e.g. "self:5,kaleh:5". */
internal fun Map<String, Int>.describe(): String = entries.joinToString(",") { "${it.key}:${it.value}" }.ifEmpty { "none" }
