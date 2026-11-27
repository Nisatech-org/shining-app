package app.shining.profile.domain.usecase

import app.shining.profile.domain.model.SamadNurtureProfile
import app.shining.profile.domain.repository.ProfileRepository

class GetNurtureProfile(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(
    ): Result<SamadNurtureProfile> {
        return repository.getNurtureProfile()
    }
}