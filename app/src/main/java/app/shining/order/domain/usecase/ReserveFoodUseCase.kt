package app.shining.order.domain.usecase

import app.shining.account.domain.repository.ReserveInFlightTracker
import app.shining.order.domain.model.ReserveRequestBodyData
import app.shining.order.domain.repository.OrderFoodRepository

class ReserveFoodUseCase(
    private val repository: OrderFoodRepository,
    private val inFlightTracker: ReserveInFlightTracker,
) {

    suspend operator fun invoke(
        foodTypeId: Int,
        mealTypeId: Int,
        programId: Int,
        selected: Boolean,
    ): Result<String> = inFlightTracker.track {
        repository.reserveFood(
            reserveRequestBodyData = ReserveRequestBodyData(
                foodTypeId = foodTypeId,
                mealTypeId = mealTypeId,
                selected = selected,
                freeFoodSelected = false,
                selectedCount = 1,
            ),
            programId = programId
        )
    }
}
