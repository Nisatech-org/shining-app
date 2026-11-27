package app.shining.market.domain

import app.shining.account.domain.repository.AccountScopedData
import app.shining.domain.model.KeyValueStore
import app.shining.market.api.TradeView
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** An account's market login: the token from joining and its own push topic. */
@Serializable
data class MarketSession(val token: String, val pushTopic: String)

/**
 * Each account's [MarketSession], kept on the phone. Joining subscribes the phone to the
 * account's topic; removing the account (or leaving the market) unsubscribes it.
 */
class MarketSessions(
    private val store: KeyValueStore,
    private val json: Json,
    /** Subscribes to (true) or leaves (false) a Firebase topic. */
    private val setTopic: (String, Boolean) -> Unit,
) : AccountScopedData {

    fun get(username: String): MarketSession? = store.getString(key(username))?.let {
        runCatching { json.decodeFromString(MarketSession.serializer(), it) }.getOrNull()
    }

    fun save(username: String, session: MarketSession) {
        store.putString(key(username), json.encodeToString(MarketSession.serializer(), session))
        setTopic(session.pushTopic, true)
    }

    fun remove(username: String) {
        get(username)?.let { setTopic(it.pushTopic, false) }
        store.remove(key(username))
    }

    /** At every start: listen on the topics of every account that joined (subscribing again is harmless). */
    fun subscribeAll(usernames: List<String>) {
        usernames.mapNotNull(::get).forEach { setTopic(it.pushTopic, true) }
    }

    override suspend fun copyAccountData(fromUsername: String?, toUsername: String) = Unit

    override suspend fun deleteAccountData(username: String) = remove(username)

    private fun key(username: String) = "market_session_$username"
}

/**
 * The account's trades as last seen, kept on the phone as well as on the
 * server. A bought code stays here after the server forgets it.
 */
class MarketHistory(
    private val store: KeyValueStore,
    private val json: Json,
) : AccountScopedData {
    private val serializer = ListSerializer(TradeView.serializer())

    fun all(username: String): List<TradeView> = store.getString(key(username))?.let {
        runCatching { json.decodeFromString(serializer, it) }.getOrNull()
    }.orEmpty()

    fun get(username: String, id: Long): TradeView? = all(username).firstOrNull { it.id == id }

    /** Saves the latest view of these trades. A code once seen is never dropped by a later view without it. */
    @Synchronized
    fun save(username: String, trades: List<TradeView>) {
        if (trades.isEmpty()) return
        val saved = all(username).associateBy { it.id }.toMutableMap()
        for (trade in trades) {
            val code = trade.code ?: saved[trade.id]?.code
            saved[trade.id] = trade.copy(code = code)
        }
        val list = saved.values.sortedByDescending { it.id }.take(MAX)
        store.putString(key(username), json.encodeToString(serializer, list))
    }

    override suspend fun copyAccountData(fromUsername: String?, toUsername: String) = Unit

    override suspend fun deleteAccountData(username: String) = store.remove(key(username))

    private fun key(username: String) = "market_history_$username"

    private companion object {
        const val MAX = 200
    }
}

/**
 * The account's reservations it has on the market right now (picked from its reservations when
 * listing), so Home can grey them out. Refreshed from the server's listings whenever the Market
 * tab loads.
 */
class ListedReservations(
    private val store: KeyValueStore,
) : AccountScopedData {

    fun get(username: String): Set<Int> =
        store.getString(key(username)).orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

    fun set(username: String, ids: Set<Int>) {
        if (ids.isEmpty()) store.remove(key(username)) else store.putString(key(username), ids.sorted().joinToString(","))
    }

    override suspend fun copyAccountData(fromUsername: String?, toUsername: String) = Unit

    override suspend fun deleteAccountData(username: String) = store.remove(key(username))

    private fun key(username: String) = "market_listed_$username"
}
