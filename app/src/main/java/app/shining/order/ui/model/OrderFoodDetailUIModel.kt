package app.shining.order.ui.model

import app.shining.order.domain.model.ReservableFoodDetail

data class OrderFoodDetailUIModel(
    val programId: Int,
    val selfId: Int,
    val mealTypeId: Int,
    val foodTypeId: Int,
    val foodName: String,
    val price: Long,
    /** Where the food is served. Null when the user has only one self, so there is no choice. */
    val selfName: String? = null,
    /** The capacity of the cafeteria that serves it, from Samad. Null for سلف مرکزی, or when unknown. */
    val selfCapacity: Int? = null,
    val isSelected: Boolean = false,
    val isDisable: Boolean = false,
    val hasPassed: Boolean = false,
    val canSelect: Boolean = true,
)

fun ReservableFoodDetail.toReserveFoodDetailUIModel(selfName: String?, selfCapacity: Int? = null): OrderFoodDetailUIModel {
    return OrderFoodDetailUIModel(
        programId = programId,
        selfId = selfId,
        mealTypeId = mealTypeId,
        foodTypeId = foodTypeId,
        foodName = foodName,
        price = price,
        selfName = selfName,
        selfCapacity = selfCapacity,
        isSelected = isReserved,
        isDisable = isDisabled,
        hasPassed = hasPassed,
        canSelect = canSelect,
    )
}
