package app.shining.reserveinfo.ui.model

import app.shining.reserveinfo.domain.model.ReservationInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

data class ReservationInfoScreenUIModel(
    val reservationInfoCardUIModelList: ImmutableList<ReservationInfoCardUIModel>,
)

fun ReservationInfo.toReservationInfoScreenUIModel(
    forgetCodeMap: Map<Int, String>
): ReservationInfoScreenUIModel {
    return ReservationInfoScreenUIModel(
        reservationInfoCardUIModelList = dayInfoList
            .map {
                it.toReservationInfoCardUIModel(
                    forgetCodeMap
                )
            }
            .filter { it.mealRowUIModelList.isNotEmpty() }
            .toImmutableList()
    )
}
