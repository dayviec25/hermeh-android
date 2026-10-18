package com.hermeh.android.core.model

@JvmInline value class MascotId(val value: String)

enum class MascotColor(val label: String, val argb: Long) {
    Sunny("Sunny", 0xFFFFC94D),
    Peach("Peach", 0xFFFF9F7A),
    Lilac("Lilac", 0xFFB9A3FF),
    Mint("Mint", 0xFF7FD8B4),
    Sky("Sky", 0xFF7CC4FF),
    Cloud("Cloud", 0xFFECE7DF),
}

sealed interface MascotArt {
    data object WingedBlob : MascotArt
}

data class Mascot(val id: MascotId, val title: String, val art: MascotArt)

object MascotCatalog {
    val Hermeh = Mascot(MascotId("hermeh.winged"), "Hermeh", MascotArt.WingedBlob)
    val all = listOf(Hermeh)
    fun find(id: MascotId): Mascot = all.firstOrNull { it.id == id } ?: Hermeh
}

data class Companion(
    val name: String,
    val mascot: MascotId,
    val color: MascotColor,
    val named: Boolean,
)

sealed interface MascotMood {
    data object Idle : MascotMood
    data object Happy : MascotMood
    data object Working : MascotMood
    data object WaitingForYou : MascotMood
    data object Offline : MascotMood
}

const val COMPANION_NAME_MAX = 24

val DefaultCompanion = Companion(
    name = MascotCatalog.Hermeh.title,
    mascot = MascotCatalog.Hermeh.id,
    color = MascotColor.Sunny,
    named = false,
)

fun companionName(raw: String): String? = raw.trim().takeIf { it.length in 1..COMPANION_NAME_MAX }
