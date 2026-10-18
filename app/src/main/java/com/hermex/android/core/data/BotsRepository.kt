package com.hermex.android.core.data

import com.hermex.android.core.model.BotLiveStatus
import com.hermex.android.core.model.BotProfile
import com.hermex.android.core.model.CanonicalSession
import com.hermex.android.core.model.ProfileName
import com.hermex.android.core.model.StoredKey
import com.hermex.android.core.network.BotsGateway
import com.hermex.android.core.network.CanonicalSessionDto
import com.hermex.android.core.network.GatewayEvent
import com.hermex.android.core.network.HermesFailure
import com.hermex.android.core.network.HermesGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.time.Instant

class BotsRepository(private val gateway: HermesGateway) {
    private val api = BotsGateway(gateway)

    val sessionsChanged: Flow<Unit> = gateway.events.filter { it.event == GatewayEvent.SessionsChanged }.map { }

    suspend fun roster(): List<BotProfile> {
        val profiles = api.profiles()
        val runtimes = try {
            api.activeRuntimes()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: HermesFailure) {
            emptyList()
        }
        val owners = HashMap<String, MutableSet<String>>()
        profiles.forEach { profile ->
            profile.canonical?.let { session ->
                listOfNotNull(session.id, session.resolvedId).forEach { owners.getOrPut(it) { HashSet() } += profile.name }
            }
        }
        val live = HashMap<String, BotLiveStatus>()
        runtimes.forEach { runtime ->
            val status = BotLiveStatus.fromWire(runtime.status) ?: return@forEach
            val owner = owners[runtime.sessionKey]?.singleOrNull() ?: return@forEach
            live[owner] = minOf(live[owner] ?: status, status)
        }
        return profiles.map { dto ->
            BotProfile(
                profile = ProfileName(dto.name),
                name = dto.lookTitle ?: dto.displayName ?: if (dto.name == "default") "Hermes" else dto.name,
                description = dto.lookDescription ?: dto.description,
                colorHex = dto.lookColor,
                pinned = dto.pinned,
                hidden = dto.hidden,
                isDefault = dto.isDefault,
                canonical = dto.canonical?.toDomain(),
                live = live[dto.name],
            )
        }
    }

    suspend fun botChatKey(bot: BotProfile): StoredKey {
        bot.chatKey?.let { return it }
        api.findBotChat(bot.profile.value)?.let { found -> return StoredKey(found.resolvedId ?: found.id) }
        val created = api.createBotChat(bot.profile.value)
        try {
            api.titleBotChat(created.runtime)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (refused: HermesFailure.Rpc) {
            if (refused.code != TITLE_TAKEN) throw refused
            api.findBotChat(bot.profile.value)?.let { found -> return StoredKey(found.resolvedId ?: found.id) }
        }
        return StoredKey(created.storedKey)
    }

    private fun CanonicalSessionDto.toDomain() = CanonicalSession(
        id = StoredKey(id),
        tip = resolvedId?.let(::StoredKey),
        title = title,
        preview = preview,
        lastActive = lastActive?.let { Instant.ofEpochMilli((it * 1000).toLong()) },
        messageCount = messageCount,
    )

    private companion object {
        const val TITLE_TAKEN = 4022
    }
}
