package app.shining.domain.repository

import app.shining.domain.api.DomainApi
import app.shining.domain.model.ShiningUser

class DomainRepositoryImplementation(
    private val domainApi: DomainApi,
) : DomainRepository {

    var user: ShiningUser? = null

    override suspend fun checkTokenValidation(): Result<Unit> {
        val result = domainApi.pingServer()
        return if (result.isSuccessful) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("توکن زایید!"))
        }
    }
}