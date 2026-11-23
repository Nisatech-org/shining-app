package app.shining.push

import app.shining.debug.DebugLog
import app.shining.market.push.MarketNotifications
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Receives the server's data messages. The server only says which vendors published which days;
 * whether and how to show it is decided on the phone, by [MenuNotifications].
 */
class MenuMessagingService : FirebaseMessagingService(), KoinComponent {

    private val notifications: MenuNotifications by inject()
    private val marketNotifications: MarketNotifications by inject()
    private val json = Json { ignoreUnknownKeys = true }

    override fun onMessageReceived(message: RemoteMessage) {
        // How late it arrived and whether Android lowered its priority are the first things to
        // look at when a push seems slow.
        val delaySeconds = if (message.sentTime > 0) (System.currentTimeMillis() - message.sentTime) / 1000 else -1
        DebugLog.i(
            TAG,
            "push received: type=${message.data["type"]} id=${message.messageId} from=${message.from} " +
                "priority=${message.priority}/${message.originalPriority} delay=${delaySeconds}s",
        )
        when (message.data["type"]) {
            "menus" -> {
                val raw = message.data["batch"] ?: return DebugLog.w(TAG, "menu push without a batch")
                val batch = runCatching { json.decodeFromString(MenuBatch.serializer(), raw) }
                    .onFailure { DebugLog.w(TAG, "unreadable menu batch: $raw", it) }
                    .getOrNull() ?: return
                notifications.announce(batch, source = "push")
            }

            "admin" -> message.data["message"]?.let { notifications.announceAdmin(it) }

            // A market trade moved on, or a listing reached the user's offer (server/.../market).
            "market" -> marketNotifications.show(message.data)

            else -> DebugLog.w(TAG, "unknown push type, ignored: ${message.data.keys}")
        }
    }

    // The app only uses topics, so the device token itself is never needed.
    override fun onNewToken(token: String) {
        DebugLog.i(TAG, "Firebase gave this phone a new push token")
    }

    override fun onDeletedMessages() {
        DebugLog.w(TAG, "Firebase dropped pending messages (phone was offline too long); the fallback check will catch up")
    }

    private companion object {
        const val TAG = "push"
    }
}
