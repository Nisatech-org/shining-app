package app.shining.domain.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.shining.domain.ui.theme.ShiningTheme

enum class ShiningButtonStyle {
    /** Black pill, for the main action of a screen or card. */
    Primary,

    /** Soft grey pill, for secondary actions ("+ Add"). */
    Secondary,

    /** Lime pill with ink text. */
    Brand,

    /** Red-tinted pill for cancel and remove. */
    Danger,

    /** No background. */
    Ghost,
}

enum class ShiningButtonSize(val height: Dp, val horizontalPadding: Dp, val iconSize: Dp) {
    Large(56.dp, 26.dp, 20.dp),
    Medium(48.dp, 20.dp, 18.dp),
    Small(36.dp, 14.dp, 16.dp),
}

@Composable
fun ShiningButton(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    style: ShiningButtonStyle = ShiningButtonStyle.Primary,
    size: ShiningButtonSize = ShiningButtonSize.Medium,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = ShiningTheme.colors
    val (container, content) = when (style) {
        ShiningButtonStyle.Primary -> colors.ink to colors.onInk
        ShiningButtonStyle.Secondary -> colors.surfaceMuted to colors.ink
        ShiningButtonStyle.Brand -> colors.brand to colors.onBrand
        ShiningButtonStyle.Danger -> colors.negativeSoft to colors.negative
        ShiningButtonStyle.Ghost -> Color.Transparent to colors.ink
    }
    val shape = ShiningTheme.shapes.pill

    Row(
        modifier = modifier
            .height(size.height)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(container)
            .clickable(
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = size.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (loading) {
            ShiningProgressIndicator(
                color = content,
                size = size.iconSize,
                strokeWidth = 2.dp,
            )
        } else if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(size.iconSize),
            )
        }
        text?.let {
            Text(
                text = it,
                style = if (size == ShiningButtonSize.Small) {
                    ShiningTheme.typography.label
                } else {
                    ShiningTheme.typography.button
                },
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A round icon-only button, like the ones in screen headers. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CircleIconButton(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 20.dp,
    background: Color = ShiningTheme.colors.surface,
    tint: Color = ShiningTheme.colors.ink,
    bordered: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val shape = ShiningTheme.shapes.pill
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background)
            .then(
                if (bordered) Modifier.border(1.dp, ShiningTheme.colors.border, shape) else Modifier
            )
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick?.let {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        it()
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}
