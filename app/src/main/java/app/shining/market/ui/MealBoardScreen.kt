package app.shining.market.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.shining.domain.ui.component.ErrorState
import app.shining.domain.ui.component.LoadingState
import app.shining.domain.ui.component.MessageBanner
import app.shining.domain.ui.component.ScreenHeader
import app.shining.domain.ui.component.SectionHeader
import app.shining.domain.ui.component.ShiningButton
import app.shining.domain.ui.component.ShiningButtonStyle
import app.shining.domain.ui.component.ShiningCard
import app.shining.domain.ui.component.ShiningDivider
import app.shining.domain.ui.component.ShiningTextField
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.ShiningRoutes
import app.shining.market.domain.MarketMeals
import app.shining.market.domain.label
import app.shining.market.domain.toman
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun MealBoardComposable(viewModel: MealBoardViewModel, navController: NavController) {
    LaunchedEffect(Unit) { viewModel.onLaunch() }
    val tradeId = viewModel.openTrade.value
    LaunchedEffect(tradeId) {
        if (tradeId != null) {
            viewModel.openTrade.value = null
            navController.navigate(ShiningRoutes.marketTrade(tradeId)) {
                popUpTo(ShiningRoutes.MarketMealScreen.route) { inclusive = true }
            }
        }
    }
    val scope = rememberCoroutineScope()
    MealBoardScreen(
        state = viewModel.state.value,
        price = viewModel.price.value,
        card = viewModel.card.value,
        onPriceChange = { viewModel.price.value = it },
        onCardChange = { viewModel.card.value = it },
        onBack = { navController.popBackStack() },
        onRetry = { scope.launch { viewModel.refresh() } },
        onJoin = { viewModel.onJoin() },
        onBuyNow = viewModel::onBuyNow,
        onSellNow = viewModel::onSellNow,
        onPlaceAsk = viewModel::onPlaceAsk,
        onPlaceBid = viewModel::onPlaceBid,
        onCancelOrder = { viewModel.onCancelOrder(it) },
        onOpenTrade = { navController.navigate(ShiningRoutes.marketTrade(it)) },
    )
}

