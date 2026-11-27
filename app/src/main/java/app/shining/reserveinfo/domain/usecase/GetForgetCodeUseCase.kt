package app.shining.reserveinfo.domain.usecase

import app.shining.reserveinfo.domain.model.ForgetCode
import app.shining.reserveinfo.domain.repository.ReservationInfoRepository

class GetForgetCodeUseCase(
    private val repository: ReservationInfoRepository,
) {
    suspend operator fun invoke(
        reserveId: Int,
        dailySale: Boolean = false,
    ): Result<ForgetCode> {
        return repository.getForgetCode(
            reserveId = reserveId,
            dailySale = dailySale
        )
    }
}