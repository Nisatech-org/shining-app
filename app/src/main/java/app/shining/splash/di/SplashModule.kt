package app.shining.splash.di

import app.shining.splash.ui.viewmodel.SplashScreenViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val splashModule = module {

    viewModel<SplashScreenViewModel> {
        SplashScreenViewModel(get())
    }

}