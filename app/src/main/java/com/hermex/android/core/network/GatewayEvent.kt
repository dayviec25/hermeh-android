package com.hermex.android.core.network

import com.hermex.android.core.model.ApprovalChoice
import com.hermex.android.core.model.PendingRequest
import kotlinx.serialization.json.JsonObject

sealed interface GatewayEvent {
    data object MessageStart : GatewayEvent
    data class MessageDelta(val text: String) : GatewayEvent
    data class MessageInterim(val text: String, val alreadyStreamed: Boolean) : GatewayEvent
    data class MessageComplete(val text: String, val status: String, val partial: Boolean) : GatewayEvent
    data class ThinkingDelta(val text: String) : GatewayEvent
    data class ReasoningDelta(val text: String) : GatewayEvent
    data class ToolStart(val toolId: String, val name: String, val context: String, val argsJson: String) : GatewayEvent
    data class ToolComplete(
        val toolId: String,
        val name: String,
        val durationSeconds: Double?,
        val output: String?,
        val error: String?,
    ) : GatewayEvent
    data class SessionInfo(
        val running: Boolean?,
        val turnStartedAt: Double?,
        val storedKey: String?,
        val title: String?,
        val model: String?,
        val provider: String?,
        val reasoningEffort: String?,
        val profileName: String?,
        val cwd: String? = null,
    ) : GatewayEvent
    data class SessionTitle(val storedKey: String?, val title: String) : GatewayEvent
    data class Error(val message: String) : GatewayEvent
    data class RequestCancel(val envelopeId: String) : GatewayEvent
    data class ServerRequest(val request: PendingRequest) : GatewayEvent
    data object SessionsChanged : GatewayEvent
    data class Unknown(val type: String) : GatewayEvent
}

data class GatewayEnvelope(val sessionId: String?, val seq: Long?, val event: GatewayEvent)

object GatewayEventParser {

    fun parseEvent(params: JsonObject): GatewayEnvelope {
        val type = params.str("type").orEmpty()
        val payload = params.child("payload")
        val event = when (type) {
            "message.start" -> GatewayEvent.MessageStart
            "message.delta" -> GatewayEvent.MessageDelta(payload.str("text").orEmpty())
            "message.interim" -> GatewayEvent.MessageInterim(
                payload.str("text").orEmpty(),
                payload.bool("already_streamed") ?: false,
            )
            "message.complete" -> GatewayEvent.MessageComplete(
                text = payload.str("text").orEmpty(),
                status = payload.str("status") ?: "complete",
                partial = payload.bool("partial") ?: false,
            )
            "thinking.delta" -> GatewayEvent.ThinkingDelta(payload.str("text").orEmpty())
            "reasoning.delta" -> GatewayEvent.ReasoningDelta(payload.str("text").orEmpty())
            "tool.start" -> GatewayEvent.ToolStart(
                toolId = payload.str("tool_id").orEmpty(),
                name = payload.str("name") ?: "tool",
                context = payload.str("context").orEmpty(),
                argsJson = payload?.get("args")?.toString() ?: "{}",
            )
            "tool.complete" -> {
                val result = payload.child("result")
                GatewayEvent.ToolComplete(
                    toolId = payload.str("tool_id").orEmpty(),
                    name = payload.str("name") ?: "tool",
                    durationSeconds = payload.double("duration_s"),
                    output = result.str("output") ?: payload?.get("result")?.takeIf { result == null }?.toString(),
                    error = result.str("error")?.takeIf { it.isNotBlank() },
                )
            }
            "session.info" -> GatewayEvent.SessionInfo(
                running = payload.bool("running"),
                turnStartedAt = payload.double("turn_started_at"),
                storedKey = payload.str("stored_session_id"),
                title = payload.str("title"),
                model = payload.str("model"),
                provider = payload.str("provider"),
                reasoningEffort = payload.str("reasoning_effort"),
                profileName = payload.str("profile_name"),
                cwd = payload.str("cwd"),
            )
            "session.title" -> GatewayEvent.SessionTitle(payload.str("session_id"), payload.str("title").orEmpty())
            "error" -> GatewayEvent.Error(payload.str("message") ?: "Hermes reported an error.")
            "request.cancel" -> GatewayEvent.RequestCancel(payload.str("id").orEmpty())
            "sessions.changed" -> GatewayEvent.SessionsChanged
            else -> GatewayEvent.Unknown(type)
        }
        return GatewayEnvelope(
            sessionId = params.str("session_id")?.takeIf { it.isNotEmpty() },
            seq = params.long("seq"),
            event = event,
        )
    }

    fun parseServerRequest(id: String, method: String, params: JsonObject?): GatewayEnvelope {
        val request: PendingRequest? = when (method) {
            "approval" -> PendingRequest.Approval(
                envelopeId = id,
                requestId = params.str("request_id").orEmpty(),
                command = params.str("command").orEmpty(),
                description = params.str("description").orEmpty(),
                choices = params.list("choices").mapNotNull { it.textOrNull()?.let(ApprovalChoice::fromWire) }
                    .ifEmpty { ApprovalChoice.entries.toList() },
            )
            "clarify" -> PendingRequest.Clarify(
                envelopeId = id,
                requestId = params.str("request_id").orEmpty(),
                question = params.str("question") ?: params.str("prompt").orEmpty(),
                options = params.list("choices").mapNotNull { it.textOrNull() },
            )
            "sudo", "secret" -> PendingRequest.Credential(
                envelopeId = id,
                kind = method,
                prompt = params.str("prompt") ?: params.str("description").orEmpty(),
            )
            else -> null
        }
        return GatewayEnvelope(
            sessionId = params.str("session_id"),
            seq = null,
            event = request?.let { GatewayEvent.ServerRequest(it) } ?: GatewayEvent.Unknown("request.$method"),
        )
    }
}
