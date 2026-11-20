package app.shining.order.ui.component

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.shining.R
import app.shining.domain.ui.component.BottomBarSpace
import app.shining.domain.ui.component.Dot
import app.shining.domain.ui.component.EmptyState
import app.shining.domain.ui.component.EmojiTile
import app.shining.domain.ui.component.PillTone
import app.shining.domain.ui.component.ShiningButton
import app.shining.domain.ui.component.ShiningButtonSize
import app.shining.domain.ui.component.ShiningButtonStyle
import app.shining.domain.ui.component.ShiningCard
import app.shining.domain.ui.component.ShiningDivider
import app.shining.domain.ui.component.StatusPill
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.toGroupedLocalNumber
import app.shining.domain.util.toLocalNumber
import app.shining.order.domain.model.FoodEmoji
import app.shining.order.domain.model.MealType
import app.shining.order.ui.fixture.orderScreenUIModelFixture
import app.shining.order.ui.model.OrderCardUIModel
import app.shining.order.ui.model.OrderFoodDetailUIModel
import app.shining.order.ui.model.OrderFoodKey
import app.shining.order.ui.model.OrderScreenUIModel
import app.shining.order.ui.model.key
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/** The week's program: a row of day chips and, under it, one card per meal of the chosen day. */
@Composable
fun OrderScreen(
    data: OrderScreenUIModel,
    pendingFoods: ImmutableSet<OrderFoodKey>,
    onReserveFoodClick: (OrderFoodDetailUIModel) -> Unit,
    /** Day (yyyy-MM-dd) to start on, e.g. from a menu notification. */
    initialDay: String? = null,
    /** Cafeterias whose foods just came out; tagged as new. */
    newSelfIds: Set<Int> = emptySet(),
) {
    val days = data.orderCardUIModelList
    if (days.isEmpty()) {
        EmptyState(
            emoji = R.drawable.emoji_calendar,
            title = stringResource(R.string.order_empty_title),
            subtitle = stringResource(R.string.order_empty_subtitle),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    var selectedDay by rememberSaveable(initialDay) { mutableIntStateOf(dayIndexFor(days.map { it.date }, initialDay)) }
    val dayIndex = selectedDay.coerceIn(0, days.lastIndex)
    val day = days[dayIndex]

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(days, key = { index, it -> "$index-${it.date}" }) { index, card ->
                DayChip(
                    title = card.farsiDayName,
                    hasReservation = card.hasReservation(),
                    selected = index == dayIndex,
                    onClick = { selectedDay = index },
                )
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = BottomBarSpace,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = day.reserveInfoList.toList(),
                key = { (mealType, _) -> "day-$dayIndex-${day.date}-$mealType" },
            ) { (mealType, foods) ->
                MealTypeCard(
                    mealType = mealType,
                    foods = foods,
                    pendingFoods = pendingFoods,
                    newSelfIds = newSelfIds,
                    onReserveFoodClick = onReserveFoodClick,
                )
            }
        }
    }
}

/** The index of [day] (yyyy-MM-dd) among the program's [dates], or the first day if it isn't there. */
internal fun dayIndexFor(dates: List<String>, day: String?): Int =
    day?.let { wanted -> dates.indexOfFirst { it.take(10) == wanted.take(10) } }?.takeIf { it >= 0 } ?: 0

private fun OrderCardUIModel.hasReservation(): Boolean =
    reserveInfoList.values.any { foods -> foods.any { it.isSelected } }

@Composable
private fun DayChip(
    title: String,
    hasReservation: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = ShiningTheme.colors
    val shape = ShiningTheme.shapes.pill
    Row(
        modifier = Modifier
            .height(42.dp)
            .clip(shape)
            .background(if (selected) colors.ink else colors.surface)
            .border(1.dp, if (selected) colors.ink else colors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = ShiningTheme.typography.label,
            color = if (selected) colors.onInk else colors.ink,
        )
        if (hasReservation) {
            Dot(color = colors.positive)
        }
    }
}

