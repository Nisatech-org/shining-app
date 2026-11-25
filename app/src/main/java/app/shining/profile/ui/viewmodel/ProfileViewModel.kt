package app.shining.profile.ui.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import app.shining.account.domain.repository.AccountManager
import app.shining.credit.domain.creditChargeUrl
import app.shining.credit.domain.usecase.GetRedirectLoginAsTokenUseCase
import app.shining.domain.model.LoadableData
import app.shining.domain.model.NavigationEvent
import app.shining.domain.ui.model.ShiningInformationBoxState
import app.shining.domain.ui.model.ShiningInformationBoxUIModel
import app.shining.domain.ui.viewmodel.ShiningViewModel
import app.shining.profile.domain.usecase.GetNurtureProfile
import app.shining.profile.ui.model.ProfileScreenUIModel
import app.shining.profile.ui.model.toProfileScreenUIModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val getNurtureProfile: GetNurtureProfile,
    private val accountManager: AccountManager,
    private val getRedirectLoginAsTokenUseCase: GetRedirectLoginAsTokenUseCase,
) : ShiningViewModel() {

    val showMessage = mutableStateOf(false)
    val informationBoxUIModel = mutableStateOf<ShiningInformationBoxUIModel?>(null)
    private var hideMessageJob: Job? = null

    val profileScreenUIModel =
        mutableStateOf<LoadableData<ProfileScreenUIModel>>(LoadableData.NotLoaded)
    val navBack = NavigationEvent()

    fun onLaunch() {
        loadProfile()
    }

    fun onBackClick() {
        navBack.navigate()
    }

    fun onIncreaseCreditClick(invokeIntent: (String) -> Unit) {
        viewModelScope.launch {
            getRedirectLoginAsTokenUseCase().fold(
                onSuccess = {
                    informationBoxUIModel.value = ShiningInformationBoxUIModel(
                        message = "در حال انتقال به صفحه افزایش اعتبار...",
                        state = ShiningInformationBoxState.SUCCESS
                    )
                    invokeIntent(creditChargeUrl(it.loginAsToken))
                },
                onFailure = {
                    informationBoxUIModel.value = ShiningInformationBoxUIModel(
                        message = it.message ?: "در افزایش اعتبار خطایی پیش آمده",
                        state = ShiningInformationBoxState.FAILED
                    )
                }
            )
            showMessage.value = true
            hideMessageJob?.cancel()
            hideMessageJob = viewModelScope.launch {
                delay(3000L)
                showMessage.value = false
            }
        }
    }

    fun onRefresh() {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            profileScreenUIModel.value = LoadableData.Loading
            getNurtureProfile().fold(
                onSuccess = {
                    // Keeps the account's name in the switcher up to date.
                    accountManager.updateActiveDisplayName("${it.firstName} ${it.lastName}".trim())
                    profileScreenUIModel.value = LoadableData.Loaded(it.toProfileScreenUIModel())
                },
                onFailure = {
                    profileScreenUIModel.value = LoadableData.Failed(
                        it.message ?: "در دریافت پروفایل مشکلی پیش آمده است."
                    )
                }
            )
        }

    }
}
