package app.shining.market.domain

import app.shining.domain.model.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "ورود مهمان" on the login screen: someone without a Samad login (no student number, or no
 * password) can still use the market. A guest sees only the Market tab, buys, and sells by
 * typing the forget code; their listings carry no verified tick. Logging in with Samad later
 * ends guest mode.
 *
 * The guest's market login is kept like an account's, under [USERNAME], which no Samad
 * username can be.
 */
class GuestMode(private val store: KeyValueStore) {

    private val _chosen = MutableStateFlow(store.getString(KEY) == "1")

    /** The user chose to continue as a guest and hasn't logged in with Samad since. */
    val chosen: StateFlow<Boolean> = _chosen.asStateFlow()

    fun enter() {
        store.putString(KEY, "1")
        _chosen.value = true
    }

    fun leave() {
        store.remove(KEY)
        _chosen.value = false
    }

    companion object {
        private const val KEY = "market_guest_mode"

        /** The market session key of the guest. Samad usernames are digits and letters, never "~". */
        const val USERNAME = "~guest"
    }
}
