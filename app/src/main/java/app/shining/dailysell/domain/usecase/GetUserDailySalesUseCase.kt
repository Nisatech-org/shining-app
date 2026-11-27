package app.shining.dailysell.domain.usecase

import app.shining.dailysell.domain.model.UserDailySales
import app.shining.dailysell.domain.repository.DailySellRepository

class GetUserDailySalesUseCase(
    private val repository: DailySellRepository
) {
    suspend operator fun invoke(): Result<UserDailySales> {
        return repository.getUserDailySales()
    }
}