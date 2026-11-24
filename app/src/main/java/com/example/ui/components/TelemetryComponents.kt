package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.DataProvenance
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ThermalRed
import kotlin.math.roundToInt

@Composable
fun RecommendationOnlyBanner(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = text,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ProvenanceBadge(
    provenance: DataProvenance,
    label: String,
    modifier: Modifier = Modifier
) {
    val color = when (provenance) {
        DataProvenance.MEASURED -> NeonEmerald
        DataProvenance.ESTIMATED -> ElectricCyan
        DataProvenance.UNTESTED -> AmberCaution
        DataProvenance.UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TelemetryKpiCard(
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    accentColor: Color,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }

            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

enum class TelemetryChartMetric(val labelEn: String, val unit: String, val color: Color) {
    HASHRATE("Hashrate", "TH/s", NeonEmerald),
    POWER("Power", "W", ElectricCyan),
    EFFICIENCY("Efficiency", "J/TH", NeonEmerald),
    ASIC_TEMP("ASIC Temp", "°C", AmberCaution),
    FREQUENCY("Frequency", "MHz", ElectricCyan),
    VOLTAGE("Voltage", "mV", ElectricCyan),
    ASIC_ERRORS("ASIC Errors", "%", ThermalRed)
}

@Composable
fun InteractiveTelemetryChart(
    title: String,
    valuesByMetric: Map<TelemetryChartMetric, List<Double>>,
    emptyMessage: String,
    modifier: Modifier = Modifier
) {
    var selectedMetric by remember { mutableIntStateOf(0) }
    val metrics = TelemetryChartMetric.entries
    val activeMetric = metrics[selectedMetric]
    val series = valuesByMetric[activeMetric].orEmpty()
    var scrubbedIndex by remember(activeMetric, series.size) { mutableIntStateOf(-1) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val displayVal = when {
                    scrubbedIndex in series.indices -> series[scrubbedIndex]
                    series.isNotEmpty() -> series.last()
                    else -> null
                }
                Text(
                    text = if (displayVal != null) {
                        "${"%.2f".format(displayVal)} ${activeMetric.unit}"
                    } else "N/A",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    color = activeMetric.color
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                metrics.forEachIndexed { idx, metric ->
                    val isSelected = idx == selectedMetric
                    val bg by animateColorAsState(
                        if (isSelected) metric.color.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        label = "chartTabBg"
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) metric.color else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedMetric = idx }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = metric.labelEn,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) metric.color else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (series.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val minV = series.minOrNull() ?: 0.0
                val maxV = series.maxOrNull() ?: 1.0
                val span = (maxV - minV).let { if (it < 1e-6) 1.0 else it }
                val lineColor = activeMetric.color
                val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(155.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .pointerInput(series) {
                                detectTapGestures { offset ->
                                    val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    scrubbedIndex = (ratio * (series.size - 1)).roundToInt()
                                }
                            }
                            .pointerInput(series) {
                                detectDragGestures(
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val ratio = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        scrubbedIndex = (ratio * (series.size - 1)).roundToInt()
                                    },
                                    onDragEnd = { scrubbedIndex = -1 }
                                )
                            }
                            .padding(10.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            // Subtle horizontal grid lines
                            for (i in 0..3) {
                                val y = h * (i / 3f)
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, y),
                                    end = Offset(w, y),
                                    strokeWidth = 1f
                                )
                            }

                            val stepX = if (series.size > 1) w / (series.size - 1).toFloat() else w
                            val linePath = Path()
                            val fillPath = Path()

                            series.forEachIndexed { index, v ->
                                val x = index * stepX
                                val norm = ((v - minV) / span).toFloat().coerceIn(0f, 1f)
                                val y = h - (norm * (h * 0.85f) + h * 0.08f)
                                if (index == 0) {
                                    linePath.moveTo(x, y)
                                    fillPath.moveTo(x, h)
                                    fillPath.lineTo(x, y)
                                } else {
                                    linePath.lineTo(x, y)
                                    fillPath.lineTo(x, y)
                                }
                                if (index == series.lastIndex) {
                                    fillPath.lineTo(x, h)
                                    fillPath.close()
                                }
                            }

                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        lineColor.copy(alpha = 0.28f),
                                        Color.Transparent
                                    )
                                )
                            )

                            drawPath(
                                path = linePath,
                                color = lineColor,
                                style = Stroke(width = 3.2f, cap = StrokeCap.Round)
                            )

                            val highlightIdx = if (scrubbedIndex in series.indices) scrubbedIndex else series.lastIndex
                            val hx = highlightIdx * stepX
                            val hNorm = ((series[highlightIdx] - minV) / span).toFloat().coerceIn(0f, 1f)
                            val hy = h - (hNorm * (h * 0.85f) + h * 0.08f)

                            drawLine(
                                color = lineColor.copy(alpha = 0.4f),
                                start = Offset(hx, 0f),
                                end = Offset(hx, h),
                                strokeWidth = 1.5f
                            )
                            drawCircle(
                                color = lineColor,
                                radius = 6f,
                                center = Offset(hx, hy)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Min: ${"%.2f".format(minV)} ${activeMetric.unit}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${series.size} samples",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Max: ${"%.2f".format(maxV)} ${activeMetric.unit}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
