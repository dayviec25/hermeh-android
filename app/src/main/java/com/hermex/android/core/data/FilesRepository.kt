package com.hermex.android.core.data

import com.hermex.android.core.model.FileEntry
import com.hermex.android.core.model.FilePreview
import com.hermex.android.core.network.HermesFailure
import com.hermex.android.core.network.bool
import com.hermex.android.core.network.long
import com.hermex.android.core.network.str

class FilesRepository(private val auth: AuthRepository) {

    suspend fun list(path: String): List<FileEntry> {
        requireSafe(path)
        val rows = auth.authorized { rest -> rest.fsList(path) }
        return rows.mapNotNull { row ->
            val name = row.str("name")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            FileEntry(
                name = name,
                path = row.str("path") ?: "${path.trimEnd('/')}/$name",
                isDirectory = row.bool("isDirectory") ?: row.bool("is_directory") ?: false,
            )
        }.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    suspend fun read(path: String): FilePreview {
        requireSafe(path)
        val root = auth.authorized { rest -> rest.fsReadText(path) }
        val size = root.long("byteSize")
        if (root.bool("binary") == true) return FilePreview.Binary(path, size)
        return FilePreview.Text(
            path = path,
            text = root.str("text").orEmpty(),
            truncated = root.bool("truncated") ?: false,
            byteSize = size,
            language = root.str("language"),
        )
    }

    private fun requireSafe(path: String) {
        val parts = path.split('/')
        if (!path.startsWith("/") || parts.any { it == ".." } || parts.any { it == "~" }) {
            throw HermesFailure.Refused(400, "Hermex only opens absolute paths inside the workspace.")
        }
    }
}
