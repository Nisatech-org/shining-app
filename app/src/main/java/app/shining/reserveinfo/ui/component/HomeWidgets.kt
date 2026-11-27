package app.shining.reserveinfo.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.shining.R
import app.shining.domain.ui.component.CircleIconButton
import app.shining.domain.ui.component.Emoji
import app.shining.domain.ui.component.NotchGap
import app.shining.domain.ui.component.NotchedCard
import app.shining.domain.ui.component.IconTile
import app.shining.domain.ui.component.ShiningButton
import app.shining.domain.ui.component.ShiningButtonSize
import app.shining.domain.ui.component.ShiningCard
import app.shining.domain.ui.theme.ShiningTheme

/**
 * "Hello, name" on a lime card. Settings and the account avatar sit in a notch cut out of the
 * card's top end corner.
 */
@Composable
fun HomeHeader(
    name: String?,
    initial: String,
    onSettingsClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onAvatarLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ShiningTheme.colors
    NotchedCard(
        notchWidth = 48.dp * 2 + NotchGap * 2,
        notchHeight = 48.dp + NotchGap,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        notch = {
            CircleIconButton(
                icon = R.drawable.ic_tune,
                onClick = onSettingsClick,
                contentDescription = stringResource(R.string.settings_screen_title),
            )
            AccountAvatarButton(initial = initial, onClick = onAvatarClick, onLongClick = onAvatarLongClick)
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(ShiningTheme.shapes.pill)
                    .background(colors.onBrand),
                contentAlignment = Alignment.Center,
            ) {
                Emoji(emoji = R.drawable.emoji_fries, size = 22.dp)
            }
            Spacer(modifier = Modifier.size(10.dp))
            Column {
                Text(text = "Shining", style = ShiningTheme.typography.subtitle, color = colors.onBrand)
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = ShiningTheme.typography.caption,
                    color = colors.onBrand.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }
        }
        Spacer(modifier = Modifier.size(28.dp))
        Text(
            text = if (name.isNullOrBlank()) {
                stringResource(R.string.home_hello)
            } else {
                stringResource(R.string.home_hello_name, name)
            },
            style = ShiningTheme.typography.hero,
            color = colors.onBrand,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountAvatarButton(
    initial: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(ShiningTheme.shapes.pill)
            .background(ShiningTheme.colors.onBrand)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = ShiningTheme.typography.title,
            color = ShiningTheme.colors.brand,
        )
    }
}

/** Says the reservations below are the copy saved on the device, because Samad didn't answer. */
@Composable
fun SavedCopyNotice(
    message: String,
    modifier: Modifier = Modifier,
    /** Set when Samad ended the session: a «ورود دوباره» button under the message. */
    onLoginAgain: (() -> Unit)? = null,
) {
    val colors = ShiningTheme.colors
    ShiningCard(
        modifier = modifier.fillMaxWidth(),
        background = colors.warningSoft,
        contentPadding = PaddingValues(12.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(
                    icon = if (onLoginAgain != null) R.drawable.ic_login else R.drawable.ic_clock,
                    size = 32.dp,
                    iconSize = 18.dp,
                    background = colors.warning,
                    tint = colors.surface,
                    bordered = false,
                    shape = ShiningTheme.shapes.pill,
                )
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = message,
                    style = ShiningTheme.typography.bodyStrong,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
            }
            if (onLoginAgain != null) {
                Spacer(modifier = Modifier.size(12.dp))
                ShiningButton(
                    text = stringResource(R.string.accounts_login_again),
                    icon = R.drawable.ic_login,
                    onClick = onLoginAgain,
                    size = ShiningButtonSize.Small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
