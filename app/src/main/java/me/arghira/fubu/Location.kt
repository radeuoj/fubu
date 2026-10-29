package me.arghira.fubu

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "locations")
data class Location(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val time: Long,
)

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations")
    suspend fun getAll(): List<Location>

    @Insert
    suspend fun insert(location: Location)
}