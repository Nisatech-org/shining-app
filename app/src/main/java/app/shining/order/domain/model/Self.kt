package app.shining.order.domain.model

data class Self(
    val id: Int,
    val name: String,
    /** Samad's capacity for the cafeteria, if it sent one. A fixed number, not what's left. */
    val capacity: Int? = null,
)

/**
 * The capacities to show in Reserve, by self id: every cafeteria's but سلف مرکزی's
 * ([CENTRAL_SELF_IDS]), which isn't shown. Missing or zero capacities are left out.
 */
fun List<Self>.shownCapacities(): Map<Int, Int> =
    filter { it.id !in CENTRAL_SELF_IDS }
        .mapNotNull { self -> self.capacity?.takeIf { it > 0 }?.let { self.id to it } }
        .toMap()