@Composable
fun MealBoardScreen(
    state: MealBoardState,
    price: String,
    card: String,
    onPriceChange: (String) -> Unit,
    onCardChange: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onJoin: () -> Unit,
    onBuyNow: () -> Unit,
    onSellNow: () -> Unit,
    onPlaceAsk: () -> Unit,
    onPlaceBid: () -> Unit,
    onCancelOrder: (Long) -> Unit,
    onOpenTrade: (Long) -> Unit,
) {
    val colors = ShiningTheme.colors
    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                ScreenHeader(
                    title = if (state.isSeller) "فروش غذا" else "خرید غذا",
                    subtitle = "بورد این غذا",
                    onBackClick = onBack,
                )
            }
            val meal = state.meal
            if (meal == null) {
                item(key = "noMeal") {
                    if (state.loading) {
                        LoadingState(modifier = Modifier.fillMaxWidth().padding(32.dp))
                    } else {
                        ErrorState(message = state.error ?: "این غذا پیدا نشد.", onRetry = onRetry, modifier = Modifier.fillMaxWidth())
                    }
                }
                return@LazyColumn
            }
            item(key = "meal") {
                ShiningCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MarketMealHeader(meal = meal)
                    Spacer(modifier = Modifier.size(12.dp))
                    ShiningDivider()
                    Spacer(modifier = Modifier.size(8.dp))
                    DetailLine("الان معامله می‌شه", state.board?.priceNow?.toman() ?: "هنوز معامله‌ای نشده")
                    state.board?.lastPrice?.let { DetailLine("آخرین معامله", it.toman()) }
                    meal.reservedPriceText()?.let { DetailLine("قیمت رزرو در سماد", it) }
                    DetailLine("تا", "${MarketMeals.clock(MarketMeals.endsAt(meal))} ${MarketMeals.dayLabel(meal.date)}")
                }
            }
            state.error?.let { error ->
                if (state.board == null && !state.loading) {
                    item(key = "error") {
                        Text(text = error, style = ShiningTheme.typography.label, color = colors.negative, modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
            state.board?.activeTradeId?.let { tradeId ->
                item(key = "activeTrade") {
                    ShiningCard(modifier = Modifier.padding(horizontal = 16.dp), background = colors.brandSoft) {
                        Text(text = "یه معامله برای این غذا داری که هنوز تموم نشده.", style = ShiningTheme.typography.bodyStrong, color = colors.ink)
                        Spacer(modifier = Modifier.size(10.dp))
                        ShiningButton(text = "برو به معامله", onClick = { onOpenTrade(tradeId) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            when {
                !state.open -> item(key = "closed") {
                    ShiningCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(text = "وقت سرو این غذا تموم شده.", style = ShiningTheme.typography.bodyStrong, color = colors.inkSecondary)
                    }
                }
                !state.joined -> item(key = "join") {
                    JoinMarketCard(loading = state.joining, error = state.joinError, onJoin = onJoin, modifier = Modifier.padding(horizontal = 16.dp))
                }
                state.board?.activeTradeId == null -> item(key = "actions") {
                    if (state.isSeller) {
                        SellActions(state, price, card, onPriceChange, onCardChange, onSellNow, onPlaceAsk, onCancelOrder)
                    } else {
                        BuyActions(state, price, onPriceChange, onBuyNow, onPlaceBid, onCancelOrder)
                    }
                }
            }
            val board = state.board
            if (board != null) {
                item(key = "asksTitle") {
                    SectionHeader(title = "فروشنده‌ها", modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
                }
                if (board.asks.isEmpty()) {
                    item(key = "asksEmpty") { EmptyLine("هنوز کسی این غذا رو نمی‌فروشه.") }
                }
                items(board.asks, key = { "ask-${it.id}" }) { order ->
                    OrderLine(order = order, modifier = Modifier.padding(horizontal = 16.dp))
                }
                item(key = "bidsTitle") {
                    SectionHeader(title = "پیشنهادهای خرید", modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
                }
                if (board.bids.isEmpty()) {
                    item(key = "bidsEmpty") { EmptyLine("هنوز کسی پیشنهاد نداده.") }
                }
                items(board.bids, key = { "bid-${it.id}" }) { order ->
                    OrderLine(order = order, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        MessageBanner(visible = state.message != null, data = state.message, modifier = Modifier.align(Alignment.TopCenter))
    }
}

/** The seller's side: sell now to the highest offer, or list at their own price. */
@Composable
private fun SellActions(
    state: MealBoardState,
    price: String,
    card: String,
    onPriceChange: (String) -> Unit,
    onCardChange: (String) -> Unit,
    onSellNow: () -> Unit,
    onPlaceAsk: () -> Unit,
    onCancelOrder: (Long) -> Unit,
) {
    val colors = ShiningTheme.colors
    ShiningCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(text = "این غذا رو رزرو کردی. بفروشش:", style = ShiningTheme.typography.title, color = colors.ink)
        Spacer(modifier = Modifier.size(12.dp))
        if (state.card == null) {
            ShiningTextField(
                value = card,
                onValueChange = { text -> onCardChange(text.filter { it.isDigit() || it == ' ' || it == '-' }.take(24)) },
                label = "شماره‌ی کارتت (خریدار پول رو اینجا می‌زنه)",
                placeholder = "۱۶ رقم روی کارت",
                ltrInput = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(modifier = Modifier.size(12.dp))
        } else {
            Text(text = "واریز به کارت ${ltr(state.card)}", style = ShiningTheme.typography.caption, color = colors.inkSecondary)
            Spacer(modifier = Modifier.size(12.dp))
        }
        state.highestOffer?.let { offer ->
            ShiningButton(
                text = "فروش فوری به ${offer.price.toman()}",
                onClick = onSellNow,
                loading = state.busy == "sell",
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "بالاترین پیشنهاد · خریدار: ${offer.reputation.label(asSeller = false)}",
                style = ShiningTheme.typography.caption,
                color = colors.inkSecondary,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
            Spacer(modifier = Modifier.size(16.dp))
        }
        val myAsk = state.myAsk
        if (myAsk != null) {
            OrderLine(order = myAsk, onCancel = { onCancelOrder(myAsk.id) })
            Text(
                text = "آگهیت روی بورده. وقتی کسی بخره، خبرت می‌کنیم و ۵ دقیقه وقت داره پول رو بزنه.",
                style = ShiningTheme.typography.caption,
                color = colors.inkSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            PriceField(value = price, onValueChange = onPriceChange, label = "یا با قیمت خودت آگهی بذار (تومن)")
            Spacer(modifier = Modifier.size(10.dp))
            ShiningButton(
                text = "ثبت آگهی",
                onClick = onPlaceAsk,
                style = if (state.highestOffer == null) ShiningButtonStyle.Primary else ShiningButtonStyle.Secondary,
                loading = state.busy == "ask",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The buyer's side: buy the cheapest listing now, or make an offer. */
@Composable
private fun BuyActions(
    state: MealBoardState,
    price: String,
    onPriceChange: (String) -> Unit,
    onBuyNow: () -> Unit,
    onPlaceBid: () -> Unit,
    onCancelOrder: (Long) -> Unit,
) {
    val colors = ShiningTheme.colors
    ShiningCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        state.cheapestListing?.let { listing ->
            ShiningButton(
                text = "خرید فوری به ${listing.price.toman()}",
                onClick = onBuyNow,
                style = ShiningButtonStyle.Brand,
                loading = state.busy == "buy",
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "ارزون‌ترین آگهی. بعدش ۵ دقیقه وقت داری کارت‌به‌کارت کنی.",
                style = ShiningTheme.typography.caption,
                color = colors.inkSecondary,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
            Spacer(modifier = Modifier.size(16.dp))
        }
        state.myBid?.let { bid ->
            OrderLine(order = bid, onCancel = { onCancelOrder(bid.id) })
            Spacer(modifier = Modifier.size(10.dp))
        }
        PriceField(
            value = price,
            onValueChange = onPriceChange,
            label = if (state.myBid != null) "عوض کردن پیشنهادت (تومن)" else "پیشنهاد قیمتت (تومن)",
        )
        Spacer(modifier = Modifier.size(10.dp))
        ShiningButton(
            text = if (state.myBid != null) "ثبت پیشنهاد تازه" else "ثبت پیشنهاد",
            onClick = onPlaceBid,
            style = if (state.cheapestListing == null) ShiningButtonStyle.Primary else ShiningButtonStyle.Secondary,
            loading = state.busy == "bid",
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "اگه کسی به قیمت پیشنهادت یا ارزون‌تر بفروشه، خبرت می‌کنیم. فروشنده‌ها هم می‌تونن پیشنهادت رو قبول کنن.",
            style = ShiningTheme.typography.caption,
            color = colors.inkSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = ShiningTheme.typography.body,
        color = ShiningTheme.colors.inkTertiary,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}
