package app.shining.market.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.shining.R
import app.shining.domain.ui.component.ErrorState
import app.shining.domain.ui.component.LoadingState
import app.shining.domain.ui.component.MessageBanner
import app.shining.domain.ui.component.ScreenHeader
import app.shining.domain.ui.component.ShiningButton
import app.shining.domain.ui.component.ShiningButtonSize
import app.shining.domain.ui.component.ShiningButtonStyle
import app.shining.domain.ui.component.ShiningCard
import app.shining.domain.ui.component.ShiningDivider
import app.shining.domain.ui.component.ShiningTextField
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.ShiningRoutes
import app.shining.domain.util.toLocalNumber
import app.shining.market.api.TradeView
import app.shining.market.domain.MarketMeals
import app.shining.market.domain.label
import app.shining.market.domain.toman
import kotlinx.coroutines.launch

@Composable
fun TradeComposable(viewModel: TradeViewModel, navController: NavController) {
    LaunchedEffect(Unit) { viewModel.onLaunch() }
    val scope = rememberCoroutineScope()
    TradeScreen(
        state = viewModel.state.value,
        now = viewModel.now.longValue,
        note = viewModel.note.value,
        problem = viewModel.problem.value,
        showProblem = viewModel.showProblem.value,
        onNoteChange = { viewModel.note.value = it.take(300) },
        onProblemChange = { viewModel.problem.value = it.take(300) },
        onShowProblem = { viewModel.showProblem.value = !viewModel.showProblem.value },
        onBack = {
            if (!navController.popBackStack()) navController.navigate(ShiningRoutes.MarketScreen.route)
        },
        onRetry = { scope.launch { viewModel.refresh() } },
        onPaid = viewModel::onPaid,
        onCancel = viewModel::onCancel,
        onConfirm = viewModel::onConfirm,
        onRate = viewModel::onRate,
        onSendProblem = viewModel::onProblem,
        onOpenBoard = { navController.navigate(ShiningRoutes.marketMeal(it)) },
    )
}

@Composable
fun TradeScreen(
    state: TradeState,
    now: Long,
    note: String,
    problem: String,
    showProblem: Boolean,
    onNoteChange: (String) -> Unit,
    onProblemChange: (String) -> Unit,
    onShowProblem: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPaid: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onRate: (Boolean) -> Unit,
    onSendProblem: () -> Unit,
    onOpenBoard: (String) -> Unit,
) {
    val colors = ShiningTheme.colors
    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val trade = state.trade
            ScreenHeader(
                title = when {
                    trade == null -> "معامله"
                    trade.isBuyer -> "خرید ${trade.meal.foodName}"
                    else -> "فروش ${trade.meal.foodName}"
                },
                subtitle = trade?.let { "${it.price.toman()} · ${MarketMeals.dayLabel(it.meal.date)}" },
                onBackClick = onBack,
            )
            if (trade == null) {
                if (state.loading) {
                    LoadingState(modifier = Modifier.fillMaxWidth().padding(32.dp))
                } else {
                    ErrorState(message = state.error ?: "این معامله پیدا نشد.", onRetry = onRetry, modifier = Modifier.fillMaxWidth())
                }
                // No early return here: leaving a Column's content early breaks Compose 1.5's
                // bookkeeping when the trade then loads, and the app crashes.
            } else {
                TradeStatusBar(trade = trade, modifier = Modifier.padding(horizontal = 20.dp))
                ShiningCard(modifier = Modifier.padding(horizontal = 16.dp), onClick = { onOpenBoard(trade.key) }) {
                    MarketMealHeader(meal = trade.meal)
                    Spacer(modifier = Modifier.size(10.dp))
                    ShiningDivider()
                    Spacer(modifier = Modifier.size(6.dp))
                    DetailLine("قیمت", trade.price.toman())
                    DetailLine(
                        if (trade.isBuyer) "فروشنده" else "خریدار",
                        trade.counterpart.label(asSeller = trade.isBuyer),
                    )
                }
                StepCard(
                    trade = trade,
                    state = state,
                    now = now,
                    note = note,
                    onNoteChange = onNoteChange,
                    onPaid = onPaid,
                    onCancel = onCancel,
                    onConfirm = onConfirm,
                    onRate = onRate,
                )
                if (trade.status != TradeView.CANCELLED) {
                    ProblemCard(
                        trade = trade,
                        open = showProblem,
                        text = problem,
                        busy = state.busy == "problem",
                        onToggle = onShowProblem,
                        onTextChange = onProblemChange,
                        onSend = onSendProblem,
                    )
                }
            }
        }
        MessageBanner(visible = state.message != null, data = state.message, modifier = Modifier.align(Alignment.TopCenter))
    }
}

