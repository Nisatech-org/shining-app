package app.shining.reserveinfo.domain.model

import app.shining.reserveinfo.db.model.ForgetCodeEntity

data class ForgetCode(
    val reserveId: Int,
    val code: String,
    val isValid: Boolean,
)

fun ForgetCode.toForgetCodeEntity(accountId: String) = ForgetCodeEntity(
    reserveId = reserveId,
    code = code,
    isValid = isValid,
    accountId = accountId,
)