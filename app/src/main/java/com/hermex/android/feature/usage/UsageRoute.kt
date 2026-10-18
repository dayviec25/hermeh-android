package com.hermex.android.feature.usage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hermex.android.core.data.UsageRepository
import com.hermex.android.core.designsystem.HermexTheme

@Composable
fun UsageRoute(repository: UsageRepository, profile: String, onBack: () -> Unit) {
    val viewModel: UsageViewModel = viewModel(factory = viewModelFactory { initializer { UsageViewModel(repository, profile) } })
    val state by viewModel.state.collectAsStateWithLifecycle()
    UsageScreen(state, onBack, viewModel::load, viewModel::selectWindow, viewModel::selectMetric)
}

@Composable
internal fun segmentColor(segment: UsageSegment): Color {
    val dark = HermexTheme.colors.isDark
    return when (segment) {
        UsageSegment.Input -> if (dark) Color(0xFF0A84FF) else Color(0xFF007AFF)
        UsageSegment.Output -> if (dark) Color(0xFFFF9F0A) else Color(0xFFFF9500)
        UsageSegment.CacheRead -> if (dark) Color(0xFF40C8E0) else Color(0xFF30B0C7)
        UsageSegment.Cost -> if (dark) Color(0xFF5E5CE6) else Color(0xFF5856D6)
        UsageSegment.Sessions -> if (dark) Color(0xFFBF5AF2) else Color(0xFFAF52DE)
    }
}

