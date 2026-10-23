package com.sam2kb.lyriqprobe.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.ui.theme.Amber
import com.sam2kb.lyriqprobe.ui.theme.Blue
import com.sam2kb.lyriqprobe.ui.theme.Faint
import com.sam2kb.lyriqprobe.ui.theme.Green
import com.sam2kb.lyriqprobe.ui.theme.Line
import com.sam2kb.lyriqprobe.ui.theme.LineBright
import com.sam2kb.lyriqprobe.ui.theme.Muted
import com.sam2kb.lyriqprobe.ui.theme.Paper
import com.sam2kb.lyriqprobe.ui.theme.Red
import com.sam2kb.lyriqprobe.ui.theme.Slate
import com.sam2kb.lyriqprobe.ui.theme.SlateHigh
import com.sam2kb.lyriqprobe.ui.theme.Teal
import com.sam2kb.lyriqprobe.ui.theme.Violet

enum class NoticeTone {
    INFO,
    SUCCESS,
    WARN,
    DANGER,
}

data class NoticeStyle(val color: Color, val dim: Color, val label: String)

fun noticeStyle(tone: NoticeTone): NoticeStyle = when (tone) {
    NoticeTone.INFO -> NoticeStyle(Blue, Color(0xFF102431), "NOTE")
    NoticeTone.SUCCESS -> NoticeStyle(Green, Color(0xFF142719), "PASS")
    NoticeTone.WARN -> NoticeStyle(Amber, Color(0xFF2D2415), "CAUTION")
    NoticeTone.DANGER -> NoticeStyle(Red, Color(0xFF301A1D), "BLOCKED")
}

fun statusColor(status: ResultStatus): Color = when (status) {
    ResultStatus.SUCCESS, ResultStatus.AVAILABLE -> Green
    ResultStatus.DENIED, ResultStatus.PRIVILEGED_ONLY -> Red
    ResultStatus.UNSUPPORTED, ResultStatus.NOT_EXPOSED -> Muted
    ResultStatus.UNAVAILABLE, ResultStatus.NOT_TESTED -> Faint
    ResultStatus.ERROR, ResultStatus.TIMEOUT -> Amber
}

@Composable
fun MonitorBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFF080C10), Color(0xFF0A1116), Color(0xFF080C10)),
                ),
            )
            .then(
                Modifier.border(
                    width = 1.dp,
                    color = Color(0xFF132028),
                ),
            ),
    )
}

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = "LYRIQ",
            style = MaterialTheme.typography.labelMedium,
            color = Amber,
        )
        Text(
            text = "CAPABILITY\nPROBE",
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            lineHeight = MaterialTheme.typography.titleMedium.lineHeight,
            color = Paper,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "AAOS / READ-ONLY",
            style = MaterialTheme.typography.labelSmall,
            color = Faint,
        )
    }
}

@Composable
fun SectionKicker(text: String, color: Color = Amber, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(1.dp)).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun PageHeader(
    kicker: String,
    title: String,
    description: String,
    aside: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            SectionKicker(kicker)
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.headlineLarge, color = Paper)
            Spacer(Modifier.height(7.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                modifier = Modifier.widthIn(max = 760.dp),
            )
        }
        if (aside != null) {
            Spacer(Modifier.width(20.dp))
            aside()
        }
    }
    Spacer(Modifier.height(20.dp))
    HorizontalDivider(color = Line)
    Spacer(Modifier.height(18.dp))
}

@Composable
fun Panel(
    title: String? = null,
    kicker: String? = null,
    accent: Color = Teal,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val surfaceModifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(5.dp))
        .background(Slate)
        .border(1.dp, Line, RoundedCornerShape(5.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    Column(surfaceModifier.padding(16.dp)) {
        if (title != null || kicker != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    if (kicker != null) {
                        SectionKicker(kicker, accent)
                        Spacer(Modifier.height(6.dp))
                    }
                    if (title != null) {
                        Text(title, style = MaterialTheme.typography.titleMedium, color = Paper)
                    }
                }
            }
            Spacer(Modifier.height(13.dp))
        }
        content()
    }
}

@Composable
fun KeyValue(
    label: String,
    value: String,
    mono: Boolean = true,
    valueColor: Color = Paper,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Faint,
            modifier = Modifier.width(150.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.SansSerif,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun StatusChip(
    status: ResultStatus,
    modifier: Modifier = Modifier,
) {
    val color = statusColor(status)
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(2.dp),
    ) {
        Text(
            text = status.name,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun SmallTag(text: String, color: Color = Muted, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.Transparent,
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(2.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun Notice(
    tone: NoticeTone,
    text: String,
    modifier: Modifier = Modifier,
) {
    val style = noticeStyle(tone)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(style.dim)
            .border(1.dp, style.color.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            style.label,
            style = MaterialTheme.typography.labelSmall,
            color = style.color,
            modifier = Modifier.width(66.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = Paper,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val color = when {
        danger -> Red
        primary -> Amber
        else -> Teal
    }
    Surface(
        modifier = modifier,
        color = if (primary) color.copy(alpha = if (enabled) 0.16f else 0.05f) else Color.Transparent,
        contentColor = if (enabled) color else color.copy(alpha = 0.35f),
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(1.dp, color.copy(alpha = if (enabled) 0.75f else 0.20f)),
        onClick = onClick,
        enabled = enabled,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MetricTile(
    value: String,
    label: String,
    accent: Color = Paper,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(SlateHigh)
            .border(1.dp, Line)
            .padding(12.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Monospace,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Faint,
            maxLines = 2,
        )
    }
}

@Composable
fun ResultRow(
    title: String,
    status: ResultStatus,
    summary: String,
    details: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = Paper,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(10.dp))
            StatusChip(status)
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(summary, style = MaterialTheme.typography.bodySmall, color = Muted)
        if (!details.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            SelectionContainer {
                Text(
                    details,
                    style = MaterialTheme.typography.labelSmall,
                    color = Faint,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    HorizontalDivider(color = Line.copy(alpha = 0.65f))
}

@Composable
fun EmptyNotice(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Line, RoundedCornerShape(4.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("NO DATA", style = MaterialTheme.typography.labelMedium, color = Faint)
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
        )
    }
}

@Composable
fun ThinProgress(value: Float?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(Line),
    ) {
        if (value == null) {
            Box(
                Modifier
                    .fillMaxWidth(0.32f)
                    .height(2.dp)
                    .background(Amber),
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(Amber),
            )
        }
    }
}

@Composable
fun MonoText(
    text: String,
    color: Color = Paper,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    SelectionContainer {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }
}

@Composable
fun Divider() {
    HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 10.dp))
}


@Composable
fun ScreenScroll(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 1220.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            content = content,
        )
    }
}
