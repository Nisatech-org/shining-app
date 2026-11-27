package app.shining.reserveinfo.domain.usecase

import app.shining.reserveinfo.domain.repository.ReservationInfoRepository

class GetForgetCodeMapCacheUseCase(
    private val repository: ReservationInfoRepository
) {

    suspend operator fun invoke(): Map<Int, String> {
        return repository.getMapCache()
    }
}