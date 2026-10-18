package com.hermex.android.feature.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermex.android.core.designsystem.GlassCircleButton
import com.hermex.android.core.designsystem.HermexTheme
import com.hermex.android.core.designsystem.glassSurface

private val CapsuleShape = RoundedCornerShape(50)
internal val CardShape = RoundedCornerShape(18.dp)

@Composable
internal fun PanelScaffold(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .background(HermexTheme.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars),
        content = content,
    )
}

@Composable
internal fun PanelTopBar(
    title: String,
    onBack: () -> Unit,
    backTag: String,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = HermexTheme.colors
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 48.dp)) {
        GlassCircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, Modifier.align(Alignment.CenterStart).testTag(backTag))
        Text(
            title,
            color = colors.label,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 112.dp),
        )
        if (actions != null) {
            Row(
                Modifier.align(Alignment.CenterEnd).heightIn(min = 44.dp).glassSurface(CapsuleShape).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@Composable
internal fun BarIcon(icon: ImageVector, description: String, tag: String, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
    val colors = HermexTheme.colors
    Box(
        Modifier.size(44.dp).clip(CapsuleShape).clickable(enabled = enabled && !busy, onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), color = colors.secondaryLabel, strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = description, tint = if (enabled) colors.label else colors.tertiaryLabel, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
internal fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = HermexTheme.colors
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title.uppercase(),
                color = colors.secondaryLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        Column(
            Modifier.fillMaxWidth().clip(CardShape).background(colors.secondaryBackground).padding(horizontal = 16.dp, vertical = 14.dp),
            content = content,
        )
    }
}

@Composable
internal fun StatusBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier.clip(CapsuleShape).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
internal fun Segmented(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier,
) {
    val colors = HermexTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (colors.isDark) colors.secondaryBackground else Color(0xFFEEEEF0))
            .padding(2.dp),
    ) {
        options.forEach { (key, label) ->
            val active = key == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (!active) Color.Transparent else if (colors.isDark) Color(0xFF636366) else colors.tertiaryBackground)
                    .clickable { onSelect(key) }
                    .heightIn(min = 36.dp)
                    .testTag("$tagPrefix.$key"),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = colors.label, fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun ContentUnavailable(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionTag: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = HermexTheme.colors
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(44.dp))
        Spacer(Modifier.size(14.dp))
        Text(title, color = colors.label, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.size(6.dp))
        Text(message, color = colors.secondaryLabel, fontSize = 15.sp, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.size(14.dp))
            Text(
                actionLabel,
                color = colors.accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CapsuleShape)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .then(if (actionTag != null) Modifier.testTag(actionTag) else Modifier),
            )
        }
    }
}

@Composable
internal fun LoadingState(text: String, modifier: Modifier = Modifier) {
    val colors = HermexTheme.colors
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), color = colors.secondaryLabel, strokeWidth = 2.5.dp)
        Spacer(Modifier.size(12.dp))
        Text(text, color = colors.secondaryLabel, fontSize = 14.sp)
    }
}

@Composable
internal fun LabelValueRow(label: String, value: String, modifier: Modifier = Modifier, labelWidth: Int = 82) {
    val colors = HermexTheme.colors
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = colors.secondaryLabel, fontSize = 14.sp, modifier = Modifier.width(labelWidth.dp))
        Text(value, color = colors.label, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}
