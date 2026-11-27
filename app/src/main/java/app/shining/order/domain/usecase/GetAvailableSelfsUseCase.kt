package app.shining.order.domain.usecase

import app.shining.order.api.dto.self.toSelf
import app.shining.order.domain.model.AvailableSelfs
import app.shining.order.domain.repository.OrderFoodRepository

class GetAvailableSelfsUseCase(
    val repository: OrderFoodRepository,
) {
    suspend operator fun invoke(
    ): Result<AvailableSelfs> {
        return repository.getAvailableSelfs()
            .map { AvailableSelfs(it.map { selfDto -> selfDto.toSelf() }) }
    }
}