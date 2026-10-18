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
    indices = [Index(value = ["serverId", "archived", "lastActive"]), Index(value = ["serverId", "profile"])],
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
    val cwd: String?,
)

@Entity(
    tableName = "transcript_rows",
    primaryKeys = ["serverId", "storedKey", "rowId"],
    indices = [Index(value = ["serverId", "storedKey", "position"])],
)
data class TranscriptRowEntity(
    val serverId: String,
    val storedKey: String,
    val rowId: String,
    val position: Int,
    val json: String,
)

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE serverId = :serverId AND archived = :archived ORDER BY lastActive DESC")
    fun observe(serverId: String, archived: Boolean): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE serverId = :serverId AND archived = :archived AND profile = :profile ORDER BY lastActive DESC")
    fun observeProfile(serverId: String, profile: String, archived: Boolean): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE serverId = :serverId AND key = :key")
    suspend fun get(serverId: String, key: String): SessionEntity?

    @Upsert
    suspend fun upsert(rows: List<SessionEntity>)

    @Query("DELETE FROM sessions WHERE serverId = :serverId AND key = :key")
    suspend fun delete(serverId: String, key: String)

    @Query("DELETE FROM sessions WHERE serverId = :serverId AND archived = :archived")
    suspend fun clear(serverId: String, archived: Boolean)

    @Query("DELETE FROM sessions WHERE serverId = :serverId AND archived = :archived AND profile = :profile")
    suspend fun clearProfile(serverId: String, profile: String, archived: Boolean)

    @Query("DELETE FROM sessions")
    suspend fun clearAll()

    @Transaction
    suspend fun replace(serverId: String, profile: String?, archived: Boolean, rows: List<SessionEntity>) {
        if (profile == null) clear(serverId, archived) else clearProfile(serverId, profile, archived)
        upsert(rows)
    }
}

@Dao
interface TranscriptDao {
    @Query("SELECT * FROM transcript_rows WHERE serverId = :serverId AND storedKey = :storedKey ORDER BY position")
    suspend fun rows(serverId: String, storedKey: String): List<TranscriptRowEntity>

    @Upsert
    suspend fun upsert(rows: List<TranscriptRowEntity>)

    @Query("DELETE FROM transcript_rows WHERE serverId = :serverId AND storedKey = :storedKey")
    suspend fun clear(serverId: String, storedKey: String)

    @Query("DELETE FROM transcript_rows")
    suspend fun clearAll()

    @Transaction
    suspend fun replace(serverId: String, storedKey: String, rows: List<TranscriptRowEntity>) {
        clear(serverId, storedKey)
        upsert(rows)
    }
}

@Database(entities = [SessionEntity::class, TranscriptRowEntity::class], version = 2, exportSchema = false)
abstract class HermexDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun transcripts(): TranscriptDao

    companion object {
        fun build(context: Context): HermexDatabase =
            Room.databaseBuilder(context, HermexDatabase::class.java, "hermex.db")
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
