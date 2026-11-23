package app.shining.reserveinfo.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.shining.reserveinfo.db.model.ForgetCodeEntity
import app.shining.reserveinfo.domain.model.ForgetCode

@Dao
interface ForgetCodeDao {

    @Query("DELETE FROM forget_code_table WHERE timestamp < :before")
    suspend fun deleteOldEntries(before: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForgetCode(reservation: ForgetCodeEntity)

    @Query("SELECT * FROM forget_code_table WHERE reserveId = :reserveId LIMIT 1")
    suspend fun getForgetCodeByReserveId(reserveId: Int): ForgetCode?

    @Query("DELETE FROM forget_code_table")
    suspend fun deleteAllForgetCodes()

    @Query("SELECT * FROM forget_code_table")
    suspend fun getAllForgetCodes(): List<ForgetCodeEntity>

    @Query("SELECT reserveId FROM forget_code_table WHERE accountId = :accountId")
    suspend fun getReserveIds(accountId: String): List<Int>

    @Query("DELETE FROM forget_code_table WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)
}