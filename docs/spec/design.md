# Hermex design spec (from the iOS app)

Reference repo: `/private/tmp/claude-501/-Users-dchungdev-hermex-android/9a337a95-8f6e-479b-8a94-cb75cc8eee82/scratchpad/src/hermex` (call it `B`). Marketing screenshots: `/private/tmp/claude-501/-Users-dchungdev-hermex-android/9a337a95-8f6e-479b-8a94-cb75cc8eee82/scratchpad/site/slide-{1..8}-{light,dark}.png` and `B/docs/assets/readme/*.png`. Look at them.

## Palette

- Attention: approval #B45309 light / #FCD34D dark; input #4F46E5 / #A5B4FC; working #0284C7 / #38BDF8.
- Accent: iOS system blue #007AFF light / #0A84FF dark.
- Logo color presets (`AppTheme.swift:72-81`): Yellow #FFD700 (default), Blue #5B7CFF, Purple #AF52DE, Red #FF3B30, Green #34C759, White #FFFFFF. Text on the color is black when luminance > 0.62, else white.
- Onboarding: background #000000 (always dark). Amber #FFBD1A for primary buttons and icons, black text on amber. Success #73EB8F. Welcome glow #FF9E14 (55%, 22%), inner #FFC72E (35%). Text white at 100/68/58/45%. Fields: black 24% fill, white 8% stroke, radius 8. Cards: white 5.5% fill, white 10% stroke.
- System-like surfaces: background #FFFFFF / #000000; secondary #F2F2F7 / #1C1C1E; tertiary #FFFFFF / #2C2C2E; label #000000 / #FFFFFF; secondary label 60% of label; tertiary label 30%; separator #C6C6C8 / #38383A.
- User bubble: #F2F2F7 (gray6) light / #48484A (gray3) dark; stroke black 4% light / white 8% dark.
- Steer bubble: accent at 12% light / 22% dark, stroke accent 30% / 45%.
- Code block: #0A0D12 dark / #F2F2F7 light, radius 24, 1px separator at 35%. Inline code: #14171F dark / #EFEFF4 light.
- Table: background #18191D / #FFFFFF, alternate row #25262A / #F7F7F9, border #42444E / #E4E4E8.
- Muted heading #6D707D / #6B6E7B; divider #333438 / #D0D0D3.
- Send button: enabled, black fill + white glyph (light) / white fill + black glyph (dark); disabled, black 12% / white 18% fill with secondary glyph. Stop: red #FF3B30 at 14% / 22% fill, red glyph.
- Badges: tint at 12% fill, capsule.

## Type

System font only (Roboto on Android is fine; no custom fonts). Mapping:
- iOS headline 17 semibold → 16sp SemiBold (row titles)
- body 17 → 16sp (chat text, composer)
- callout 16 → 15sp (user bubble)
- subheadline 15 → 14sp
- footnote 13 → 13sp
- caption 12 → 12sp (metadata, reasoning body)
- caption2 11 semibold → 11sp (badges); uppercase for section-card titles
- Code: monospace 13sp
- Onboarding hero 31sp bold; step title 28sp bold
- Large screen titles ("Sessions", "Tasks"): ~22sp semibold

## Radii and spacing

Composer card 26 (collapsed 54dp-tall capsule); section card 18; user bubble 20; steer bubble 14; code block 24; attachment tile 14 / 12; onboarding buttons and fields 8; onboarding step icon box 22; log row highlight 6.
Header: horizontal 24, top 28, icon target 44, logo 160dp wide (aspect 643:185).
Session row: horizontal 12 (inside a 12dp list inset, so text sits ~24 from the edge), vertical 8, min height 54 (46 without second line); title 2 lines max, time trailing on the title row; second line "N messages • profile" caption secondary. Unread dot 10dp accent.
Sidebar nav row (Tasks, Skills, Memory, Insights, Active Profile, Projects): icon 21dp in 28dp box, gap 18, min height 44, chevron for the expandable ones.
User bubble padding 14/8. Log row (thinking/tool) min height 32, icon column 20, gap 6, body indent 26, expanded body max 240dp.
Run-status capsule: padding 11/7, 7dp dot, caption semibold monospace timer + label ("Hermes is working", "Starting response", "Reconnecting stream", "Waiting for you", "Stopping response").
Section card content padding 16/14.

## Materials

iOS uses glass/blur. On Android: glass surfaces = secondary surface at ~85% alpha with a hairline border (white 8% dark / black 6% light), plus soft shadow (black 28% dark / 12% light, 14dp blur, y 6) for the composer and floating Chat button. Header search+avatar sit in one glass pill. Back/folder buttons in chat header are 44dp glass circles.

