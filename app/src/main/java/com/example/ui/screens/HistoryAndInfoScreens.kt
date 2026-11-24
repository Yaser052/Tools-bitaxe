package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AnalysisProfile
import com.example.domain.model.AppLanguage
import com.example.domain.model.EndpointStatus
import com.example.ui.BitaxeUiState
import com.example.ui.components.RecommendationOnlyBanner
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ThermalRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    state: BitaxeUiState,
    strings: AppStrings,
    onToggleCompare: (Long) -> Unit
) {
    val experiments = state.experiments
    val bestStableId = state.recommendation?.recommendedConfig?.id
        ?: experiments.filter { it.isStable }.minByOrNull { it.efficiencyJth }?.id
    val selectedItems = experiments.filter { it.id in state.selectedComparisonIds }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

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
                Icon(Icons.Default.History, contentDescription = strings.historyTitle, tint = ElectricCyan)
                Text(strings.historyTitle, style = MaterialTheme.typography.titleLarge)
            }
        }

        if (selectedItems.size >= 2) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    border = BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.CompareArrows, contentDescription = strings.compareSelectedTitle, tint = NeonEmerald)
                            Text(strings.compareSelectedTitle, style = MaterialTheme.typography.titleSmall, color = NeonEmerald)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            selectedItems.forEach { rec ->
                                Surface(
                                    modifier = Modifier.width(175.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "${rec.frequencyMhz} MHz / ${rec.voltageMv} mV",
                                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                            color = ElectricCyan
                                        )
                                        Text("Avg: ${"%.3f".format(rec.avgHashrateThs)} TH/s", style = MaterialTheme.typography.bodySmall)
                                        Text("Power: ${"%.2f".format(rec.avgPowerWatts)} W", style = MaterialTheme.typography.bodySmall)
                                        Text("Eff: ${"%.2f".format(rec.efficiencyJth)} J/TH", style = MaterialTheme.typography.bodySmall, color = NeonEmerald)
                                        Text("ASIC Temp: ${rec.avgAsicTempC?.let { "${"%.1f".format(it)}°C" } ?: strings.notAvailable}", style = MaterialTheme.typography.bodySmall)
                                        Text("Errors: ${rec.avgAsicErrorPct?.let { "${"%.2f".format(it)}%" } ?: strings.notAvailable}", style = MaterialTheme.typography.bodySmall)
                                        Text("Stable: ${if (rec.isStable) "YES" else "NO"} (${rec.stabilityScore.toInt()}/100)", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (experiments.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = strings.historyEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(experiments, key = { it.id }) { exp ->
                val isBest = exp.id == bestStableId
                val isChecked = exp.id in state.selectedComparisonIds
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(
                        width = if (isBest) 1.5.dp else 1.dp,
                        color = if (isBest) NeonEmerald else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${exp.frequencyMhz} MHz • ${exp.voltageMv} mV",
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = ElectricCyan
                                )
                                Text(
                                    text = "${dateFormat.format(Date(exp.timestampMillis))} • ${exp.durationSeconds}s (${exp.sampleCount} samples)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { onToggleCompare(exp.id) },
                                modifier = Modifier.testTag("compare_checkbox_${exp.id}")
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TagBadge(exp.operationMode, ElectricCyan)
                            TagBadge(
                                exp.experimentResult,
                                when {
                                    exp.isStable || exp.experimentResult == "APPLIED_VERIFIED" || exp.experimentResult == "ROLLED_BACK" -> NeonEmerald
                                    else -> ThermalRed
                                }
                            )
                            if (isBest) {
                                TagBadge(strings.bestStableBadge, NeonEmerald)
                            }
                            if (exp.isLastKnownStable) {
                                TagBadge(strings.lastStableBadge, ElectricCyan)
                            }
                        }

                        if (exp.rollbackTriggered) {
                            Text(
                                text = "Rollback to ${exp.rollbackTarget ?: "?"} • ${if (exp.rollbackConfirmed) "Confirmed" else "Unconfirmed"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberCaution
                            )
                        }

                        Text(
                            text = "Hash: ${"%.3f".format(exp.avgHashrateThs)} TH/s (${"%.2f".format(exp.minHashrateThs)}–${"%.2f".format(exp.maxHashrateThs)}) • Power: ${"%.2f".format(exp.avgPowerWatts)} W • Eff: ${"%.2f".format(exp.efficiencyJth)} J/TH",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "ASIC: ${exp.avgAsicTempC?.let { "${"%.1f".format(it)}°C" } ?: strings.notAvailable} • VR: ${exp.avgVrTempC?.let { "${"%.1f".format(it)}°C" } ?: strings.notAvailable} • Err: ${exp.avgAsicErrorPct?.let { "${"%.2f".format(it)}%" } ?: strings.notAvailable} • Shares +${exp.acceptedShares}/-${exp.rejectedShares}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${exp.deviceModel} (Board ${exp.boardVersion}) • FW ${exp.firmwareVersion} • ${exp.stabilityNotes}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun DeviceInfoScreen(
    state: BitaxeUiState,
    strings: AppStrings
) {
    val live = state.liveTelemetry

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
                Icon(Icons.Default.DeveloperBoard, contentDescription = strings.deviceInfoTitle, tint = ElectricCyan)
                Text(strings.deviceInfoTitle, style = MaterialTheme.typography.titleLarge)
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KeyValueRow("Target Hardware Profile", "Bitaxe Gamma 1370 • Board 601 • BM1370")
                    KeyValueRow("Detected Model", live?.deviceModel ?: strings.notAvailable)
                    KeyValueRow("Board Version", live?.boardVersion ?: strings.notAvailable)
                    KeyValueRow("ASIC Chip", live?.asicModel ?: strings.notAvailable)
                    KeyValueRow("ASIC Count / Small Cores", "${live?.asicCount ?: strings.notAvailable} / ${live?.smallCoreCount ?: strings.notAvailable}")
                    KeyValueRow("Local IP", live?.deviceIp?.ifEmpty { strings.notAvailable } ?: strings.notAvailable)
                    KeyValueRow("Hostname", live?.hostname ?: strings.notAvailable)
                    KeyValueRow("MAC Address", live?.macAddress ?: strings.notAvailable)
                    KeyValueRow("Wi-Fi SSID / RSSI", "${live?.ssid ?: strings.notAvailable} (${live?.wifiRssi?.let { "$it dBm" } ?: strings.notAvailable})")
                    KeyValueRow("Input Voltage / Current", "${live?.inputVoltageMv?.let { "${it.toInt()} mV" } ?: strings.notAvailable} / ${live?.currentMa?.let { "${it.toInt()} mA" } ?: strings.notAvailable}")
                    KeyValueRow("Stratum Pool", live?.stratumUrl?.let { "$it:${live.stratumPort ?: 3333}" } ?: strings.notAvailable)
                    KeyValueRow("Stratum Worker", live?.stratumUser ?: strings.notAvailable)
                    KeyValueRow("Pool Difficulty", live?.poolDifficulty?.toString() ?: strings.notAvailable)
                    KeyValueRow("Best Difficulty", live?.bestDiff ?: strings.notAvailable)
                    KeyValueRow("Fan Speed / Auto", "${live?.fanRpm?.let { "$it RPM" } ?: strings.notAvailable} (${if (live?.autoFanSpeed == true) "Auto" else "Manual"})")
                    KeyValueRow("Uptime", live?.uptimeSeconds?.let { formatDuration(it) } ?: strings.notAvailable)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun FirmwareInfoScreen(
    state: BitaxeUiState,
    strings: AppStrings
) {
    val live = state.liveTelemetry
    val caps = state.asicCapabilities

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
                Icon(Icons.Default.Memory, contentDescription = strings.firmwareInfoTitle, tint = NeonEmerald)
                Text(strings.firmwareInfoTitle, style = MaterialTheme.typography.titleLarge)
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KeyValueRow("ESP-Miner Version", live?.firmwareVersion ?: strings.notAvailable)
                    KeyValueRow("AxeOS Version", live?.axeOsVersion ?: strings.notAvailable)
                    KeyValueRow("ESP-IDF Version", live?.idfVersion ?: strings.notAvailable)
                    KeyValueRow("Default Freq / Volt", "${caps?.defaultFrequencyMhz ?: strings.notAvailable} MHz / ${caps?.defaultVoltageMv ?: strings.notAvailable} mV")
                    KeyValueRow("Abs Max Freq / Volt", "${caps?.absMaxFrequencyMhz ?: strings.notAvailable} MHz / ${caps?.absMaxVoltageMv ?: strings.notAvailable} mV")
                    if (!caps?.frequencyOptions.isNullOrEmpty()) {
                        KeyValueRow("FW Freq Options", caps!!.frequencyOptions.joinToString(", "))
                    }
                    if (!caps?.voltageOptions.isNullOrEmpty()) {
                        KeyValueRow("FW Volt Options", caps!!.voltageOptions.joinToString(", "))
                    }
                }
            }
        }

        item {
            Text(strings.endpointMatrixTitle, style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
        }

        items(state.endpointProbes, key = { it.endpoint }) { probe ->
            val color = when (probe.status) {
                EndpointStatus.SUPPORTED -> NeonEmerald
                EndpointStatus.UNSUPPORTED -> AmberCaution
                EndpointStatus.ERROR -> ThermalRed
                EndpointStatus.NOT_PROBED -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = probe.endpoint,
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        probe.detail?.let {
                            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TagBadge(probe.status.name, color)
                }
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Terminal, contentDescription = strings.firmwareLogsTitle, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                Text(strings.firmwareLogsTitle, style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
            }
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    if (state.firmwareLogs.isEmpty()) {
                        Text(
                            text = "No firmware log lines received yet (requires /api/system/logs or /api/ws/live).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.firmwareLogs.takeLast(25).forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun SettingsScreen(
    state: BitaxeUiState,
    strings: AppStrings,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetPollingInterval: (Int) -> Unit,
    onSetAutoReconnect: (Boolean) -> Unit,
    onSetDefaultProfile: (AnalysisProfile) -> Unit,
    onSaveSafetyThresholds: (Double, Double, Double, Long) -> Unit
) {
    val cfg = state.settings.safetyThresholds
    var maxAsicText by remember(cfg.maxAsicTempHardLimitC) { mutableStateOf(cfg.maxAsicTempHardLimitC.toInt().toString()) }
    var maxVrText by remember(cfg.maxVrTempHardLimitC) { mutableStateOf(cfg.maxVrTempHardLimitC.toInt().toString()) }
    var maxErrText by remember(cfg.maxAsicErrorPct) { mutableStateOf(cfg.maxAsicErrorPct.toString()) }
    var minObsText by remember(cfg.minObservationSeconds) { mutableStateOf(cfg.minObservationSeconds.toString()) }

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
                Icon(Icons.Default.Settings, contentDescription = strings.settingsTitle, tint = ElectricCyan)
                Text(strings.settingsTitle, style = MaterialTheme.typography.titleLarge)
            }
        }

        // Language Selection (English LTR / Arabic RTL)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(strings.languageLabel, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = state.settings.language == AppLanguage.EN,
                            onClick = { onSetLanguage(AppLanguage.EN) },
                            label = { Text("English (LTR)") },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("language_en_chip")
                        )
                        FilterChip(
                            selected = state.settings.language == AppLanguage.AR,
                            onClick = { onSetLanguage(AppLanguage.AR) },
                            label = { Text("العربية (RTL)") },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("language_ar_chip")
                        )
                    }
                }
            }
        }

        // Polling Interval (3-5 seconds) & Auto-reconnect
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(strings.pollingIntervalLabel, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "${state.settings.pollingIntervalSeconds}s",
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = NeonEmerald
                        )
                    }
                    Slider(
                        value = state.settings.pollingIntervalSeconds.toFloat(),
                        onValueChange = { onSetPollingInterval(it.toInt().coerceIn(3, 5)) },
                        valueRange = 3f..5f,
                        steps = 1
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.autoReconnectLabel, style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = state.settings.autoReconnect,
                            onCheckedChange = onSetAutoReconnect,
                            modifier = Modifier.testTag("auto_reconnect_switch")
                        )
                    }
                }
            }
        }

        // Default Analysis Profile
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(strings.defaultProfileLabel, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnalysisProfile.entries.forEach { profile ->
                            FilterChip(
                                selected = state.settings.defaultProfile == profile,
                                onClick = { onSetDefaultProfile(profile) },
                                label = { Text(profile.name) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            )
                        }
                    }
                }
            }
        }

        // Safety Gates Configuration
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(strings.safetyThresholdsTitle, style = MaterialTheme.typography.titleSmall, color = AmberCaution)
                    OutlinedTextField(
                        value = maxAsicText,
                        onValueChange = { maxAsicText = it },
                        label = { Text(strings.maxAsicTempLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = maxVrText,
                        onValueChange = { maxVrText = it },
                        label = { Text(strings.maxVrTempLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = maxErrText,
                        onValueChange = { maxErrText = it },
                        label = { Text(strings.maxErrorRateLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = minObsText,
                        onValueChange = { minObsText = it },
                        label = { Text(strings.minObservationLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onSaveSafetyThresholds(
                                maxAsicText.toDoubleOrNull() ?: cfg.maxAsicTempHardLimitC,
                                maxVrText.toDoubleOrNull() ?: cfg.maxVrTempHardLimitC,
                                maxErrText.toDoubleOrNull() ?: cfg.maxAsicErrorPct,
                                minObsText.toLongOrNull() ?: cfg.minObservationSeconds
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("save_safety_thresholds_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonEmerald,
                            contentColor = Color(0xFF002110)
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = strings.saveThresholds)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(strings.saveThresholds, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TagBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontWeight = FontWeight.Bold
        )
    }
}
