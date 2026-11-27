package app.shining.reserveinfo.domain.model

import kotlinx.serialization.Serializable

/** The last reservations loaded from Samad, kept on the device so they show without internet. */
@Serializable
data class SavedReservations(
    val info: ReservationInfo,
    /** When they were loaded, in milliseconds. */
    val savedAt: Long,
)
