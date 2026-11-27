package app.shining.push

import app.shining.domain.model.KeyValueStore

/**
 * The user's push choices, kept on the phone only: which vendors they don't want alerts for
 * (every vendor is on by default, new ones included), and whether admin alerts are on.
 */
class PushSettings(private val store: KeyValueStore) {

    fun isVendorEnabled(key: String): Boolean = key !in disabledVendors()

    fun setVendorEnabled(key: String, enabled: Boolean) {
        val disabled = if (enabled) disabledVendors() - key else disabledVendors() + key
        if (disabled.isEmpty()) {
            store.remove(DISABLED_VENDORS)
        } else {
            store.putString(DISABLED_VENDORS, disabled.sorted().joinToString(","))
        }
    }

    var adminAlerts: Boolean
        get() = store.getString(ADMIN_ALERTS) == "true"
        set(value) {
            if (value) store.putString(ADMIN_ALERTS, "true") else store.remove(ADMIN_ALERTS)
        }

    /**
     * Names of the user's cafeterias that the server doesn't watch (dorms, the girls' self for an
     * account the server can't see), found by the fallback check. Empty until it has run.
     */
    var ownSelfNames: List<String>
        get() = store.getString(OWN_SELF_NAMES)?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
        set(value) {
            if (value.isEmpty()) store.remove(OWN_SELF_NAMES) else store.putString(OWN_SELF_NAMES, value.joinToString("\n"))
        }

    private fun disabledVendors(): Set<String> =
        store.getString(DISABLED_VENDORS)?.split(",")?.filter { it.isNotBlank() }?.toSet().orEmpty()

    private companion object {
        const val DISABLED_VENDORS = "PUSH_DISABLED_VENDORS"
        const val ADMIN_ALERTS = "PUSH_ADMIN_ALERTS"
        const val OWN_SELF_NAMES = "PUSH_OWN_SELF_NAMES"
    }
}
