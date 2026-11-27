package app.shining.push

import app.shining.debug.DebugLog
import com.google.firebase.messaging.FirebaseMessaging

/**
 * The Firebase topics the app listens on. The server sends to topics, never to single phones, so
 * it needs no list of users or devices.
 */
object PushTopics {
    const val MENUS = "menus"
    const val ADMIN = "admin"

    private const val TAG = "topics"

    /** Called at every start; subscribing again is harmless. */
    fun subscribeAll(settings: PushSettings) {
        set(MENUS, true)
        set(ADMIN, settings.adminAlerts)
    }

    fun set(topic: String, subscribed: Boolean) {
        val action = if (subscribed) "subscribe to" else "unsubscribe from"
        try {
            val messaging = FirebaseMessaging.getInstance()
            val task = if (subscribed) messaging.subscribeToTopic(topic) else messaging.unsubscribeFromTopic(topic)
            task.addOnSuccessListener { DebugLog.i(TAG, "$action '$topic': done") }
            task.addOnFailureListener { DebugLog.w(TAG, "$action '$topic' failed, will retry next start", it) }
        } catch (e: IllegalStateException) {
            // Builds without google-services.json (CI, forks) have no Firebase; the app works without push.
            DebugLog.w(TAG, "Firebase isn't set up in this build, so no push", e)
        }
    }
}
