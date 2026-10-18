# Panels B: Kanban, Workspace files, Bots inbox, session actions

Hermes server only. iOS repo `B` = `/private/tmp/claude-501/-Users-dchungdev-hermex-android/9a337a95-8f6e-479b-8a94-cb75cc8eee82/scratchpad/src/hermex`. Read the cited Swift for layout and copy. [measured] = live 0.21.5 host.

## Kanban — `B/HermesMobile/Features/Kanban/*` (main view `KanbanLabView.swift:2420`), client `Networking/Hermes/HermesKanbanClient.swift`, model `Models/Kanban.swift:571-625`, notes `B/docs/agents/kanban.md` § Hermes

Base `/api/plugins/kanban`; every card route takes `?board=<slug>`. 404 on `/config` → plugin absent: "Kanban isn’t available on this server." / "Turn on the Kanban plugin on the Hermes host, then try again."

| Call | Shape |
|---|---|
| `GET /config` | `{default_tenant, lane_by_profile, include_archived_by_default, render_markdown}` [measured] |
| `GET /boards` | `{boards:[{slug,name,description,icon,color,archived,is_current,counts,total}], current}` [measured] |
| `GET /board?board=&tenant=&include_archived=` | `{columns:[{name, tasks:[card]}], tenants, assignees, latest_event_id, now}` [measured]; column order triage, todo, scheduled, ready, running, blocked, review, done |
| `GET /stats?board=` | `{by_status, by_assignee, oldest_ready_age_seconds, now}` |
| `GET /assignees?board=` | `{assignees:[{name,on_disk,counts}]}` [measured] |
| `GET /tasks/{id}?board=` | `{task, comments, events, attachments, links{parents,children}, link_tasks, child_results, runs}`; 404 `{detail}` |
| `GET /tasks/{id}/log?board=&tail=` | `{task_id, exists, size_bytes, content, truncated}` |
| `POST /tasks?board=` | body `{title, body?, assignee: string|null (never ""), tenant?, priority?, workspace_kind?, parents?, triage: bool, idempotency_key, skills?, max_runtime_seconds?}` → `{task, warning?}` |
| `PATCH /tasks/{id}?board=` | `{title?, body?, priority?, assignee? ("" unassigns), status?}`; Block = `{status:"blocked", block_reason?}`; Unblock = `{status:"ready"}`; never set `running` |
| `POST /tasks/{id}/comments?board=` | `{body}` |
| `POST /tasks/bulk?board=` | `{ids, status|assignee|priority|archive:true}` → `{results:[{id,ok,error?}]}` |
| `POST /dispatch?board=&dry_run=true|false&max=8` | dispatch result |
| `POST /boards`, `PATCH /boards/{slug}`, `DELETE /boards/{slug}?delete=false` (archive), `POST /boards/{slug}/switch` | board management |
| live: WS `/api/plugins/kanban/events?board=&since=&ticket=<ws-ticket>` | frames `{events, cursor}`; ping 25 s; on 3rd failure show "Live updates delayed" and poll `/board` every 30 s. Optional. |

Card: `id, title, status, assignee, body, tenant, priority, comment_count, link_counts, age{created_age_seconds, started_age_seconds}, created_at, updated_at, workspace_kind, skills, max_runtime_seconds, current_run_id`. Decode tolerantly; drop host paths.
UI: title "Kanban", search "Search Cards"; board picker in header; horizontal Status selector with counts (Triage, To Do, Scheduled, Ready, Running, Blocked, Review, Done), one status at a time as a vertical card list; card row: id, priority, title, markdown preview, profile or "Unassigned", comment/dependency counts, age. Toolbar: New Card (+), Dispatcher ("Preview Dispatch", "Run Dispatcher" with confirm; "Preview is advisory and may become stale. It never starts workers."). Card detail: Description ("No Content"), Metadata, Dependencies, comments + composer ("Comments cannot be edited or deleted after sending."), worker log; menu Move (Triage/Ready), Block (on Ready/Running), Unblock (on Blocked), Complete (on Review → done), Archive. Editor "New Card"/"Edit Card": Title, Description, Status (Triage/Ready), Priority (-100..100), Profile ("Unassigned"), Workspace (Scratch/Worktree/Directory), Skills, Max runtime.
States: "Loading Kanban"; "No Cards in this Status" / "Choose another Status or refresh the Board."; "Kanban could not reach the server." / "Check your connection, then try again."

