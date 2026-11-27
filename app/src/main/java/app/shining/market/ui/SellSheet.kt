package app.shining.market.ui

import androidx.compose.foundation.background
import app.shining.domain.ui.theme.SheetWindowInsets
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.shining.domain.ui.component.EmojiTile
import app.shining.domain.ui.component.LoadingState
import app.shining.domain.ui.component.ShiningButton
import app.shining.domain.ui.component.ShiningButtonSize
import app.shining.domain.ui.component.ShiningButtonStyle
import app.shining.domain.ui.component.ShiningProgressIndicator
import app.shining.domain.ui.component.ShiningTextField
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.market.api.MealInfo
import app.shining.market.domain.MarketMeals
import app.shining.order.domain.model.FoodEmoji
import app.shining.order.domain.model.MealType
import app.shining.order.domain.model.foodNameWithoutPlace
import app.shining.order.domain.model.shortSelfName

/**
 * The sell form, as a sheet over the Market tab. Top to bottom: the forget code (the main
 * thing), "or pick from my reservations" (only with a Samad login; it fills the meal and the
 * code), which meal it is (day, meal, cafeteria, food, from the server's copy of the menus),
 * the price and the card.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SellSheet(form: SellFormState, viewModel: MarketViewModel) {
    ModalBottomSheet(
        onDismissRequest = viewModel::closeSell,
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = "غذات رو بفروش", style = ShiningTheme.typography.headline, color = colors.ink)

                ShiningTextField(
                    value = form.code,
                    onValueChange = viewModel::onSellCode,
                    label = "کد فراموشی غذا",
                    placeholder = "مثلاً ۱۲۳۴۵",
                    ltrInput = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "کد رو خریدار فقط وقتی می‌بینه که تو پرداختش رو تأیید کنی.",
                    style = ShiningTheme.typography.caption,
                    color = colors.inkTertiary,
                )

                if (form.reservations.isNotEmpty()) {
                    Label("یا از رزروهات انتخاب کن")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        form.reservations.forEach { item ->
                            val place = shortSelfName(item.meal.selfName)
                            Chip(
                                label = "${MarketMeals.dayLabel(item.meal.date)} ${item.meal.mealName}: " +
                                    foodNameWithoutPlace(item.meal.foodName, place),
                                selected = item.reserveId == form.pickedReserveId,
                                onClick = { viewModel.onPickReservation(item) },
                            )
                        }
                    }
                    if (form.loadingCodeFor != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ShiningProgressIndicator(size = 16.dp, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("کد رو از سماد می‌گیریم…", style = ShiningTheme.typography.caption, color = colors.inkSecondary)
                        }
                    }
                }

                Label("کدوم غذا؟")
                val meal = form.meal
                if (form.pickedReserveId != null && meal != null) {
                    PickedMeal(meal)
                } else {
                    MealPicker(form, viewModel)
                }

                PriceField(value = form.price, onValueChange = viewModel::onSellPrice, label = "قیمت (تومن)", modifier = Modifier.fillMaxWidth())

                if (form.needsCard) {
                    ShiningTextField(
                        value = form.card,
                        onValueChange = { text -> viewModel.onSellCard(text.filter { it.isDigit() || it == ' ' || it == '-' }.take(24)) },
                        label = "شماره‌ی کارت (برای واریز خریدار)",
                        placeholder = "۱۶ رقم روی کارتت",
                        ltrInput = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "واریز به کارت ${ltr(form.savedCard.orEmpty())}",
                            style = ShiningTheme.typography.label,
                            color = colors.inkSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        ShiningButton(text = "عوض کن", onClick = viewModel::onChangeCard, style = ShiningButtonStyle.Ghost, size = ShiningButtonSize.Small)
                    }
                }

                form.error?.let { Text(text = it, style = ShiningTheme.typography.label, color = colors.negative) }

                ShiningButton(
                    text = "بذار برای فروش",
                    onClick = viewModel::onSubmitSell,
                    loading = form.busy,
                    size = ShiningButtonSize.Large,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Day, then meal, then cafeteria, then food: chips at each step, from the server's menus. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealPicker(form: SellFormState, viewModel: MarketViewModel) {
    val colors = ShiningTheme.colors
    when {
        form.menuLoading && form.menu.isEmpty() -> LoadingState(modifier = Modifier.fillMaxWidth().padding(16.dp))
        form.menu.isEmpty() -> Text(
            text = form.menuError ?: "منوی سلف‌ها پیدا نشد.",
            style = ShiningTheme.typography.label,
            color = colors.negative,
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                form.menuDays.forEach { day ->
                    Chip(label = MarketMeals.dayLabel(day), selected = day == form.day, onClick = { viewModel.onSellDay(day) })
                }
            }
            if (form.mealTypes.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    form.mealTypes.forEach { (id, name) ->
                        Chip(label = name, selected = id == form.mealTypeId, onClick = { viewModel.onSellMealType(id) })
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                form.selves.forEach { (id, name) ->
                    Chip(label = name, selected = id == form.selfId, onClick = { viewModel.onSellSelf(id) })
                }
            }
            form.foods.takeIf { form.selfId != null }?.forEach { food ->
                FoodOption(meal = food, selected = food.key == form.meal?.key, onClick = { viewModel.onSellMeal(food) })
            }
        }
    }
}

@Composable
private fun FoodOption(meal: MealInfo, selected: Boolean, onClick: () -> Unit) {
    val colors = ShiningTheme.colors
    val shape = ShiningTheme.shapes.tile
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.brandSoft else colors.surfaceMuted)
            .border(1.dp, if (selected) colors.brand else colors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiTile(emoji = FoodEmoji.forFood(meal.foodName, MealType.getMealTypeById(meal.mealTypeId)), size = 32.dp, emojiSize = 18.dp)
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = foodNameWithoutPlace(meal.foodName, shortSelfName(meal.selfName)),
            style = ShiningTheme.typography.bodyStrong,
            color = colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PickedMeal(meal: MealInfo) {
    MarketMealHeader(
        meal = meal,
        modifier = Modifier
            .clip(ShiningTheme.shapes.tile)
            .background(ShiningTheme.colors.surfaceMuted)
            .padding(12.dp),
    )
}

@Composable
private fun Label(text: String) {
    Text(text = text, style = ShiningTheme.typography.label, color = ShiningTheme.colors.inkSecondary)
}
