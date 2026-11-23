package app.shining.dailysell.ui.model

import app.shining.dailysell.domain.model.DailySellItem
import app.shining.dailysell.domain.model.UserDailySaleInfo
import app.shining.domain.util.toLocalNumber

data class DailySaleCardUIModel(
    val id: Int,
    val count: String,
    val soldCount: String,
    val foodName: String,
    val mealTypeName: String,
    val price: String,
    val selfName: String?,
    val startTime: String?,
    val finishTime: String?,
    val showOrderButton: Boolean,
    val userDailySaleInfo: UserDailySaleCardUIModel?,
    /** Share already sold, from 0 to 1. */
    val soldRatio: Float = 0f,
)

fun DailySellItem.toDailySaleCardUIModel(
    forgetCodeMap: Map<Int, String>,
    userDailySaleInfo: UserDailySaleInfo?
): DailySaleCardUIModel {
    return DailySaleCardUIModel(
        id = id,
        count = count.toString().toLocalNumber(),
        soldCount = soldCount.toString().toLocalNumber(),
        foodName = foodNames,
        mealTypeName = mealTypeName,
        price = price.toString().toLocalNumber(),
        selfName = selfName,
        startTime = startTime?.timeStr,
        finishTime = finishTime?.timeStr,
        showOrderButton = userDailySaleInfo == null,
        userDailySaleInfo = userDailySaleInfo?.toUserDailySaleCardUIModel(forgetCodeMap[userDailySaleInfo.id]),
        soldRatio = if (count > 0) (soldCount.toFloat() / count).coerceIn(0f, 1f) else 0f,
    )
}