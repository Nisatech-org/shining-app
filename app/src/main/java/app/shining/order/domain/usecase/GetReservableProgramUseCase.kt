package app.shining.order.domain.usecase

import app.shining.order.api.dto.reserve.toWeekReserveProgram
import app.shining.order.domain.model.WeekReservableProgram
import app.shining.order.domain.repository.OrderFoodRepository

class GetReservableProgramUseCase(
    private val repository: OrderFoodRepository,
) {
    suspend operator fun invoke(
        selfId: Int,
        weekStartDate: String,
    ): Result<WeekReservableProgram> {
        return repository.getReserveProgram(
            selfId = selfId,
            weekStartDate = weekStartDate,
        ).map { it.toWeekReserveProgram() }
    }
}