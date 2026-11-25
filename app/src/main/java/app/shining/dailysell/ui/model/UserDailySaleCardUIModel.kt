package app.shining.dailysell.ui.model

import app.shining.dailysell.domain.model.UserDailySaleInfo

data class UserDailySaleCardUIModel(
    val consumed: Boolean,
    val id: Int,
    val forgetCode: String?,
)

fun UserDailySaleInfo.toUserDailySaleCardUIModel(
    forgetCode: String?
): UserDailySaleCardUIModel {
    return UserDailySaleCardUIModel(
        consumed = consumed,
        id = id,
        forgetCode = forgetCode,
    )
}

