package app.shining.reserveinfo.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.shining.domain.ui.component.ShiningCard
import app.shining.domain.ui.component.ShiningDivider
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.reserveinfo.ui.fixture.reserveInfoCardUIModelFixture
import app.shining.reserveinfo.ui.model.ReservationInfoCardUIModel

/** One day of reservations: the day and its date, then the meal reserved for it. */
@Composable
fun ReservationInfoCard(
    data: ReservationInfoCardUIModel,
    onGetForgetCodeClick: (Int, () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    forSale: Set<Int> = emptySet(),
) {
    ShiningCard(modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.farsiDayName,
                style = ShiningTheme.typography.title,
                color = ShiningTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = data.farsiDate,
                style = ShiningTheme.typography.label,
                color = ShiningTheme.colors.inkSecondary,
                modifier = Modifier
                    .clip(ShiningTheme.shapes.pill)
                    .background(ShiningTheme.colors.surfaceMuted)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        data.mealRowUIModelList.forEach { meal ->
            ShiningDivider(modifier = Modifier.padding(horizontal = 20.dp))
            MealRow(data = meal, onGetForgetCodeClick = onGetForgetCodeClick, forSale = meal.reserveId in forSale)
        }
    }
}

@Preview
@Composable
private fun ReservationInfoCardPreview() {
    ShiningTheme {
        ReservationInfoCard(
            data = reserveInfoCardUIModelFixture,
            onGetForgetCodeClick = { _, _ -> }
        )
    }
}
