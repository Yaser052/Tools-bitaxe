package com.example.data.repository

import com.example.data.local.AppSettings
import com.example.data.local.ExperimentDao
import com.example.data.local.ExperimentEntity
import com.example.data.local.SettingsDataStore
import com.example.data.network.AxeOsNetworkClient
import com.example.data.network.DiscoveredBitaxeDevice
import com.example.data.network.FullDeviceSnapshot
import com.example.data.network.WebSocketEvent
import com.example.domain.engine.BitaxeOptimizationEngine
import com.example.domain.engine.EfficiencyCalculator
import com.example.domain.model.AnalysisProfile
import com.example.domain.model.AppLanguage
import com.example.domain.model.ExperimentRecord
import com.example.domain.model.LiveTelemetry
import com.example.domain.model.RollingAverages
import com.example.domain.model.SafetyThresholdConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BitaxeRepository(
    private val networkClient: AxeOsNetworkClient,
    private val experimentDao: ExperimentDao,
    private val settingsDataStore: SettingsDataStore
) {

    val allExperiments: Flow<List<ExperimentRecord>> =
        experimentDao.observeAllExperiments().map { list -> list.map { it.toDomain() } }

    val settingsFlow: Flow<AppSettings> = settingsDataStore.settingsFlow

    suspend fun connectAndProbe(hostIp: String, baseUrl: String): Result<FullDeviceSnapshot> {
        return runCatching {
            val snapshot = networkClient.probeAllEndpoints(hostIp, baseUrl)
            settingsDataStore.saveConnectedIp(hostIp)
            snapshot
        }
    }

    suspend fun pollTelemetry(hostIp: String, baseUrl: String): Result<LiveTelemetry> {
        return networkClient.fetchSystemInfo(hostIp, baseUrl)
    }

    fun observeWebSocket(hostIp: String): Flow<WebSocketEvent> =
        networkClient.observeLiveWebSocket(hostIp)

    suspend fun discoverLocalDevices(): List<DiscoveredBitaxeDevice> =
        networkClient.discoverLocalBitaxeDevices()

    suspend fun saveExperiment(record: ExperimentRecord): Long {
        val insertedId = experimentDao.insertExperiment(ExperimentEntity.fromDomain(record))
        if (record.isStable) {
            experimentDao.clearLastKnownStableFlag()
            experimentDao.markAsLastKnownStable(insertedId)
        }
        return insertedId
    }

    suspend fun setLanguage(language: AppLanguage) = settingsDataStore.setLanguage(language)

    suspend fun setPollingInterval(seconds: Int) = settingsDataStore.setPollingIntervalSeconds(seconds)

    suspend fun setAutoReconnect(enabled: Boolean) = settingsDataStore.setAutoReconnect(enabled)

    suspend fun setDefaultProfile(profile: AnalysisProfile) = settingsDataStore.setDefaultProfile(profile)

    suspend fun updateSafetyThresholds(
        maxAsicTempC: Double,
        maxVrTempC: Double,
        maxErrorPct: Double,
        minObservationSeconds: Long
    ) = settingsDataStore.updateSafetyThresholds(
        maxAsicTempC = maxAsicTempC,
        maxVrTempC = maxVrTempC,
        maxErrorPct = maxErrorPct,
        minObservationSeconds = minObservationSeconds
    )

    fun calculateRollingAverages(samples: List<LiveTelemetry>, nowMs: Long = System.currentTimeMillis()): RollingAverages {
        val valid = samples.filter { it.currentHashrateThs != null }
        val s1m = valid.filter { nowMs - it.timestampMillis <= 60_000L }
        val s10m = valid.filter { nowMs - it.timestampMillis <= 600_000L }
        val s30m = valid.filter { nowMs - it.timestampMillis <= 1_800_000L }
        val s1h = valid.filter { nowMs - it.timestampMillis <= 3_600_000L }

        return RollingAverages(
            avg1mThs = s1m.mapNotNull { it.currentHashrateThs }.takeIf { it.isNotEmpty() }?.average(),
            avg10mThs = s10m.mapNotNull { it.currentHashrateThs }.takeIf { it.isNotEmpty() }?.average(),
            avg30mThs = s30m.mapNotNull { it.currentHashrateThs }.takeIf { it.isNotEmpty() }?.average(),
            avg1hThs = s1h.mapNotNull { it.currentHashrateThs }.takeIf { it.isNotEmpty() }?.average(),
            sampleCount1m = s1m.size,
            sampleCount10m = s10m.size,
            sampleCount30m = s30m.size,
            sampleCount1h = s1h.size
        )
    }

    fun buildExperimentRecordFromSamples(
        samples: List<LiveTelemetry>,
        frequencyMhz: Int,
        voltageMv: Int,
        startTimestampMillis: Long,
        disconnectCount: Int,
        thresholds: SafetyThresholdConfig
    ): ExperimentRecord? {
        val validHash = samples.mapNotNull { it.currentHashrateThs }.filter { it > 0.0 }
        val validPower = samples.mapNotNull { it.powerWatts }.filter { it > 0.0 }
        if (validHash.isEmpty() || validPower.isEmpty()) return null

        val last = samples.last()
        val endMs = last.timestampMillis
        val durationSec = ((endMs - startTimestampMillis) / 1000L).coerceAtLeast(1L)
        val avgHash = validHash.average()
        val avgPower = validPower.average()
        val efficiency = EfficiencyCalculator.calculateEfficiencyJth(avgPower, avgHash) ?: 0.0
        val avgTemp = samples.mapNotNull { it.asicTempC }.takeIf { it.isNotEmpty() }?.average()
        val avgVr = samples.mapNotNull { it.vrTempC }.takeIf { it.isNotEmpty() }?.average()
        val avgErr = samples.mapNotNull { it.asicErrorPct }.takeIf { it.isNotEmpty() }?.average()

        val firstAccepted = samples.firstNotNullOfOrNull { it.acceptedShares } ?: 0L
        val lastAccepted = samples.asReversed().firstNotNullOfOrNull { it.acceptedShares } ?: firstAccepted
        val firstRejected = samples.firstNotNullOfOrNull { it.rejectedShares } ?: 0L
        val lastRejected = samples.asReversed().firstNotNullOfOrNull { it.rejectedShares } ?: firstRejected
        val firstStale = samples.firstNotNullOfOrNull { it.staleShares }
        val lastStale = samples.asReversed().firstNotNullOfOrNull { it.staleShares }

        val stabilityScore = BitaxeOptimizationEngine.computeStabilityScore(
            hashrates = validHash,
            asicErrorPct = avgErr,
            disconnectCount = disconnectCount,
            durationSeconds = durationSec,
            thresholds = thresholds
        )

        val draft = ExperimentRecord(
            timestampMillis = startTimestampMillis,
            endTimestampMillis = endMs,
            deviceIp = last.deviceIp,
            deviceIdentifier = last.macAddress ?: last.hostname ?: last.deviceIp,
            deviceModel = last.deviceModel ?: "Bitaxe Gamma 1370",
            boardVersion = last.boardVersion ?: "Unknown",
            firmwareVersion = last.firmwareVersion ?: "Unknown",
            frequencyMhz = frequencyMhz,
            voltageMv = voltageMv,
            avgHashrateThs = avgHash,
            minHashrateThs = validHash.minOrNull() ?: avgHash,
            maxHashrateThs = validHash.maxOrNull() ?: avgHash,
            avgPowerWatts = avgPower,
            efficiencyJth = efficiency,
            avgAsicTempC = avgTemp,
            avgVrTempC = avgVr,
            avgAsicErrorPct = avgErr,
            acceptedShares = (lastAccepted - firstAccepted).coerceAtLeast(0L),
            rejectedShares = (lastRejected - firstRejected).coerceAtLeast(0L),
            staleShares = if (firstStale != null && lastStale != null) (lastStale - firstStale).coerceAtLeast(0L) else null,
            sampleCount = samples.size,
            disconnectCount = disconnectCount,
            isStable = false,
            stabilityScore = stabilityScore,
            stabilityNotes = "",
            durationSeconds = durationSec,
            isLastKnownStable = false
        )

        val issues = BitaxeOptimizationEngine.evaluateSafetyIssues(draft, null, thresholds)
        val stable = issues.isEmpty() && stabilityScore >= 60.0
        val notes = if (stable) {
            "Verified stable (${samples.size} samples, ${durationSec}s, score ${stabilityScore.toInt()}/100)"
        } else {
            issues.joinToString("; ").ifEmpty { "Stability score ${stabilityScore.toInt()}/100 below 60" }
        }

        return draft.copy(
            isStable = stable,
            stabilityNotes = notes,
            isLastKnownStable = stable
        )
    }
}
