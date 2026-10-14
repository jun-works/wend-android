package app.wend.sea.data.local

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): WendDatabase =
        Room.databaseBuilder(context, WendDatabase::class.java, "wend.db")
            .addMigrations(WendDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun mediaLocationDao(database: WendDatabase): MediaLocationDao = database.mediaLocationDao()

    @Provides
    fun tripDao(database: WendDatabase): TripDao = database.tripDao()
}
