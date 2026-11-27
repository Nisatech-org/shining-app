package app.shining.account.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.shining.R
import app.shining.account.ui.model.AccountRowUIModel
import app.shining.domain.ui.component.InlineMessage
import app.shining.domain.ui.model.ShiningInformationBoxUIModel
import app.shining.domain.ui.theme.ShiningTheme
import kotlinx.collections.immutable.ImmutableList

/**
 * Content of the quick switcher opened by long-pressing the Profile tab.
 */
@Composable
fun AccountSwitcherSheet(
    accounts: ImmutableList<AccountRowUIModel>,
    canAddAccount: Boolean,
    switchingTo: AccountRowUIModel?,
    waitingForReserve: Boolean,
    status: ShiningInformationBoxUIModel?,
    onAccountClick: (String) -> Unit,
    onLoginAgainClick: (String) -> Unit,
    onAddAccountClick: () -> Unit,
) {
    val contentColor = ShiningTheme.colors.ink
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.accounts_title),
            style = ShiningTheme.typography.title,
            color = contentColor,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        AccountSwitchStatus(
            switchingTo = switchingTo,
            waitingForReserve = waitingForReserve,
            status = status,
            contentColor = contentColor,
        )
        AccountList(
            accounts = accounts,
            canAddAccount = canAddAccount,
            contentColor = contentColor,
            rowBackground = ShiningTheme.colors.surfaceMuted,
            onAccountClick = onAccountClick,
            onLoginAgainClick = onLoginAgainClick,
            onAddAccountClick = onAddAccountClick,
            enabled = switchingTo == null,
        )
    }
}

/**
 * "Waiting for a reservation", "logging in to X…" or the last error, above an account list.
 */
@Composable
fun AccountSwitchStatus(
    switchingTo: AccountRowUIModel?,
    waitingForReserve: Boolean,
    status: ShiningInformationBoxUIModel?,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = switchingTo != null) {
            Text(
                text = if (waitingForReserve) {
                    stringResource(R.string.accounts_waiting_for_reserve)
                } else {
                    stringResource(R.string.accounts_switching, switchingTo?.title.orEmpty())
                },
                style = ShiningTheme.typography.caption,
                color = contentColor,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        AnimatedVisibility(visible = status != null) {
            status?.let {
                Column {
                    InlineMessage(data = it, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.size(8.dp))
                }
            }
        }
    }
}
