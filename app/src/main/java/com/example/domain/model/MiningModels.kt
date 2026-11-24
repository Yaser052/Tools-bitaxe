package com.example.domain.model

enum class AppLanguage(val code: String, val isRtl: Boolean) {
    EN("en", false),
    AR("ar", true)
}

enum class AnalysisProfile {
    ECO,
    BALANCED,
    BEAST
}

enum class DataProvenance {
    MEASURED,
    ESTIMATED,
    UNAVAILABLE,
    UNTESTED
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

enum class EndpointStatus {
    SUPPORTED,
    UNSUPPORTED,
    ERROR,
    NOT_PROBED
}

data class EndpointProbeResult(
    val endpoint: String,
    val protocol: String,
    val status: EndpointStatus,
    val httpCode: Int? = null,
    val detail: String? = null,
    val lastCheckedMs: Long = System.currentTimeMillis()
)

data class LiveTelemetry(
    val timestampMillis: Long = System.currentTimeMillis(),
    val deviceIp: String = "",
    val hostname: String? = null,
    val deviceModel: String? = null,
    val boardVersion: String? = null,
    val asicModel: String? = null,
    val asicCount: Int? = null,
    val smallCoreCount: Int? = null,
    val firmwareVersion: String? = null,
    val axeOsVersion: String? = null,
    val idfVersion: String? = null,
    val macAddress: String? = null,
    val ssid: String? = null,
    val wifiStatus: String? = null,
    val wifiRssi: Int? = null,
    val currentHashrateGhs: Double? = null,
    val currentHashrateThs: Double? = null,
    val averageHashrateThs: Double? = null,
    val expectedHashrateThs: Double? = null,
    val frequencyMhz: Int? = null,
    val coreVoltageTargetMv: Int? = null,
    val coreVoltageActualMv: Int? = null,
    val inputVoltageMv: Double? = null,
    val currentMa: Double? = null,
    val powerWatts: Double? = null,
    val efficiencyJth: Double? = null,
    val asicTempC: Double? = null,
    val vrTempC: Double? = null,
    val asicErrorPct: Double? = null,
    val acceptedShares: Long? = null,
    val rejectedShares: Long? = null,
    val staleShares: Long? = null,
    val bestDiff: String? = null,
    val bestSessionDiff: String? = null,
    val fanRpm: Int? = null,
    val fanSpeedPct: Double? = null,
    val autoFanSpeed: Boolean? = null,
    val uptimeSeconds: Long? = null,
    val stratumUrl: String? = null,
    val stratumPort: Int? = null,
    val stratumUser: String? = null,
    val isFallbackStratum: Boolean? = null,
    val poolDifficulty: Double? = null,
    val overheatMode: Boolean = false,
    val powerFault: String? = null,
    val latencyMs: Long? = null,
    val transportType: String = "HTTP",
    val isWebSocketLive: Boolean = false
) {
    val isBitaxeGamma1370Verified: Boolean
        get() {
            val chipMatch = asicModel?.contains("BM1370", ignoreCase = true) == true
            val boardMatch = boardVersion?.contains("601", ignoreCase = true) == true
            val modelMatch = deviceModel?.contains("Gamma", ignoreCase = true) == true ||
                hostname?.contains("bitaxe", ignoreCase = true) == true
            return chipMatch && (boardMatch || modelMatch)
        }
}

data class AsicCapabilities(
    val asicModel: String? = null,
    val deviceModel: String? = null,
    val frequencyOptions: List<Int> = emptyList(),
    val voltageOptions: List<Int> = emptyList(),
    val defaultFrequencyMhz: Int? = null,
    val defaultVoltageMv: Int? = null,
    val absMaxFrequencyMhz: Int? = null,
    val absMaxVoltageMv: Int? = null
)

data class RollingAverages(
    val avg1mThs: Double? = null,
    val avg10mThs: Double? = null,
    val avg30mThs: Double? = null,
    val avg1hThs: Double? = null,
    val sampleCount1m: Int = 0,
    val sampleCount10m: Int = 0,
    val sampleCount30m: Int = 0,
    val sampleCount1h: Int = 0
)

data class TelemetryChartPoint(
    val timestampMillis: Long,
    val hashrateThs: Double?,
    val frequencyMhz: Int?,
    val voltageMv: Int?,
    val powerWatts: Double?,
    val efficiencyJth: Double?,
    val asicTempC: Double?,
    val vrTempC: Double?,
    val asicErrorPct: Double?
)

enum class CandidateCellStatus {
    CURRENT_ACTIVE,
    MEASURED_STABLE,
    MEASURED_UNSTABLE,
    CANDIDATE_NEXT,
    UNTESTED,
    OUTSIDE_FIRMWARE_LIMIT
}

data class SafetyThresholdConfig(
    val maxAsicTempWarnC: Double = 65.0,
    val maxAsicTempHardLimitC: Double = 70.0,
    val maxVrTempWarnC: Double = 78.0,
    val maxVrTempHardLimitC: Double = 85.0,
    val maxAsicErrorPct: Double = 3.0,
    val minObservationSeconds: Long = 60L,
    val minSampleCount: Int = 10,
    val maxDisconnectsAllowed: Int = 1
)

data class ExperimentRecord(
    val id: Long = 0,
    val timestampMillis: Long,
    val endTimestampMillis: Long,
    val deviceIp: String,
    val deviceIdentifier: String,
    val deviceModel: String,
    val boardVersion: String,
    val firmwareVersion: String,
    val frequencyMhz: Int,
    val voltageMv: Int,
    val avgHashrateThs: Double,
    val minHashrateThs: Double,
    val maxHashrateThs: Double,
    val avgPowerWatts: Double,
    val efficiencyJth: Double,
    val avgAsicTempC: Double?,
    val avgVrTempC: Double?,
    val avgAsicErrorPct: Double?,
    val acceptedShares: Long,
    val rejectedShares: Long,
    val staleShares: Long?,
    val sampleCount: Int,
    val disconnectCount: Int,
    val isStable: Boolean,
    val stabilityScore: Double,
    val stabilityNotes: String,
    val durationSeconds: Long,
    val isLastKnownStable: Boolean = false,
    val operationMode: String = "BASELINE",
    val experimentResult: String = if (isStable) "STABLE_PASS" else "UNSTABLE",
    val rollbackTriggered: Boolean = false,
    val rollbackTarget: String? = null,
    val rollbackConfirmed: Boolean = false
)

data class GridCandidate(
    val frequencyMhz: Int,
    val voltageMv: Int,
    val status: CandidateCellStatus,
    val measuredRecord: ExperimentRecord? = null,
    val compositeScore: Double? = null,
    val safetyIssues: List<String> = emptyList(),
    val stepDistanceFromBaseline: Int? = null
)

data class EfficiencyComparison(
    val currentEfficiencyJth: Double?,
    val previousEfficiencyJth: Double?,
    val recommendedEfficiencyJth: Double?,
    val improvementPercentVsCurrent: Double?,
    val powerDiffWatts: Double?,
    val hashrateDiffThs: Double?,
    val currentProvenance: DataProvenance,
    val recommendedProvenance: DataProvenance
)

data class OptimizationRecommendation(
    val profile: AnalysisProfile,
    val isInsufficientData: Boolean,
    val insufficientDataReason: String? = null,
    val currentConfig: ExperimentRecord? = null,
    val recommendedConfig: ExperimentRecord? = null,
    val compositeScore: Double? = null,
    val confidencePercent: Int? = null,
    val confidenceLabel: String = "UNAVAILABLE",
    val nextExperimentCandidate: GridCandidate? = null,
    val explanationPoints: List<String> = emptyList(),
    val advantages: List<String> = emptyList(),
    val disadvantages: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val efficiencyComparison: EfficiencyComparison? = null,
    val rankedStableCandidates: List<GridCandidate> = emptyList()
)

data class ActiveRecordingSession(
    val startTimestampMillis: Long,
    val deviceIp: String,
    val frequencyMhz: Int,
    val voltageMv: Int,
    val samples: List<LiveTelemetry>,
    val disconnectCount: Int = 0
) {
    val elapsedSeconds: Long
        get() = ((System.currentTimeMillis() - startTimestampMillis) / 1000L).coerceAtLeast(0L)
}

data class ConfigVerificationResult(
    val verified: Boolean,
    val httpSuccess: Boolean,
    val httpCode: Int?,
    val requestedFrequencyMhz: Int,
    val requestedVoltageMv: Int,
    val actualFrequencyMhz: Int?,
    val actualVoltageMv: Int?,
    val actualCoreVoltageMv: Int?,
    val telemetryAfterApply: LiveTelemetry?,
    val message: String
)

data class ExplorationRangeConfig(
    val minFrequencyMhz: Int = 600,
    val maxFrequencyMhz: Int = 670,
    val minVoltageMv: Int = 1100,
    val maxVoltageMv: Int = 1170,
    val maxExperimentsBudget: Int = 8,
    val observationSeconds: Long = 60L,
    val stabilizationSeconds: Long = 10L
)

data class RangeValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val frequencyCount: Int = 0,
    val voltageCount: Int = 0,
    val totalCombinations: Int = 0
)

