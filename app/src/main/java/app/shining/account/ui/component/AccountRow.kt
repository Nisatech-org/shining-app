package app.shining.account.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.shining.R
import app.shining.account.ui.model.AccountRowUIModel
import app.shining.domain.ui.component.ShiningButton
import app.shining.domain.ui.component.ShiningButtonSize
import app.shining.domain.ui.theme.ShiningTheme

@Composable
fun AccountAvatar(
    initial: String,
    isActive: Boolean,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (isActive) ShiningTheme.colors.brand else ShiningTheme.colors.surface
            )
            .border(1.dp, if (isActive) Color.Transparent else ShiningTheme.colors.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = ShiningTheme.typography.subtitle,
            color = if (isActive) ShiningTheme.colors.onBrand else contentColor,
        )
    }
}

@Composable
fun AccountRow(
    data: AccountRowUIModel,
    contentColor: Color,
    onLoginAgainClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRemoveClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccountAvatar(initial = data.initial, isActive = data.isActive, contentColor = contentColor)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = data.title,
                style = ShiningTheme.typography.subtitle,
                color = contentColor,
            )
            val subtitle = when {
                data.needsReauth -> stringResource(R.string.accounts_needs_login)
                data.isActive && data.subtitle.isNotEmpty() ->
                    "${stringResource(R.string.accounts_active)} · ${data.subtitle}"

                data.isActive -> stringResource(R.string.accounts_active)
                else -> data.subtitle
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = ShiningTheme.typography.caption,
                    color = if (data.needsReauth) {
                        ShiningTheme.colors.negative
                    } else {
                        contentColor.copy(alpha = 0.7f)
                    },
                )
            }
        }
        if (data.needsReauth) {
            ShiningButton(
                text = stringResource(R.string.accounts_login_again),
                icon = R.drawable.ic_login,
                onClick = onLoginAgainClick,
                size = ShiningButtonSize.Small,
            )
        } else if (data.isActive) {
            Icon(
                painter = painterResource(R.drawable.ic_check_rounded),
                contentDescription = stringResource(R.string.accounts_active),
                tint = ShiningTheme.colors.positive,
                modifier = Modifier.size(24.dp),
            )
        }
        onRemoveClick?.let {
            IconButton(onClick = it) {
                Icon(
                    painter = painterResource(R.drawable.ic_trash),
                    contentDescription = stringResource(R.string.accounts_remove),
                    tint = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Preview
@Composable
private fun AccountRowPreview() {
    ShiningTheme {
        Column(Modifier.background(ShiningTheme.colors.surface)) {
            AccountRow(
                data = AccountRowUIModel(
                    username = "400100100",
                    title = "علی رضایی",
                    subtitle = "۴۰۰۱۰۰۱۰۰",
                    initial = "ع",
                    isActive = true,
                    needsReauth = false,
                ),
                contentColor = ShiningTheme.colors.ink,
                onLoginAgainClick = {},
                onRemoveClick = {},
            )
            AccountRow(
                data = AccountRowUIModel(
                    username = "400200200",
                    title = "مریم احمدی",
                    subtitle = "۴۰۰۲۰۰۲۰۰",
                    initial = "م",
                    isActive = false,
                    needsReauth = true,
                ),
                contentColor = ShiningTheme.colors.ink,
                onLoginAgainClick = {},
            )
        }
    }
}
