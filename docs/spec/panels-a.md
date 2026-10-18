# Panels A: Tasks, Skills, Memory, Usage (Insights), Active Profile, Projects

Hermes server only. Reference: iOS repo `B` = `/private/tmp/claude-501/-Users-dchungdev-hermex-android/9a337a95-8f6e-479b-8a94-cb75cc8eee82/scratchpad/src/hermex`. Read the cited Swift files for layout and copy. [measured] = seen on the live 0.21.5 host; otherwise from Swift code.

REST error rule (`HermesCronClient.swift:167-201`): 2xx body; 4xx with `detail` → show "The server rejected the request: \(detail)"; 403/502-504/520-530 → rejected; else HTTP error.

## Tasks (cron) — editable. `B/HermesMobile/Features/Tasks/*`, `Networking/Hermes/HermesCronClient.swift`, `Models/Cron.swift`

| Action | Call | Notes |
|---|---|---|
| List | `GET /api/cron/jobs` → bare array [measured `[]`] | all profiles |
| Delivery targets | `GET /api/cron/delivery-targets?profile=` → `{targets:[{id,name,home_target_set,home_env_var}]}` [measured: `local` "Local (save only)", `bot-chat:default` "Bot Chat (default)"] | |
| Create | `POST /api/cron/jobs?profile=` body `{schedule, prompt, name, deliver, skills:[], model?, provider?}` | 424 = saved but scheduler failed: show `detail.error` as warning, reload |
| Edit | `PUT /api/cron/jobs/{id}?profile=` body `{updates:{prompt?,schedule?,name?,deliver?,skills?,model?,provider?}}` | |
| Pause / Resume | `POST /api/cron/jobs/{id}/pause` / `/resume` `?profile=` | |
| Run now | `POST /api/cron/jobs/{id}/trigger?profile=` | long timeout (returns when run ends); 409 `detail` if running |
| Delete | `DELETE /api/cron/jobs/{id}?profile=` → `{ok}` | |
| Runs | `GET /api/cron/jobs/{id}/runs?profile=&limit=100` → `{runs:[...], limit}` newest first | run row: `id` (`cron_<job>_<YYYYmmdd_HHMMSS>`), `started_at`, `ended_at`, `is_active`, `model`, `input_tokens`, `output_tokens`, `actual_cost_usd` or `estimated_cost_usd` |
| Run output | `GET /api/sessions/{runId}/messages?profile=` → last assistant message without tool_calls | 404 → "This run is no longer on the server." |

Job: `id, name, prompt, schedule{kind, expr|expression, run_at, every}, schedule_display, enabled, state, next_run_at, last_run_at, last_status, last_error, last_delivery_error, repeat{times,completed}, deliver, skills, model, provider, profile, fire_claim{at}, latest_execution{status}, scheduler_heartbeat_age_s`. Dates may be epoch numbers or ISO strings (decode both).

List: segmented filter "All n" / "Live n" / "Paused n" / "Errors n"; rows with title, status badge ("Running", "Last run failed", "Needs Attention", "Paused", "Active"), description, label/value grid (Schedule, Next, Last, Deliver, Skills) per screenshot `B/docs/assets/readme/screenshot-tasks.png`; "Running now" card; stall banner "Scheduled Tasks aren't running. The host's scheduler hasn't checked in for \(stalled)."; toolbar New Task (+) and Refresh; row menu Delete / Run Now / Pause|Resume.
Detail: header ("Running now", "Next run · …", "Not scheduled", "Last run · …", "Last run failed. …"), sections "Prompt", "Run Output", "Configuration", "Run History · n" with "Load more"; run pills Running/Completed/Failed; "\(n) tokens"; menu Run Now / Edit / Delete.
Editor: sections Task (name, prompt, schedule), Delivery (targets), Configuration (model from gateway `model.options`, skills from `GET /api/skills`). Hint "Leaving the model on Server default lets the server use the selected profile's model."
Strings: "Delete Task?" / "“\(name)” will be removed from the Hermes server."; "Run Now?" / "This also resumes the Task."; "No Tasks" / "Scheduled jobs from the Hermes server will appear here."; "No Matching Tasks" / "No tasks match this filter."; "Could Not Load Tasks" / "Try Again"; "Empty Output" / "No output for this run."; "This task has not produced any output yet."

## Skills — toggle editable. `B/HermesMobile/Features/Skills/*`, `HermesSkillsClient.swift`

- `GET /api/skills?profile=` [measured] bare array of `{name, description, category, enabled, usage, provenance}`.
- `PUT /api/skills/toggle` body `{name, enabled, profile}` → `{ok,name,enabled}`. Optimistic, roll back on error, alert "Could Not Update Skill".
- `GET /api/skills/content?name=&profile=` [measured] → `{name, content (SKILL.md markdown), path}`; 404 `{detail}`.
- Linked files: folder = `path` minus `/SKILL.md`; `GET /api/fs/list?path=` → `{entries:[{name,path,isDirectory}]}` (folder + one level); `GET /api/fs/read-text?path=` → `{text, binary, truncated, byteSize}`.
UI: title "Skills", search "Search skills..." (name/description/category), sections by category (uppercase headers, case-insensitive sort, missing → "Uncategorized"), rows: 36dp dark circle + hammer, name, 2–3 line description, "Disabled" pill and 0.55 alpha when disabled, chevron. Detail renders markdown content + "Linked Files". Empty: "No Skills" / "Skills from the Hermes server will appear here."; search empty "No Results" / `No skills match "…"`; error "Could Not Load Skills" / "Try Again".

