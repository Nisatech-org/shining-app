package app.shining.order.ui.model

import app.shining.order.domain.model.MealType
import app.shining.order.domain.model.SelfDayReservableProgram
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

data class OrderCardUIModel(
    val farsiDayName: String,
    val date: String,
    val dayName: String,
    val reserveInfoList: Map<MealType, ImmutableList<OrderFoodDetailUIModel>>

)

/** [selfNames] maps a self id to the name shown on its foods; ids missing from it get no tag. */
fun SelfDayReservableProgram.toReserveCardUIModel(selfNames: Map<Int, String>): OrderCardUIModel {
    return OrderCardUIModel(
        this.farsiDayName,
        this.date,
        this.dayName,
        this.reserveInfoList.mapValues { (_, value) ->
            value.map { it.toReserveFoodDetailUIModel(selfNames[it.selfId]) }.toImmutableList()
        }.filter { (_, value) -> value.isNotEmpty() }
    )
}
