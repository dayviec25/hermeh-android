package com.hermeh.android.feature.files

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hermeh.android.core.data.FilesRepository
import com.hermeh.android.core.designsystem.GlassCircleButton
import com.hermeh.android.core.designsystem.AutomationAccessibilityRefresh
import com.hermeh.android.core.designsystem.HermehTheme
import com.hermeh.android.core.designsystem.glassSurface
import com.hermeh.android.core.model.FileEntry
import com.hermeh.android.core.model.FilePreview
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FileRowUi(val name: String, val path: String, val detail: String, val isDirectory: Boolean)

sealed interface FolderUiState {
    val query: String

    data class Loading(override val query: String = "") : FolderUiState
    data class Error(val message: String, override val query: String = "") : FolderUiState
    data class Success(val rows: List<FileRowUi>, val visible: List<FileRowUi>, override val query: String) : FolderUiState
}

class FolderViewModel(private val files: FilesRepository, private val path: String, private val root: String) : ViewModel() {
    private val _state = MutableStateFlow<FolderUiState>(FolderUiState.Loading())
    val state: StateFlow<FolderUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = FolderUiState.Loading(_state.value.query)
        viewModelScope.launch {
            _state.value = try {
                val rows = files.list(path).map { it.toRow() }
                filtered(rows, _state.value.query)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                FolderUiState.Error(error.message ?: "Couldn't list this folder.", _state.value.query)
            }
        }
    }

    fun onQuery(query: String) = _state.update { current ->
        when (current) {
            is FolderUiState.Success -> filtered(current.rows, query)
            is FolderUiState.Loading -> current.copy(query = query)
            is FolderUiState.Error -> current.copy(query = query)
        }
    }

    private fun filtered(rows: List<FileRowUi>, query: String): FolderUiState.Success {
        val needle = query.trim()
        val visible = if (needle.isEmpty()) rows else rows.filter { it.name.contains(needle, ignoreCase = true) }
        return FolderUiState.Success(rows, visible, query)
    }

    private fun FileEntry.toRow() = FileRowUi(name, path, relative(path), isDirectory)

    private fun relative(full: String): String {
        val base = root.trimEnd('/')
        return if (full.startsWith("$base/")) full.removePrefix("$base/") else full
    }
}

sealed interface FileViewerUiState {
    data object Loading : FileViewerUiState
    data class Error(val message: String) : FileViewerUiState
    data class Ready(val preview: FilePreview, val lines: List<String>, val markdown: Boolean) : FileViewerUiState
}

class FileViewerViewModel(private val files: FilesRepository, private val path: String) : ViewModel() {
    private val _state = MutableStateFlow<FileViewerUiState>(FileViewerUiState.Loading)
    val state: StateFlow<FileViewerUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = FileViewerUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val preview = files.read(path)
                val lines = (preview as? FilePreview.Text)?.text?.lines().orEmpty()
                val markdown = path.lowercase().let { it.endsWith(".md") || it.endsWith(".mdx") || it.endsWith(".markdown") }
                FileViewerUiState.Ready(preview, lines, markdown)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                FileViewerUiState.Error(error.message ?: "Couldn't open this file.")
            }
        }
    }
}

@Composable
fun FolderRoute(
    viewModel: FolderViewModel,
    path: String,
    root: String,
    onBack: () -> Unit,
    onRoot: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AutomationAccessibilityRefresh()
    FolderScreen(state, path, root, onBack, onRoot, viewModel::onQuery, viewModel::load, onOpenFolder, onOpenFile)
}

@Composable
fun FolderScreen(
    state: FolderUiState,
    path: String,
    root: String,
    onBack: () -> Unit,
    onRoot: () -> Unit,
    onQuery: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HermehTheme.colors
    val atRoot = path.trimEnd('/') == root.trimEnd('/')
    val location = if (atRoot) "Root" else path.removePrefix(root.trimEnd('/') + "/")
    Column(modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassCircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, Modifier.testTag("files.back"))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text("Files", color = colors.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("files.title"))
                Text(root, color = colors.secondaryLabel, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            }
        }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Location", color = colors.secondaryLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(location, color = colors.label, fontSize = 13.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.StartEllipsis, modifier = Modifier.testTag("files.location"))
            }
            if (!atRoot) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Root", Icons.Outlined.Home, onRoot, Modifier.testTag("files.root"))
                    Pill("Up", Icons.AutoMirrored.Filled.ArrowBack, onBack, Modifier.testTag("files.up"))
                }
            }
            SearchField(state.query, onQuery)
        }
        Spacer(Modifier.size(8.dp))
        HorizontalDivider(color = colors.separator.copy(alpha = 0.5f))
        when (state) {
            is FolderUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
            is FolderUiState.Error -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, color = colors.secondaryLabel, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.testTag("files.error"))
                TextButton(onClick = onRetry) { Text("Try Again", color = colors.accent) }
            }
            is FolderUiState.Success -> if (state.visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (state.query.isBlank()) "No Files" else "No Matches", color = colors.secondaryLabel, fontSize = 15.sp, modifier = Modifier.testTag("files.empty"))
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().testTag("files.list"),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(state.visible, key = { it.path }, contentType = { if (it.isDirectory) "dir" else "file" }) { row ->
                        FileRow(row, onClick = { if (row.isDirectory) onOpenFolder(row.path) else onOpenFile(row.path) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(row: FileRowUi, onClick: () -> Unit) {
    val colors = HermehTheme.colors
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 60.dp)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("files.row.${row.name}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(colors.secondaryBackground),
                contentAlignment = Alignment.Center,
            ) {
                if (row.isDirectory) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = colors.label, modifier = Modifier.size(20.dp))
                } else {
                    val icon = remember(row.name) { FileIcons.resolve(row.name) }
                    Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(row.name, color = colors.label, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.detail, color = colors.secondaryLabel, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.StartEllipsis)
            }
            if (row.isDirectory) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.tertiaryLabel)
        }
        HorizontalDivider(color = colors.separator.copy(alpha = 0.35f), modifier = Modifier.padding(start = 68.dp, end = 20.dp))
    }
}

