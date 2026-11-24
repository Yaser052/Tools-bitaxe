package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.engine.BitaxeOptimizationEngine
import com.example.domain.engine.SafetyController
import com.example.domain.model.ConnectionState
import com.example.domain.model.ManualControlStatus
import com.example.ui.BitaxeUiState
import com.example.ui.components.RecommendationOnlyBanner
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ThermalRed

@Composable
fun ControlCenterScreen(
    state: BitaxeUiState,
    strings: AppStrings,
    onOpenManualMode: () -> Unit,
    onOpenAiAutoMode: () -> Unit,
    onOpenConnect: () -> Unit,
    onOpenDashboard: () -> Unit
) {
    val live = state.liveTelemetry
    val lastStable = SafetyController.selectRollbackTarget(
        history = state.experiments,
        baseline = state.aiAutoState.baselineRecord,
        capabilities = state.asicCapabilities
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            RecommendationOnlyBanner(text = strings.dualControlBanner)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = strings.controlCenterTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = NeonEmerald
                )
                Text(
                    text = strings.controlCenterSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Device Connection & Active Config Summary
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(
                    1.dp,
                    if (state.connectionState == ConnectionState.CONNECTED) NeonEmerald.copy(alpha = 0.5f)
                    else AmberCaution.copy(alpha = 0.5f)
                )
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
                            text = if (live != null) {
                                "${live.deviceModel ?: "Bitaxe Gamma"} • ${live.deviceIp}"
                            } else {
                                strings.statusDisconnected
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(onClick = onOpenConnect, modifier = Modifier.testTag("control_center_connect_btn")) {
                            Icon(Icons.Default.Link, contentDescription = strings.navConnect, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(strings.navConnect)
                        }
                    }

                    if (live != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Active: ${live.frequencyMhz ?: "?"} MHz / ${live.coreVoltageTargetMv ?: live.coreVoltageActualMv ?: "?"} mV",
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = ElectricCyan
                            )
                            Text(
                                text = "${live.currentHashrateThs?.let { "%.3f TH/s".format(it) } ?: strings.notAvailable} • ${live.asicTempC?.let { "%.1f°C".format(it) } ?: strings.notAvailable}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = NeonEmerald
                            )
                        }
                    }

                    lastStable?.let { (f, v) ->
                        Text(
                            text = "${strings.lastStableBadge}: $f MHz / $v mV",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Mode 1: MANUAL MODE Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenManualMode)
                    .testTag("mode_card_manual"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.5.dp, ElectricCyan.copy(alpha = 0.65f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = strings.manualModeCardTitle, tint = ElectricCyan, modifier = Modifier.size(28.dp))
                        Text(
                            text = strings.manualModeCardTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = ElectricCyan
                        )
                    }
                    Text(
                        text = strings.manualModeCardDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onOpenManualMode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("enter_manual_mode_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = Color(0xFF001F2E)
                        )
                    ) {
                        Text(strings.manualControlTitle, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Mode 2: AI AUTO MODE Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAiAutoMode)
                    .testTag("mode_card_ai_auto"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.5.dp, NeonEmerald.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = strings.aiAutoModeCardTitle, tint = NeonEmerald, modifier = Modifier.size(28.dp))
                        Text(
                            text = strings.aiAutoModeCardTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = NeonEmerald
                        )
                    }
                    Text(
                        text = strings.aiAutoModeCardDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onOpenAiAutoMode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("enter_ai_auto_mode_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonEmerald,
                            contentColor = Color(0xFF002110)
                        )
                    ) {
                        Text(strings.aiAutoTitle, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onOpenDashboard,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(strings.navDashboard, color = ElectricCyan)
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun ManualControlScreen(
    state: BitaxeUiState,
    strings: AppStrings,
    onFrequencyTextChange: (String) -> Unit,
    onVoltageTextChange: (String) -> Unit,
    onStepFrequency: (Int) -> Unit,
    onStepVoltage: (Int) -> Unit,
    onReadCurrent: () -> Unit,
    onRequestApply: () -> Unit,
    onVerify: () -> Unit,
    onRequestRestoreStable: () -> Unit,
    onConfirmApply: () -> Unit,
    onConfirmRestore: () -> Unit,
    onDismissDialogs: () -> Unit
) {
    val live = state.liveTelemetry
    val manual = state.manualControl
    val currentFreq = live?.frequencyMhz
    val currentVolt = live?.coreVoltageTargetMv ?: live?.coreVoltageActualMv

    if (manual.showApplyConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissDialogs,
            title = { Text(strings.confirmApplyTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.confirmApplyBody)
                    Text(
                        text = "Current: ${currentFreq ?: "?"} MHz / ${currentVolt ?: "?"} mV",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily)
                    )
                    Text(
                        text = "Proposed: ${manual.proposedFrequencyMhz} MHz / ${manual.proposedVoltageMv} mV",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = NeonEmerald
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmApply,
                    modifier = Modifier.testTag("confirm_manual_apply_dialog_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = Color(0xFF002110))
                ) {
                    Text(strings.confirmButton, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDialogs) { Text(strings.cancelButton) }
            }
        )
    }

    if (manual.showRestoreConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissDialogs,
            title = { Text(strings.restoreLastStable) },
            text = {
                Text(
                    "Restore last known stable configuration ${manual.proposedFrequencyMhz} MHz / ${manual.proposedVoltageMv} mV via PATCH /api/system and verify?"
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmRestore,
                    modifier = Modifier.testTag("confirm_restore_stable_dialog_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF001F2E))
                ) {
                    Text(strings.confirmButton, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDialogs) { Text(strings.cancelButton) }
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

        // Current vs Proposed Review Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(strings.manualControlTitle, style = MaterialTheme.typography.titleMedium, color = ElectricCyan)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(strings.currentDeviceSettings, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${currentFreq ?: strings.notAvailable} MHz",
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = ElectricCyan
                                )
                                Text(
                                    text = "${currentVolt ?: strings.notAvailable} mV",
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily)
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(strings.proposedSettings, style = MaterialTheme.typography.labelSmall, color = NeonEmerald)
                                Text(
                                    text = "${manual.proposedFrequencyMhz} MHz",
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = NeonEmerald
                                )
                                Text(
                                    text = "${manual.proposedVoltageMv} mV",
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily)
                                )
                            }
                        }
                    }

                    if (currentFreq != null && currentVolt != null) {
                        val df = manual.proposedFrequencyMhz - currentFreq
                        val dv = manual.proposedVoltageMv - currentVolt
                        Text(
                            text = "Delta review: ${if (df >= 0) "+" else ""}$df MHz  |  ${if (dv >= 0) "+" else ""}$dv mV",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = AmberCaution
                        )
                    }
                }
            }
        }

        // Precision 5 MHz Frequency & 5 mV Voltage Steppers
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PrecisionStepperInput(
                        label = "${strings.frequencyMhzLabel} (600–670, step 5)",
                        valueText = manual.frequencyText,
                        onValueTextChange = onFrequencyTextChange,
                        onDecrement = { onStepFrequency(-1) },
                        onIncrement = { onStepFrequency(1) },
                        inputTag = "manual_frequency_input",
                        minusTag = "manual_freq_minus",
                        plusTag = "manual_freq_plus"
                    )

                    PrecisionStepperInput(
                        label = "${strings.voltageMvLabel} (1100–1170, step 5)",
                        valueText = manual.voltageText,
                        onValueTextChange = onVoltageTextChange,
                        onDecrement = { onStepVoltage(-1) },
                        onIncrement = { onStepVoltage(1) },
                        inputTag = "manual_voltage_input",
                        minusTag = "manual_volt_minus",
                        plusTag = "manual_volt_plus"
                    )
                }
            }
        }

        // Status & Verification Readout
        manual.statusMessage?.let { msg ->
            item {
                val color = when (manual.status) {
                    ManualControlStatus.VERIFIED_SUCCESS -> NeonEmerald
                    ManualControlStatus.APPLYING, ManualControlStatus.VERIFYING, ManualControlStatus.VALIDATING -> ElectricCyan
                    ManualControlStatus.VERIFICATION_MISMATCH -> AmberCaution
                    ManualControlStatus.REJECTED_SAFETY, ManualControlStatus.FAILED -> ThermalRed
                    ManualControlStatus.IDLE -> ElectricCyan
                }
                Surface(
                    color = color.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (manual.status == ManualControlStatus.APPLYING || manual.status == ManualControlStatus.VERIFYING) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = color)
                        } else {
                            Icon(
                                imageVector = if (manual.status == ManualControlStatus.VERIFIED_SUCCESS) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                contentDescription = msg,
                                tint = color
                            )
                        }
                        Text(text = msg, style = MaterialTheme.typography.bodySmall, color = color)
                    }
                }
            }
        }

        // 4 Mandatory Action Buttons
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onReadCurrent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("read_current_settings_button"),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.7f))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = strings.readCurrentSettings, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(strings.readCurrentSettings, color = ElectricCyan)
                }

                Button(
                    onClick = onRequestApply,
                    enabled = manual.status != ManualControlStatus.APPLYING,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("apply_manual_settings_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonEmerald,
                        contentColor = Color(0xFF002110)
                    )
                ) {
                    Text(strings.applySettings, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onVerify,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("verify_settings_button"),
                    border = BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Verified, contentDescription = strings.verifySettings, tint = NeonEmerald, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(strings.verifySettings, color = NeonEmerald)
                }

                OutlinedButton(
                    onClick = onRequestRestoreStable,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("restore_last_stable_button"),
                    border = BorderStroke(1.dp, AmberCaution.copy(alpha = 0.7f))
                ) {
                    Icon(Icons.Default.Restore, contentDescription = strings.restoreLastStable, tint = AmberCaution, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(strings.restoreLastStable, color = AmberCaution)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun PrecisionStepperInput(
    label: String,
    valueText: String,
    onValueTextChange: (String) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    inputTag: String,
    minusTag: String,
    plusTag: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalIconButton(
                onClick = onDecrement,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(minusTag)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease by 5")
            }
            OutlinedTextField(
                value = valueText,
                onValueChange = onValueTextChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .testTag(inputTag)
            )
            FilledTonalIconButton(
                onClick = onIncrement,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(plusTag)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase by 5")
            }
        }
    }
}
