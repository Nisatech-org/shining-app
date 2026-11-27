package app.shining.autoreserve.domain.usecase

import app.shining.autoreserve.db.model.toAutoReserveDays
import app.shining.autoreserve.domain.model.AutoReserveDays
import app.shining.autoreserve.domain.repository.AutoReserveRepository

class GetAllSelectedDaysUseCase(
    private val repository: AutoReserveRepository
) {
    suspend operator fun invoke(): Result<AutoReserveDays> {
//        return try {
//            Result.success(repository.getAllReserveDays()?.toAutoReserveDays() ?: AutoReserveDays(
//                days = mutableListOf()
//            ))
//        } catch (e: Exception) {
//            Result.failure(e)
//        }
        return Result.success(
            repository.getAllReserveDays()?.toAutoReserveDays() ?: AutoReserveDays(
                days = mutableListOf()
            )
        )
    }


}