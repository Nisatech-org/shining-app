package app.shining.dailysell.api.dto

import app.shining.dailysell.domain.model.DailySellProgram
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DailySellProgramDto(
    @SerialName("dailySellPrograms") val dailySellItemsDto: List<DailySellItemDto> = emptyList()
)

fun DailySellProgramDto.toDailySellProgram(): DailySellProgram {
    return DailySellProgram(
        dailySaleInfoList = dailySellItemsDto.map { it.toDailySellItem() }
    )
}