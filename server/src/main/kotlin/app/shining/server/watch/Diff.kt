package app.shining.server.watch

import java.time.Instant
import java.time.LocalDate

data class DiffResult(val vendors: Map<String, VendorWeek>, val events: List<MenuEvent>)

/**
 * Compares what a check [observed] (vendor → days with food) with what was known before.
 *
 * A vendor with no days before and some now "appeared"; one that gains days "added days". Days
 * never disappear: if Samad briefly returns less (or nothing) for a vendor, the earlier days are
 * kept, so a flaky reply can't make the same menu be announced twice.
 */
fun diffWeek(
    previous: Map<String, VendorWeek>,
    observed: Map<String, Set<LocalDate>>,
    now: Instant,
): DiffResult {
    val vendors = previous.toMutableMap()
    val events = mutableListOf<MenuEvent>()
    for ((key, days) in observed) {
        if (days.isEmpty()) continue
        val before = previous[key]
        if (before == null || before.days.isEmpty()) {
            vendors[key] = VendorWeek(days = days.toSortedSet(), firstSeenAt = now)
            events += MenuEvent(key, days.toSortedSet(), firstAppearance = true, at = now)
        } else {
            val added = days - before.days
            if (added.isNotEmpty()) {
                vendors[key] = before.copy(days = (before.days + days).toSortedSet())
                events += MenuEvent(key, added.toSortedSet(), firstAppearance = false, at = now)
            }
        }
    }
    return DiffResult(vendors, events)
}
