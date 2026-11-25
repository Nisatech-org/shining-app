package app.shining.order.domain.model

data class WeekReservableProgram(
    val userId: Int,
    val selfWeekProgram: List<SelfDayReservableProgram>,
)

/** Appends [other]'s days to this one's. Used for a single self's two weeks. */
fun WeekReservableProgram?.merge(other: WeekReservableProgram?): WeekReservableProgram {
    if (this == null && other == null) {
        return WeekReservableProgram(userId = 0, selfWeekProgram = emptyList())
    }
    if (this == null) return other!!
    if (other == null) return this

    return WeekReservableProgram(
        userId = this.userId,
        selfWeekProgram = this.selfWeekProgram + other.selfWeekProgram
    )
}

/**
 * Combines several programs (every self, both weeks) into one with a single entry per day that
 * holds the foods of every self. Days are in date order, meals in [MealType] order and, within a
 * meal, the cheapest food comes first so prices are easy to compare.
 */
fun List<WeekReservableProgram>.mergeByDay(): WeekReservableProgram {
    val days = flatMap { it.selfWeekProgram }
        .filter { it.date.isNotEmpty() }
        .groupBy { it.date }
        .toSortedMap()
        .map { (date, programs) ->
            val first = programs.first()
            SelfDayReservableProgram(
                dayName = first.dayName,
                date = date,
                farsiDayName = first.farsiDayName,
                reserveInfoList = programs
                    .flatMap { it.reserveInfoList.entries }
                    .groupBy(keySelector = { it.key }, valueTransform = { it.value })
                    .toSortedMap()
                    .mapValues { (_, foods) ->
                        foods.flatten()
                            .distinctBy { Triple(it.programId, it.mealTypeId, it.foodTypeId) }
                            .sortedBy { it.price }
                    },
            )
        }
    return WeekReservableProgram(
        userId = firstOrNull()?.userId ?: 0,
        selfWeekProgram = days,
    )
}