## Memory — editable. `B/HermesMobile/Features/Memory/*`, `Networking/APIClient+Memory.swift:63+`

1. `GET /api/config?profile=` [measured] → use `config.memory`: `memory_enabled` (true), `user_profile_enabled` (true), `memory_char_limit` (2200), `user_char_limit` (1375).
2. Profile folder = `path` of the profile (from `GET /api/profiles` [measured has `path`] or gateway `profiles.list`) + `/memories`.
3. `GET /api/fs/read-text?path=<folder>/MEMORY.md` and `/USER.md`; 404 = empty.
4. `GET /api/profiles/{name}/soul` [measured] → `{content, exists}`.
Writes: `POST /api/fs/write-text {path, content}`; on 400 "Parent directory does not exist" → `POST /api/files/mkdir {path: folder}` then retry once; re-read to confirm. Soul: `PUT /api/profiles/{name}/soul {content}` → `{ok:true}`. Before save re-read; if changed since open (and not already equal to the draft) show "Changed on the host" / "\(section) changed since you opened it. Your draft is kept. Reload shows the host's text and drops this draft." with Reload.
UI: title "Memory"; sections "My Notes" (MEMORY.md), "User Profile" (USER.md), "Agent Soul"; empty "No notes yet." / "No profile yet." / "No soul defined yet."; editor "Edit \(section)", counter "\(used) of \(limit) characters" / over limit "Over the host's limit. Shorten to save."; footer "Edits apply from the agent's next session."; "Read-only" if > 512 KiB or binary; errors "Could Not Load Memory" / "Could not save memory.".

## Usage (sidebar label "Insights", screen title "Usage") — read-only. `B/HermesMobile/Features/Insights/*`

- `GET /api/analytics/usage?days=7|30|90&profile=` [measured]: `{daily:[{day,input_tokens,output_tokens,cache_read_tokens,reasoning_tokens,estimated_cost,actual_cost,sessions,api_calls}], by_model:[{model,input_tokens,output_tokens,estimated_cost,sessions,api_calls}], totals:{total_input,total_output,total_cache_read,total_reasoning,total_estimated_cost,total_actual_cost,total_sessions,total_api_calls}, period_days, skills:{summary,top_skills}, tools:[{tool,count,percentage}]}`.
- `GET /api/analytics/models?days=&profile=` [measured]: `{models:[{model,provider,input_tokens,output_tokens,cache_read_tokens,reasoning_tokens,estimated_cost,actual_cost,sessions,api_calls,tool_calls,last_used_at,avg_tokens_per_session}], totals, period_days}`.
- 503 with `detail.error` starting `state_db` → "Hermes couldn’t read this Profile’s session history. Run hermes doctor on the host, then try again."
UI: "Window" picker 7 / 30 / 90 days; "Totals" grid (Sessions, Estimated cost, Processed tokens, Cached input with hit %); chart card with Metric picker Cost/Tokens/Sessions (daily bars; empty "No activity in this window."); "By model" rows ("\(x)% of cost", tokens, sessions); tools list; footer "Source: server insights from the last \(n) days."; empty "No Data" / "Session usage data will appear here once you have conversations."; error "Could Not Load Usage" / "Try Again".

## Active Profile — local preference, no server write

- `GET /api/profiles/active` [measured] → `{active, current}`.
- `GET /api/profiles` [measured] → `{profiles:[{name, path, is_default, model, provider, skill_count, display_name, description, ...}]}` (or gateway `profiles.list {include_sessions:false}`).
- Picking a profile saves it locally; session list, new chats (`session.create {profile}`), and all panels use the selected profile. Plus a "Show all" option that merges every profile's sessions.
- Home row "Active Profile ›" expands inline to the profile list with a check on the current. Strings: "No profiles", "Could not load profiles".

## Projects — read + create

- Gateway `projects.tree {profile}` → `{projects:[{id,label,color,path,isAuto,isNoProject,sessionIds:[],sessionCount}], active_id, scoped_session_ids}`. User projects first, then auto per-repo. `isNoProject` is not a lane.
- Home row "Projects ›" expands inline to project rows (label, count); tapping filters the Sessions list to that project's `sessionIds`; a chip shows the active filter with clear.
- Create: gateway `projects.create {profile, name, folder, color?}`; error code 5063 = folder already owned. Placeholder "~/Projects/app".
- Strings: "Projects", "No projects", "No project", "Loading projects...". Delete (`projects.delete`) confirm: "Sessions in this project will be moved to No project. The sessions themselves will not be deleted."
- Gateway methods that return -32601 mark the feature unavailable: hide it.
