package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AnalysisProfile
import com.example.domain.model.DataProvenance
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
fun AnalyzerScreen(
    state: BitaxeUiState,
    strings: AppStrings,
    onStartRecording: () -> Unit,
    onStopAndSave: () -> Unit,
    onNavigateToConnect: () -> Unit,
    onNavigateToMatrix: () -> Unit
) {
    val live = state.liveTelemetry
    val recording = state.activeRecording
    val thresholds = state.settings.safetyThresholds
    val rec = state.recommendation

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
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Science, contentDescription = strings.analyzerTitle, tint = NeonEmerald)
                        Column {
                            Text(strings.analyzerTitle, style = MaterialTheme.typography.titleMedium)
                            Text(strings.analyzerSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (live == null) {
                        Text(
                            text = strings.notConnectedPrompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onNavigateToConnect,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(strings.goToConnect)
                        }
                    } else {
                        val activeFreq = live.frequencyMhz
                        val activeVolt = live.coreVoltageTargetMv ?: live.coreVoltageActualMv
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Active Config", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "${activeFreq ?: "?"} MHz • ${activeVolt ?: "?"} mV",
                                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = ElectricCyan
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Live Eff.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = live.efficiencyJth?.let { "${"%.2f".format(it)} J/TH" } ?: strings.notAvailable,
                                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = NeonEmerald
                                    )
                                }
                            }
                        }

                        if (recording == null) {
                            Button(
                                onClick = onStartRecording,
                                enabled = activeFreq != null && activeVolt != null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("start_recording_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonEmerald,
                                    contentColor = Color(0xFF002110)
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = strings.startRecording)
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(strings.startRecording, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            val elapsed = recording.elapsedSeconds
                            val progress = (elapsed.toFloat() / thresholds.minObservationSeconds.toFloat()).coerceIn(0f, 1f)
                            Surface(
                                color = NeonEmerald.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.FiberManualRecord, contentDescription = strings.recordingActive, tint = NeonEmerald, modifier = Modifier.size(12.dp))
                                        Text(strings.recordingActive, style = MaterialTheme.typography.labelMedium, color = NeonEmerald)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("${strings.samplesLabel}: ${recording.samples.size} / min ${thresholds.minSampleCount}", style = MaterialTheme.typography.bodySmall)
                                        Text("${strings.elapsedLabel}: ${elapsed}s / ${thresholds.minObservationSeconds}s", style = MaterialTheme.typography.bodySmall)
                                    }
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = NeonEmerald
                                    )
                                }
                            }

                            Button(
                                onClick = onStopAndSave,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("stop_save_recording_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ThermalRed,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = strings.stopAndSave)
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(strings.stopAndSave, fontWeight = FontWeight.Bold)
                            }
                        }

                        state.recordingStatusMessage?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = ElectricCyan
                            )
                        }
                    }
                }
            }
        }

        // Controlled Single-Variable Next Experiment Card
        item {
            val nextCandidate = rec?.nextExperimentCandidate
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f))
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
                        Text(strings.nextExperimentTitle, style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
                        ProvenanceBadge(DataProvenance.UNTESTED, strings.tagUntested)
                    }
                    if (nextCandidate != null) {
                        Text(
                            text = "${nextCandidate.frequencyMhz} MHz  •  ${nextCandidate.voltageMv} mV",
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = strings.applyManuallyNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "All nearby 5 MHz / 5 mV configurations within verified firmware limits have been sampled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onNavigateToMatrix,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(strings.navMatrix, color = ElectricCyan)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun RecommendationScreen(
    state: BitaxeUiState,
    strings: AppStrings,
    onSelectProfile: (AnalysisProfile) -> Unit,
    onNavigateToAnalyzer: () -> Unit
) {
    val rec = state.recommendation

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
            Text(
                text = strings.recommendScreenTitle,
                style = MaterialTheme.typography.titleLarge,
                color = NeonEmerald
            )
        }

        // Three Selectable Analysis Profiles (ECO, BALANCED default, BEAST)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnalysisProfile.entries.forEach { profile ->
                        val selected = state.activeProfile == profile
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectProfile(profile) },
                            label = {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("profile_chip_${profile.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonEmerald.copy(alpha = 0.2f),
                                selectedLabelColor = NeonEmerald
                            )
                        )
                    }
                }
                Text(
                    text = when (state.activeProfile) {
                        AnalysisProfile.ECO -> strings.profileEcoDesc
                        AnalysisProfile.BALANCED -> strings.profileBalancedDesc
                        AnalysisProfile.BEAST -> strings.profileBeastDesc
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (rec == null || rec.isInsufficientData) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, AmberCaution.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.WarningAmber, contentDescription = "Insufficient Data", tint = AmberCaution)
                            Text(
                                text = rec?.insufficientDataReason
                                    ?: "Insufficient measurement data to generate a reliable recommendation.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Button(
                            onClick = onNavigateToAnalyzer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = Color(0xFF001F2E)
                            )
                        ) {
                            Text(strings.navAnalyzer, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            val current = rec.currentConfig!!
            val best = rec.recommendedConfig!!

            // CURRENT SETTINGS vs AI RECOMMENDED SETTINGS
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ConfigSummaryCard(
                        header = strings.currentSettingsHeader,
                        freqMhz = current.frequencyMhz,
                        voltMv = current.voltageMv,
                        hashrateThs = current.avgHashrateThs,
                        powerW = current.avgPowerWatts,
                        effJth = current.efficiencyJth,
                        tempC = current.avgAsicTempC,
                        errPct = current.avgAsicErrorPct,
                        provenanceLabel = strings.tagMeasured,
                        provenance = DataProvenance.MEASURED,
                        accentColor = ElectricCyan,
                        strings = strings,
                        modifier = Modifier.weight(1f)
                    )

                    ConfigSummaryCard(
                        header = strings.recommendedSettingsHeader,
                        freqMhz = best.frequencyMhz,
                        voltMv = best.voltageMv,
                        hashrateThs = best.avgHashrateThs,
                        powerW = best.avgPowerWatts,
                        effJth = best.efficiencyJth,
                        tempC = best.avgAsicTempC,
                        errPct = best.avgAsicErrorPct,
                        provenanceLabel = strings.tagMeasured,
                        provenance = DataProvenance.MEASURED,
                        accentColor = NeonEmerald,
                        strings = strings,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Confidence & Score Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.45f))
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
                            Text(strings.confidenceLevel, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${rec.confidencePercent ?: 0}% • ${rec.confidenceLabel}",
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = NeonEmerald
                            )
                        }
                        rec.compositeScore?.let { score ->
                            Text(
                                text = "${strings.profileName(rec.profile)} Score: ${"%.1f".format(score)} / 100",
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                        }
                        rec.explanationPoints.forEach { point ->
                            Text(
                                text = "• $point",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Dedicated Recommendation Comparison Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.TrendingUp, contentDescription = strings.comparisonTitle, tint = ElectricCyan)
                            Text(strings.comparisonTitle, style = MaterialTheme.typography.titleMedium)
                        }

                        val hashDiff = best.avgHashrateThs - current.avgHashrateThs
                        val powerDiff = best.avgPowerWatts - current.avgPowerWatts
                        val effDiff = best.efficiencyJth - current.efficiencyJth
                        val tempDiff = if (best.avgAsicTempC != null && current.avgAsicTempC != null) {
                            best.avgAsicTempC - current.avgAsicTempC
                        } else null
                        val errDiff = if (best.avgAsicErrorPct != null && current.avgAsicErrorPct != null) {
                            best.avgAsicErrorPct - current.avgAsicErrorPct
                        } else null
                        val stabDiff = best.stabilityScore - current.stabilityScore

                        ComparisonDiffRow("Hashrate Diff", "${formatDiff(hashDiff, 3)} TH/s", hashDiff >= 0)
                        ComparisonDiffRow("Power Diff", "${formatDiff(powerDiff, 2)} W", powerDiff <= 0)
                        ComparisonDiffRow("Efficiency Diff", "${formatDiff(effDiff, 2)} J/TH", effDiff <= 0)
                        ComparisonDiffRow(
                            "Temp Diff",
                            tempDiff?.let { "${formatDiff(it, 1)} °C" } ?: strings.notAvailable,
                            (tempDiff ?: 0.0) <= 0
                        )
                        ComparisonDiffRow(
                            "ASIC Error Diff",
                            errDiff?.let { "${formatDiff(it, 2)} %" } ?: strings.notAvailable,
                            (errDiff ?: 0.0) <= 0
                        )
                        ComparisonDiffRow("Stability Score", "${formatDiff(stabDiff, 1)} pts", stabDiff >= 0)

                        if (rec.advantages.isNotEmpty()) {
                            Text(strings.advantagesTitle, style = MaterialTheme.typography.labelLarge, color = NeonEmerald)
                            rec.advantages.forEach { adv ->
                                Text("+ $adv", style = MaterialTheme.typography.bodySmall, color = NeonEmerald)
                            }
                        }
                        if (rec.disadvantages.isNotEmpty()) {
                            Text(strings.disadvantagesTitle, style = MaterialTheme.typography.labelLarge, color = AmberCaution)
                            rec.disadvantages.forEach { dis ->
                                Text("− $dis", style = MaterialTheme.typography.bodySmall, color = AmberCaution)
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
private fun ConfigSummaryCard(
    header: String,
    freqMhz: Int,
    voltMv: Int,
    hashrateThs: Double,
    powerW: Double,
    effJth: Double,
    tempC: Double?,
    errPct: Double?,
    provenanceLabel: String,
    provenance: DataProvenance,
    accentColor: Color,
    strings: AppStrings,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(text = header, style = MaterialTheme.typography.labelSmall, color = accentColor, fontWeight = FontWeight.Bold)
            ProvenanceBadge(provenance = provenance, label = provenanceLabel)
            Text(
                text = "$freqMhz MHz",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$voltMv mV",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                color = accentColor
            )
            Text("Hash: ${"%.3f".format(hashrateThs)} TH/s", style = MaterialTheme.typography.bodySmall)
            Text("Power: ${"%.2f".format(powerW)} W", style = MaterialTheme.typography.bodySmall)
            Text("Eff: ${"%.2f".format(effJth)} J/TH", style = MaterialTheme.typography.bodySmall, color = NeonEmerald)
            Text("Temp: ${tempC?.let { "${"%.1f".format(it)}°C" } ?: strings.notAvailable}", style = MaterialTheme.typography.bodySmall)
            Text("Err: ${errPct?.let { "${"%.2f".format(it)}%" } ?: strings.notAvailable}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ComparisonDiffRow(label: String, value: String, favorable: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            color = if (favorable) NeonEmerald else AmberCaution
        )
    }
}

private fun formatDiff(value: Double, decimals: Int): String {
    val prefix = if (value >= 0) "+" else ""
    return "$prefix%.${decimals}f".format(value)
}
