package app.shining.order.ui.model

import app.shining.order.domain.model.WeekReservableProgram
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

data class OrderScreenUIModel(
    val orderCardUIModelList: ImmutableList<OrderCardUIModel>
)

/**
 * [selfNames] maps a self id to the name shown on its foods; ids missing from it get no tag.
 * [selfCapacities] does the same for the cafeteria's capacity.
 */
fun WeekReservableProgram.toReservableScreenUIModel(
    selfNames: Map<Int, String> = emptyMap(),
    selfCapacities: Map<Int, Int> = emptyMap(),
): OrderScreenUIModel {
    return OrderScreenUIModel(
        orderCardUIModelList = selfWeekProgram.map { it.toReserveCardUIModel(selfNames, selfCapacities) }
            .filter { it.reserveInfoList.isNotEmpty() }.toImmutableList()
    )
}
