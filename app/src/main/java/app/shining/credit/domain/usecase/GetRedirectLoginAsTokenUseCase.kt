package app.shining.credit.domain.usecase

import app.shining.credit.domain.model.RedirectLoginAsToken
import app.shining.credit.domain.repository.CreditRepository

class GetRedirectLoginAsTokenUseCase(
    private val creditRepository: CreditRepository
) {
    suspend operator fun invoke(): Result<RedirectLoginAsToken> {
        return creditRepository.getLoginAsToken()
    }
}