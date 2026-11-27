package app.shining.market.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
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
import app.shining.domain.ui.component.ShiningButton
import app.shining.domain.ui.component.ShiningButtonSize
import app.shining.domain.ui.component.ShiningButtonStyle
import app.shining.domain.ui.component.ShiningCard
import app.shining.domain.ui.component.StatusPill
import app.shining.domain.ui.model.ShiningInformationBoxState
import app.shining.domain.ui.model.ShiningInformationBoxUIModel
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.ShiningRoutes
import app.shining.login.domain.model.LoginMode
import app.shining.market.api.ListingView
import app.shining.market.domain.MarketMeals
import app.shining.market.domain.label
import app.shining.market.domain.toman
import app.shining.order.domain.model.FoodEmoji
import app.shining.order.domain.model.MealType
import app.shining.order.domain.model.shortSelfName
import kotlinx.coroutines.delay

@Composable
fun MarketComposable(viewModel: MarketViewModel, navController: NavController) {
    LaunchedEffect(Unit) {
        viewModel.onLaunch()
        // Listings move all lunchtime; the open tab keeps up without a pull.
        while (true) {
            delay(20_000)
            viewModel.refresh()
        }
    }
    val openTrade by viewModel.openTrade.collectAsState()
    LaunchedEffect(openTrade) {
        openTrade?.let {
            viewModel.onTradeOpened()
            navController.navigate(ShiningRoutes.marketTrade(it))
        }
    }
    MarketScreen(
        state = viewModel.state.value,
        onRefresh = viewModel::refresh,
        onJoin = viewModel::onJoin,
        onLeave = viewModel::onLeave,
        onLogin = { navController.navigate(ShiningRoutes.login(LoginMode.Initial)) },
        onDaySelected = viewModel::onDaySelected,
        onMealTypeSelected = viewModel::onMealTypeSelected,
        onSelfSelected = viewModel::onSelfSelected,
        onSell = { viewModel.openSell() },
        onBuy = viewModel::onBuy,
        onCancelListing = viewModel::onCancelListing,
        onTradeClick = { navController.navigate(ShiningRoutes.marketTrade(it)) },
    )
    if (viewModel.sell.value.visible) {
        SellSheet(form = viewModel.sell.value, viewModel = viewModel)
    }
}

