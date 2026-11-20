package app.shining.server.watch

import app.shining.server.discovery.TEHRAN
import app.shining.server.log.Log
import app.shining.server.samad.SignInNeededException
import app.shining.server.vendors.VENDOR_RULES
import app.shining.server.vendors.VendorRule
import app.shining.server.vendors.vendorFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Failed checks in a row before the admin is alerted. */
const val FAILURES_BEFORE_ALERT = 3

/** While the server waits for a sign-in, the admin is reminded this often. */
val SIGN_IN_REMINDER: Duration = Duration.ofHours(12)

/** How long before the Samad session runs out the admin is asked to sign in again. */
val SESSION_WARNINGS: List<Duration> = listOf(Duration.ofDays(7), Duration.ofDays(1))

/**
 * A first appearance counts as a release time only if the check before it was this recent;
 * otherwise (the first check on Monday, after downtime) we don't know when it really came out.
 */
val PRECISE_GAP: Duration = Duration.ofMinutes(65)

/**
 * Checks Samad for the watched week, turns changes into events, and sends them in bundles.
 * [state] is saved after every change, so a restart picks up where it left off.
 *
 * A day counts once it has food that can be reserved: a menu that's visible before reservations
 * open is recorded (and the admin told), and users are alerted when it opens.
 */
