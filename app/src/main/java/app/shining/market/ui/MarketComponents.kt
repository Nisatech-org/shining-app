package app.shining.market.ui

import androidx.annotation.DrawableRes
import app.shining.domain.ui.theme.SheetWindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.shining.domain.ui.component.Emoji
import app.shining.domain.ui.component.ShiningButtonSize
import app.shining.domain.ui.component.ShiningButtonStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.shining.R
import app.shining.domain.ui.component.EmojiTile
import app.shining.domain.ui.component.PillTone
import app.shining.domain.ui.component.ShiningButton
import app.shining.domain.ui.component.ShiningCard
import app.shining.domain.ui.component.ShiningTextField
import app.shining.domain.ui.component.StatusPill
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.toGroupedLocalNumber
import app.shining.domain.util.toLocalNumber
import app.shining.market.api.MealInfo
import app.shining.market.api.OrderView
import app.shining.market.api.TradeView
import app.shining.market.domain.MarketMeals
import app.shining.market.domain.label
import app.shining.market.domain.toman
import app.shining.order.domain.model.FoodEmoji
import app.shining.order.domain.model.MealType

/**
 * Joining the market: one line on what it is, the join button, and "how does it work?", which
 * opens [MarketInfoSheet] with the steps and what the server keeps. Nothing is sent before the
 * join button is tapped.
 */
