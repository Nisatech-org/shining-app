package app.shining.domain.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.shining.R
import app.shining.domain.ui.model.ShiningInformationBoxState
import app.shining.domain.ui.model.ShiningInformationBoxUIModel
import app.shining.domain.ui.theme.ShiningTheme

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}

/**
 * The top of a screen: an optional back button, a large title with an optional subtitle, and
 * actions at the end.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onBackClick?.let {
            CircleIconButton(
                icon = R.drawable.ic_back,
                onClick = it,
                contentDescription = stringResource(R.string.back),
            )
            Spacer(modifier = Modifier.size(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = ShiningTheme.typography.headline,
                color = ShiningTheme.colors.ink,
                maxLines = 1,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = ShiningTheme.typography.caption,
                    color = ShiningTheme.colors.inkSecondary,
                    maxLines = 1,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        ShiningProgressIndicator()
    }
}

@Composable
fun EmptyState(
    @DrawableRes emoji: Int,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmojiTile(
            emoji = emoji,
            size = 72.dp,
            emojiSize = 38.dp,
            shape = RoundedCornerShape(24.dp),
            background = ShiningTheme.colors.surface,
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = title,
            style = ShiningTheme.typography.title,
            color = ShiningTheme.colors.ink,
            textAlign = TextAlign.Center,
        )
        subtitle?.let {
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = it,
                style = ShiningTheme.typography.body,
                color = ShiningTheme.colors.inkSecondary,
                textAlign = TextAlign.Center,
            )
        }
        action?.let {
            Spacer(modifier = Modifier.size(20.dp))
            it()
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        emoji = R.drawable.emoji_warning,
        title = message,
        modifier = modifier,
        action = {
            ShiningButton(
                text = stringResource(R.string.retry),
                icon = R.drawable.ic_refresh,
                onClick = onRetry,
            )
        },
    )
}

/** A message row inside the layout, for results and errors that should stay in place. */
@Composable
fun InlineMessage(
    data: ShiningInformationBoxUIModel,
    modifier: Modifier = Modifier,
) {
    val colors = ShiningTheme.colors
    val success = data.state == ShiningInformationBoxState.SUCCESS
    ShiningCard(
        modifier = modifier.fillMaxWidth(),
        background = if (success) colors.positiveSoft else colors.negativeSoft,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                icon = if (success) R.drawable.ic_check_rounded else R.drawable.ic_exclamation_rounded,
                size = 32.dp,
                iconSize = 18.dp,
                background = if (success) colors.positive else colors.negative,
                tint = colors.surface,
                bordered = false,
                shape = ShiningTheme.shapes.pill,
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = data.message,
                style = ShiningTheme.typography.bodyStrong,
                color = if (success) colors.positive else colors.negative,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * A floating message that slides in from the top of the screen. Keeps showing the last message
 * while it animates out.
 */
@Composable
fun MessageBanner(
    visible: Boolean,
    data: ShiningInformationBoxUIModel?,
    modifier: Modifier = Modifier,
) {
    val last = remember { mutableStateOf(data) }
    if (data != null) last.value = data
    AnimatedVisibility(
        visible = visible && last.value != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        last.value?.let {
            val colors = ShiningTheme.colors
            val success = it.state == ShiningInformationBoxState.SUCCESS
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .shadow(16.dp, ShiningTheme.shapes.card),
            ) {
                ShiningCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(
                            icon = if (success) R.drawable.ic_check_rounded else R.drawable.ic_exclamation_rounded,
                            size = 32.dp,
                            iconSize = 18.dp,
                            background = if (success) colors.positiveSoft else colors.negativeSoft,
                            tint = if (success) colors.positive else colors.negative,
                            bordered = false,
                            shape = ShiningTheme.shapes.pill,
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(
                            text = it.message,
                            style = ShiningTheme.typography.bodyStrong,
                            color = colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
