package app.shining.reserveinfo.ui.model

import app.shining.order.domain.model.foodNameWithoutPlace
import app.shining.order.domain.model.shortSelfName
import app.shining.reserveinfo.domain.model.ReservationMealInfo

data class MealRowUIModel(
    val mealName: String,
    val selfName: String,
    val foodName: String,
    val reserveId: Int,
    val forgetCode: String? = null,
    val consumed: Boolean = false,
)

fun ReservationMealInfo.toMealRowUIModel(
    forgetCode: String? = null
): MealRowUIModel {
    val place = shortSelfName(selfText)
    return MealRowUIModel(
        mealName = mealText,
        selfName = place,
        foodName = foodNameWithoutPlace(foodText, place),
        consumed = consumed,
        reserveId = id,
        forgetCode = forgetCode
    )
}
