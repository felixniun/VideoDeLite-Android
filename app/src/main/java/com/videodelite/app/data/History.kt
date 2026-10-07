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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    /** simple | professional — which mode produced this entry. */
    val mode: String = "simple",
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

@Database(entities = [HistoryEntry::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /**
         * v1 → v2 adds `mode`; existing rows default to "simple" so a user's
         * history survives the upgrade.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE history ADD COLUMN mode TEXT NOT NULL DEFAULT 'simple'"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "videodelite.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
