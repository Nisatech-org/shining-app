package app.shining.autoreserve.domain.usecase

import app.shining.autoreserve.domain.repository.AutoReserveRepository

class InsertFoodUseCase(
    private val repository: AutoReserveRepository,
) {
    suspend operator fun invoke(
        food: String,
    ) {
        repository.insertFood(name = food, priority = 0)
    }
}
