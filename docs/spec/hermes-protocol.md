# Hermes server wire protocol, as the Hermex iOS client speaks it

Scope: a client for a `hermes dashboard` host (hermes-agent, default port 9119). Written from the Swift source and the fixtures in `HermesMobileTests/Fixtures/HermesAgent/`. Source root: `/private/tmp/claude-501/-Users-dchungdev-hermex-android/9a337a95-8f6e-479b-8a94-cb75cc8eee82/scratchpad/src/hermex`. Paths below are relative to that root.

Labels: **measured** = read in code or a fixture; **inferred** = follows from code but not stated; **guess** = my reading, unverified.

Pins: `HERMES_AGENT_TESTED_SHA` line 1 `ca6782850432927f33df4775cb6dd45bb51460d2`, line 2 `0.21.5` (measured). `HermesCompatibility.minimumVersion = "0.21.3"` (`HermesMobile/Networking/Hermes/HermesCompatibility.swift:9`), `testedVersion = "0.21.5"` (`:11`).

Not read in depth: `HermesMobile/Features/Bots/BotConnection.swift`, `BotConnectionView.swift`, `HermesMobileTests/HermesGatewayTests.swift`, `HermesBrowserSignInTests.swift` (only greps), `HermesConnectionTests.swift` (only greps), `CONTEXT.md`, `DEVELOPMENT.md`. The brief's `HermesChat*.swift` engine is mostly in `HermesMobile/Features/Hermes/HermesConversation.swift` and `HermesMobile/Features/Chat/HermesChatTurnCoordinator.swift`.

---

## 0. Shape of the system

Three layers, all on one host origin `base` (e.g. `https://hermes.example:9119`, or with a path prefix):

1. HTTPS REST under `base/api/...` and `base/auth/...` (JSON bodies).
2. One WebSocket `ws(s)://…/api/ws` carrying JSON-RPC 2.0 (`HermesGateway`). Every chat turn, approval, setting and session attach goes through this socket.
3. Optional plugin sockets (`/api/plugins/kanban/events`) and REST plugin routes, not needed for chat.

Client state per server: one cookie jar (or a bearer for browser sign-in), one gateway socket shared by all screens.

---

## 1. Sign-in and auth

### 1.1 Public status probe (no credentials)

`GET {base}/api/status`, no cookie, no bearer. (`HermesREST.swift:342-343`, `case .status`; `HermesConnection.swift:474-478`, `publicStatus`.)

Response handling (`HermesConnection.swift:474-489`):
- 200 with a JSON object: OK.
- 200 with a non-JSON body, 404, or 401 whose body is a JSON object: not a Hermes dashboard (`BotFailure.notDashboard`). A 401 with a non-JSON body means a proxy in front (`.blocked`).
- Any 3xx or a reply from another host: `.blocked` (`HermesConnection.swift:507-515`, `exchange`).
- Other statuses: `.rejected(code)`.

Fields the client reads (fixture `HermesMobileTests/Fixtures/HermesAgent/status.json`, measured):
```json
{
  "version": "0.21.5",
  "install_id": "fixture-install",
  "auth_required": true,
  "auth_providers": ["basic"],
  "auth_flows": ["fixture-redacted", "fixture-redacted"],
  "gateway_running": true,
  "gateway_state": "fixture-redacted",
  "profiles": ["inbox-triage"],
  "can_update_hermes": true
}
```
- `version`: string. Compared with the minimum (`HermesConnection.swift:193`: `if let version = serverVersion, !HermesCompatibility.isSupported(version) { throw BotFailure.outdated(version) }`). Rule (`HermesCompatibility.swift:15-30`): missing or unparseable version counts as supported; otherwise compare the leading `MAJOR.MINOR.PATCH` numerically against `0.21.3`. Canary `0.21.4+canary…` reads as `0.21.4`. Version is read before the auth gate, so an old host is refused before any password is sent.
- `install_id`: `HermesConnection.swift:192` reads it through `BotConnection.installID(in:)`. Trust on first use: the first sign-in stores it; later sign-ins must match (`:194`, `:217-218`). Key name inferred from fixture.
- `auth_required` must be true (`:195`), else `.unsupported`.
- `auth_providers` contains `"basic"` for password sign-in (`HermesConnection.swift:206-208`, guess on the line; the check is `status["auth_providers"]…contains("basic")`).
- `auth_flows` containing `"native_pkce"` plus no `basic` provider means browser sign-in is required (`HermesBrowserSignIn.swift:36-42`, `isRequired(by:)`).

### 1.2 Password sign-in (basic provider)

Step A, login: `POST {base}/auth/password-login`, `Content-Type: application/json`. (`HermesREST.swift:360-362`.)
```json
{"provider": "basic", "username": "<user>", "password": "<pass>"}
```
Accepted status: exactly 200 (`HermesConnection.swift`, `Self.send(... accepting: 200..<201 ...)`, the `decoded` helper at `:464-468`). Body unused. The host sets its session cookie in the response; the client keeps it in an ephemeral cookie jar (`HermesConnection.swift:58-60`, `:115`). Cookie name and path are not in the code (guess: a dashboard session cookie scoped by `X-Forwarded-Prefix`, see §1.5).

Step B, identity: `GET {base}/api/auth/me` with the cookie (`HermesREST.swift:372`). Must return `provider == "basic"` (`HermesConnection.swift:~219-221`). Other fields unused by chat.

Step C, ticket and socket: see §2.

Sign-in is single-flight and the password is only sent after the status check (`HermesConnection.swift:11-14`). A 401 on login is `rejected(401)`, reported as "didn't accept the username or password" (`BotProtocol.swift:73`).

### 1.3 Browser sign-in (native PKCE, when the host has no basic provider)

Implemented in `HermesMobile/Networking/Hermes/HermesBrowserSignIn.swift`.

1. Client makes a random `verifier` (32 bytes, base64url, no padding) and `state` (16 bytes) (`:65`).
2. Client starts a loopback HTTP listener on `127.0.0.1` on an ephemeral port (`:145-186`).
3. Opens `GET {base}/auth/native/authorize?code_challenge=<S256>&code_challenge_method=S256&redirect_uri=http%3A%2F%2F127.0.0.1%3A<port>%2Fcallback&state=<state>` in an ephemeral system browser session (`:70-71`, `:90-101`). Every query value is percent-encoded per RFC 3986 unreserved set. No `provider` parameter.
4. `challenge = base64url(SHA256(verifier))` (`:104-106`).
5. The host's pages run; the host redirects to `http://127.0.0.1:<port>/callback?code=<code>&state=<state>`. The listener accepts the first `GET /callback` whose `state` matches and that has a non-empty `code` (`:224-233`), and replies `302 Found` with `Location: hermexsignin://done` (`:234`), which closes the sheet.
6. Token exchange: `POST {base}/auth/native/token`, body:
```json
{"code": "<code>", "code_verifier": "<verifier>"}
```
(`HermesREST.swift:368-371`, `case .nativeToken`.) Public, no cookie. Any refusal is a generic 400 (`:119`).
Response (`HermesREST.swift:113-116`): `{access_token, refresh_token, token_type, expires_at, provider, user_id}`. Client requires non-empty `access_token` and `refresh_token` (`HermesConnection.swift:261-262`).
7. Refresh later: `POST {base}/auth/native/refresh`, body `{"refresh_token": "<rt>", "provider": "<provider>"}` (`HermesREST.swift:364-367`). Response same shape. The host rotates the refresh token every time; the client saves the new one before using the access token (`HermesConnection.swift:279-310`). Expired or unknown token: 401 `{"error": "session_expired"}` (`HermesREST.swift:113-116`).
8. Signed-in REST requests then carry `Authorization: Bearer <access_token>` (`HermesConnection.swift:520-526`, `bearing`). The access token is memory-only. Gateway upgrades do not carry it; they present a ticket (`:520-521`).
9. Identity check after sign-in: `GET /api/auth/me` must return the same `provider` and `user_id` as saved (`HermesConnection.swift:~200-205`).

