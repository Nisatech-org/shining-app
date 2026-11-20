package app.shining.server.http

import app.shining.server.discovery.TEHRAN
import app.shining.server.vendors.VENDOR_RULES
import app.shining.server.vendors.VendorRule
import app.shining.server.watch.WatchState
import app.shining.server.watch.isComplete
import app.shining.server.watch.usualArrival
import app.shining.server.watch.watchedWeek
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * What `GET /v1/status` returns: next week's state per vendor. Public on purpose: it only says
 * which menus are published, nothing about any user.
 */
@Serializable
data class StatusResponse(
    /** The Saturday of the week being watched. */
    val week: String,
    /** The last successful check, or null if there was none yet. */
    val updatedAt: String?,
    val lastCheckOk: Boolean,
    /** Self ids the server watches; the app checks the others itself. */
    val coveredSelfIds: List<Int>,
    val vendors: List<VendorStatus>,
)

@Serializable
data class VendorStatus(
    val key: String,
    val name: String,
    val selfIds: List<Int>,
    /** none, partial or complete (Saturday to Wednesday). */
    val state: String,
    val firstSeenAt: String?,
    val days: List<String>,
    /** When it usually comes out, once there are enough weeks to tell. */
    val usualArrival: UsualArrival?,
)

/** E.g. SUNDAY at 14:00 (Tehran). */
@Serializable
data class UsualArrival(val dayOfWeek: String, val time: String)

fun buildStatus(state: WatchState, now: Instant, rules: List<VendorRule> = VENDOR_RULES): StatusResponse {
    val watched = watchedWeek(now.atZone(TEHRAN).toLocalDate())
    val vendors = if (state.weekStart == watched) state.vendors else emptyMap()
    return StatusResponse(
        week = watched.toString(),
        updatedAt = state.lastSuccessAt.takeIf { it != Instant.EPOCH }?.toString(),
        lastCheckOk = state.failures == 0,
        coveredSelfIds = state.coveredSelfIds.sorted(),
        vendors = rules.map { rule ->
            // Only days users were told about: days still held in the bundle window stay out, or
            // phones catching up from here would announce them one by one.
            val unsent = state.pending.filter { it.vendorKey == rule.key }.flatMap { it.newDays }.toSet()
            val vendor = vendors[rule.key]?.let { it.copy(days = it.days - unsent) }?.takeIf { it.days.isNotEmpty() }
            VendorStatus(
                key = rule.key,
                name = rule.name,
                selfIds = rule.selfIds.sorted(),
                state = when {
                    vendor == null || vendor.days.isEmpty() -> "none"
                    isComplete(vendor, watched) -> "complete"
                    else -> "partial"
                },
                firstSeenAt = vendor?.firstSeenAt?.toString(),
                days = vendor?.days?.sorted()?.map { it.toString() }.orEmpty(),
                usualArrival = usualArrival(state.history, rule.key, watched)?.atZone(TEHRAN)?.let {
                    UsualArrival(it.dayOfWeek.name, it.format(DateTimeFormatter.ofPattern("HH:mm")))
                },
            )
        },
    )
}

/**
 * The server's HTTP routes: `GET /v1/status` for the app, `/health` for monitoring. Only the
 * server finds menus; phones only read this (the phone reports of 1.3.0 are gone).
 */
fun Application.statusRoutes(
    status: () -> StatusResponse,
    json: Json = Json,
) {
    routing {
        get("/v1/status") {
            call.response.header("Cache-Control", "public, max-age=60")
            call.respondText(json.encodeToString(StatusResponse.serializer(), status()), ContentType.Application.Json)
        }
        get("/health") {
            call.respondText("ok")
        }
    }
}
