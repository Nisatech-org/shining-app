package app.shining.server.discovery

import app.shining.server.samad.SamadClient
import app.shining.server.samad.samadPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Plan Phase 0: logs in as the user, fetches the cafeteria list and this week's and next week's
 * menus, and writes them to a file so we can see how vendors (کاله, کلانا, ...) appear in Samad.
 *
 * The output holds menus and cafeteria names only. Tokens are never written, and the user's own
 * reservations and user id are removed from each menu.
 */
object Discovery {

    private val prettyJson = Json { prettyPrint = true }

    fun run(username: String, password: CharArray, outputDir: File = File("discovery-output")) {
        val client = SamadClient()
        client.login(username, password)
        password.fill(' ')
        println("Logged in. Reading cafeterias...")

        val selfs = client.get("rest/reservations/selfs").samadPayload() as? JsonArray ?: JsonArray(emptyList())
        println("${selfs.size} cafeterias. Reading this week's and next week's menus...")

        val weeks = weekStarts(LocalDate.now(TEHRAN))
        val programs = buildJsonArray {
            for (self in selfs.filterIsInstance<JsonObject>()) {
                val selfId = self.string("id") ?: continue
                for (week in weeks) {
                    val entry = buildJsonObject {
                        put("selfId", selfId)
                        put("selfName", self.string("name"))
                        put("weekStart", week.toString())
                        try {
                            val payload = client.get(
                                "rest/reservations/programs/v2",
                                mapOf("selfId" to selfId, "weekStartDate" to samadWeekParam(week)),
                            ).samadPayload()
                            put("payload", withoutPersonalData(payload) ?: JsonNull)
                        } catch (e: Exception) {
                            put("error", "${e.javaClass.simpleName}: ${e.message}")
                        }
                    }
                    add(entry)
                    Thread.sleep(POLITE_DELAY_MS)
                }
            }
        }

        val output = buildJsonObject {
            put("generatedAt", ZonedDateTime.now(TEHRAN).toString())
            put("selfs", selfs)
            put("programs", programs)
        }

        outputDir.mkdirs()
        val stamp = ZonedDateTime.now(TEHRAN).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        val dataFile = File(outputDir, "samad-discovery-$stamp.json")
        val summaryFile = File(outputDir, "samad-discovery-$stamp-summary.txt")
        dataFile.writeText(prettyJson.encodeToString(JsonElement.serializer(), output), Charsets.UTF_8)
        summaryFile.writeText(summary(output), Charsets.UTF_8)

        println()
        println("Done. Two files were written:")
        println("  ${dataFile.absolutePath}")
        println("  ${summaryFile.absolutePath}")
        println("Open the summary file (Persian text may look broken in this window).")
    }

    /** Removes the user's own reservations and user id; only the menu itself is kept. */
    private fun withoutPersonalData(payload: JsonElement?): JsonElement? {
        val program = payload as? JsonObject ?: return payload
        return JsonObject(program - setOf("userWeekReserves", "userId"))
    }

    /** A readable report: cafeterias, each menu's food groups, and where vendor names appear. */
    fun summary(output: JsonObject): String = buildString {
        appendLine("Samad discovery, ${output.string("generatedAt")}")
        appendLine()
        appendLine("== Cafeterias ==")
        (output["selfs"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().forEach { self ->
            val extra = self.keys - setOf("id", "name")
            appendLine("  ${self.string("id")}: ${self.string("name")}" +
                if (extra.isEmpty()) "" else "   (other fields: ${extra.joinToString()})")
        }

        val fields = sortedSetOf<String>()
        appendLine()
        appendLine("== Menus ==")
        (output["programs"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().forEach { program ->
            val title = "self ${program.string("selfId")} (${program.string("selfName")}), week of ${program.string("weekStart")}"
            program.string("error")?.let {
                appendLine("  $title: ERROR $it")
                return@forEach
            }
            val entries = programEntries(program["payload"])
            entries.forEach { fields += it.keys }
            val days = entries.mapNotNull { it.string("date")?.take(10) }.distinct().sorted()
            appendLine("  $title: ${entries.size} foods on ${days.size} days ${days.joinToString(prefix = "[", postfix = "]")}")
            groupFoods(entries).forEach { group ->
                appendLine("      foodTypeTitle=${group.foodTypeTitle}  groupId=${group.groupId}  " +
                    "selfIds=${group.selfIds}  x${group.count}  e.g. ${group.sampleFoods.joinToString(" / ")}")
            }
        }

        appendLine()
        appendLine("== Fields in each menu entry ==")
        appendLine("  ${fields.joinToString()}")

        appendLine()
        appendLine("== Where vendor names appear ==")
        val mentions = findVendorMentions(output)
        KNOWN_VENDORS.forEach { vendor ->
            val forVendor = mentions.filter { it.vendorKey == vendor.key }
            if (forVendor.isEmpty()) {
                appendLine("  ${vendor.name}: not found")
            } else {
                appendLine("  ${vendor.name}: ${forVendor.size} times")
                forVendor.groupBy { it.path }.forEach { (path, found) ->
                    appendLine("      at $path  e.g. ${found.map { it.value }.distinct().take(3).joinToString(" / ")}")
                }
            }
        }
    }

    /** Pause between menu requests so the tool doesn't hammer Samad. */
    private const val POLITE_DELAY_MS = 400L
}
