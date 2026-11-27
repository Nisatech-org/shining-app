package app.shining.account.ui.model

import app.shining.account.domain.model.Account
import app.shining.domain.util.toLocalNumber

data class AccountRowUIModel(
    val username: String,
    val title: String,
    /** Shown under the name; empty when the name is the username itself. */
    val subtitle: String,
    val initial: String,
    val isActive: Boolean,
    val needsReauth: Boolean,
)

fun Account.toAccountRowUIModel(activeUsername: String?): AccountRowUIModel {
    val hasName = !displayName.isNullOrBlank()
    return AccountRowUIModel(
        username = username,
        title = if (hasName) label else username.toLocalNumber(),
        subtitle = if (hasName) username.toLocalNumber() else "",
        initial = (if (hasName) label else username.toLocalNumber()).trim().take(1).ifEmpty { "?" },
        isActive = username == activeUsername,
        needsReauth = needsReauth,
    )
}
