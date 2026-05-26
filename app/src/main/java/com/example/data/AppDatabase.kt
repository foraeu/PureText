package com.example.data

import android.content.Context
import androidx.room.*
import com.example.model.RecentFile
import com.example.model.UserSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {
    @Query("SELECT * FROM recent_files ORDER BY lastOpened DESC")
    fun getRecentFiles(): Flow<List<RecentFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentFile(file: RecentFile)

    @Query("UPDATE recent_files SET scrollIndex = :index, scrollOffset = :offset, lastOpened = :timestamp WHERE uriString = :uri")
    suspend fun updateScrollPosition(uri: String, index: Int, offset: Int, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM recent_files WHERE uriString = :uri LIMIT 1")
    suspend fun getRecentFileByUri(uri: String): RecentFile?

    @Delete
    suspend fun deleteRecentFile(file: RecentFile)

    @Query("DELETE FROM recent_files")
    suspend fun clearAllRecents()
}

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    fun getUserSettings(): Flow<UserSettings?>

    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    suspend fun getUserSettingsSync(): UserSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserSettings(settings: UserSettings)
}

@Database(entities = [RecentFile::class, UserSettings::class], version = 1, exportSchema = false)
abstract class PureTextDatabase : RoomDatabase() {
    abstract fun recentFileDao(): RecentFileDao
    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: PureTextDatabase? = null

        fun getDatabase(context: Context): PureTextDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PureTextDatabase::class.java,
                    "puretext_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class PureTextRepository(private val db: PureTextDatabase) {
    private val recentFileDao = db.recentFileDao()
    private val userSettingsDao = db.userSettingsDao()

    val recentFiles: Flow<List<RecentFile>> = recentFileDao.getRecentFiles()
    val userSettings: Flow<UserSettings?> = userSettingsDao.getUserSettings()

    suspend fun insertRecentFile(file: RecentFile) {
        recentFileDao.insertRecentFile(file)
    }

    suspend fun updateScrollPosition(uri: String, index: Int, offset: Int) {
        recentFileDao.updateScrollPosition(uri, index, offset)
    }

    suspend fun getRecentFileByUri(uri: String): RecentFile? {
        return recentFileDao.getRecentFileByUri(uri)
    }

    suspend fun deleteRecentFile(file: RecentFile) {
        recentFileDao.deleteRecentFile(file)
    }

    suspend fun clearAllRecents() {
        recentFileDao.clearAllRecents()
    }

    suspend fun getUserSettingsSync(): UserSettings {
        return userSettingsDao.getUserSettingsSync() ?: UserSettings().also {
            userSettingsDao.saveUserSettings(it)
        }
    }

    suspend fun saveUserSettings(settings: UserSettings) {
        userSettingsDao.saveUserSettings(settings)
    }
}