@Composable
private fun Pill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HermehTheme.colors
    Row(
        modifier.glassSurface(CircleShape).clickable(onClick = onClick).heightIn(min = 36.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(16.dp))
        Text(text, color = colors.secondaryLabel, fontSize = 14.sp)
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val colors = HermehTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.secondaryBackground).heightIn(min = 44.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(color = colors.label, fontSize = 16.sp),
            cursorBrush = SolidColor(colors.label),
            modifier = Modifier.weight(1f).testTag("files.search"),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) Text("Search files", color = colors.tertiaryLabel, fontSize = 16.sp)
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = colors.secondaryLabel, modifier = Modifier.size(28.dp).clip(CircleShape).clickable { onQuery("") }.padding(5.dp))
        }
    }
}

@Composable
fun FileViewerRoute(viewModel: FileViewerViewModel, path: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AutomationAccessibilityRefresh()
    FileViewerScreen(state, path, onBack, viewModel::load)
}

@Composable
fun FileViewerScreen(state: FileViewerUiState, path: String, onBack: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HermehTheme.colors
    val name = path.substringAfterLast('/')
    Column(modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassCircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, Modifier.testTag("file.back"))
            Image(painterResource(remember(name) { FileIcons.resolve(name) }), contentDescription = null, modifier = Modifier.padding(start = 14.dp).size(20.dp))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(name, color = colors.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("file.title"))
                Text(path, color = colors.secondaryLabel, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.StartEllipsis)
            }
        }
        when (state) {
            FileViewerUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
            is FileViewerUiState.Error -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, color = colors.secondaryLabel, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.testTag("file.error"))
                TextButton(onClick = onRetry) { Text("Try Again", color = colors.accent) }
            }
            is FileViewerUiState.Ready -> when (val preview = state.preview) {
                is FilePreview.Binary -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Preview is not available for this file type.", color = colors.secondaryLabel, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.testTag("file.binary"))
                }
                is FilePreview.Text -> Column(Modifier.fillMaxSize()) {
                    if (preview.truncated) {
                        Text(
                            "Preview truncated",
                            color = colors.approval,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).testTag("file.truncated"),
                        )
                    }
                    if (state.markdown) {
                        MarkdownBody(preview.text, Modifier.weight(1f))
                    } else {
                        CodeBody(state.lines, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBody(lines: List<String>, modifier: Modifier) {
    val colors = HermehTheme.colors
    val gutter = (lines.size.toString().length * 9 + 12).dp
    val horizontal = rememberScrollState()
    LazyColumn(
        modifier.fillMaxWidth().background(colors.codeBackground).horizontalScroll(horizontal).testTag("file.text"),
        contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp + 24.dp),
    ) {
        itemsIndexed(lines, contentType = { _, _ -> "line" }) { index, line ->
            Row(Modifier.widthIn(min = 360.dp)) {
                Text(
                    "${index + 1}",
                    color = colors.tertiaryLabel,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(gutter).padding(end = 10.dp),
                )
                Text(line.ifEmpty { " " }, color = colors.label, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace, softWrap = false, modifier = Modifier.padding(end = 20.dp))
            }
        }
    }
}

@Composable
private fun MarkdownBody(text: String, modifier: Modifier) {
    val colors = HermehTheme.colors
    val body = TextStyle(color = colors.label, fontSize = 16.sp, lineHeight = 23.sp)
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("file.markdown"),
    ) {
        Markdown(
            content = text,
            colors = markdownColor(
                text = colors.label,
                codeBackground = colors.codeBackground,
                inlineCodeBackground = colors.inlineCode,
                dividerColor = colors.separator,
                tableBackground = colors.tableBackground,
            ),
            typography = markdownTypography(
                h1 = body.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
                h2 = body.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp),
                h3 = body.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                h4 = body.copy(fontWeight = FontWeight.SemiBold),
                h5 = body.copy(fontWeight = FontWeight.SemiBold),
                h6 = body.copy(fontWeight = FontWeight.SemiBold),
                text = body,
                paragraph = body,
                ordered = body,
                bullet = body,
                list = body,
                code = body.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp),
                inlineCode = body.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                quote = body.copy(color = colors.secondaryLabel),
                table = body.copy(fontSize = 15.sp),
            ),
        )
    }
}
