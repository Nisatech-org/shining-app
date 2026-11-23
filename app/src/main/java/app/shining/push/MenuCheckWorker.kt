package app.shining.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.shining.account.domain.repository.AccountStore
import app.shining.debug.DebugLog
import app.shining.domain.util.getNextSaturday
import app.shining.order.domain.repository.OrderFoodRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * The phone's own safety net for menu alerts, every ~15 minutes (Android's shortest) on Sunday to Wednesday:
 * 1. asks the Shining server for its status, catching any push that didn't arrive;
 * 2. checks the user's own cafeterias that the server can't see (dorms, a self the server's
 *    account doesn't have) directly on Samad, with the user's login. New days wait for the next
 *    check ([AlertWindow]) so days opening close together share one alert, like the server.
 * The server's cafeterias are the server's job alone: the phone never checks them, even when the
 * server is down, and never reports anything to it (since 1.9.0). A day counts once it has food
 * that can be reserved, as on the server. Anything already announced is skipped, so this never
 * repeats a push.
 */
class MenuCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {

    private val statusRepository: ServerStatusRepository by inject()
    private val notifications: MenuNotifications by inject()
    private val settings: PushSettings by inject()
    private val orderRepository: OrderFoodRepository by inject()
    private val accountStore: AccountStore by inject()
    private val announced: AnnouncedAlerts by inject()
    private val alertWindow: AlertWindow by inject()

    override suspend fun doWork(): Result {
        val forced = inputData.getBoolean(KEY_FORCE, false)
        if (!forced && !isCheckDay(Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran")))) {
            DebugLog.d(TAG, "skipped: checks run Sunday to Wednesday only")
            return Result.success()
        }
        DebugLog.i(TAG, "check started${if (forced) " (run by hand)" else ""}, attempt ${runAttemptCount + 1}")

        val fetched = statusRepository.fetch().getOrNull()
        fetched?.let { notifications.announce(it.publishedBatch(), source = "fallback/server") }

        if (accountStore.active == null) {
            DebugLog.i(TAG, "no logged-in account: own cafeterias not checked")
        } else {
            // The saved status is enough to tell which cafeterias are the server's.
            runCatching { checkOwnSelfs(fetched ?: statusRepository.cached()) }
                .onFailure { DebugLog.w(TAG, "own cafeteria check failed", it) }
        }
        DebugLog.i(TAG, "check finished")
        return Result.success()
    }

    private suspend fun checkOwnSelfs(status: ServerStatus?) {
        val weekStart = status?.week ?: getNextSaturday().take(10)
        if (weekStart.isEmpty()) return DebugLog.w(TAG, "can't tell next week's date on this Android version")
        val selfs = orderRepository.getAvailableSelfs().getOrElse {
            return DebugLog.w(TAG, "couldn't load the account's cafeterias: ${it.message}")
        }
        val vendors = status?.vendors?.takeIf { it.isNotEmpty() } ?: DEFAULT_VENDORS
        val mine = selfsToCheck(selfs.map { it.id }, status).toSet()
        val uncovered = selfs.filter { it.id in mine }
        DebugLog.i(
            TAG,
            "account has cafeterias ${selfs.map { it.id }}, the server's: ${(selfs.map { it.id } - mine).sorted()}, " +
                "checking here: ${uncovered.map { "${it.id} ${it.name}" }}",
        )
        if (uncovered.isEmpty()) return

        val days = uncovered.associate { self ->
            val program = orderRepository.getReserveProgram(self.id, "$weekStart 00:00:00").getOrNull()
            if (program == null) DebugLog.w(TAG, "couldn't load cafeteria ${self.id}'s menu")
            // Only days with food that can be reserved now, like the server.
            self.id to program?.selfWeekProgramDtoList.orEmpty().flatten().filterNot { it.reserveRuleViolated }.map { it.date.take(10) }
        }
        val result = ownSelfsBatch(weekStart, uncovered.map { it.id to it.name }, days, vendors)
        settings.ownSelfNames = result.ownSelfNames
        if (result.batch.vendors.isEmpty()) {
            DebugLog.i(TAG, "own cafeterias: nothing published for $weekStart yet")
            return
        }
        // New days wait for the next check, so days opening close together share one alert.
        val fresh = announced.unannounced(result.batch)
        val ready = alertWindow.ready(result.batch, fresh, System.currentTimeMillis())
        val held = fresh.vendors.map { it.key } - ready.vendors.map { it.key }.toSet()
        if (held.isNotEmpty()) DebugLog.i(TAG, "own cafeterias: new days of $held go out at the next check")
        if (ready.vendors.isNotEmpty()) notifications.announce(ready, source = "fallback/own")
    }

    companion object {
        private const val TAG = "fallback"
        private const val WORK_NAME = "menu-check"
        private const val KEY_FORCE = "force"

        /** Sunday to Wednesday, when next week's menus come out and reservations close. */
        fun isCheckDay(tehranNow: Calendar): Boolean =
            tehranNow.get(Calendar.DAY_OF_WEEK) in listOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY)

        /**
         * Which of the account's cafeterias ([selfIds]) the phone checks itself: those the server
         * can't see. The server says which it covers; until it has (or with no status at all),
         * every vendor's cafeteria counts as the server's, so the phone checks only dorms.
         */
        fun selfsToCheck(selfIds: List<Int>, status: ServerStatus?): List<Int> {
            val covered = status?.coveredSelfIds?.takeIf { it.isNotEmpty() }?.toSet()
                ?: (status?.vendors?.takeIf { it.isNotEmpty() } ?: DEFAULT_VENDORS).flatMap { it.selfIds }.toSet()
            return selfIds.filterNot { it in covered }
        }

        /** Joins vendors that appear more than once (e.g. two of the user's dorm selfs). */
        internal fun mergeByKey(vendors: List<BatchVendor>): List<BatchVendor> = vendors
            .groupBy { it.key }
            .map { (_, group) ->
                group.first().copy(
                    newDays = group.flatMap { it.newDays }.distinct().sorted(),
                    selfIds = group.flatMap { it.selfIds }.distinct().sorted(),
                )
            }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MenuCheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            // UPDATE, not KEEP: phones that already have the check scheduled (every 30 minutes before
            // 1.7.3) switch to the new interval without losing their place.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** Runs one check now, on any day (from the debug screen). */
        fun runNow(context: Context) {
            DebugLog.i(TAG, "check requested by hand")
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<MenuCheckWorker>().setInputData(workDataOf(KEY_FORCE to true)).build(),
            )
        }

        const val UNIQUE_NAME = WORK_NAME
    }
}

