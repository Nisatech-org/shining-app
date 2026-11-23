package app.shining.reserveinfo.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ReservationMealInfo(
    val selfText: String,
    val foodText: String,
    val mealText: String,
    val price: Long,
    val id: Int,
    val consumed: Boolean = false,
    val hasPassed: Boolean = false,
    /** Samad's ids, for the market. 0 in copies saved before 1.13.0. */
    val selfId: Int = 0,
    val foodId: Int = 0,
    val mealTypeId: Int = 0,
)
