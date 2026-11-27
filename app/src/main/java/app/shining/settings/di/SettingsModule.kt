package app.shining.settings.di

import app.shining.settings.ui.viewmodel.SettingsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val settingsModule = module {

    viewModel {
        SettingsViewModel(get(), get(), get())
    }

}
