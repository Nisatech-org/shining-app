package app.shining.dailysell.domain.repository

import app.shining.dailysell.domain.model.DailySellProgram
import app.shining.dailysell.domain.model.UserDailySales

interface DailySellRepository {

    suspend fun getDailySellProgram(): Result<DailySellProgram>

    suspend fun getUserDailySales(): Result<UserDailySales>

    suspend fun orderDailySellItem(
        reserveId: Int
    ): Result<Unit>
}