@Composable
internal fun UsageScreen(
    state: UsageScreenState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onWindow: (Int) -> Unit,
    onMetric: (UsageMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    PanelScaffold(modifier) {
        PanelTopBar(title = "Usage", onBack = onBack, backTag = "usage.back") {
            BarIcon(Icons.Filled.Refresh, "Refresh", "usage.refresh", busy = state.refreshing && state.content !is UsageUiState.Loading, onClick = onRefresh)
        }
        val picker = @Composable {
            Segmented(
                options = listOf(7, 30, 90).map { it.toString() to "$it days" },
                selected = state.days.toString(),
                onSelect = { onWindow(it.toInt()) },
                tagPrefix = "usage.window",
            )
        }
        when (val content = state.content) {
            UsageUiState.Loading -> Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                picker()
                LoadingState("Loading usage...")
            }
            UsageUiState.Empty -> Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                picker()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ContentUnavailable(Icons.Filled.BarChart, "No Data", "Session usage data will appear here once you have conversations.")
                }
            }
            is UsageUiState.Error -> Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                picker()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ContentUnavailable(Icons.Filled.Warning, "Could Not Load Usage", content.message, actionLabel = "Try Again", actionTag = "usage.retry", onAction = onRefresh)
                }
            }
            is UsageUiState.Success -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                picker()
                ChartCard(content.view, state.metric, onMetric)
                TotalsGrid(content.view)
                if (content.view.report.models.isNotEmpty()) ModelsCard(content.view)
                if (content.view.report.tools.isNotEmpty()) ToolsCard(content.view)
                Text(
                    "Source: server insights from the last ${content.view.report.periodDays} days.",
                    color = HermexTheme.colors.secondaryLabel, fontSize = 12.sp, modifier = Modifier.testTag("usage.source"),
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ChartCard(view: UsageView, metric: UsageMetric, onMetric: (UsageMetric) -> Unit) {
    val colors = HermexTheme.colors
    var selected by remember(view, metric) { mutableStateOf<Int?>(null) }
    val buckets = view.buckets
    val bucket = selected?.let { buckets.getOrNull(it) }
    val (label, value, caption) = when {
        bucket != null -> Triple(
            bucket.label,
            when (metric) {
                UsageMetric.Cost -> UsageFormat.cost(bucket.cost)
                UsageMetric.Tokens -> UsageFormat.tokens(bucket.input + bucket.output)
                UsageMetric.Sessions -> bucket.sessions.toString()
            },
            listOfNotNull(
                if (metric != UsageMetric.Tokens) "${UsageFormat.tokens(bucket.input + bucket.output)} tokens" else null,
                if (bucket.cacheRead > 0) "${UsageFormat.tokens(bucket.cacheRead)} cached" else null,
                "${bucket.sessions} sessions",
            ).joinToString(" · "),
        )
        metric == UsageMetric.Cost -> Triple("Estimated cost", UsageFormat.cost(view.cost), "Estimated by the server from session metadata.")
        metric == UsageMetric.Tokens -> Triple("Processed tokens", UsageFormat.tokens(view.processedTokens), "Across ${view.report.totals.sessions} sessions.")
        else -> Triple("Sessions", view.report.totals.sessions.toString(), "Sessions in the last ${view.report.periodDays} days.")
    }
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text(label, color = colors.secondaryLabel, fontSize = 14.sp)
                Text(value, color = colors.label, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.testTag("usage.hero"))
                Text(caption, color = colors.secondaryLabel, fontSize = 12.sp)
            }
            Segmented(
                options = UsageMetric.entries.map { it.slug to it.title },
                selected = metric.slug,
                onSelect = { slug -> onMetric(UsageMetric.entries.first { it.slug == slug }) },
                tagPrefix = "usage.metric",
            )
            val max = buckets.maxOfOrNull { it.total(metric) } ?: 0.0
            if (max <= 0.0) {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    Text("No activity in this window.", color = colors.secondaryLabel, fontSize = 16.sp)
                }
            } else {
                val segmentColors = UsageSegment.entries.associateWith { segmentColor(it) }
                val track = colors.label.copy(alpha = 0.06f)
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag("usage.chart")
                        .semantics { contentDescription = "${metric.title} per day, last ${view.report.periodDays} days" }
                        .pointerInput(buckets, metric) {
                            detectTapGestures { offset ->
                                val index = ((offset.x / size.width) * buckets.size).toInt().coerceIn(0, buckets.lastIndex)
                                selected = if (selected == index) null else index
                            }
                        },
                ) {
                    val slot = size.width / buckets.size
                    val gap = (slot * 0.28f).coerceAtMost(6.dp.toPx())
                    val barWidth = (slot - gap).coerceAtLeast(1.5f)
                    val radius = CornerRadius((barWidth / 2).coerceAtMost(4.dp.toPx()))
                    buckets.forEachIndexed { index, item ->
                        val x = index * slot + gap / 2
                        val alpha = if (selected == null || selected == index) 1f else 0.3f
                        drawRoundRect(track, Offset(x, 0f), Size(barWidth, size.height), radius)
                        var top = size.height
                        item.segments(metric).forEach { (segment, amount) ->
                            if (amount <= 0) return@forEach
                            val h = (amount / max * size.height).toFloat().coerceAtLeast(2f)
                            top -= h
                            drawRoundRect(requireNotNull(segmentColors[segment]).copy(alpha = alpha), Offset(x, top), Size(barWidth, h), radius)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(buckets.firstOrNull()?.label.orEmpty(), color = colors.secondaryLabel, fontSize = 11.sp, modifier = Modifier.weight(1f))
                if (metric == UsageMetric.Tokens) {
                    listOf(UsageSegment.Input, UsageSegment.Output, UsageSegment.CacheRead).forEach { segment ->
                        Box(Modifier.size(7.dp).clip(CircleShape).background(segmentColor(segment)))
                        Text(segment.title, color = colors.secondaryLabel, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, end = 10.dp))
                    }
                }
                Text(buckets.lastOrNull()?.label.orEmpty(), color = colors.secondaryLabel, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
    }
}

@Composable
private fun TotalsGrid(view: UsageView) {
    val totals = view.report.totals
    val cells = listOf(
        Triple("Sessions", totals.sessions.toString(), "${UsageFormat.tokens(totals.apiCalls.toLong())} API calls"),
        Triple("Estimated cost", UsageFormat.cost(view.cost), if (totals.actualCost > 0) "actual, reported by the provider" else "estimated by the server"),
        Triple("Processed tokens", UsageFormat.tokens(view.processedTokens), "${UsageFormat.tokens(totals.input)} in · ${UsageFormat.tokens(totals.output)} out"),
        Triple("Cached input", UsageFormat.tokens(totals.cacheRead), view.cacheHitPercent?.let { "${UsageFormat.percent(it)} hit rate" } ?: "no cache reads"),
    )
    SectionCard(title = "Totals", modifier = Modifier.testTag("usage.totals")) {
        cells.chunked(2).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (label, value, detail) ->
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(label, color = HermexTheme.colors.secondaryLabel, fontSize = 12.sp)
                        Text(value, color = HermexTheme.colors.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(detail, color = HermexTheme.colors.tertiaryLabel, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelsCard(view: UsageView) {
    val colors = HermexTheme.colors
    val models = view.report.models
    val totalCost = models.sumOf { it.estimatedCost }
    val totalTokens = models.sumOf { it.inputTokens + it.outputTokens }
    SectionCard(title = "By model") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            models.sortedByDescending { it.estimatedCost * 1e9 + it.inputTokens + it.outputTokens }.forEach { model ->
                val tokens = model.inputTokens + model.outputTokens
                val share = when {
                    totalCost > 0 -> "${Math.round(model.estimatedCost / totalCost * 100)}% of cost"
                    totalTokens > 0 -> "${Math.round(tokens.toDouble() / totalTokens * 100)}% of tokens"
                    else -> null
                }
                val line = listOfNotNull(model.provider, share, "${UsageFormat.tokens(tokens)} tokens", "${model.sessions} sessions").joinToString(" · ")
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.testTag("usage.model.${model.model}")) {
                    Column(Modifier.weight(1f)) {
                        Text(model.model, color = colors.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                        Text(line, color = colors.secondaryLabel, fontSize = 12.sp)
                    }
                    if (model.estimatedCost > 0) Text(UsageFormat.cost(model.estimatedCost), color = colors.label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun ToolsCard(view: UsageView) {
    val colors = HermexTheme.colors
    SectionCard(title = "Tools") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            view.report.tools.sortedByDescending { it.count }.take(12).forEach { tool ->
                Column(Modifier.testTag("usage.tool.${tool.tool}")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tool.tool, color = colors.label, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${tool.count} · ${UsageFormat.percent(Math.round(tool.percentage * 10) / 10.0)}", color = colors.secondaryLabel, fontSize = 13.sp)
                    }
                    Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.label.copy(alpha = 0.08f))) {
                        Box(
                            Modifier.fillMaxWidth((tool.percentage / 100).toFloat().coerceIn(0f, 1f)).height(4.dp)
                                .clip(RoundedCornerShape(2.dp)).background(segmentColor(UsageSegment.Input)),
                        )
                    }
                }
            }
        }
    }
}

