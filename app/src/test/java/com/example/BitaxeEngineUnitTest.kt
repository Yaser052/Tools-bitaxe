package com.example

import com.example.domain.engine.BitaxeOptimizationEngine
import com.example.domain.engine.EfficiencyCalculator
import com.example.domain.engine.IpInvalidReason
import com.example.domain.engine.IpValidationResult
import com.example.domain.engine.NetworkValidator
import com.example.domain.engine.SafetyController
import com.example.domain.model.AnalysisProfile
import com.example.domain.model.AsicCapabilities
import com.example.domain.model.CandidateCellStatus
import com.example.domain.model.ExperimentRecord
import com.example.domain.model.ExplorationRangeConfig
import com.example.domain.model.LiveTelemetry
import com.example.domain.model.SafetyThresholdConfig
import com.example.domain.model.SafetyViolationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BitaxeEngineUnitTest {

    @Test
    fun `frequency and voltage precision grids generate exact 5-unit steps and 225 combinations`() {
        val expectedFreqs = listOf(600, 605, 610, 615, 620, 625, 630, 635, 640, 645, 650, 655, 660, 665, 670)
        val expectedVolts = listOf(1100, 1105, 1110, 1115, 1120, 1125, 1130, 1135, 1140, 1145, 1150, 1155, 1160, 1165, 1170)

        assertEquals(expectedFreqs, BitaxeOptimizationEngine.FREQUENCY_VALUES)
        assertEquals(expectedVolts, BitaxeOptimizationEngine.VOLTAGE_VALUES)

        val grid = BitaxeOptimizationEngine.buildPrecisionGrid(
            history = emptyList(),
            currentTelemetry = null,
            capabilities = null,
            thresholds = SafetyThresholdConfig(),
            profile = AnalysisProfile.BALANCED
        )
        assertEquals(225, grid.size)
        assertTrue(grid.any { it.frequencyMhz == 605 && it.voltageMv == 1105 })
        assertTrue(grid.any { it.frequencyMhz == 665 && it.voltageMv == 1165 })
    }

    @Test
    fun `range validation enforces min less than max, exact 5-unit steps, and firmware limits`() {
        val valid = BitaxeOptimizationEngine.validateExplorationRange(
            ExplorationRangeConfig(610, 645, 1115, 1155),
            AsicCapabilities(absMaxFrequencyMhz = 670, absMaxVoltageMv = 1170)
        )
        assertTrue(valid.isValid)
        assertEquals(8, valid.frequencyCount)
        assertEquals(9, valid.voltageCount)
        assertEquals(72, valid.totalCombinations)

        val badStep = BitaxeOptimizationEngine.validateExplorationRange(
            ExplorationRangeConfig(603, 645, 1110, 1152),
            null
        )
        assertFalse(badStep.isValid)
        assertTrue(badStep.errors.size >= 2)

        val inverted = BitaxeOptimizationEngine.validateExplorationRange(
            ExplorationRangeConfig(650, 620, 1150, 1120),
            null
        )
        assertFalse(inverted.isValid)

        val exceedsFw = BitaxeOptimizationEngine.validateExplorationRange(
            ExplorationRangeConfig(600, 670, 1100, 1170),
            AsicCapabilities(absMaxFrequencyMhz = 650, absMaxVoltageMv = 1160)
        )
        assertFalse(exceedsFw.isValid)
    }

    @Test
    fun `candidate generation in range excludes unsafe historical configs`() {
        val unsafePoint = sampleRecord(
            id = 9, freq = 625, volt = 1120, hash = 1.15, power = 19.0, temp = 75.0, err = 5.0
        ).copy(isStable = false)

        val candidates = BitaxeOptimizationEngine.generateEligibleCandidatesInRange(
            range = ExplorationRangeConfig(620, 630, 1115, 1125),
            capabilities = null,
            history = listOf(unsafePoint),
            thresholds = SafetyThresholdConfig()
        )
        // 3 freqs x 3 volts = 9 minus 1 unsafe = 8
        assertEquals(8, candidates.size)
        assertFalse((625 to 1120) in candidates)
        assertTrue((625 to 1125) in candidates)
    }

    @Test
    fun `efficiency calculator converts GHs to THs and computes exact J per TH`() {
        val ths = EfficiencyCalculator.hashrateGhsToThs(1200.0)
        assertNotNull(ths)
        assertEquals(1.20, ths!!, 0.0001)

        val jth = EfficiencyCalculator.calculateEfficiencyJth(18.7, ths)
        assertNotNull(jth)
        assertEquals(15.5833, jth!!, 0.01)

        assertNull(EfficiencyCalculator.calculateEfficiencyJth(18.7, 0.0))
        assertNull(EfficiencyCalculator.calculateEfficiencyJth(null, 1.2))
    }

    @Test
    fun `network validator accepts private LAN IPs and blocks public internet IPs`() {
        val valid = NetworkValidator.validateLocalMinerAddress("http://192.168.1.100/")
        assertTrue(valid is IpValidationResult.Valid)
        assertEquals("192.168.1.100", (valid as IpValidationResult.Valid).normalizedHost)

        val mdns = NetworkValidator.validateLocalMinerAddress("bitaxe-gamma.local")
        assertTrue(mdns is IpValidationResult.Valid)

        val publicIp = NetworkValidator.validateLocalMinerAddress("8.8.8.8")
        assertTrue(publicIp is IpValidationResult.Invalid)
        assertEquals(IpInvalidReason.PUBLIC_INTERNET_BLOCKED, (publicIp as IpValidationResult.Invalid).reasonKey)
    }

    @Test
    fun `safety controller detects overheat, errors, unexpected restart, collapse, and rejects unsafe settings`() {
        val thresholds = SafetyThresholdConfig(maxAsicTempHardLimitC = 70.0, maxAsicErrorPct = 3.0)

        // Pre-apply rejection for invalid non-5-unit step
        val badStep = SafetyController.validateTargetSetting(
            frequencyMhz = 622,
            voltageMv = 1150,
            capabilities = null,
            liveTelemetry = null,
            thresholds = thresholds
        )
        assertFalse(badStep.isSafe)
        assertTrue(badStep.violations.any { it.type == SafetyViolationType.INVALID_LIMITS })

        // Live tick detecting unexpected restart + overheat + hashrate collapse + errors
        val badTick = LiveTelemetry(
            deviceIp = "192.168.1.100",
            asicTempC = 72.5,
            asicErrorPct = 4.2,
            currentHashrateThs = 0.30,
            uptimeSeconds = 5L
        )
        val assessment = SafetyController.assessLiveTelemetry(
            current = badTick,
            previousUptimeSeconds = 180L,
            baselineHashrateThs = 1.20,
            consecutiveTimeouts = 0,
            thresholds = thresholds,
            enforceHashrateCollapse = true
        )
        assertFalse(assessment.isSafe)
        val types = assessment.violations.map { it.type }.toSet()
        assertTrue(SafetyViolationType.OVERHEAT_ASIC in types)
        assertTrue(SafetyViolationType.ABNORMAL_ASIC_ERRORS in types)
        assertTrue(SafetyViolationType.DEVICE_RESTART_DETECTED in types)
        assertTrue(SafetyViolationType.HASHRATE_COLLAPSE in types)
    }

    @Test
    fun `safety priority blocks overheating high-hashrate config from recommendation`() {
        val thresholds = SafetyThresholdConfig(maxAsicTempHardLimitC = 70.0, minObservationSeconds = 60L, minSampleCount = 10)
        val current = sampleRecord(id = 1, freq = 625, volt = 1150, hash = 1.20, power = 18.7, temp = 59.0, err = 0.2)
        val stableGood = sampleRecord(id = 2, freq = 630, volt = 1145, hash = 1.24, power = 18.4, temp = 60.5, err = 0.3)
        val unsafeHot = sampleRecord(id = 3, freq = 665, volt = 1170, hash = 1.42, power = 24.5, temp = 74.0, err = 4.5)

        val live = LiveTelemetry(deviceIp = "192.168.1.100", frequencyMhz = 625, coreVoltageTargetMv = 1150)

        val rec = BitaxeOptimizationEngine.recommend(
            history = listOf(current, stableGood, unsafeHot),
            currentTelemetry = live,
            capabilities = AsicCapabilities(absMaxFrequencyMhz = 670, absMaxVoltageMv = 1170),
            thresholds = thresholds,
            profile = AnalysisProfile.BEAST
        )

        assertFalse(rec.isInsufficientData)
        assertEquals(630, rec.recommendedConfig?.frequencyMhz)
        assertEquals(1145, rec.recommendedConfig?.voltageMv)
    }

    @Test
    fun `configuration verification confirms exact match and detects mismatch`() {
        val telemetryMatch = LiveTelemetry(
            deviceIp = "192.168.1.100",
            frequencyMhz = 635,
            coreVoltageTargetMv = 1145,
            coreVoltageActualMv = 1144
        )
        val matchFreq = telemetryMatch.frequencyMhz == 635
        val matchVolt = telemetryMatch.coreVoltageTargetMv == 1145 ||
            (telemetryMatch.coreVoltageTargetMv == null && abs((telemetryMatch.coreVoltageActualMv ?: 0) - 1145) <= 10)
        assertTrue(matchFreq && matchVolt)

        val telemetryMismatch = LiveTelemetry(
            deviceIp = "192.168.1.100",
            frequencyMhz = 625,
            coreVoltageTargetMv = 1150
        )
        assertFalse(telemetryMismatch.frequencyMhz == 635 && telemetryMismatch.coreVoltageTargetMv == 1145)
    }

    @Test
    fun `rollback target selection prioritizes last known stable over baseline and defaults`() {
        val lastKnown = sampleRecord(id = 10, freq = 630, volt = 1140, hash = 1.23, power = 18.2, temp = 58.0, err = 0.1)
            .copy(isLastKnownStable = true, isStable = true)
        val baseline = sampleRecord(id = 11, freq = 625, volt = 1150, hash = 1.20, power = 18.7, temp = 59.0, err = 0.2)
            .copy(isLastKnownStable = false, isStable = true)
        val caps = AsicCapabilities(defaultFrequencyMhz = 600, defaultVoltageMv = 1150)

        assertEquals(630 to 1140, SafetyController.selectRollbackTarget(listOf(baseline, lastKnown), baseline, caps))
        assertEquals(625 to 1150, SafetyController.selectRollbackTarget(emptyList(), baseline, caps))
        assertEquals(600 to 1150, SafetyController.selectRollbackTarget(emptyList(), null, caps))
        assertNull(SafetyController.selectRollbackTarget(emptyList(), null, null))
    }

    @Test
    fun `eco mode prefers lower J per TH while beast mode prefers peak stable TH per s`() {
        val ecoFriendly = sampleRecord(id = 1, freq = 605, volt = 1110, hash = 1.15, power = 16.5, temp = 55.0, err = 0.1)
        val highHash = sampleRecord(id = 2, freq = 655, volt = 1160, hash = 1.32, power = 21.8, temp = 64.0, err = 0.3)
        val live = LiveTelemetry(deviceIp = "192.168.1.100", frequencyMhz = 605, coreVoltageTargetMv = 1110)

        val ecoRec = BitaxeOptimizationEngine.recommend(
            history = listOf(ecoFriendly, highHash),
            currentTelemetry = live,
            capabilities = null,
            thresholds = SafetyThresholdConfig(),
            profile = AnalysisProfile.ECO
        )
        assertEquals(605, ecoRec.recommendedConfig?.frequencyMhz)

        val beastRec = BitaxeOptimizationEngine.recommend(
            history = listOf(ecoFriendly, highHash),
            currentTelemetry = live,
            capabilities = null,
            thresholds = SafetyThresholdConfig(),
            profile = AnalysisProfile.BEAST
        )
        assertEquals(655, beastRec.recommendedConfig?.frequencyMhz)
    }

    @Test
    fun `moshi parses real ESP-Miner system info, asic, and patch payloads`() {
        val moshi = com.squareup.moshi.Moshi.Builder()
            .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        val infoAdapter = moshi.adapter(com.example.data.network.SystemInfoDto::class.java)
        val patchAdapter = moshi.adapter(com.example.data.network.SystemConfigPatchRequest::class.java)

        val infoJson = """
            {
              "power": 18.7,
              "temp": 59.5,
              "vrTemp": 67.2,
              "hashRate": 1200.0,
              "frequency": 625,
              "coreVoltage": 1150,
              "coreVoltageActual": 1148,
              "ASICModel": "BM1370",
              "boardVersion": "601",
              "deviceModel": "Gamma",
              "sharesAccepted": 140,
              "sharesRejected": 1,
              "version": "v2.6.0",
              "uptimeSeconds": 3600
            }
        """.trimIndent()
        val info = infoAdapter.fromJson(infoJson)
        assertNotNull(info)
        assertEquals("BM1370", info!!.asicModel)
        assertEquals("601", info.boardVersion)
        assertNull(info.staleShares)

        val serializedPatch = patchAdapter.toJson(
            com.example.data.network.SystemConfigPatchRequest(frequency = 635, coreVoltage = 1145)
        )
        assertTrue(serializedPatch.contains("\"frequency\":635"))
        assertTrue(serializedPatch.contains("\"coreVoltage\":1145"))
    }

    private fun sampleRecord(
        id: Long,
        freq: Int,
        volt: Int,
        hash: Double,
        power: Double,
        temp: Double,
        err: Double
    ): ExperimentRecord {
        return ExperimentRecord(
            id = id,
            timestampMillis = 1_700_000_000_000L + id * 10_000L,
            endTimestampMillis = 1_700_000_000_000L + id * 10_000L + 120_000L,
            deviceIp = "192.168.1.100",
            deviceIdentifier = "AA:BB:CC:DD:EE:FF",
            deviceModel = "Bitaxe Gamma",
            boardVersion = "601",
            firmwareVersion = "v2.6.0",
            frequencyMhz = freq,
            voltageMv = volt,
            avgHashrateThs = hash,
            minHashrateThs = hash - 0.03,
            maxHashrateThs = hash + 0.03,
            avgPowerWatts = power,
            efficiencyJth = power / hash,
            avgAsicTempC = temp,
            avgVrTempC = temp + 6.0,
            avgAsicErrorPct = err,
            acceptedShares = 42,
            rejectedShares = 0,
            staleShares = 0,
            sampleCount = 30,
            disconnectCount = 0,
            isStable = true,
            stabilityScore = 92.0,
            stabilityNotes = "Verified stable",
            durationSeconds = 120,
            isLastKnownStable = false
        )
    }
}
