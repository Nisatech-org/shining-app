package app.shining.order.domain.model

import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import app.shining.R

/**
 * Picks an emoji for a food from keywords in its Persian name.
 *
 * Samad writes the same dish in different ways ("چلو‌خورش" and "چلوخورشت", "زرشک پلو" and
 * "زرشک‌پلو"), so names are compared with spaces and half-spaces removed. Rules are checked in
 * order and the first match wins, so more specific dishes come before broad ones: "سالاد ماکارونی"
 * is a salad before it is pasta, and "تخم مرغ" is an egg before it is chicken.
 */
object FoodEmoji {

    private val rules: List<Pair<List<String>, Int>> = listOf(
        listOf("کنسرو") to R.drawable.emoji_canned,
        listOf("همبرگر", "برگر") to R.drawable.emoji_burger,
        listOf("پیتزا") to R.drawable.emoji_pizza,
        listOf("هاتداگ", "سوسیس", "کوکتل", "بندری") to R.drawable.emoji_hotdog,
        listOf("ساندویچ", "ساندویج") to R.drawable.emoji_sandwich,
        listOf("فلافل", "کوفته") to R.drawable.emoji_falafel,
        listOf("سالاد", "الویه") to R.drawable.emoji_salad,
        listOf("ماکارونی", "پاستا", "اسپاگتی", "لازانیا") to R.drawable.emoji_spaghetti,
        listOf("آش", "سوپ", "حلیم", "عدسی") to R.drawable.emoji_stew,
        listOf("میگو") to R.drawable.emoji_shrimp,
        // Before fish: "ماهیچه" (lamb shank) starts with "ماهی".
        listOf("ماهیچه") to R.drawable.emoji_meat_bone,
        listOf("ماهی") to R.drawable.emoji_fish,
        listOf("کباب", "جوجه") to R.drawable.emoji_kebab,
        listOf("خورش", "قیمه", "قورمه", "فسنجان") to R.drawable.emoji_curry_rice,
        listOf("کوکو", "املت", "نیمرو", "تخممرغ") to R.drawable.emoji_cooking,
        listOf("مرغ", "ناگت", "سوخاری", "شنیتسل", "شنیسل") to R.drawable.emoji_poultry_leg,
        listOf("بادمجان", "میرزاقاسمی") to R.drawable.emoji_eggplant,
        listOf("قارچ") to R.drawable.emoji_mushroom,
        listOf("سیبزمینیسرخ") to R.drawable.emoji_fries,
        listOf("سیبزمینی", "پوره") to R.drawable.emoji_potato,
        listOf("کالباس", "ژامبون") to R.drawable.emoji_bacon,
        listOf("پلو", "چلو", "برنج", "کته") to R.drawable.emoji_rice,
        listOf("استیک", "گوشت") to R.drawable.emoji_steak,
        listOf("پنیر") to R.drawable.emoji_cheese,
        listOf("نان") to R.drawable.emoji_bread,
        listOf("عسل", "مربا") to R.drawable.emoji_honey,
        listOf("میوه") to R.drawable.emoji_apple,
    )

    /** The emoji for [foodName], or null when no rule matches. */
    @DrawableRes
    fun forFood(foodName: String): Int? {
        val name = normalize(foodName)
        if (name.isEmpty()) return null
        return rules.firstOrNull { (keywords, _) -> keywords.any { it in name } }?.second
    }

    /** The emoji for [foodName], or the [mealType]'s emoji when no rule matches. */
    @DrawableRes
    fun forFood(foodName: String, mealType: MealType): Int = forFood(foodName) ?: mealType.getEmoji()

    @VisibleForTesting
    internal fun normalize(text: String): String = text
        .replace('ي', 'ی')
        .replace('ى', 'ی')
        .replace('ك', 'ک')
        .replace('ة', 'ه')
        .replace("ـ", "")
        .filterNot { it.isWhitespace() || it == '‌' || it == '‏' || it == '‎' }
}
