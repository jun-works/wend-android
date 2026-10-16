package app.wend.sea.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MediaLocationEntity::class, TripEntity::class, TripMediaEntity::class, UserNameEntity::class, ExcludedMediaEntity::class, MediaFeatureEntity::class, BestShotEntity::class],
    version = 12,
    exportSchema = false,
)
abstract class WendDatabase : RoomDatabase() {
    abstract fun mediaLocationDao(): MediaLocationDao
    abstract fun tripDao(): TripDao
    abstract fun mediaFeatureDao(): MediaFeatureDao

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

        private const val CREATE_MEDIA_FEATURE =
            "CREATE TABLE IF NOT EXISTS `media_feature` (`mediaId` INTEGER NOT NULL, `modified` INTEGER NOT NULL, `brightness` REAL NOT NULL, `darkClip` REAL NOT NULL, `brightClip` REAL NOT NULL, `hash` INTEGER NOT NULL, `peak` INTEGER NOT NULL, PRIMARY KEY(`mediaId`))"

        /** 6 → 7: 베스트샷 장면 묶기·사진 정리에 쓰는 사진 값 */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_MEDIA_FEATURE)
            }
        }

        /** 7 → 8: 좋은 사진 판정을 빼면서 화질·얼굴 값을 지운다(개발 빌드에만 있던 표 — 다시 계산한다) */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `media_feature`")
                db.execSQL(CREATE_MEDIA_FEATURE)
            }
        }

        /** 8 → 9: 사용자가 고른 베스트샷 */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `best_shot` (`mediaId` INTEGER NOT NULL, PRIMARY KEY(`mediaId`))")
            }
        }

        /** 9 → 10: 너무 어두운 사진에서 달·불빛을 가려내는 가장 밝은 부분 값 — 사진 값을 다시 계산한다(베스트샷은 그대로) */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `media_feature`")
                db.execSQL(CREATE_MEDIA_FEATURE)
            }
        }

        /** 10 → 11: 빠진 사진에서 직접 넣은 사진 */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `manual_media` (`mediaId` INTEGER NOT NULL, `video` INTEGER NOT NULL, `anchorId` INTEGER NOT NULL, `anchorVideo` INTEGER NOT NULL, PRIMARY KEY(`mediaId`, `video`))")
            }
        }

        /** 11 → 12: 처음부터 다시 찾기는 초기화 — 넣은 사진을 따로 기억하지 않는다(개발 빌드에만 있던 표) */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `manual_media`")
            }
        }
    }
}
