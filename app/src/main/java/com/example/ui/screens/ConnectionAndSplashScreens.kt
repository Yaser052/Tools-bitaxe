package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.ConnectionState
import com.example.ui.BitaxeUiState
import com.example.ui.components.RecommendationOnlyBanner
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ThermalRed

@Composable
fun SplashScreen(
    strings: AppStrings,
    onContinue: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.45f))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_bitaxe_hero),
                        contentDescription = "Bitaxe Gamma 1370 hardware art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(NeonEmerald)
                        )
                        Text(
                            text = "GAMMA 1370 • BOARD 601 • BM1370",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonEmerald
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = strings.appTitle,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = strings.appSubtitle,
                style = MaterialTheme.typography.titleMedium,
                color = ElectricCyan
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = strings.splashTagline,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))
            RecommendationOnlyBanner(text = strings.recommendationOnlyBanner)
            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("splash_enter_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonEmerald,
                    contentColor = Color(0xFF002110)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = strings.splashButton,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ConnectionScreen(
    state: BitaxeUiState,
    strings: AppStrings,
    onIpChange: (String) -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onScanNetwork: () -> Unit,
    onManualRefresh: () -> Unit
) {
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

        // Status Header Card
        item {
            val statusColor = when (state.connectionState) {
                ConnectionState.CONNECTED -> NeonEmerald
                ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> AmberCaution
                ConnectionState.ERROR -> ThermalRed
                ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val statusText = when (state.connectionState) {
                ConnectionState.CONNECTED -> strings.statusConnected
                ConnectionState.CONNECTING -> strings.statusConnecting
                ConnectionState.RECONNECTING -> strings.statusReconnecting
                ConnectionState.ERROR -> strings.statusError
                ConnectionState.DISCONNECTED -> strings.statusDisconnected
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.titleSmall,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (state.connectionState == ConnectionState.CONNECTED) {
                            IconButton(
                                onClick = onManualRefresh,
                                modifier = Modifier.testTag("manual_refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = strings.manualRefresh,
                                    tint = ElectricCyan
                                )
                            }
                        }
                    }

                    state.liveTelemetry?.let { live ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            InfoChip(
                                icon = Icons.Default.Memory,
                                label = "ASIC: ${live.asicModel ?: strings.notAvailable}",
                                color = if (live.asicModel?.contains("BM1370", true) == true) NeonEmerald else AmberCaution,
                                modifier = Modifier.weight(1f)
                            )
                            InfoChip(
                                icon = Icons.Default.DeveloperBoard,
                                label = "Board: ${live.boardVersion ?: strings.notAvailable}",
                                color = if (live.boardVersion?.contains("601", true) == true) NeonEmerald else ElectricCyan,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            InfoChip(
                                icon = Icons.Default.Router,
                                label = "FW: ${live.firmwareVersion ?: strings.notAvailable}",
                                color = ElectricCyan,
                                modifier = Modifier.weight(1f)
                            )
                            InfoChip(
                                icon = Icons.Default.WifiFind,
                                label = "${strings.latencyLabel}: ${live.latencyMs?.let { "${it}ms" } ?: strings.notAvailable}",
                                color = NeonEmerald,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Surface(
                            color = (if (live.isBitaxeGamma1370Verified) NeonEmerald else AmberCaution).copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (live.isBitaxeGamma1370Verified) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                    contentDescription = strings.deviceVerifiedBadge,
                                    tint = if (live.isBitaxeGamma1370Verified) NeonEmerald else AmberCaution,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (live.isBitaxeGamma1370Verified) strings.deviceVerifiedBadge else strings.deviceUnverifiedBadge,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (live.isBitaxeGamma1370Verified) NeonEmerald else AmberCaution
                                )
                            }
                        }
                    }

                    state.connectionError?.let { err ->
                        Surface(
                            color = ThermalRed.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ThermalRed.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = ThermalRed,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Manual IP Entry + Actions
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = strings.connectionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = state.ipInput,
                        onValueChange = onIpChange,
                        label = { Text(strings.ipAddressLabel) },
                        placeholder = { Text(strings.ipAddressHint) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { onConnect(state.ipInput) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ip_address_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onConnect(state.ipInput) },
                            enabled = state.connectionState != ConnectionState.CONNECTING,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("connect_device_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonEmerald,
                                contentColor = Color(0xFF002110)
                            )
                        ) {
                            if (state.connectionState == ConnectionState.CONNECTING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF002110)
                                )
                            } else {
                                Icon(Icons.Default.Link, contentDescription = strings.connectButton, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(strings.connectButton, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (state.connectionState == ConnectionState.CONNECTED ||
                            state.connectionState == ConnectionState.RECONNECTING
                        ) {
                            OutlinedButton(
                                onClick = onDisconnect,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("disconnect_device_button"),
                                border = BorderStroke(1.dp, ThermalRed)
                            ) {
                                Icon(Icons.Default.LinkOff, contentDescription = strings.disconnectButton, tint = ThermalRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(4.dp))
                                Text(strings.disconnectButton, color = ThermalRed)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onScanNetwork,
                        enabled = !state.isScanningLan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("scan_lan_button"),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f))
                    ) {
                        if (state.isScanningLan) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = ElectricCyan
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(strings.scanningNetwork, color = ElectricCyan)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = strings.scanNetworkButton, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(strings.scanNetworkButton, color = ElectricCyan)
                        }
                    }
                }
            }
        }

        // Discovered LAN Devices
        if (state.discoveredDevices.isNotEmpty()) {
            item {
                Text(
                    text = strings.discoveredDevicesTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = NeonEmerald
                )
            }
            items(state.discoveredDevices, key = { it.ip }) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onConnect(device.ip) }
                        .testTag("discovered_device_${device.ip}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    border = BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = device.ip,
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = NeonEmerald
                            )
                            Text(
                                text = "${device.deviceModel ?: "Bitaxe"} • ${device.asicModel ?: "?"} • Board ${device.boardVersion ?: "?"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${device.latencyMs}ms",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan
                        )
                    }
                }
            }
        }

        // Saved Local IPs
        item {
            Text(
                text = strings.savedDevicesTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(state.settings.savedIps, key = { it }) { savedIp ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onIpChange(savedIp)
                        onConnect(savedIp)
                    },
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = savedIp,
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = strings.connectButton,
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricCyan
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
