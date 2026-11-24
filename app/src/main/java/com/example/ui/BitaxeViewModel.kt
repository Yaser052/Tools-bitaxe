package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppSettings
import com.example.data.local.BitaxeDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.network.AxeOsNetworkClient
import com.example.data.network.DiscoveredBitaxeDevice
import com.example.data.network.WebSocketEvent
import com.example.data.repository.BitaxeRepository
import com.example.domain.engine.BitaxeOptimizationEngine
import com.example.domain.engine.EfficiencyCalculator
import com.example.domain.engine.IpInvalidReason
import com.example.domain.engine.IpValidationResult
import com.example.domain.engine.NetworkValidator
import com.example.domain.engine.SafetyController
import com.example.domain.model.ActiveRecordingSession
import com.example.domain.model.AiAutoExperimentLogItem
import com.example.domain.model.AiAutoPhase
import com.example.domain.model.AiAutoSessionState
import com.example.domain.model.AnalysisProfile
import com.example.domain.model.AppLanguage
import com.example.domain.model.AsicCapabilities
import com.example.domain.model.ConnectionState
import com.example.domain.model.EfficiencyComparison
import com.example.domain.model.EndpointProbeResult
import com.example.domain.model.EndpointStatus
import com.example.domain.model.ExperimentRecord
import com.example.domain.model.ExplorationRangeConfig
import com.example.domain.model.GridCandidate
import com.example.domain.model.LiveTelemetry
import com.example.domain.model.ManualControlStatus
import com.example.domain.model.ManualControlUiState
import com.example.domain.model.OptimizationRecommendation
import com.example.domain.model.RangeValidationResult
import com.example.domain.model.RollingAverages
import com.example.domain.model.TelemetryChartPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class BitaxeUiState(
    val ipInput: String = "192.168.1.100",
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val connectionError: String? = null,
    val disconnectCountSession: Int = 0,
    val isScanningLan: Boolean = false,
    val discoveredDevices: List<DiscoveredBitaxeDevice> = emptyList(),
    val liveTelemetry: LiveTelemetry? = null,
    val asicCapabilities: AsicCapabilities? = null,
    val endpointProbes: List<EndpointProbeResult> = emptyList(),
    val firmwareLogs: List<String> = emptyList(),
    val chartHistory: List<TelemetryChartPoint> = emptyList(),
    val rollingAverages: RollingAverages = RollingAverages(),
    val activeProfile: AnalysisProfile = AnalysisProfile.BALANCED,
    val settings: AppSettings = AppSettings(),
    val experiments: List<ExperimentRecord> = emptyList(),
    val activeRecording: ActiveRecordingSession? = null,
    val recordingStatusMessage: String? = null,
    val precisionGrid: List<GridCandidate> = emptyList(),
    val recommendation: OptimizationRecommendation? = null,
    val efficiencyComparison: EfficiencyComparison? = null,
    val selectedComparisonIds: Set<Long> = emptySet(),
    val selectedMatrixCell: GridCandidate? = null,
    val manualControl: ManualControlUiState = ManualControlUiState(),
    val explorationRange: ExplorationRangeConfig = ExplorationRangeConfig(),
    val rangeValidation: RangeValidationResult = BitaxeOptimizationEngine.validateExplorationRange(
        ExplorationRangeConfig(),
        null
    ),
    val showAiAuthDialog: Boolean = false,
    val aiAutoState: AiAutoSessionState = AiAutoSessionState()
)

class BitaxeViewModel(application: Application) : AndroidViewModel(application) {

    private val database = BitaxeDatabase.getInstance(application)
    private val settingsStore = SettingsDataStore(application)
    private val networkClient = AxeOsNetworkClient(application)
    private val repository = BitaxeRepository(
        networkClient = networkClient,
        experimentDao = database.experimentDao(),
        settingsDataStore = settingsStore
    )

    private val _uiState = MutableStateFlow(BitaxeUiState())
    val uiState: StateFlow<BitaxeUiState> = _uiState.asStateFlow()

    private val rawSamplesBuffer = ArrayDeque<LiveTelemetry>(1200)
    private var pollingJob: Job? = null
    private var wsJob: Job? = null
    private var aiAutoJob: Job? = null
    private var activeHostIp: String? = null
    private var activeBaseUrl: String? = null
    private var initializedDefaultProfile = false

    init {
        viewModelScope.launch {
            combine(
                repository.settingsFlow,
                repository.allExperiments
            ) { settings, experiments ->
                settings to experiments
            }.collect { (settings, experiments) ->
                if (!initializedDefaultProfile) {
                    initializedDefaultProfile = true
                    _uiState.update {
                        it.copy(
                            ipInput = settings.lastConnectedIp,
                            activeProfile = settings.defaultProfile,
                            explorationRange = it.explorationRange.copy(
                                observationSeconds = settings.safetyThresholds.minObservationSeconds
                            )
                        )
                    }
                }
                _uiState.update { state ->
                    state.copy(
                        settings = settings,
                        experiments = experiments
                    )
                }
                recomputeAnalysisModels()
            }
        }
    }

    fun updateIpInput(value: String) {
        _uiState.update { it.copy(ipInput = value, connectionError = null) }
    }

    fun selectProfile(profile: AnalysisProfile) {
        _uiState.update { it.copy(activeProfile = profile) }
        recomputeAnalysisModels()
    }

    fun selectMatrixCell(candidate: GridCandidate?) {
        _uiState.update { it.copy(selectedMatrixCell = candidate) }
    }

    fun toggleComparisonExperiment(id: Long) {
        _uiState.update { state ->
            val next = if (id in state.selectedComparisonIds) {
                state.selectedComparisonIds - id
            } else {
                (state.selectedComparisonIds + id).take(4).toSet()
            }
            state.copy(selectedComparisonIds = next)
        }
    }

