package app.shining.domain.usecase

import app.shining.domain.repository.DomainRepository

class CheckTokenValidationUseCase(
    val repository: DomainRepository,
) {

    suspend operator fun invoke(): Result<Unit> {
        return repository.checkTokenValidation()
    }
}