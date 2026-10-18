package com.hermeh.android.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hermeh.android.core.model.Companion
import com.hermeh.android.core.model.DefaultCompanion
import com.hermeh.android.core.model.MascotCatalog
import com.hermeh.android.core.model.MascotColor
import com.hermeh.android.core.model.MascotId
import com.hermeh.android.core.model.ServerConfig
import com.hermeh.android.core.model.companionName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hermeh")

enum class ThemeMode { System, Light, Dark }

@Serializable
private data class StoredServer(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String,
    val headers: List<List<String>> = emptyList(),
    val installId: String? = null,
)

data class SavedServer(val config: ServerConfig, val installId: String?)

class AppStore(private val context: Context, private val cipher: SecretCipher) {

    private val json = Json { ignoreUnknownKeys = true }

    val server: Flow<SavedServer?> = context.dataStore.data
        .map { prefs -> prefs[KEY_SERVER]?.let(::decodeServer) }
        .distinctUntilChanged()

    val theme: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.System
    }.distinctUntilChanged()

    val companion: Flow<Companion> = context.dataStore.data.map { prefs ->
        Companion(
            name = prefs[KEY_COMPANION_NAME]?.let(::companionName) ?: DefaultCompanion.name,
            mascot = prefs[KEY_COMPANION_MASCOT]?.let { MascotCatalog.find(MascotId(it)).id } ?: DefaultCompanion.mascot,
            color = prefs[KEY_COMPANION_COLOR]?.let { name -> MascotColor.entries.firstOrNull { it.name == name } } ?: DefaultCompanion.color,
            named = prefs[KEY_COMPANION_NAMED] == "1",
        )
    }.distinctUntilChanged()

    suspend fun currentServer(): SavedServer? = server.first()

    suspend fun password(): String? = context.dataStore.data.first()[KEY_PASSWORD]?.let(cipher::decrypt)

    suspend fun cookies(): String? = context.dataStore.data.first()[KEY_COOKIES]?.let(cipher::decrypt)

    suspend fun saveCookies(serialized: String) {
        val sealed = cipher.encrypt(serialized)
        context.dataStore.edit { it[KEY_COOKIES] = sealed }
    }

    suspend fun saveServer(server: SavedServer, password: String) {
        val stored = StoredServer(
            id = server.config.id,
            name = server.config.name,
            baseUrl = server.config.baseUrl,
            username = server.config.username,
            headers = server.config.customHeaders.map { listOf(it.first, it.second) },
            installId = server.installId,
        )
        val sealed = cipher.encrypt(password)
        context.dataStore.edit {
            it[KEY_SERVER] = json.encodeToString(StoredServer.serializer(), stored)
            it[KEY_PASSWORD] = sealed
        }
    }

    suspend fun clearCredentials() {
        context.dataStore.edit {
            it.remove(KEY_SERVER)
            it.remove(KEY_PASSWORD)
            it.remove(KEY_COOKIES)
            it.remove(KEY_PROFILE_PICK)
            it.remove(KEY_SHOW_ALL)
        }
    }

    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDED] == "1" }.distinctUntilChanged()

    suspend fun markOnboarded() {
        context.dataStore.edit { it[KEY_ONBOARDED] = "1" }
    }

    suspend fun profilePick(): String? = context.dataStore.data.first()[KEY_PROFILE_PICK]?.takeIf { it.isNotEmpty() }

    suspend fun showAllProfiles(): Boolean = context.dataStore.data.first()[KEY_SHOW_ALL] == "1"

    suspend fun setProfilePick(name: String, showAll: Boolean) {
        context.dataStore.edit {
            it[KEY_PROFILE_PICK] = name
            it[KEY_SHOW_ALL] = if (showAll) "1" else "0"
        }
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setCompanionName(raw: String) {
        val name = companionName(raw) ?: return
        context.dataStore.edit {
            it[KEY_COMPANION_NAME] = name
            it[KEY_COMPANION_NAMED] = "1"
        }
    }

    suspend fun setCompanionColor(color: MascotColor) {
        context.dataStore.edit { it[KEY_COMPANION_COLOR] = color.name }
    }

    suspend fun setCompanionMascot(id: MascotId) {
        context.dataStore.edit { it[KEY_COMPANION_MASCOT] = MascotCatalog.find(id).id.value }
    }

    private fun decodeServer(raw: String): SavedServer? = runCatching {
        val stored = json.decodeFromString(StoredServer.serializer(), raw)
        SavedServer(
            config = ServerConfig(
                id = stored.id,
                name = stored.name,
                baseUrl = stored.baseUrl,
                username = stored.username,
                customHeaders = stored.headers.mapNotNull { pair ->
                    if (pair.size == 2) pair[0] to pair[1] else null
                },
            ),
            installId = stored.installId,
        )
    }.getOrNull()

    private companion object Keys {
        val KEY_SERVER = stringPreferencesKey("server")
        val KEY_PASSWORD = stringPreferencesKey("password.sealed")
        val KEY_COOKIES = stringPreferencesKey("cookies.sealed")
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_COMPANION_NAME = stringPreferencesKey("companion.name")
        val KEY_COMPANION_MASCOT = stringPreferencesKey("companion.mascot")
        val KEY_COMPANION_COLOR = stringPreferencesKey("companion.color")
        val KEY_COMPANION_NAMED = stringPreferencesKey("companion.named")
        val KEY_ONBOARDED = stringPreferencesKey("onboarded")
        val KEY_PROFILE_PICK = stringPreferencesKey("profile.pick")
        val KEY_SHOW_ALL = stringPreferencesKey("profile.showAll")
    }
}
