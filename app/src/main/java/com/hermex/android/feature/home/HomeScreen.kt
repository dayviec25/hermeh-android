package com.hermex.android.feature.home

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermex.android.R
import com.hermex.android.core.designsystem.HermexTheme
import com.hermex.android.core.designsystem.HermexWordmark
import com.hermex.android.core.designsystem.glassSurface

enum class HomeDestination(val slug: String, val title: String, @DrawableRes val icon: Int, val expandable: Boolean) {
    Bots("bots", "Bots", R.drawable.ic_lucide_bot, false),
    Tasks("tasks", "Tasks", R.drawable.ic_lucide_calendar_clock, false),
    Kanban("kanban", "Kanban", R.drawable.ic_lucide_columns3, false),
    Skills("skills", "Skills", R.drawable.ic_lucide_hammer, false),
    Memory("memory", "Memory", R.drawable.ic_lucide_brain, false),
    Insights("insights", "Insights", R.drawable.ic_lucide_chart_column_increasing, false),
    ActiveProfile("activeProfile", "Active Profile", R.drawable.ic_lucide_user_round_cog, true),
    Projects("projects", "Projects", R.drawable.ic_lucide_folder, true),
}

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    initials: String,
    onOpenSession: (SessionRowUi) -> Unit,
    onNewChat: () -> Unit,
    onDestination: (HomeDestination) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        initials = initials,
        onRefresh = { viewModel.refresh() },
        onOpenSession = onOpenSession,
        onNewChat = onNewChat,
        onDestination = onDestination,
        onSearch = onSearch,
        onSettings = onSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    initials: String,
    onRefresh: () -> Unit,
    onOpenSession: (SessionRowUi) -> Unit,
    onNewChat: () -> Unit,
    onDestination: (HomeDestination) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HermexTheme.colors
    Box(modifier.fillMaxSize().background(colors.background)) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("home.list"),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                item(key = "header", contentType = "header") {
                    Header(initials = initials, onSearch = onSearch, onSettings = onSettings)
                }
                items(HomeDestination.entries, key = { "nav-${it.slug}" }, contentType = { "nav" }) { destination ->
                    NavRow(destination, onClick = { onDestination(destination) })
                }
                item(key = "sessions-title", contentType = "title") {
                    Text(
                        "Sessions",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.label,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 8.dp),
                    )
                }
                when (val content = state.sessions) {
                    SessionsContent.Loading -> item(key = "loading", contentType = "status") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        }
                    }
                    SessionsContent.Empty -> item(key = "empty", contentType = "status") {
                        StatusText("No sessions yet. Start a chat to begin.", Modifier.testTag("home.empty"))
                    }
                    is SessionsContent.Error -> item(key = "error", contentType = "status") {
                        StatusText(content.message, Modifier.testTag("home.error"))
                    }
                    is SessionsContent.Success -> items(content.rows, key = { it.key }, contentType = { "session" }) { row ->
                        SessionRow(row, onClick = { onOpenSession(row) })
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 20.dp, bottom = 16.dp)
                .glassSurface(CircleShape, elevated = true)
                .clickable(onClick = onNewChat)
                .padding(horizontal = 22.dp, vertical = 15.dp)
                .testTag("home.chat"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.EditNote, contentDescription = null, tint = colors.label, modifier = Modifier.size(22.dp))
            Text("Chat", color = colors.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Header(initials: String, onSearch: () -> Unit, onSettings: () -> Unit) {
    val colors = HermexTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HermexWordmark(color = colors.logo, modifier = Modifier.width(160.dp))
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.glassSurface(CircleShape).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onSearch).testTag("home.search"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Search, contentDescription = "Search", tint = colors.label, modifier = Modifier.size(24.dp))
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onSettings)
                    .testTag("home.avatar"),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(colors.logo),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(initials, color = colors.onLogo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun NavRow(destination: HomeDestination, onClick: () -> Unit) {
    val colors = HermexTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("home.row.${destination.slug}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(destination.icon), contentDescription = null, tint = colors.label, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(18.dp))
        Text(destination.title, color = colors.label, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (destination.expandable) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.secondaryLabel)
        }
    }
}

@Composable
private fun SessionRow(row: SessionRowUi, onClick: () -> Unit) {
    val colors = HermexTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 54.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("session.row.${row.key}"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (row.unread) {
                Box(Modifier.padding(top = 6.dp, end = 6.dp).size(10.dp).clip(CircleShape).background(colors.accent))
            }
            Text(
                row.title,
                color = colors.label,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(row.time, color = colors.secondaryLabel, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Text(row.subtitle, color = colors.secondaryLabel, fontSize = 12.sp)
    }
}

@Composable
private fun StatusText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = HermexTheme.colors.secondaryLabel,
        fontSize = 14.sp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
    )
}
