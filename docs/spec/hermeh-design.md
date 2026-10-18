# Hermeh design spec

Hermeh replaces the Hermex look. It leans on Meta's Muse for character and on Claude and ChatGPT for reading comfort.

| Source | What Hermeh takes |
|---|---|
| Muse | A named companion mascot at the top of Home, a name tag under it, and a thought bubble showing what the agent is doing in the background. Tapping the mascot opens the companion. The user names the agent. |
| Claude | Warm paper neutrals instead of pure white and black, a serif for display text, a terracotta accent, and full-width assistant prose. |
| ChatGPT | A quiet, chrome-light chat, a rounded composer pinned at the bottom with "Message …", an ink send button, and starter prompts on an empty chat. |

`design.md` still holds the protocol-driven pieces (log rows, tool icons, approval choices). Where the two disagree, this file wins.

## Data shape

Lives in `core/model/Companion.kt`. Persisted in `AppStore`.

```kotlin
@JvmInline value class MascotId(val value: String)

enum class MascotColor(val label: String, val argb: Long) {
    Sunny, Peach, Lilac, Mint, Sky, Cloud   // values below; Sunny is the default
}

sealed interface MascotArt {
    data object WingedBlob : MascotArt      // drawn in Compose; the only art today
}

data class Mascot(val id: MascotId, val title: String, val art: MascotArt)

object MascotCatalog {
    val Hermeh = Mascot(MascotId("hermeh.winged"), "Hermeh", MascotArt.WingedBlob)
    val all = listOf(Hermeh)
    fun find(id: MascotId): Mascot = all.firstOrNull { it.id == id } ?: Hermeh
}

data class Companion(
    val name: String,          // user-chosen, default "Hermeh", trimmed, 1..24 chars
    val mascot: MascotId,
    val color: MascotColor,
    val named: Boolean,        // false until the user renames once; drives the intro thought
)

sealed interface MascotMood {
    data object Idle : MascotMood
    data object Happy : MascotMood
    data object Working : MascotMood
    data object WaitingForYou : MascotMood
    data object Offline : MascotMood
}
```

Home derives one `CompanionThought` from sessions and connection. Priority runs top to bottom:

```kotlin
sealed interface CompanionThought {
    val mood: MascotMood
    data class Offline(val connection: ConnectionState) : CompanionThought       // Offline
    data object Loading : CompanionThought                                       // Idle
    data class Working(val session: SessionRowUi, val others: Int) : CompanionThought   // Working
    data class Replies(val first: SessionRowUi, val count: Int) : CompanionThought      // Happy
    data object Intro : CompanionThought                                         // Happy, when !named
    data class Greeting(val partOfDay: PartOfDay) : CompanionThought             // Idle
}
```

`Working` comes from `SessionSummary.isActive`, carried onto `SessionRowUi.active`. `Replies` counts unread rows. Copy:

- Offline: "I can't reach your server", "Reconnecting…", "Waiting for network…"
- Working: "Working on “{title}”" plus " and {n} more" when others > 0. Tap opens that session.
- Replies: "{n} new repl{y|ies} waiting" with the first title as a second line. Tap opens the first.
- Intro: "Hi, I'm {name}! Tap my name tag to rename me."
- Greeting: "Good morning/afternoon/evening. What should we work on?" Tap starts a new chat.

The token-priced AI mascot generator is out of scope. The picker shows it as a disabled "Create with AI" card so the slot exists. A generated mascot later adds a `MascotArt` variant and catalog entries. Nothing else changes.

## Palette

Defined once in `Theme.kt`. Every screen reads `HermehTheme.colors`.

| Token | Light | Dark |
|---|---|---|
| background | #FBF8F3 | #161412 |
| secondaryBackground (cards, sheets) | #F3EEE6 | #211E1B |
| tertiaryBackground (raised) | #FFFFFF | #2B2724 |
| label | #1F1B16 | #F4EFE8 |
| secondaryLabel | label 62% | label 62% |
| tertiaryLabel | label 34% | label 34% |
| separator | #E6DED2 | #3A3531 |
| accent (links, selection, focus) | #C8643B | #E8946A |
| ink (send button, primary buttons) | #1F1B16 on #FBF8F3 text | #F4EFE8 on #161412 text |
| userBubble | mascot color 22% over secondaryBackground | mascot color 18% over secondaryBackground |
| glass | secondaryBackground 88% | secondaryBackground 88% |
| glassStroke | black 6% | white 7% |
| codeBackground | #F1EBE1 | #0F0E0C |
| inlineCode | #EEE7DC | #24201C |
| table bg / alt / border | #FFFFFF / #F8F4EE / #E6DED2 | #1C1A17 / #262320 / #3A3531 |

Keep approval, input, working, danger and success as they are in `design.md`.

Mascot colors: Sunny #FFC94D (default), Peach #FF9F7A, Lilac #B9A3FF, Mint #7FD8B4, Sky #7CC4FF, Cloud #ECE7DF. They replace the logo presets. Text on a mascot color is #1F1B16 for all six.

## Type

- Display: `FontFamily.Serif`, Medium. Home greeting and thought text 17sp; screen titles (Settings, Tasks, Skills, Sessions header) 26sp; empty-chat headline 28sp; onboarding hero 32sp.
- UI: system sans, as in `design.md`.
- Name tag: 13sp SemiBold sans.

## Shapes

