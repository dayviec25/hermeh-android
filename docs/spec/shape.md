# Hermex Android: data shape and plan

Platform scope: Android only (Kotlin, Compose). The iOS app at `/private/tmp/claude-501/-Users-dchungdev-hermex-android/9a337a95-8f6e-479b-8a94-cb75cc8eee82/scratchpad/src/hermex` is the behavior and design reference. Server kind: **Hermes dashboard only** (hermes-agent `hermes dashboard`). The legacy hermes-webui server is out of scope.

Wire protocol: `docs/spec/hermes-protocol.md` (from the Swift source) plus real captures from hermes-agent 0.21.5 in `docs/spec/captured-turn.txt` and `docs/spec/captured-rest.txt`. Captures win over the spec where they differ. Design: `docs/spec/design.md`.

## Verified against the live host (0.21.5)

- `POST /auth/password-login {"provider":"basic","username","password"}` → 200 `{"ok":true}` and cookies `hermes_session_at` (12 h), `hermes_session_rt` (30 d), `hermes_session_provider`. Persist all cookies per host.
- `GET /api/auth/me` → `{user_id, display_name, provider, expires_at}`.
- `POST /api/auth/ws-ticket {}` → `{"ticket","ttl_seconds":30}`. Single use; mint one per socket.
- WS `ws(s)://host/api/ws`, subprotocols `hermes-gateway-v1` and `hermes-gateway-ticket.<ticket>`. First frame from the server: `event` `gateway.ready`.
- Approvals: answer `approval.respond {session_id: runtime, request_id: params.request_id, choice}`. **`request_id` is `params.request_id`, not the JSON-RPC envelope id.** Reply `{"resolved":1}`. The server then emits `request.cancel {id: <envelope id>, reason:"resolved"}`.
- `tool.complete.payload`: `{tool_id, name, args, duration_s, result:{output, exit_code, error, ...}}`.
- `message.complete.payload`: `{text, usage, status:"complete", persisted_turn}`. Then `session.info {running:false}`.
- `reasoning.available.payload.text` repeats the interim/final text in this host; do not render it as thinking. `thinking.delta` is a spinner caption ("ಠ_ಠ contemplating..."), show it only in the run-status capsule.
- Transcript REST rows: `role` user | assistant | tool; assistant `tool_calls[].function.{name,arguments(JSON string)}`; tool rows carry `tool_call_id`, `tool_name`, `content` (JSON string of the result). `reasoning` / `reasoning_content` may hold real thinking text.
- Session list rows: `id` (stored key), `title` (nullable → fall back to `preview`), `preview`, `message_count`, `last_active` (epoch seconds, double), `profile`, `pinned`, `archived`, `unread`, `is_active`, `model`, `parent_session_id`, `_lineage_root_id` (optional).

## Kotlin shape (core/model)

```kotlin
@JvmInline value class StoredKey(val value: String)   // durable session id
@JvmInline value class RuntimeId(val value: String)   // live runtime id, never persisted
@JvmInline value class ProfileName(val value: String)

data class ServerConfig(
    val id: String,                 // uuid, local
    val name: String,               // user label, default host name
    val baseUrl: String,            // scheme+host+port (+ optional path prefix), no trailing slash
    val username: String,
    val customHeaders: List<Pair<String, String>>,
)

sealed interface AuthState {
    data object Unconfigured : AuthState
    data class SignedOut(val server: ServerConfig, val message: String?) : AuthState
    data class SignedIn(val server: ServerConfig, val user: HermesUser, val hostVersion: String) : AuthState
}

data class SessionSummary(
    val key: StoredKey, val profile: ProfileName, val title: String, // title ?: preview ?: "New chat"
    val preview: String, val messageCount: Int, val lastActive: Instant,
    val pinned: Boolean, val archived: Boolean, val unread: Boolean, val isActive: Boolean,
    val model: String?, val parentKey: StoredKey?,
)

sealed interface TranscriptItem {
    val id: String
    data class User(override val id: String, val text: String, val steer: Boolean) : TranscriptItem
    data class Assistant(override val id: String, val markdown: String, val streaming: Boolean) : TranscriptItem
    data class Thinking(override val id: String, val text: String) : TranscriptItem
    data class Tool(override val id: String, val name: String, val context: String,
                    val argsJson: String, val status: ToolStatus, val output: String?) : TranscriptItem
    data class Notice(override val id: String, val text: String, val kind: NoticeKind) : TranscriptItem // error, interrupted
}
sealed interface ToolStatus { data object Running; data class Done(val seconds: Double?); data class Failed(val error: String); data object Interrupted }

sealed interface PendingRequest {
    val envelopeId: String
    data class Approval(override val envelopeId: String, val requestId: String, val command: String,
                        val description: String, val choices: List<ApprovalChoice>) : PendingRequest
    data class Clarify(override val envelopeId: String, val requestId: String, val question: String,
                       val options: List<String>) : PendingRequest
    data class Credential(override val envelopeId: String, val kind: String, val prompt: String) : PendingRequest
}
enum class ApprovalChoice { Once, Session, Always, Deny }

sealed interface RunState {
    data object Idle : RunState
    data class Working(val since: Instant, val caption: String) : RunState   // "Hermes is working", "Starting response"
    data object WaitingForYou : RunState
    data object Stopping : RunState
    data object Reconnecting : RunState
}

data class ComposerState(val text: String, val model: String?, val provider: String?,
                         val reasoning: String?, val profile: ProfileName, val cwd: String?)
```

