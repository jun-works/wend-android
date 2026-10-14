package app.wend.sea.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MediaLocationEntity::class, TripEntity::class, TripMediaEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class WendDatabase : RoomDatabase() {
    abstract fun mediaLocationDao(): MediaLocationDao
    abstract fun tripDao(): TripDao

    companion object {
        /** 1 → 2: 여행 결과 테이블 추가(위치 저장값은 그대로) */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `trip` (`number` INTEGER NOT NULL, `start` INTEGER NOT NULL, `end` INTEGER NOT NULL, `mediaCount` INTEGER NOT NULL, PRIMARY KEY(`number`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `trip_media` (`tripNumber` INTEGER NOT NULL, `mediaId` INTEGER NOT NULL, `video` INTEGER NOT NULL, PRIMARY KEY(`tripNumber`, `mediaId`, `video`))")
            }
        }
    }
}
