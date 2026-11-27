package app.shining.domain.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.shining.domain.ui.theme.ShiningTheme

enum class PillTone { Neutral, Positive, Negative, Warning, Brand, Ink }

/** A small rounded label for a status, like "reserved" or "+5%". */
@Composable
fun StatusPill(
    text: String,
    tone: PillTone,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    val colors = ShiningTheme.colors
    val (container, content) = when (tone) {
        PillTone.Neutral -> colors.surfaceMuted to colors.inkSecondary
        PillTone.Positive -> colors.positiveSoft to colors.positive
        PillTone.Negative -> colors.negativeSoft to colors.negative
        PillTone.Warning -> colors.warningSoft to colors.warning
        PillTone.Brand -> colors.brand to colors.onBrand
        PillTone.Ink -> colors.ink to colors.onInk
    }
    Row(
        modifier = modifier
            .clip(ShiningTheme.shapes.pill)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon?.let {
            Icon(
                painter = painterResource(it),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(text = text, style = ShiningTheme.typography.label, color = content, maxLines = 1)
    }
}

/**
 * A progress bar whose empty part is hatched with diagonal stripes. Fills from the start edge,
 * so from the right in Persian.
 */
@Composable
fun StripedProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = ShiningTheme.colors.ink,
    height: Dp = 16.dp,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "progress",
    )
    val track = ShiningTheme.colors.surfaceMuted
    val stripe = ShiningTheme.colors.border
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(ShiningTheme.shapes.pill)
    ) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color = track, cornerRadius = radius)

        val outline = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = 0f, top = 0f, right = size.width, bottom = size.height,
                    cornerRadius = radius,
                )
            )
        }
        clipPath(outline) {
            val step = 7.dp.toPx()
            val strokeWidth = 2.dp.toPx()
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(
                    color = stripe,
                    start = Offset(x, size.height),
                    end = Offset(x + size.height, 0f),
                    strokeWidth = strokeWidth,
                )
                x += step
            }
        }

        val filled = size.width * animated
        if (filled > 0f) {
            val width = filled.coerceAtLeast(size.height)
            drawRoundRect(
                color = color,
                topLeft = Offset(if (isRtl) size.width - width else 0f, 0f),
                size = Size(width, size.height),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
fun ShiningProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = ShiningTheme.colors.ink,
    size: Dp = 32.dp,
    strokeWidth: Dp = 3.dp,
) {
    CircularProgressIndicator(
        color = color,
        strokeWidth = strokeWidth,
        modifier = modifier.size(size),
    )
}

/** A dot used to mark something, like a day that already has a reservation. */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 6.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(ShiningTheme.shapes.pill)
            .background(color)
    )
}

@Composable
fun StarRating(
    rating: Int,
    onRatingChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    starSize: Dp = 26.dp,
    @DrawableRes filledIcon: Int,
    @DrawableRes emptyIcon: Int,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..5).forEach { index ->
            val filled = index <= rating
            Box(
                modifier = Modifier
                    .size(starSize + 6.dp)
                    .clip(ShiningTheme.shapes.pill)
                    .then(
                        Modifier.clickableNoRipple { onRatingChange(index) }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (filled) filledIcon else emptyIcon),
                    contentDescription = null,
                    tint = if (filled) ShiningTheme.colors.brand else ShiningTheme.colors.inkTertiary,
                    modifier = Modifier
                        .size(starSize)
                        .padding(1.dp),
                )
            }
        }
    }
}
