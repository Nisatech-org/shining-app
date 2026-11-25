package app.shining.reserveinfo.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ReservationDayInfo(
    val name: String,
    val iranianDate: String,
    val date: String,
    val mealInfo: List<ReservationMealInfo?>,
)
