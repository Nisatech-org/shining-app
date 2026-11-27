package app.shining.market.push

import android.Manifest
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
import app.shining.push.MenuNotifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where a market notification takes the user: a trade, or a meal's board. */
sealed interface MarketTarget {
    data class Trade(val id: Long) : MarketTarget
    data class Meal(val key: String) : MarketTarget
}

/** Hands a [MarketTarget] from a tapped notification to the app's navigation, once. */
object MarketDeepLink {
    private const val EXTRA_TRADE = "app.shining.market.TRADE"
    private const val EXTRA_MEAL = "app.shining.market.MEAL"

    private val pending = MutableStateFlow<MarketTarget?>(null)
    val target: StateFlow<MarketTarget?> = pending.asStateFlow()

    fun putExtras(intent: Intent, target: MarketTarget): Intent = when (target) {
        is MarketTarget.Trade -> intent.putExtra(EXTRA_TRADE, target.id)
        is MarketTarget.Meal -> intent.putExtra(EXTRA_MEAL, target.key)
    }

    fun receive(intent: Intent?) {
        intent ?: return
        val trade = intent.getLongExtra(EXTRA_TRADE, -1L)
        val meal = intent.getStringExtra(EXTRA_MEAL)
        pending.value = when {
            trade > 0 -> MarketTarget.Trade(trade)
            meal != null -> MarketTarget.Meal(meal)
            else -> return
        }
        intent.removeExtra(EXTRA_TRADE)
        intent.removeExtra(EXTRA_MEAL)
        DebugLog.i("deeplink", "opened from a market notification: ${pending.value}")
    }

    fun take(): MarketTarget? = pending.value.also { pending.value = null }
}

/**
 * Shows the server's market pushes (a trade moved on, a listing reached your offer). The server
 * writes the text; the phone only decides where tapping goes.
 */
class MarketNotifications(private val context: Context) {

    fun show(data: Map<String, String>): Boolean {
        val title = data["title"]?.takeIf { it.isNotBlank() } ?: return false
        val body = data["body"].orEmpty()
        val target = data["tradeId"]?.toLongOrNull()?.let { MarketTarget.Trade(it) }
            ?: data["mealKey"]?.let { MarketTarget.Meal(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            DebugLog.w(TAG, "market push not shown: notification permission is off")
            return false
        }
        MenuNotifications.ensureChannel(context, CHANNEL, context.getString(R.string.push_channel_market))
        // One notification per trade: each step replaces the last, so the shade shows where it is now.
        val id = when (target) {
            is MarketTarget.Trade -> (target.id % 100_000).toInt() + 200_000
            is MarketTarget.Meal -> target.key.hashCode() % 100_000 + 300_000
            null -> 399_999
        }
        val intent = Intent(context, MainActivity::class.java).let { if (target != null) MarketDeepLink.putExtras(it, target) else it }
        val open = PendingIntent.getActivity(
            context,
            id,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_restaurant)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
        DebugLog.i(TAG, "shown: ${data["kind"]} → $target")
        return true
    }

    companion object {
        private const val TAG = "market"
        const val CHANNEL = "market"
    }
}
