package app.shining.autoreserve.domain.usecase

import app.shining.autoreserve.db.model.toAutoReserveFoodPriority
import app.shining.autoreserve.domain.model.AutoReserveFoodPriority
import app.shining.autoreserve.domain.repository.AutoReserveRepository

class GetAllFoodsUseCase(
    private val repository: AutoReserveRepository
) {
    suspend operator fun invoke(
    ): Result<List<AutoReserveFoodPriority>> {
        return try {
            Result.success(repository.getAllFoodPriorities().map { it.toAutoReserveFoodPriority() })
        } catch (e: Exception) {
            Result.failure(Exception("Failed to get all foods"))
        }
    }
}