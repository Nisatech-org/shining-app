package app.shining.dailysell.domain.usecase

import app.shining.dailysell.domain.model.DailySellProgram
import app.shining.dailysell.domain.repository.DailySellRepository

class GetDailySellProgramUseCase(
    private val repository: DailySellRepository
) {
    suspend operator fun invoke(): Result<DailySellProgram> {
        return repository.getDailySellProgram()
    }
}