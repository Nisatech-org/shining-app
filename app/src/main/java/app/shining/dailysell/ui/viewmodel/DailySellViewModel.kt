package app.shining.dailysell.ui.viewmodel

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import app.shining.credit.domain.usecase.GetRedirectLoginAsTokenUseCase
import app.shining.dailysell.domain.model.DailySellProgram
import app.shining.dailysell.domain.model.UserDailySales
import app.shining.dailysell.domain.usecase.GetDailySellProgramUseCase
import app.shining.dailysell.domain.usecase.GetUserDailySalesUseCase
import app.shining.dailysell.domain.usecase.OrderDailySellFoodUseCase
import app.shining.dailysell.ui.model.DailySellScreenUIModel
import app.shining.dailysell.ui.model.toDailySellScreenUIModel
import app.shining.domain.model.LoadableData
import app.shining.domain.model.NavigationEvent
import app.shining.domain.ui.model.ShiningInformationBoxState
import app.shining.domain.ui.model.ShiningInformationBoxUIModel
import app.shining.domain.ui.viewmodel.ShiningViewModel
import app.shining.reserveinfo.domain.usecase.GetForgetCodeMapCacheUseCase
import app.shining.reserveinfo.domain.usecase.GetForgetCodeUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DailySellViewModel(
    private val orderDailySellFoodUseCase: OrderDailySellFoodUseCase,
    private val getUserDailySalesUseCase: GetUserDailySalesUseCase,
    private val getDailySellProgramUseCase: GetDailySellProgramUseCase,
    private val getForgetCodeUseCase: GetForgetCodeUseCase,
    private val getForgetCodeMapCacheUseCase: GetForgetCodeMapCacheUseCase,
    private val getRedirectLoginAsTokenUseCase: GetRedirectLoginAsTokenUseCase,
) : ShiningViewModel() {

    val dailySaleScreenUIModel =
        mutableStateOf<LoadableData<DailySellScreenUIModel>>(LoadableData.NotLoaded)
    private var _dailySaleInfo: DailySellProgram? = null
    private var _userDailySales: UserDailySales? = null
    val navBack: NavigationEvent = NavigationEvent()

    val informationBoxUIModel = mutableStateOf<ShiningInformationBoxUIModel?>(null)
    val showMessage = mutableStateOf(false)

    fun onLaunch() {
        getDailySellProgram()
    }

    fun onBackClick() {
        navBack.navigate()
    }


    fun onGetForgetCode(
        reserveId: Int,
        onFinish: () -> Unit,
    ) {
        viewModelScope.launch {
            getForgetCodeUseCase(reserveId = reserveId, dailySale = true).fold(
                onSuccess = { _ ->
                    updateDailySaleScreenUIModel()
                },
                onFailure = {
                    informationBoxUIModel.value = ShiningInformationBoxUIModel(
                        state = ShiningInformationBoxState.FAILED,
                        message = "در دریافت کد فراموشی غذای مورد نظر خطایی پیش اومده",
                    )

                    showInformationBox()
                }
            )
            onFinish()
        }

    }


    fun onIncreaseCreditClick(
        invokeIntent: (String) -> Unit,
    ) {
        viewModelScope.launch {

            getRedirectLoginAsTokenUseCase().fold(
                onSuccess = {
                    informationBoxUIModel.value = ShiningInformationBoxUIModel(
                        message = "در حال انتقال به صفحه افزایش اعتبار...",
                        state = ShiningInformationBoxState.SUCCESS
                    )

                    val url = Uri.Builder()
                        .scheme("https")  // Protocol (https, http, etc.)
                        .authority("setad.dining.sharif.edu")  // Base URL
                        .appendPath("j_security_check")  // Path segment (if any)
                        .appendQueryParameter("loginAsToken", it.loginAsToken)  // Add query params
                        .appendQueryParameter("redirect", "/nurture/user/credit/charge/view.rose")
                        .build()
                        .toString()

                    invokeIntent(url)
                },
                onFailure = {
                    informationBoxUIModel.value = ShiningInformationBoxUIModel(
                        message = it.message ?: "در افزایش اعتبار خطایی پیش آمده",
                        state = ShiningInformationBoxState.FAILED
                    )
                }
            )
            showInformationBox()
        }

    }

    fun onRefresh() {
        getDailySellProgram()
    }

    private fun getDailySellProgram() {

        dailySaleScreenUIModel.value = LoadableData.Loading
        viewModelScope.launch {
            getDailySellProgramUseCase().fold(
                onSuccess = {
                    _dailySaleInfo = it
                },
                onFailure = {
                    dailySaleScreenUIModel.value =
                        LoadableData.Failed(it.message ?: "در دریافت برنامه غذایی مشکلی پیش آمده.")
                }
            )

            getUserDailySalesUseCase().fold(
                onSuccess = {
                    _userDailySales = it
                },
                onFailure = {

                }
            )
            updateDailySaleScreenUIModel()
        }

    }

    fun onOrderDailySale(id: Int, onFinish: () -> Unit) {
        viewModelScope.launch {
            orderDailySellFoodUseCase(reserveId = id).fold(
                onSuccess = {
                    updateDailySaleScreenUIModel()
                },
                onFailure = {
                    informationBoxUIModel.value = ShiningInformationBoxUIModel(
                        state = ShiningInformationBoxState.FAILED,
                        message = "در ثبت سفارش خطایی پیش آمده",
                    )
                    showInformationBox()
                }
            )
            onFinish()
        }
    }

    private fun showInformationBox() {
        showMessage.value = true
        viewModelScope.launch {
            delay(3000)
            showMessage.value = false
        }
    }


    private suspend fun updateDailySaleScreenUIModel() {
        _dailySaleInfo?.let {
            dailySaleScreenUIModel.value = LoadableData.Loaded(
                it.toDailySellScreenUIModel(
                    forgetCodeMap = getForgetCodeMapCacheUseCase(),
                    userDailySales = _userDailySales
                )
            )
        }
    }
}
