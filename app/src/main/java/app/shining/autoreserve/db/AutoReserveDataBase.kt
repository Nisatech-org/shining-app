package app.shining.autoreserve.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.shining.autoreserve.db.converter.DaysConverter
import app.shining.autoreserve.db.dao.AutoReserveDao
import app.shining.autoreserve.db.model.AutoReserveDaysEntity
import app.shining.autoreserve.db.model.AutoReserveFoodEntity

@Database(
    entities = [AutoReserveFoodEntity::class, AutoReserveDaysEntity::class],
    version = AutoReserveDataBase.DATABASE_VERSION,
)
@TypeConverters(DaysConverter::class)
abstract class AutoReserveDataBase : RoomDatabase() {
    companion object {
        const val DATABASE_NAME = "auto_reserve_db"
        const val DATABASE_VERSION = 2

        /** Bundled default food list, copied into the template rows on first creation. */
        const val SEED_ASSET = "database/food_priority.db"
    }

    abstract fun foodDao(): AutoReserveDao
}
