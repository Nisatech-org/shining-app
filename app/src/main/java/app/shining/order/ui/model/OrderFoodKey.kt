package app.shining.order.ui.model

import kotlinx.collections.immutable.toImmutableList

/**
 * Identifies a single reservable food. These are the same ids the reserve request is sent with.
 */
data class OrderFoodKey(
    val programId: Int,
    val mealTypeId: Int,
    val foodTypeId: Int,
)

val OrderFoodDetailUIModel.key: OrderFoodKey
    get() = OrderFoodKey(
        programId = programId,
        mealTypeId = mealTypeId,
        foodTypeId = foodTypeId,
    )

/**
 * Returns a copy where the food matching [key] has [OrderFoodDetailUIModel.isSelected] set to
 * [isSelected]. Used to show a reserve/cancel result before the program is fetched again.
 * Reserving also locks that day's foods at other selfs, since a day's reservations must all be
 * at one self; cancelling leaves them for the refresh to unlock, as other reservations may remain.
 * Returns the same instance when no food matches.
 */
fun OrderScreenUIModel.withFoodSelected(
    key: OrderFoodKey,
    isSelected: Boolean,
): OrderScreenUIModel {
    var changed = false
    val cards = orderCardUIModelList.map { card ->
        val target = card.reserveInfoList.values.flatten().firstOrNull { it.key == key }
            ?: return@map card
        changed = true
        val reserveInfoList = card.reserveInfoList.mapValues { (_, foods) ->
            foods.map {
                when {
                    it.key == key -> it.copy(isSelected = isSelected)
                    isSelected && it.selfId != target.selfId -> it.copy(canSelect = false)
                    else -> it
                }
            }.toImmutableList()
        }
        card.copy(reserveInfoList = reserveInfoList)
    }
    return if (changed) copy(orderCardUIModelList = cards.toImmutableList()) else this
}
