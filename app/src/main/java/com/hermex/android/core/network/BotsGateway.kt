package com.hermex.android.core.network

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class CanonicalSessionDto(
    val id: String,
    val resolvedId: String?,
    val title: String?,
    val preview: String?,
    val lastActive: Double?,
    val messageCount: Int,
)

data class ProfileDto(
    val name: String,
    val displayName: String?,
    val description: String?,
    val isDefault: Boolean,
    val canonical: CanonicalSessionDto?,
    val lookTitle: String?,
    val lookDescription: String?,
    val lookColor: String?,
    val pinned: Boolean,
    val hidden: Boolean,
)

data class ActiveRuntimeDto(val sessionKey: String, val status: String?)

data class CreatedSessionDto(val runtime: String, val storedKey: String)

class BotsGateway(private val gateway: HermesGateway) {

    suspend fun profiles(): List<ProfileDto> {
        val result = gateway.call("profiles.list", buildJsonObject { put("include_sessions", true) }).obj()
        return result.list("profiles").mapNotNull { element ->
            val row = element.obj() ?: return@mapNotNull null
            val name = row.str("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val look = row.child("ui_meta").child("hermes-bots")
            ProfileDto(
                name = name,
                displayName = row.str("display_name").trimmedOrNull(),
                description = row.str("description").trimmedOrNull(),
                isDefault = row.bool("is_default") ?: (name == "default"),
                canonical = row.child("canonical_session")?.let(::canonical),
                lookTitle = look.str("title").trimmedOrNull(),
                lookDescription = look.str("description").trimmedOrNull(),
                lookColor = look.str("color").trimmedOrNull(),
                pinned = look.bool("pinned") ?: false,
                hidden = look.bool("hidden") ?: false,
            )
        }
    }

    suspend fun activeRuntimes(): List<ActiveRuntimeDto> {
        val result = gateway.call("session.active_list", JsonObject(emptyMap())).obj()
        return result.list("sessions").mapNotNull { element ->
            val row = element.obj() ?: return@mapNotNull null
            ActiveRuntimeDto(row.str("session_key") ?: return@mapNotNull null, row.str("status"))
        }
    }

    suspend fun findBotChat(profile: String): CanonicalSessionDto? {
        val result = gateway.call("session.list", buildJsonObject {
            put("profile", profile)
            put("title", BOT_CHAT_TITLE)
            put("include_hidden", true)
        }).obj()
        return result.list("sessions").firstNotNullOfOrNull { it.obj()?.let(::canonical) }
    }

    suspend fun createBotChat(profile: String): CreatedSessionDto {
        val result = gateway.call("session.create", buildJsonObject {
            put("profile", profile)
            put("title", BOT_CHAT_TITLE)
            put("hidden", true)
            put("follow_profile_config", true)
            put("source", "hermex")
        }).obj()
        return CreatedSessionDto(
            runtime = requireNotNull(result.str("session_id")) { "session.create without session_id" },
            storedKey = requireNotNull(result.str("stored_session_id")) { "session.create without stored_session_id" },
        )
    }

    suspend fun titleBotChat(runtime: String) {
        gateway.call("session.title", buildJsonObject {
            put("session_id", runtime)
            put("title", BOT_CHAT_TITLE)
        })
    }

    private fun canonical(row: JsonObject): CanonicalSessionDto? {
        val id = row.str("id")?.takeIf { it.isNotBlank() } ?: return null
        return CanonicalSessionDto(
            id = id,
            resolvedId = row.str("resolved_id").trimmedOrNull(),
            title = row.str("title").trimmedOrNull() ?: row.str("root_title").trimmedOrNull(),
            preview = row.str("preview").trimmedOrNull(),
            lastActive = row.double("last_active") ?: row.double("started_at"),
            messageCount = row.long("message_count")?.toInt() ?: 0,
        )
    }

    private fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        const val BOT_CHAT_TITLE = "Bot Chat"
    }
}
