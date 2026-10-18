package com.hermeh.android.core.data

import com.hermeh.android.core.model.MemoryEntry
import com.hermeh.android.core.model.MemorySection
import com.hermeh.android.core.model.MemorySnapshot
import com.hermeh.android.core.network.HermesPanelsRest
import com.hermeh.android.core.network.HermesRefusal
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class MemoryConflict : Exception("Changed on the host")

class MemoryProfileMissing : Exception("This Profile is no longer on the server.")

class MemoryRepository(private val auth: AuthRepository) {

    private data class FileText(val text: String, val editable: Boolean)

    private suspend fun <T> panels(block: suspend (HermesPanelsRest) -> T): T =
        auth.authorized { rest -> block(HermesPanelsRest(rest, auth.httpClient)) }

    suspend fun load(profile: String): MemorySnapshot = panels { api ->
        coroutineScope {
            val config = async { api.memoryConfig(profile) }
            val folder = async { memoriesFolder(api, profile) }
            val soul = async { api.soul(profile) }
            val cfg = config.await()
            val dir = folder.await()
            val notes = if (cfg.memoryEnabled) async { read(api, "$dir/MEMORY.md") } else null
            val user = if (cfg.userEnabled) async { read(api, "$dir/USER.md") } else null
            buildList {
                notes?.await()?.let { add(MemoryEntry(MemorySection.Notes, it.text, cfg.memoryLimit, !it.editable)) }
                user?.await()?.let { add(MemoryEntry(MemorySection.User, it.text, cfg.userLimit, !it.editable)) }
                add(MemoryEntry(MemorySection.Soul, soul.await(), null, false))
            }.let(::MemorySnapshot)
        }
    }

    suspend fun reload(profile: String, section: MemorySection): String = panels { api ->
        when (section) {
            MemorySection.Soul -> api.soul(profile)
            MemorySection.Notes, MemorySection.User -> read(api, pathFor(memoriesFolder(api, profile), section)).text
        }
    }

    suspend fun save(profile: String, section: MemorySection, content: String, loaded: String): String = panels { api ->
        when (section) {
            MemorySection.Soul -> {
                val current = api.soul(profile)
                if (current != loaded && current != content) throw MemoryConflict()
                api.setSoul(profile, content)
                content
            }
            MemorySection.Notes, MemorySection.User -> {
                val folder = memoriesFolder(api, profile)
                val path = pathFor(folder, section)
                val text = MemoryCanonicalizer.canonical(content)
                val current = read(api, path)
                if (!current.editable || (current.text != loaded && current.text != text)) throw MemoryConflict()
                var written = api.fsWriteText(path, text)
                if (written.code == 400 && written.detail == "Parent directory does not exist") {
                    api.mkdir(folder)
                    written = api.fsWriteText(path, text)
                }
                api.accepted(written)
                if (read(api, path).text != text) throw MemoryConflict()
                text
            }
        }
    }

    private fun pathFor(folder: String, section: MemorySection) =
        folder + if (section == MemorySection.User) "/USER.md" else "/MEMORY.md"

    private suspend fun memoriesFolder(api: HermesPanelsRest, profile: String): String {
        val path = api.profilePath(profile)?.takeIf { !it.contains('\u0000') } ?: throw MemoryProfileMissing()
        return path.trimEnd('/') + "/memories"
    }

    private suspend fun read(api: HermesPanelsRest, path: String): FileText {
        val file = api.fsReadText(path) ?: return FileText("", true)
        val text = file.text ?: if (file.binary) "" else throw HermesRefusal("Could not read memory.")
        return FileText(text, !file.truncated && !file.binary)
    }
}

object MemoryCanonicalizer {
    private const val SEPARATOR = "\n§\n"

    fun canonical(text: String): String {
        val body = text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        val entries = mutableListOf<String>()
        val current = StringBuilder()
        for (line in body.split('\n')) {
            if (line.trim(::isPythonSpace) == "§") {
                entries += current.toString()
                current.clear()
            } else {
                if (current.isNotEmpty()) current.append('\n')
                current.append(line)
            }
        }
        entries += current.toString()
        val seen = HashSet<String>()
        return entries.map { it.trim(::isPythonSpace) }
            .filter { it.isNotEmpty() && seen.add(it) }
            .joinToString(SEPARATOR)
    }

    fun count(text: String): Int = canonical(text).let { it.codePointCount(0, it.length) }

    private fun isPythonSpace(c: Char): Boolean = when (c.code) {
        in 0x09..0x0D, in 0x1C..0x20, 0x85, 0xA0, 0x1680, in 0x2000..0x200A, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000 -> true
        else -> false
    }
}
