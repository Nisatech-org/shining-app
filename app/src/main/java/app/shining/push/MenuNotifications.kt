package app.shining.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.shining.MainActivity
import app.shining.R
import app.shining.debug.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/** Where a menu notification should take the user: a day in Reserve, with some cafeterias' foods highlighted. */
data class ReserveTarget(val day: String, val highlightSelfIds: Set<Int>)

/**
 * Hands a [ReserveTarget] from a tapped notification to the Reserve screen. The activity sets it,
 * Home forwards to Reserve once the user is logged in, and Reserve takes it.
 */
object ReserveDeepLink {
    private const val EXTRA_DAY = "app.shining.reserve.DAY"
    private const val EXTRA_SELF_IDS = "app.shining.reserve.SELF_IDS"

    private val pending = MutableStateFlow<ReserveTarget?>(null)
    val target: StateFlow<ReserveTarget?> = pending.asStateFlow()

    fun putExtras(intent: Intent, target: ReserveTarget): Intent = intent
        .putExtra(EXTRA_DAY, target.day)
        .putExtra(EXTRA_SELF_IDS, target.highlightSelfIds.toIntArray())

    /** Reads a target from an activity's intent, if it came from a menu notification. */
    fun receive(intent: Intent?) {
        val day = intent?.getStringExtra(EXTRA_DAY) ?: return
        pending.value = ReserveTarget(day, intent.getIntArrayExtra(EXTRA_SELF_IDS)?.toSet().orEmpty())
        intent.removeExtra(EXTRA_DAY)
        DebugLog.i("deeplink", "opened from a menu notification: ${pending.value}")
    }

    /** Returns the pending target and clears it, so it's used once. */
    fun take(): ReserveTarget? = pending.value.also {
        if (it != null) DebugLog.i("deeplink", "Reserve opened for $it")
        pending.value = null
    }
}

/** One notification to post: its line, and where tapping it goes. */
data class AnnouncementPart(val text: MenuAlertText, val target: ReserveTarget)

/** The notifications for a batch of not-yet-announced vendors, and which were left out because they're off. */
data class AnnouncementPlan(val parts: List<AnnouncementPart>, val skipped: List<String>)

/** Each notification opens Reserve on its own vendors' first new day, highlighting their cafeterias. */
fun planAnnouncement(fresh: MenuBatch, isVendorEnabled: (String) -> Boolean): AnnouncementPlan {
    val off = fresh.vendors.filterNot { isVendorEnabled(it.key) }
    val parts = menuAlerts(fresh, isVendorEnabled).map { part ->
        AnnouncementPart(
            text = part.text,
            target = ReserveTarget(
                day = part.vendors.flatMap { it.newDays }.min().take(10),
                highlightSelfIds = part.vendors.flatMap { it.selfIds }.toSet(),
            ),
        )
    }
    return AnnouncementPlan(parts, off.map { it.key })
}

private fun List<BatchVendor>.describe() = joinToString { "${it.key}:${it.newDays.joinToString("+") { day -> day.takeLast(5) }}" }

/**
 * Turns menu batches into notifications, for both the push receiver and the fallback check:
 * leaves out what was already announced and the vendors the user turned off, then posts one
 * notification that opens Reserve on the first new day.
 */
class MenuNotifications(
    private val context: Context,
    private val settings: PushSettings,
    private val announced: AnnouncedAlerts,
) {
    /** Notification ids, starting from the time so they don't repeat across app restarts either. */
    private val nextId = AtomicInteger((System.currentTimeMillis() / 1000 % 1_000_000_000).toInt())

    /** Returns true if a notification was posted. [source] says where the batch came from, for the log. */
    fun announce(batch: MenuBatch, source: String): Boolean {
        val fresh = announced.unannounced(batch)
        val plan = planAnnouncement(fresh, settings::isVendorEnabled)
        DebugLog.i(
            TAG,
            "$source: week ${batch.weekStart}, in batch [${batch.vendors.describe()}], " +
                "new [${fresh.vendors.describe()}], turned off [${plan.skipped.joinToString()}]",
        )
        if (fresh.vendors.isEmpty()) {
            DebugLog.i(TAG, "$source: nothing new, everything was already announced")
            return false
        }
        // Marked even when nothing is shown (vendor turned off), so turning it on later doesn't
        // bring up an old alert.
        announced.markAnnounced(fresh)

        if (plan.parts.isEmpty()) return false.also { DebugLog.i(TAG, "$source: nothing to show, every new vendor is turned off") }
        var shown = false
        for (part in plan.parts) {
            val posted = post(
                channelId = MENUS_CHANNEL,
                channelName = context.getString(R.string.push_channel_menus),
                title = part.text.title,
                body = part.text.body,
                intent = ReserveDeepLink.putExtras(Intent(context, MainActivity::class.java), part.target),
            )
            if (posted) DebugLog.i(TAG, "$source: shown \"${part.text.title}\", opens ${part.target.day} highlighting ${part.target.highlightSelfIds}")
            shown = shown || posted
        }
        return shown
    }

    fun announceAdmin(message: String): Boolean {
        if (!settings.adminAlerts) {
            DebugLog.i(TAG, "admin message ignored: admin alerts are off on this phone")
            return false
        }
        DebugLog.i(TAG, "admin message: $message")
        return post(
            channelId = ADMIN_CHANNEL,
            channelName = context.getString(R.string.push_channel_admin),
            title = context.getString(R.string.push_admin_title),
            body = message,
            intent = Intent(context, MainActivity::class.java),
        )
    }

    private fun post(channelId: String, channelName: String, title: String, body: String?, intent: Intent): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            DebugLog.w(TAG, "not shown: notification permission is off for Shining")
            return false
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            DebugLog.w(TAG, "notifications are turned off for Shining in system settings; Android will hide this")
        }
        ensureChannel(context, channelId, channelName)
        // Its own id each time: a split alert posts its notifications in the same millisecond, and
        // a shared id would make each replace the one before.
        val id = nextId.incrementAndGet()
        val open = PendingIntent.getActivity(
            context,
            id,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_restaurant)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
        // Menu alerts are one line (the title); admin alerts add their message below it.
        if (!body.isNullOrBlank()) {
            builder.setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }
        val notification = builder.build()
        NotificationManagerCompat.from(context).notify(id, notification)
        return true
    }

    companion object {
        private const val TAG = "alerts"
        const val MENUS_CHANNEL = "menu_alerts"
        const val ADMIN_CHANNEL = "admin_alerts"

        /** Separate channels, so users can silence menu alerts and reminders independently. */
        fun ensureChannel(context: Context, id: String, name: String) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(id) == null) {
                manager.createNotificationChannel(NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH))
            }
        }
    }
}
