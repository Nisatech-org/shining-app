package app.shining.server.vendors

import app.shining.server.discovery.normalizeForSearch

/**
 * A vendor users can get menu alerts for. In Samad every vendor is its own cafeteria (self), so a
 * vendor is matched by self id first, then by the self's name in case Samad renumbers them.
 *
 * Found with the discovery tool on 2026-11-03 (with the server's account). Every self there serves lunch only.
 */
data class VendorRule(
    val key: String,
    val name: String,
    val selfIds: Set<Int>,
    /** Compared after [normalizeForSearch], so spaces and half-spaces don't matter. */
    val selfNameKeywords: List<String>,
)

val VENDOR_RULES = listOf(
    // Self 1 also sells some کاله dishes ("سینی پیتزا و کراکت (کاله)"). They are published with the
    // self's own menu, so they count as سلف, which is why سلف and کاله seem to arrive together.
    // Self 2 (girls' self) isn't visible to the server's account; its id comes from the old Foodie list.
    VendorRule("self", "سلف", setOf(1, 2), listOf("سلفدانشجویان")),
    VendorRule("kaleh", "کاله", setOf(20, 21), listOf("کاله")),
    // Also called "فسا فود شریف".
    VendorRule("fastfood", "فست‌فود شریف", setOf(22), listOf("فستفود")),
    VendorRule("cleanfood", "کلین‌فود", setOf(23), listOf("کلینفود")),
    VendorRule("kalana", "کلانا", setOf(24), listOf("کلانا")),
    VendorRule("unifood", "یونی‌فود", setOf(25), listOf("یونیفود")),
)

/** The vendor a cafeteria belongs to, or null for one we don't send alerts about (dorms, ...). */
fun vendorFor(selfId: Int, selfName: String, rules: List<VendorRule> = VENDOR_RULES): VendorRule? {
    rules.firstOrNull { selfId in it.selfIds }?.let { return it }
    val name = normalizeForSearch(selfName)
    return rules.firstOrNull { rule -> rule.selfNameKeywords.any { normalizeForSearch(it) in name } }
}
