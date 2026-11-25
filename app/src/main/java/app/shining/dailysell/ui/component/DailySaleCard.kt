package app.shining.dailysell.ui.component

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.shining.R
import app.shining.dailysell.ui.model.DailySaleCardUIModel
import app.shining.dailysell.ui.model.UserDailySaleCardUIModel
import app.shining.domain.ui.component.EmojiTile
import app.shining.domain.ui.component.PillTone
import app.shining.domain.ui.component.ShiningButton
import app.shining.domain.ui.component.ShiningButtonSize
import app.shining.domain.ui.component.ShiningButtonStyle
import app.shining.domain.ui.component.ShiningCard
import app.shining.domain.ui.component.StatusPill
import app.shining.domain.ui.component.StripedProgressBar
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.toGroupedLocalNumber
import app.shining.domain.util.toLocalNumber
import app.shining.order.domain.model.FoodEmoji
import app.shining.order.domain.model.MealType

@Composable
fun DailySaleCard(
    uiModel: DailySaleCardUIModel,
    onOrderDailySaleClick: (Int, () -> Unit) -> Unit,
    onGetForgetCodeClick: (Int, () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ShiningTheme.colors
    var ordering by remember(uiModel) { mutableStateOf(false) }
    val soldOut = uiModel.soldRatio >= 1f

    ShiningCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(emoji = FoodEmoji.forFood(uiModel.foodName, MealType.fromLocalName(uiModel.mealTypeName)))
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiModel.foodName,
                    style = ShiningTheme.typography.title,
                    color = colors.ink,
                )
                Text(
                    text = listOfNotNull(uiModel.mealTypeName, uiModel.selfName).joinToString("  ·  "),
                    style = ShiningTheme.typography.caption,
                    color = colors.inkSecondary,
                )
            }
            Text(
                text = uiModel.price.toGroupedLocalNumber(),
                style = ShiningTheme.typography.subtitle,
                color = colors.ink,
            )
        }

        uiModel.startTime?.let { start ->
            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(
                    text = stringResource(R.string.daily_sell_time_range, start, uiModel.finishTime ?: "–"),
                    tone = PillTone.Neutral,
                    icon = R.drawable.ic_clock,
                )
            }
        }

        Row(
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.daily_sell_sold),
                style = ShiningTheme.typography.label,
                color = colors.inkSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.daily_sell_sold_of, uiModel.soldCount, uiModel.count),
                style = ShiningTheme.typography.label,
                color = colors.ink,
            )
        }
        StripedProgressBar(
            progress = uiModel.soldRatio,
            color = if (soldOut) colors.negative else colors.ink,
        )

        if (uiModel.showOrderButton) {
            ShiningButton(
                text = stringResource(if (soldOut) R.string.daily_sell_sold_out else R.string.daily_sell_order),
                icon = R.drawable.ic_shopping_cart,
                enabled = !soldOut,
                loading = ordering,
                onClick = {
                    ordering = true
                    onOrderDailySaleClick(uiModel.id) { ordering = false }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
        }

        uiModel.userDailySaleInfo?.let {
            UserDailySaleRow(
                uiModel = it,
                onGetForgetCode = onGetForgetCodeClick,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/** The user's own purchase of this item: its state and the forget code. */
@Composable
private fun UserDailySaleRow(
    uiModel: UserDailySaleCardUIModel,
    onGetForgetCode: (Int, () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var loading by remember(uiModel) { mutableStateOf(false) }
    val copiedMessage = stringResource(R.string.forget_code_copied)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShiningTheme.shapes.tile)
            .background(if (uiModel.consumed) ShiningTheme.colors.surfaceMuted else ShiningTheme.colors.positiveSoft)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusPill(
            text = stringResource(if (uiModel.consumed) R.string.reservation_consumed else R.string.order_order_food_detail_selected),
            tone = if (uiModel.consumed) PillTone.Neutral else PillTone.Positive,
            icon = R.drawable.ic_check,
        )
        Spacer(modifier = Modifier.weight(1f))
        val code = uiModel.forgetCode
        if (code != null) {
            Row(
                modifier = Modifier
                    .clip(ShiningTheme.shapes.pill)
                    .background(ShiningTheme.colors.brand)
                    .clickable {
                        clipboard.setText(AnnotatedString(code))
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
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
                    text = code.toLocalNumber(),
                    style = ShiningTheme.typography.button,
                    color = ShiningTheme.colors.onBrand,
                )
            }
        } else if (!uiModel.consumed) {
            ShiningButton(
                text = stringResource(R.string.forget_code_short),
                icon = R.drawable.ic_code,
                style = ShiningButtonStyle.Secondary,
                size = ShiningButtonSize.Small,
                loading = loading,
                onClick = {
                    loading = true
                    onGetForgetCode(uiModel.id) { loading = false }
                },
            )
        }
    }
}

@Preview
@Composable
private fun DailySaleCardPreview() {
    ShiningTheme {
        DailySaleCard(
            uiModel = DailySaleCardUIModel(
                count = "10".toLocalNumber(),
                soldCount = "6".toLocalNumber(),
                foodName = "پیتزا",
                mealTypeName = "ناهار",
                price = "100000",
                startTime = "12:00".toLocalNumber(),
                finishTime = "14:00".toLocalNumber(),
                showOrderButton = true,
                id = 0,
                selfName = "سلف مرکزی",
                userDailySaleInfo = null,
                soldRatio = 0.6f,
            ),
            onOrderDailySaleClick = { _, _ -> },
            onGetForgetCodeClick = { _, _ -> },
            modifier = Modifier.padding(16.dp),
        )
    }
}
