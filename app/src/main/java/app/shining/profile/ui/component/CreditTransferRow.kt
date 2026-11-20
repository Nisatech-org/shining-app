package app.shining.profile.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.shining.R
import app.shining.domain.ui.component.EmojiTile
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.toGroupedLocalNumber
import app.shining.profile.api.dto.nurture.toCreditTransfer
import app.shining.profile.ui.fixture.profileCreditTransferDto1
import app.shining.profile.ui.model.CreditTransferUIModel
import app.shining.profile.ui.model.toCreditTransferUIModel

/** A credit change: green arrow up for top-ups, red arrow down for spending. */
@Composable
fun CreditTransferRow(
    data: CreditTransferUIModel,
    modifier: Modifier = Modifier,
) {
    val colors = ShiningTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiTile(
            emoji = if (data.isPositive) R.drawable.emoji_money_bag else R.drawable.emoji_money_wings,
            size = 38.dp,
            emojiSize = 22.dp,
            background = if (data.isPositive) colors.positiveSoft else colors.negativeSoft,
            bordered = false,
            shape = ShiningTheme.shapes.pill,
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (data.isPositive) R.string.profile_transaction_in else R.string.profile_transaction_out
                ),
                style = ShiningTheme.typography.bodyStrong,
                color = colors.ink,
            )
            Text(
                text = data.date,
                style = ShiningTheme.typography.caption,
                color = colors.inkSecondary,
            )
        }
        Text(
            text = (if (data.isPositive) "+" else "−") +
                kotlin.math.abs(data.amountValue).toGroupedLocalNumber(),
            style = ShiningTheme.typography.subtitle.copy(textDirection = TextDirection.Ltr),
            color = if (data.isPositive) colors.positive else colors.negative,
        )
    }
}

@Preview
@Composable
private fun CreditTransferRowPreview() {
    ShiningTheme {
        CreditTransferRow(
            data = profileCreditTransferDto1.toCreditTransfer().toCreditTransferUIModel()
        )
    }
}
