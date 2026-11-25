package app.shining.dailysell.domain.usecase

import app.shining.dailysell.domain.repository.DailySellRepository

class OrderDailySellFoodUseCase(
    private val repository: DailySellRepository,
) {
    suspend operator fun invoke(reserveId: Int): Result<Unit> {
        return repository.orderDailySellItem(reserveId = reserveId)
    }
}