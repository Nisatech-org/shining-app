package app.shining.autoreserve.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.shining.autoreserve.domain.model.AutoReserveDays
import app.shining.domain.util.DaysOfWeek

@Entity(tableName = "auto_reserve_days")
data class AutoReserveDaysEntity(
    @PrimaryKey val accountId: String,
    val days: List<String>
)

fun AutoReserveDaysEntity.toAutoReserveDays() = AutoReserveDays(
    days = days.map { DaysOfWeek.fromName(it) }
)