/** What this side has to do now. */
@Composable
private fun StepCard(
    trade: TradeView,
    state: TradeState,
    now: Long,
    note: String,
    onNoteChange: (String) -> Unit,
    onPaid: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onRate: (Boolean) -> Unit,
) {
    val colors = ShiningTheme.colors
    val payBy = MarketMeals.parseInstant(trade.payBy)
    val confirmBy = MarketMeals.parseInstant(trade.confirmBy)
    ShiningCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        when (trade.status) {
            TradeView.AWAITING_PAYMENT -> if (trade.isBuyer) {
                Timer(label = if (trade.origin == "sell_now") "فروشنده پیشنهادت رو قبول کرد. وقت پرداخت:" else "وقت پرداخت:", until = payBy, now = now)
                Spacer(modifier = Modifier.size(12.dp))
                Text(text = "${trade.price.toman()} رو به این کارت بزن:", style = ShiningTheme.typography.bodyStrong, color = colors.ink)
                Spacer(modifier = Modifier.size(8.dp))
                trade.card?.let { CopyBox(text = it, copiedMessage = "شماره‌ی کارت کپی شد") }
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "توی اپ بانکت اسم صاحب کارت رو می‌بینی. فقط همین مبلغ رو بزن، بعد «پرداخت کردم» رو بزن.",
                    style = ShiningTheme.typography.caption,
                    color = colors.inkSecondary,
                )
                Spacer(modifier = Modifier.size(12.dp))
                ShiningTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    label = "یادداشت برای فروشنده (اختیاری)",
                    placeholder = "مثلاً شماره‌ی پیگیری",
                )
                Spacer(modifier = Modifier.size(12.dp))
                ShiningButton(text = "پرداخت کردم", onClick = onPaid, loading = state.busy == "paid", modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.size(8.dp))
                ShiningButton(
                    text = "منصرف شدم",
                    onClick = onCancel,
                    style = ShiningButtonStyle.Ghost,
                    loading = state.busy == "cancel",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Timer(label = "منتظر پرداخت خریدار:", until = payBy, now = now)
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "خریدار شماره‌ی کارتت رو می‌بینه و ${trade.price.toman()} رو کارت‌به‌کارت می‌کنه. وقتی بگه پول رو زده، خبرت می‌کنیم.",
                    style = ShiningTheme.typography.body,
                    color = colors.inkSecondary,
                )
            }

            TradeView.PAID -> if (trade.isBuyer) {
                Timer(label = "منتظر تأیید فروشنده:", until = confirmBy, now = now)
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "فروشنده پیامک بانکش رو چک می‌کنه. وقتی تأیید کنه، کد فراموشی همین‌جا میاد.",
                    style = ShiningTheme.typography.body,
                    color = colors.inkSecondary,
                )
            } else {
                SellerConfirm(trade, state, confirmBy, now, onConfirm)
            }

            TradeView.COMPLETED -> if (trade.isBuyer) {
                Text(text = "کد فراموشی:", style = ShiningTheme.typography.bodyStrong, color = colors.ink)
                Spacer(modifier = Modifier.size(8.dp))
                val code = trade.code
                if (code != null) {
                    CopyBox(text = code.toLocalNumber(), copyText = code, copiedMessage = "کد کپی شد", big = true)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "توی ${trade.meal.selfName}، کد رو به جای کارت دانشجویی بزن. روی گوشیت هم می‌مونه.",
                        style = ShiningTheme.typography.caption,
                        color = colors.inkSecondary,
                    )
                } else {
                    Text(text = "کد روی این گوشی نیست.", style = ShiningTheme.typography.body, color = colors.inkSecondary)
                }
                Spacer(modifier = Modifier.size(16.dp))
                when (trade.buyerOk) {
                    null -> {
                        Text(text = "کد کار کرد؟", style = ShiningTheme.typography.bodyStrong, color = colors.ink)
                        Spacer(modifier = Modifier.size(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShiningButton(text = "آره، غذا رو گرفتم", onClick = { onRate(true) }, loading = state.busy == "ok", modifier = Modifier.weight(1f))
                            ShiningButton(
                                text = "نه",
                                onClick = { onRate(false) },
                                style = ShiningButtonStyle.Danger,
                                loading = state.busy == "bad",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    true -> Text(text = "نوش جان!", style = ShiningTheme.typography.bodyStrong, color = colors.positive)
                    false -> Text(text = "گفتی کد کار نکرد. بررسیش می‌کنیم.", style = ShiningTheme.typography.bodyStrong, color = colors.negative)
                }
            } else {
                Text(text = "فروختی! ${trade.price.toman()}", style = ShiningTheme.typography.title, color = colors.positive)
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = when (trade.buyerOk) {
                        true -> "خریدار گفت غذا رو گرفت."
                        false -> "خریدار گفته کد کار نکرد. اگه اشتباهی شده، از «مشکل دارم» توضیح بده."
                        null -> "کد برای خریدار رفت."
                    },
                    style = ShiningTheme.typography.body,
                    color = colors.inkSecondary,
                )
            }

            TradeView.EXPIRED -> Text(
                text = if (trade.isBuyer) {
                    "وقت پرداخت تموم شد و معامله لغو شد. اگه پول رو زده بودی، از «مشکل دارم» خبر بده."
                } else {
                    "خریدار به‌موقع پول نزد. آگهیت دوباره روی بورده."
                },
                style = ShiningTheme.typography.body,
                color = colors.inkSecondary,
            )

            TradeView.CANCELLED -> Text(
                text = if (trade.isBuyer) "این معامله رو لغو کردی." else "خریدار منصرف شد. آگهیت دوباره روی بورده.",
                style = ShiningTheme.typography.body,
                color = colors.inkSecondary,
            )

            TradeView.DISPUTED -> {
                Text(
                    text = "این معامله در حال بررسیه. تیم شاینینگ نگاهش می‌کنه و خبرت می‌کنه.",
                    style = ShiningTheme.typography.bodyStrong,
                    color = colors.warning,
                )
                trade.problem?.let {
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(text = "گزارش: $it", style = ShiningTheme.typography.caption, color = colors.inkSecondary)
                }
                if (!trade.isBuyer && trade.paidAt != null) {
                    Spacer(modifier = Modifier.size(12.dp))
                    SellerConfirm(trade, state, null, now, onConfirm)
                }
            }
        }
    }
}