@Composable
fun JoinMarketCard(
    loading: Boolean,
    error: String?,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ShiningTheme.colors
    var showInfo by remember { mutableStateOf(false) }
    ShiningCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(emoji = R.drawable.emoji_money_bag, size = 48.dp, emojiSize = 28.dp)
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "بازار شاینینگ", style = ShiningTheme.typography.title, color = colors.ink)
                Text(
                    text = "غذایی که نمی‌خوری رو بفروش، غذای بقیه رو بخر",
                    style = ShiningTheme.typography.caption,
                    color = colors.inkSecondary,
                )
            }
        }
        Spacer(modifier = Modifier.size(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MarketPerk(emoji = R.drawable.emoji_money_wings, text = "بدون کارمزد", modifier = Modifier.weight(1f))
            MarketPerk(emoji = R.drawable.emoji_clock, text = "کد توی ۵ دقیقه", modifier = Modifier.weight(1f))
            MarketPerk(emoji = R.drawable.emoji_profile, text = "بی‌نام", modifier = Modifier.weight(1f))
        }
        error?.let {
            Spacer(modifier = Modifier.size(10.dp))
            Text(text = it, style = ShiningTheme.typography.label, color = colors.negative)
        }
        Spacer(modifier = Modifier.size(14.dp))
        ShiningButton(
            text = "ورود به بازار",
            onClick = onJoin,
            style = ShiningButtonStyle.Brand,
            loading = loading,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.size(6.dp))
        ShiningButton(
            text = "چطوری کار می‌کنه؟",
            icon = R.drawable.ic_info,
            onClick = { showInfo = true },
            style = ShiningButtonStyle.Ghost,
            size = ShiningButtonSize.Small,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (showInfo) {
        MarketInfoSheet(
            joined = false,
            onJoin = {
                showInfo = false
                onJoin()
            },
            onDismiss = { showInfo = false },
        )
    }
}

/** A small tile with an emoji and two or three words, for what the market promises. */
@Composable
private fun MarketPerk(@DrawableRes emoji: Int, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(ShiningTheme.shapes.tile)
            .background(ShiningTheme.colors.surfaceMuted)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Emoji(emoji = emoji, size = 24.dp)
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = text,
            style = ShiningTheme.typography.caption,
            color = ShiningTheme.colors.ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * "How does the market work?": the four steps of a trade, then what the server keeps. Opened
 * from the join card, and from the Market tab's header once joined, where it also holds
 * "leave the market".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketInfoSheet(
    joined: Boolean,
    onDismiss: () -> Unit,
    onJoin: () -> Unit = {},
    onLeave: () -> Unit = {},
) {
    var confirmLeave by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ShiningTheme.colors.surface,
        shape = ShiningTheme.shapes.sheet,
        windowInsets = SheetWindowInsets,
    ) {
        // The sheet is its own window and loses the theme's right-to-left layout.
        ShiningTheme {
            val colors = ShiningTheme.colors
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            ) {
                Text(text = "بازار چطوری کار می‌کنه؟", style = ShiningTheme.typography.headline, color = colors.ink)
                Spacer(modifier = Modifier.size(16.dp))
                InfoStep(1, R.drawable.emoji_plate, "غذات رو بذار", "کد فراموشی غذایی که نمی‌خوری رو با قیمت خودت بنویس، یا از رزروهات انتخابش کن تا خودش پر بشه.")
                InfoStep(2, R.drawable.emoji_cart, "یا غذا بخر", "همه‌ی آگهی‌ها توی یه لیسته. هر کدوم رو خواستی بزن «بخر». تیک آبی یعنی فروشنده با حساب سمادش وارد شده.")
                InfoStep(3, R.drawable.emoji_money_wings, "۵ دقیقه برای کارت‌به‌کارت", "خریدار کارت فروشنده رو می‌بینه و پول رو مستقیم می‌زنه. شاینینگ نه پولی نگه می‌داره، نه کارمزد می‌گیره.")
                InfoStep(4, R.drawable.emoji_check, "کد میاد توی اپ", "فروشنده پیامک بانکش رو چک می‌کنه و تأیید می‌کنه؛ کدی که با آگهی نوشته برای خریدار می‌ره.")
                Spacer(modifier = Modifier.size(8.dp))
                ShiningCard(background = colors.surfaceMuted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Emoji(emoji = R.drawable.emoji_info, size = 22.dp)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = "حریم خصوصیت", style = ShiningTheme.typography.subtitle, color = colors.ink)
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    PrivacyLine("برای ورود، یه بار ثابت می‌کنیم دانشجویی: توکن سمادت یه بار به سرور شاینینگ می‌ره، سرور شماره‌ی دانشجویی رو از سماد می‌خونه و توکن رو نگه نمی‌داره.")
                    PrivacyLine("سرور فقط اینا رو نگه می‌داره: یه شناسه‌ی رمزشده به جای شماره‌ی دانشجویی (برای مهمان‌ها یه شناسه‌ی تصادفی)، آگهی‌ها و معامله‌هات، و اگه بفروشی، شماره‌ی کارتت و کد آگهی (هر دو رمزشده).")
                    PrivacyLine("اسمت به کسی نشون داده نمی‌شه؛ فقط خریدار موقع کارت‌به‌کارت اسم صاحب کارت رو توی اپ بانکش می‌بینه.")
                    PrivacyLine("کد فراموشی همین که به گوشی خریدار برسه از سرور پاک می‌شه. هر وقت بخوای از بازار بیرون میای و حساب و کارتت پاک می‌شه.")
                }
                Spacer(modifier = Modifier.size(16.dp))
                if (joined) {
                    ShiningButton(
                        text = "خروج از بازار",
                        onClick = { confirmLeave = true },
                        style = ShiningButtonStyle.Danger,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    ShiningButton(text = "ورود به بازار", onClick = onJoin, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("از بازار بیرون میای؟", style = ShiningTheme.typography.title) },
            text = {
                Text(
                    "حساب بازار، کارتت و آگهی‌هات پاک می‌شه. معامله‌های قبلی فقط برای طرف دیگه، بدون تو، می‌مونه.",
                    style = ShiningTheme.typography.body,
                )
            },
            confirmButton = {
                ShiningButton(
                    text = "آره، بیرون بیا",
                    onClick = {
                        confirmLeave = false
                        onLeave()
                    },
                    style = ShiningButtonStyle.Danger,
                    size = ShiningButtonSize.Small,
                )
            },
            dismissButton = {
                ShiningButton(text = "نه", onClick = { confirmLeave = false }, style = ShiningButtonStyle.Ghost, size = ShiningButtonSize.Small)
            },
        )
    }
}

@Composable
private fun InfoStep(number: Int, @DrawableRes emoji: Int, title: String, body: String) {
    val colors = ShiningTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.Top) {
        Box {
            EmojiTile(emoji = emoji, size = 48.dp, emojiSize = 26.dp)
            Text(
                text = number.toString().toLocalNumber(),
                style = ShiningTheme.typography.caption,
                color = colors.onBrand,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .clip(ShiningTheme.shapes.pill)
                    .background(colors.brand),
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = ShiningTheme.typography.subtitle, color = colors.ink)
            Text(text = body, style = ShiningTheme.typography.body, color = colors.inkSecondary)
        }
    }
}

@Composable
private fun PrivacyLine(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(text = "•  ", style = ShiningTheme.typography.caption, color = ShiningTheme.colors.inkSecondary)
        Text(text = text, style = ShiningTheme.typography.caption, color = ShiningTheme.colors.inkSecondary)
    }
}

/** Card numbers stay left to right inside Persian text, or their groups of four come out reversed. */
fun ltr(text: String): String = "\u2066$text\u2069"

/** A price in toman. "۱۲۰" is read as 120,000 (see parsePrice). */
@Composable
fun PriceField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    ShiningTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() || it == '٬' || it == ',' }.take(12)) },
        label = label,
        placeholder = "مثلاً ۱۲۰٬۰۰۰ تومن",
        ltrInput = true,
        error = error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** The meal at the top of a board or trade: its emoji, food, self, day and meal. */
@Composable
fun MarketMealHeader(meal: MealInfo, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val colors = ShiningTheme.colors
    val mealType = MealType.getMealTypeById(meal.mealTypeId)
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        EmojiTile(emoji = FoodEmoji.forFood(meal.foodName, mealType), size = 48.dp, emojiSize = 28.dp)
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = meal.foodName, style = ShiningTheme.typography.title, color = colors.ink)
            Text(
                text = "${MarketMeals.dayLabel(meal.date)} · ${meal.mealName} · ${meal.selfName}",
                style = ShiningTheme.typography.caption,
                color = colors.inkSecondary,
            )
        }
        trailing()
    }
}

