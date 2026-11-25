package app.shining.credit.api.dto

import app.shining.credit.domain.model.RedirectLoginAsToken
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RedirectLoginAsTokenDto(
    @SerialName("loginAsToken") val loginAsToken: String
//    val changeProfilePicturePermission: Boolean,
)

fun RedirectLoginAsTokenDto.toRedirectLoginAsToken(): RedirectLoginAsToken {
    return RedirectLoginAsToken(
        loginAsToken = loginAsToken
    )
}