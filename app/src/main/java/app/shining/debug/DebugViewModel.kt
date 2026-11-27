package app.shining.debug

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import app.shining.BuildConfig
import app.shining.account.domain.repository.AccountStore
import app.shining.domain.ui.viewmodel.ShiningViewModel
import app.shining.push.AnnouncedAlerts
import app.shining.push.DEFAULT_VENDORS
import app.shining.push.MenuCheckWorker
import app.shining.push.OWN_SELFS_KEY
import app.shining.push.PushSettings
import app.shining.push.ReminderWorker
import app.shining.push.ServerStatusRepository
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The hidden debug screen (Settings › tap the version 5 times): what the app knows about push,
 * the server and its background jobs, buttons to run the checks now, and the log with a share
 * button. Meant for the developers, so it's in English.
 */
class DebugViewModel(
    private val context: Context,
    private val settings: PushSettings,
    private val status: ServerStatusRepository,
    private val announced: AnnouncedAlerts,
    private val accountStore: AccountStore,
) : ShiningViewModel() {

    val info = mutableStateOf<List<Pair<String, String>>>(emptyList())
    val log = mutableStateOf("")

    fun refresh() {
        viewModelScope.launch {
            info.value = withContext(Dispatchers.IO) { collectInfo() }
            log.value = DebugLog.read()
        }
    }

    fun runMenuCheck() {
        MenuCheckWorker.runNow(context)
        refresh()
    }

    fun runReminderCheck() {
        ReminderWorker.enqueue(context)
        refresh()
    }

    fun clearLog() {
        DebugLog.clear()
        DebugLog.i("debug", "log cleared by hand")
        refresh()
    }

    /** The info and the newest part of the log, small enough to share as text. */
    fun shareText(): String = report(MAX_SHARE_CHARS)

    /** The same, shorter: the clipboard can't hold as much, and it gets pasted into a chat. */
    fun copyText(): String = report(MAX_COPY_CHARS)

    private fun report(maxLogChars: Int): String {
        val header = info.value.joinToString("\n") { (key, value) -> "$key: $value" }
        return "Shining debug report\n$header\n\n--- log (newest last) ---\n${DebugLog.read().takeLast(maxLogChars)}"
    }

    private fun collectInfo(): List<Pair<String, String>> {
        val notificationsPermitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val cached = status.cached()
        val job = runCatching {
            WorkManager.getInstance(context).getWorkInfosForUniqueWork(MenuCheckWorker.UNIQUE_NAME).get().firstOrNull()
        }.getOrNull()
        val off = (DEFAULT_VENDORS.map { it.key } + OWN_SELFS_KEY).filterNot(settings::isVendorEnabled)
        return listOf(
            "App" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}",
            "Device" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}",
            "Now (Tehran)" to tehran(System.currentTimeMillis()),
            "Account" to (accountStore.active?.username?.let { "•••" + it.takeLast(3) } ?: "none"),
            "Server" to BuildConfig.SHINING_SERVER_URL,
            "Firebase" to if (FirebaseApp.getApps(context).isNotEmpty()) "configured" else "missing (no google-services.json in this build)",
            "Notifications" to "permission ${if (notificationsPermitted) "granted" else "MISSING"}, " +
                "app notifications ${if (NotificationManagerCompat.from(context).areNotificationsEnabled()) "on" else "OFF in system settings"}",
            "Admin alerts" to if (settings.adminAlerts) "on (long-press the version to turn off)" else "off",
            "Vendors turned off" to off.ifEmpty { listOf("none") }.joinToString(),
            "Own cafeterias found" to settings.ownSelfNames.ifEmpty { listOf("none yet") }.joinToString(),
            "Announced" to announced.describe(),
            "Saved server status" to (cached?.let { s ->
                "week ${s.week}, saved ${status.cachedAt()?.let(::tehran) ?: "?"}, lastCheckOk=${s.lastCheckOk}, " +
                    s.vendors.joinToString { "${it.key}=${it.state}(${it.days.size})" }
            } ?: "never reached the server"),
            "Menu check job" to (job?.let {
                "${it.state}, attempts ${it.runAttemptCount}, next ${if (it.nextScheduleTimeMillis == Long.MAX_VALUE) "?" else tehran(it.nextScheduleTimeMillis)}"
            } ?: "not scheduled"),
        )
    }

    private fun tehran(millis: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("Asia/Tehran") }
        .format(Date(millis))

    private companion object {
        /** Sharing goes through Android's Binder, which rejects very large texts. */
        const val MAX_SHARE_CHARS = 150_000
        const val MAX_COPY_CHARS = 60_000
    }
}
