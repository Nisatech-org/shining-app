package app.shining.login.di

import app.shining.login.domain.model.LoginMode
import app.shining.login.ui.viewmodel.LoginScreenViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val loginModule = module {

    viewModel { (mode: LoginMode, username: String?) ->
        LoginScreenViewModel(get(), get(), mode, username, get())
    }
}