    fun scanLocalNetwork() {
        if (_uiState.value.isScanningLan) return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningLan = true) }
            val found = repository.discoverLocalDevices()
            _uiState.update { it.copy(isScanningLan = false, discoveredDevices = found) }
        }
    }

    fun connect(rawIp: String = _uiState.value.ipInput) {
        when (val validation = NetworkValidator.validateLocalMinerAddress(rawIp)) {
            is IpValidationResult.Invalid -> {
                val msg = when (validation.reasonKey) {
                    IpInvalidReason.EMPTY -> "Please enter a Bitaxe local IP address (e.g. 192.168.1.100)."
                    IpInvalidReason.MALFORMED -> "Invalid IPv4 or .local address format."
                    IpInvalidReason.PUBLIC_INTERNET_BLOCKED ->
                        "Public internet addresses are blocked. Only private LAN IPs (192.168.x.x, 10.x.x.x, 172.16-31.x.x) or .local hosts are allowed."
                }
                _uiState.update { it.copy(connectionState = ConnectionState.ERROR, connectionError = msg) }
                return
            }
            is IpValidationResult.Valid -> {
                activeHostIp = validation.normalizedHost
                activeBaseUrl = validation.baseUrl
                _uiState.update {
                    it.copy(
                        ipInput = validation.normalizedHost,
                        connectionState = ConnectionState.CONNECTING,
                        connectionError = null
                    )
                }
                performInitialConnect(validation.normalizedHost, validation.baseUrl)
            }
        }
    }

    private fun performInitialConnect(hostIp: String, baseUrl: String) {
        pollingJob?.cancel()
        wsJob?.cancel()

        viewModelScope.launch {
            val result = repository.connectAndProbe(hostIp, baseUrl)
            result.onSuccess { snapshot ->
                recordIncomingSample(snapshot.telemetry)
                val syncedFreq = snapshot.telemetry.frequencyMhz
                    ?.coerceIn(BitaxeOptimizationEngine.MIN_FREQUENCY_MHZ, BitaxeOptimizationEngine.MAX_FREQUENCY_MHZ)
                    ?: _uiState.value.manualControl.proposedFrequencyMhz
                val syncedVolt = (snapshot.telemetry.coreVoltageTargetMv ?: snapshot.telemetry.coreVoltageActualMv)
                    ?.coerceIn(BitaxeOptimizationEngine.MIN_VOLTAGE_MV, BitaxeOptimizationEngine.MAX_VOLTAGE_MV)
                    ?: _uiState.value.manualControl.proposedVoltageMv

                _uiState.update { state ->
                    state.copy(
                        connectionState = ConnectionState.CONNECTED,
                        connectionError = null,
                        liveTelemetry = snapshot.telemetry,
                        asicCapabilities = snapshot.asicCapabilities,
                        endpointProbes = snapshot.endpointProbes,
                        firmwareLogs = snapshot.firmwareLogs.takeLast(80),
                        manualControl = state.manualControl.copy(
                            proposedFrequencyMhz = syncedFreq,
                            proposedVoltageMv = syncedVolt,
                            frequencyText = syncedFreq.toString(),
                            voltageText = syncedVolt.toString()
                        ),
                        rangeValidation = BitaxeOptimizationEngine.validateExplorationRange(
                            state.explorationRange,
                            snapshot.asicCapabilities
                        )
                    )
                }
                recomputeAnalysisModels()
                startPollingLoop(hostIp, baseUrl)
                startWebSocketObserver(hostIp)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.ERROR,
                        connectionError = err.message ?: "Unable to connect to $hostIp"
                    )
                }
            }
        }
    }

    private fun startPollingLoop(hostIp: String, baseUrl: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            var consecutiveFailures = 0
            while (isActive) {
                val intervalSec = _uiState.value.settings.pollingIntervalSeconds.coerceIn(3, 5)
                delay(intervalSec * 1000L)
                val poll = repository.pollTelemetry(hostIp, baseUrl)
                poll.onSuccess { telemetry ->
                    consecutiveFailures = 0
                    val prevTransport = _uiState.value.liveTelemetry
                    val merged = if (prevTransport?.isWebSocketLive == true) {
                        telemetry.copy(isWebSocketLive = true)
                    } else {
                        telemetry
                    }
                    recordIncomingSample(merged)
                    _uiState.update { state ->
                        state.copy(
                            connectionState = ConnectionState.CONNECTED,
                            connectionError = null,
                            liveTelemetry = merged
                        )
                    }
                    recomputeAnalysisModels()
                }.onFailure { err ->
                    consecutiveFailures++
                    val autoReconnect = _uiState.value.settings.autoReconnect
                    val activeSession = _uiState.value.activeRecording
                    _uiState.update { state ->
                        state.copy(
                            disconnectCountSession = state.disconnectCountSession + 1,
                            activeRecording = activeSession?.copy(
                                disconnectCount = activeSession.disconnectCount + 1
                            ),
                            connectionState = if (autoReconnect) {
                                ConnectionState.RECONNECTING
                            } else {
                                ConnectionState.ERROR
                            },
                            connectionError = "Polling timeout ($consecutiveFailures): ${err.message ?: "No response"}"
                        )
                    }
                    if (!autoReconnect && consecutiveFailures >= 3) {
                        pollingJob?.cancel()
                    }
                }
            }
        }
    }

    private fun startWebSocketObserver(hostIp: String) {
        wsJob?.cancel()
        wsJob = viewModelScope.launch {
            repository.observeWebSocket(hostIp).collect { event ->
                when (event) {
                    is WebSocketEvent.Opened -> {
                        updateWsProbeStatus(EndpointStatus.SUPPORTED, "Live stream active")
                    }
                    is WebSocketEvent.TelemetryMessage -> {
                        recordIncomingSample(event.telemetry)
                        _uiState.update { state ->
                            state.copy(
                                liveTelemetry = event.telemetry,
                                connectionState = ConnectionState.CONNECTED
                            )
                        }
                        recomputeAnalysisModels()
                    }
                    is WebSocketEvent.LogLine -> {
                        _uiState.update { state ->
                            state.copy(firmwareLogs = (state.firmwareLogs + event.line).takeLast(120))
                        }
                    }
                    is WebSocketEvent.ClosedOrFailed -> {
                        updateWsProbeStatus(EndpointStatus.UNSUPPORTED, event.reason)
                    }
                }
            }
        }
    }

    private fun updateWsProbeStatus(status: EndpointStatus, detail: String) {
        _uiState.update { state ->
            val updatedProbes = state.endpointProbes.map { probe ->
                if (probe.endpoint.startsWith("WS")) {
                    probe.copy(status = status, detail = detail, lastCheckedMs = System.currentTimeMillis())
                } else {
                    probe
                }
            }
            state.copy(endpointProbes = updatedProbes)
        }
    }

    fun manualRefresh() {
        val ip = activeHostIp ?: return
        val url = activeBaseUrl ?: return
        viewModelScope.launch {
            repository.pollTelemetry(ip, url).onSuccess { telemetry ->
                recordIncomingSample(telemetry)
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.CONNECTED,
                        connectionError = null,
                        liveTelemetry = telemetry
                    )
                }
                recomputeAnalysisModels()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(connectionError = err.message ?: "Refresh failed")
                }
            }
        }
    }

    fun disconnect() {
        aiAutoJob?.cancel()
        pollingJob?.cancel()
        wsJob?.cancel()
        activeHostIp = null
        activeBaseUrl = null
        _uiState.update {
            it.copy(
                connectionState = ConnectionState.DISCONNECTED,
                connectionError = null,
                activeRecording = null,
                aiAutoState = it.aiAutoState.copy(isRunning = false, isPaused = false, phase = AiAutoPhase.IDLE)
            )
        }
    }

    // ==================== MANUAL MODE CONTROLS ====================

    fun updateManualFrequencyText(raw: String) {
        val parsed = raw.trim().toIntOrNull()
        _uiState.update { state ->
            state.copy(
                manualControl = state.manualControl.copy(
                    frequencyText = raw,
                    proposedFrequencyMhz = parsed ?: state.manualControl.proposedFrequencyMhz,
                    status = ManualControlStatus.IDLE,
                    statusMessage = null
                )
            )
        }
    }

    fun updateManualVoltageText(raw: String) {
        val parsed = raw.trim().toIntOrNull()
        _uiState.update { state ->
            state.copy(
                manualControl = state.manualControl.copy(
                    voltageText = raw,
                    proposedVoltageMv = parsed ?: state.manualControl.proposedVoltageMv,
                    status = ManualControlStatus.IDLE,
                    statusMessage = null
                )
            )
        }
    }

    fun stepManualFrequency(deltaSteps: Int) {
        _uiState.update { state ->
            val current = state.manualControl.frequencyText.toIntOrNull()
                ?: state.manualControl.proposedFrequencyMhz
            val snapped = snapToStep(
                current + deltaSteps * BitaxeOptimizationEngine.FREQUENCY_STEP_MHZ,
                BitaxeOptimizationEngine.MIN_FREQUENCY_MHZ,
                BitaxeOptimizationEngine.MAX_FREQUENCY_MHZ,
                BitaxeOptimizationEngine.FREQUENCY_STEP_MHZ
            )
            state.copy(
                manualControl = state.manualControl.copy(
                    proposedFrequencyMhz = snapped,
                    frequencyText = snapped.toString(),
                    status = ManualControlStatus.IDLE,
                    statusMessage = null
                )
            )
        }
    }

    fun stepManualVoltage(deltaSteps: Int) {
        _uiState.update { state ->
            val current = state.manualControl.voltageText.toIntOrNull()
                ?: state.manualControl.proposedVoltageMv
            val snapped = snapToStep(
                current + deltaSteps * BitaxeOptimizationEngine.VOLTAGE_STEP_MV,
                BitaxeOptimizationEngine.MIN_VOLTAGE_MV,
                BitaxeOptimizationEngine.MAX_VOLTAGE_MV,
                BitaxeOptimizationEngine.VOLTAGE_STEP_MV
            )
            state.copy(
                manualControl = state.manualControl.copy(
                    proposedVoltageMv = snapped,
                    voltageText = snapped.toString(),
                    status = ManualControlStatus.IDLE,
                    statusMessage = null
                )
            )
        }
    }

    fun readCurrentSettingsIntoManual() {
        val ip = activeHostIp
        val url = activeBaseUrl
        if (ip == null || url == null) {
            _uiState.update {
                it.copy(
                    manualControl = it.manualControl.copy(
                        status = ManualControlStatus.FAILED,
                        statusMessage = "Not connected to a Bitaxe device."
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(manualControl = it.manualControl.copy(status = ManualControlStatus.VALIDATING))
            }
            val res = repository.pollTelemetry(ip, url)
            res.onSuccess { live ->
                recordIncomingSample(live)
                val f = live.frequencyMhz ?: _uiState.value.manualControl.proposedFrequencyMhz
                val v = (live.coreVoltageTargetMv ?: live.coreVoltageActualMv)
                    ?: _uiState.value.manualControl.proposedVoltageMv
                _uiState.update { state ->
                    state.copy(
                        liveTelemetry = live,
                        manualControl = state.manualControl.copy(
                            proposedFrequencyMhz = f,
                            proposedVoltageMv = v,
                            frequencyText = f.toString(),
                            voltageText = v.toString(),
                            status = ManualControlStatus.IDLE,
                            statusMessage = "Read device settings: $f MHz / $v mV"
                        )
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        manualControl = it.manualControl.copy(
                            status = ManualControlStatus.FAILED,
                            statusMessage = "Failed to read settings: ${err.message}"
                        )
                    )
                }
            }
        }
    }

    fun requestApplyManualSettings() {
        val state = _uiState.value
        if (state.aiAutoState.isRunning) {
            _uiState.update {
                it.copy(
                    manualControl = it.manualControl.copy(
                        status = ManualControlStatus.REJECTED_SAFETY,
                        statusMessage = "Stop AI Auto Optimization before making manual changes."
                    )
                )
            }
            return
        }
        if (activeHostIp == null || activeBaseUrl == null) {
            _uiState.update {
                it.copy(
                    manualControl = it.manualControl.copy(
                        status = ManualControlStatus.FAILED,
                        statusMessage = "Connect to your Bitaxe Gamma 1370 before applying settings."
                    )
                )
            }
            return
        }
        val freq = state.manualControl.frequencyText.trim().toIntOrNull()
        val volt = state.manualControl.voltageText.trim().toIntOrNull()
        if (freq == null || volt == null) {
            _uiState.update {
                it.copy(
                    manualControl = it.manualControl.copy(
                        status = ManualControlStatus.REJECTED_SAFETY,
                        statusMessage = "Enter valid integer Frequency (MHz) and Voltage (mV)."
                    )
                )
            }
            return
        }
        val safety = SafetyController.validateTargetSetting(
            frequencyMhz = freq,
            voltageMv = volt,
            capabilities = state.asicCapabilities,
            liveTelemetry = state.liveTelemetry,
            thresholds = state.settings.safetyThresholds
        )
        if (!safety.isSafe) {
            _uiState.update {
                it.copy(
                    manualControl = it.manualControl.copy(
                        status = ManualControlStatus.REJECTED_SAFETY,
                        statusMessage = safety.violations.joinToString(" ") { v -> v.message }
                    )
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                manualControl = it.manualControl.copy(
                    proposedFrequencyMhz = freq,
                    proposedVoltageMv = volt,
                    showApplyConfirmation = true,
                    status = ManualControlStatus.IDLE,
                    statusMessage = null
                )
            )
        }
    }

    fun dismissManualDialogs() {
        _uiState.update {
            it.copy(
                manualControl = it.manualControl.copy(
                    showApplyConfirmation = false,
                    showRestoreConfirmation = false
                )
            )
        }
    }

    fun confirmApplyManualSettings() {
        val ip = activeHostIp ?: return
        val url = activeBaseUrl ?: return
        val targetFreq = _uiState.value.manualControl.proposedFrequencyMhz
        val targetVolt = _uiState.value.manualControl.proposedVoltageMv

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    manualControl = it.manualControl.copy(
                        showApplyConfirmation = false,
                        status = ManualControlStatus.APPLYING,
                        statusMessage = "Sending PATCH /api/system ($targetFreq MHz / $targetVolt mV)..."
                    )
                )
            }

            val result = repository.applyAndVerifyConfig(ip, url, targetFreq, targetVolt)
            result.telemetryAfterApply?.let { recordIncomingSample(it) }

            val status = when {
                result.verified -> ManualControlStatus.VERIFIED_SUCCESS
                result.httpSuccess -> ManualControlStatus.VERIFICATION_MISMATCH
                else -> ManualControlStatus.FAILED
            }

            val sampleForDb = result.telemetryAfterApply ?: _uiState.value.liveTelemetry
            if (sampleForDb != null) {
                val now = System.currentTimeMillis()
                val record = ExperimentRecord(
                    timestampMillis = now,
                    endTimestampMillis = now,
                    deviceIp = ip,
                    deviceIdentifier = sampleForDb.macAddress ?: sampleForDb.hostname ?: ip,
                    deviceModel = sampleForDb.deviceModel ?: "Bitaxe Gamma 1370",
                    boardVersion = sampleForDb.boardVersion ?: "601",
                    firmwareVersion = sampleForDb.firmwareVersion ?: "Unknown",
                    frequencyMhz = targetFreq,
                    voltageMv = targetVolt,
                    avgHashrateThs = sampleForDb.currentHashrateThs ?: 0.0,
                    minHashrateThs = sampleForDb.currentHashrateThs ?: 0.0,
                    maxHashrateThs = sampleForDb.currentHashrateThs ?: 0.0,
                    avgPowerWatts = sampleForDb.powerWatts ?: 0.0,
                    efficiencyJth = sampleForDb.efficiencyJth ?: 0.0,
                    avgAsicTempC = sampleForDb.asicTempC,
                    avgVrTempC = sampleForDb.vrTempC,
                    avgAsicErrorPct = sampleForDb.asicErrorPct,
                    acceptedShares = sampleForDb.acceptedShares ?: 0L,
                    rejectedShares = sampleForDb.rejectedShares ?: 0L,
                    staleShares = sampleForDb.staleShares,
                    sampleCount = 1,
                    disconnectCount = 0,
                    isStable = false,
                    stabilityScore = 0.0,
                    stabilityNotes = result.message,
                    durationSeconds = 1L,
                    isLastKnownStable = false,
                    operationMode = "MANUAL",
                    experimentResult = if (result.verified) "APPLIED_VERIFIED" else "VERIFY_FAILED"
                )
                repository.saveExperiment(record)
            }

            _uiState.update { state ->
                state.copy(
                    liveTelemetry = result.telemetryAfterApply ?: state.liveTelemetry,
                    manualControl = state.manualControl.copy(
                        status = status,
                        statusMessage = result.message,
                        lastVerification = result
                    )
                )
            }
        }
    }

    fun verifyManualSettingsOnDevice() {
        val ip = activeHostIp ?: return
        val url = activeBaseUrl ?: return
        val targetFreq = _uiState.value.manualControl.proposedFrequencyMhz
        val targetVolt = _uiState.value.manualControl.proposedVoltageMv

        viewModelScope.launch {
            _uiState.update {
                it.copy(manualControl = it.manualControl.copy(status = ManualControlStatus.VERIFYING))
            }
            val result = repository.verifyCurrentConfig(ip, url, targetFreq, targetVolt)
            result.telemetryAfterApply?.let { recordIncomingSample(it) }
            _uiState.update { state ->
                state.copy(
                    liveTelemetry = result.telemetryAfterApply ?: state.liveTelemetry,
                    manualControl = state.manualControl.copy(
                        status = if (result.verified) ManualControlStatus.VERIFIED_SUCCESS else ManualControlStatus.VERIFICATION_MISMATCH,
                        statusMessage = result.message,
                        lastVerification = result
                    )
                )
            }
        }
    }

    fun requestRestoreLastStable() {
        val state = _uiState.value
        val target = SafetyController.selectRollbackTarget(
            history = state.experiments,
            baseline = state.aiAutoState.baselineRecord,
            capabilities = state.asicCapabilities
        )
        if (target == null) {
            _uiState.update {
                it.copy(
                    manualControl = it.manualControl.copy(
                        status = ManualControlStatus.FAILED,
                        statusMessage = "No verified last known stable configuration found in history yet."
                    )
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                manualControl = it.manualControl.copy(
                    proposedFrequencyMhz = target.first,
                    proposedVoltageMv = target.second,
                    frequencyText = target.first.toString(),
                    voltageText = target.second.toString(),
                    showRestoreConfirmation = true
                )
            )
        }
    }

    fun confirmRestoreLastStable() {
        val ip = activeHostIp ?: return
        val url = activeBaseUrl ?: return
        val targetFreq = _uiState.value.manualControl.proposedFrequencyMhz
        val targetVolt = _uiState.value.manualControl.proposedVoltageMv

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    manualControl = it.manualControl.copy(
                        showRestoreConfirmation = false,
                        status = ManualControlStatus.APPLYING,
                        statusMessage = "Restoring last stable setting: $targetFreq MHz / $targetVolt mV..."
                    )
                )
            }
            val result = repository.applyAndVerifyConfig(ip, url, targetFreq, targetVolt)
            result.telemetryAfterApply?.let { recordIncomingSample(it) }

            val live = result.telemetryAfterApply ?: _uiState.value.liveTelemetry
            if (live != null) {
                val now = System.currentTimeMillis()
                repository.saveExperiment(
                    ExperimentRecord(
                        timestampMillis = now,
                        endTimestampMillis = now,
                        deviceIp = ip,
                        deviceIdentifier = live.macAddress ?: live.hostname ?: ip,
                        deviceModel = live.deviceModel ?: "Bitaxe Gamma 1370",
                        boardVersion = live.boardVersion ?: "601",
                        firmwareVersion = live.firmwareVersion ?: "Unknown",
                        frequencyMhz = targetFreq,
                        voltageMv = targetVolt,
                        avgHashrateThs = live.currentHashrateThs ?: 0.0,
                        minHashrateThs = live.currentHashrateThs ?: 0.0,
                        maxHashrateThs = live.currentHashrateThs ?: 0.0,
                        avgPowerWatts = live.powerWatts ?: 0.0,
                        efficiencyJth = live.efficiencyJth ?: 0.0,
                        avgAsicTempC = live.asicTempC,
                        avgVrTempC = live.vrTempC,
                        avgAsicErrorPct = live.asicErrorPct,
                        acceptedShares = live.acceptedShares ?: 0L,
                        rejectedShares = live.rejectedShares ?: 0L,
                        staleShares = live.staleShares,
                        sampleCount = 1,
                        disconnectCount = 0,
                        isStable = false,
                        stabilityScore = 0.0,
                        stabilityNotes = "Manual restore: ${result.message}",
                        durationSeconds = 1L,
                        isLastKnownStable = false,
                        operationMode = "ROLLBACK",
                        experimentResult = if (result.verified) "ROLLED_BACK" else "VERIFY_FAILED",
                        rollbackTriggered = true,
                        rollbackTarget = "$targetFreq MHz / $targetVolt mV",
                        rollbackConfirmed = result.verified
                    )
                )
            }

            _uiState.update { state ->
                state.copy(
                    liveTelemetry = result.telemetryAfterApply ?: state.liveTelemetry,
                    manualControl = state.manualControl.copy(
                        status = if (result.verified) ManualControlStatus.VERIFIED_SUCCESS else ManualControlStatus.FAILED,
                        statusMessage = if (result.verified) {
                            "Restored and verified: $targetFreq MHz / $targetVolt mV"
                        } else {
                            "Rollback could not be confirmed. Inspect AxeOS directly. (${result.message})"
                        },
                        lastVerification = result
                    )
                )
            }
        }
    }

    // ==================== AI AUTO MODE CONTROLS & ENGINE ====================

    fun updateExplorationRange(
        minFreq: Int? = null,
        maxFreq: Int? = null,
        minVolt: Int? = null,
        maxVolt: Int? = null,
        maxBudget: Int? = null,
        obsSeconds: Long? = null
    ) {
        _uiState.update { state ->
            val current = state.explorationRange
            val updated = current.copy(
                minFrequencyMhz = minFreq ?: current.minFrequencyMhz,
                maxFrequencyMhz = maxFreq ?: current.maxFrequencyMhz,
                minVoltageMv = minVolt ?: current.minVoltageMv,
                maxVoltageMv = maxVolt ?: current.maxVoltageMv,
                maxExperimentsBudget = (maxBudget ?: current.maxExperimentsBudget).coerceIn(1, 30),
                observationSeconds = (obsSeconds ?: current.observationSeconds).coerceIn(30L, 600L)
            )
            state.copy(
                explorationRange = updated,
                rangeValidation = BitaxeOptimizationEngine.validateExplorationRange(
                    updated,
                    state.asicCapabilities
                )
            )
        }
    }

    fun stepRangeValue(field: String, deltaSteps: Int) {
        val r = _uiState.value.explorationRange
        when (field) {
            "minF" -> updateExplorationRange(
                minFreq = (r.minFrequencyMhz + deltaSteps * 5).coerceIn(600, 670)
            )
            "maxF" -> updateExplorationRange(
                maxFreq = (r.maxFrequencyMhz + deltaSteps * 5).coerceIn(600, 670)
            )
            "minV" -> updateExplorationRange(
                minVolt = (r.minVoltageMv + deltaSteps * 5).coerceIn(1100, 1170)
            )
            "maxV" -> updateExplorationRange(
                maxVolt = (r.maxVoltageMv + deltaSteps * 5).coerceIn(1100, 1170)
            )
        }
    }

    fun requestStartAiAutoOptimization() {
        val state = _uiState.value
        val validation = BitaxeOptimizationEngine.validateExplorationRange(
            state.explorationRange,
            state.asicCapabilities
        )
        _uiState.update { it.copy(rangeValidation = validation) }
        if (!validation.isValid) return
        if (activeHostIp == null || activeBaseUrl == null || state.connectionState != ConnectionState.CONNECTED) {
            _uiState.update {
                it.copy(
                    aiAutoState = it.aiAutoState.copy(
                        statusBanner = "Connect to your Bitaxe device before starting AI Auto Mode."
                    )
                )
            }
            return
        }
        _uiState.update { it.copy(showAiAuthDialog = true) }
    }

    fun dismissAiAuthDialog() {
        _uiState.update { it.copy(showAiAuthDialog = false) }
    }

    fun confirmAndStartAiAutoOptimization() {
        val ip = activeHostIp ?: return
        val url = activeBaseUrl ?: return
        if (_uiState.value.aiAutoState.isRunning) return

        _uiState.update { it.copy(showAiAuthDialog = false) }

        aiAutoJob?.cancel()
        aiAutoJob = viewModelScope.launch {
            runAiAutoLoop(ip, url)
        }
    }

    fun pauseAiAutoOptimization() {
        _uiState.update { state ->
            if (!state.aiAutoState.isRunning) state
            else state.copy(
                aiAutoState = state.aiAutoState.copy(
                    isPaused = true,
                    phase = AiAutoPhase.PAUSED,
                    statusBanner = "Paused by user. Monitoring continues; no new setting changes will be sent."
                )
            )
        }
    }

    fun resumeAiAutoOptimization() {
        _uiState.update { state ->
            if (!state.aiAutoState.isRunning) state
            else state.copy(
                aiAutoState = state.aiAutoState.copy(
                    isPaused = false,
                    statusBanner = "Resuming AI staged optimization..."
                )
            )
        }
    }

    fun stopAiAutoOptimization() {
        val currentState = _uiState.value
        if (!currentState.aiAutoState.isRunning) return
        aiAutoJob?.cancel()

        viewModelScope.launch {
            val ip = activeHostIp
            val url = activeBaseUrl
            val live = _uiState.value.liveTelemetry
            val thresholds = _uiState.value.settings.safetyThresholds
            val unstableNow = live != null && !SafetyController.assessLiveTelemetry(
                current = live,
                previousUptimeSeconds = null,
                baselineHashrateThs = _uiState.value.aiAutoState.baselineRecord?.avgHashrateThs,
                consecutiveTimeouts = 0,
                thresholds = thresholds,
                enforceHashrateCollapse = false
            ).isSafe

            if (unstableNow && ip != null && url != null) {
                performSafeRollback(
                    ip = ip,
                    url = url,
                    reason = "User stopped optimization while device was outside safety limits.",
                    targetPhase = AiAutoPhase.STOPPED
                )
            } else {
                _uiState.update {
                    it.copy(
                        aiAutoState = it.aiAutoState.copy(
                            isRunning = false,
                            isPaused = false,
                            phase = AiAutoPhase.STOPPED,
                            statusBanner = "AI Auto Optimization stopped safely by user."
                        )
                    )
                }
            }
        }
    }

    fun restoreLastStableFromAiAuto() {
        requestRestoreLastStable()
    }

    private suspend fun runAiAutoLoop(ip: String, url: String) {
        val initialRange = _uiState.value.explorationRange
        val profile = _uiState.value.activeProfile
        val thresholds = _uiState.value.settings.safetyThresholds.copy(
            minObservationSeconds = initialRange.observationSeconds
        )

        val eligible = BitaxeOptimizationEngine.generateEligibleCandidatesInRange(
            range = initialRange,
            capabilities = _uiState.value.asicCapabilities,
            history = _uiState.value.experiments,
            thresholds = thresholds
        )

        if (eligible.isEmpty()) {
            _uiState.update {
                it.copy(
                    aiAutoState = AiAutoSessionState(
                        phase = AiAutoPhase.STOPPED,
                        isRunning = false,
                        statusBanner = "No safe/supported candidates found inside the selected range."
                    )
                )
            }
            return
        }

        val plannedBudget = minOf(initialRange.maxExperimentsBudget, eligible.size)
        _uiState.update {
            it.copy(
                aiAutoState = AiAutoSessionState(
                    phase = AiAutoPhase.ESTABLISHING_BASELINE,
                    isRunning = true,
                    totalPlannedExperiments = plannedBudget,
                    statusBanner = "Step 1/4: Establishing real baseline from current device configuration..."
                )
            )
        }

        // Step 1-4: Read initial live telemetry and establish baseline
        val initPoll = repository.pollTelemetry(ip, url)
        val initLive = initPoll.getOrNull()
        if (initLive == null) {
            _uiState.update {
                it.copy(
                    aiAutoState = it.aiAutoState.copy(
                        isRunning = false,
                        phase = AiAutoPhase.SAFETY_ABORTED,
                        statusBanner = "Failed to establish baseline: device unreachable."
                    )
                )
            }
            return
        }
        recordIncomingSample(initLive)

        val baseFreq = initLive.frequencyMhz ?: initialRange.minFrequencyMhz
        val baseVolt = (initLive.coreVoltageTargetMv ?: initLive.coreVoltageActualMv) ?: initialRange.minVoltageMv

        val baselineObservationSec = minOf(initialRange.observationSeconds, 30L)
        val baselineOutcome = observeWindowWithSafety(
            ip = ip,
            url = url,
            targetFreq = baseFreq,
            targetVolt = baseVolt,
            durationSec = baselineObservationSec,
            baselineHashrateThs = null,
            thresholds = thresholds,
            enforceHashrateCollapse = false,
            phase = AiAutoPhase.ESTABLISHING_BASELINE
        )

        if (baselineOutcome.violationMessage != null) {
            _uiState.update {
                it.copy(
                    aiAutoState = it.aiAutoState.copy(
                        isRunning = false,
                        phase = AiAutoPhase.SAFETY_ABORTED,
                        statusBanner = "Baseline safety abort: ${baselineOutcome.violationMessage}"
                    )
                )
            }
            return
        }

        val baselineRecord = repository.buildExperimentRecordFromSamples(
            samples = baselineOutcome.samples,
            frequencyMhz = baseFreq,
            voltageMv = baseVolt,
            startTimestampMillis = baselineOutcome.startMs,
            disconnectCount = baselineOutcome.timeouts,
            thresholds = thresholds.copy(minObservationSeconds = baselineObservationSec, minSampleCount = 5),
            operationMode = "BASELINE"
        )
        if (baselineRecord == null) {
            _uiState.update {
                it.copy(
                    aiAutoState = it.aiAutoState.copy(
                        isRunning = false,
                        phase = AiAutoPhase.SAFETY_ABORTED,
                        statusBanner = "Insufficient valid baseline telemetry received."
                    )
                )
            }
            return
        }
        repository.saveExperiment(baselineRecord)

        val testedSet = mutableSetOf(baseFreq to baseVolt)
        val sessionRecords = mutableListOf<ExperimentRecord>(baselineRecord)
        var bestStable: ExperimentRecord = baselineRecord

        _uiState.update {
            it.copy(
                aiAutoState = it.aiAutoState.copy(
                    baselineRecord = baselineRecord,
                    bestStableRecord = bestStable,
                    statusBanner = "Baseline locked (${baseFreq} MHz / ${baseVolt} mV • ${"%.3f".format(baselineRecord.avgHashrateThs)} TH/s • ${"%.2f".format(baselineRecord.efficiencyJth)} J/TH)"
                )
            )
        }

        // Staged Optimization Loop
        for (expNum in 1..plannedBudget) {
            waitWhilePaused()
            if (!coroutineContextIsActive()) break

            _uiState.update {
                it.copy(
                    aiAutoState = it.aiAutoState.copy(
                        phase = AiAutoPhase.SELECTING_CANDIDATE,
                        currentExperimentNumber = expNum,
                        statusBanner = "Selecting candidate #$expNum/$plannedBudget around stable anchor ${bestStable.frequencyMhz} MHz / ${bestStable.voltageMv} mV..."
                    )
                )
            }

            val nextPair = BitaxeOptimizationEngine.selectNextAutoCandidate(
                eligibleCandidates = eligible,
                testedInSession = testedSet,
                anchorFreqMhz = bestStable.frequencyMhz,
                anchorVoltMv = bestStable.voltageMv,
                historicalRecords = _uiState.value.experiments,
                profile = profile
            ) ?: break

            val (candFreq, candVolt) = nextPair
            testedSet += nextPair

            // Pre-apply safety validation
            val preSafety = SafetyController.validateTargetSetting(
                frequencyMhz = candFreq,
                voltageMv = candVolt,
                capabilities = _uiState.value.asicCapabilities,
                liveTelemetry = _uiState.value.liveTelemetry,
                thresholds = thresholds
            )
            if (!preSafety.isSafe) {
                val msg = preSafety.violations.joinToString("; ") { it.message }
                appendAutoLog(
                    AiAutoExperimentLogItem(
                        index = expNum,
                        frequencyMhz = candFreq,
                        voltageMv = candVolt,
                        result = "REJECTED_PRECHECK",
                        avgHashrateThs = null,
                        avgPowerWatts = null,
                        efficiencyJth = null,
                        asicTempC = null,
                        errorPct = null,
                        detail = msg
                    )
                )
                continue
            }

            // Apply and verify configuration
            _uiState.update {
                it.copy(
                    aiAutoState = it.aiAutoState.copy(
                        phase = AiAutoPhase.APPLYING_CONFIG,
                        currentTargetFreqMhz = candFreq,
                        currentTargetVoltMv = candVolt,
                        phaseProgress = 0f,
                        statusBanner = "Experiment #$expNum/$plannedBudget: Applying $candFreq MHz / $candVolt mV..."
                    )
                )
            }

            val applyRes = repository.applyAndVerifyConfig(ip, url, candFreq, candVolt)
            applyRes.telemetryAfterApply?.let { recordIncomingSample(it) }

            if (!applyRes.verified) {
                appendAutoLog(
                    AiAutoExperimentLogItem(
                        index = expNum,
                        frequencyMhz = candFreq,
                        voltageMv = candVolt,
                        result = "VERIFY_FAILED",
                        avgHashrateThs = applyRes.telemetryAfterApply?.currentHashrateThs,
                        avgPowerWatts = applyRes.telemetryAfterApply?.powerWatts,
                        efficiencyJth = applyRes.telemetryAfterApply?.efficiencyJth,
                        asicTempC = applyRes.telemetryAfterApply?.asicTempC,
                        errorPct = applyRes.telemetryAfterApply?.asicErrorPct,
                        detail = applyRes.message
                    )
                )
                if (!applyRes.httpSuccess) {
                    // Device unreachable or unsupported PATCH endpoint -> safe abort without spamming
                    performSafeRollback(
                        ip = ip,
                        url = url,
                        reason = applyRes.message,
                        targetPhase = AiAutoPhase.SAFETY_ABORTED
                    )
                    return
                }
                continue
            }

            // Stabilization wait
            val stabOutcome = observeWindowWithSafety(
                ip = ip,
                url = url,
                targetFreq = candFreq,
                targetVolt = candVolt,
                durationSec = initialRange.stabilizationSeconds,
                baselineHashrateThs = baselineRecord.avgHashrateThs,
                thresholds = thresholds,
                enforceHashrateCollapse = false,
                phase = AiAutoPhase.STABILIZING
            )

            if (stabOutcome.violationMessage != null) {
                handleSafetyAbortAndSave(
                    ip = ip,
                    url = url,
                    expNum = expNum,
                    freq = candFreq,
                    volt = candVolt,
                    outcome = stabOutcome,
                    thresholds = thresholds
                )
                return
            }

            // Full measurement observation window
            val obsOutcome = observeWindowWithSafety(
                ip = ip,
                url = url,
                targetFreq = candFreq,
                targetVolt = candVolt,
                durationSec = initialRange.observationSeconds,
                baselineHashrateThs = baselineRecord.avgHashrateThs,
                thresholds = thresholds,
                enforceHashrateCollapse = true,
                phase = AiAutoPhase.OBSERVING
            )

            if (obsOutcome.violationMessage != null) {
                handleSafetyAbortAndSave(
                    ip = ip,
                    url = url,
                    expNum = expNum,
                    freq = candFreq,
                    volt = candVolt,
                    outcome = obsOutcome,
                    thresholds = thresholds
                )
                return
            }

            val record = repository.buildExperimentRecordFromSamples(
                samples = obsOutcome.samples,
                frequencyMhz = candFreq,
                voltageMv = candVolt,
                startTimestampMillis = obsOutcome.startMs,
                disconnectCount = obsOutcome.timeouts,
                thresholds = thresholds,
                operationMode = "AI_AUTO"
            )
            if (record != null) {
                repository.saveExperiment(record)
                sessionRecords += record
                val stablePool = sessionRecords.filter { it.isStable }
                if (stablePool.isNotEmpty()) {
                    val scores = BitaxeOptimizationEngine.scoreStableRecords(stablePool, profile)
                    bestStable = stablePool.maxByOrNull { scores[it.frequencyMhz to it.voltageMv] ?: 0.0 } ?: bestStable
                }

                appendAutoLog(
                    AiAutoExperimentLogItem(
                        index = expNum,
                        frequencyMhz = candFreq,
                        voltageMv = candVolt,
                        result = record.experimentResult,
                        avgHashrateThs = record.avgHashrateThs,
                        avgPowerWatts = record.avgPowerWatts,
                        efficiencyJth = record.efficiencyJth,
                        asicTempC = record.avgAsicTempC,
                        errorPct = record.avgAsicErrorPct,
                        detail = record.stabilityNotes
                    )
                )

                _uiState.update {
                    it.copy(
                        aiAutoState = it.aiAutoState.copy(
                            bestStableRecord = bestStable
                        )
                    )
                }
            }
        }

        // Optimization budget completed: apply & verify best measured stable config if different from current
        val liveFreq = _uiState.value.liveTelemetry?.frequencyMhz
        val liveVolt = _uiState.value.liveTelemetry?.coreVoltageTargetMv
        if (bestStable.frequencyMhz != liveFreq || bestStable.voltageMv != liveVolt) {
            _uiState.update {
                it.copy(
                    aiAutoState = it.aiAutoState.copy(
                        phase = AiAutoPhase.APPLYING_CONFIG,
                        statusBanner = "Applying best verified stable setting: ${bestStable.frequencyMhz} MHz / ${bestStable.voltageMv} mV..."
                    )
                )
            }
            repository.applyAndVerifyConfig(ip, url, bestStable.frequencyMhz, bestStable.voltageMv)
        }

        _uiState.update {
            it.copy(
                aiAutoState = it.aiAutoState.copy(
                    isRunning = false,
                    isPaused = false,
                    phase = AiAutoPhase.COMPLETED,
                    phaseProgress = 1f,
                    bestStableRecord = bestStable,
                    statusBanner = "Optimization complete! Best stable: ${bestStable.frequencyMhz} MHz / ${bestStable.voltageMv} mV (${"%.3f".format(bestStable.avgHashrateThs)} TH/s, ${"%.2f".format(bestStable.efficiencyJth)} J/TH)"
                )
            )
        }
    }

    private data class ObservationOutcome(
        val samples: List<LiveTelemetry>,
        val startMs: Long,
        val timeouts: Int,
        val violationMessage: String?
    )

    private suspend fun observeWindowWithSafety(
        ip: String,
        url: String,
        targetFreq: Int,
        targetVolt: Int,
        durationSec: Long,
        baselineHashrateThs: Double?,
        thresholds: com.example.domain.model.SafetyThresholdConfig,
        enforceHashrateCollapse: Boolean,
        phase: AiAutoPhase
    ): ObservationOutcome {
        val startMs = System.currentTimeMillis()
        val samples = mutableListOf<LiveTelemetry>()
        var consecutiveTimeouts = 0
        var totalTimeouts = 0
        var prevUptime: Long? = _uiState.value.liveTelemetry?.uptimeSeconds

        while (true) {
            waitWhilePaused()
            val elapsedSec = (System.currentTimeMillis() - startMs) / 1000L
            if (elapsedSec >= durationSec && samples.isNotEmpty()) break

            val poll = repository.pollTelemetry(ip, url)
            val live = poll.getOrNull()
            if (live == null) {
                consecutiveTimeouts++
                totalTimeouts++
                if (consecutiveTimeouts > thresholds.maxDisconnectsAllowed) {
                    return ObservationOutcome(
                        samples = samples,
                        startMs = startMs,
                        timeouts = totalTimeouts,
                        violationMessage = "Telemetry timeout / network interruption ($consecutiveTimeouts consecutive failures)."
                    )
                }
            } else {
                consecutiveTimeouts = 0
                recordIncomingSample(live)
                samples += live
                _uiState.update {
                    it.copy(
                        liveTelemetry = live,
                        aiAutoState = it.aiAutoState.copy(
                            phase = phase,
                            phaseProgress = (elapsedSec.toFloat() / durationSec.toFloat()).coerceIn(0f, 1f),
                            activeExperimentSamples = samples.toList(),
                            statusBanner = "${phase.name}: $targetFreq MHz / $targetVolt mV • ${elapsedSec}s/${durationSec}s"
                        )
                    )
                }

                val assessment = SafetyController.assessLiveTelemetry(
                    current = live,
                    previousUptimeSeconds = prevUptime,
                    baselineHashrateThs = baselineHashrateThs,
                    consecutiveTimeouts = consecutiveTimeouts,
                    thresholds = thresholds,
                    enforceHashrateCollapse = enforceHashrateCollapse
                )
                prevUptime = live.uptimeSeconds ?: prevUptime

                if (!assessment.isSafe) {
                    return ObservationOutcome(
                        samples = samples,
                        startMs = startMs,
                        timeouts = totalTimeouts,
                        violationMessage = assessment.violations.joinToString("; ") { it.message }
                    )
                }
            }

            delay(3000L)
        }

        return ObservationOutcome(
            samples = samples,
            startMs = startMs,
            timeouts = totalTimeouts,
            violationMessage = null
        )
    }

    private suspend fun handleSafetyAbortAndSave(
        ip: String,
        url: String,
        expNum: Int,
        freq: Int,
        volt: Int,
        outcome: ObservationOutcome,
        thresholds: com.example.domain.model.SafetyThresholdConfig
    ) {
        val reason = outcome.violationMessage ?: "Safety threshold exceeded"
        val rollbackTarget = SafetyController.selectRollbackTarget(
            history = _uiState.value.experiments,
            baseline = _uiState.value.aiAutoState.baselineRecord,
            capabilities = _uiState.value.asicCapabilities
        )
        val rollbackLabel = rollbackTarget?.let { "${it.first} MHz / ${it.second} mV" }

        val confirmedRollback = performSafeRollback(
            ip = ip,
            url = url,
            reason = reason,
            targetPhase = AiAutoPhase.SAFETY_ABORTED
        )

        if (outcome.samples.isNotEmpty()) {
            val failedRec = repository.buildExperimentRecordFromSamples(
                samples = outcome.samples,
                frequencyMhz = freq,
                voltageMv = volt,
                startTimestampMillis = outcome.startMs,
                disconnectCount = outcome.timeouts,
                thresholds = thresholds,
                operationMode = "AI_AUTO",
                forcedResult = "SAFETY_ABORTED",
                forcedNotes = reason,
                rollbackTriggered = rollbackTarget != null,
                rollbackTarget = rollbackLabel,
                rollbackConfirmed = confirmedRollback
            )
            if (failedRec != null) {
                repository.saveExperiment(failedRec)
            }
        }

        appendAutoLog(
            AiAutoExperimentLogItem(
                index = expNum,
                frequencyMhz = freq,
                voltageMv = volt,
                result = "SAFETY_ABORTED",
                avgHashrateThs = outcome.samples.mapNotNull { it.currentHashrateThs }.takeIf { it.isNotEmpty() }?.average(),
                avgPowerWatts = outcome.samples.mapNotNull { it.powerWatts }.takeIf { it.isNotEmpty() }?.average(),
                efficiencyJth = outcome.samples.mapNotNull { it.efficiencyJth }.takeIf { it.isNotEmpty() }?.average(),
                asicTempC = outcome.samples.mapNotNull { it.asicTempC }.takeIf { it.isNotEmpty() }?.average(),
                errorPct = outcome.samples.mapNotNull { it.asicErrorPct }.takeIf { it.isNotEmpty() }?.average(),
                detail = reason
            )
        )
    }

    /**
     * Attempts a single clean rollback to the last verified stable configuration.
     * Never spams configuration commands if the device is unreachable.
     */
    private suspend fun performSafeRollback(
        ip: String,
        url: String,
        reason: String,
        targetPhase: AiAutoPhase
    ): Boolean {
        val target = SafetyController.selectRollbackTarget(
            history = _uiState.value.experiments,
            baseline = _uiState.value.aiAutoState.baselineRecord,
            capabilities = _uiState.value.asicCapabilities
        )

        if (target == null) {
            _uiState.update {
                it.copy(
                    aiAutoState = it.aiAutoState.copy(
                        isRunning = false,
                        isPaused = false,
                        phase = targetPhase,
                        statusBanner = "Safety abort: $reason",
                        rollbackNotice = "No previous stable configuration available. Inspect AxeOS directly."
                    )
                )
            }
            return false
        }

        _uiState.update {
            it.copy(
                aiAutoState = it.aiAutoState.copy(
                    phase = AiAutoPhase.ROLLING_BACK,
                    statusBanner = "Safety triggered ($reason). Restoring ${target.first} MHz / ${target.second} mV..."
                )
            )
        }

        // Probe reachability first; if unreachable, do NOT send repeated config writes
        val ping = repository.pollTelemetry(ip, url)
        if (ping.isFailure) {
            _uiState.update {
                it.copy(
                    aiAutoState = it.aiAutoState.copy(
                        isRunning = false,
                        isPaused = false,
                        phase = targetPhase,
                        statusBanner = "Safety abort: $reason",
                        rollbackNotice = "Device unreachable; recovery to ${target.first} MHz / ${target.second} mV could not be confirmed. Please inspect AxeOS directly."
                    )
                )
            }
            return false
        }

        val rollbackRes = repository.applyAndVerifyConfig(ip, url, target.first, target.second)
        rollbackRes.telemetryAfterApply?.let { recordIncomingSample(it) }

        val notice = if (rollbackRes.verified) {
            "Rollback confirmed: restored ${target.first} MHz / ${target.second} mV."
        } else {
            "Recovery could not be confirmed (${rollbackRes.message}). Inspect AxeOS directly."
        }

        _uiState.update {
            it.copy(
                liveTelemetry = rollbackRes.telemetryAfterApply ?: it.liveTelemetry,
                aiAutoState = it.aiAutoState.copy(
                    isRunning = false,
                    isPaused = false,
                    phase = targetPhase,
                    statusBanner = "Stopped: $reason",
                    rollbackNotice = notice
                )
            )
        }
        return rollbackRes.verified
    }

    private suspend fun waitWhilePaused() {
        while (_uiState.value.aiAutoState.isPaused) {
            delay(500L)
        }
    }

    private fun coroutineContextIsActive(): Boolean = aiAutoJob?.isActive != false

    private fun appendAutoLog(item: AiAutoExperimentLogItem) {
        _uiState.update { state ->
            state.copy(
                aiAutoState = state.aiAutoState.copy(
                    logs = listOf(item) + state.aiAutoState.logs
                )
            )
        }
    }

    // ==================== BASELINE ANALYZER & SETTINGS ====================

    fun startRecordingSession() {
        val live = _uiState.value.liveTelemetry ?: return
        val freq = live.frequencyMhz ?: return
        val volt = live.coreVoltageTargetMv ?: live.coreVoltageActualMv ?: return

        _uiState.update {
            it.copy(
                activeRecording = ActiveRecordingSession(
                    startTimestampMillis = System.currentTimeMillis(),
                    deviceIp = live.deviceIp,
                    frequencyMhz = freq,
                    voltageMv = volt,
                    samples = listOf(live),
                    disconnectCount = 0
                ),
                recordingStatusMessage = null
            )
        }
    }

    fun stopAndSaveRecordingSession() {
        val session = _uiState.value.activeRecording ?: return
        val thresholds = _uiState.value.settings.safetyThresholds
        val record = repository.buildExperimentRecordFromSamples(
            samples = session.samples,
            frequencyMhz = session.frequencyMhz,
            voltageMv = session.voltageMv,
            startTimestampMillis = session.startTimestampMillis,
            disconnectCount = session.disconnectCount,
            thresholds = thresholds,
            operationMode = "BASELINE"
        )
        if (record == null) {
            _uiState.update {
                it.copy(
                    activeRecording = null,
                    recordingStatusMessage = "Could not save session: no valid hashrate/power samples were received."
                )
            }
            return
        }
        viewModelScope.launch {
            repository.saveExperiment(record)
            _uiState.update {
                it.copy(
                    activeRecording = null,
                    recordingStatusMessage = "Saved ${record.frequencyMhz} MHz / ${record.voltageMv} mV (${record.sampleCount} samples, ${"%.2f".format(record.efficiencyJth)} J/TH)."
                )
            }
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { repository.setLanguage(language) }
    }

    fun setPollingInterval(seconds: Int) {
        viewModelScope.launch { repository.setPollingInterval(seconds) }
    }

    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoReconnect(enabled) }
    }

    fun setDefaultProfile(profile: AnalysisProfile) {
        viewModelScope.launch {
            repository.setDefaultProfile(profile)
            _uiState.update { it.copy(activeProfile = profile) }
            recomputeAnalysisModels()
        }
    }

    fun updateSafetyThresholds(
        maxAsicTempC: Double,
        maxVrTempC: Double,
        maxErrorPct: Double,
        minObservationSeconds: Long
    ) {
        viewModelScope.launch {
            repository.updateSafetyThresholds(
                maxAsicTempC = maxAsicTempC,
                maxVrTempC = maxVrTempC,
                maxErrorPct = maxErrorPct,
                minObservationSeconds = minObservationSeconds
            )
        }
    }

    private fun recordIncomingSample(telemetry: LiveTelemetry) {
        val oneHourAgo = System.currentTimeMillis() - 3_600_000L
        rawSamplesBuffer.addLast(telemetry)
        while (rawSamplesBuffer.size > 1200 ||
            (rawSamplesBuffer.isNotEmpty() && rawSamplesBuffer.first().timestampMillis < oneHourAgo)
        ) {
            rawSamplesBuffer.removeFirst()
        }

        val samplesList = rawSamplesBuffer.toList()
        val rolling = repository.calculateRollingAverages(samplesList)
        val chartPoints = samplesList.takeLast(60).map { s ->
            TelemetryChartPoint(
                timestampMillis = s.timestampMillis,
                hashrateThs = s.currentHashrateThs,
                frequencyMhz = s.frequencyMhz,
                voltageMv = s.coreVoltageTargetMv ?: s.coreVoltageActualMv,
                powerWatts = s.powerWatts,
                efficiencyJth = s.efficiencyJth,
                asicTempC = s.asicTempC,
                vrTempC = s.vrTempC,
                asicErrorPct = s.asicErrorPct
            )
        }

        _uiState.update { state ->
            val currentRec = state.activeRecording
            val updatedRec = if (currentRec != null) {
                val sampleFreq = telemetry.frequencyMhz
                val sampleVolt = telemetry.coreVoltageTargetMv ?: telemetry.coreVoltageActualMv
                if ((sampleFreq == null || sampleFreq == currentRec.frequencyMhz) &&
                    (sampleVolt == null || sampleVolt == currentRec.voltageMv)
                ) {
                    currentRec.copy(samples = currentRec.samples + telemetry)
                } else {
                    currentRec
                }
            } else null

            state.copy(
                chartHistory = chartPoints,
                rollingAverages = rolling,
                activeRecording = updatedRec
            )
        }
    }

    private fun recomputeAnalysisModels() {
        val state = _uiState.value
        val grid = BitaxeOptimizationEngine.buildPrecisionGrid(
            history = state.experiments,
            currentTelemetry = state.liveTelemetry,
            capabilities = state.asicCapabilities,
            thresholds = state.settings.safetyThresholds,
            profile = state.activeProfile
        )

        val recommendation = BitaxeOptimizationEngine.recommend(
            history = state.experiments,
            currentTelemetry = state.liveTelemetry,
            capabilities = state.asicCapabilities,
            thresholds = state.settings.safetyThresholds,
            profile = state.activeProfile
        )

        val currentEff = state.liveTelemetry?.efficiencyJth
            ?: recommendation.currentConfig?.efficiencyJth
        val prevEff = state.experiments.firstOrNull { it.isStable }?.efficiencyJth
        val recEff = recommendation.recommendedConfig?.efficiencyJth
        val currentPower = state.liveTelemetry?.powerWatts ?: recommendation.currentConfig?.avgPowerWatts
        val recPower = recommendation.recommendedConfig?.avgPowerWatts
        val currentHash = state.liveTelemetry?.currentHashrateThs ?: recommendation.currentConfig?.avgHashrateThs
        val recHash = recommendation.recommendedConfig?.avgHashrateThs

        val effComparison = EfficiencyCalculator.buildComparison(
            currentEfficiencyJth = currentEff,
            previousEfficiencyJth = prevEff,
            recommendedEfficiencyJth = recEff,
            currentPowerWatts = currentPower,
            recommendedPowerWatts = recPower,
            currentHashrateThs = currentHash,
            recommendedHashrateThs = recHash,
            recommendedIsMeasured = recommendation.recommendedConfig != null
        )

        _uiState.update {
            it.copy(
                precisionGrid = grid,
                recommendation = recommendation,
                efficiencyComparison = effComparison
            )
        }
    }

    private fun snapToStep(value: Int, min: Int, max: Int, step: Int): Int {
        val clamped = value.coerceIn(min, max)
        val stepsFromMin = ((clamped - min) + step / 2) / step
        return (min + stepsFromMin * step).coerceIn(min, max)
    }

    override fun onCleared() {
        aiAutoJob?.cancel()
        pollingJob?.cancel()
        wsJob?.cancel()
        super.onCleared()
    }
}
