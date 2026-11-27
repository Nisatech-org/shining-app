package app.shining.market.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import app.shining.R
import app.shining.domain.ui.component.ShiningButtonStyle
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
    val reserveId: Int,
    /** The highest offer or the last trade; null when nobody has traded or offered yet. */
    val priceNow: Long?,
    val offers: Int,
)

/**
 * "Want to sell today's meal?" at the top of Home: the soonest reserved meal
 * that isn't eaten and is still being served, with its live price. Sell opens the Market tab's
 * sell form with that reservation picked.
 */
class MarketWidgetViewModel(
    private val repository: MarketRepository,
    private val loadReservations: LoadReservationsUseCase,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val card = mutableStateOf<SellCardState?>(null)

    /** Reservations on the market or sold: Home greys them out. */
    val forSale = mutableStateOf<Set<Int>>(emptySet())

    fun onLaunch() {
        forSale.value = repository.forSaleReservations()
        viewModelScope.launch {
            val days = loadReservations.saved()?.info?.dayInfoList.orEmpty()
            val sold = repository.forSaleReservations()
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
                reserveId = next.reserveId,
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
    ShiningCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onSellClick,
        background = colors.inverse,
        contentPadding = PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = MarketMeals.dayLabel(meal.date).let { day ->
                    "${meal.mealName} $day${if (day.endsWith("ه")) "‌ت" else "ت"} رو نمی‌خوری؟"
                },
                style = ShiningTheme.typography.label,
                color = colors.onInverseSecondary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            val live = state.priceNow?.let { "الان ${it.toman()}" }
                ?: state.offers.takeIf { it > 0 }?.let { "${it.toLong().toGroupedLocalNumber()} پیشنهاد" }
            Text(
                text = live ?: "توی بازار",
                style = ShiningTheme.typography.label,
                color = if (live != null) colors.onBrand else colors.onInverse,
                maxLines = 1,
                modifier = Modifier
                    .clip(ShiningTheme.shapes.pill)
                    .background(if (live != null) colors.brand else colors.onInverse.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        Spacer(modifier = Modifier.size(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(
                emoji = FoodEmoji.forFood(meal.foodName, MealType.getMealTypeById(meal.mealTypeId)),
                size = 48.dp,
                emojiSize = 26.dp,
                background = colors.onInverse.copy(alpha = 0.1f),
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = meal.foodName,
                style = ShiningTheme.typography.title,
                color = colors.onInverse,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.size(8.dp))
            ShiningButton(
                text = "بفروش",
                icon = R.drawable.ic_arrow_up_left,
                onClick = onSellClick,
                style = ShiningButtonStyle.Brand,
                size = ShiningButtonSize.Medium,
            )
        }
    }
}