Note: `HermesREST.swift:~1-100` (header comment, "native sign-in routes (#708)") says a bearer passes the gate on every `/api/*` route, and the WebSocket still needs a ticket that the bearer can mint. Measured in the author's 0.21.5 spike per the comment; not re-verified here.

### 1.4 Signed-in 401

A signed-in REST request answered 401 signs in again once and is resent (`HermesConnection.swift:1-25` header doc; `:379-400` `authorized`). Nothing else is retried. Writes are safe to resend only because the 401 is emitted before any handler (doc, same lines).

### 1.5 Headers and cookies rules

- Cookies: one ephemeral cookie jar per connection (`HermesConnection.swift:58-60`, `:115`). Clients must not hand-set `Cookie`; `cookie` is in the reserved list.
- Custom connection headers allowed by the user are validated against a reserved list (`HermesConnection.swift:716-719`, measured): `host, connection, upgrade, content-length, transfer-encoding, te, trailer, keep-alive, proxy-connection, proxy-authorization, cookie, origin, x-forwarded-prefix, x-hermes-session-token`. Also refused: `Authorization: Bearer` (Hermes reads it as its own session), and any `Authorization` on a browser sign-in connection (`:682-703`). Cloudflare Access uses `CF-Access-Client-Id` and `CF-Access-Client-Secret` instead.
- `Authorization` otherwise passes (e.g. Cloudflare Access single-header service token).
- Redirects: the session refuses any cross-host redirect and treats any 3xx as `blocked` (`HermesConnection.swift:503-515`).
- Protocol header: `X-Forwarded-Prefix` is reserved because the session cookie's path depends on it (`:682-688`).

### 1.6 Errors from sign-in

`BotFailure` cases (`HermesMobile/Networking/Bots/BotProtocol.swift:43-64`): `notDashboard`, `blocked`, `browserSignIn` (host needs browser sign-in but client has no browser), `signInExpired`, `browserSignInFailed`, `upgradeRefused(status)`, `outdated(version)`, `rejected(code)`, `transport`, `stale`, `wrongIdentity`, `unsupported`.

---

## 2. Transport: the gateway WebSocket

### 2.1 Opening the socket

1. Mint a ticket: `POST {base}/api/auth/ws-ticket`, empty JSON object body `{}`, cookie or bearer (`HermesREST.swift:374`; `HermesConnection.swift:419-420`). Response: `{"ticket": "<single-use string>"}`. Missing or empty ticket is `.unsupported`. Test stub shape: `HermesConnectionTests.swift:921` `{"ticket":"ticket"}`.
2. Upgrade: `GET {base-with-ws-scheme}/api/ws`, with:
```
Sec-WebSocket-Protocol: hermes-gateway-v1, hermes-gateway-ticket.<ticket>
```
Scheme `http`→`ws`, `https`→`wss` (`HermesREST.swift:695-703`, `gatewayUpgrade`). The same cookie jar applies. Every socket mints its own fresh ticket (`HermesGateway.swift:12`, `handshake` at `:265-270`).
3. Max message size 16 MiB (`HermesGateway.swift:276`).
4. A refused upgrade: status 101 is success. 408, 429, 5xx: retryable `.rejected(status)`. Any other status: `.upgradeRefused(status)`, which does not heal on its own (`HermesGateway.swift:557-563`).

### 2.2 Handshake (order matters)

(`HermesGateway.swift:265-298`)
1. Server sends first frame (measured shape from `BotClientTests.swift:1011`):
```json
{"method":"event","params":{"type":"gateway.ready","payload":{"replay_epoch":"epoch"}}}
```
Client requires `method == "event"`, `params.type == "gateway.ready"`, a non-empty `params.payload.replay_epoch` (`HermesGateway.swift:283-285`). `replay_epoch` is kept and compared at attach (`HermesConversation.swift:~365-385`).
2. Client's first outbound frame, id-numbered like any call (`HermesGateway.swift:293`):
```json
{"jsonrpc":"2.0","id":1,"method":"client.capabilities","params":{"server_requests":true}}
```
Without this the host withdraws approvals unsent, answers clarify empty, and skips sudo/secret (`HermesGateway.swift:288-292`). A host answering `-32601` is tolerated (`:294`).
3. Heartbeat starts (see 2.5).

No screen sends anything until the handshake returns (`:263-264`).

### 2.3 JSON-RPC framing

Client request (`HermesGateway.swift:156-197`):
```json
{"jsonrpc":"2.0","id":<int>,"method":"<method>","params":{...}}
```
- `id`: integer, increments from 1 per socket (`HermesGateway.swift:52`, `:159`). Matched on reply.
- Reply from server: `{"jsonrpc":"2.0","id":<same int>,"result":{...}}` or `{"jsonrpc":"2.0","id":<int>,"error":{"code":<int>,"message":"...","data":{...}}}` (`HermesGateway.swift:379-394`). `result: null` is treated as unsupported (`:394`).
- Error `code` is a JSON-RPC error code, not a WebSocket close code. Codes the client handles by name (`BotProtocol.swift:73-114`, measured): 4001 (runtime reaped, see 3.7), 4002/4010 (steer/redirect refused), 4007 and 4009 (session.resume refused while host swaps or settles, retry), 4022 (title in use), 4090 (another process owns the conversation), 4130 (too large), 5008 (name in use / cut refused), -32601 (method not on this host), -32602.
- Room (`groups.*`) errors carry `error.data.reason` (`HermesGateway.swift:384-385`).

Server events, server requests, and replies are separate shapes. Routing (`HermesGateway.swift:365-404`):
- `method == "event"`: sequenced event. `params = {type, session_id, seq, payload}` (`HermesGateway.swift:366`).
- Frame with string `id` and string `method`: server request (approval, clarify, credential). Forwarded whole. It has no `seq` (`:367-369`). Client answers it with a call such as `approval.respond` (not with a JSON-RPC response frame), and the gateway never answers it itself (`:19-20`).
- Frame with string `id` and no `method` (e.g. `heartbeat-N` pong): dropped (`:370`).
- Integer `id`: reply to a pending call (`:371-394`). Unknown id: logged and ignored (`:372-377`).

### 2.4 Event sequence

