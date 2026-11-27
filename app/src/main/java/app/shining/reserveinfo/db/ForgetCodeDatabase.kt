package app.shining.reserveinfo.db

import androidx.room.Database
import androidx.room.RoomDatabase
import app.shining.reserveinfo.db.dao.ForgetCodeDao
import app.shining.reserveinfo.db.model.ForgetCodeEntity

@Database(entities = [ForgetCodeEntity::class], version = ForgetCodeDatabase.DATABASE_VERSION)
abstract class ForgetCodeDatabase : RoomDatabase() {

    companion object {
        const val DATABASE_NAME = "forget_code_database"
        const val DATABASE_VERSION = 2
        const val DATABASE_TABLE_NAME = "forget_code_table"
    }

    abstract fun forgetCodeDao(): ForgetCodeDao
}