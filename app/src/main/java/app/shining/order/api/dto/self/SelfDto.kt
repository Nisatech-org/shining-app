package app.shining.order.api.dto.self

import app.shining.order.domain.model.Self
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SelfDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    /** How many the cafeteria serves, as Samad's cafeteria list says (e.g. کاله 600, سلف 7000). */
    @SerialName("capacity") val capacity: Int? = null,
//    @SerialName("type") val type: String
)

fun SelfDto.toSelf(): Self {
    return Self(
        id = id,
        name = name,
        capacity = capacity,
    )
}
