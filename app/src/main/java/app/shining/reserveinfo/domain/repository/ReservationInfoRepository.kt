package app.shining.reserveinfo.domain.repository

import app.shining.account.domain.repository.AccountScopedData
import app.shining.reserveinfo.domain.model.ForgetCode
import app.shining.reserveinfo.domain.model.ReservationInfo
import app.shining.reserveinfo.domain.model.SavedReservations

interface ReservationInfoRepository : AccountScopedData {
    suspend fun getReservationInfo(
        weekStartDate: String = "",
    ): Result<ReservationInfo>

    /** Username of the account whose reservations Samad returns right now. */
    fun activeUsername(): String?

    /** Keeps [info] on the device as [username]'s latest reservations. */
    fun saveReservations(username: String, info: ReservationInfo)

    /**
     * The active account's saved reservations without the days that have already passed, or null
     * if nothing was saved yet.
     */
    fun getSavedReservations(): SavedReservations?

    /**
     * The forget code for [reserveId]. Samad issues one code per reservation and it never changes,
     * so it is fetched once and then kept on the device.
     */
    suspend fun getForgetCode(
        reserveId: Int,
        dailySale: Boolean = false,
    ): Result<ForgetCode>

    /** Every forget code fetched so far, by reservation id. Read from the device after a restart. */
    suspend fun getMapCache(): Map<Int, String>
}