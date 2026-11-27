package app.shining.domain.repository

interface DomainRepository {
    suspend fun checkTokenValidation(): Result<Unit>
}