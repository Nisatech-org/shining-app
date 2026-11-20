package app.shining.server.watch

import app.shining.server.discovery.TEHRAN
import app.shining.server.discovery.weekStarts
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Check interval while a release is likely (or before the pattern is known). */
val FAST_INTERVAL: Duration = Duration.ofMinutes(5)

/** Check interval outside a vendor's usual window, so early or late releases are still caught. */
val SLOW_INTERVAL: Duration = Duration.ofMinutes(60)

/**
 * Check interval when no menu is expected (Saturday, or once every vendor has its whole week).
 * The server never stops checking: late changes are caught, and the Samad session stays in use,
 * so one that stops working is noticed within the hour.
 */
val IDLE_INTERVAL: Duration = Duration.ofMinutes(60)

/** The learned window around a vendor's usual release time. */
val WINDOW_BEFORE: Duration = Duration.ofHours(2)
val WINDOW_AFTER: Duration = Duration.ofHours(4)

/** Weeks of release times used for learning, and how many are needed before trusting them. */
const val HISTORY_WEEKS = 8
const val MIN_SAMPLES = 3

/** The week whose menus are being waited for: the one after today's. */
fun watchedWeek(today: LocalDate): LocalDate = weekStarts(today)[1]

/**
 * Checks for [watched] run from the Sunday before it (00:00 Tehran) until it starts. Menus usually
 * come out on Monday; starting a day early catches an early release and shows the server works.
 */
fun activeFrom(watched: LocalDate): Instant = watched.minusDays(6).atStartOfDay(TEHRAN).toInstant()

/** Saturday 00:00 of the week before [watched]: release times are measured from here. */
private fun weekBase(watched: LocalDate): Instant = watched.minusWeeks(1).atStartOfDay(TEHRAN).toInstant()

/** Saturday to Wednesday: the days every Sharif cafeteria serves lunch. */
fun isComplete(vendor: VendorWeek?, watched: LocalDate): Boolean =
    vendor != null && (0L..4L).all { watched.plusDays(it) in vendor.days }

/**
 * How long after the start of the week before its menus a vendor usually publishes: the median of
 * its last [HISTORY_WEEKS] samples, or null with fewer than [MIN_SAMPLES].
 */
fun usualArrivalOffset(history: List<ArrivalSample>, vendorKey: String): Duration? {
    val offsets = history.filter { it.vendorKey == vendorKey }
        .sortedByDescending { it.weekStart }
        .take(HISTORY_WEEKS)
        .map { Duration.between(weekBase(it.weekStart), it.at) }
        .sorted()
    if (offsets.size < MIN_SAMPLES) return null
    val middle = offsets.size / 2
    return if (offsets.size % 2 == 1) offsets[middle] else (offsets[middle - 1] + offsets[middle]).dividedBy(2)
}

/** When the vendor is expected to publish [watched]'s menu, if its pattern is known. */
fun usualArrival(history: List<ArrivalSample>, vendorKey: String, watched: LocalDate): Instant? =
    usualArrivalOffset(history, vendorKey)?.let { weekBase(watched) + it }

/** Keeps the last [HISTORY_WEEKS] samples of each vendor. */
fun trimHistory(history: List<ArrivalSample>): List<ArrivalSample> = history
    .groupBy { it.vendorKey }
    .flatMap { (_, samples) -> samples.sortedByDescending { it.weekStart }.take(HISTORY_WEEKS) }
    .sortedWith(compareBy({ it.weekStart }, { it.vendorKey }))

/**
 * When to check Samad next. From Sunday, every 5 minutes for any vendor still missing days whose
 * pattern isn't known yet or whose usual window is open, and hourly otherwise (waking early for a
 * window that opens sooner). On Saturday, and once every watched vendor has its whole week, every
 * [IDLE_INTERVAL]: checks never stop.
 */
fun nextCheckAt(now: Instant, state: WatchState, vendorKeys: Collection<String>): Instant =
    nextCheckPlan(now, state, vendorKeys).at

/** When the next check is, and why, for the log. */
data class CheckPlan(val at: Instant, val reason: String)

fun nextCheckPlan(now: Instant, state: WatchState, vendorKeys: Collection<String>): CheckPlan {
    val watched = watchedWeek(now.atZone(TEHRAN).toLocalDate())
    val from = activeFrom(watched)
    if (now.isBefore(from)) {
        return CheckPlan(minOf(now + IDLE_INTERVAL, from), "no menu expected before Sunday; checking hourly")
    }

    val vendors = if (state.weekStart == watched) state.vendors else emptyMap()
    val incomplete = vendorKeys.filterNot { isComplete(vendors[it], watched) }
    if (incomplete.isEmpty()) {
        return CheckPlan(now + IDLE_INTERVAL, "every vendor has its whole week; checking hourly")
    }

    var next = CheckPlan(now + SLOW_INTERVAL, "outside every usual window (${incomplete.joinToString(",")} incomplete)")
    for (key in incomplete) {
        val usual = usualArrival(state.history, key, watched)
            ?: return CheckPlan(now + FAST_INTERVAL, "$key has no known pattern yet")
        val opens = usual - WINDOW_BEFORE
        val closes = usual + WINDOW_AFTER
        if (!now.isBefore(opens) && !now.isAfter(closes)) return CheckPlan(now + FAST_INTERVAL, "$key's usual window is open")
        if (opens.isAfter(now) && opens.isBefore(next.at)) next = CheckPlan(opens, "$key's usual window opens")
    }
    return next
}
