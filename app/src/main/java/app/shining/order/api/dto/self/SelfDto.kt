package app.shining.order.api.dto.self

import app.shining.order.domain.model.Self
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SelfDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
//    @SerialName("type") val type: String
)

fun SelfDto.toSelf(): Self {
    return Self(
        id = id,
        name = name,
    )
}