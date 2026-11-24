package com.example

import com.example.domain.engine.BitaxeOptimizationEngine
import com.example.domain.engine.EfficiencyCalculator
import com.example.domain.engine.IpInvalidReason
import com.example.domain.engine.IpValidationResult
import com.example.domain.engine.NetworkValidator
import com.example.domain.model.AnalysisProfile
import com.example.domain.model.AsicCapabilities
import com.example.domain.model.CandidateCellStatus
import com.example.domain.model.ExperimentRecord
import com.example.domain.model.LiveTelemetry
import com.example.domain.model.SafetyThresholdConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun `safety priority blocks overheating high-hashrate config from recommendation`() {
        val thresholds = SafetyThresholdConfig(maxAsicTempHardLimitC = 70.0, minObservationSeconds = 60L, minSampleCount = 10)
        val current = sampleRecord(id = 1, freq = 625, volt = 1150, hash = 1.20, power = 18.7, temp = 59.0, err = 0.2)
        val stableGood = sampleRecord(id = 2, freq = 630, volt = 1145, hash = 1.24, power = 18.4, temp = 60.5, err = 0.3)
        // Higher hashrate, but unsafe ASIC temperature (74°C > 70°C)
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
    fun `insufficient measurement data triggers explicit warning instead of inventing recommendation`() {
        val single = sampleRecord(id = 1, freq = 625, volt = 1150, hash = 1.20, power = 18.7, temp = 59.0, err = 0.2)
        val live = LiveTelemetry(deviceIp = "192.168.1.100", frequencyMhz = 625, coreVoltageTargetMv = 1150)

        val rec = BitaxeOptimizationEngine.recommend(
            history = listOf(single),
            currentTelemetry = live,
            capabilities = null,
            thresholds = SafetyThresholdConfig(),
            profile = AnalysisProfile.BALANCED
        )

        assertTrue(rec.isInsufficientData)
        assertNull(rec.recommendedConfig)
        assertTrue(rec.insufficientDataReason!!.contains(BitaxeOptimizationEngine.INSUFFICIENT_DATA_MESSAGE))
        assertNotNull(rec.nextExperimentCandidate)
        assertEquals(CandidateCellStatus.CANDIDATE_NEXT, rec.nextExperimentCandidate?.status)
    }

    @Test
    fun `moshi parses real ESP-Miner system info and asic payloads with missing optional fields`() {
        val moshi = com.squareup.moshi.Moshi.Builder()
            .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        val infoAdapter = moshi.adapter(com.example.data.network.SystemInfoDto::class.java)
        val asicAdapter = moshi.adapter(com.example.data.network.SystemAsicDto::class.java)

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
        assertEquals(625.0, info.frequency!!, 0.01)
        assertEquals(1150, info.coreVoltage)
        assertNull(info.staleShares) // missing field stays null, never fabricated

        val asicJson = """
            {
              "ASICModel": "BM1370",
              "frequencyOptions": [500, 525, 550, 575, 600, 625, 650],
              "voltageOptions": [1100, 1125, 1150, 1175],
              "absMaxFrequency": 670,
              "absMaxVoltage": 1170
            }
        """.trimIndent()
        val asic = asicAdapter.fromJson(asicJson)
        assertNotNull(asic)
        assertEquals(670, asic!!.absMaxFrequency)
        assertEquals(1170, asic.absMaxVoltage)
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