/** The seller checks their bank SMS and sends the code. */
@Composable
private fun SellerConfirm(trade: TradeView, state: TradeState, confirmBy: Long?, now: Long, onConfirm: () -> Unit) {
    val colors = ShiningTheme.colors
    if (confirmBy != null) {
        Timer(label = "خریدار می‌گه پول رو زده. وقت تأیید:", until = confirmBy, now = now)
        Spacer(modifier = Modifier.size(8.dp))
    }
    Text(
        text = "پیامک بانکت رو چک کن. اگه ${trade.price.toman()} رسیده، دکمه رو بزن تا کد فراموشی از سماد گرفته بشه و برای خریدار بره.",
        style = ShiningTheme.typography.body,
        color = colors.inkSecondary,
    )
    trade.note?.let {
        Spacer(modifier = Modifier.size(8.dp))
        Text(text = "یادداشت خریدار: $it", style = ShiningTheme.typography.bodyStrong, color = colors.ink)
    }
    Spacer(modifier = Modifier.size(12.dp))
    ShiningButton(text = "پول رسید، کد رو بفرست", onClick = onConfirm, loading = state.busy == "confirm", modifier = Modifier.fillMaxWidth())
    Text(
        text = "اگه پول نرسیده، «مشکل دارم» رو بزن.",
        style = ShiningTheme.typography.caption,
        color = colors.inkSecondary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun Timer(label: String, until: Long?, now: Long) {
    val colors = ShiningTheme.colors
    val left = until?.let { it - now } ?: 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = ShiningTheme.typography.bodyStrong, color = colors.ink, modifier = Modifier.weight(1f))
        Text(
            text = countdown(until, now),
            style = ShiningTheme.typography.display,
            color = if (left < 60_000) colors.negative else colors.ink,
        )
    }
}

/** A card number or code, tap to copy. */
@Composable
private fun CopyBox(text: String, copiedMessage: String, copyText: String = text.filter { it.isDigit() }, big: Boolean = false) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val colors = ShiningTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShiningTheme.shapes.field)
            .background(if (big) colors.brand else colors.surfaceMuted)
            .clickable {
                clipboard.setText(AnnotatedString(copyText))
                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
            }
            .padding(horizontal = 16.dp, vertical = if (big) 16.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            // Left to right always: a card number's groups of four would otherwise come out reversed.
            style = (if (big) ShiningTheme.typography.display else ShiningTheme.typography.title)
                .copy(textDirection = TextDirection.Ltr),
            color = if (big) colors.onBrand else colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.ic_content_copy),
            contentDescription = "کپی",
            tint = if (big) colors.onBrand else colors.inkSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** "I have a problem": a note to the admins, with the trade's times as evidence. */
@Composable
private fun ProblemCard(
    trade: TradeView,
    open: Boolean,
    text: String,
    busy: Boolean,
    onToggle: () -> Unit,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ShiningButton(
            text = if (open) "بی‌خیال" else "مشکل دارم",
            onClick = onToggle,
            style = ShiningButtonStyle.Ghost,
            size = ShiningButtonSize.Small,
        )
        if (open) {
            ShiningCard {
                ShiningTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = "چی شده؟",
                    placeholder = if (trade.isBuyer) "مثلاً پول زدم ولی کد نیومد" else "مثلاً پول به کارتم نرسید",
                )
                Spacer(modifier = Modifier.size(10.dp))
                ShiningButton(text = "بفرست", onClick = onSend, loading = busy, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
