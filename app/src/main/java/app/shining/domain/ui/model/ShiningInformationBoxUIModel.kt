package app.shining.domain.ui.model

enum class ShiningInformationBoxState {
    SUCCESS,
    FAILED
}

data class ShiningInformationBoxUIModel(
    val state: ShiningInformationBoxState,
    val message: String,
)

fun Boolean.toShiningInformationBoxState() =
    if (this) ShiningInformationBoxState.SUCCESS else ShiningInformationBoxState.FAILED