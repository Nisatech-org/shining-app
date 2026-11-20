package app.shining.server.discovery

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** A food vendor and the words its name may show up as in Samad's data. */
data class Vendor(val key: String, val name: String, val keywords: List<String>)

/** The known vendors. سلف itself is left out: the word appears in every cafeteria's name. */
val KNOWN_VENDORS = listOf(
    Vendor("kaleh", "کاله", listOf("کاله", "kaleh", "kalleh")),
    Vendor("kalana", "کلانا", listOf("کلانا", "kalana")),
    Vendor("cleanfood", "کلین فود", listOf("کلین", "cleanfood")),
    Vendor("unifood", "یونی فود", listOf("یونیفود", "یونی", "unifood")),
    Vendor("fasafood", "فسا فود شریف", listOf("فسافود", "فسا", "fasa")),
)

/** Where a vendor's name was found: the JSON path (array indices as `[*]`) and the text there. */
data class VendorMention(val vendorKey: String, val path: String, val value: String)

/**
 * Makes Persian text comparable: Arabic ي/ك become ی/ک, and spaces, half-spaces and tatweel are
 * removed, so "کلین فود", "کلین‌فود" and "كلين فود" all match.
 */
fun normalizeForSearch(text: String): String = text
    .lowercase()
    .replace('ي', 'ی')
    .replace('ى', 'ی')
    .replace('ك', 'ک')
    .replace("ـ", "")
    .filterNot { it.isWhitespace() || it == '‌' || it == '‏' || it == '‎' }

/** Every string in [root] that mentions one of [vendors], with the path it was found at. */
fun findVendorMentions(
    root: JsonElement,
    vendors: List<Vendor> = KNOWN_VENDORS,
): List<VendorMention> {
    val found = mutableListOf<VendorMention>()
    val keywords = vendors.map { vendor -> vendor to vendor.keywords.map(::normalizeForSearch) }

    fun walk(element: JsonElement, path: String) {
        when (element) {
            is JsonObject -> element.forEach { (key, child) -> walk(child, "$path.$key") }
            is JsonArray -> element.forEach { child -> walk(child, "$path[*]") }
            is JsonNull -> Unit
            is JsonPrimitive -> {
                if (!element.isString) return
                val text = element.content
                val normalized = normalizeForSearch(text)
                keywords.forEach { (vendor, words) ->
                    if (words.any { it in normalized }) found += VendorMention(vendor.key, path, text)
                }
            }
        }
    }
    walk(root, "$")
    return found
}

/** One kind of food in a program: entries that share a food type title and group. */
data class FoodGroup(
    val foodTypeTitle: String?,
    val groupId: String?,
    val selfIds: Set<String>,
    val count: Int,
    val sampleFoods: List<String>,
)

/**
 * Groups program entries (the objects inside `selfWeekPrograms`) by `foodTypeTitle` and `groupId`,
 * the two fields most likely to tell vendors apart if they share one cafeteria.
 */
fun groupFoods(entries: List<JsonObject>): List<FoodGroup> = entries
    .groupBy { it.string("foodTypeTitle") to it.string("groupId") }
    .map { (key, items) ->
        FoodGroup(
            foodTypeTitle = key.first,
            groupId = key.second,
            selfIds = items.mapNotNull { it.string("selfId") }.toSortedSet(),
            count = items.size,
            sampleFoods = items.mapNotNull { it.string("foodName") }.distinct().take(4),
        )
    }
    .sortedByDescending { it.count }

/** The entries of a `programs/v2` payload: `selfWeekPrograms` is a list of days, each a list of foods. */
fun programEntries(payload: JsonElement?): List<JsonObject> {
    val days = (payload as? JsonObject)?.get("selfWeekPrograms") as? JsonArray ?: return emptyList()
    return days.flatMap { day -> (day as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty() }
}

internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
