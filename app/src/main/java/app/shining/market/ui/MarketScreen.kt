package app.shining.market.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.shining.R
import app.shining.domain.ui.component.BottomBarSpace
import app.shining.domain.ui.component.CircleIconButton
import app.shining.domain.ui.component.EmojiTile
import app.shining.domain.ui.component.EmptyState
import app.shining.domain.ui.component.InlineMessage
import app.shining.domain.ui.component.LoadingState
import app.shining.domain.ui.component.PillTone
import app.shining.domain.ui.component.ScreenHeader
import app.shining.domain.ui.component.SectionHeader
import app.shining.domain.ui.component.ShiningCard
import app.shining.domain.ui.component.StatusPill
import app.shining.domain.ui.model.ShiningInformationBoxState
import app.shining.domain.ui.model.ShiningInformationBoxUIModel
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.ShiningRoutes
import app.shining.market.domain.toman
import app.shining.order.domain.model.FoodEmoji
import app.shining.order.domain.model.MealType
import kotlinx.coroutines.delay

@Composable
fun MarketComposable(viewModel: MarketViewModel, navController: NavController) {
    LaunchedEffect(Unit) {
        viewModel.onLaunch()
        // Boards move all lunchtime; the open tab keeps up without a pull.
        while (true) {
            delay(20_000)
            viewModel.refreshBoards()
        }
    }
    MarketScreen(
        state = viewModel.state.value,
        onRefresh = viewModel::refresh,
        onJoin = viewModel::onJoin,
        onDaySelected = viewModel::onDaySelected,
        onLeave = viewModel::onLeave,
        onMealClick = { navController.navigate(ShiningRoutes.marketMeal(it)) },
        onTradeClick = { navController.navigate(ShiningRoutes.marketTrade(it)) },
    )
}

@Composable
fun MarketScreen(
    state: MarketScreenState,
    onRefresh: () -> Unit,
    onJoin: () -> Unit,
    onDaySelected: (String) -> Unit,
    onLeave: () -> Unit,
    onMealClick: (String) -> Unit,
    onTradeClick: (Long) -> Unit,
) {
    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        MarketInfoSheet(
            joined = state.joined,
            onDismiss = { showInfo = false },
            onJoin = {
                showInfo = false
                onJoin()
            },
            onLeave = {
                showInfo = false
                onLeave()
            },
        )
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ShiningTheme.colors.background),
        contentPadding = PaddingValues(bottom = BottomBarSpace),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            ScreenHeader(
                title = "بازار",
                subtitle = "کد فراموشی غذا رو بخر و بفروش",
                actions = {
                    if (state.joined) {
                        CircleIconButton(icon = R.drawable.ic_info, onClick = { showInfo = true }, contentDescription = "بازار چطوری کار می‌کنه؟")
                    }
                    CircleIconButton(icon = R.drawable.ic_refresh, onClick = onRefresh, contentDescription = "تازه کردن")
                },
            )
        }
        state.notice?.let { notice ->
            item(key = "notice") {
                InlineMessage(
                    data = ShiningInformationBoxUIModel(ShiningInformationBoxState.FAILED, notice),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        if (!state.joined) {
            item(key = "join") {
                JoinMarketCard(
                    loading = state.joining,
                    error = state.joinError,
                    onJoin = onJoin,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        if (state.activeTrades.isNotEmpty()) {
            item(key = "activeTitle") {
                SectionHeader(title = "معامله‌های در جریان", modifier = Modifier.padding(horizontal = 20.dp))
            }
            items(state.activeTrades, key = { "active-${it.id}" }) { trade ->
                TradeLine(trade = trade, onClick = { onTradeClick(trade.id) }, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        if (state.days.isNotEmpty()) {
            item(key = "days") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.days, key = { it.first }) { (date, label) ->
                        DayChip(label = label, selected = date == state.selectedDay, onClick = { onDaySelected(date) })
                    }
                }
            }
        }
        when {
            state.loading && state.rows.isEmpty() -> item(key = "loading") {
                LoadingState(modifier = Modifier.fillMaxWidth().padding(32.dp))
            }
            state.rows.isEmpty() -> item(key = "empty") {
                ShiningCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    EmptyState(
                        emoji = R.drawable.emoji_plate,
                        title = "غذایی برای معامله پیدا نکردیم",
                        subtitle = "منوی سلف‌هات از سماد نیومد یا وقت سرو غذاهای امروز تموم شده.",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            else -> {
                var lastMeal: String? = null
                state.rows.forEach { row ->
                    if (row.meal.mealName != lastMeal) {
                        lastMeal = row.meal.mealName
                        item(key = "meal-${row.meal.key}") {
                            Text(
                                text = row.meal.mealName,
                                style = ShiningTheme.typography.label,
                                color = ShiningTheme.colors.inkSecondary,
                                modifier = Modifier.padding(start = 20.dp, top = 4.dp),
                            )
                        }
                    }
                    item(key = row.meal.key) {
                        MarketMealCard(row = row, onClick = { onMealClick(row.meal.key) }, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
        if (state.pastTrades.isNotEmpty()) {
            item(key = "pastTitle") {
                SectionHeader(title = "معامله‌های قبلی", modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
            }
            items(state.pastTrades, key = { "past-${it.id}" }) { trade ->
                TradeLine(trade = trade, onClick = { onTradeClick(trade.id) }, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = ShiningTheme.colors
    Text(
        text = label,
        style = ShiningTheme.typography.label,
        color = if (selected) colors.onInk else colors.ink,
        modifier = Modifier
            .clip(ShiningTheme.shapes.pill)
            .background(if (selected) colors.ink else colors.surface)
            .border(1.dp, if (selected) colors.ink else colors.border, ShiningTheme.shapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** A meal in the Market tab: the cheapest listing and the highest offer, or "nobody yet". */
@Composable
private fun MarketMealCard(row: MarketRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ShiningTheme.colors
    ShiningCard(modifier = modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(
                emoji = FoodEmoji.forFood(row.meal.foodName, MealType.getMealTypeById(row.meal.mealTypeId)),
                size = 40.dp,
                emojiSize = 22.dp,
            )
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = row.meal.foodName, style = ShiningTheme.typography.subtitle, color = colors.ink, maxLines = 1)
                Text(text = row.meal.selfName, style = ShiningTheme.typography.caption, color = colors.inkSecondary, maxLines = 1)
            }
            Spacer(modifier = Modifier.size(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (row.reservedByMe) StatusPill(text = "رزرو تو", tone = PillTone.Brand)
                when {
                    row.bestAsk != null -> Text(
                        text = "از ${row.bestAsk.toman()}",
                        style = ShiningTheme.typography.bodyStrong,
                        color = colors.ink,
                    )
                    !row.reservedByMe && !row.hasActivity -> Text(
                        text = "هنوز کسی نیست",
                        style = ShiningTheme.typography.caption,
                        color = colors.inkTertiary,
                    )
                    else -> Unit
                }
                row.bestBid?.let {
                    Text(text = "پیشنهاد ${it.toman()}", style = ShiningTheme.typography.caption, color = colors.positive)
                }
            }
        }
    }
}
