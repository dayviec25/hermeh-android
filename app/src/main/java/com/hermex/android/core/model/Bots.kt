package com.hermex.android.core.model

import java.time.Instant

data class CanonicalSession(
    val id: StoredKey,
    val tip: StoredKey?,
    val title: String?,
    val preview: String?,
    val lastActive: Instant?,
    val messageCount: Int,
)

enum class BotLiveStatus(val label: String) {
    Waiting("Waiting for you"),
    Working("Working"),
    ;

    companion object {
        fun fromWire(value: String?): BotLiveStatus? = when (value) {
            "waiting" -> Waiting
            "working", "starting", "streaming" -> Working
            else -> null
        }
    }
}

data class BotProfile(
    val profile: ProfileName,
    val name: String,
    val description: String?,
    val colorHex: String?,
    val pinned: Boolean,
    val hidden: Boolean,
    val isDefault: Boolean,
    val canonical: CanonicalSession?,
    val live: BotLiveStatus?,
) {
    val preview: String? get() = canonical?.preview?.takeIf { it.isNotBlank() }
    val lastActive: Instant? get() = canonical?.lastActive
    val chatKey: StoredKey? get() = canonical?.let { it.tip ?: it.id }
}
