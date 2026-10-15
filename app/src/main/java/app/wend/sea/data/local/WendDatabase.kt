package app.wend.sea.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MediaLocationEntity::class, TripEntity::class, TripMediaEntity::class, UserNameEntity::class, ExcludedMediaEntity::class],
    version = 6,
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

        /** 2 → 3: 여행 이름이 될 도시 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `trip` ADD COLUMN `cityId` INTEGER")
            }
        }

        /** 3 → 4: 도시 사진 비율과 나라. 이름 규칙이 바뀌어 도시를 다시 찾게 비운다 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `trip` ADD COLUMN `cityShare` REAL")
                db.execSQL("ALTER TABLE `trip` ADD COLUMN `countryCode` TEXT")
                db.execSQL("UPDATE `trip` SET `cityId` = NULL")
            }
        }

        /** 4 → 5: 사용자가 붙인 여행·장소 이름 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `user_name` (`mediaId` INTEGER NOT NULL, `video` INTEGER NOT NULL, `kind` INTEGER NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`mediaId`, `video`, `kind`))")
            }
        }

        /** 5 → 6: 사용자가 여행에서 뺀 사진 */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `excluded_media` (`mediaId` INTEGER NOT NULL, `video` INTEGER NOT NULL, PRIMARY KEY(`mediaId`, `video`))")
            }
        }
    }
}