@Composable
private fun MealTypeCard(
    mealType: MealType,
    foods: List<OrderFoodDetailUIModel>,
    pendingFoods: ImmutableSet<OrderFoodKey>,
    newSelfIds: Set<Int>,
    onReserveFoodClick: (OrderFoodDetailUIModel) -> Unit,
) {
    ShiningCard(contentPadding = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EmojiTile(emoji = mealType.getEmoji())
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mealType.getLocalName(),
                    style = ShiningTheme.typography.title,
                    color = ShiningTheme.colors.ink,
                )
                Text(
                    text = stringResource(R.string.order_options_count, foods.size.toString().toLocalNumber()),
                    style = ShiningTheme.typography.caption,
                    color = ShiningTheme.colors.inkSecondary,
                )
            }
            if (foods.any { it.isSelected }) {
                StatusPill(
                    text = stringResource(R.string.order_order_food_detail_selected),
                    tone = PillTone.Positive,
                    icon = R.drawable.ic_check,
                )
            }
        }
        foods.forEach { food ->
            ShiningDivider()
            FoodOptionRow(
                data = food,
                isLoading = food.key in pendingFoods,
                isNew = food.selfId in newSelfIds,
                onClick = { onReserveFoodClick(food) },
            )
        }
    }
}

@Composable
fun FoodOptionRow(
    data: OrderFoodDetailUIModel,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Its cafeteria just published the menu (opened from a notification). */
    isNew: Boolean = false,
) {
    val colors = ShiningTheme.colors
    // Derived from data on every composition: the item stays on screen while it is reserved or
    // cancelled, so remembered values would go stale.
    val isMissed = !data.isSelected && data.isDisable
    val canAct = !data.isDisable && data.canSelect

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (data.isSelected) colors.positiveSoft.copy(alpha = 0.55f) else colors.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiTile(
            emoji = FoodEmoji.forFood(data.foodName, MealType.getMealTypeById(data.mealTypeId)),
            muted = isMissed,
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = data.foodName,
                style = ShiningTheme.typography.subtitle,
                color = if (isMissed) colors.inkTertiary else colors.ink,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_money_bag),
                    contentDescription = null,
                    tint = colors.inkSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = data.price.toGroupedLocalNumber(),
                    style = ShiningTheme.typography.label,
                    color = colors.inkSecondary,
                )
                data.selfName?.let {
                    // Weighted so a status pill keeps its room and a long name is cut instead.
                    SelfTag(name = it, modifier = Modifier.weight(1f, fill = false))
                }
                when {
                    isMissed -> StatusPill(
                        text = stringResource(R.string.order_order_food_detail_disabled),
                        tone = PillTone.Neutral,
                    )

                    !data.canSelect && !data.isSelected -> StatusPill(
                        text = stringResource(R.string.order_order_food_detail_selected_elsewhere),
                        tone = PillTone.Warning,
                    )

                    isNew -> StatusPill(
                        text = stringResource(R.string.order_food_new),
                        tone = PillTone.Brand,
                    )
                }
            }
        }
        if (canAct) {
            Spacer(modifier = Modifier.size(12.dp))
            if (data.isSelected) {
                ShiningButton(
                    text = stringResource(R.string.order_order_food_detail_cancel),
                    onClick = onClick,
                    style = ShiningButtonStyle.Danger,
                    size = ShiningButtonSize.Small,
                    loading = isLoading,
                    icon = R.drawable.ic_close,
                )
            } else {
                ShiningButton(
                    text = stringResource(R.string.order_order_food_detail_reserve),
                    onClick = onClick,
                    style = ShiningButtonStyle.Primary,
                    size = ShiningButtonSize.Small,
                    loading = isLoading,
                    icon = R.drawable.ic_add,
                )
            }
        } else if (data.isSelected) {
            Spacer(modifier = Modifier.size(12.dp))
            StatusPill(
                text = stringResource(R.string.order_order_food_detail_selected),
                tone = PillTone.Positive,
                icon = R.drawable.ic_check,
            )
        }
    }
}

/** Names the self a food is served at, so foods from every self can share one list. */
@Composable
private fun SelfTag(name: String, modifier: Modifier = Modifier) {
    val colors = ShiningTheme.colors
    Row(
        modifier = modifier
            .clip(ShiningTheme.shapes.pill)
            .background(colors.surfaceMuted)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_location),
            contentDescription = null,
            tint = colors.inkSecondary,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = name,
            style = ShiningTheme.typography.caption,
            color = colors.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun OrderScreenPreview() {
    ShiningTheme {
        OrderScreen(
            data = orderScreenUIModelFixture,
            pendingFoods = persistentSetOf(),
            onReserveFoodClick = {},
        )
    }
}
