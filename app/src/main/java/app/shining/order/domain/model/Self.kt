package app.shining.order.domain.model

data class Self(
    val id: Int,
    val name: String,
)

/**
 * The short name a cafeteria goes by in the app, the same for every user: Samad's names are long
 * and uneven ("مرکزی - سلف دانشجویان آقا", "کاله پردیس مرکزی(بیرون بر)"), and a cut one shows as a
 * bare "…". Both سلف مرکزی selfs are «سلف»; a dorm keeps «خوابگاه»; a bracketed note like
 * "(بیرون بر)" and the main campus are dropped, and the north campus is «شمالی».
 */
fun shortSelfName(name: String): String {
    val parts = name.split(" - ", limit = 2).map { it.trim() }
    if (parts.size == 2 && parts[0] == "مرکزی" && parts[1].startsWith("سلف")) return "سلف"
    val base = if (parts.size == 2) "${parts[0]} ${parts[1]}" else name
    return base
        .replace(Regex("""\s*\([^)]*\)\s*"""), " ")
        .replace("پردیس مرکزی", "")
        .replace("پردیس شمالی", "شمالی")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .ifEmpty { name }
}

/**
 * [foodName] without the vendor Samad puts in brackets at its end ("ساندویچ مرغ پستو (کاله)"),
 * when [placeName] (the short name shown next to it) already says it, so it isn't shown twice.
 * A bracket that says something else stays: a کاله dish sold at «سلف» keeps its "(کاله)", and
 * with no [placeName] (a single cafeteria, no tag) the name is left whole.
 */
fun foodNameWithoutPlace(foodName: String, placeName: String?): String {
    if (placeName.isNullOrBlank()) return foodName
    val match = Regex("""^(.*\S)\s*\(([^()]+)\)\s*$""").find(foodName) ?: return foodName
    val (food, bracket) = match.destructured
    val inBracket = bracket.forMatching()
    return if (inBracket.isNotEmpty() && inBracket in placeName.forMatching()) food.trim() else foodName
}

/** Spacing, half-spaces and Arabic letter forms differ between Samad's names ("کلین فود", "کلین‌فود"). */
private fun String.forMatching(): String =
    replace('ي', 'ی').replace('ك', 'ک').filterNot { it.isWhitespace() || it == '‌' }
