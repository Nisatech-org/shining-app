package app.shining.account.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.shining.R
import app.shining.account.ui.model.AccountRowUIModel
import app.shining.domain.ui.theme.ShiningTheme

@Composable
fun RemoveAccountDialog(
    account: AccountRowUIModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ShiningTheme.colors.surface,
        shape = ShiningTheme.shapes.card,
        title = {
            Text(
                text = stringResource(R.string.accounts_remove_title),
                style = ShiningTheme.typography.title,
                color = ShiningTheme.colors.ink,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.accounts_remove_message, account.title),
                style = ShiningTheme.typography.body,
                color = ShiningTheme.colors.ink,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.accounts_remove_confirm),
                    style = ShiningTheme.typography.button,
                    color = ShiningTheme.colors.negative,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.accounts_cancel),
                    style = ShiningTheme.typography.button,
                    color = ShiningTheme.colors.ink,
                )
            }
        },
    )
}
