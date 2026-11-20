package app.shining.reserveinfo.domain.usecase

import app.shining.domain.util.getNextSaturday
import app.shining.reserveinfo.domain.model.ReservationInfo
import app.shining.reserveinfo.domain.model.SavedReservations
import app.shining.reserveinfo.domain.repository.ReservationInfoRepository

/**
 * Loads this week's and next week's reservations from Samad and keeps a copy on the device, so
 * the home screen still shows them without internet.
 */
class LoadReservationsUseCase(
    private val repository: ReservationInfoRepository,
    /** Start of next week as Samad expects it, replaceable in tests. */
    private val nextWeekStart: () -> String = ::getNextSaturday,
) {
    /** The copy saved on the device for the active account, without days that have passed. */
    fun saved(): SavedReservations? = repository.getSavedReservations()

    /**
     * Fails only when this week can't be loaded. Next week is often not published yet, so
     * without it this week is shown (and saved) alone.
     */
    suspend operator fun invoke(): Result<ReservationInfo> {
        // Samad answers for whoever is active when the request is sent, so the copy is saved
        // for that account even if another one becomes active meanwhile.
        val username = repository.activeUsername()
        val thisWeek = repository.getReservationInfo().getOrElse { return Result.failure(it) }
        val info = repository.getReservationInfo(weekStartDate = nextWeekStart())
            .fold(onSuccess = { thisWeek + it }, onFailure = { thisWeek })
        username?.let { repository.saveReservations(it, info) }
        return Result.success(info)
    }
}