## Icons

Use Lucide-style line icons. The repo ships SVGs for: bot, calendar-clock (Tasks), columns-3 (Kanban), hammer (Skills), brain (Memory, and Thinking rows), chart-column-increasing (Insights), user-round-cog (Active Profile), user-round, folder (Projects) at `B/HermesMobile/Resources/Assets.xcassets/Lucide*.imageset/*.svg` — convert to VectorDrawables. Otherwise Material Symbols Outlined equivalents: search, add, edit_square (new chat), chevron_right, expand_more/less, close, check, warning, push_pin, archive, settings, arrow_upward (send), stop (stop), mic, folder, bolt, account_circle, terminal, description, language, build, call_split, more_horiz, remove, image, wifi_off.
Tool row icons by tool: terminal→terminal, read_file→description, patch/write_file→edit_note, search_files→search, web_search/web_extract→language, todo→list, delegate→call_split, other→build. Status glyph: check (done), close red (failed), more_horiz (running), remove (interrupted).

## App icon and logo

- Launcher icon: `B/HermesMobile/Resources/Assets.xcassets/AppIcon.appiconset/hermes_mobile_light_icon.png` and dark variant `AppIconDark.appiconset/hermes_mobile_dark_icon.png` (black rounded square, amber gradient "H" with speech-bubble tail). Onboarding hero uses the dark one (`HermesAppIcon.imageset`).
- Wordmark (pixel-art "HERMEX"): layers in `B/HermesMobile/Resources/Assets.xcassets/` — `hermes-fill-mask` (tint with logo color), `hermes-shading-overlay` (multiply), `hermes-highlight` (screen), `hermes-outline-shadow` (normal). Stack them; aspect 643:185, 160dp wide in the header.
- Workspace file icons: `B/HermesMobile/Resources/Assets.xcassets/FileIcons/*.imageset`.

## Navigation

- Unconfigured → Onboarding pager (Welcome "Get Started" → Features "What you get" → Agent prompt (copyable setup prompt for the agent) → Tailscale/reachability → Connect). "Already have a server?" jumps to Connect.
- Logged out (server saved) → sign-in form.
- Logged in → Home. Home = header (wordmark left; glass pill with search + avatar initials circle right), sidebar rows (Bots, Tasks, Kanban, Skills, Memory, Insights, Active Profile ›, Projects ›), then "Sessions" section with session rows. Floating "Chat" capsule bottom-right (edit_square icon + "Chat", headline semibold, padding 22) starts a new chat.
- Avatar tap → Settings (grouped list: Servers; Appearance (System/Light/Dark, logo color); Chat; General; About).
- Session row → Chat.

## Screens

**Onboarding welcome**: black; centered 124dp app icon (radius 22% of size) with amber radial glow and 1dp white gradient stroke; left-aligned 31sp bold white headline, 58% white subhead, two capsule badges ("Password protected", "Private network ready"); full-width amber "Get Started" button, black text, radius 8. Page indicator: active pill 24×8 white, inactive 8×8 white 18%.

**Connect**: fields with amber icon + caption label: Server URL, Username, Password (secure), Advanced (custom headers). Buttons "Test Connection" (secondary) and "Connect" (amber). Status banner tinted 12% fill, 18% stroke, radius 8.

**Chat**: header with back glass circle, title (1 line, ellipsis) + subtitle (profile name), trailing glass circle. User bubble right-aligned, max ~80% width. Assistant text unbubbled, full width, markdown (headings, lists, bold/italic, inline code, code blocks with language label + copy icon, tables). Thinking row: brain icon, bold "Thinking", one-line secondary summary, chevron; expands to caption body in a bordered rounded card (see slide-2-dark). Tool rows: same log-row shape with tool icon, label, detail, status glyph; consecutive tool rows group with "Show previous N". Run-status capsule while a turn runs. Approval/clarify cards inline above the composer.

**Composer**: glass card radius 26 with placeholder "Ask anything… /commands"; bottom row inside the card: + (attachments), model chip "model ⌄", reasoning chip "XHigh ⌄", spacer, mic, 44dp circular send (arrow_upward) / stop. Below the card: folder/workspace chip, profile chip ("Default"), context ring with count.

**Tasks**: title "Tasks", + and refresh in a capsule; "Running now" card; "Scheduled Jobs" header; job cards: title, status badge (e.g. orange "Paused"), description, label/value grid (Schedule, Next, Last, Deliver, Skills), chevron.

**Skills**: search pill "Search skills…", uppercase category headers, rows with 36dp dark circle + hammer, name, 2–3 line description, chevron.
