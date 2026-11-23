package app.shining.autoreserve.domain.usecase

import app.shining.autoreserve.domain.model.AutoReserveFoodPriority
import app.shining.autoreserve.domain.repository.AutoReserveRepository

class UpdateFoodPriorityUseCase(
    private val repository: AutoReserveRepository,
) {
    suspend operator fun invoke(
        food: AutoReserveFoodPriority,
        priority: Int
    ) {
        repository.updateFoodPriority(id = food.id, priority = priority)
    }

}
