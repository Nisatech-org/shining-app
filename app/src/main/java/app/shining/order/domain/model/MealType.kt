package app.shining.order.domain.model

import app.shining.R

enum class MealType {
    SAHAR,
    BREAKFAST,
    LUNCH,
    EFTAR,
    DINNER,
    OTHER;

    fun getLocalName(): String {
        return when (this) {
            SAHAR -> "سحری"
            BREAKFAST -> "صبحانه"
            LUNCH -> "ناهار"
            DINNER -> "شام"
            EFTAR -> "افطار"
            OTHER -> "سایر"
        }
    }

    fun getAbbreviation(): String {
        return when (this) {
            SAHAR -> "سحری"
            BREAKFAST -> "صبحانه"
            LUNCH -> "ناهار"
            DINNER -> "شام"
            EFTAR -> "افطار"
            OTHER -> "سایر"
        }
    }

    /** The emoji drawn next to this meal. */
    fun getEmoji(): Int {
        return when (this) {
            SAHAR -> R.drawable.emoji_sunrise
            BREAKFAST -> R.drawable.emoji_sunrise
            LUNCH -> R.drawable.emoji_sun
            DINNER -> R.drawable.emoji_moon
            EFTAR -> R.drawable.emoji_sunset
            OTHER -> R.drawable.emoji_plate
        }
    }

    companion object {
        /** Best guess from a meal's Persian name, as the reservation list only has the name. */
        fun fromLocalName(name: String): MealType = when {
            "سحر" in name -> SAHAR
            "صبحانه" in name -> BREAKFAST
            "نهار" in name || "ناهار" in name -> LUNCH
            "افطار" in name -> EFTAR
            "شام" in name -> DINNER
            else -> OTHER
        }

        fun getMealTypeById(id: Int): MealType {
            return when (id) {
                2 -> SAHAR
                3 -> BREAKFAST
                1 -> LUNCH
                4 -> EFTAR
                5 -> DINNER
                else -> OTHER
            }
        }
    }
}