## Workspace files — `B/HermesMobile/Features/Workspace/*`, `HermesWorkspaceFileClient.swift`

Entry: the chat header's trailing folder button opens the browser rooted at the session's `cwd` (from `session.info` / session row `cwd`). Home sidebar has no Workspace row on iOS Hermes; also allow it from Settings? No: chat only.
- `GET /api/fs/list?path=<abs>` → `{entries:[{name,path,isDirectory}]}` [measured]; missing → 200 `{entries:[], error:"ENOENT"}`.
- `GET /api/fs/read-text?path=` → `{text, binary, truncated, byteSize, language, mimeType, path}` [measured]; directory → 400 `{detail:"Path points to a directory"}`.
- `GET /api/fs/download?path=&profile=&session=` full file (share/save; images preview).
Refuse `..`/`~` before requesting. One folder per screen, folders first, push into subfolders, breadcrumb title. File viewer: monospace text with line numbers (markdown rendered for .md), "Preview truncated", binary → "Preview is not available for this file type.". "Search files" filters the loaded tree. Empty "No Files", "No Matches". Title "Files". File-type icons from `B/HermesMobile/Resources/Assets.xcassets/FileIcons/*` (map by extension per `B/HermesMobile/Features/Workspace/FileIcon.swift`).

## Bots inbox — `B/HermesMobile/Features/Bots/BotsInboxView.swift`, `BotInbox.swift`, `BotConnection.swift`; fixture `B/HermesMobileTests/Fixtures/HermesAgent/profiles-list.json`

Gateway only:
- `profiles.list {include_sessions:true}` → `{profiles:[{name, display_name, description, has_avatar, is_default, model, skill_count, path, canonical_session{id, preview, last_active, title, message_count}, ui_meta{"hermes-bots":{title,pinned,hidden,sectionId,sectionName,color,...}}}]}`.
- `session.active_list` → live runtimes with `session_key` and status (`working|starting|streaming` → "Working", `waiting` → "Waiting for you").
- Opening a bot: its Bot Chat is the profile's `canonical_session.id` (stored key) → open the normal chat screen with that key and profile. If none, create with `session.create {profile, title:"Bot Chat", hidden:true, follow_profile_config:true, source:"hermex"}`.
Rows: 44dp avatar (initials on profile color), name (headline), preview line (else description), trailing time or live status in attention colors, unread dot. Hidden bots 50% alpha. Empty "No bots yet. Tap + to create one.". Search "Search bots and messages". Error "Could Not Load Profiles". Group rooms, bot create/edit/delete: out of scope for now.

## Session actions — `B/HermesMobile/Features/SessionList/*`, `Networking/Hermes/HermesSessionActions.swift`

- List: `GET /api/sessions?profile=&order=recent&archived=exclude|only&limit=50&offset=&min_messages=1&exclude_sources=cron,kanban,oneshot,subagent,tool` → `{sessions, total, limit, offset}` [measured]; paginate on scroll.
- Search: `GET /api/sessions/search?q=&profile=&limit=20&exclude_sources=...` → `{results:[...]}` [measured]; plus client-side filter of loaded rows. Prompt "Search sessions".
- Rename: `PATCH /api/sessions/{id}` `{title, profile}`; 400 `{detail}` shown. Sheet "Rename Session", field "Session title".
- Pin / Archive / Unread: `PATCH` `{pinned}` / `{archived}` / `{unread}`. "Pinned" section on top. Archive toast "Archived" with "Undo". "Archived Sessions" screen uses `archived=only`. Labels "Mark as Read"/"Mark as Unread".
- Delete: gateway `session.active_list`; if this client's runtime is mid-reply → "This session is still replying here. Stop the reply first, then delete it."; `session.close {session_id: runtime}` for own runtimes; `session.delete {session_id: storedKey, profile}`; error 4023 → "This session is open in another app, so nothing was deleted. Close it there, then try again." Confirm "Delete Session?".
- Duplicate: `GET /api/sessions/{id}/export?profile=` → `POST /api/sessions/import {sessions:[export with new id, no parent], profile}` → PATCH title "<title> (copy)" (then "(copy 2)"…). 413 → "This session is too large to duplicate.".
- Fork: chat message action "Fork From Here" → gateway `session.branch {session_id: runtime, name?, count?}`; open the returned session.
Row long-press menu: Rename, Pin/Unpin, Mark as Read/Unread, Duplicate, Archive, Delete.
