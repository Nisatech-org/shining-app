package app.shining.screenshots

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.ShiningRoutes
import app.shining.home.ui.component.BottomNavigationBarContent
import app.shining.market.api.ListingView
import app.shining.market.api.MealInfo
import app.shining.market.api.Reputation
import app.shining.market.api.TradeView
import app.shining.market.domain.MarketMeals
import app.shining.market.ui.MarketScreen
import app.shining.market.ui.MarketScreenState
import app.shining.market.ui.MarketViewModel
import app.shining.market.ui.SellFormState
import app.shining.market.ui.SellSheet
import app.shining.market.ui.TradeScreen
import app.shining.market.ui.TradeState
import kotlinx.coroutines.delay
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * One market trade, recorded from each phone on the same timeline: [recordSeller] lists a meal,
 * [recordBuyer] buys it, pays and gets the forget code. Each writes a screen recording to
 * /sdcard/Download (market-seller.mp4, market-buyer.mp4) that starts with a few black seconds,
 * which make_market_flow_video.py uses to line the two up side by side.
 *
 * The screens run on the real clock in a plain activity, and the script taps and types like a
 * person: it finds buttons through accessibility (sheets and dialogs included) and injects real
 * touches and key presses, so ripples, sheets and the keyboard show as on a phone.
 *
 * Nothing here reaches Samad or the Shining server: the screens get sample data, and the steps
 * that would call a server (picking a reservation, "put it up") are played by changing the sample
 * data instead of tapping. Every button that is tapped only runs this test's own code.
 */
