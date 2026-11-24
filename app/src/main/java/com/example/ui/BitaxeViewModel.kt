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
import com.example.domain.model.ActiveRecordingSession
import com.example.domain.model.AnalysisProfile
import com.example.domain.model.AppLanguage
import com.example.domain.model.AsicCapabilities
import com.example.domain.model.ConnectionState
import com.example.domain.model.EfficiencyComparison
import com.example.domain.model.EndpointProbeResult
import com.example.domain.model.EndpointStatus
import com.example.domain.model.ExperimentRecord
import com.example.domain.model.GridCandidate
import com.example.domain.model.LiveTelemetry
import com.example.domain.model.OptimizationRecommendation
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
    val selectedMatrixCell: GridCandidate? = null
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
                            activeProfile = settings.defaultProfile
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
                _uiState.update { state ->
                    state.copy(
                        connectionState = ConnectionState.CONNECTED,
                        connectionError = null,
                        liveTelemetry = snapshot.telemetry,
                        asicCapabilities = snapshot.asicCapabilities,
                        endpointProbes = snapshot.endpointProbes,
                        firmwareLogs = snapshot.firmwareLogs.takeLast(80)
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
        pollingJob?.cancel()
        wsJob?.cancel()
        activeHostIp = null
        activeBaseUrl = null
        _uiState.update {
            it.copy(
                connectionState = ConnectionState.DISCONNECTED,
                connectionError = null,
                activeRecording = null
            )
        }
    }

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
            thresholds = thresholds
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
                // Only append samples matching the active configuration being measured
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

    override fun onCleared() {
        pollingJob?.cancel()
        wsJob?.cancel()
        super.onCleared()
    }
}
