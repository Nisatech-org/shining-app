package app.shining.push

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.shining.MainActivity
import app.shining.R
import app.shining.account.domain.repository.AccountStore
import app.shining.debug.DebugLog
import app.shining.domain.notification.NOTIFICATION_CHANNEL_ID
import app.shining.domain.util.getNextSaturday
import app.shining.reserveinfo.domain.repository.ReservationInfoRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Saturday to Wednesday. */
private const val WEEK_DAYS = 5

/**
 * Whether the Tuesday/Wednesday reminder is worth showing: not when next week's menus aren't out
 * yet, nor when every day of next week is already reserved. When either can't be checked (no
 * internet, Samad down) it reminds anyway, so nobody misses the deadline because of a failed check.
 */
fun shouldRemind(menusUp: Boolean?, reservedDays: Int?): Boolean = when {
    menusUp == false -> false
    reservedDays != null && reservedDays >= WEEK_DAYS -> false
    else -> true
}

/** Runs the check behind a reminder alarm, then shows the reminder if [shouldRemind] says so. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {

    private val statusRepository: ServerStatusRepository by inject()
    private val reservations: ReservationInfoRepository by inject()
    private val accountStore: AccountStore by inject()

    override suspend fun doWork(): Result {
        // A server that couldn't reach Samad lately doesn't know either way.
        val status = statusRepository.fetch().getOrNull()?.takeIf { it.lastCheckOk }
        val menusUp = status?.vendors?.any { it.days.isNotEmpty() }

        val nextWeek = getNextSaturday()
        val reservedDays = if (accountStore.active != null && nextWeek.isNotEmpty()) {
            reservations.getReservationInfo(weekStartDate = nextWeek).getOrNull()
                ?.dayInfoList
                ?.count { day -> day.mealInfo.any { it != null } }
        } else {
            null
        }

        val remind = shouldRemind(menusUp, reservedDays)
        DebugLog.i(
            "reminder",
            "menusUp=${menusUp ?: "unknown"} (server ${if (status == null) "unreachable or stale" else "ok"}), " +
                "reservedDays=${reservedDays ?: "unknown"} of $WEEK_DAYS for week $nextWeek → ${if (remind) "reminding" else "skipping"}",
        )
        if (remind) showReserveReminder(applicationContext)
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            DebugLog.i("reminder", "reminder alarm fired; checking whether to remind")
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ReminderWorker>().build())
        }
    }
}

/** The usual "reserve next week's food" reminder, with one of the friendly texts. */
fun showReserveReminder(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        DebugLog.w("reminder", "not shown: notification permission is off for Shining")
        return
    }
    val open = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_restaurant)
        .setContentTitle(context.getString(R.string.app_name))
        .setContentText(context.getString(REMINDER_TEXTS.random()))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(0, notification)
}

private val REMINDER_TEXTS = listOf(
    R.string.notification_remember_to_reserve_1,
    R.string.notification_remember_to_reserve_2,
    R.string.notification_remember_to_reserve_3,
    R.string.notification_remember_to_reserve_4,
    R.string.notification_remember_to_reserve_5,
    R.string.notification_remember_to_reserve_6,
    R.string.notification_remember_to_reserve_7,
    R.string.notification_remember_to_reserve_8,
    R.string.notification_remember_to_reserve_9,
    R.string.notification_remember_to_reserve_10,
    R.string.notification_remember_to_reserve_11,
    R.string.notification_remember_to_reserve_12,
    R.string.notification_remember_to_reserve_13,
    R.string.notification_remember_to_reserve_14,
    R.string.notification_remember_to_reserve_15,
    R.string.notification_remember_to_reserve_16,
    R.string.notification_remember_to_reserve_17,
)