@Composable
fun MarketScreen(
    state: MarketScreenState,
    onRefresh: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onLogin: () -> Unit,
    onDaySelected: (String?) -> Unit,
    onMealTypeSelected: (Int?) -> Unit,
    onSelfSelected: (Int?) -> Unit,
    onSell: () -> Unit,
    onBuy: (ListingView) -> Unit,
    onCancelListing: (ListingView) -> Unit,
    onTradeClick: (Long) -> Unit,
) {
    var showInfo by remember { mutableStateOf(false) }
    var showVerified by remember { mutableStateOf(false) }
    var confirmBuy by remember { mutableStateOf<ListingView?>(null) }
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
    if (showVerified) VerifiedInfoDialog(onDismiss = { showVerified = false })
    confirmBuy?.let { listing ->
        BuyDialog(
            listing = listing,
            onConfirm = {
                confirmBuy = null
                onBuy(listing)
            },
            onDismiss = { confirmBuy = null },
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
        state.message?.let { message ->
            item(key = "message") {
                InlineMessage(data = message, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        if (state.guest) {
            item(key = "guest") { GuestCard(onLogin = onLogin, modifier = Modifier.padding(horizontal = 16.dp)) }
        } else if (!state.joined) {
            item(key = "join") {
                JoinMarketCard(
                    loading = state.joining,
                    error = state.joinError,
                    onJoin = onJoin,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        item(key = "sell") {
            ShiningButton(
                text = "غذات رو بفروش",
                icon = R.drawable.ic_add,
                onClick = onSell,
                style = ShiningButtonStyle.Brand,
                size = ShiningButtonSize.Large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
        if (state.activeTrades.isNotEmpty()) {
            item(key = "activeTitle") {
                SectionHeader(title = "معامله‌های در جریان", modifier = Modifier.padding(horizontal = 20.dp))
            }
            items(state.activeTrades, key = { "active-${it.id}" }) { trade ->
                TradeLine(trade = trade, onClick = { onTradeClick(trade.id) }, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        item(key = "listingsTitle") {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(title = "آگهی‌ها", modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(ShiningTheme.shapes.pill)
                        .clickable { showVerified = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VerifiedTick()
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(text = "یعنی چی؟", style = ShiningTheme.typography.caption, color = ShiningTheme.colors.inkSecondary)
                    Spacer(modifier = Modifier.size(4.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = "تیک تأیید یعنی چی؟",
                        tint = ShiningTheme.colors.inkTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        // Three dropdowns in one row: each says what it filters by, or what's chosen, and lists
        // only what's on the market (so no choice ends in an empty list).
        if (state.all.isNotEmpty()) {
            item(key = "filters") {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterDropdown(label = "روز", allLabel = "همه‌ی روزها", options = state.days, selected = state.selectedDay, onSelect = onDaySelected)
                    FilterDropdown(label = "وعده", allLabel = "همه‌ی وعده‌ها", options = state.mealTypes, selected = state.selectedMealType, onSelect = onMealTypeSelected)
                    FilterDropdown(label = "سلف", allLabel = "همه‌ی سلف‌ها", options = state.selves, selected = state.selectedSelf, onSelect = onSelfSelected)
                }
            }
        }
        when {
            state.loading && state.all.isEmpty() -> item(key = "loading") {
                LoadingState(modifier = Modifier.fillMaxWidth().padding(32.dp))
            }
            // The notice above already says why there's nothing.
            state.listings.isEmpty() && state.notice != null -> Unit
            state.listings.isEmpty() -> item(key = "empty") {
                ShiningCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    EmptyState(
                        emoji = R.drawable.emoji_plate,
                        title = if (state.all.isEmpty()) "فعلاً کسی چیزی نفروخته" else "با این فیلتر آگهی‌ای نیست",
                        subtitle = if (state.all.isEmpty()) "اولین نفر باش: غذایی که نمی‌خوری رو بذار." else "یه فیلتر دیگه رو انتخاب کن.",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            else -> items(state.listings, key = { "listing-${it.id}" }) { listing ->
                ListingCard(
                    listing = listing,
                    busy = state.busyId == listing.id,
                    onBuy = { confirmBuy = listing },
                    onCancel = { onCancelListing(listing) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
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
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ShiningTheme.colors
    Text(
        text = label,
        style = ShiningTheme.typography.label,
        color = if (selected) colors.onInk else colors.ink,
        maxLines = 1,
        modifier = modifier
            .clip(ShiningTheme.shapes.pill)
            .background(if (selected) colors.ink else colors.surface)
            .border(1.dp, if (selected) colors.ink else colors.border, ShiningTheme.shapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * One filter as a pill: "روز ▾" while nothing is chosen, "فردا ▾" (filled) once something is.
 * Tapping opens a short menu with "all" and each option; the chosen one has a check.
 */
@Composable
private fun <T> FilterDropdown(
    label: String,
    allLabel: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T?) -> Unit,
) {
    val colors = ShiningTheme.colors
    var open by remember { mutableStateOf(false) }
    val chosen = options.firstOrNull { it.first == selected }?.second
    val active = chosen != null
    Box {
        Row(
            modifier = Modifier
                .clip(ShiningTheme.shapes.pill)
                .background(if (active) colors.ink else colors.surface)
                .border(1.dp, if (active) colors.ink else colors.border, ShiningTheme.shapes.pill)
                .clickable { open = true }
                .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = chosen ?: label,
                style = ShiningTheme.typography.label,
                color = if (active) colors.onInk else colors.ink,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_down),
                contentDescription = null,
                tint = if (active) colors.onInk else colors.inkSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(colors.surface),
        ) {
            // The menu is its own window and loses the theme's right-to-left layout.
            ShiningTheme {
                (listOf(null to allLabel) + options).forEach { (value, text) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = text,
                                style = ShiningTheme.typography.body,
                                color = ShiningTheme.colors.ink,
                            )
                        },
                        trailingIcon = {
                            if (value == selected) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = "انتخاب‌شده",
                                    tint = ShiningTheme.colors.brand,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        onClick = {
                            open = false
                            onSelect(value)
                        },
                    )
                }
            }
        }
    }
}

/** The blue check next to a seller who joined with their Samad account. */
@Composable
fun VerifiedTick(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_check_rounded),
        contentDescription = "تأییدشده با سماد",
        tint = ShiningTheme.colors.brand,
        modifier = modifier.size(16.dp),
    )
}

/** One listing: the meal, when and where, the seller (verified or not, their record), the price and Buy. */
@Composable
private fun ListingCard(
    listing: ListingView,
    busy: Boolean,
    onBuy: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ShiningTheme.colors
    val meal = listing.meal
    ShiningCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(
                emoji = FoodEmoji.forFood(meal.foodName, MealType.getMealTypeById(meal.mealTypeId)),
                size = 44.dp,
                emojiSize = 24.dp,
            )
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = meal.foodName,
                    style = ShiningTheme.typography.subtitle,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${MarketMeals.dayLabel(meal.date)} · ${meal.mealName} · ${shortSelfName(meal.selfName)}",
                    style = ShiningTheme.typography.caption,
                    color = colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (listing.verified) {
                        VerifiedTick(modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                    }
                    Text(
                        text = when {
                            listing.mine -> "آگهی خودت"
                            listing.verified -> "تأییدشده، ${listing.reputation.label(asSeller = true)}"
                            else -> "مهمان، ${listing.reputation.label(asSeller = true)}"
                        },
                        style = ShiningTheme.typography.caption,
                        color = if (listing.reputation.problems > 0 && !listing.mine) colors.warning else colors.inkTertiary,
                        maxLines = 1,
                    )
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = listing.price.toman(), style = ShiningTheme.typography.bodyStrong, color = colors.ink)
                if (listing.mine) {
                    ShiningButton(
                        text = "پس بگیر",
                        onClick = onCancel,
                        loading = busy,
                        style = ShiningButtonStyle.Danger,
                        size = ShiningButtonSize.Small,
                    )
                } else {
                    ShiningButton(text = "بخر", onClick = onBuy, loading = busy, size = ShiningButtonSize.Small)
                }
            }
        }
    }
}

/** A guest's card: what they can do, and logging in with Samad for the tick and one-tap selling. */
@Composable
private fun GuestCard(onLogin: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ShiningTheme.colors
    ShiningCard(modifier = modifier.fillMaxWidth(), background = colors.surfaceMuted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(emoji = R.drawable.emoji_profile, size = 40.dp, emojiSize = 22.dp)
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "مهمانی", style = ShiningTheme.typography.subtitle, color = colors.ink)
                Text(
                    text = "می‌تونی بخری و با نوشتن کد بفروشی. با حساب سماد وارد شو تا آگهی‌هات تیک تأیید بگیرن و رزروهات رو مستقیم انتخاب کنی.",
                    style = ShiningTheme.typography.caption,
                    color = colors.inkSecondary,
                )
            }
        }
        Spacer(modifier = Modifier.size(10.dp))
        ShiningButton(
            text = "ورود با حساب سماد",
            icon = R.drawable.ic_login,
            onClick = onLogin,
            style = ShiningButtonStyle.Secondary,
            size = ShiningButtonSize.Small,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun VerifiedInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VerifiedTick(modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text("تیک تأیید یعنی چی؟", style = ShiningTheme.typography.title)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "این فروشنده با حساب سمادش وارد بازار شده، پس شماره‌ی دانشجویی‌ش پیش ماست. اگه معامله‌ای خراب شد، از «مشکل دارم» خبر بده یا به پشتیبانی شاینینگ ایمیل بزن؛ ما شماره‌ی دانشجویی فروشنده رو پیدا می‌کنیم و پیگیری می‌کنیم.",
                    style = ShiningTheme.typography.body,
                )
                Text(
                    "آگهی‌های بدون تیک مال مهمان‌هاست، یعنی کسی که با سماد وارد نشده. شماره‌ی کارتشون رو داری، ولی پیگیری سخت‌تره؛ مثل خریدن از یه اکانت بی‌نام‌ونشون توی گروه تلگرام.",
                    style = ShiningTheme.typography.body,
                )
            }
        },
        confirmButton = {
            ShiningButton(text = "فهمیدم", onClick = onDismiss, size = ShiningButtonSize.Small)
        },
    )
}

@Composable
private fun BuyDialog(listing: ListingView, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${listing.meal.foodName} به ${listing.price.toman()}؟", style = ShiningTheme.typography.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${MarketMeals.dayLabel(listing.meal.date)} · ${listing.meal.mealName} · ${shortSelfName(listing.meal.selfName)}. " +
                        "بعد از «بخر» ۵ دقیقه وقت داری پول رو کارت‌به‌کارت کنی؛ کد وقتی فروشنده تأیید کرد میاد.",
                    style = ShiningTheme.typography.body,
                )
                if (!listing.verified) {
                    StatusPill(text = "فروشنده مهمانه و با سماد وارد نشده", tone = PillTone.Warning)
                }
            }
        },
        confirmButton = { ShiningButton(text = "بخر", onClick = onConfirm, size = ShiningButtonSize.Small) },
        dismissButton = {
            ShiningButton(text = "نه", onClick = onDismiss, style = ShiningButtonStyle.Ghost, size = ShiningButtonSize.Small)
        },
    )
}
