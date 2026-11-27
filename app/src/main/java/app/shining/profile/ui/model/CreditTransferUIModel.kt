package app.shining.profile.ui.model

import app.shining.domain.util.toLocalNumber
import app.shining.profile.domain.model.CreditTransfer

data class CreditTransferUIModel(
    val amount: String,
    val date: String,
    val isPositive: Boolean,
    val amountValue: Long = 0,
)

fun CreditTransfer.toCreditTransferUIModel(): CreditTransferUIModel {
    return CreditTransferUIModel(
        amount = amount.toString().toLocalNumber(),
        date = date.split(" ")[0].replace('-', '/').toLocalNumber(),
        isPositive = amount > 0,
        amountValue = amount.toLong(),
    )
}