/** One listing or offer on a board: price, the other person's record, and "yours". */
@Composable
fun OrderLine(
    order: OrderView,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    val colors = ShiningTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShiningTheme.shapes.tile)
            .background(if (order.mine) colors.brandSoft else colors.surfaceMuted)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = order.price.toman(), style = ShiningTheme.typography.subtitle, color = colors.ink)
            Text(
                text = if (order.mine) "مال خودت" else order.reputation.label(asSeller = order.side == "ask"),
                style = ShiningTheme.typography.caption,
                color = if (order.reputation.problems > 0 && !order.mine) colors.warning else colors.inkSecondary,
            )
        }
        if (order.mine && onCancel != null) {
            ShiningButton(
                text = "پس بگیر",
                onClick = onCancel,
                style = ShiningButtonStyle.Danger,
                size = ShiningButtonSize.Small,
            )
        }
    }
}

/**
 * The trade's progress, like a ride app's progress bar: offer → accepted → 5 minutes
 * to pay → I paid → code.
 */
@Composable
fun TradeStatusBar(trade: TradeView, modifier: Modifier = Modifier) {
    val colors = ShiningTheme.colors
    val steps = listOf("پیشنهاد", "تأیید", "پرداخت", "پرداخت کردم", "کد")
    val current = when (trade.status) {
        TradeView.AWAITING_PAYMENT -> 2
        TradeView.PAID -> 3
        TradeView.COMPLETED -> 5
        TradeView.DISPUTED -> if (trade.paidAt != null) 3 else 2
        else -> 2
    }
    val failed = trade.status in listOf(TradeView.EXPIRED, TradeView.CANCELLED, TradeView.DISPUTED)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            steps.forEachIndexed { index, _ ->
                val color = when {
                    index < current -> colors.brand
                    index == current && failed -> if (trade.status == TradeView.DISPUTED) colors.warning else colors.negative
                    index == current -> colors.ink
                    else -> colors.surfaceMuted
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(ShiningTheme.shapes.pill)
                        .background(color),
                )
            }
        }
        Spacer(modifier = Modifier.size(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            steps.forEachIndexed { index, step ->
                Text(
                    text = step,
                    style = ShiningTheme.typography.caption,
                    color = if (index <= current) colors.ink else colors.inkTertiary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A trade in a list: the meal, the price, and where it stands. */
@Composable
fun TradeLine(trade: TradeView, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ShiningTheme.colors
    ShiningCard(modifier = modifier.fillMaxWidth(), onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(
                emoji = FoodEmoji.forFood(trade.meal.foodName, MealType.getMealTypeById(trade.meal.mealTypeId)),
                size = 36.dp,
                emojiSize = 20.dp,
            )
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${if (trade.isBuyer) "خرید" else "فروش"} ${trade.meal.foodName}",
                    style = ShiningTheme.typography.bodyStrong,
                    color = colors.ink,
                    maxLines = 1,
                )
                Text(
                    text = "${trade.price.toman()} · ${MarketMeals.dayLabel(trade.meal.date)} · ${trade.meal.selfName}",
                    style = ShiningTheme.typography.caption,
                    color = colors.inkSecondary,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
            StatusPill(text = statusText(trade), tone = statusTone(trade))
        }
    }
}

fun statusText(trade: TradeView): String = when (trade.status) {
    TradeView.AWAITING_PAYMENT -> if (trade.isBuyer) "پرداخت کن" else "منتظر پرداخت"
    TradeView.PAID -> if (trade.isBuyer) "منتظر کد" else "تأیید کن"
    TradeView.COMPLETED -> "تموم شد"
    TradeView.EXPIRED -> "وقتش تموم شد"
    TradeView.CANCELLED -> "لغو شد"
    TradeView.DISPUTED -> "در حال بررسی"
    else -> trade.status
}

fun statusTone(trade: TradeView): PillTone = when (trade.status) {
    TradeView.AWAITING_PAYMENT, TradeView.PAID -> PillTone.Ink
    TradeView.COMPLETED -> PillTone.Positive
    TradeView.DISPUTED -> PillTone.Warning
    else -> PillTone.Neutral
}

/** "۴:۳۲" for the time left, "۰:۰۰" once it's over. */
fun countdown(untilMillis: Long?, now: Long): String {
    if (untilMillis == null) return ""
    val seconds = ((untilMillis - now) / 1000).coerceAtLeast(0)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}".toLocalNumber()
}

/** A label and a value on one line, for a trade's details. */
@Composable
fun DetailLine(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = ShiningTheme.typography.body, color = ShiningTheme.colors.inkSecondary, modifier = Modifier.weight(1f))
        Text(text = value, style = ShiningTheme.typography.bodyStrong, color = ShiningTheme.colors.ink)
    }
}

/** Samad's reserved price, in toman like the market's prices (Samad's own numbers are in rial). */
fun MealInfo.reservedPriceText(): String? = reservedPrice.takeIf { it > 0 }?.let { (it / 10).toman() }
