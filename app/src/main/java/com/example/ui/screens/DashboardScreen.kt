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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.domain.model.ConnectionState
import com.example.ui.BitaxeUiState
import com.example.ui.components.InteractiveTelemetryChart
import com.example.ui.components.RecommendationOnlyBanner
import com.example.ui.components.TelemetryChartMetric
import com.example.ui.components.TelemetryKpiCard
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ThermalRed

@Composable
fun DashboardScreen(
    state: BitaxeUiState,
    strings: AppStrings,
    onManualRefresh: () -> Unit,
    onNavigateToConnect: () -> Unit,
    onNavigateToMatrix: () -> Unit,
    onNavigateToEfficiency: () -> Unit
) {
    val live = state.liveTelemetry
    val thresholds = state.settings.safetyThresholds

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

        if (live == null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = strings.notConnectedPrompt,
                            tint = ElectricCyan,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = strings.notConnectedPrompt,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onNavigateToConnect,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("dashboard_go_connect_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonEmerald,
                                contentColor = Color(0xFF002110)
                            )
                        ) {
                            Text(strings.goToConnect, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Header with live status & quick tools
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${live.deviceModel ?: "Bitaxe Gamma"} • ${live.deviceIp}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${live.transportType} • ${live.latencyMs?.let { "${it}ms" } ?: "Live"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.connectionState == ConnectionState.CONNECTED) NeonEmerald else AmberCaution
                        )
                    }
                    IconButton(
                        onClick = onManualRefresh,
                        modifier = Modifier.testTag("dashboard_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = strings.manualRefresh, tint = ElectricCyan)
                    }
                }
            }

            // Thermal / Error Safety Warning Alert if triggered
            val warningList = buildList {
                if (live.overheatMode) add("OVERHEAT PROTECTION ACTIVE")
                if (!live.powerFault.isNullOrBlank()) add("POWER FAULT: ${live.powerFault}")
                live.asicTempC?.let {
                    if (it >= thresholds.maxAsicTempWarnC) add("High ASIC Temp: ${"%.1f".format(it)}°C")
                }
                live.vrTempC?.let {
                    if (it >= thresholds.maxVrTempWarnC) add("High VR Temp: ${"%.1f".format(it)}°C")
                }
                live.asicErrorPct?.let {
                    if (it > thresholds.maxAsicErrorPct) add("Abnormal ASIC Errors: ${"%.2f".format(it)}%")
                }
                if (state.disconnectCountSession > 2) add("Repeated disconnects (${state.disconnectCountSession})")
            }
            if (warningList.isNotEmpty()) {
                item {
                    Surface(
                        color = ThermalRed.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ThermalRed)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.WarningAmber, contentDescription = "Warning", tint = ThermalRed)
                            Text(
                                text = warningList.joinToString(" • "),
                                style = MaterialTheme.typography.labelMedium,
                                color = ThermalRed
                            )
                        }
                    }
                }
            }

            // 10 Primary Dashboard KPI Cards (2-column industrial grid)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Row 1: 1. LIVE HASHRATE | 2. POWER CONSUMPTION
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TelemetryKpiCard(
                            title = strings.liveHashrate,
                            value = live.currentHashrateThs?.let { "%.3f".format(it) } ?: strings.notAvailable,
                            unit = if (live.currentHashrateThs != null) "TH/s" else "",
                            subtitle = live.currentHashrateGhs?.let { "${"%.1f".format(it)} GH/s" },
                            icon = Icons.Default.Speed,
                            accentColor = NeonEmerald,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryKpiCard(
                            title = strings.powerConsumption,
                            value = live.powerWatts?.let { "%.2f".format(it) } ?: strings.notAvailable,
                            unit = if (live.powerWatts != null) "W" else "",
                            subtitle = live.currentMa?.let { "${"%.0f".format(it)} mA" },
                            icon = Icons.Default.Bolt,
                            accentColor = ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 2: 3. ENERGY EFFICIENCY | 4. ASIC TEMPERATURE
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TelemetryKpiCard(
                            title = strings.energyEfficiency,
                            value = live.efficiencyJth?.let { "%.2f".format(it) } ?: strings.notAvailable,
                            unit = if (live.efficiencyJth != null) "J/TH" else "",
                            subtitle = live.averageHashrateThs?.let { "Avg ${"%.2f".format(it)} TH/s" },
                            icon = Icons.Default.ElectricBolt,
                            accentColor = NeonEmerald,
                            modifier = Modifier.weight(1f)
                        )
                        val asicTempColor = when {
                            (live.asicTempC ?: 0.0) >= thresholds.maxAsicTempHardLimitC -> ThermalRed
                            (live.asicTempC ?: 0.0) >= thresholds.maxAsicTempWarnC -> AmberCaution
                            else -> NeonEmerald
                        }
                        TelemetryKpiCard(
                            title = strings.asicTemp,
                            value = live.asicTempC?.let { "%.1f".format(it) } ?: strings.notAvailable,
                            unit = if (live.asicTempC != null) "°C" else "",
                            subtitle = "Limit ${thresholds.maxAsicTempHardLimitC.toInt()}°C",
                            icon = Icons.Default.Thermostat,
                            accentColor = asicTempColor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 3: 5. VR TEMPERATURE | 6. CURRENT FREQUENCY
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val vrColor = when {
                            (live.vrTempC ?: 0.0) >= thresholds.maxVrTempHardLimitC -> ThermalRed
                            (live.vrTempC ?: 0.0) >= thresholds.maxVrTempWarnC -> AmberCaution
                            else -> ElectricCyan
                        }
                        TelemetryKpiCard(
                            title = strings.vrTemp,
                            value = live.vrTempC?.let { "%.1f".format(it) } ?: strings.notAvailable,
                            unit = if (live.vrTempC != null) "°C" else "",
                            subtitle = "Limit ${thresholds.maxVrTempHardLimitC.toInt()}°C",
                            icon = Icons.Default.Thermostat,
                            accentColor = vrColor,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryKpiCard(
                            title = strings.currentFrequency,
                            value = live.frequencyMhz?.toString() ?: strings.notAvailable,
                            unit = if (live.frequencyMhz != null) "MHz" else "",
                            subtitle = "5 MHz precision",
                            icon = Icons.Default.Memory,
                            accentColor = ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 4: 7. CURRENT VOLTAGE | 8. ASIC ERRORS
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val voltDisplay = live.coreVoltageTargetMv ?: live.coreVoltageActualMv
                        TelemetryKpiCard(
                            title = strings.currentVoltage,
                            value = voltDisplay?.toString() ?: strings.notAvailable,
                            unit = if (voltDisplay != null) "mV" else "",
                            subtitle = live.coreVoltageActualMv?.let { "Actual: $it mV" },
                            icon = Icons.Default.DeveloperBoard,
                            accentColor = ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                        val errColor = if ((live.asicErrorPct ?: 0.0) > thresholds.maxAsicErrorPct) ThermalRed else NeonEmerald
                        TelemetryKpiCard(
                            title = strings.asicErrors,
                            value = live.asicErrorPct?.let { "%.2f".format(it) } ?: strings.notAvailable,
                            unit = if (live.asicErrorPct != null) "%" else "",
                            subtitle = "Gate <= ${thresholds.maxAsicErrorPct}%",
                            icon = Icons.Default.BugReport,
                            accentColor = errColor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 5: 9. ACCEPTED SHARES | 10. REJECTED SHARES
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TelemetryKpiCard(
                            title = strings.acceptedShares,
                            value = live.acceptedShares?.toString() ?: strings.notAvailable,
                            unit = "",
                            subtitle = live.bestSessionDiff?.let { "Best: $it" },
                            icon = Icons.Default.CheckCircle,
                            accentColor = NeonEmerald,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryKpiCard(
                            title = strings.rejectedShares,
                            value = live.rejectedShares?.toString() ?: strings.notAvailable,
                            unit = "",
                            subtitle = "${strings.staleShares}: ${live.staleShares?.toString() ?: strings.notAvailable}",
                            icon = Icons.Default.ErrorOutline,
                            accentColor = if ((live.rejectedShares ?: 0L) > 0L) AmberCaution else ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Remaining Hardware Telemetry Strip (Fan RPM, Uptime, Pool, Firmware, Network Stability)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricRowItem(
                            icon = Icons.Default.Compress,
                            label = strings.fanRpm,
                            value = live.fanRpm?.let { "$it RPM (${live.fanSpeedPct?.toInt() ?: "?"}%)" } ?: strings.notAvailable
                        )
                        MetricRowItem(
                            icon = Icons.Default.Schedule,
                            label = strings.uptime,
                            value = live.uptimeSeconds?.let { formatDuration(it) } ?: strings.notAvailable
                        )
                        MetricRowItem(
                            icon = Icons.Default.CloudDone,
                            label = strings.poolStatus,
                            value = live.stratumUrl?.let { url ->
                                val port = live.stratumPort?.let { ":$it" } ?: ""
                                val fb = if (live.isFallbackStratum == true) " (Fallback)" else ""
                                "$url$port$fb"
                            } ?: strings.notAvailable
                        )
                        MetricRowItem(
                            icon = Icons.Default.Tune,
                            label = strings.firmwareVersion,
                            value = live.firmwareVersion ?: strings.notAvailable
                        )
                        MetricRowItem(
                            icon = Icons.Default.Wifi,
                            label = strings.networkStability,
                            value = buildString {
                                append(live.wifiStatus ?: "Online")
                                live.wifiRssi?.let { append(" • $it dBm") }
                                append(" • Drops: ${state.disconnectCountSession}")
                            }
                        )
                    }
                }
            }

            // Rolling Historical Averages (1m, 10m, 30m, 1h)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = strings.rollingAveragesTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RollingWindowChip(
                                label = strings.window1m,
                                value = state.rollingAverages.avg1mThs?.let { "${"%.3f".format(it)} TH/s" } ?: strings.notAvailable,
                                modifier = Modifier.weight(1f)
                            )
                            RollingWindowChip(
                                label = strings.window10m,
                                value = state.rollingAverages.avg10mThs?.let { "${"%.3f".format(it)} TH/s" } ?: strings.notAvailable,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RollingWindowChip(
                                label = strings.window30m,
                                value = state.rollingAverages.avg30mThs?.let { "${"%.3f".format(it)} TH/s" } ?: strings.notAvailable,
                                modifier = Modifier.weight(1f)
                            )
                            RollingWindowChip(
                                label = strings.window1h,
                                value = state.rollingAverages.avg1hThs?.let { "${"%.3f".format(it)} TH/s" } ?: strings.notAvailable,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Interactive 7-Metric Real-Time Chart
            item {
                val points = state.chartHistory
                val chartMap = mapOf(
                    TelemetryChartMetric.HASHRATE to points.mapNotNull { it.hashrateThs },
                    TelemetryChartMetric.POWER to points.mapNotNull { it.powerWatts },
                    TelemetryChartMetric.EFFICIENCY to points.mapNotNull { it.efficiencyJth },
                    TelemetryChartMetric.ASIC_TEMP to points.mapNotNull { it.asicTempC },
                    TelemetryChartMetric.FREQUENCY to points.mapNotNull { it.frequencyMhz?.toDouble() },
                    TelemetryChartMetric.VOLTAGE to points.mapNotNull { it.voltageMv?.toDouble() },
                    TelemetryChartMetric.ASIC_ERRORS to points.mapNotNull { it.asicErrorPct }
                )
                InteractiveTelemetryChart(
                    title = strings.chartsTitle,
                    valuesByMetric = chartMap,
                    emptyMessage = "Collecting live telemetry samples..."
                )
            }

            // Quick shortcuts to 225 Matrix and Efficiency Monitor
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToMatrix,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("open_matrix_button"),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.GridView, contentDescription = strings.navMatrix, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(strings.navMatrix, color = ElectricCyan)
                    }
                    OutlinedButton(
                        onClick = onNavigateToEfficiency,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("open_efficiency_button"),
                        border = BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.ElectricBolt, contentDescription = strings.navEfficiency, tint = NeonEmerald, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(strings.navEfficiency, color = NeonEmerald)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun MetricRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = label, tint = ElectricCyan, modifier = Modifier.size(16.dp))
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RollingWindowChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                color = NeonEmerald
            )
        }
    }
}

fun formatDuration(totalSeconds: Long): String {
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val mins = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return buildString {
        if (days > 0) append("${days}d ")
        if (hours > 0 || days > 0) append("${hours}h ")
        append("${mins}m ${secs}s")
    }
}
