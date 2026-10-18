package com.hermex.android.core.data

import com.hermex.android.core.data.db.SessionDao
import com.hermex.android.core.data.db.SessionEntity
import com.hermex.android.core.model.ProfileName
import com.hermex.android.core.model.SessionSummary
import com.hermex.android.core.model.StoredKey
import com.hermex.android.core.network.HermesRest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class SessionRepository(
    private val auth: AuthRepository,
    private val dao: SessionDao,
) {
    fun sessions(serverId: String): Flow<List<SessionSummary>> =
        dao.observe(serverId).map { rows -> rows.map { it.toDomain() } }

    suspend fun refresh(serverId: String) {
        val profile = auth.activeProfile.value
        val rows = auth.authorized { rest ->
            val all = mutableListOf<SessionEntity>()
            val seen = HashSet<String>()
            var offset = 0
            while (true) {
                val page = rest.sessions(profile, offset)
                val fresh = page.filter { seen.add(it.id) }
                all += fresh.map { row ->
                    SessionEntity(
                        serverId = serverId,
                        key = row.id,
                        profile = row.profile ?: profile,
                        title = row.title?.takeIf { it.isNotBlank() },
                        preview = row.preview.orEmpty(),
                        messageCount = row.messageCount,
                        lastActive = row.lastActive ?: 0.0,
                        pinned = row.pinned,
                        archived = row.archived,
                        unread = row.unread,
                        isActive = row.isActive,
                        model = row.model,
                        parentKey = row.parentSessionId,
                    )
                }
                if (page.size < HermesRest.SESSION_PAGE || fresh.isEmpty()) break
                offset += HermesRest.SESSION_PAGE
            }
            all
        }
        dao.replace(serverId, rows)
    }

    suspend fun clearAll() = dao.clearAll()

    private fun SessionEntity.toDomain() = SessionSummary(
        key = StoredKey(key),
        profile = ProfileName(profile),
        title = title ?: preview.takeIf { it.isNotBlank() } ?: "New chat",
        preview = preview,
        messageCount = messageCount,
        lastActive = Instant.ofEpochMilli((lastActive * 1000).toLong()),
        pinned = pinned,
        archived = archived,
        unread = unread,
        isActive = isActive,
        model = model,
        parentKey = parentKey?.let(::StoredKey),
    )
}
