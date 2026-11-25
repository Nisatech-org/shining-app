package app.shining.push

/** One switch in Settings › next-week menu alerts. */
data class MenuVendorToggle(val key: String, val name: String, val enabled: Boolean)

/** Key for alerts about the user's own cafeterias that the server doesn't watch, like dorm selfs. */
const val OWN_SELFS_KEY = "own"

/**
 * The vendors to offer in Settings: the server's list (or the built-in one before it loads), plus
 * one switch for the user's own other cafeterias when the phone has found any.
 */
fun menuVendorToggles(
    status: ServerStatus?,
    settings: PushSettings,
    ownSelfsName: String,
): List<MenuVendorToggle> {
    val vendors = status?.vendors?.takeIf { it.isNotEmpty() } ?: DEFAULT_VENDORS
    val toggles = vendors.map { MenuVendorToggle(it.key, it.name, settings.isVendorEnabled(it.key)) }
    val own = if (settings.ownSelfNames.isNotEmpty()) {
        listOf(MenuVendorToggle(OWN_SELFS_KEY, ownSelfsName, settings.isVendorEnabled(OWN_SELFS_KEY)))
    } else {
        emptyList()
    }
    return toggles + own
}
