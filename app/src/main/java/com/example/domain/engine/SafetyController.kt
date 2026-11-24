package com.example.domain.engine

import com.example.domain.model.AsicCapabilities
import com.example.domain.model.ExperimentRecord
import com.example.domain.model.LiveTelemetry
import com.example.domain.model.SafetyAssessment
import com.example.domain.model.SafetyThresholdConfig
import com.example.domain.model.SafetyViolation
import com.example.domain.model.SafetyViolationType

/**
 * Mandatory independent SafetyController.
 * Enforces hardware limits, firmware capabilities, thermal bounds, ASIC error caps,
 * unexpected restart detection, hashrate collapse detection, and rollback target selection.
 */
object SafetyController {

    fun validateTargetSetting(
        frequencyMhz: Int,
        voltageMv: Int,
        capabilities: AsicCapabilities?,
        liveTelemetry: LiveTelemetry?,
        thresholds: SafetyThresholdConfig
    ): SafetyAssessment {
        val violations = mutableListOf<SafetyViolation>()

        if (frequencyMhz !in BitaxeOptimizationEngine.MIN_FREQUENCY_MHZ..BitaxeOptimizationEngine.MAX_FREQUENCY_MHZ) {
            violations += SafetyViolation(
                SafetyViolationType.INVALID_LIMITS,
                "Frequency $frequencyMhz MHz outside verified range (${BitaxeOptimizationEngine.MIN_FREQUENCY_MHZ}–${BitaxeOptimizationEngine.MAX_FREQUENCY_MHZ} MHz)."
            )
        }
        if ((frequencyMhz - BitaxeOptimizationEngine.MIN_FREQUENCY_MHZ) % BitaxeOptimizationEngine.FREQUENCY_STEP_MHZ != 0) {
            violations += SafetyViolation(
                SafetyViolationType.INVALID_LIMITS,
                "Frequency $frequencyMhz MHz must align with exact 5 MHz increments."
            )
        }
        if (voltageMv !in BitaxeOptimizationEngine.MIN_VOLTAGE_MV..BitaxeOptimizationEngine.MAX_VOLTAGE_MV) {
            violations += SafetyViolation(
                SafetyViolationType.INVALID_LIMITS,
                "Voltage $voltageMv mV outside verified range (${BitaxeOptimizationEngine.MIN_VOLTAGE_MV}–${BitaxeOptimizationEngine.MAX_VOLTAGE_MV} mV)."
            )
        }
        if ((voltageMv - BitaxeOptimizationEngine.MIN_VOLTAGE_MV) % BitaxeOptimizationEngine.VOLTAGE_STEP_MV != 0) {
            violations += SafetyViolation(
                SafetyViolationType.INVALID_LIMITS,
                "Voltage $voltageMv mV must align with exact 5 mV increments."
            )
        }

        if (!BitaxeOptimizationEngine.isWithinFirmwareLimits(frequencyMhz, voltageMv, capabilities)) {
            violations += SafetyViolation(
                SafetyViolationType.INVALID_LIMITS,
                "Requested $frequencyMhz MHz / $voltageMv mV exceeds firmware /api/system/asic limits."
            )
        }

        liveTelemetry?.let { live ->
            val tickCheck = assessLiveTelemetry(
                current = live,
                previousUptimeSeconds = null,
                baselineHashrateThs = null,
                consecutiveTimeouts = 0,
                thresholds = thresholds,
                enforceHashrateCollapse = false
            )
            violations += tickCheck.violations
        }

        return SafetyAssessment(
            isSafe = violations.isEmpty(),
            violations = violations
        )
    }

