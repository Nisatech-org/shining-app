package app.shining.dailysell.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.shining.R
import app.shining.dailysell.ui.model.DailySellScreenUIModel
import app.shining.domain.ui.component.BottomBarSpace
import app.shining.domain.ui.component.EmptyState

@Composable
fun DailySaleScreen(
    uiModel: DailySellScreenUIModel,
    onOrderDailySaleClick: (Int, () -> Unit) -> Unit,
    onGetForgetCodeClick: (Int, () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiModel.dailySaleCardUIModelList.isEmpty()) {
        EmptyState(
            emoji = R.drawable.emoji_cart,
            title = stringResource(R.string.daily_sell_no_program_found),
            subtitle = stringResource(R.string.daily_sell_no_program_hint),
            modifier = modifier.fillMaxSize(),
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = BottomBarSpace),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiModel.dailySaleCardUIModelList, key = { it.id }) {
                DailySaleCard(
                    uiModel = it,
                    onOrderDailySaleClick = onOrderDailySaleClick,
                    onGetForgetCodeClick = onGetForgetCodeClick,
                )
            }
        }
    }
}
