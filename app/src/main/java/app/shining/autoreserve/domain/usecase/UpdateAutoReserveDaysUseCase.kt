package app.shining.autoreserve.domain.usecase

import app.shining.autoreserve.domain.model.AutoReserveDays
import app.shining.autoreserve.domain.repository.AutoReserveRepository

class UpdateAutoReserveDaysUseCase(
    private val repository: AutoReserveRepository
) {
    suspend operator fun invoke(
        days: AutoReserveDays,
    ) {
        repository.updateReserveDays(days.days.map { it.name })
    }

}
