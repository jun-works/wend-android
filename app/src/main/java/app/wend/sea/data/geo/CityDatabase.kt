package app.wend.sea.data.geo

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 도시 한 곳(GeoNames 인구 1천 이상). 위도·경도는 1만 배 정수, [cell] 은 [CityGrid] 칸 번호.
 * 이름이 없는 언어는 null.
 */
@Entity(tableName = "city", indices = [Index("cell")])
data class CityEntity(
    @PrimaryKey val id: Long,
    val lat: Int,
    val lon: Int,
    val cell: Int,
    val cc: String,
    val pop: Int,
    val en: String,
    val ko: String?,
    val ja: String?,
    @ColumnInfo(name = "zh_hans") val zhHans: String?,
    @ColumnInfo(name = "zh_hant") val zhHant: String?,
)

@Dao
interface CityDao {
    @Query("SELECT * FROM city WHERE cell IN (:cells)")
    suspend fun inCells(cells: List<Int>): List<CityEntity>

    @Query("SELECT * FROM city WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<CityEntity>
}

/**
 * 앱에 넣어 둔 도시 목록(assets/geo/cities.db, tools/geo/build_cities_db.py 로 만듦). 읽기만 한다.
 * 데이터를 바꾸면 [version] 을 올린다 — 기기에 복사해 둔 예전 파일을 버리고 새로 복사한다.
 */
@Database(entities = [CityEntity::class], version = 1, exportSchema = false)
abstract class CityDatabase : RoomDatabase() {
    abstract fun cityDao(): CityDao
}

@Module
@InstallIn(SingletonComponent::class)
object CityDatabaseModule {
    @Provides
    @Singleton
    fun cityDatabase(@ApplicationContext context: Context): CityDatabase =
        Room.databaseBuilder(context, CityDatabase::class.java, "cities.db")
            .createFromAsset("geo/cities.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun cityDao(database: CityDatabase): CityDao = database.cityDao()
}