enum class ManualControlStatus {
    IDLE,
    VALIDATING,
    APPLYING,
    VERIFYING,
    VERIFIED_SUCCESS,
    VERIFICATION_MISMATCH,
    REJECTED_SAFETY,
    FAILED
}

data class ManualControlUiState(
    val proposedFrequencyMhz: Int = 625,
    val proposedVoltageMv: Int = 1150,
    val frequencyText: String = "625",
    val voltageText: String = "1150",
    val status: ManualControlStatus = ManualControlStatus.IDLE,
    val statusMessage: String? = null,
    val lastVerification: ConfigVerificationResult? = null,
    val showApplyConfirmation: Boolean = false,
    val showRestoreConfirmation: Boolean = false
)

enum class AiAutoPhase {
    IDLE,
    ESTABLISHING_BASELINE,
    SELECTING_CANDIDATE,
    APPLYING_CONFIG,
    STABILIZING,
    OBSERVING,
    PAUSED,
    ROLLING_BACK,
    COMPLETED,
    SAFETY_ABORTED,
    STOPPED
}

data class AiAutoExperimentLogItem(
    val index: Int,
    val frequencyMhz: Int,
    val voltageMv: Int,
    val result: String,
    val avgHashrateThs: Double?,
    val avgPowerWatts: Double?,
    val efficiencyJth: Double?,
    val asicTempC: Double?,
    val errorPct: Double?,
    val detail: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class AiAutoSessionState(
    val phase: AiAutoPhase = AiAutoPhase.IDLE,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val currentExperimentNumber: Int = 0,
    val totalPlannedExperiments: Int = 0,
    val currentTargetFreqMhz: Int? = null,
    val currentTargetVoltMv: Int? = null,
    val baselineRecord: ExperimentRecord? = null,
    val bestStableRecord: ExperimentRecord? = null,
    val activeExperimentSamples: List<LiveTelemetry> = emptyList(),
    val phaseProgress: Float = 0f,
    val statusBanner: String? = null,
    val rollbackNotice: String? = null,
    val logs: List<AiAutoExperimentLogItem> = emptyList()
)

enum class SafetyViolationType {
    OVERHEAT_ASIC,
    OVERHEAT_VR,
    FIRMWARE_OVERHEAT_MODE,
    POWER_FAULT,
    ABNORMAL_ASIC_ERRORS,
    DEVICE_RESTART_DETECTED,
    HASHRATE_COLLAPSE,
    NETWORK_INTERRUPTION,
    TELEMETRY_TIMEOUT,
    INVALID_LIMITS
}

data class SafetyViolation(
    val type: SafetyViolationType,
    val message: String,
    val critical: Boolean = true
)

data class SafetyAssessment(
    val isSafe: Boolean,
    val violations: List<SafetyViolation> = emptyList()
)