/** What the phone's own check found: alerts to give, and the dorm-like cafeterias for Settings. */
data class OwnSelfsResult(val batch: MenuBatch, val ownSelfNames: List<String>)

/**
 * Turns the menus of the user's cafeterias that the server doesn't watch into a batch. A cafeteria
 * that belongs to a known vendor (the girls' self is سلف) is announced under that vendor; others
 * (dorms) share the [OWN_SELFS_KEY] switch. Days before [weekStart] are ignored.
 */
fun ownSelfsBatch(
    weekStart: String,
    selfs: List<Pair<Int, String>>,
    daysBySelf: Map<Int, List<String>>,
    vendors: List<VendorStatus>,
): OwnSelfsResult {
    val found = selfs.mapNotNull { (id, name) ->
        val days = daysBySelf[id].orEmpty().filter { it >= weekStart }.distinct().sorted()
        if (days.isEmpty()) return@mapNotNull null
        val vendor = vendors.firstOrNull { id in it.selfIds }
        BatchVendor(
            key = vendor?.key ?: OWN_SELFS_KEY,
            name = vendor?.name ?: name,
            newDays = days,
            firstAppearance = true,
            selfIds = listOf(id),
        )
    }
    val ownNames = selfs.filter { (id, _) -> vendors.none { id in it.selfIds } }.map { it.second }
    return OwnSelfsResult(MenuBatch(weekStart, MenuCheckWorker.mergeByKey(found)), ownNames)
}