Gateway events are parsed once, at the boundary, into a `sealed interface GatewayEvent` (MessageStart, MessageDelta(text), MessageInterim(text), MessageComplete(text), ThinkingDelta(text), ReasoningDelta(text), ToolStart, ToolComplete, SessionInfo(running, model, …), Error, RequestCancel(envelopeId), ServerRequest(PendingRequest), SessionsChanged, Unknown(type)). Unknown types are ignored, never crash. All JSON decoding is tolerant (`ignoreUnknownKeys`, `explicitNulls = false`, defaults on every field).

The turn reducer is a pure function `reduce(ChatState, GatewayEvent): ChatState`, so streaming logic lives in one place.

## Architecture

Single `:app` module, packages `core.model`, `core.network` (OkHttp + kotlinx.serialization: `HermesRest`, `HermesGateway` WebSocket JSON-RPC with ping every 15 s, 45 s silence = dead, reconnect with backoff 1/2/4/8 s, reattach via `session.resume`), `core.data` (repositories, DataStore for servers + prefs, password encrypted with an Android Keystore AES key, Room cache for session list + transcripts for offline read), `core.designsystem` (theme tokens from design.md, wordmark, glass surfaces, icons), `feature.<name>`. Hand-written `AppGraph`. Navigation Compose with `@Serializable` routes; one `HermexNavHost` owns every route so feature packages never import each other.

Package id `com.hermex.android`. minSdk 28, target/compile 36. Debug builds allow cleartext (users run plain `http://` on LAN/Tailscale); release allows cleartext too because self-hosted LAN servers are a supported setup on iOS (ATS exception there is per-user). Emulator reaches the local host via `adb reverse tcp:9199 tcp:9199` and `http://127.0.0.1:9199`.

## Verification host

Instead of astack-mock, the app is verified against the real pinned hermes-agent with a stub model (`scripts/local-hermes` in the iOS repo), running at `http://127.0.0.1:9199`, user `hermex`, password `hermex-local`. Each turn: interim "Let me run a quick check.", a `terminal` tool call that waits for approval, then "The command returned: 1." This is higher fidelity than a mock. Empty state = fresh host; error state = wrong password / host stopped.

## Phases

1. Foundation + core chat: scaffold, design system, onboarding + connect + sign-in, home (sidebar rows + Sessions list), chat (transcript load, send, stream, thinking/tool rows, approvals, stop, run status), model + reasoning picker, settings (server, appearance, sign out). All other destinations exist as routes with a "coming soon" placeholder.
2. Panels in parallel, one package each: Tasks (cron), Skills, Memory, Insights; Profiles + Projects; Kanban; Workspace browser; Bots inbox; session actions (rename, pin, archive, delete, search, fork).
3. Polish: offline cache, attachments, slash commands, markdown completeness, dark mode and large-font passes.
