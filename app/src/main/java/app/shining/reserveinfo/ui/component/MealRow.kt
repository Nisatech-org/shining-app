package app.shining.reserveinfo.ui.component

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.shining.R
import app.shining.domain.ui.component.EmojiTile
import app.shining.domain.ui.component.PillTone
import app.shining.domain.ui.component.ShiningButton
import app.shining.domain.ui.component.ShiningButtonSize
import app.shining.domain.ui.component.ShiningButtonStyle
import app.shining.domain.ui.component.StatusPill
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.toLocalNumber
import app.shining.order.domain.model.FoodEmoji
import app.shining.order.domain.model.MealType
import app.shining.reserveinfo.ui.fixture.reserveInfoMealRowUIModelFixture1
import app.shining.reserveinfo.ui.model.MealRowUIModel

@Composable
fun MealRow(
    data: MealRowUIModel,
    onGetForgetCodeClick: (Int, () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * On the market or sold: greyed out, so it isn't eaten by mistake. It stays in the list
     * (the user may cancel the listing).
     */
    forSale: Boolean = false,
) {
    val colors = ShiningTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (forSale) 0.45f else 1f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiTile(
            emoji = FoodEmoji.forFood(data.foodName, MealType.fromLocalName(data.mealName)),
            background = if (data.consumed) colors.surfaceMuted else colors.surface,
            muted = data.consumed || forSale,
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = data.foodName,
                style = ShiningTheme.typography.subtitle,
                color = if (data.consumed || forSale) colors.inkSecondary else colors.ink,
            )
            if (forSale) {
                Text(
                    text = "گذاشتی توی بازار",
                    style = ShiningTheme.typography.caption,
                    color = colors.inkSecondary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = data.mealName,
                    style = ShiningTheme.typography.caption,
                    color = colors.inkSecondary,
                )
                Text(
                    text = "  ·  ",
                    style = ShiningTheme.typography.caption,
                    color = colors.inkTertiary,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_location),
                    contentDescription = null,
                    tint = colors.inkTertiary,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(modifier = Modifier.size(2.dp))
                Text(
                    text = data.selfName,
                    style = ShiningTheme.typography.caption,
                    color = colors.inkSecondary,
                    maxLines = 1,
                )
            }
        }
        Spacer(modifier = Modifier.size(8.dp))
        ForgetCodeAction(data = data, onGetForgetCodeClick = onGetForgetCodeClick)
    }
}

/** "Consumed", the forget code (tap to copy), or a button to fetch the code. */
@Composable
private fun ForgetCodeAction(
    data: MealRowUIModel,
    onGetForgetCodeClick: (Int, () -> Unit) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var loading by remember(data) { mutableStateOf(false) }
    val copiedMessage = stringResource(R.string.forget_code_copied)

    when {
        data.forgetCode != null -> {
            Row(
                modifier = Modifier
                    .clip(ShiningTheme.shapes.pill)
                    .background(ShiningTheme.colors.brand)
                    .clickable {
                        clipboard.setText(AnnotatedString(data.forgetCode))
                        Toast
                            .makeText(context, copiedMessage, Toast.LENGTH_SHORT)
                            .show()
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_content_copy),
                    contentDescription = stringResource(R.string.forget_code_copy),
                    tint = ShiningTheme.colors.onBrand,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = data.forgetCode.toLocalNumber(),
                    style = ShiningTheme.typography.button,
                    color = ShiningTheme.colors.onBrand,
                )
            }
        }

        data.consumed -> StatusPill(
            text = stringResource(R.string.reservation_consumed),
            tone = PillTone.Neutral,
            icon = R.drawable.ic_check,
        )

        else -> ShiningButton(
            text = stringResource(R.string.forget_code_short),
            icon = R.drawable.ic_code,
            style = ShiningButtonStyle.Secondary,
            size = ShiningButtonSize.Small,
            loading = loading,
            onClick = {
                loading = true
                onGetForgetCodeClick(data.reserveId) { loading = false }
            },
        )
    }
}

@Preview
@Composable
private fun MealRowPreview() {
    ShiningTheme {
        MealRow(
            data = reserveInfoMealRowUIModelFixture1,
            onGetForgetCodeClick = { _, _ -> }
        )
    }
}
