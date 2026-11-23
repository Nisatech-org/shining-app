package app.shining.domain.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.shining.push.ReminderWorker

/**
 * Fired by the Tuesday 20:00 and Wednesday 15:00 alarms. It doesn't show the reminder itself: it
 * starts a short check (are menus out, is anything left to reserve?), see [ReminderWorker].
 *
 * The old version asked for the notification permission from here by casting the context to an
 * Activity, which crashed: a receiver's context is never an Activity.
 */
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderWorker.enqueue(context)
    }
}
