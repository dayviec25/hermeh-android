package com.hermeh.android.feature.bots

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermeh.android.core.designsystem.GlassCircleButton
import com.hermeh.android.core.designsystem.HermehTheme
import com.hermeh.android.core.designsystem.glassSurface
import com.hermeh.android.core.model.BotLiveStatus

@Composable
fun BotsRoute(
    viewModel: BotsViewModel,
    onBack: () -> Unit,
    onOpenChat: (BotChatTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onVisible()
        onPauseOrDispose { viewModel.onHidden() }
    }
    val target = (state as? BotsUiState.Success)?.openChat
    LaunchedEffect(target) {
        if (target != null) {
            viewModel.onChatOpened()
            onOpenChat(target)
        }
    }
    BotsScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        onQueryChange = viewModel::onQueryChange,
        onOpen = { viewModel.open(it.bot) },
        onDismissNotice = viewModel::dismissNotice,
        modifier = modifier,
    )
}

@Composable
fun BotsScreen(
    state: BotsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOpen: (BotRowUi) -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HermehTheme.colors
    Column(modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassCircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, Modifier.testTag("bots.back"))
            Text("Bots", color = colors.label, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 14.dp))
        }
        val success = state as? BotsUiState.Success
        SearchBar(success?.query.orEmpty(), onQueryChange, enabled = success != null)
        when (state) {
            BotsUiState.Loading -> Box(Modifier.fillMaxSize().testTag("bots.loading"), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
            BotsUiState.Empty -> Message("No bots yet. Tap + to create one.", null, null, Modifier.testTag("bots.empty"))
            is BotsUiState.Error -> Message("Could Not Load Profiles", state.message, onRetry, Modifier.testTag("bots.error"))
            is BotsUiState.Success -> {
                if (state.notice != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.danger.copy(alpha = if (colors.isDark) 0.22f else 0.12f))
                            .padding(start = 14.dp)
                            .heightIn(min = 44.dp)
                            .testTag("bots.notice"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(state.notice, color = colors.label, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismissNotice) { Text("Dismiss", color = colors.accent) }
                    }
                }
                PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize().testTag("bots.list"), contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp)) {
                        if (state.rows.isEmpty()) {
                            item(key = "nomatch", contentType = "status") {
                                Message("No Matches", null, null, Modifier.padding(top = 48.dp).testTag("bots.nomatch"))
                            }
                        }
                        items(state.rows, key = { it.key }, contentType = { "bot" }) { row ->
                            BotRow(row, opening = state.opening == row.key, onClick = { onOpen(row) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    val colors = HermehTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .heightIn(min = 44.dp)
            .glassSurface(RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Search bots and messages", color = colors.secondaryLabel, fontSize = 16.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(color = colors.label, fontSize = 16.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth().testTag("bots.search"),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Clear",
                tint = colors.secondaryLabel,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onValueChange("") },
            )
        }
    }
}

@Composable
private fun BotRow(row: BotRowUi, opening: Boolean, onClick: () -> Unit) {
    val colors = HermehTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (row.dimmed) 0.5f else 1f)
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = !opening, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("bots.row.${row.key}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(row.initials, row.bot.colorHex)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.bot.name,
                    color = colors.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                when {
                    opening -> CircularProgressIndicator(strokeWidth = 2.dp, color = colors.secondaryLabel, modifier = Modifier.size(16.dp))
                    row.live != null -> Text(
                        row.live.label,
                        color = if (row.live == BotLiveStatus.Waiting) colors.approval else colors.working,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("bots.row.${row.key}.status"),
                    )
                    row.time != null -> Text(row.time, color = colors.secondaryLabel, fontSize = 14.sp, modifier = Modifier.testTag("bots.row.${row.key}.time"))
                }
            }
            Text(
                row.subline,
                color = colors.secondaryLabel,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("bots.row.${row.key}.preview"),
            )
        }
    }
}

@Composable
private fun Avatar(initials: String, hex: String?) {
    val colors = HermehTheme.colors
    val fill = parseHex(hex)?.takeIf { hex?.lowercase() != ADAPTIVE } ?: colors.label
    val ink = if (fill.luminance() > 0.62f) Color.Black else Color.White
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(fill).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Message(title: String, detail: String?, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = HermehTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = colors.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (detail != null) {
            Text(detail, color = colors.secondaryLabel, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        }
        if (onRetry != null) {
            TextButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp).testTag("bots.retry")) {
                Text("Try Again", color = colors.accent, fontSize = 16.sp)
            }
        }
    }
}

private const val ADAPTIVE = "#ffffff"

private fun parseHex(hex: String?): Color? {
    val digits = hex?.removePrefix("#") ?: return null
    if (digits.length != 6) return null
    val value = digits.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or value)
}
