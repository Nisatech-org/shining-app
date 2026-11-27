package app.shining.credit.domain.repository

import app.shining.credit.domain.model.RedirectLoginAsToken

interface CreditRepository {

    suspend fun getLoginAsToken(): Result<RedirectLoginAsToken>
}