class Watcher(
    private val source: MenuSource,
    private val store: StateStore,
    private val notifier: Notifier,
    private val clock: () -> Instant = Instant::now,
    private val rules: List<VendorRule> = VENDOR_RULES,
    /** Pause between menu requests, so the server doesn't hammer Samad. */
    private val requestGap: Duration = Duration.ofMillis(300),
    /** Keeps every version of each menu. None in most tests. */
    private val archive: MenuArchive? = null,
    /** Tells the admin once after the first good check since start, so a deploy is confirmed end to end. */
    private val announceStart: Boolean = false,
    /** When the Samad session runs out, if known, so the admin can sign in again before it does. */
    private val sessionExpiry: () -> Instant? = { null },
) {
    /** Checks and sends change [state] one at a time. */
    private val lock = Mutex()

    private var startAnnounced = false

    /** When the admin was last asked to sign in to Samad, or null while the session works. */
    private var signInAlertedAt: Instant? = null

    /** The session end and the closest warning already sent for it. */
    private var sessionWarned: Pair<Instant, Duration>? = null

    @Volatile
    var state: WatchState = store.load()
        private set

    /** Vendors the server watches: those its account can see, or all of them before the first check. */
    val watchedVendorKeys: Set<String>
        get() {
            val covered = rules.filter { rule -> rule.selfIds.any { it in state.coveredSelfIds } }
            return (covered.ifEmpty { rules }).map { it.key }.toSet()
        }

    suspend fun check() = lock.withLock { checkLocked() }

    /**
     * Warns the admin [SESSION_WARNINGS] before the Samad session runs out (7 days, then 1 day),
     * so they sign in again on the page while it still works and checks never stop. A new session (its
     * own end date) starts the warnings over.
     */
    private suspend fun warnIfSessionEnding(now: Instant) {
        val expires = sessionExpiry() ?: return
        val left = Duration.between(now, expires)
        val closest = SESSION_WARNINGS.filter { left <= it }.minOrNull() ?: return
        val warned = sessionWarned
        if (warned != null && warned.first == expires && warned.second <= closest) return
        sessionWarned = expires to closest
        val days = left.toDays()
        val whenText = if (days >= 1) "$days روز دیگه" else "کمتر از یک روز دیگه"
        val at = expires.atZone(TEHRAN).toLocalDateTime().toString().replace('T', ' ').take(16)
        Log.warn(TAG, "the Samad session runs out soon; asking admin to sign in again", "until" to expires)
        notifyAdmin("نشست سرور شاینینگ توی سماد $whenText ($at) تموم می‌شه. قبلش از اینجا دوباره واردش کن تا بررسی‌ها قطع نشه: $SIGN_IN_PAGE")
    }

    /** Asks the admin to sign in on the sign-in page, then again every [SIGN_IN_REMINDER] while it's still needed. */
    private suspend fun alertSignIn(now: Instant): Boolean {
        val last = signInAlertedAt
        if (last != null && Duration.between(last, now) < SIGN_IN_REMINDER) return false
        signInAlertedAt = now
        Log.error(TAG, "Samad needs someone to sign in; alerting admin", "page" to SIGN_IN_PAGE)
        notifyAdmin("سرور شاینینگ باید دوباره وارد سماد بشه. کد امنیتی رو اینجا بزن: $SIGN_IN_PAGE")
        return true
    }

    private suspend fun checkLocked() {
        val now = clock()
        val started = System.nanoTime()
        val watched = watchedWeek(now.atZone(TEHRAN).toLocalDate())
        var next = if (state.weekStart == watched) {
            state
        } else {
            // A new week: start over, but keep the history. Unsent events of the old week are dropped.
            if (state.weekStart != LocalDate.EPOCH) {
                Log.info(TAG, "new week", "from" to state.weekStart, "to" to watched, "droppedPending" to state.pending.size)
            }
            state.copy(weekStart = watched, vendors = emptyMap(), pending = emptyList(), closedNoticeSent = emptySet())
        }

        val observed = try {
            observe(watched)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val failures = next.failures + 1
            val reason = listOfNotNull(e.javaClass.simpleName, e.message).joinToString(": ")
            Log.warn(TAG, "check failed", "week" to watched, "inARow" to failures, "ms" to elapsedMs(started), error = e)
            next = next.copy(
                failures = failures,
                recentChecks = (next.recentChecks + CheckRecord(now, false, elapsedMs(started), watched.toString(), error = reason)).takeLast(RECENT_CHECKS),
            )
            if (e is SignInNeededException) {
                // Not a passing glitch: nothing works until someone signs in, so say so right away.
                if (alertSignIn(now)) next = next.copy(adminAlerted = true)
            } else if (failures == FAILURES_BEFORE_ALERT) {
                Log.error(TAG, "checks keep failing; alerting admin", "inARow" to failures)
                notifyAdmin("سرور شاینینگ به سماد وصل نمی‌شه ($failures بار پشت سر هم): $reason")
                next = next.copy(adminAlerted = true)
            }
            commit(next)
            return
        }

        signInAlertedAt = null
        if (next.adminAlerted) {
            Log.info(TAG, "checks work again; telling admin", "afterFailures" to next.failures)
            notifyAdmin("سرور شاینینگ دوباره به سماد وصله.")
        }

        val precise = next.lastSuccessAt != Instant.EPOCH &&
            Duration.between(next.lastSuccessAt, now) <= PRECISE_GAP
        val diff = diffWeek(next.vendors, observed.openDays, now)
        // The very first check only learns what's already there; announcing it all would be noise.
        val events = if (next.initialized) diff.events else emptyList()
        if (!next.initialized) Log.info(TAG, "first check: recording what's there without alerting")
        val samples = if (precise) {
            events.filter { it.firstAppearance }.map { ArrivalSample(it.vendorKey, watched, now) }
        } else {
            emptyList()
        }
        events.forEach {
            Log.info(
                TAG, if (it.firstAppearance) "vendor appeared" else "vendor added days",
                "vendor" to it.vendorKey, "days" to it.newDays.sorted().joinToString(","),
                "learnTime" to (it.firstAppearance && precise),
            )
        }

        val dayCounts = diff.vendors.mapValues { it.value.days.size }
        val foodDayCounts = observed.foodDays.filterValues { it.isNotEmpty() }.mapValues { it.value.size }
        val foodCounts = observed.foods.filterValues { it > 0 }
        // Seen with food but nothing to reserve yet, and never open before this week.
        val closed = foodDayCounts.keys.filter { key ->
            observed.openDays[key].isNullOrEmpty() && diff.vendors[key]?.days.isNullOrEmpty()
        }
        val ms = elapsedMs(started)
        Log.info(
            TAG, "check done",
            "week" to watched, "ms" to ms, "selfs" to observed.coveredSelfIds.size,
            "days" to dayCounts.describe(),
            "foodDays" to foodDayCounts.describe(),
            "foods" to foodCounts.describe(),
            "closed" to closed.joinToString(",").ifEmpty { "none" },
            "events" to events.size, "pending" to next.pending.size + events.size,
        )
        val newlyClosed = closed.filterNot { it in next.closedNoticeSent }
        if (newlyClosed.isNotEmpty()) {
            val names = newlyClosed.map { key -> rules.firstOrNull { it.key == key }?.name ?: key }
            Log.info(TAG, "menu visible but not reservable yet; users are alerted when it opens", "vendors" to newlyClosed.joinToString(","))
            notifyAdmin("منوی هفته بعد ${names.joinToString(" و ")} توی سماد هست ولی هنوز رزرو نمی‌شه. وقتی باز شد اعلان می‌ره.")
        }
        if (announceStart && !startAnnounced) {
            startAnnounced = true
            val open = dayCounts.filterValues { it > 0 }
            val summary = if (open.isEmpty()) "هنوز منویی باز نشده" else open.describe()
            val firstNote = if (next.initialized) "" else " (اولین بررسی؛ فقط ثبت شد)"
            notifyAdmin("سرور شاینینگ روشنه و به سماد وصله. هفته‌ی $watched: $summary$firstNote")
        }
        warnIfSessionEnding(now)
        commit(
            next.copy(
                initialized = true,
                vendors = diff.vendors,
                pending = next.pending + events,
                history = trimHistory(next.history + samples),
                coveredSelfIds = observed.coveredSelfIds,
                lastSuccessAt = now,
                failures = 0,
                adminAlerted = false,
                closedNoticeSent = next.closedNoticeSent + newlyClosed,
                recentChecks = (next.recentChecks + CheckRecord(
                    at = now, ok = true, ms = ms, week = watched.toString(), days = dayCounts,
                    events = events.map { "${it.vendorKey} ${if (it.firstAppearance) "appeared" else "added"} ${it.newDays.sorted().joinToString(",")}" },
                    foodDays = foodDayCounts,
                    foods = foodCounts,
                )).takeLast(RECENT_CHECKS),
            )
        )
    }

    /** Sends the pending events as one batch once their bundle window (and quiet hours) are over. */
    suspend fun flush() = lock.withLock { flushLocked() }

    private suspend fun flushLocked() {
        val ready = bundleReadyAt(state.pending, state.vendors, state.weekStart) ?: return
        if (clock().isBefore(ready)) return
        val sending = state.pending
        val batch = toBatch(state.weekStart, sending, rules)
        val vendors = batch.vendors.map { "${it.key}:${it.newDays.size}" }
        Log.info(PUSH_TAG, "sending menu bundle", "week" to batch.weekStart, "vendors" to vendors.joinToString(","), "events" to sending.size)
        try {
            notifier.sendMenus(batch)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.warn(PUSH_TAG, "sending menus failed; the bundle stays queued and is retried", error = e)
            commit(state.copy(recentPushes = (state.recentPushes + PushRecord(clock(), false, vendors, e.message)).takeLast(RECENT_PUSHES)))
            return
        }
        commit(
            state.copy(
                pending = emptyList(),
                recentPushes = (state.recentPushes + PushRecord(clock(), true, vendors)).takeLast(RECENT_PUSHES),
            )
        )
    }

    private class Observed(
        /** Days with food that can be reserved, per vendor. */
        val openDays: Map<String, Set<LocalDate>>,
        /** Days with any food, per vendor. */
        val foodDays: Map<String, Set<LocalDate>>,
        val foods: Map<String, Int>,
        val coveredSelfIds: Set<Int>,
    )

    private suspend fun observe(watched: LocalDate): Observed {
        val week = (0L..6L).map { watched.plusDays(it) }.toSet()
        val covered = source.selfs().mapNotNull { self -> vendorFor(self.id, self.name, rules)?.let { it to self } }
        val open = mutableMapOf<String, MutableSet<LocalDate>>()
        val food = mutableMapOf<String, MutableSet<LocalDate>>()
        val foods = mutableMapOf<String, Int>()
        var lastError: Exception? = null
        var failed = 0
        for ((vendor, self) in covered) {
            // One cafeteria's menu failing must not hold up the others' alerts: it's skipped this
            // round (its known days are kept). Only when every one fails is the check a failure.
            val program = try {
                source.program(self.id, watched)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                lastError = e
                Log.warn(TAG, "couldn't read one menu; the others go on", "self" to self.id, "vendor" to vendor.key, error = e)
                continue
            }
            open.getOrPut(vendor.key) { mutableSetOf() } += program.openDays.filter { it in week }
            food.getOrPut(vendor.key) { mutableSetOf() } += program.foodDays.filter { it in week }
            foods[vendor.key] = (foods[vendor.key] ?: 0) + program.foods
            archive?.record(watched, self, program, clock())
            if (!requestGap.isZero) delay(requestGap.toMillis())
        }
        if (covered.isNotEmpty() && failed == covered.size) throw lastError!!
        return Observed(open, food, foods, covered.map { it.second.id }.toSet())
    }

    private suspend fun notifyAdmin(message: String) {
        try {
            notifier.alertAdmin(message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.warn(PUSH_TAG, "admin alert failed", error = e)
        }
    }

    private fun commit(newState: WatchState) {
        state = newState
        store.save(newState)
    }

    private fun elapsedMs(startedNanos: Long) = (System.nanoTime() - startedNanos) / 1_000_000

    private companion object {
        const val TAG = "check"
        const val PUSH_TAG = "push"
    }
}
