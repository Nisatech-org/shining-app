package app.shining.domain.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.shining.domain.ui.theme.ShiningTheme

/** A white rounded card with a hairline border, the main building block of every screen. */
@Composable
fun ShiningCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    background: Color = ShiningTheme.colors.surface,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = ShiningTheme.shapes.card
    Column(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(1.dp, ShiningTheme.colors.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** A small boxed line icon, tinted with [tint]. For pictures, use [EmojiTile]. */
@Composable
fun IconTile(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    background: Color = ShiningTheme.colors.surface,
    tint: Color = ShiningTheme.colors.ink,
    bordered: Boolean = true,
    shape: Shape = ShiningTheme.shapes.tile,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background)
            .then(
                if (bordered) Modifier.border(1.dp, ShiningTheme.colors.border, shape) else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * An Apple emoji, drawn from the bundled `emoji_*` images in its own colors. [muted] draws it in
 * grey, for things that are done (an eaten meal).
 */
@Composable
fun Emoji(
    @DrawableRes emoji: Int,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    muted: Boolean = false,
) {
    Image(
        painter = painterResource(emoji),
        contentDescription = null,
        modifier = modifier.size(size),
        alpha = if (muted) 0.5f else 1f,
        colorFilter = if (muted) GreyscaleFilter else null,
    )
}

private val GreyscaleFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** An [Emoji] in a rounded box, as used in front of card titles and list rows. */
@Composable
fun EmojiTile(
    @DrawableRes emoji: Int,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    emojiSize: Dp = 22.dp,
    background: Color = ShiningTheme.colors.surface,
    bordered: Boolean = true,
    shape: Shape = ShiningTheme.shapes.tile,
    muted: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background)
            .then(
                if (bordered) Modifier.border(1.dp, ShiningTheme.colors.border, shape) else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Emoji(emoji = emoji, size = emojiSize, muted = muted)
    }
}

/** Title above a group of cards, with an optional trailing action. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = ShiningTheme.typography.title,
            color = ShiningTheme.colors.ink,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
fun ShiningDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ShiningTheme.colors.border)
    )
}

/** Space reserved at the bottom of scrolling content so the floating navigation bar never covers it. */
val BottomBarSpace: Dp = 104.dp
