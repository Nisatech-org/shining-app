package app.shining.reserveinfo.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ReservationInfo(
    val remainCredit: Long,
    val dayInfoList: List<ReservationDayInfo>,
) {
    operator fun plus(other: ReservationInfo): ReservationInfo {
        return ReservationInfo(
            remainCredit = remainCredit,
            dayInfoList = dayInfoList + other.dayInfoList
        )
    }
}
