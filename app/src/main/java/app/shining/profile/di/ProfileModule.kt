package app.shining.profile.di

import app.shining.domain.usecase.CheckTokenValidationUseCase
import app.shining.profile.api.ProfileApi
import app.shining.profile.domain.repository.ProfileRepository
import app.shining.profile.domain.repository.ProfileRepositoryImplementation
import app.shining.profile.domain.usecase.GetNurtureProfile
import app.shining.profile.ui.viewmodel.ProfileViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

val profileModule = module {

    single { get<Retrofit.Builder>().build().create(ProfileApi::class.java) }

    single<ProfileRepository> { ProfileRepositoryImplementation(get()) }

    factory { CheckTokenValidationUseCase(get()) }

    factory { GetNurtureProfile(get()) }

    viewModel {
        ProfileViewModel(get(), get(), get())
    }
}