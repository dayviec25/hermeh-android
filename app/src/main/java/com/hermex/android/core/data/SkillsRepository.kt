package com.hermex.android.core.data

import com.hermex.android.core.model.Skill
import com.hermex.android.core.model.SkillDetail
import com.hermex.android.core.model.SkillFile
import com.hermex.android.core.network.HermesPanelsRest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

class SkillsRepository(private val auth: AuthRepository) {
    private val folders = ConcurrentHashMap<String, String>()

    private suspend fun <T> panels(block: suspend (HermesPanelsRest) -> T): T =
        auth.authorized { rest -> block(HermesPanelsRest(rest, auth.httpClient)) }

    suspend fun skills(profile: String): List<Skill> = panels { it.skills(profile) }

    suspend fun setEnabled(name: String, enabled: Boolean, profile: String): Boolean =
        panels { it.toggleSkill(name, enabled, profile) }

    suspend fun detail(name: String, profile: String): SkillDetail = panels { api ->
        val (content, path) = api.skillContent(name, profile)
        val folder = path?.let { folderOf(it) }
        if (folder != null) folders["$profile/$name"] = folder
        SkillDetail(name, content, folder?.let { linkedFiles(api, it) }.orEmpty())
    }

    suspend fun file(name: String, profile: String, relative: String): SkillFile? {
        require(relative.split('/').all(::isName)) { "Linked file path leaves the skill folder" }
        val folder = folders["$profile/$name"] ?: run {
            detail(name, profile)
            folders["$profile/$name"]
        } ?: return null
        return panels { it.fsReadText("$folder/$relative") }?.copy(name = relative)
    }

    private suspend fun linkedFiles(api: HermesPanelsRest, folder: String): List<String> = coroutineScope {
        val top = visible(api, folder)
        val nested = top.filter { it.isDirectory }.map { dir ->
            async { visible(api, "$folder/${dir.name}").filter { !it.isDirectory }.map { "${dir.name}/${it.name}" } }
        }.awaitAll().flatten()
        (top.filter { !it.isDirectory && it.name != "SKILL.md" }.map { it.name } + nested).sorted()
    }

    private suspend fun visible(api: HermesPanelsRest, folder: String): List<HermesPanelsRest.FsEntry> =
        try {
            api.fsList(folder)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            emptyList()
        }.filter { isName(it.name) && !it.name.startsWith(".") }

    private fun folderOf(path: String): String? =
        listOf("/SKILL.md", "\\SKILL.md").firstOrNull { path.length > it.length && path.endsWith(it) }
            ?.let { path.dropLast(it.length) }

    private fun isName(name: String) =
        name.isNotEmpty() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == '\u0000' }
}
