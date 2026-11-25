package app.shining.reserveinfo.api.dto

import app.shining.reserveinfo.domain.model.ReservationMealInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserMealTypeDto(
//    @SerialName("date") val date: String,
//    @SerialName("dateTime") val dateTime: Long,
//    @SerialName("mealTypeId") val mealTypeId: Int,
    @SerialName("name") val name: String = "",
    @SerialName("reserve") val reserveDto: ReserveDto? = null
)

fun UserMealTypeDto.toReservationMealInfo(): ReservationMealInfo? {
    return reserveDto?.let {
        ReservationMealInfo(
            selfText = it.selfName,
            foodText = it.foodNames,
            price = it.price.toLong(),
            consumed = it.consumed,
            mealText = name,
            id = it.id,
            hasPassed = it.timeDistanceUntilToday < 0,
            selfId = it.selfId,
            foodId = it.foodId,
            mealTypeId = it.mealTypeId,
        )
    }
}