- `seq` is an integer per runtime session, starting at 1 (fixture `turn-frames.json`: seq 1..10, measured). Client keeps a cursor; a duplicate is dropped, a gap sets a rebuild flag (`HermesConversation.swift:~515-535`, `admit`/`reconcile`; `HermesChatTurnCoordinator.swift:1005-1010`).
- An event frame without `seq` sets `replayWasReset` (`HermesConversation.swift:~516-520`).
- Every event carries `session_id` = the runtime id (not the stored key) (fixtures, measured).

### 2.5 Heartbeats and silence

- Client sends `gateway.ping` every 15 s (`HermesGateway.swift:33`, `:315-336`), frame built verbatim:
```json
{"jsonrpc":"2.0","id":"heartbeat-1","method":"gateway.ping","params":{}}
```
String ids increase per beat. The host's pong has a string id and is dropped (`:315-319`, `:370`).
- The host sends no keepalive of its own. If no frame arrives for 45 s, the client closes the socket and treats it as lost (`HermesGateway.swift:338-363`, `:354`).
- Per-call reply deadline 30 s (`HermesGateway.swift:31`). `slash.exec` gets 60 s; `session.compress` 660 s; commit message 120 s (`:478-485`).

### 2.6 Reconnect and "4001 reap" semantics

Socket lifecycle (`HermesGateway.swift:106-150`):
- Socket opens when the first screen joins and closes when the last leaves (`:106-121`).
- App to background: client closes cleanly and silently; reconnect on active (`:123-133`).
- Dropped socket (read/send error, 45 s silence): all pending calls fail as `transport`; attached screens each hear the error once (`:406-438`).
- Dashboard restart (#1075): `dropSocket()` (`:138-143`).
- Connection replaced: `retire()`; later calls throw `stale` (`:145-150`).

Conversation reattach (`HermesConversation.swift:112-120`, `attach()` `~:365-400`):
- On a lost socket, reconnect with a backoff while the screen is active (`HermesConversation.swift:117`, `:264-290`). The backoff schedule itself is not read (open question).
- `session.resume` refusals 4007 and 4009 are retried for up to 60 s after the first refusal (`HermesConversation.swift:128-129`, `:272-283`).
- Runtime reaped: the host answers a write with JSON-RPC error 4001. The client reattaches to the stored key (which only reads) (`HermesChatTurnCoordinator.swift:~466-472`, in `submit`: `if case BotFailure.rejected(4001) = error { reattach() }`; approximate line).
- The host can also broadcast `session.reclaimed` as an event (`HermesConversation.swift:479`, `:494-504`); payload carries `session_id`, `stored_session_id`, `reason`. If it names this runtime or stored key, the client rebuilds as after a gap.
- A `session.close` the client asked for is never announced (`HermesConversation.swift:498`).

---

## 3. Sessions, profiles, and opening a chat

Terminology (measured, `HermesConversation.swift:112-122`): the **stored key** (e.g. `20260925_023517_59d8c3`) names a session durably; it goes into caches and drafts. The **runtime id** (e.g. `e7d1ef0d`) is per attached runtime, keys `seq` and session-scoped calls, changes after a reap, and is never stored. **Lineage root** (`_lineage_root_id`) is the identity of a legacy compression chain.

### 3.1 Profiles list (gateway)

Method `profiles.list`, params `{"include_sessions": false}` or `true` (`HermesCall.swift:348`). Call sites: `HermesCronClient.swift:99`, `BotInbox.swift:579`, `HermesChatSettings.swift:154`.

Reply shape (fixture `profiles-list.json`, measured, abridged):
```json
{
  "bot_mode_protocol": true,
  "profiles": [{
    "name": "inbox-triage", "display_name": "...", "description": "...", "is_default": false,
    "model": "...", "provider": "...", "role": null, "skill_count": 58, "has_avatar": false,
    "path": "/fixture/path", "previous_names": [], "last_session": null,
    "canonical_session": {"id": "20260916_133600_3b1e0b", "resolved_id": "20260916_133600_3b1e0b",
      "root_title": "...", "title": "...", "preview": "...", "message_count": 33,
      "started_at": 1789580160.646865, "last_active": 1790131994.38938},
    "ui_meta": {"hermes-bots": {"color": "...", "hidden": true, "pinned": false, "title": "..."}},
    "worker_session": null
  }]
}
```
`started_at` and `last_active` are Unix epoch seconds with fractions (measured).

Profile active/current: `GET {base}/api/profiles/active` → `{active, current}`, both fall back to `default` (`HermesREST.swift:124`, `:373`; doc at `HermesREST.swift:~10-30` header).

### 3.2 Session list (REST, the Sessions screen and inbox)

`GET {base}/api/sessions?profile=<p>&order=recent&archived=exclude&limit=100&offset=<n>&min_messages=1&exclude_sources=cron,kanban,oneshot,subagent,tool` (`HermesREST.swift:440-453`). Archived screen uses `archived=only` and drops `min_messages`. Page size constant `sessionPageSize = 100` (`HermesREST.swift:101-102`).

Reply: `{"sessions": [ <row>, … ]}` (`HermesSessionPage.swift:7-27`). A page without `sessions` is a failed read (`:3-5`).

Row fields (`HermesSessionPage.swift:40-80`, all optional except `id`):
- `id` (string): the stored key or tip id, used to open and change the row.
- `_lineage_root_id` (string): identity when present (legacy compression chain). Identity = `_lineage_root_id ?? id` (`:55`).
- `title`, `preview` (first prompt, 60 chars with `...`).
- `last_active`, `started_at`: Double, epoch seconds.
- `message_count`: int.
- `pinned`, `archived`, `unread`, `hidden`: bool.
- `model`, `cwd`, `profile`, `parent_session_id`.

Paging (`HermesSessionPage.swift:207-240`, measured): offset starts at 0 and advances by 100 each page. `hasMore = page.count >= 100 && added > 0`. `total` is never read (`:3-4`). Pages with only rows already seen end the list (`:219-236`).

Sessions list reading order (`SessionListViewModel.swift:1878-1916`) uses that same loop.

### 3.3 Opening a Bot Chat or Hermes session (gateway)

Attach sequence (`HermesConversation.swift:112-120` doc; `attach()` ~`:365-400`, measured order):

1. **Connect** the socket (§2).
2. **Platform note** (optional but done every attach): `GET {base}/api/config?profile=<p>` then, if `platform_hints.hermex.append` differs, `PUT {base}/api/config` deep-merging `platform_hints.hermex.append` (`HermesConnection.swift:156-168`; `HermesREST.swift:282-283` `setPlatformNote`, `HermesREST.swift:596` `api/config`, `:1278` note). Text is the constant `HermesREST.platformNote` (`HermesREST.swift:~96-99`). Not required by the protocol; it only tells the agent what the screen renders.
3. **Identity**:
   - Bot Chat (`ConversationTarget.canonicalChat`): `session.list` with `{"profile": p, "title": "Bot Chat", "include_hidden": true}` (`HermesCall.swift:357-358`; `HermesConversation.swift:412-425`). Must return exactly one row with `id` (root) and `resolved_id` (tip). Tip becomes `storedKey`.
   - Existing session (`.session(profile, key)`): no call; key is the stored key.
   - New session (`.new`): `session.create` (method name for `.sessionNew`, `HermesCall.swift:285`) with `{"profile": p, "source": "hermex", "cwd"?: "<dir>", "model"?: "<id>", "provider"?: "<p>"}` (`HermesCall.swift:362-366`). Reply must carry `stored_session_id` and `session_id` (`HermesConversation.swift:431-437`). Then the client treats it as `.session`.
4. **Identity resume**: `session.resume` with `{"profile": p, "session_id": "<stored key>", "close_on_disconnect": false, "source": "hermex", "omit_messages": true}` (`HermesCall.swift:394-397`; `HermesConversation.swift:250`). Reply carries the runtime `session_id` and `session_key` (or `stored_session_id` for a session that has not started, `HermesConversation.swift:444-448`). For a Bot Chat, `session_key` must equal the tip from step 3 (`:444-446`).
5. **Replay**: `session.events.since` with `{"session_id": "<runtime>", "last_seen": <seq>}` (`HermesCall.swift:399-400`). Reply must carry `latest_seq` (int), `epoch` (string), `truncated` (bool), `events` (array). Events after the cursor are applied in `seq` order (`HermesConversation.swift:~466-495`, `reconcile`).
6. **Snapshot**: second `session.resume` (same params). Read `running`, `turn_started_at`, `inflight`, `queued.user`, `info`, `title`, `status` (`HermesChatTurnCoordinator.swift` `reconcile`, ~`:1150-1185`; `HermesConversation.swift:~400-405`).
7. Connected. Live events flow.

Fixture `session-resume.json` (measured, abridged):
```json
{
  "info": {"model": "fixture-redacted", "provider": "fixture-redacted", "cwd": "/fixture/cwd",
           "running": false, "turn_started_at": null, "stored_session_id": "20260925_023517_59d8c3",
           "title": "fixture-redacted", "version": "0.21.5", "yolo": true, "usage": {...}},
  "message_count": 2,
  "messages": [{"role": "user", "row_id": 78, "text": "...", "timestamp": 1790318117.41698},
               {"role": "assistant", "row_id": 79, "text": "ok", "timestamp": 1790318122.93445}],
  "messages_omitted": false, "resumed": "fixture-redacted", "running": false,
  "session_id": "e7d1ef0d", "session_key": "20260925_023517_59d8c3",
  "started_at": 1790318117.288243, "status": "idle", "turn_started_at": null
}
```

### 3.4 Transcript history (REST)

`GET {base}/api/sessions/{stored_key}/messages?profile=<p>&order=latest&limit=100&offset=<n>&include_compacted=true` (`HermesREST.swift:423-438`). Without `offset`, the latest 500 rows (`HermesREST.swift:~18-21`).

Reply: `{"session_id", "profile", "messages": [...], "pagination": {"limit", "offset", "order", "returned"}}` (`HermesREST.swift:~54-62`). Each page is oldest first and carries no total: a short page is the start. `offset` counts display rows back from the newest (`HermesTranscriptHistory.swift:7`). 404 means the Profile does not have the session, returned as `nil` (`BotClient.swift:112-123`). Call sites: `HermesChatTurnCoordinator.swift:1300-1305`.

Message row fields (session-resume fixture and `BotClient`): `role`, `row_id` (int, durable prompt/answer row), `text`, `timestamp`, and optionally `tool_calls`, `codex_message_items`, `codex_reasoning_items` (opaque; ignore).

---

## 4. Sending a message and streaming the reply

### 4.1 The send call

Method `prompt.submit` with `{"session_id": "<runtime>", "text": "<prompt>", "queued": true}` (`HermesCall.swift:402-403`). `queued: true` is always sent so an idle Send cannot be turned into a steer or redirect by a host setting (`HermesCall.swift:~91-92`).

Reply status (`BotPromptMode.swift:~40-53`, `outcome(_:)`, measured):
- `"streaming"` for a send that started a turn → `.started`. Test stub: `HermesTranscriptPagingTests.swift:181`.
- `"queued"` for a follow-up queued behind a running turn → `.followUpQueued`.
- `"rejected"` (only for steer and redirect modes) → `.rejected`.
- `voice_stopped: true` on a send → `.voiceStopped`.
- Anything else → `.unknown`.

Prompt text with attachments: files are staged first. Images: `POST {base}/api/chat/image-upload?profile=<p>`, body with `dataURL`/`filename` (`HermesREST.swift:379-383`). Documents: `file.attach {session_id, name, data_url}` (`HermesCall.swift:418-419`). Their references are appended to the prompt text (`HermesChatTurnCoordinator.swift:~438-445`). Files only go with a fresh turn (`send` or `queue`, `:~427-428`).

Stream modes from the composer (`ChatViewModel.swift:3244-3248`): while a turn runs, Steer → `session.steer`, Queue → `prompt.submit` (queued), Stop & send → `session.redirect`. With files staged, Steer becomes Queue (`ChatViewModel.swift:3245`).

### 4.2 Events on the socket (the stream)

All events: JSON-RPC notification `{"jsonrpc":"2.0","method":"event","params":{"type":"<type>","session_id":"<runtime>","seq":<int>,"payload":{...}}}`. The client dispatches on `type` (`HermesChatTurnCoordinator.swift:1009-1090`, `apply`).

| `type` | Payload (measured unless noted) | What the client does | Source |
|---|---|---|---|
| `session.info` | see below | turn state, title, model, `running` flag | `:1056`, `applyInfo` |
| `message.start` | `{}` (fixture) | starts a turn (or consumes the prompt's own start) | `:1011-1019` |
| `message.delta` | `{"text": "..."}` | appends visible answer text; empty text ignored | `:1020-1027` |
| `message.interim` | `{"text": "...", "already_streamed": bool}` | intermediate assistant text between tool calls | `:1028-1034`; fixture `turn-tool-approval-frames.json` seq 7 |
| `thinking.delta` | `{"text": "..."}` (empty allowed) | spinner text only, not reasoning | fixture; `:1090-1093` comment |
| `reasoning.available` | `{"text": "..."}` | answer text carried as reasoning; the client does not treat it as reasoning (comment) | fixture; `:1090-1093` comment |
| `reasoning.delta` | `{"text": "..."}` | handled as reasoning, but no fixture emits it | `:1035-1039` (see open questions) |
| `tool.start` | `{"tool_id", "name", "args": {...}, "context": "..."}` | adds a tool row | `:1040-1044`; fixture seq 8 |
| `tool.complete` | `{"tool_id", "name", "args", "duration_s", "result": {"exit_code", "output", "error", "approval"}}` | completes that tool row (matched by `tool_id`) | `:1045-1048`; fixture seq 10 |
| `session.usage` | `{"usage": {...}}` | context window usage | `:1054-1055` |
| `session.title` | `{"session_id": "<stored key>", "title": "..."}` | suggested title; ignored if `session_id` is another stored key | `:1049-1053` |
| `message.complete` | see below | final reply, usage, persisted turn receipt | `:1058`, `complete()` |
| `error` | `{"message": "..."}` | error text; turn fails (see 4.3) | `:1060-1072` |
| `request.cancel` | `{"id": "<request id>", "method": "approval", "reason": "..."}` | withdraws an open approval/clarify | `:1073-1074`; fixture seq 9 |
| `connection.request` / `connection.update` | connection prompt payload | connection (credential/host) requests | `:1075-1079` |
| `todo.updated` | plan payload | plan view | `:1081-1082` |
| `subagent.spawn_requested`, `subagent.start`, `subagent.progress`, `subagent.tool`, `subagent.complete` | child events | delegated work roster only | `:1080-1082` |
| `status.update` | `{"kind": "compacted", ...}` | compaction finished | `:1083-1085` |
| `btw.complete`, `background.complete`, `session.control.update` | side task payloads | side tasks | `:1086-1088` |
| `session.reclaimed` | `{"session_id", "stored_session_id", "reason"}` | runtime was torn down; rebuild | `HermesConversation.swift:479` |

`session.info` payload (measured, fixtures `turn-frames.json` and `session-resume.json`), abridged:
```json
{
  "running": true,
  "turn_started_at": 1790318117.362543,
  "stored_session_id": "20260925_023517_59d8c3",
  "title": "…",
  "model": "…", "provider": "…", "cwd": "/…", "terminal_backend": "…",
  "profile_name": "inbox-triage", "version": "0.21.5",
  "yolo": false, "fast": false, "reasoning_effort": "…", "approval_mode": "…",
  "usage": {"calls": 1, "input": 14215, "output": 23, "total": 14238, "context_used": 14215, "context_max": 1048576, "context_percent": 1, "avg_latency_s": 4.8}
}
```
`stored_session_id` changes after a `/compress` or compaction (`HermesChatTurnCoordinator.swift:applyInfo`, `engine.adoptStoredKey`).

`message.complete` payload (measured):
```json
{
  "status": "complete" | "interrupted" | "error",
  "text": "ok",
  "partial": true?,
  "persisted_turn": {"complete": true, "user_row_id": 78, "final_assistant_row_id": 79, "row_ids": [78, 79]},
  "usage": {"calls": 1, "input": 14215, "output": 23, "total": 14238, "avg_tps": 4.8, "context_used": 14215, "context_max": 1048576, "context_percent": 1}
}
```
Mapping (`HermesChatTurnCoordinator.swift:~1113-1148`, `complete`): `interrupted` → cancelled, `error` → failed, anything else → completed. `text` is shown as the reply unless the outcome is failed without `partial: true`. `persisted_turn` (0.21.5) names the rows that replace the streamed ones.

### 4.3 Turn end rule

From `HermesChatTurnCoordinator.swift`:
- The turn is over when a `session.info` with `running: false` arrives (`applyInfo` → `finish(...)`, `:~1094-1115`). Measured: fixture seq 9 `message.complete`, then seq 10 `session.info` with `running: false`.
- `message.complete` sets the turn's ending; it finishes the turn immediately only when the host already reports idle (`complete()`, last line `if !hostRunning { finish(ending) }`). Otherwise it waits for `session.info running:false`.
- `error` during a turn sets the ending to failed and waits for the idle `session.info`; `error` before the turn's own `message.start`, or when the turn never began, finishes it as failed at once (`:1060-1072`).
- A new `message.start` while the last turn waits for idle ends that turn (`:1011-1019`).
- Finish with `stopRequested` → cancelled (`applyInfo`).
- A snapshot on reattach that says the turn ended settles it (`reconcile`).

Practical rule for a minimum client: the turn ends on `session.info {running:false}`; treat `message.complete` as the final text and status, and do not end on `message.complete` alone.

### 4.4 Full example: send and stream

Client, id 7 (`HermesCall.swift:402-403`):
```json
{"jsonrpc":"2.0","id":7,"method":"prompt.submit","params":{"session_id":"e7d1ef0d","text":"Reply with exactly: ok. Do not use tools.","queued":true}}
```
Host reply (shape from the test stub `HermesTranscriptPagingTests.swift:181`; not a captured frame):
```json
{"jsonrpc":"2.0","id":7,"result":{"status":"streaming"}}
```
Host events. These are the captured frames of `HermesMobileTests/Fixtures/HermesAgent/turn-frames.json`, with fixture values intact. Events can interleave with the reply; the client keys on `seq`, not on arrival order versus the reply.
```json
{"jsonrpc":"2.0","method":"event","params":{"seq":1,"session_id":"e7d1ef0d","type":"session.info","payload":{"running":true,"turn_started_at":1790318117.362543,"stored_session_id":"20260925_023517_59d8c3","version":"0.21.5","yolo":false,"usage":{"calls":0,"input":0,"output":0,"total":0}}}}
{"jsonrpc":"2.0","method":"event","params":{"seq":2,"session_id":"e7d1ef0d","type":"message.start"}}
{"jsonrpc":"2.0","method":"event","params":{"seq":3,"session_id":"e7d1ef0d","type":"session.title","payload":{"session_id":"20260925_023517_59d8c3","title":"fixture-redacted"}}}
{"jsonrpc":"2.0","method":"event","params":{"seq":4,"session_id":"e7d1ef0d","type":"thinking.delta","payload":{"text":"( ͡° ͜ʖ ͡°) computing..."}}}
{"jsonrpc":"2.0","method":"event","params":{"seq":5,"session_id":"e7d1ef0d","type":"thinking.delta","payload":{"text":""}}}
{"jsonrpc":"2.0","method":"event","params":{"seq":6,"session_id":"e7d1ef0d","type":"message.delta","payload":{"text":"ok"}}}
{"jsonrpc":"2.0","method":"event","params":{"seq":7,"session_id":"e7d1ef0d","type":"thinking.delta","payload":{"text":""}}}
{"jsonrpc":"2.0","method":"event","params":{"seq":8,"session_id":"e7d1ef0d","type":"reasoning.available","payload":{"text":"ok"}}}
{"jsonrpc":"2.0","method":"event","params":{"seq":9,"session_id":"e7d1ef0d","type":"message.complete","payload":{"status":"complete","text":"ok","persisted_turn":{"complete":true,"user_row_id":78,"final_assistant_row_id":79,"row_ids":[78,79]},"usage":{"calls":1,"input":14215,"output":23,"total":14238,"context_used":14215,"context_max":1048576,"context_percent":1,"avg_latency_s":4.8,"avg_tps":4.8}}}}
{"jsonrpc":"2.0","method":"event","params":{"seq":10,"session_id":"e7d1ef0d","type":"session.info","payload":{"running":false,"turn_started_at":null,"stored_session_id":"20260925_023517_59d8c3","yolo":true,"usage":{"calls":1,"input":14215,"output":23,"total":14238}}}}
```
Client result: text "ok" shown; turn finished at seq 10.

A longer exchange with a tool call and an approval (fixture `turn-tool-approval-frames.json`, abridged):
```
seq 1  session.info   running:true, turn_started_at:1790745552.718498
seq 2  message.start
seq 3  thinking.delta "(´･_･`) formulating..."
seq 5  session.usage  {usage:{calls:1,...}}
seq 6  reasoning.available "Let me run a quick check."
seq 7  message.interim {text:"Let me run a quick check.", already_streamed:false}
seq 8  tool.start     {tool_id, name:"terminal", args:{command:"python3 -c \"print(1)\""}, context}
  (server request, string id, no seq)
       {"jsonrpc":"2.0","id":"srq-627297cd2bb0","method":"approval","params":{"session_id":"7dd4dff2","request_id":"850a0a9d32c448c2b4b9bc34503f5812","command":"python3 -c \"print(1)\"","description":"script execution via -e/-c flag","choices":["once","session","always","deny"],"allow_session":true,"allow_permanent":true,"pattern_key":"…","pattern_keys":["…"]}}
seq 9  request.cancel {id:"srq-627297cd2bb0", method:"approval", reason:"…"}   (answered elsewhere / withdrawn)
seq 10 tool.complete {tool_id, name:"terminal", args, duration_s:0.53, result:{exit_code:0, output, error:null, approval}}
seq 11 thinking.delta, seq 13 reasoning.available "The command returned: 1."
seq 14 message.complete {status:"complete", text:"The command returned: 1.", persisted_turn:{…}}
seq 15 session.info   running:false
```

---

## 5. Stop, interrupt, steer

- **Stop**: `session.interrupt` with `{"session_id": "<runtime>"}` (`HermesCall.swift:416`, `:~415-416` params). Client sets `stopRequested`, sends it, then clears any queued prompt and withdraws open requests locally (`HermesChatTurnCoordinator.swift:704-722`). Host discards its queued prompt, withdraws requests and denies pending approvals (doc, `:~704-707`). The turn then ends through events (`message.complete` with `status: "interrupted"`, then `session.info running:false`; `:1113-1116` mapping).
- **Steer** (guidance into a running turn): `session.steer` with `{"session_id", "text"}` (`HermesCall.swift:413-415`). Reply `status`: `"queued"` → guidance queued; `"rejected"` → refused (`BotPromptMode.swift:~40-53`; codes 4002/4010 refused, `BotProtocol`/`HermesGateway.swift:500`).
- **Redirect** (Stop & send): `session.redirect` with `{"session_id", "text"}` (`HermesCall.swift:413-415`). Reply `status`: `"redirected"`, `"queued"`, `"rejected"` (`BotPromptMode.swift:~44-50`).
- Steer and redirect errors are `BotSettingFailure`-style messages (`HermesGateway.swift:386-388`, rejection kind `.setting`, see `:495-505`).

---

## 6. Approvals, clarifications, credentials

Server requests (string `id`, `method` = kind, `params.session_id`). Forwarded by the gateway unchanged (`HermesGateway.swift:367-369`; `HermesConversation.swift:92`).

**approval** (fixture `turn-tool-approval-frames.json:153-175`, measured):
```json
{"jsonrpc":"2.0","id":"srq-627297cd2bb0","method":"approval",
 "params":{"session_id":"7dd4dff2","request_id":"850a…","command":"python3 -c …","description":"…",
           "choices":["once","session","always","deny"],"allow_session":true,"allow_permanent":true,"pattern_key":"…","pattern_keys":["…"]}}
```
Answer (client call): `approval.respond` with `{"session_id", "request_id", "choice"}`, `choice` ∈ `once|session|always|deny` (`HermesCall.swift:420-421`). Reply `{"resolved": <int>}`: `>0` answered, `0` already resolved, missing or negative is uncertain (`HermesConversation.swift:315-320`). Parsing: `BotPendingRequest.swift:52-58` (`method == "approval"`).

**clarify** (`BotPendingRequest.swift:58-61`): server request with `method: "clarify"`. Answer per question: `clarify.lock {"request_id", "question_id", "answer"}` (`HermesCall.swift:426-427`), reply `{"status": "ok", "remaining": [...]}`; `"expired"` means dropped. Single unkeyed answer: `request.answer {"id": "<envelope id>", "result": {"answer": "..."}}` (`HermesCall.swift:424-425`). Reply `{"status": "ok"|"expired"}` (`HermesConversation.swift:309-332`).

**credential/value prompt** (sudo, secret): kinds come from `BotCredentialRequest.Kind` (`BotPendingRequest.swift:64-68`, not read). Answer with `request.answer {"id", "result": {"value": "..."}}`, empty value skips (`HermesCall.swift:422-423`; `HermesConversation.swift:305-308`).

**connection** (host or credential connection, 0.21.5): events `connection.request` / `connection.update` (`HermesChatTurnCoordinator.swift:1075-1077`). Respond with `connection.respond`: `{"op_id", "result": <answer>, "owner": {"type": "session", "session_id": "<runtime>"}}` on 0.21.5, or `{"op_id", "result", "session_id"}` on 0.21.4 (`HermesCall.swift:428-433`, `:494-500`). Answers: `continue` without, `skip(target)`, `connect(target, env)` (`HermesCall.swift:571-577` validity list).

**Withdrawal**: `request.cancel` event (§4.2). `request.cancel` also appears as a sequenced event, fixture seq 9.

Calls to answer must be resent only after a fresh snapshot; the client never resends a write (`HermesConversation.swift:121-125`).

---

## 7. Models and per-session settings

Model list: `model.options` with `{"session_id": "<runtime>", "profile": "<p>"}` (`HermesCall.swift:435-436`, `HermesCall.swift:312`). Requires a `providers` array or the reply is `unsupported` (`BotChatControls.swift:119-124`).

Reply shape (`HermesModelCatalog.swift:13-37`, measured from parser):
```json
{"model": "<active model id>", "provider": "<active provider slug>",
 "providers": [{"slug": "openai", "name": "OpenAI", "authenticated": true,
                "models": ["gpt-x", "gpt-y"], "unavailable_models": ["gpt-y"],
                "capabilities": {"gpt-x": {...}}}]}
```
Rules: a provider with `authenticated: false` is dropped; models in `unavailable_models` are dropped; a provider with no models is dropped (`:17-28`).

Configured-only variant: `model.options` with `{"include_unconfigured": false}` (`HermesCall.swift:437`, `configuredModelOptions`).

Set model for the session: `config.set`:
```json
{"session_id": "<runtime>", "profile": "<p>", "scope": "session", "key": "model",
 "value": "<model-id> --provider <slug> --session", "confirm_expensive_model": false}
```
(`HermesCall.swift:439-445`; value built at `HermesModelCatalog.swift:41-48`, `sessionModelValue`.) Reply must echo `key == "model"` and `scope == "session"` (`BotChatControls.swift:184`).

Other settings through `config.set` (`HermesCall.swift:439-455`):
- `reasoning`: `key "reasoning"`, value one of `none, minimal, low, medium, high, xhigh, max, ultra` (`HermesModelCatalog.swift:6`).
- `fast`: `key "fast"`, value `"fast"` or `"normal"`.
- `yolo`: `key "yolo"`, value `"on"` or `"off"`.
- `personality`: `key "personality"`, no `scope` field (`HermesCall.swift:441`).

New session with a chosen model: `session.create` params include `model` and `provider` (`HermesCall.swift:365`).

Working folder: `session.cwd.set {session_id, profile, cwd}` (`HermesCall.swift:456-457`). Folder moves: `session.workspace.move {profile, session_key, cwd}` (`HermesCall.swift:377-378`).

---

## 8. Other gateway methods the chat uses

Listed with params (all measured from `HermesCall.swift`; method names at `:276-340`):

| Method | Params | Purpose | Essential for minimum chat? |
|---|---|---|---|
| `session.list` | `profile, title, include_hidden` | Bot Chat identity | Only for Bot Chats |
| `session.create` | `profile, source, cwd?, model?, provider?` (or `profile, title, hidden, follow_profile_config, source`) | new session | Yes for new chats |
| `session.resume` | `profile, session_id(stored key), close_on_disconnect:false, source, omit_messages?` | attach | **Yes** |
| `session.events.since` | `session_id(runtime), last_seen` | replay | **Yes** (reconnect) |
| `prompt.submit` | `session_id, text, queued:true` | send | **Yes** |
| `session.interrupt` | `session_id` | stop | **Yes** |
| `approval.respond` | `session_id, request_id, choice` | approval answer | **Yes** if tools may need approval |
| `request.answer` | `id, result` | clarify (unkeyed), credential | Recommended |
| `clarify.lock` | `request_id, question_id, answer` | clarify batch | Recommended |
| `client.capabilities` | `server_requests:true` | enables approvals/clarify | **Yes** (first frame, see 2.2) |
| `gateway.ping` | `{}` | heartbeat | **Yes** (every 15 s) |
| `session.steer` / `session.redirect` | `session_id, text` | steer / stop & send | Optional |
| `model.options` / `config.set` | see §7 | model picker | Optional |
| `session.title` | `session_id, title` | rename (Bot Chat) | Optional |
| `session.rename` (also method `session.title`) | `session_id, title` | rename | Optional |
| `session.close` | `session_id` (runtime) | close runtime | Optional (clean detach) |
| `session.delete` | `profile, session_id(stored key)` | delete | Optional |
| `session.branch` | `session_id, name?, count?` | fork | Optional |
| `session.undo` | `session_id` | `/undo` | Optional |
| `session.compress` | `session_id, focus?, profile` | `/compress` (660 s deadline) | Optional |
| `prompt.rewind` (method `prompt.submit`) | `session_id, text, truncate_before_row_id, confirm_truncate, confirm_empty_truncate, confirm_deep_truncate?` | retry/edit | Optional |
| `prompt.btw`, `prompt.background` | `session_id, text` | side tasks | Optional |
| `commands.catalog` | `session_id` | slash command list | Optional |
| `command.dispatch` | `name, arg, session_id` | skill / goal | Optional |
| `slash.exec` | `session_id, command` | slash commands | Optional |
| `complete.path`, `complete.folder`, `complete.slash` | word/text + session/profile | `@` path and `/` completion | Optional |
| `file.attach` | `session_id, name, data_url` | document upload | Optional |
| `subagent.list`, `subagent.tail`, `subagent.interrupt` | `session_id, subagent_id?` | delegated work | Optional |
| `llm.oneshot` | diff, commits, avoid, session_id, profile | commit message | Optional |
| `insights.get` | `days (1-365), profile` | usage insights (gateway variant) | Optional |
| `profiles.list`, `profiles.describe`, `profiles.create`, `profiles.configure`, `profiles.get_asset`, `profiles.set_asset` | see `HermesCall.swift:348-356`, `:519-527` | profiles | Profiles list needed; rest optional |
| `groups.*` | `room_id`, etc. | group rooms | Optional |
| `projects.*` | `profile, id, name, folder, color` | projects tree | Optional |

Minimum-chat methods are the bolded rows plus `session.list` for Bot Chats. Everything else is capability-gated: `-32601` makes the client mark the method unavailable and hide its UI (`HermesGateway.swift:380-382`, `noteUnavailable`).

---

## 9. REST endpoints used beyond chat

All paths relative to `{base}`. Methods and query names are measured from `HermesREST.swift` (line numbers given). All except `status`, `auth/*` and `ws-ticket` need the session cookie or bearer.

Auth and server: `GET /api/status` (`:343`); `POST /auth/password-login` (`:360`); `POST /auth/native/refresh` (`:364`); `POST /auth/native/token` (`:368`); `GET /api/auth/me` (`:372`); `POST /api/auth/ws-ticket` (`:374`); `GET /api/profiles/active` (`:373`); `GET /api/health` (`:344`).

Skills (`HermesSkillsClient.swift`, `HermesREST.swift:519-527`):
- `GET /api/skills?profile=<p>` → list.
- `PUT /api/skills/toggle` body `{name, enabled, profile?}` (`:520-523`).
- `GET /api/skills/content?name=<name>&profile=<p>` → `{name, content, path}` or 404 `{detail}` (`:524-527`).

Cron (`HermesCronClient.swift`, `HermesREST.swift:499-517`):
- `GET /api/cron/jobs` → bare array across Profiles (`:~1040 doc`).
- `POST /api/cron/jobs` create (`:500`).
- `PUT`/`PATCH` update: `POST /api/cron/jobs/{id}` style (`:502-511`); pause and resume append `/pause`, `/resume`.
- `DELETE /api/cron/jobs/{id}` (`:508`).
- `POST /api/cron/jobs/{id}/trigger` (`:509-510`).
- `GET /api/cron/jobs/{id}/runs?limit=1-100` (`:511-514`).
- `GET /api/cron/delivery-targets` (`:517`).
(Exact method per route: read `HermesREST.swift:499-517` to confirm; `Self.bare("DELETE", …)` at `:508` is measured.)

Insights (`HermesInsightsClient.swift:5`, `HermesREST.swift:289-293`, `:674`):
- `GET /api/analytics/usage?days=<1-365>&profile=<p>` → `{daily, totals, by_model, period_days}`.
- `GET /api/analytics/models?days=&profile=` → `{models, totals, period_days}`.
- 503 with `detail.error` starting `state_db` means unavailable (`HermesInsightsClient.swift:70`).

Memory screen (`HermesREST.swift:38`, `:596`, `:602-604`, `:401-402`):
- `GET /api/config?profile=<p>`; `PUT /api/config` deep-merges the body's `config` (`:596`; `HermesREST.swift` doc, `#1278`).
- `GET /api/profiles/{name}/soul`, `PUT` with `{content}` (`:604`, `:286`).
- `PUT /api/env` `{key, value}` writes a managed environment value at host root (`:401-402`).
- Files: `GET /api/fs/list?path=`, `GET /api/fs/read-text?path=`, `POST /api/fs/write-text {path, content}`, `POST /api/files/mkdir {path}` (`:530-545`, `:589-593`).

Sessions (REST, §3.2-3.4): `GET /api/sessions` (`:440-453`); `GET /api/sessions/search?q=&profile=&limit=` (`:485-495`; `limit` ≤ 100, default 20); `PATCH /api/sessions/{key}` (`:466-469`) with `{title}` or pin/archive flags; `GET /api/sessions/{key}` row (`:476`); `GET /api/sessions/{key}/export` (`:473-475`); `POST /api/sessions/import` (`:479-484`).

Speech: `POST /api/audio/transcribe` (`:385-387`) with base64 `dataURL` and `mimeType`, up to 25 MiB; `POST /api/audio/speak` (`:~610`).

Updates and plugins (optional, not chat): `GET /api/hermes/update/check`, `POST /api/hermes/update`, `GET /api/hermes/update/receipt`, `GET /api/actions/hermes-update/status?lines=40` (`:345-359`); `GET /api/dashboard/plugins/hub`; `GET|POST /api/dashboard/agent-plugins…`; `api/plugins/hermex-push/…` (`:412-422`); `POST /api/gateway/restart`.

Kanban (optional): `GET /api/plugins/kanban/...` (`:684`), events `GET ws /api/plugins/kanban/events?board=&since=` (`:705-716`), using its own ticket.

Git (optional): `/api/git/status`, `/api/git/review/list`, `/api/git/review/diff`, `/api/git/review/commit`, `/api/git/review/push`, `/api/git/branches`, etc. (`:546-603`). `/api/fs/git-root` (`:546-547`).

Downloads: `GET /api/fs/download?path=&profile=&session_id=` (`:388-398`).

---

## 10. Minimum viable client (what an Android build must do)

1. `GET /api/status`: refuse if `version` < 0.21.3 (missing version = allowed); require `auth_required`; read `install_id` and pin it.
2. Sign in with `POST /auth/password-login {provider:"basic",username,password}` (HTTP 200) and keep the session cookie. Verify with `GET /api/auth/me` `provider == "basic"`. (Browser-only hosts: PKCE flow §1.3, out of minimum scope.)
3. `POST /api/auth/ws-ticket` → `{ticket}`.
4. Open `wss://…/api/ws` (or `ws://`), `Sec-WebSocket-Protocol: hermes-gateway-v1, hermes-gateway-ticket.<ticket>`, same cookie jar.
5. Wait for `event` `gateway.ready` → read `params.payload.replay_epoch`.
6. Send `client.capabilities {server_requests:true}` as id 1 (tolerate -32601).
7. Start `gateway.ping` every 15 s with string ids. Treat 45 s of silence as a dead socket.
8. Attach: `session.resume {profile, session_id:<stored key>, close_on_disconnect:false, source:"hermex", omit_messages:true}`; for a new chat `session.create {profile, source:"hermex"}`. Keep `session_id` (runtime) and `session_key` (stored).
9. Replay: `session.events.since {session_id:<runtime>, last_seen}`; apply `events` in order; then `session.resume` again for live state (`running`, `turn_started_at`, `inflight`).
10. Transcript: `GET /api/sessions/{key}/messages?profile=&order=latest&limit=100&offset=N&include_compacted=true`, oldest first, paged until a short page.
11. Send: `prompt.submit {session_id, text, queued:true}`; expect `status` "streaming" or "queued".
12. Render events by `type` (§4.2): `message.start`, `message.delta`, `message.interim`, `tool.start`, `tool.complete`, `thinking.delta` (spinner only), `message.complete` (final text), `error`, `session.info` (`running:false` ends the turn).
13. Approvals: on server request `method:"approval"` show choices; answer `approval.respond {session_id, request_id, choice}`; on `request.cancel` drop the card.
14. Stop: `session.interrupt {session_id}`.
15. Reattach on any socket drop, and on error code 4001 from a write; retry `session.resume` refusals 4007/4009 for 60 s.

Sessions list minimum: `GET /api/sessions` with the paging in §3.2.

---

## 11. Open questions and ambiguities

1. **Reasoning events.** The Swift switch handles `reasoning.delta` as reasoning (`HermesChatTurnCoordinator.swift:1035-1039`), but no fixture emits it. The fixtures show `thinking.delta` (spinner text) and `reasoning.available` carrying the answer, and the code comment says neither is reasoning (`:1090-1093`). Whether any host version sends real reasoning text is unknown.
2. **Turn end without `session.info`.** The turn ends on `session.info running:false`. If a host sent `message.complete` and never a final `session.info`, the turn stays open. Fixtures show it always does; no test covering a missing event was read.
3. **Captured frames missing.** There is no captured `gateway.ready` fixture (only the stub at `BotClientTests.swift:1011`), no captured `ws-ticket` reply (stub `HermesConnectionTests.swift:921`), and no captured `prompt.submit` reply (stub `HermesTranscriptPagingTests.swift:181`). Shapes above are from code plus stubs.
4. **Session cookie details.** Cookie name, path and attributes are not in the code. The header policy mentions `X-Forwarded-Prefix` for "the session cookie's path" (`HermesConnection.swift:682-688`). A Kotlin client must use a cookie jar that keeps the host's cookie across the upgrade (guess: the gateway upgrade needs that cookie; inferred from `HermesGateway.swift:275` using the connection's session).
5. **Ticket reuse.** Tickets are single-use per the code comments (`HermesREST.swift:374`, `HermesConnection.swift:403-404`), and each socket mints one. The host's TTL is not stated.
6. **Backoff schedule.** `HermesConversation` reconnects "on a backoff" (`:117`), but the delays are not read. The 60 s window for 4007/4009 is measured (`:129`).
7. **Minimum version vs feature set.** The minimum is 0.21.3 (`HermesCompatibility.swift:9`), but several routes were verified only at 0.21.5 or at `ca678285` (`HermesREST.swift:1-30`). The `connection.respond` field changed between 0.21.4 and 0.21.5 (`HermesCall.swift:428-433`). Features beyond the minimum chat set are capability-gated by `-32601`, not by version.
8. **Pre-0.21.5 `session.resume` reply.** The reduced reply for a not-yet-started session uses `stored_session_id` instead of `session_key` (`HermesConversation.swift:444-448`). Whether older hosts send `session_key` is not stated.
9. **Bot Chat versus Hermes session.** A Bot Chat is found by its title "Bot Chat" (`HermesCall.swift:14`, `:357-358`), and a hidden session is created with `hidden: true` and `follow_profile_config: true` (`HermesCall.swift:359-361`). Plain sessions are created without these. The Android client has to choose one flow; the minimum for a "chat with Hermes" app is the plain session.
10. **Gateway vs REST session list.** The list screens use REST `GET /api/sessions` (`HermesSessionPage.swift`), while identity uses gateway `session.list` (Bot Chat only). Both return rows with `id`, `title`, `last_active`, etc. Their exact field parity is not checked here.
11. **Credential kinds.** `BotCredentialRequest.Kind` (sudo, secret, and others) was not read (`BotPendingRequest.swift:64-68`).
12. **Not read.** `BotConnection.swift`, `BotConnectionView.swift`, `HermesGatewayTests.swift` (the handshake, close and reap tests) and the body of `HermesBrowserSignInTests.swift` were not opened. They are the best next source for exact reconnect and browser-flow behavior.
13. **Search.** Whether the gateway `session.list`'s `include_hidden` returns rows the REST list hides is not checked.
14. **Server-request replies.** §2.3 says the gateway never answers a server request itself (`HermesGateway.swift:19-20`), and the client answers with `approval.respond`, `clarify.lock` or `request.answer`. Whether the host also expects a JSON-RPC response frame keyed to the string id is not shown in the code read (guess: no, since the Swift never sends one; unverified).
15. **Line numbers marked "~" are approximate.** Several citations in §3.3, §3.4, §4.1 and §9 were located by grep context rather than exact line; re-check before relying on them to the line.