@RunWith(AndroidJUnit4::class)
class MarketFlowTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).apply {
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
    }

    /**
     * Copy and paste go through this instead of Android's clipboard, whose preview pops up over the
     * screen (and its share button catches taps) every time something is copied.
     */
    private val clipboard = object : ClipboardManager {
        private var text: AnnotatedString? = null
        override fun setText(annotatedString: AnnotatedString) {
            text = annotatedString
        }
        override fun getText(): AnnotatedString? = text
    }

    private var blank by mutableStateOf(true)
    private var note by mutableStateOf("")
    private var startedAt = 0L
    private var recording: ParcelFileDescriptor? = null

    @Test
    fun recordSeller() {
        val sell = GlobalContext.get().get<MarketViewModel>()
        var listed by mutableStateOf(false)
        var trade by mutableStateOf<TradeView?>(null)
        record("market-seller") {
            val current = trade
            if (current != null) {
                Trade(current.copy(role = "seller"), onConfirm = { trade = DONE })
            } else {
                Market(
                    state = if (listed) SELLER_AFTER else BEFORE,
                    onSell = { sell.sell.value = SELL_START },
                )
                val form = sell.sell.value
                if (form.visible) SellSheet(form = form, viewModel = sell)
            }
        }
        try {
            // The script, in seconds from the start; the buyer's script runs on the same clock.
            at(1.2) { tap("غذات رو بفروش") }
            // Picking the reservation reads its code from Samad: played with sample data.
            at(3.4) {
                ui {
                    sell.sell.value = sell.sell.value.copy(
                        pickedReserveId = 11,
                        meal = SOLD,
                        day = SOLD.date,
                        mealTypeId = SOLD.mealTypeId,
                        selfId = SOLD.selfId,
                        loadingCodeFor = 11,
                    )
                }
            }
            at(4.1) { ui { sell.sell.value = sell.sell.value.copy(loadingCodeFor = null, code = "48213") } }
            at(4.8) { tap("قیمت (تومن)", below = true) }
            type(at = 5.4, every = 0.3, text = "50000")
            at(7.0) { key(KeyEvent.KEYCODE_BACK) }
            // "Put it up" sends it to the server: played with sample data.
            at(7.4) { ui { sell.sell.value = sell.sell.value.copy(busy = true) } }
            at(7.9) {
                ui {
                    sell.sell.value = sell.sell.value.copy(visible = false, busy = false)
                    listed = true
                }
            }
            at(10.4) { ui { trade = TRADE } }
            at(19.8) { ui { trade = PAID } }
            at(22.4) { tap("پول رسید، کد رو بفرست") }
            at(27.0) {}
        } finally {
            stop()
        }
    }

    @Test
    fun recordBuyer() {
        var listed by mutableStateOf(false)
        var trade by mutableStateOf<TradeView?>(null)
        var inBank by mutableStateOf(false)
        var bankStep by mutableStateOf(BankStep.Form)
        record("market-buyer") {
            AppSwitch(
                otherApp = inBank,
                shining = {
                    val current = trade
                    if (current != null) {
                        Trade(
                            current,
                            onPaid = { trade = PAID },
                            onRate = { ok -> trade = trade?.copy(buyerOk = ok) },
                        )
                    } else {
                        Market(
                            state = if (listed) BUYER_AFTER else BEFORE,
                            onBuy = { trade = TRADE },
                        )
                    }
                },
                other = { MockBank(step = bankStep, onPay = { bankStep = BankStep.Paying }) },
            )
        }
        try {
            at(7.9) { ui { listed = true } }
            at(8.9) { tap("بخر", first = true) }
            at(10.2) { tap("بخر") }
            // Card to card in a made-up bank app, then back with its receipt.
            at(11.5) { tap("کپی") }
            at(12.6) { ui { inBank = true } }
            at(14.2) { tap("تأیید و پرداخت") }
            at(15.0) { ui { bankStep = BankStep.Done } }
            at(16.2) { tap("کپی رسید") }
            at(17.2) { ui { inBank = false } }
            at(18.5) { tap("چسباندن") }
            at(19.7) { tap("پرداخت کردم") }
            at(22.6) { ui { trade = DONE } }
            at(24.6) { tap("آره، غذا رو گرفتم") }
            at(27.0) {}
        } finally {
            stop()
        }
    }

    /** Opens the screen, starts recording, shows black for 3 s, then the scene; time 0 is when it appears. */
    private fun record(name: String, scene: @Composable () -> Unit) {
        val activity = launch()
        instrumentation.runOnMainSync {
            activity.setContent {
                CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                    ShiningTheme {
                        if (blank) Box(modifier = Modifier.fillMaxSize().background(Color.Black)) else scene()
                    }
                }
            }
        }
        shell("rm -f /sdcard/Download/$name.mp4")
        recording = automation.executeShellCommand("screenrecord --bit-rate 16000000 /sdcard/Download/$name.mp4")
        startedAt = SystemClock.uptimeMillis() + 3_000
        waitUntil(startedAt)
        ui { blank = false }
    }

    private fun launch(): ComponentActivity {
        var activity: ComponentActivity? = null
        ActivityScenario.launch(ComponentActivity::class.java).onActivity { activity = it }
        return checkNotNull(activity)
    }

    private fun stop() {
        shell("pkill -INT screenrecord")
        Thread.sleep(1_500)
        recording?.close()
    }

    private fun shell(command: String) {
        automation.executeShellCommand(command).use { fd ->
            ParcelFileDescriptor.AutoCloseInputStream(fd).readBytes()
        }
    }

    private fun ui(action: () -> Unit) = instrumentation.runOnMainSync(action)

    /** Waits until [seconds] into the script, then does [action]. */
    private fun at(seconds: Double, action: () -> Unit) {
        waitUntil(startedAt + (seconds * 1000).toLong())
        action()
    }

    private fun waitUntil(target: Long) {
        val left = target - SystemClock.uptimeMillis()
        if (left > 0) Thread.sleep(left)
    }

    /**
     * Taps the element showing [text] in the window on top (a sheet or dialog when one is open);
     * the last match, which in a dialog is its button, or the [first]. [below]: tap just under it,
     * for a field's label.
     */
    private fun tap(text: String, below: Boolean = false, first: Boolean = false) {
        val node = find(text, first)
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val x = bounds.exactCenterX()
        val y = if (below) bounds.bottom + 80f else bounds.exactCenterY()
        val down = SystemClock.uptimeMillis()
        touch(down, MotionEvent.ACTION_DOWN, x, y)
        Thread.sleep(90)
        touch(down, MotionEvent.ACTION_UP, x, y)
    }

    /** The lowest (or [first]: highest) element on screen showing [text], looking at the topmost window first. */
    private fun find(text: String, first: Boolean = false): AccessibilityNodeInfo {
        repeat(60) {
            // The window list can briefly miss the app's window; the active window covers that.
            val roots = automation.windows.sortedByDescending { it.layer }.mapNotNull { it.root } +
                listOfNotNull(automation.rootInActiveWindow)
            for (root in roots) {
                // An element that says exactly [text] beats one that only mentions it.
                val all = matches(root, text)
                val found = all.filter { (it.text ?: it.contentDescription)?.toString()?.trim() == text }.ifEmpty { all }
                val top = { node: AccessibilityNodeInfo -> Rect().also { node.getBoundsInScreen(it) }.top }
                (if (first) found.minByOrNull(top) else found.maxByOrNull(top))?.let { return it }
            }
            Thread.sleep(50)
        }
        val seen = automation.windows.joinToString("\n") { w -> "${w.layer} ${w.title}: " + texts(w.root).joinToString(" | ") }
        throw AssertionError("Nothing shows \"$text\". Windows:\n$seen")
    }

    /** Every element under [node] whose text or description contains [text], in screen order. */
    private fun matches(node: AccessibilityNodeInfo?, text: String): List<AccessibilityNodeInfo> {
        if (node == null) return emptyList()
        val own = listOfNotNull(node.text, node.contentDescription).any { text in it.toString() }
        return (if (own) listOf(node) else emptyList()) + (0 until node.childCount).flatMap { matches(node.getChild(it), text) }
    }

    private fun texts(node: AccessibilityNodeInfo?): List<String> {
        if (node == null) return emptyList()
        val own = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        return own + (0 until node.childCount).flatMap { texts(node.getChild(it)) }
    }

    private fun touch(downTime: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        automation.injectInputEvent(event, true)
        event.recycle()
    }

    /** Types [text] (digits) one key at a time into the field that has the cursor. */
    private fun type(at: Double, every: Double, text: String) {
        text.forEachIndexed { i, c -> at(at + i * every) { key(KeyEvent.KEYCODE_0 + (c - '0')) } }
    }

    private fun key(code: Int) {
        val now = SystemClock.uptimeMillis()
        automation.injectInputEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0), true)
        automation.injectInputEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0), true)
    }

    /**
     * Two apps and a swipe between them, like switching apps on a phone: [shining] slides out
     * as a card while [other] slides in, or back.
     */
    @Composable
    private fun AppSwitch(otherApp: Boolean, shining: @Composable () -> Unit, other: @Composable () -> Unit) {
        val progress by animateFloatAsState(
            targetValue = if (otherApp) 1f else 0f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            label = "appSwitch",
        )
        // Cards shrink in the middle of the swipe and fill the screen again at the end.
        val lift = sin(progress * PI.toFloat()).coerceAtLeast(0f)
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFF050506))) {
            val width = constraints.maxWidth.toFloat()
            // Right to left: the other app comes in from the left, as the next card in Android's switcher.
            SwitchPage(offset = progress * width * 1.05f, lift = lift, content = shining)
            SwitchPage(offset = (progress - 1f) * width * 1.05f, lift = lift, content = other)
        }
    }

    /**
     * One app in [AppSwitch]. Both stay composed and the hidden one just sits off screen: taking the
     * trade screen out of the composition and back trips a Compose 1.5 bug.
     */
    @Composable
    private fun SwitchPage(offset: Float, lift: Float, content: @Composable () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offset
                    scaleX = 1f - 0.16f * lift
                    scaleY = 1f - 0.16f * lift
                    shape = RoundedCornerShape((28 * lift).dp)
                    clip = true
                }
        ) { content() }
    }

    /** A made-up mobile bank's card-to-card screen: the form, then the receipt. */
    @Composable
    private fun MockBank(step: BankStep, onPay: () -> Unit) {
        val clipboard = LocalClipboardManager.current
        val green = Color(0xFF1FA87A)
        val ink = Color(0xFFF2F4F3)
        val muted = Color(0xFF93A09B)
        val surface = Color(0xFF17201D)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E1513))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text = "همراه‌بانک", color = green, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(text = "کارت به کارت", color = ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            if (step != BankStep.Done) {
                BankRow("از کارت", ltr("۵۰۲۲-۲۹**-****-۱۰۷۳"), surface, ink, muted)
                BankRow("به کارت", ltr("۶۰۳۷-۹۹۷۵-۰۰۱۲-۴۸۲۱"), surface, ink, muted)
                BankRow("صاحب کارت", "سارا محمدی", surface, ink, muted)
                BankRow("مبلغ", "۵۰۰٬۰۰۰ ریال", surface, ink, muted)
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(green)
                        .clickable(onClick = onPay),
                    contentAlignment = Alignment.Center,
                ) {
                    if (step == BankStep.Paying) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                    } else {
                        Text(text = "تأیید و پرداخت", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(72.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(green),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "✓", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "کارت به کارت انجام شد",
                    color = ink,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                BankRow("مبلغ", "۵۰۰٬۰۰۰ ریال", surface, ink, muted)
                BankRow("به نام", "سارا محمدی", surface, ink, muted)
                BankRow("تاریخ", "۱۲:۰۰ جمعه، ۷ آذر ۱۴۰۵", surface, ink, muted)
                BankRow("شماره پیگیری", "۸۲۷۳۴۱۹۰۵۵", surface, ink, muted)
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(surface)
                        .clickable { clipboard.setText(AnnotatedString(RECEIPT)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "کپی رسید", color = green, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    private fun BankRow(label: String, value: String, surface: Color, ink: Color, muted: Color) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, color = muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(text = value, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }

    private enum class BankStep { Form, Paying, Done }

    @Composable
    private fun Market(state: MarketScreenState, onSell: () -> Unit = {}, onBuy: (ListingView) -> Unit = {}) {
        Box(modifier = Modifier.fillMaxSize().background(ShiningTheme.colors.background)) {
            MarketScreen(
                state = state,
                onRefresh = {},
                onJoin = {},
                onLeave = {},
                onLogin = {},
                onDaySelected = {},
                onMealTypeSelected = {},
                onSelfSelected = {},
                onSell = onSell,
                onBuy = onBuy,
                onCancelListing = {},
                onTradeClick = {},
            )
            BottomNavigationBarContent(
                currentRoute = ShiningRoutes.MarketScreen.route,
                onItemClick = {},
                onProfileLongPress = {},
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    @Composable
    private fun Trade(
        trade: TradeView,
        onPaid: () -> Unit = {},
        onConfirm: () -> Unit = {},
        onRate: (Boolean) -> Unit = {},
    ) {
        // The timers count down as on a phone.
        val now by produceState(NOW) {
            while (true) {
                value = NOW + (SystemClock.uptimeMillis() - startedAt)
                delay(250)
            }
        }
        TradeScreen(
            state = TradeState(trade = trade, loading = false),
            now = now,
            note = note,
            problem = "",
            showProblem = false,
            onNoteChange = { note = it.take(300) },
            onProblemChange = {},
            onShowProblem = {},
            onBack = {},
            onRetry = {},
            onPaid = onPaid,
            onCancel = {},
            onConfirm = onConfirm,
            onRate = onRate,
            onSendProblem = {},
            onOpenBoard = {},
        )
    }

    private companion object {
        val NOW = System.currentTimeMillis()
        private const val DAY = 24 * 60 * 60 * 1000L

        fun date(daysFromNow: Int): String = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("Asia/Tehran") }
            .format(Date(NOW + daysFromNow * DAY))

        fun instant(millis: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))

        fun meal(days: Int, mealTypeId: Int, mealName: String, selfId: Int, self: String, food: String, price: Long) =
            MealInfo(date(days), mealTypeId, mealName, selfId, self, foodId = food.hashCode(), foodName = food, reservedPrice = price)

        /** Keeps a card number in order inside right-to-left text, as banks' receipts do. */
        fun ltr(text: String) = "\u2066$text\u2069"

        /** What the made-up bank copies, and the buyer pastes as the note. Every name and number is invented. */
        val RECEIPT = """
            همراه‌بانک
            انتقال کارت به کارت
            از کارت: ${ltr("۵۰۲۲-۲۹**-****-۱۰۷۳")}
            به کارت: ${ltr("۶۰۳۷-۹۹۷۵-۰۰۱۲-۴۸۲۱")}
            به نام: سارا محمدی
            مبلغ: ۵۰۰٬۰۰۰ ریال
            تاریخ: ۱۲:۰۰ جمعه، ۷ آذر ۱۴۰۵
            شماره پیگیری: ۸۲۷۳۴۱۹۰۵۵
        """.trimIndent()

        /** The meal that changes hands. */
        val SOLD = meal(1, 1, "ناهار", 1, "مرکزی - سلف دانشجویان آقا", "چلو مرغ", 45_000)
        private val OTHERS = listOf(
            meal(1, 1, "ناهار", 2, "کاله", "کباب کوبیده", 98_000) to 85_000L,
            meal(1, 5, "شام", 1, "مرکزی - سلف دانشجویان آقا", "عدس پلو", 35_000) to 30_000L,
            meal(2, 1, "ناهار", 3, "کلانا", "پاستا آلفردو", 120_000) to 100_000L,
        ).mapIndexed { i, (meal, price) ->
            ListingView(100L + i, meal.key, meal, price, verified = i != 1, reputation = Reputation(sales = 3 - i))
        }
        private const val PRICE = 50_000L

        private val NEW_LISTING = ListingView(1, SOLD.key, SOLD, PRICE, verified = true, reputation = Reputation(sales = 4))

        private fun state(listings: List<ListingView>) = MarketScreenState(
            joined = true,
            all = listings,
            listings = listings,
            days = listOf(date(1) to "فردا", date(2) to MarketMeals.dayLabel(date(2))),
            loading = false,
        )

        val BEFORE = state(OTHERS)
        // Same order the app uses: soonest meal first, then cheapest.
        val SELLER_AFTER = state(listOf(NEW_LISTING.copy(mine = true, reserveId = 11)) + OTHERS)
        val BUYER_AFTER = state(listOf(NEW_LISTING) + OTHERS)

        val SELL_START = SellFormState(
            visible = true,
            reservations = listOf(MarketMeals.Sellable(SOLD, reserveId = 11)),
            menu = listOf(SOLD) + OTHERS.map { it.meal },
            savedCard = "6037 99** **** 4821",
        )

        val TRADE = TradeView(
            id = 41,
            key = SOLD.key,
            meal = SOLD,
            role = "buyer",
            origin = "buy_now",
            price = PRICE,
            status = TradeView.AWAITING_PAYMENT,
            createdAt = instant(NOW),
            payBy = instant(NOW + 5 * 60_000 + 10_000),
            card = "6037 9975 0012 4821",
            counterpart = Reputation(sales = 4),
            counterpartVerified = true,
        )
        val PAID = TRADE.copy(
            status = TradeView.PAID,
            paidAt = instant(NOW + 15_000),
            confirmBy = instant(NOW + 10 * 60_000 + 15_000),
            note = RECEIPT,
        )
        val DONE = PAID.copy(
            status = TradeView.COMPLETED,
            completedAt = instant(NOW + 18_000),
            code = "48213",
        )
    }
}
