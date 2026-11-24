package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.model.ConnectionState
import com.example.ui.i18n.AppStrings
import com.example.ui.screens.AiAutoOptimizationScreen
import com.example.ui.screens.AnalyzerScreen
import com.example.ui.screens.ConnectionScreen
import com.example.ui.screens.ControlCenterScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DeviceInfoScreen
import com.example.ui.screens.EfficiencyMonitorScreen
import com.example.ui.screens.FirmwareInfoScreen
import com.example.ui.screens.FreqVoltMatrixScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.ManualControlScreen
import com.example.ui.screens.RecommendationScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ThermalRed

object BitaxeRoute {
    const val SPLASH = "splash"
    const val CONTROL_CENTER = "control_center"
    const val CONNECTION = "connection"
    const val DASHBOARD = "dashboard"
    const val MANUAL_CONTROL = "manual_control"
    const val AI_AUTO = "ai_auto"
    const val ANALYZER = "analyzer"
    const val RECOMMENDATIONS = "recommendations"
    const val MATRIX = "matrix"
    const val EFFICIENCY = "efficiency"
    const val HISTORY = "history"
    const val DEVICE_INFO = "device_info"
    const val FIRMWARE_INFO = "firmware_info"
    const val SETTINGS = "settings"
}

private data class PrimaryNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BitaxeAppShell(
    viewModel: BitaxeViewModel,
    state: BitaxeUiState,
    strings: AppStrings
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: BitaxeRoute.SPLASH

    val primaryTabs = listOf(
        PrimaryNavItem(BitaxeRoute.CONTROL_CENTER, strings.navControlCenter, Icons.Default.Tune, "nav_control_center"),
        PrimaryNavItem(BitaxeRoute.DASHBOARD, strings.navDashboard, Icons.Default.Dashboard, "nav_dashboard"),
        PrimaryNavItem(BitaxeRoute.MANUAL_CONTROL, strings.navManual, Icons.Default.Tune, "nav_manual"),
        PrimaryNavItem(BitaxeRoute.AI_AUTO, strings.navAiAuto, Icons.Default.AutoAwesome, "nav_ai_auto"),
        PrimaryNavItem(BitaxeRoute.HISTORY, strings.navHistory, Icons.Default.History, "nav_history")
    )

    val primaryRoutes = primaryTabs.map { it.route }.toSet()
    val showChrome = currentRoute != BitaxeRoute.SPLASH
    val showBackArrow = showChrome && currentRoute !in primaryRoutes

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(BitaxeRoute.CONTROL_CENTER) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateToTool(route: String) {
        navController.navigate(route) {
            launchSingleTop = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (showChrome) {
                    var menuExpanded by remember { mutableStateOf(false) }
                    val statusColor = when (state.connectionState) {
                        ConnectionState.CONNECTED -> NeonEmerald
                        ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> AmberCaution
                        ConnectionState.ERROR -> ThermalRed
                        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        navigationIcon = {
                            if (showBackArrow) {
                                IconButton(
                                    onClick = { navController.popBackStack() },
                                    modifier = Modifier.testTag("top_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = ElectricCyan
                                    )
                                }
                            }
                        },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Text(
                                    text = when (currentRoute) {
                                        BitaxeRoute.CONTROL_CENTER -> strings.controlCenterTitle
                                        BitaxeRoute.CONNECTION -> strings.connectionTitle
                                        BitaxeRoute.DASHBOARD -> strings.dashboardTitle
                                        BitaxeRoute.MANUAL_CONTROL -> strings.manualControlTitle
                                        BitaxeRoute.AI_AUTO -> strings.aiAutoTitle
                                        BitaxeRoute.ANALYZER -> strings.analyzerTitle
                                        BitaxeRoute.RECOMMENDATIONS -> strings.recommendScreenTitle
                                        BitaxeRoute.MATRIX -> strings.matrixTitle
                                        BitaxeRoute.EFFICIENCY -> strings.efficiencyTitle
                                        BitaxeRoute.HISTORY -> strings.historyTitle
                                        BitaxeRoute.DEVICE_INFO -> strings.deviceInfoTitle
                                        BitaxeRoute.FIRMWARE_INFO -> strings.firmwareInfoTitle
                                        BitaxeRoute.SETTINGS -> strings.settingsTitle
                                        else -> strings.appTitle
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { navigateToTool(BitaxeRoute.CONNECTION) },
                                modifier = Modifier.testTag("topbar_connect_button")
                            ) {
                                Icon(Icons.Default.Link, contentDescription = strings.navConnect, tint = statusColor)
                            }
                            IconButton(
                                onClick = { navigateToTool(BitaxeRoute.MATRIX) },
                                modifier = Modifier.testTag("topbar_matrix_button")
                            ) {
                                Icon(Icons.Default.GridView, contentDescription = strings.navMatrix, tint = ElectricCyan)
                            }
                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.testTag("topbar_more_button")
                                ) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More tools")
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(strings.navConnect) },
                                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                                        onClick = { menuExpanded = false; navigateToTool(BitaxeRoute.CONNECTION) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(strings.navAnalyzer) },
                                        leadingIcon = { Icon(Icons.Default.Science, contentDescription = null) },
                                        onClick = { menuExpanded = false; navigateToTool(BitaxeRoute.ANALYZER) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(strings.navRecommend) },
                                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                                        onClick = { menuExpanded = false; navigateToTool(BitaxeRoute.RECOMMENDATIONS) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(strings.navMatrix) },
                                        leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null) },
                                        onClick = { menuExpanded = false; navigateToTool(BitaxeRoute.MATRIX) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(strings.navEfficiency) },
                                        leadingIcon = { Icon(Icons.Default.ElectricBolt, contentDescription = null) },
                                        onClick = { menuExpanded = false; navigateToTool(BitaxeRoute.EFFICIENCY) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(strings.navDeviceInfo) },
                                        leadingIcon = { Icon(Icons.Default.DeveloperBoard, contentDescription = null) },
                                        onClick = { menuExpanded = false; navigateToTool(BitaxeRoute.DEVICE_INFO) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(strings.navFirmwareInfo) },
                                        leadingIcon = { Icon(Icons.Default.Memory, contentDescription = null) },
                                        onClick = { menuExpanded = false; navigateToTool(BitaxeRoute.FIRMWARE_INFO) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(strings.navSettings) },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        onClick = { menuExpanded = false; navigateToTool(BitaxeRoute.SETTINGS) }
                                    )
                                }
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (showChrome && !useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        primaryTabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateToTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NeonEmerald,
                                    selectedTextColor = NeonEmerald,
                                    indicatorColor = NeonEmerald.copy(alpha = 0.16f)
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showChrome && useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        primaryTabs.forEach { tab ->
                            NavigationRailItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateToTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = BitaxeRoute.SPLASH,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(BitaxeRoute.SPLASH) {
                        SplashScreen(
                            strings = strings,
                            onContinue = {
                                navController.navigate(BitaxeRoute.CONTROL_CENTER) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                    composable(BitaxeRoute.CONTROL_CENTER) {
                        ControlCenterScreen(
                            state = state,
                            strings = strings,
                            onOpenManualMode = { navigateToTab(BitaxeRoute.MANUAL_CONTROL) },
                            onOpenAiAutoMode = { navigateToTab(BitaxeRoute.AI_AUTO) },
                            onOpenConnect = { navigateToTool(BitaxeRoute.CONNECTION) },
                            onOpenDashboard = { navigateToTab(BitaxeRoute.DASHBOARD) }
                        )
                    }
                    composable(BitaxeRoute.CONNECTION) {
                        ConnectionScreen(
                            state = state,
                            strings = strings,
                            onIpChange = viewModel::updateIpInput,
                            onConnect = { ip -> viewModel.connect(ip) },
                            onDisconnect = viewModel::disconnect,
                            onScanNetwork = viewModel::scanLocalNetwork,
                            onManualRefresh = viewModel::manualRefresh
                        )
                    }
                    composable(BitaxeRoute.DASHBOARD) {
                        DashboardScreen(
                            state = state,
                            strings = strings,
                            onManualRefresh = viewModel::manualRefresh,
                            onNavigateToConnect = { navigateToTool(BitaxeRoute.CONNECTION) },
                            onNavigateToMatrix = { navigateToTool(BitaxeRoute.MATRIX) },
                            onNavigateToEfficiency = { navigateToTool(BitaxeRoute.EFFICIENCY) }
                        )
                    }
                    composable(BitaxeRoute.MANUAL_CONTROL) {
                        ManualControlScreen(
                            state = state,
                            strings = strings,
                            onFrequencyTextChange = viewModel::updateManualFrequencyText,
                            onVoltageTextChange = viewModel::updateManualVoltageText,
                            onStepFrequency = viewModel::stepManualFrequency,
                            onStepVoltage = viewModel::stepManualVoltage,
                            onReadCurrent = viewModel::readCurrentSettingsIntoManual,
                            onRequestApply = viewModel::requestApplyManualSettings,
                            onVerify = viewModel::verifyManualSettingsOnDevice,
                            onRequestRestoreStable = viewModel::requestRestoreLastStable,
                            onConfirmApply = viewModel::confirmApplyManualSettings,
                            onConfirmRestore = viewModel::confirmRestoreLastStable,
                            onDismissDialogs = viewModel::dismissManualDialogs
                        )
                    }
                    composable(BitaxeRoute.AI_AUTO) {
                        AiAutoOptimizationScreen(
                            state = state,
                            strings = strings,
                            onUpdateRange = viewModel::updateExplorationRange,
                            onStepRange = viewModel::stepRangeValue,
                            onSelectProfile = viewModel::selectProfile,
                            onRequestStart = viewModel::requestStartAiAutoOptimization,
                            onConfirmStart = viewModel::confirmAndStartAiAutoOptimization,
                            onDismissAuth = viewModel::dismissAiAuthDialog,
                            onPause = viewModel::pauseAiAutoOptimization,
                            onResume = viewModel::resumeAiAutoOptimization,
                            onStop = viewModel::stopAiAutoOptimization,
                            onRestoreLastStable = viewModel::restoreLastStableFromAiAuto
                        )
                    }
                    composable(BitaxeRoute.ANALYZER) {
                        AnalyzerScreen(
                            state = state,
                            strings = strings,
                            onStartRecording = viewModel::startRecordingSession,
                            onStopAndSave = viewModel::stopAndSaveRecordingSession,
                            onNavigateToConnect = { navigateToTool(BitaxeRoute.CONNECTION) },
                            onNavigateToMatrix = { navigateToTool(BitaxeRoute.MATRIX) }
                        )
                    }
                    composable(BitaxeRoute.RECOMMENDATIONS) {
                        RecommendationScreen(
                            state = state,
                            strings = strings,
                            onSelectProfile = viewModel::selectProfile,
                            onNavigateToAnalyzer = { navigateToTool(BitaxeRoute.ANALYZER) }
                        )
                    }
                    composable(BitaxeRoute.MATRIX) {
                        FreqVoltMatrixScreen(
                            state = state,
                            strings = strings,
                            onSelectCell = viewModel::selectMatrixCell
                        )
                    }
                    composable(BitaxeRoute.EFFICIENCY) {
                        EfficiencyMonitorScreen(
                            state = state,
                            strings = strings
                        )
                    }
                    composable(BitaxeRoute.HISTORY) {
                        HistoryScreen(
                            state = state,
                            strings = strings,
                            onToggleCompare = viewModel::toggleComparisonExperiment
                        )
                    }
                    composable(BitaxeRoute.DEVICE_INFO) {
                        DeviceInfoScreen(
                            state = state,
                            strings = strings
                        )
                    }
                    composable(BitaxeRoute.FIRMWARE_INFO) {
                        FirmwareInfoScreen(
                            state = state,
                            strings = strings
                        )
                    }
                    composable(BitaxeRoute.SETTINGS) {
                        SettingsScreen(
                            state = state,
                            strings = strings,
                            onSetLanguage = viewModel::setLanguage,
                            onSetPollingInterval = viewModel::setPollingInterval,
                            onSetAutoReconnect = viewModel::setAutoReconnect,
                            onSetDefaultProfile = viewModel::setDefaultProfile,
                            onSaveSafetyThresholds = viewModel::updateSafetyThresholds
                        )
                    }
                }
            }
        }
    }
}
