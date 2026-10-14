package app.wend.sea.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MediaLocationEntity::class], version = 1, exportSchema = false)
abstract class WendDatabase : RoomDatabase() {
    abstract fun mediaLocationDao(): MediaLocationDao
}
