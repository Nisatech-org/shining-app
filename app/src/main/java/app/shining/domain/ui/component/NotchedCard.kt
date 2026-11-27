package app.shining.domain.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.shining.domain.ui.theme.ShiningTheme

enum class NotchCorner { TopEnd, BottomEnd }

/**
 * A rounded rectangle with a rounded bite taken out of one corner, so round buttons can sit in
 * the gap and the card flows around them. The notch's own corners are rounded too: convex where
 * the card turns into it, concave at its inner corner.
 */
class NotchedShape(
    private val notchWidth: Dp,
    private val notchHeight: Dp,
    private val corner: NotchCorner = NotchCorner.TopEnd,
    private val cornerRadius: Dp = 30.dp,
    private val notchRadius: Dp = 22.dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val (r, nw, nh, nr) = with(density) {
            listOf(cornerRadius.toPx(), notchWidth.toPx(), notchHeight.toPx(), notchRadius.toPx())
        }
        // Drawn with the notch at the top right, then mirrored: in RTL "end" is the left side.
        val flipX = layoutDirection == LayoutDirection.Rtl
        val flipY = corner == NotchCorner.BottomEnd
        fun x(v: Float) = if (flipX) w - v else v
        fun y(v: Float) = if (flipY) h - v else v
        fun rect(l: Float, t: Float, rr: Float, b: Float) =
            Rect(minOf(x(l), x(rr)), minOf(y(t), y(b)), maxOf(x(l), x(rr)), maxOf(y(t), y(b)))
        fun angle(a: Float): Float {
            var out = a
            if (flipX) out = 180f - out
            if (flipY) out = -out
            return out
        }
        val flips = (if (flipX) 1 else 0) + (if (flipY) 1 else 0)
        fun sweep(s: Float) = if (flips % 2 == 1) -s else s

        val path = Path().apply {
            moveTo(x(0f), y(r))
            arcTo(rect(0f, 0f, 2 * r, 2 * r), angle(180f), sweep(90f), false)
            lineTo(x(w - nw - nr), y(0f))
            // The card turns down into the notch.
            arcTo(rect(w - nw - 2 * nr, 0f, w - nw, 2 * nr), angle(270f), sweep(90f), false)
            lineTo(x(w - nw), y(nh - nr))
            // The notch's inner corner, curving the other way.
            arcTo(rect(w - nw, nh - 2 * nr, w - nw + 2 * nr, nh), angle(180f), sweep(-90f), false)
            lineTo(x(w - nr), y(nh))
            // And back out to the card's edge.
            arcTo(rect(w - 2 * nr, nh, w, nh + 2 * nr), angle(270f), sweep(90f), false)
            lineTo(x(w), y(h - r))
            arcTo(rect(w - 2 * r, h - 2 * r, w, h), angle(0f), sweep(90f), false)
            lineTo(x(r), y(h))
            arcTo(rect(0f, h - 2 * r, 2 * r, h), angle(90f), sweep(90f), false)
            close()
        }
        return Outline.Generic(path)
    }
}

/** Gap between the card's edge and whatever sits in its notch. */
val NotchGap: Dp = 8.dp

/**
 * A card with a notch at [corner] that holds [notch] (round buttons, a pill). The notch is sized
 * to [notchWidth] by [notchHeight] including the [NotchGap] around its content.
 */
@Composable
fun NotchedCard(
    notchWidth: Dp,
    notchHeight: Dp,
    modifier: Modifier = Modifier,
    corner: NotchCorner = NotchCorner.TopEnd,
    background: Color = ShiningTheme.colors.brand,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    notch: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(NotchedShape(notchWidth, notchHeight, corner))
                .background(background)
                .padding(contentPadding),
            content = content,
        )
        Row(
            modifier = Modifier
                .align(if (corner == NotchCorner.TopEnd) Alignment.TopEnd else Alignment.BottomEnd)
                .size(notchWidth, notchHeight)
                .padding(
                    start = NotchGap,
                    top = if (corner == NotchCorner.BottomEnd) NotchGap else 0.dp,
                    bottom = if (corner == NotchCorner.TopEnd) NotchGap else 0.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(NotchGap, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            content = notch,
        )
    }
}
