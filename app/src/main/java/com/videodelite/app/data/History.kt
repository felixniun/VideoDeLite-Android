package com.videodelite.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Compression history, the Room counterpart of the desktop's SQLite history. */
@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val codec: String,
    val quality: String,
    val sourceSizeBytes: Long,
    val outputSizeBytes: Long,
    val width: Int,
    val height: Int,
    val videoDurationMs: Long,
    val compressDurationMs: Long,
    /** success | failed */
    val status: String,
    val error: String?,
    val outputName: String?,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: HistoryEntry): Long

    @Query("SELECT * FROM history ORDER BY id DESC")
    fun observeAll(): Flow<List<HistoryEntry>>

    @Query("DELETE FROM history")
    suspend fun clearAll()

    @Delete
    suspend fun delete(entry: HistoryEntry)
}

@Database(entities = [HistoryEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "videodelite.db",
                ).build().also { instance = it }
            }
    }
}
