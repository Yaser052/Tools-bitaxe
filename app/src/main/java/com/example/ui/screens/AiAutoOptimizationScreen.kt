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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AiAutoPhase
import com.example.domain.model.AnalysisProfile
import com.example.ui.BitaxeUiState
import com.example.ui.components.RecommendationOnlyBanner
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ThermalRed

@Composable
fun AiAutoOptimizationScreen(
    state: BitaxeUiState,
    strings: AppStrings,
    onUpdateRange: (Int?, Int?, Int?, Int?, Int?, Long?) -> Unit,
    onStepRange: (String, Int) -> Unit,
    onSelectProfile: (AnalysisProfile) -> Unit,
    onRequestStart: () -> Unit,
    onConfirmStart: () -> Unit,
    onDismissAuth: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRestoreLastStable: () -> Unit
) {
    val range = state.explorationRange
    val validation = state.rangeValidation
    val auto = state.aiAutoState
    val live = state.liveTelemetry

    if (state.showAiAuthDialog) {
        AlertDialog(
            onDismissRequest = onDismissAuth,
            icon = { Icon(Icons.Default.Shield, contentDescription = strings.authDialogTitle, tint = AmberCaution) },
            title = { Text(strings.authDialogTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(strings.authDialogBody, style = MaterialTheme.typography.bodySmall)
                    Text("• Freq Range: ${range.minFrequencyMhz}–${range.maxFrequencyMhz} MHz (step 5)", style = MaterialTheme.typography.labelSmall)
                    Text("• Voltage Range: ${range.minVoltageMv}–${range.maxVoltageMv} mV (step 5)", style = MaterialTheme.typography.labelSmall)
                    Text("• Profile: ${strings.profileName(state.activeProfile)}", style = MaterialTheme.typography.labelSmall)
                    Text("• Observation: ${range.observationSeconds}s per candidate", style = MaterialTheme.typography.labelSmall)
                    Text("• Max Experiments: ${range.maxExperimentsBudget}", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "• Safety & Rollback: Automatically stops & restores last verified stable config if ASIC >= ${state.settings.safetyThresholds.maxAsicTempHardLimitC}°C, VR >= ${state.settings.safetyThresholds.maxVrTempHardLimitC}°C, errors > ${state.settings.safetyThresholds.maxAsicErrorPct}%, restart, or collapse.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberCaution
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmStart,
                    modifier = Modifier.testTag("confirm_ai_auto_auth_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = Color(0xFF002110))
                ) {
                    Text(strings.authorizeAndStart, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissAuth) { Text(strings.cancelButton) }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            RecommendationOnlyBanner(text = strings.dualControlBanner)
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = strings.aiAutoTitle, tint = NeonEmerald)
                Text(strings.aiAutoTitle, style = MaterialTheme.typography.titleLarge)
            }
        }

        // Four Independent Exploration Range Inputs (with 5-unit steppers)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PrecisionStepperInput(
                        label = strings.minFrequencyLabel,
                        valueText = range.minFrequencyMhz.toString(),
                        onValueTextChange = { it.toIntOrNull()?.let { v -> onUpdateRange(v, null, null, null, null, null) } },
                        onDecrement = { onStepRange("minF", -1) },
                        onIncrement = { onStepRange("minF", 1) },
                        inputTag = "auto_min_freq_input",
                        minusTag = "auto_min_freq_minus",
                        plusTag = "auto_min_freq_plus"
                    )
                    PrecisionStepperInput(
                        label = strings.maxFrequencyLabel,
                        valueText = range.maxFrequencyMhz.toString(),
                        onValueTextChange = { it.toIntOrNull()?.let { v -> onUpdateRange(null, v, null, null, null, null) } },
                        onDecrement = { onStepRange("maxF", -1) },
                        onIncrement = { onStepRange("maxF", 1) },
                        inputTag = "auto_max_freq_input",
                        minusTag = "auto_max_freq_minus",
                        plusTag = "auto_max_freq_plus"
                    )
                    PrecisionStepperInput(
                        label = strings.minVoltageLabel,
                        valueText = range.minVoltageMv.toString(),
                        onValueTextChange = { it.toIntOrNull()?.let { v -> onUpdateRange(null, null, v, null, null, null) } },
                        onDecrement = { onStepRange("minV", -1) },
                        onIncrement = { onStepRange("minV", 1) },
                        inputTag = "auto_min_volt_input",
                        minusTag = "auto_min_volt_minus",
                        plusTag = "auto_min_volt_plus"
                    )
                    PrecisionStepperInput(
                        label = strings.maxVoltageLabel,
                        valueText = range.maxVoltageMv.toString(),
                        onValueTextChange = { it.toIntOrNull()?.let { v -> onUpdateRange(null, null, null, v, null, null) } },
                        onDecrement = { onStepRange("maxV", -1) },
                        onIncrement = { onStepRange("maxV", 1) },
                        inputTag = "auto_max_volt_input",
                        minusTag = "auto_max_volt_minus",
                        plusTag = "auto_max_volt_plus"
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = range.maxExperimentsBudget.toString(),
                            onValueChange = { it.toIntOrNull()?.let { b -> onUpdateRange(null, null, null, null, b, null) } },
                            label = { Text(strings.maxExperimentsLabel) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = range.observationSeconds.toString(),
                            onValueChange = { it.toLongOrNull()?.let { s -> onUpdateRange(null, null, null, null, null, s) } },
                            label = { Text(strings.observationWindowLabel) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (validation.isValid) {
                        Text(
                            text = "Valid exploration space: ${validation.frequencyCount} freq × ${validation.voltageCount} volt = ${validation.totalCombinations} combinations (5-unit steps)",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonEmerald
                        )
                    } else {
                        validation.errors.forEach { err ->
                            Text(text = "• $err", style = MaterialTheme.typography.bodySmall, color = ThermalRed)
                        }
                    }
                }
            }
        }

        // Optimization Profile Selector (ECO / BALANCED default / BEAST)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalysisProfile.entries.forEach { profile ->
                    FilterChip(
                        selected = state.activeProfile == profile,
                        onClick = { if (!auto.isRunning) onSelectProfile(profile) },
                        label = { Text(profile.name) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("auto_profile_${profile.name.lowercase()}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonEmerald.copy(alpha = 0.2f),
                            selectedLabelColor = NeonEmerald
                        )
                    )
                }
            }
        }

        // Controls: START / PAUSE / RESUME / STOP / RESTORE LAST STABLE
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!auto.isRunning) {
                    Button(
                        onClick = onRequestStart,
                        enabled = validation.isValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("start_ai_optimization_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonEmerald,
                            contentColor = Color(0xFF002110)
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = strings.startAiOptimization)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(strings.startAiOptimization, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!auto.isPaused) {
                            OutlinedButton(
                                onClick = onPause,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("pause_ai_optimization_button"),
                                border = BorderStroke(1.dp, AmberCaution)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = strings.pauseOptimization, tint = AmberCaution)
                                Spacer(modifier = Modifier.size(4.dp))
                                Text(strings.pauseOptimization, color = AmberCaution)
                            }
                        } else {
                            Button(
                                onClick = onResume,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("resume_ai_optimization_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = Color(0xFF002110))
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = strings.resumeOptimization)
                                Spacer(modifier = Modifier.size(4.dp))
                                Text(strings.resumeOptimization)
                            }
                        }

                        Button(
                            onClick = onStop,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("stop_ai_optimization_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = ThermalRed, contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = strings.stopOptimization)
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(strings.stopOptimization, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedButton(
                    onClick = onRestoreLastStable,
                    enabled = !auto.isRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("auto_restore_last_stable_button"),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.7f))
                ) {
                    Icon(Icons.Default.Restore, contentDescription = strings.restoreLastStable, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(strings.restoreLastStable, color = ElectricCyan)
                }
            }
        }

        // Real-Time Experiment Monitoring Panel
        if (auto.phase != AiAutoPhase.IDLE || auto.statusBanner != null) {
            item {
                val bannerColor = when (auto.phase) {
                    AiAutoPhase.SAFETY_ABORTED -> ThermalRed
                    AiAutoPhase.ROLLING_BACK, AiAutoPhase.PAUSED -> AmberCaution
                    AiAutoPhase.COMPLETED -> NeonEmerald
                    else -> ElectricCyan
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${strings.experimentProgressLabel}: ${auto.currentExperimentNumber} / ${auto.totalPlannedExperiments}",
                                style = MaterialTheme.typography.titleSmall,
                                color = bannerColor
                            )
                            Text(
                                text = auto.phase.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = bannerColor
                            )
                        }

                        LinearProgressIndicator(
                            progress = {
                                if (auto.totalPlannedExperiments > 0) {
                                    ((auto.currentExperimentNumber - 1 + auto.phaseProgress) / auto.totalPlannedExperiments.toFloat()).coerceIn(0f, 1f)
                                } else auto.phaseProgress
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = bannerColor
                        )

                        auto.statusBanner?.let {
                            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }

                        auto.rollbackNotice?.let { notice ->
                            Surface(
                                color = AmberCaution.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, AmberCaution)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.WarningAmber, contentDescription = notice, tint = AmberCaution, modifier = Modifier.size(16.dp))
                                    Text(text = notice, style = MaterialTheme.typography.bodySmall, color = AmberCaution)
                                }
                            }
                        }

                        live?.let { l ->
                            Text(
                                text = "Live: ${l.frequencyMhz ?: "?"} MHz / ${l.coreVoltageTargetMv ?: l.coreVoltageActualMv ?: "?"} mV • ${l.currentHashrateThs?.let { "%.3f TH/s".format(it) } ?: "N/A"} • ${l.powerWatts?.let { "%.1f W".format(it) } ?: "N/A"} • ${l.efficiencyJth?.let { "%.2f J/TH".format(it) } ?: "N/A"}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = NeonEmerald
                            )
                            Text(
                                text = "Temps: ASIC ${l.asicTempC?.let { "%.1f°C".format(it) } ?: "N/A"} / VR ${l.vrTempC?.let { "%.1f°C".format(it) } ?: "N/A"} • Err ${l.asicErrorPct?.let { "%.2f%%".format(it) } ?: "N/A"} • Shares +${l.acceptedShares ?: 0}/-${l.rejectedShares ?: 0}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        auto.bestStableRecord?.let { best ->
                            Text(
                                text = "${strings.bestStableBadge}: ${best.frequencyMhz} MHz / ${best.voltageMv} mV • ${"%.3f".format(best.avgHashrateThs)} TH/s • ${"%.2f".format(best.efficiencyJth)} J/TH",
                                style = MaterialTheme.typography.labelMedium,
                                color = NeonEmerald
                            )
                        }
                    }
                }
            }
        }

        // Live Experiment Execution Log
        if (auto.logs.isNotEmpty()) {
            item {
                Text("Live Experiment Log", style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
            }
            items(auto.logs, key = { "${it.index}_${it.timestampMillis}" }) { log ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (log.result == "STABLE_PASS") NeonEmerald.copy(alpha = 0.5f) else ThermalRed.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "#${log.index} • ${log.frequencyMhz} MHz / ${log.voltageMv} mV",
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = ElectricCyan
                            )
                            Text(
                                text = log.result,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (log.result == "STABLE_PASS") NeonEmerald else ThermalRed
                            )
                        }
                        Text(
                            text = "${log.avgHashrateThs?.let { "%.3f TH/s".format(it) } ?: "N/A"} • ${log.avgPowerWatts?.let { "%.1f W".format(it) } ?: "N/A"} • ${log.efficiencyJth?.let { "%.2f J/TH".format(it) } ?: "N/A"} • ASIC ${log.asicTempC?.let { "%.1f°C".format(it) } ?: "N/A"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(text = log.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