    /**
     * Evaluates a live telemetry tick during Manual verification or AI Auto Mode execution.
     */
    fun assessLiveTelemetry(
        current: LiveTelemetry,
        previousUptimeSeconds: Long?,
        baselineHashrateThs: Double?,
        consecutiveTimeouts: Int,
        thresholds: SafetyThresholdConfig,
        enforceHashrateCollapse: Boolean = true
    ): SafetyAssessment {
        val violations = mutableListOf<SafetyViolation>()

        if (current.overheatMode) {
            violations += SafetyViolation(
                SafetyViolationType.FIRMWARE_OVERHEAT_MODE,
                "ESP-Miner reports active overheat_mode protection."
            )
        }

        if (!current.powerFault.isNullOrBlank()) {
            violations += SafetyViolation(
                SafetyViolationType.POWER_FAULT,
                "Device reported power fault: ${current.powerFault}"
            )
        }

        current.asicTempC?.let { temp ->
            if (temp >= thresholds.maxAsicTempHardLimitC) {
                violations += SafetyViolation(
                    SafetyViolationType.OVERHEAT_ASIC,
                    "ASIC temperature ${"%.1f".format(temp)}°C reached hard safety limit (${thresholds.maxAsicTempHardLimitC}°C)."
                )
            }
        }

        current.vrTempC?.let { vr ->
            if (vr >= thresholds.maxVrTempHardLimitC) {
                violations += SafetyViolation(
                    SafetyViolationType.OVERHEAT_VR,
                    "VR temperature ${"%.1f".format(vr)}°C reached hard safety limit (${thresholds.maxVrTempHardLimitC}°C)."
                )
            }
        }

        current.asicErrorPct?.let { err ->
            if (err > thresholds.maxAsicErrorPct) {
                violations += SafetyViolation(
                    SafetyViolationType.ABNORMAL_ASIC_ERRORS,
                    "ASIC error rate ${"%.2f".format(err)}% exceeded max allowed (${thresholds.maxAsicErrorPct}%)."
                )
            }
        }

        // Unexpected device restart detection: uptime dropped significantly compared to previous tick
        val curUp = current.uptimeSeconds
        if (previousUptimeSeconds != null && curUp != null &&
            previousUptimeSeconds >= 30L && curUp + 10L < previousUptimeSeconds
        ) {
            violations += SafetyViolation(
                SafetyViolationType.DEVICE_RESTART_DETECTED,
                "Unexpected device restart detected (uptime dropped from ${previousUptimeSeconds}s to ${curUp}s)."
            )
        }

        // Hashrate collapse detection during observation (after stabilization)
        if (enforceHashrateCollapse && baselineHashrateThs != null && baselineHashrateThs > 0.2) {
            val curHash = current.currentHashrateThs
            if (curHash != null && curHash < baselineHashrateThs * 0.45) {
                violations += SafetyViolation(
                    SafetyViolationType.HASHRATE_COLLAPSE,
                    "Hashrate collapsed to ${"%.3f".format(curHash)} TH/s (< 45% of baseline ${"%.3f".format(baselineHashrateThs)} TH/s)."
                )
            }
        }

        if (consecutiveTimeouts > thresholds.maxDisconnectsAllowed) {
            violations += SafetyViolation(
                SafetyViolationType.TELEMETRY_TIMEOUT,
                "Telemetry timed out $consecutiveTimeouts consecutive times."
            )
        }

        return SafetyAssessment(
            isSafe = violations.isEmpty(),
            violations = violations
        )
    }

    /**
     * Resolves the safest verified configuration to restore during emergency rollback.
     * Priority:
     * 1. Explicit lastKnownStable from historical DB
     * 2. Most recent verified stable ExperimentRecord
     * 3. Baseline captured before AI Auto Mode began
     * 4. Firmware defaultFrequency / defaultVoltage if available
     */
    fun selectRollbackTarget(
        history: List<ExperimentRecord>,
        baseline: ExperimentRecord?,
        capabilities: AsicCapabilities?
    ): Pair<Int, Int>? {
        val lastKnown = history.firstOrNull { it.isLastKnownStable && it.isStable }
        if (lastKnown != null) return lastKnown.frequencyMhz to lastKnown.voltageMv

        val recentStable = history.filter { it.isStable }.maxByOrNull { it.timestampMillis }
        if (recentStable != null) return recentStable.frequencyMhz to recentStable.voltageMv

        if (baseline != null) return baseline.frequencyMhz to baseline.voltageMv

        val defF = capabilities?.defaultFrequencyMhz
        val defV = capabilities?.defaultVoltageMv
        if (defF != null && defV != null) return defF to defV

        return null
    }
}
