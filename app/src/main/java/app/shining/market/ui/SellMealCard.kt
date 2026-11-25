package app.shining.market.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shining.domain.ui.component.EmojiTile
import app.shining.domain.ui.component.ShiningButton
import app.shining.domain.ui.component.ShiningButtonSize
import app.shining.domain.ui.component.ShiningCard
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.toGroupedLocalNumber
import app.shining.market.api.MealInfo
import app.shining.market.domain.MarketMeals
import app.shining.market.domain.MarketRepository
import app.shining.market.domain.toman
import app.shining.order.domain.model.FoodEmoji
import app.shining.order.domain.model.MealType
import app.shining.reserveinfo.domain.usecase.LoadReservationsUseCase
import kotlinx.coroutines.launch

/** The Home card: the user's next meal they could sell, and what it trades at right now. */
data class SellCardState(
    val meal: MealInfo,
    /** The highest offer or the last trade; null when nobody has traded or offered yet. */
    val priceNow: Long?,
    val offers: Int,
)

/**
 * "Want to sell today's meal?" at the top of Home: the soonest reserved meal
 * that isn't eaten and is still being served, with its live price. Sell opens the meal's board.
 */
class MarketWidgetViewModel(
    private val repository: MarketRepository,
    private val loadReservations: LoadReservationsUseCase,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val card = mutableStateOf<SellCardState?>(null)

    fun onLaunch() {
        viewModelScope.launch {
            val days = loadReservations.saved()?.info?.dayInfoList.orEmpty()
            val sold = repository.soldReservations()
            val next = MarketMeals.sellable(days, now()).firstOrNull { it.reserveId !in sold }
            if (next == null) {
                card.value = null
                return@launch
            }
            repository.remember(next.meal)
            // Only shown once the server answers: not while the market is off or out of reach.
            val boards = repository.boards(next.meal.date).getOrElse {
                card.value = null
                return@launch
            }
            val summary = boards.firstOrNull { it.key == next.meal.key }
            card.value = SellCardState(
                meal = next.meal,
                priceNow = summary?.let { it.bestBid ?: it.lastPrice },
                offers = summary?.bids ?: 0,
            )
        }
    }
}

@Composable
fun SellMealCard(state: SellCardState, onSellClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ShiningTheme.colors
    val meal = state.meal
    ShiningCard(modifier = modifier.fillMaxWidth(), onClick = onSellClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(emoji = FoodEmoji.forFood(meal.foodName, MealType.getMealTypeById(meal.mealTypeId)))
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = MarketMeals.dayLabel(meal.date).let { day ->
                        "${meal.mealName} $day${if (day.endsWith("ه")) "‌ت" else "ت"} رو نمی‌خوری؟"
                    },
                    style = ShiningTheme.typography.subtitle,
                    color = colors.ink,
                    maxLines = 1,
                )
                Text(
                    text = meal.foodName,
                    style = ShiningTheme.typography.caption,
                    color = colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val live = state.priceNow?.let { "الان ${it.toman()} معامله می‌شه" }
                    ?: state.offers.takeIf { it > 0 }?.let { "${it.toLong().toGroupedLocalNumber()} پیشنهاد خرید داره" }
                Text(
                    text = live ?: "توی بازار بفروشش",
                    style = ShiningTheme.typography.label,
                    color = if (live != null) colors.positive else colors.inkSecondary,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
            ShiningButton(text = "بفروش", onClick = onSellClick, size = ShiningButtonSize.Small)
        }
    }
}