Cards 24, composer 28, sheets 28 on top corners, tiles 20. Bubbles 22 with a 6dp corner on the speaker side, bottom-end for the user. Chips and the name tag are capsules.

## Mascot

`core/designsystem/Mascot.kt`: `Mascot(mascot: Mascot, color: Color, mood: MascotMood, size: Dp, modifier)`, drawn on a Canvas so it scales from 20dp to 160dp.

- Body: a soft squircle, slightly wider than tall (1.08:1), filled with the mascot color with a 12% darker bottom shade and a small top-left highlight.
- Wings: two small rounded white wings on the upper sides of the head (Hermes' winged helmet), with a hairline label-12% stroke.
- Face: two dark oval eyes with a white glint, rosy blush ovals (#FF8A8A 35%), a small mouth.
- Moods: Idle, a gentle smile. Happy, a wider smile with eyes as arcs. Working, eyes glance up and to the side, mouth small "o". WaitingForYou, raised eyes plus a small accent "!" badge top-right. Offline, closed-line eyes plus a small "z".
- Motion: an idle bob of ±3% of size over 2.6s, ease in-out. A blink every 3.5 to 5.5s, randomized. Working wings flap quickly. When the system animator scale is 0, all loops stop and the mascot rests at frame 0.
- Semantics: contentDescription "{name}, {mood words}", and testTag `mascot`.

The launcher icon is the same mascot face as an adaptive vector icon: Sunny body on a #FBF8F3 background, with monochrome for themed icons.

## Home

Top to bottom:

1. **Top bar**: 44dp glass circle for search on the left; avatar initials circle (Settings) on the right. Search open keeps today's behavior.
2. **Companion hero**, centered:
   - The thought bubble: a rounded 22 card in tertiaryBackground with a hairline stroke and soft shadow, max width 300dp. Serif 17sp text, 2 lines max, with an optional 13sp secondary second line. Two trailing circles (10dp, 6dp) step down toward the mascot's head. Crossfade on change. testTag `home.thought`.
   - The mascot at 112dp. Tap opens the Companion sheet. testTag `home.mascot`.
   - The name tag: a capsule in secondaryBackground with name SemiBold and a small pencil icon. Tap opens rename. testTag `home.nameTag`.
3. **Destinations** as a 3-column grid of tiles: Bots, Tasks, Kanban, Skills, Memory, Insights. Each tile is secondaryBackground with radius 20, a 22dp Lucide icon and a 14sp Medium label, about 76dp tall. Keep `home.row.<slug>` tags on the tiles.
4. **Filters row**: two capsule chips, "Profile · {name} ⌄" (`home.row.activeProfile`) and "Project · {All|label} ⌄" (`home.row.projects`). Each expands its existing inline list below the row, keeping today's behavior and tags.
5. **Sessions**: a serif 26sp "Sessions" header, rows unchanged in behavior. An active session row shows a 7dp working-color dot before the title.
6. **Bottom composer pill**: full width minus 16dp gutters, radius 28, glass with shadow. It reads "Message {name}…" with the 28dp mini mascot leading and an ink circle arrow trailing. Tap starts a new chat with the keyboard up. It replaces the floating Chat button and keeps the tag `home.chat`.

**Companion sheet**: a ModalBottomSheet with the mascot at 96dp and the current mood, a name field (`companion.name`, Save `companion.save`), color swatches (`companion.color.<name>`), and a "Mascot" section. That section holds a grid with the Hermeh card selected (`companion.mascot.hermeh.winged`) and a disabled "Create with AI" card (`companion.mascot.create`) with a sparkle icon. Its copy: "Design your own mascot. Uses tokens. Coming soon." Rename opens the same sheet with the name field focused.

## Chat

- **Header**: back glass circle, then a 28dp mini mascot whose mood follows the run (Working while running, WaitingForYou on a pending request, Offline when disconnected, otherwise Idle). Then the title (session title, or "New chat") with the subtitle "{name} · {profile}", then the files circle.
- **Assistant turn header**: before the first assistant-side row after a user message, or at transcript start, add a 22dp static mascot with the name in 13sp SemiBold. Assistant prose stays full width and unbubbled.
- **User bubble**: userBubble color, radius 22 with the bottom-end corner at 6, max 80% width, right-aligned.
- **Run status**: the capsule keeps its tag, timer and states. It swaps the dot for a 20dp mini mascot in the matching mood. Copy uses the companion name: "{name} is working", "Waiting for you". "Hermes has a question" becomes "{name} has a question".
- **Empty new chat**: a centered 120dp mascot (Idle), a serif 28sp "What should we work on?", and three starter chips that fill the composer without sending: "Check on my server", "Summarize my recent sessions", "Plan a scheduled task". Tags `chat.empty`, `chat.starter.<n>`.
- **Composer**: radius 28 card, placeholder "Message {name}…", ink send circle. The rest is unchanged.

## Onboarding and brand

- Onboarding moves from black to warm dark #161412. Amber becomes Sunny #FFC94D, with #1F1B16 text on it.
- The welcome hero replaces the app icon with the 124dp mascot (Happy) and a Sunny radial glow. Headline: "Meet Hermeh, your agent's companion." The Features and Connect pages keep their content and get the new colors.
- The pixel HERMEX wordmark and its four PNG layers go away. Where a wordmark is needed, as in About, use "Hermeh" in serif 22sp Medium.
- User-visible "Hermex" copy becomes "Hermeh". "Hermes" stays where it names the server or host software, for example "Not connected to Hermes".
