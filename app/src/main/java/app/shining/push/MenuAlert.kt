package app.shining.push

import kotlinx.serialization.Serializable

/**
 * What the server sends when vendors publish next week's menu: the same shape as the server's
 * `MenuBatch` (see shining-backend's server/.../watch/Bundle.kt). Dates are yyyy-MM-dd.
 */
@Serializable
data class MenuBatch(
    val weekStart: String,
    val vendors: List<BatchVendor>,
)

@Serializable
data class BatchVendor(
    val key: String,
    val name: String,
    val newDays: List<String>,
    /** True if the vendor's week appeared now, not just more days of it. */
    val firstAppearance: Boolean,
    /** The vendor's cafeterias, to highlight their foods in Reserve. */
    val selfIds: List<Int> = emptyList(),
)

/** A notification: one line, the title. [body] is only for admin alerts, which carry a message. */
data class MenuAlertText(val title: String, val body: String? = null)

/**
 * The longest line a menu notification may have. Android shows the title on one line and cuts a
 * longer one with «…», on a narrow phone from about 40 Persian characters.
 */
const val MAX_ALERT_TITLE = 40

/** One notification: its line, and the vendors it's about. */
data class MenuAlertPart(val text: MenuAlertText, val vendors: List<BatchVendor>)

/**
 * The notifications for [batch], each one line: «رزرو غذای سلف هفته بعد باز شد»,
 * «رزرو غذای سلف و کاله هفته بعد باز شد». When more vendors open together than fit in
 * [MAX_ALERT_TITLE], they're split over as few notifications as possible. Days added after a vendor
 * was announced (a short week filled in later) get their own: «رزرو روزهای تازه‌ی سلف باز شد».
 * Only the vendors the user wants; empty if none of them is in it.
 */
fun menuAlerts(batch: MenuBatch, isVendorEnabled: (String) -> Boolean): List<MenuAlertPart> {
    val vendors = batch.vendors.filter { isVendorEnabled(it.key) && it.newDays.isNotEmpty() }
    val (opened, added) = vendors.partition { it.firstAppearance }
    return split(opened, ::openedTitle) + split(added, ::addedTitle)
}

private fun openedTitle(names: List<String>) = "رزرو غذای ${persianJoin(names)} هفته بعد باز شد"

private fun addedTitle(names: List<String>) = "رزرو روزهای تازه‌ی ${persianJoin(names)} باز شد"

/**
 * Splits [vendors] into the fewest groups whose line fits [MAX_ALERT_TITLE], the longest line as
 * short as possible; a vendor whose line alone is longer gets its own. Vendors keep their order.
 * There are at most a handful of vendors, so every way of grouping them is tried.
 */
private fun split(vendors: List<BatchVendor>, title: (List<String>) -> String): List<MenuAlertPart> {
    if (vendors.isEmpty()) return emptyList()
    fun line(group: List<BatchVendor>) = title(group.map { it.name })
    fun fits(group: List<BatchVendor>) = group.size == 1 || line(group).length <= MAX_ALERT_TITLE

    var best: List<List<BatchVendor>>? = null
    fun score(groups: List<List<BatchVendor>>) = groups.size * 1000 + groups.maxOf { line(it).length }
    fun place(index: Int, groups: List<List<BatchVendor>>) {
        best?.let { if (groups.size > it.size) return }
        if (index == vendors.size) {
            if (best == null || score(groups) < score(best!!)) best = groups
            return
        }
        val vendor = vendors[index]
        groups.forEachIndexed { at, group ->
            val joined = group + vendor
            if (fits(joined)) place(index + 1, groups.toMutableList().also { it[at] = joined })
        }
        place(index + 1, groups + listOf(listOf(vendor)))
    }
    place(0, emptyList())
    return best!!.map { group -> MenuAlertPart(MenuAlertText(line(group)), group) }
}

/** «الف»، «الف و ب»، «الف، ب و ج». */
internal fun persianJoin(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items.single()
    else -> items.dropLast(1).joinToString("، ") + " و " + items.last()
}
