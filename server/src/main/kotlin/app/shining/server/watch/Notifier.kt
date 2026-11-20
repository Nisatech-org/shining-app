package app.shining.server.watch

import app.shining.server.log.Log
import kotlinx.serialization.json.Json

/** Sends alerts out: Firebase in production ([app.shining.server.push.FcmNotifier]), the log otherwise. */
interface Notifier {
    /** Tells users that [batch]'s vendors published next week's menu (or more of it). */
    suspend fun sendMenus(batch: MenuBatch)

    /** Tells the admin something about the server itself, such as checks failing. */
    suspend fun alertAdmin(message: String)
}

/** Used when no Firebase key is set: the alert only goes to the log, with its full content. */
class LogNotifier(private val json: Json = Json) : Notifier {
    override suspend fun sendMenus(batch: MenuBatch) {
        Log.info("push", "would send menus (no Firebase key set)", "batch" to json.encodeToString(MenuBatch.serializer(), batch))
    }

    override suspend fun alertAdmin(message: String) {
        Log.info("push", "would send admin alert (no Firebase key set)", "message" to message)
    }
}
