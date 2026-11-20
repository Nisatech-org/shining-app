package app.shining.server.watch

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.LocalDate

/** What the server knows about one vendor's menu for the watched week. */
@Serializable
data class VendorWeek(
    /** Days of the watched week with food that could be reserved. Only grows during a week. */
    val days: Set<@Serializable(with = LocalDateAsString::class) LocalDate>,
    /** When the server first saw any of this week's menu. */
    @Serializable(with = InstantAsString::class) val firstSeenAt: Instant,
)

/** A vendor published (part of) next week's menu. */
@Serializable
data class MenuEvent(
    val vendorKey: String,
    val newDays: Set<@Serializable(with = LocalDateAsString::class) LocalDate>,
    /** True when this vendor had nothing for the week before. */
    val firstAppearance: Boolean,
    @Serializable(with = InstantAsString::class) val at: Instant,
)

/** When a vendor's menu first appeared one week. Used to learn its usual release time. */
@Serializable
data class ArrivalSample(
    val vendorKey: String,
    /** The Saturday that starts the week the menu is for. */
    @Serializable(with = LocalDateAsString::class) val weekStart: LocalDate,
    @Serializable(with = InstantAsString::class) val at: Instant,
)

/** Everything the server keeps between checks and restarts, saved in one JSON file. */
@Serializable
data class WatchState(
    /** False until the first successful check, which only records what's there without alerting. */
    val initialized: Boolean = false,
    /** The Saturday of the week being watched (next week). EPOCH means none yet. */
    @Serializable(with = LocalDateAsString::class) val weekStart: LocalDate = LocalDate.EPOCH,
    val vendors: Map<String, VendorWeek> = emptyMap(),
    /** Events waiting for the bundle window or quiet hours to end. */
    val pending: List<MenuEvent> = emptyList(),
    val history: List<ArrivalSample> = emptyList(),
    /** Self ids the server's account can see that belong to a vendor. */
    val coveredSelfIds: Set<Int> = emptySet(),
    @Serializable(with = InstantAsString::class) val lastSuccessAt: Instant = Instant.EPOCH,
    /** Failed checks in a row. */
    val failures: Int = 0,
    /** Set once the admin was told checks are failing, so they are told again when they recover. */
    val adminAlerted: Boolean = false,
    /** The last [RECENT_CHECKS] checks, newest last, for the debug page. */
    val recentChecks: List<CheckRecord> = emptyList(),
    /** The last [RECENT_PUSHES] menu pushes, newest last. */
    val recentPushes: List<PushRecord> = emptyList(),
    /**
     * Vendors whose menu was seen this week before it could be reserved, and the admin was told. Each
     * is told once a week; users are alerted only when days open.
     */
    val closedNoticeSent: Set<String> = emptySet(),
)

const val RECENT_CHECKS = 100
const val RECENT_PUSHES = 30

/** What one check did: when, how long, what it saw, or why it failed. */
@Serializable
data class CheckRecord(
    @Serializable(with = InstantAsString::class) val at: Instant,
    val ok: Boolean,
    val ms: Long,
    val week: String,
    /** Days that can be reserved, per vendor, e.g. "kaleh" to 5. */
    val days: Map<String, Int> = emptyMap(),
    /** E.g. "kalana appeared 2026-11-14,2026-11-15". */
    val events: List<String> = emptyList(),
    val error: String? = null,
    /** Days with food per vendor, including days that can't be reserved yet. */
    val foodDays: Map<String, Int> = emptyMap(),
    /** Foods on each vendor's menu, all days together. */
    val foods: Map<String, Int> = emptyMap(),
)

/** One attempt to send a bundle of menu events. */
@Serializable
data class PushRecord(
    @Serializable(with = InstantAsString::class) val at: Instant,
    val ok: Boolean,
    val vendors: List<String>,
    val error: String? = null,
)

object LocalDateAsString : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object InstantAsString : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
