package app.shining.order.domain.model

import app.shining.R
import app.shining.domain.static.allFoods
import org.junit.Assert.assertEquals
import org.junit.Test

class FoodEmojiTest {

    // Every name Samad is known to use, with the emoji it should get (null: falls back to the meal).
    private val expected: Map<String, Int?> = mapOf(
        "آش رشته و بسته میوه" to R.drawable.emoji_stew,
        "استامبولی پلو با گوشت" to R.drawable.emoji_rice,
        "الویه ژامبون نرمال" to R.drawable.emoji_salad,
        "الویه ژامبون کوچک اقتصادی" to R.drawable.emoji_salad,
        "الویه ژامبون کوچک پلاس" to R.drawable.emoji_salad,
        "خوراک بندری" to R.drawable.emoji_hotdog,
        "خوراک شنیتسل مرغ" to R.drawable.emoji_poultry_leg,
        "خوراک عدسی و بسته میوه" to R.drawable.emoji_stew,
        "خوراک فلافل" to R.drawable.emoji_falafel,
        "خوراک فیله سوخاری" to R.drawable.emoji_poultry_leg,
        "خوراک قارچ و مرغ" to R.drawable.emoji_poultry_leg,
        "خوراک ماکارونی با گوشت" to R.drawable.emoji_spaghetti,
        "خوراک میرزا قاسمی" to R.drawable.emoji_eggplant,
        "خوراک کتلت" to null,
        "خوراک کوکو سیب‌زمینی" to R.drawable.emoji_cooking,
        "رشته پلو با گوشت" to R.drawable.emoji_rice,
        "زرشک پلو با گوشت" to R.drawable.emoji_rice,
        "زرشک‌پلو با مرغ" to R.drawable.emoji_poultry_leg,
        "سالاد الویه (غذای سرد)" to R.drawable.emoji_salad,
        "سالاد ماکارونی و سوپ جو" to R.drawable.emoji_salad,
        "ساندویچ مرغ و خوراک عدسی" to R.drawable.emoji_sandwich,
        "ساندویچ مرغ و خوراک لوبیا" to R.drawable.emoji_sandwich,
        "ساندویچ مرغ و سوپ جو" to R.drawable.emoji_sandwich,
        "ساندویچ همبرگر ذغالی نرمال" to R.drawable.emoji_burger,
        "ساندویچ همبرگر ذغالی کوچک اقتصادی" to R.drawable.emoji_burger,
        "ساندویچ همبرگر ذغالی کوچک پلاس" to R.drawable.emoji_burger,
        "ساندویچ همبرگر ذغالی کوچک" to R.drawable.emoji_burger,
        "ساندویچ کوکتل بندری نرمال" to R.drawable.emoji_hotdog,
        "ساندویچ کوکتل بندری کوچک اقتصادی" to R.drawable.emoji_hotdog,
        "ساندویچ کوکتل بندری کوچک پلاس" to R.drawable.emoji_hotdog,
        "ساندویچ کوکو سبزی نرمال" to R.drawable.emoji_sandwich,
        "ساندویچ کوکو سبزی کوچک" to R.drawable.emoji_sandwich,
        "ساندویچ گوشت و قارچ با پوره سیب زمینی نرمال" to R.drawable.emoji_sandwich,
        "ساندویچ گوشت و قارچ با پوره سیب زمینی کوچک" to R.drawable.emoji_sandwich,
        "سبزی پلو با تن ماهی" to R.drawable.emoji_fish,
        "سبزی پلو با ماهی" to R.drawable.emoji_fish,
        "شنیتسل مرغ با برنج" to R.drawable.emoji_poultry_leg,
        "شوید پلو با مرغ" to R.drawable.emoji_poultry_leg,
        "عدس‌پلو با گوشت" to R.drawable.emoji_rice,
        "قارچ پلو" to R.drawable.emoji_mushroom,
        "لوبیا پلو" to R.drawable.emoji_rice,
        "ناگت مرغ آماده (غذای سرد)" to R.drawable.emoji_poultry_leg,
        "چلو با تن ماهی" to R.drawable.emoji_fish,
        "چلو با شامی کباب" to R.drawable.emoji_kebab,
        "چلو جوجه‌کباب" to R.drawable.emoji_kebab,
        "چلو خورشت مسما بادمجان" to R.drawable.emoji_curry_rice,
        "چلو کباب کوبیده" to R.drawable.emoji_kebab,
        "چلوخورش فسنجان با مرغ" to R.drawable.emoji_curry_rice,
        "چلوخورشت آلو اسفناج" to R.drawable.emoji_curry_rice,
        "چلو‌خورش بامیه" to R.drawable.emoji_curry_rice,
        "چلو‌خورش قورمه‌سبزی" to R.drawable.emoji_curry_rice,
        "چلو‌خورش قیمه‌ سیب زمینی" to R.drawable.emoji_curry_rice,
        "چلو‌خورش قیمه‌بادمجان" to R.drawable.emoji_curry_rice,
        "چلو‌خورش کرفس" to R.drawable.emoji_curry_rice,
        "کالباس خشک نرمال" to R.drawable.emoji_bacon,
        "کالباس خشک کوچک" to R.drawable.emoji_bacon,
        "کباب مرغ با پنیر کوچک اقتصادی" to R.drawable.emoji_kebab,
        "کباب مرغ با پنیرنرمال" to R.drawable.emoji_kebab,
        "کباب مرغ با پنیرکوچک پلاس" to R.drawable.emoji_kebab,
        "کشک بادمجان" to R.drawable.emoji_eggplant,
        "کنسرو لوبیا چیتی (غذای سرد)" to R.drawable.emoji_canned,
        "کوکو سیب زمینی نرمال" to R.drawable.emoji_cooking,
        "کوکو سیب زمینی کوچک اقتصادی" to R.drawable.emoji_cooking,
        "کوکو سیب زمینی کوچک پلاس" to R.drawable.emoji_cooking,
    )

    @Test
    fun everyKnownFoodGetsTheExpectedEmoji() {
        expected.forEach { (name, emoji) ->
            assertEquals(name, emoji, FoodEmoji.forFood(name))
        }
    }

    @Test
    fun theFoodListHasNoNameMissingFromThisTest() {
        val missing = allFoods.map { it.name }.toSet() - expected.keys
        assertEquals(emptySet<String>(), missing)
    }

    @Test
    fun arabicLettersAndSpacingDoNotMatter() {
        assertEquals(R.drawable.emoji_curry_rice, FoodEmoji.forFood("چلو خورش قيمه"))
        assertEquals(R.drawable.emoji_kebab, FoodEmoji.forFood("چلو  جوجه كباب"))
        assertEquals(R.drawable.emoji_rice, FoodEmoji.forFood("زرشک‌پلو"))
    }

    @Test
    fun specificDishesWinOverBroadOnes() {
        assertEquals(R.drawable.emoji_meat_bone, FoodEmoji.forFood("چلو ماهیچه"))
        assertEquals(R.drawable.emoji_cooking, FoodEmoji.forFood("املت گوجه با تخم مرغ"))
        assertEquals(R.drawable.emoji_cooking, FoodEmoji.forFood("تخم مرغ آب پز"))
    }

    @Test
    fun unknownFoodsFallBackToTheMeal() {
        assertEquals(null, FoodEmoji.forFood("خوراک کتلت"))
        assertEquals(null, FoodEmoji.forFood(""))
        assertEquals(R.drawable.emoji_sun, FoodEmoji.forFood("خوراک کتلت", MealType.LUNCH))
        assertEquals(R.drawable.emoji_moon, FoodEmoji.forFood("دلمه", MealType.DINNER))
    }
}
