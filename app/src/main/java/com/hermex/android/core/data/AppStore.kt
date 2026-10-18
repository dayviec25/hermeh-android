package com.hermex.android.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hermex.android.core.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hermex")

enum class ThemeMode { System, Light, Dark }

enum class LogoColor(val label: String, val argb: Long) {
    Yellow("Yellow", 0xFFFFD700),
    Blue("Blue", 0xFF5B7CFF),
    Purple("Purple", 0xFFAF52DE),
    Red("Red", 0xFFFF3B30),
    Green("Green", 0xFF34C759),
    White("White", 0xFFFFFFFF),
}

data class AppearancePrefs(val theme: ThemeMode, val logo: LogoColor)

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

    val appearance: Flow<AppearancePrefs> = context.dataStore.data.map { prefs ->
        AppearancePrefs(
            theme = prefs[KEY_THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.System,
            logo = prefs[KEY_LOGO]?.let { name -> LogoColor.entries.firstOrNull { it.name == name } } ?: LogoColor.Yellow,
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
        }
    }

    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDED] == "1" }.distinctUntilChanged()

    suspend fun markOnboarded() {
        context.dataStore.edit { it[KEY_ONBOARDED] = "1" }
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setLogo(color: LogoColor) {
        context.dataStore.edit { it[KEY_LOGO] = color.name }
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

    private companion object {
        val KEY_SERVER = stringPreferencesKey("server")
        val KEY_PASSWORD = stringPreferencesKey("password.sealed")
        val KEY_COOKIES = stringPreferencesKey("cookies.sealed")
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_LOGO = stringPreferencesKey("logo")
        val KEY_ONBOARDED = stringPreferencesKey("onboarded")
    }
}
