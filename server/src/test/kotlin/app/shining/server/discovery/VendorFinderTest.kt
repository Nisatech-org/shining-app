package app.shining.server.discovery

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VendorFinderTest {

    private val sample = Json.parseToJsonElement(
        """
        {
          "selfs": [
            {"id": 1, "name": "مرکزی - سلف دانشجویان آقا"},
            {"id": 58, "name": "رستوران های مکمل - کلین‌فود"}
          ],
          "programs": [
            {"selfId": "1", "payload": {"selfWeekPrograms": [[
              {"date": "2026-11-07 00:00:00", "foodName": "چلو جوجه کباب", "foodTypeTitle": "غذای اصلی", "groupId": 3, "selfId": 1},
              {"date": "2026-11-07 00:00:00", "foodName": "ساندویچ مرغ", "foodTypeTitle": "كاله", "groupId": 9, "selfId": 1}
            ], [
              {"date": "2026-11-08 00:00:00", "foodName": "قرمه سبزی", "foodTypeTitle": "غذای اصلی", "groupId": 3, "selfId": 1}
            ]]}}
          ]
        }
        """
    ).jsonObject

    @Test
    fun `finds vendors written with half-spaces and Arabic letters`() {
        val mentions = findVendorMentions(sample)

        val clean = mentions.single { it.vendorKey == "cleanfood" }
        assertEquals("$.selfs[*].name", clean.path)

        // "كاله" with an Arabic kaf still counts as کاله.
        val kaleh = mentions.single { it.vendorKey == "kaleh" }
        assertEquals("$.programs[*].payload.selfWeekPrograms[*][*].foodTypeTitle", kaleh.path)
    }

    @Test
    fun `reports nothing for vendors that are not there`() {
        val mentions = findVendorMentions(sample)
        assertTrue(mentions.none { it.vendorKey == "kalana" || it.vendorKey == "fasafood" })
    }

    @Test
    fun `flattens days and groups foods by type and group`() {
        val payload = (sample["programs"] as kotlinx.serialization.json.JsonArray)[0]
            .jsonObject["payload"]
        val entries = programEntries(payload)
        assertEquals(3, entries.size)

        val groups = groupFoods(entries)
        val main = groups.first()
        assertEquals("غذای اصلی", main.foodTypeTitle)
        assertEquals("3", main.groupId)
        assertEquals(2, main.count)
        assertEquals(setOf("1"), main.selfIds)
    }

    @Test
    fun `a menu without selfWeekPrograms has no entries`() {
        assertEquals(emptyList(), programEntries(JsonObject(emptyMap())))
        assertEquals(emptyList(), programEntries(null))
    }

    @Test
    fun `normalizing removes spaces and half-spaces`() {
        assertEquals("کلینفود", normalizeForSearch("کلین‌ فود"))
        assertEquals("یونیفود", normalizeForSearch("يوني فود"))
    }
}
