package app.shining.order.domain.model

/**
 * سلف مرکزی: the boys' (1) and girls' (2) central self. Samad gives the boys' one room for 7000 a
 * day, so its foods never run out. کاله (600), کلانا and the others hold a few hundred and really do.
 */
val CENTRAL_SELF_IDS: Set<Int> = setOf(1, 2)

/**
 * True if Samad's answer to a reservation says the food or the cafeteria is full: its capacity or
 * reservation limit was reached. Not a credit ceiling (سقف اعتبار) or a passed deadline (مهلت).
 */
internal fun isCapacityMessage(message: String): Boolean {
    val text = message.lowercase()
    if ("اعتبار" in text || "مهلت" in text) return false
    return listOf("ظرفیت", "تکمیل", "سقف", "حداکثر", "capacity", "limit").any { it in text }
}

/**
 * What to tell the user when Samad refuses to reserve a food at [selfId]. When Samad says a سلف
 * مرکزی food is full, it's Samad's own error, since that self can't fill up: the user is told so
 * and asked to try again instead of giving up on the food. Everywhere else a full food really is
 * full, so Samad's [message] is shown as it is.
 */
fun reserveErrorMessage(selfId: Int, message: String): String =
    if (selfId in CENTRAL_SELF_IDS && isCapacityMessage(message)) {
        "خطای سماد: «$message». سلف مرکزی پر نمی‌شه؛ چند لحظه دیگه دوباره رزرو کن."
    } else {
        message
    }
