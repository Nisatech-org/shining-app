package app.shining.server.watch

import app.shining.server.vendors.VENDOR_RULES
import app.shining.server.vendors.VendorRule
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Days that open within this long of the first unannounced one share its notification:
 * not one alert per day, but not a long wait either, since being first to reserve is
 * the point of the app. Days that open later get their own alert.
 */
val BUNDLE_WINDOW: Duration = Duration.ofMinutes(20)

/**
 * When the [pending] events may be sent: [BUNDLE_WINDOW] after the first of them, or as soon as
 * every vendor in them has its whole week (Saturday to Wednesday) open, since nothing more is
 * coming. [vendors] holds each vendor's open days now. Day or night. Null when
 * nothing is pending.
 */
fun bundleReadyAt(pending: List<MenuEvent>, vendors: Map<String, VendorWeek>, weekStart: LocalDate): Instant? {
    val first = pending.minOfOrNull { it.at } ?: return null
    val allComplete = pending.map { it.vendorKey }.toSet().all { isComplete(vendors[it], weekStart) }
    return if (allComplete) pending.maxOf { it.at } else first + BUNDLE_WINDOW
}

/** One push: every vendor that published something in the bundle window. */
@Serializable
data class MenuBatch(
    @Serializable(with = LocalDateAsString::class) val weekStart: LocalDate,
    val vendors: List<BatchVendor>,
)

@Serializable
data class BatchVendor(
    val key: String,
    val name: String,
    val newDays: List<@Serializable(with = LocalDateAsString::class) LocalDate>,
    /** True if the vendor's week appeared in this bundle (not just more days). */
    val firstAppearance: Boolean,
    /** The vendor's cafeterias, so the app can highlight their foods in Reserve. */
    val selfIds: List<Int> = emptyList(),
)

/** Merges [pending] into one batch, a vendor at most once, in the order of [rules]. */
fun toBatch(
    weekStart: LocalDate,
    pending: List<MenuEvent>,
    rules: List<VendorRule> = VENDOR_RULES,
): MenuBatch {
    val order = rules.map { it.key }
    val vendors = pending.groupBy { it.vendorKey }
        .map { (key, events) ->
            BatchVendor(
                key = key,
                name = rules.firstOrNull { it.key == key }?.name ?: key,
                newDays = events.flatMap { it.newDays }.toSortedSet().toList(),
                firstAppearance = events.any { it.firstAppearance },
                selfIds = rules.firstOrNull { it.key == key }?.selfIds?.sorted().orEmpty(),
            )
        }
        .sortedBy { order.indexOf(it.key).let { index -> if (index == -1) Int.MAX_VALUE else index } }
    return MenuBatch(weekStart, vendors)
}
