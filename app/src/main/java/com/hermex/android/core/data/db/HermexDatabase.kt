package com.hermex.android.core.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "sessions",
    primaryKeys = ["serverId", "key"],
    indices = [Index(value = ["serverId", "lastActive"])],
)
data class SessionEntity(
    val serverId: String,
    val key: String,
    val profile: String,
    val title: String?,
    val preview: String,
    val messageCount: Int,
    val lastActive: Double,
    val pinned: Boolean,
    val archived: Boolean,
    val unread: Boolean,
    val isActive: Boolean,
    val model: String?,
    val parentKey: String?,
)

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE serverId = :serverId AND archived = 0 ORDER BY lastActive DESC")
    fun observe(serverId: String): Flow<List<SessionEntity>>

    @Upsert
    suspend fun upsert(rows: List<SessionEntity>)

    @Query("DELETE FROM sessions WHERE serverId = :serverId")
    suspend fun clear(serverId: String)

    @Query("DELETE FROM sessions")
    suspend fun clearAll()

    @Transaction
    suspend fun replace(serverId: String, rows: List<SessionEntity>) {
        clear(serverId)
        upsert(rows)
    }
}

@Database(entities = [SessionEntity::class], version = 1, exportSchema = false)
abstract class HermexDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao

    companion object {
        fun build(context: Context): HermexDatabase =
            Room.databaseBuilder(context, HermexDatabase::class.java, "hermex.db")
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
