package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.engine.BitaxeOptimizationEngine
import com.example.domain.model.CandidateCellStatus
import com.example.domain.model.DataProvenance
import com.example.domain.model.GridCandidate
import com.example.ui.BitaxeUiState
import com.example.ui.components.ProvenanceBadge
import com.example.ui.components.RecommendationOnlyBanner
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ThermalRed

@Composable
fun FreqVoltMatrixScreen(
    state: BitaxeUiState,
    strings: AppStrings,
    onSelectCell: (GridCandidate) -> Unit
) {
    val grid = state.precisionGrid
    val byKey = grid.associateBy { it.frequencyMhz to it.voltageMv }
    val selected = state.selectedMatrixCell

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            RecommendationOnlyBanner(text = strings.recommendationOnlyBanner)
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.GridView, contentDescription = strings.matrixTitle, tint = ElectricCyan)
                Column {
                    Text(strings.matrixTitle, style = MaterialTheme.typography.titleLarge)
                    Text(strings.matrixSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Legend Strip
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LegendPill("Active", ElectricCyan)
                LegendPill("Stable", NeonEmerald)
                LegendPill("Next Step", AmberCaution)
                LegendPill("Unstable", ThermalRed)
                LegendPill("Untested (${grid.count { it.status == CandidateCellStatus.UNTESTED }})", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Exact 15 x 15 = 225 Precision Matrix (5 MHz rows x 5 mV columns)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                        .horizontalScroll(rememberScrollState())
                        .testTag("precision_matrix_grid"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Voltage Header Row (1100..1170 mV step 5)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(62.dp), contentAlignment = Alignment.Center) {
                            Text("MHz\\mV", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        BitaxeOptimizationEngine.VOLTAGE_VALUES.forEach { volt ->
                            Box(modifier = Modifier.width(50.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$volt",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = ElectricCyan
                                )
                            }
                        }
                    }

                    // Frequency Rows (600..670 MHz step 5)
                    BitaxeOptimizationEngine.FREQUENCY_VALUES.forEach { freq ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.width(62.dp), contentAlignment = Alignment.CenterStart) {
                                Text(
                                    text = "$freq MHz",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = NeonEmerald
                                )
                            }
                            BitaxeOptimizationEngine.VOLTAGE_VALUES.forEach { volt ->
                                val cell = byKey[freq to volt]
                                val cellColor = when (cell?.status) {
                                    CandidateCellStatus.CURRENT_ACTIVE -> ElectricCyan
                                    CandidateCellStatus.MEASURED_STABLE -> NeonEmerald
                                    CandidateCellStatus.CANDIDATE_NEXT -> AmberCaution
                                    CandidateCellStatus.MEASURED_UNSTABLE -> ThermalRed
                                    CandidateCellStatus.OUTSIDE_FIRMWARE_LIMIT -> Color(0xFF475569)
                                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                                val isSelected = selected?.frequencyMhz == freq && selected.voltageMv == volt
                                Box(
                                    modifier = Modifier
                                        .size(width = 50.dp, height = 40.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (cell?.status == CandidateCellStatus.UNTESTED) {
                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                            } else {
                                                cellColor.copy(alpha = 0.24f)
                                            }
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color.White else cellColor.copy(alpha = 0.65f),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { if (cell != null) onSelectCell(cell) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val rec = cell?.measuredRecord
                                    val label = when {
                                        rec != null -> "${"%.2f".format(rec.avgHashrateThs)}"
                                        cell?.status == CandidateCellStatus.CANDIDATE_NEXT -> "NEXT"
                                        cell?.status == CandidateCellStatus.OUTSIDE_FIRMWARE_LIMIT -> "LIM"
                                        else -> "·"
                                    }
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = if (cell?.status == CandidateCellStatus.UNTESTED) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            cellColor
                                        },
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selected Cell Inspector
        selected?.let { cell ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${cell.frequencyMhz} MHz • ${cell.voltageMv} mV",
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = ElectricCyan
                            )
                            ProvenanceBadge(
                                provenance = if (cell.measuredRecord != null) DataProvenance.MEASURED else DataProvenance.UNTESTED,
                                label = if (cell.measuredRecord != null) strings.tagMeasured else strings.tagUntested
                            )
                        }
                        Text(
                            text = "Status: ${cell.status.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        cell.measuredRecord?.let { rec ->
                            Text(
                                text = "Hashrate: ${"%.3f".format(rec.avgHashrateThs)} TH/s • Power: ${"%.2f".format(rec.avgPowerWatts)} W • Eff: ${"%.2f".format(rec.efficiencyJth)} J/TH",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonEmerald
                            )
                            Text(
                                text = "Samples: ${rec.sampleCount} • Duration: ${rec.durationSeconds}s • Stability: ${rec.stabilityScore.toInt()}/100",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        cell.compositeScore?.let { score ->
                            Text(
                                text = "${strings.profileName(state.activeProfile)} Score: ${"%.1f".format(score)} / 100",
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                        }
                        if (cell.safetyIssues.isNotEmpty()) {
                            cell.safetyIssues.forEach { issue ->
                                Text(text = "• $issue", style = MaterialTheme.typography.bodySmall, color = ThermalRed)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun EfficiencyMonitorScreen(
    state: BitaxeUiState,
    strings: AppStrings
) {
    val live = state.liveTelemetry
    val comp = state.efficiencyComparison

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            RecommendationOnlyBanner(text = strings.recommendationOnlyBanner)
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.ElectricBolt, contentDescription = strings.efficiencyTitle, tint = NeonEmerald)
                Text(strings.efficiencyTitle, style = MaterialTheme.typography.titleLarge)
            }
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = strings.efficiencyFormulaNote,
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EfficiencyRow(
                        label = strings.currentEfficiencyLabel,
                        value = comp?.currentEfficiencyJth?.let { "${"%.2f".format(it)} J/TH" } ?: strings.notAvailable,
                        provenance = comp?.currentProvenance ?: DataProvenance.UNAVAILABLE,
                        provenanceLabel = if (comp?.currentEfficiencyJth != null) strings.tagMeasured else strings.tagUnavailable
                    )
                    EfficiencyRow(
                        label = strings.previousEfficiencyLabel,
                        value = comp?.previousEfficiencyJth?.let { "${"%.2f".format(it)} J/TH" } ?: strings.notAvailable,
                        provenance = if (comp?.previousEfficiencyJth != null) DataProvenance.MEASURED else DataProvenance.UNAVAILABLE,
                        provenanceLabel = if (comp?.previousEfficiencyJth != null) strings.tagMeasured else strings.tagUnavailable
                    )
                    EfficiencyRow(
                        label = strings.recommendedEfficiencyLabel,
                        value = comp?.recommendedEfficiencyJth?.let { "${"%.2f".format(it)} J/TH" } ?: strings.notAvailable,
                        provenance = comp?.recommendedProvenance ?: DataProvenance.UNAVAILABLE,
                        provenanceLabel = when {
                            comp?.recommendedEfficiencyJth == null -> strings.tagUnavailable
                            comp.recommendedProvenance == DataProvenance.MEASURED -> strings.tagMeasured
                            else -> strings.tagEstimated
                        }
                    )

                    val pct = comp?.improvementPercentVsCurrent
                    val pctText = when {
                        pct == null -> strings.notAvailable
                        pct > 0.01 -> "+${"%.2f".format(pct)}% improvement (lower J/TH)"
                        pct < -0.01 -> "${"%.2f".format(pct)}% deterioration (higher J/TH)"
                        else -> "0.00% (identical)"
                    }
                    Text(
                        text = "Efficiency Delta: $pctText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if ((pct ?: 0.0) >= 0) NeonEmerald else AmberCaution
                    )

                    Text(
                        text = "Power Difference: ${comp?.powerDiffWatts?.let { "${if (it >= 0) "+" else ""}${"%.2f".format(it)} W" } ?: strings.notAvailable}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Hashrate Difference: ${comp?.hashrateDiffThs?.let { "${if (it >= 0) "+" else ""}${"%.3f".format(it)} TH/s" } ?: strings.notAvailable}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Live Unit Conversion Breakdown
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Live Unit Conversion Breakdown", style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
                    val ghs = live?.currentHashrateGhs
                    val ths = live?.currentHashrateThs
                    val watts = live?.powerWatts
                    val jth = live?.efficiencyJth
                    Text(
                        text = "API Hashrate: ${ghs?.let { "${"%.2f".format(it)} GH/s" } ?: strings.notAvailable}  →  ${ths?.let { "${"%.4f".format(it)} TH/s" } ?: strings.notAvailable}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily)
                    )
                    Text(
                        text = "Measured Power: ${watts?.let { "${"%.2f".format(it)} W" } ?: strings.notAvailable}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily)
                    )
                    Text(
                        text = "Calculated J/TH: ${jth?.let { "${"%.2f".format(it)} J/TH" } ?: strings.notAvailable}",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = NeonEmerald
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun LegendPill(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EfficiencyRow(
    label: String,
    value: String,
    provenance: DataProvenance,
    provenanceLabel: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ProvenanceBadge(provenance = provenance, label = provenanceLabel)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}
