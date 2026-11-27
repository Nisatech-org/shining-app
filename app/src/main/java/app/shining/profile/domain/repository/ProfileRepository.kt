package app.shining.profile.domain.repository

import app.shining.profile.domain.model.SamadNurtureProfile

interface ProfileRepository {

    suspend fun getNurtureProfile(): Result<SamadNurtureProfile>
}