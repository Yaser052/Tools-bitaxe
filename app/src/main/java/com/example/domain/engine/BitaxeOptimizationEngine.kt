package com.example.domain.engine

import com.example.domain.model.AnalysisProfile
import com.example.domain.model.AsicCapabilities
import com.example.domain.model.CandidateCellStatus
import com.example.domain.model.ExperimentRecord
import com.example.domain.model.ExplorationRangeConfig
import com.example.domain.model.GridCandidate
import com.example.domain.model.LiveTelemetry
import com.example.domain.model.OptimizationRecommendation
import com.example.domain.model.RangeValidationResult
import com.example.domain.model.SafetyThresholdConfig
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

object BitaxeOptimizationEngine {

    const val MIN_FREQUENCY_MHZ = 600
    const val MAX_FREQUENCY_MHZ = 670
    const val FREQUENCY_STEP_MHZ = 5

    const val MIN_VOLTAGE_MV = 1100
    const val MAX_VOLTAGE_MV = 1170
    const val VOLTAGE_STEP_MV = 5

    // Exact 15 frequency steps: 600, 605, ..., 670 MHz
    val FREQUENCY_VALUES: List<Int> =
        (MIN_FREQUENCY_MHZ..MAX_FREQUENCY_MHZ step FREQUENCY_STEP_MHZ).toList()

    // Exact 15 voltage steps: 1100, 1105, ..., 1170 mV
    val VOLTAGE_VALUES: List<Int> =
        (MIN_VOLTAGE_MV..MAX_VOLTAGE_MV step VOLTAGE_STEP_MV).toList()

    const val TOTAL_THEORETICAL_COMBINATIONS = 225

    const val INSUFFICIENT_DATA_MESSAGE =
        "Insufficient measurement data to generate a reliable recommendation."

    /**
     * Validates user-defined exploration ranges for AI Auto Mode.
     * Checks: min < max, exact 5-unit step alignment, global hardware bounds, and firmware capabilities.
     */
    fun validateExplorationRange(
        range: ExplorationRangeConfig,
        capabilities: AsicCapabilities?
    ): RangeValidationResult {
        val errors = mutableListOf<String>()

        if (range.minFrequencyMhz !in MIN_FREQUENCY_MHZ..MAX_FREQUENCY_MHZ) {
            errors += "Minimum frequency (${range.minFrequencyMhz} MHz) must be within $MIN_FREQUENCY_MHZ–$MAX_FREQUENCY_MHZ MHz."
        }
        if (range.maxFrequencyMhz !in MIN_FREQUENCY_MHZ..MAX_FREQUENCY_MHZ) {
            errors += "Maximum frequency (${range.maxFrequencyMhz} MHz) must be within $MIN_FREQUENCY_MHZ–$MAX_FREQUENCY_MHZ MHz."
        }
        if ((range.minFrequencyMhz - MIN_FREQUENCY_MHZ) % FREQUENCY_STEP_MHZ != 0) {
            errors += "Minimum frequency (${range.minFrequencyMhz} MHz) must align with exact 5 MHz steps."
        }
        if ((range.maxFrequencyMhz - MIN_FREQUENCY_MHZ) % FREQUENCY_STEP_MHZ != 0) {
            errors += "Maximum frequency (${range.maxFrequencyMhz} MHz) must align with exact 5 MHz steps."
        }
        if (range.minFrequencyMhz >= range.maxFrequencyMhz) {
            errors += "Minimum frequency must be strictly less than maximum frequency."
        }

        if (range.minVoltageMv !in MIN_VOLTAGE_MV..MAX_VOLTAGE_MV) {
            errors += "Minimum voltage (${range.minVoltageMv} mV) must be within $MIN_VOLTAGE_MV–$MAX_VOLTAGE_MV mV."
        }
        if (range.maxVoltageMv !in MIN_VOLTAGE_MV..MAX_VOLTAGE_MV) {
            errors += "Maximum voltage (${range.maxVoltageMv} mV) must be within $MIN_VOLTAGE_MV–$MAX_VOLTAGE_MV mV."
        }
        if ((range.minVoltageMv - MIN_VOLTAGE_MV) % VOLTAGE_STEP_MV != 0) {
            errors += "Minimum voltage (${range.minVoltageMv} mV) must align with exact 5 mV steps."
        }
        if ((range.maxVoltageMv - MIN_VOLTAGE_MV) % VOLTAGE_STEP_MV != 0) {
            errors += "Maximum voltage (${range.maxVoltageMv} mV) must align with exact 5 mV steps."
        }
        if (range.minVoltageMv >= range.maxVoltageMv) {
            errors += "Minimum voltage must be strictly less than maximum voltage."
        }

        capabilities?.absMaxFrequencyMhz?.let { fwMaxF ->
            if (range.maxFrequencyMhz > fwMaxF) {
                errors += "Max frequency (${range.maxFrequencyMhz} MHz) exceeds firmware absMaxFrequency ($fwMaxF MHz)."
            }
        }
        capabilities?.absMaxVoltageMv?.let { fwMaxV ->
            if (range.maxVoltageMv > fwMaxV) {
                errors += "Max voltage (${range.maxVoltageMv} mV) exceeds firmware absMaxVoltage ($fwMaxV mV)."
            }
        }

        if (range.maxExperimentsBudget < 1 || range.maxExperimentsBudget > 50) {
            errors += "Experiment budget must be between 1 and 50."
        }
        if (range.observationSeconds < 30L) {
            errors += "Observation window must be at least 30 seconds."
        }

        if (errors.isNotEmpty()) {
            return RangeValidationResult(isValid = false, errors = errors)
        }

        val freqCount = ((range.maxFrequencyMhz - range.minFrequencyMhz) / FREQUENCY_STEP_MHZ) + 1
        val voltCount = ((range.maxVoltageMv - range.minVoltageMv) / VOLTAGE_STEP_MV) + 1
        return RangeValidationResult(
            isValid = true,
            errors = emptyList(),
            frequencyCount = freqCount,
            voltageCount = voltCount,
            totalCombinations = freqCount * voltCount
        )
    }

    /**
     * Generates all eligible 5 MHz x 5 mV candidate settings inside the user-defined ranges,
     * removing firmware-unsupported or historically unsafe configurations.
     */
    fun generateEligibleCandidatesInRange(
        range: ExplorationRangeConfig,
        capabilities: AsicCapabilities?,
        history: List<ExperimentRecord>,
        thresholds: SafetyThresholdConfig
    ): List<Pair<Int, Int>> {
        val unsafeKnown = history
            .filter { !it.isStable || evaluateSafetyIssues(it, capabilities, thresholds).isNotEmpty() }
            .map { it.frequencyMhz to it.voltageMv }
            .toSet()

        val freqs = (range.minFrequencyMhz..range.maxFrequencyMhz step FREQUENCY_STEP_MHZ).toList()
        val volts = (range.minVoltageMv..range.maxVoltageMv step VOLTAGE_STEP_MV).toList()

        val result = mutableListOf<Pair<Int, Int>>()
        for (f in freqs) {
            for (v in volts) {
                if (isWithinFirmwareLimits(f, v, capabilities) && (f to v) !in unsafeKnown) {
                    result += (f to v)
                }
            }
        }
        return result
    }

    /**
     * Staged data-driven candidate selector for AI Auto Mode.
     * Avoids brute-force random jumps by prioritizing nearby 5 MHz / 5 mV steps from the best
     * known stable anchor, steered by the active Optimization Profile (ECO / BALANCED / BEAST).
     */
    fun selectNextAutoCandidate(
        eligibleCandidates: List<Pair<Int, Int>>,
        testedInSession: Set<Pair<Int, Int>>,
        anchorFreqMhz: Int,
        anchorVoltMv: Int,
        historicalRecords: List<ExperimentRecord>,
        profile: AnalysisProfile
    ): Pair<Int, Int>? {
        val untested = eligibleCandidates.filter { it !in testedInSession }
        if (untested.isEmpty()) return null

        val historicallyMeasured = historicalRecords.map { it.frequencyMhz to it.voltageMv }.toSet()

        // Prefer never-tested points first, then re-verify stale points if needed
        val freshPool = untested.filter { it !in historicallyMeasured }.ifEmpty { untested }

        // Compute profile-guided priority score for each candidate around the stable anchor
        return freshPool.sortedWith(
            compareBy<Pair<Int, Int>> { (f, v) ->
                val df = abs(f - anchorFreqMhz) / FREQUENCY_STEP_MHZ
                val dv = abs(v - anchorVoltMv) / VOLTAGE_STEP_MV
                val singleVarBonus = if (df == 0 || dv == 0) 0 else 1
                // Prefer immediate 1-step neighbors first, then 2-step neighbors
                (df + dv) * 10 + singleVarBonus * 3
            }.thenBy { (f, v) ->
                when (profile) {
                    // ECO prioritizes lower voltage first, then modest frequency
                    AnalysisProfile.ECO -> (v - anchorVoltMv) * 2 + (f - anchorFreqMhz)
                    // BEAST prioritizes higher frequency first, then minimal necessary voltage
                    AnalysisProfile.BEAST -> -(f - anchorFreqMhz) * 2 + abs(v - anchorVoltMv)
                    // BALANCED explores slight frequency increase or undervolt symmetrically
                    AnalysisProfile.BALANCED -> (v - anchorVoltMv) - (f - anchorFreqMhz)
                }
            }
        ).firstOrNull()
    }

    fun evaluateSafetyIssues(
        record: ExperimentRecord,
        capabilities: AsicCapabilities?,
        thresholds: SafetyThresholdConfig
    ): List<String> {
        val issues = mutableListOf<String>()

        if (record.durationSeconds < thresholds.minObservationSeconds ||
            record.sampleCount < thresholds.minSampleCount
        ) {
            issues += "Observation window too short (${record.durationSeconds}s / ${record.sampleCount} samples; requires ${thresholds.minObservationSeconds}s & ${thresholds.minSampleCount} samples)"
        }

        if (record.disconnectCount > thresholds.maxDisconnectsAllowed) {
            issues += "Repeated disconnects detected (${record.disconnectCount})"
        }

        val asicTemp = record.avgAsicTempC
        if (asicTemp != null && asicTemp >= thresholds.maxAsicTempHardLimitC) {
            issues += "ASIC temperature ${"%.1f".format(asicTemp)}°C exceeds safe limit (${thresholds.maxAsicTempHardLimitC}°C)"
        }

        val vrTemp = record.avgVrTempC
        if (vrTemp != null && vrTemp >= thresholds.maxVrTempHardLimitC) {
            issues += "VR temperature ${"%.1f".format(vrTemp)}°C exceeds safe limit (${thresholds.maxVrTempHardLimitC}°C)"
        }

        val errPct = record.avgAsicErrorPct
        if (errPct != null && errPct > thresholds.maxAsicErrorPct) {
            issues += "Abnormal ASIC error rate ${"%.2f".format(errPct)}% > ${thresholds.maxAsicErrorPct}%"
        }

        if (!isWithinFirmwareLimits(record.frequencyMhz, record.voltageMv, capabilities)) {
            issues += "Setting ${record.frequencyMhz} MHz / ${record.voltageMv} mV outside firmware ASIC limits"
        }

        if (record.avgHashrateThs <= 0.0 || record.avgPowerWatts <= 0.0) {
            issues += "Invalid or zero hashrate/power telemetry"
        }

        if (record.avgHashrateThs > 0.0) {
            val spreadRatio = (record.maxHashrateThs - record.minHashrateThs) / record.avgHashrateThs
            if (spreadRatio > 0.45 && record.sampleCount >= 5) {
                issues += "High hashrate instability (spread ${(spreadRatio * 100).toInt()}%)"
            }
        }

        return issues
    }

    fun isWithinFirmwareLimits(
        frequencyMhz: Int,
        voltageMv: Int,
        capabilities: AsicCapabilities?
    ): Boolean {
        if (capabilities == null) return true
        val absMaxFreq = capabilities.absMaxFrequencyMhz
        if (absMaxFreq != null && frequencyMhz > absMaxFreq) return false

        val absMaxVolt = capabilities.absMaxVoltageMv
        if (absMaxVolt != null && voltageMv > absMaxVolt) return false

        if (capabilities.frequencyOptions.isNotEmpty() && absMaxFreq == null) {
            val maxOpt = capabilities.frequencyOptions.maxOrNull() ?: MAX_FREQUENCY_MHZ
            val minOpt = capabilities.frequencyOptions.minOrNull() ?: 400
            if (frequencyMhz > maxOpt || frequencyMhz < minOpt) return false
        }
        if (capabilities.voltageOptions.isNotEmpty() && absMaxVolt == null) {
            val maxOpt = capabilities.voltageOptions.maxOrNull() ?: MAX_VOLTAGE_MV
            val minOpt = capabilities.voltageOptions.minOrNull() ?: 1000
            if (voltageMv > maxOpt || voltageMv < minOpt) return false
        }
        return true
    }

    fun buildPrecisionGrid(
        history: List<ExperimentRecord>,
        currentTelemetry: LiveTelemetry?,
        capabilities: AsicCapabilities?,
        thresholds: SafetyThresholdConfig,
        profile: AnalysisProfile
    ): List<GridCandidate> {
        val groupedByConfig = history.groupBy { it.frequencyMhz to it.voltageMv }
            .mapValues { (_, records) -> aggregateRecords(records) }

        val activeFreq = currentTelemetry?.frequencyMhz
        val activeVolt = currentTelemetry?.coreVoltageTargetMv ?: currentTelemetry?.coreVoltageActualMv

        val anchorRecord = groupedByConfig.values
            .filter { it.isStable && evaluateSafetyIssues(it, capabilities, thresholds).isEmpty() }
            .maxByOrNull { it.timestampMillis }
            ?: if (activeFreq != null && activeVolt != null) {
                groupedByConfig[activeFreq to activeVolt]
            } else null

        val anchorFreq = anchorRecord?.frequencyMhz ?: activeFreq
        val anchorVolt = anchorRecord?.voltageMv ?: activeVolt

        val stableRecords = groupedByConfig.values.filter {
            it.isStable && evaluateSafetyIssues(it, capabilities, thresholds).isEmpty()
        }
        val scoredByKey = scoreStableRecords(stableRecords, profile)

        val candidates = ArrayList<GridCandidate>(TOTAL_THEORETICAL_COMBINATIONS)
        for (freq in FREQUENCY_VALUES) {
            for (volt in VOLTAGE_VALUES) {
                val key = freq to volt
                val record = groupedByConfig[key]
                val firmwareAllowed = isWithinFirmwareLimits(freq, volt, capabilities)
                val isActive = (activeFreq == freq && activeVolt == volt)

                val stepDistance = if (anchorFreq != null && anchorVolt != null) {
                    (abs(freq - anchorFreq) / FREQUENCY_STEP_MHZ) +
                        (abs(volt - anchorVolt) / VOLTAGE_STEP_MV)
                } else null

                val safetyIssues = if (record != null) {
                    evaluateSafetyIssues(record, capabilities, thresholds)
                } else if (!firmwareAllowed) {
                    listOf("Outside verified firmware ASIC limits")
                } else {
                    emptyList()
                }

                val isNextSingleStep = firmwareAllowed &&
                    record == null &&
                    anchorFreq != null &&
                    anchorVolt != null &&
                    ((abs(freq - anchorFreq) == FREQUENCY_STEP_MHZ && volt == anchorVolt) ||
                        (freq == anchorFreq && abs(volt - anchorVolt) == VOLTAGE_STEP_MV))

                val status = when {
                    !firmwareAllowed -> CandidateCellStatus.OUTSIDE_FIRMWARE_LIMIT
                    isActive -> CandidateCellStatus.CURRENT_ACTIVE
                    record != null && record.isStable && safetyIssues.isEmpty() ->
                        CandidateCellStatus.MEASURED_STABLE
                    record != null -> CandidateCellStatus.MEASURED_UNSTABLE
                    isNextSingleStep -> CandidateCellStatus.CANDIDATE_NEXT
                    else -> CandidateCellStatus.UNTESTED
                }

                candidates += GridCandidate(
                    frequencyMhz = freq,
                    voltageMv = volt,
                    status = status,
                    measuredRecord = record,
                    compositeScore = scoredByKey[key],
                    safetyIssues = safetyIssues,
                    stepDistanceFromBaseline = stepDistance
                )
            }
        }
        return candidates
    }

    fun scoreStableRecords(
        records: List<ExperimentRecord>,
        profile: AnalysisProfile
    ): Map<Pair<Int, Int>, Double> {
        if (records.isEmpty()) return emptyMap()

        val weights = when (profile) {
            AnalysisProfile.BALANCED -> ScoreWeights(0.35, 0.30, 0.20, 0.10, 0.05)
            AnalysisProfile.ECO -> ScoreWeights(0.05, 0.50, 0.20, 0.10, 0.15)
            AnalysisProfile.BEAST -> ScoreWeights(0.55, 0.10, 0.20, 0.10, 0.05)
        }

        val minHash = records.minOf { it.avgHashrateThs }
        val maxHash = records.maxOf { it.avgHashrateThs }

        val minEff = records.minOf { it.efficiencyJth }
        val maxEff = records.maxOf { it.efficiencyJth }

        val minStab = records.minOf { it.stabilityScore }
        val maxStab = records.maxOf { it.stabilityScore }

        val errs = records.mapNotNull { it.avgAsicErrorPct }
        val minErr = errs.minOrNull() ?: 0.0
        val maxErr = errs.maxOrNull() ?: 0.0

        val temps = records.mapNotNull { it.avgAsicTempC }
        val minTemp = temps.minOrNull() ?: 50.0
        val maxTemp = temps.maxOrNull() ?: 50.0

        return records.associate { rec ->
            val nHash = normalizeHigherBetter(rec.avgHashrateThs, minHash, maxHash)
            val nEff = normalizeLowerBetter(rec.efficiencyJth, minEff, maxEff)
            val nStab = normalizeHigherBetter(rec.stabilityScore, minStab, maxStab)
            val nErr = rec.avgAsicErrorPct?.let { normalizeLowerBetter(it, minErr, maxErr) } ?: 0.5
            val nTemp = rec.avgAsicTempC?.let { normalizeLowerBetter(it, minTemp, maxTemp) } ?: 0.5

            val score = (
                nHash * weights.hashrate +
                    nEff * weights.efficiency +
                    nStab * weights.stability +
                    nErr * weights.errorRate +
                    nTemp * weights.temperature
                ) * 100.0

            (rec.frequencyMhz to rec.voltageMv) to score
        }
    }

    fun recommend(
        history: List<ExperimentRecord>,
        currentTelemetry: LiveTelemetry?,
        capabilities: AsicCapabilities?,
        thresholds: SafetyThresholdConfig,
        profile: AnalysisProfile
    ): OptimizationRecommendation {
        val grid = buildPrecisionGrid(history, currentTelemetry, capabilities, thresholds, profile)
        val warnings = mutableListOf<String>()

        currentTelemetry?.let { live ->
            if (live.overheatMode) warnings += "Device reports active OVERHEAT protection mode."
            if (!live.powerFault.isNullOrBlank()) warnings += "Power fault reported: ${live.powerFault}"
            live.asicTempC?.let {
                if (it >= thresholds.maxAsicTempWarnC) {
                    warnings += "ASIC temperature (${"%.1f".format(it)}°C) is near or above warning threshold (${thresholds.maxAsicTempWarnC}°C)."
                }
            }
            live.vrTempC?.let {
                if (it >= thresholds.maxVrTempWarnC) {
                    warnings += "VR temperature (${"%.1f".format(it)}°C) is elevated."
                }
            }
            live.asicErrorPct?.let {
                if (it > thresholds.maxAsicErrorPct) {
                    warnings += "Current ASIC error rate (${"%.2f".format(it)}%) exceeds safe target (${thresholds.maxAsicErrorPct}%)."
                }
            }
        }

        val currentFreq = currentTelemetry?.frequencyMhz
        val currentVolt = currentTelemetry?.coreVoltageTargetMv ?: currentTelemetry?.coreVoltageActualMv

        val grouped = history.groupBy { it.frequencyMhz to it.voltageMv }
            .mapValues { (_, recs) -> aggregateRecords(recs) }

        val currentRecord = if (currentFreq != null && currentVolt != null) {
            grouped[currentFreq to currentVolt]
        } else null

        val stableCandidates = grid
            .filter {
                it.measuredRecord != null &&
                    it.measuredRecord.isStable &&
                    it.safetyIssues.isEmpty() &&
                    it.compositeScore != null
            }
            .sortedByDescending { it.compositeScore }

        val nextStepCandidate = selectNextControlledExperiment(
            grid = grid,
            currentFreq = currentFreq,
            currentVolt = currentVolt,
            profile = profile
        )

        if (stableCandidates.size < 2 || currentRecord == null) {
            val reason = when {
                history.isEmpty() ->
                    "$INSUFFICIENT_DATA_MESSAGE Connect to your Bitaxe and run AI Auto Mode or record at least 2 operating points (${thresholds.minObservationSeconds}s+ each)."
                stableCandidates.isEmpty() ->
                    "$INSUFFICIENT_DATA_MESSAGE Recorded sessions did not pass stability and safety thresholds."
                stableCandidates.size == 1 ->
                    "$INSUFFICIENT_DATA_MESSAGE Only 1 verified stable configuration (${stableCandidates.first().frequencyMhz} MHz / ${stableCandidates.first().voltageMv} mV) has been measured so far. Test a nearby 5 MHz or 5 mV step to compare."
                else ->
                    "$INSUFFICIENT_DATA_MESSAGE Current operating point (${currentFreq ?: "?"} MHz / ${currentVolt ?: "?"} mV) has not yet completed a full ${thresholds.minObservationSeconds}s baseline measurement."
            }

            return OptimizationRecommendation(
                profile = profile,
                isInsufficientData = true,
                insufficientDataReason = reason,
                currentConfig = currentRecord,
                recommendedConfig = null,
                compositeScore = null,
                confidencePercent = null,
                confidenceLabel = "UNAVAILABLE",
                nextExperimentCandidate = nextStepCandidate,
                warnings = warnings,
                rankedStableCandidates = stableCandidates
            )
        }

        val bestCandidate = stableCandidates.first()
        val bestRecord = bestCandidate.measuredRecord!!

        val minSamples = minOf(currentRecord.sampleCount, bestRecord.sampleCount)
        val minDuration = minOf(currentRecord.durationSeconds, bestRecord.durationSeconds)
        val confidence = calculateMeasuredConfidence(minSamples, minDuration, bestRecord.disconnectCount)
        val confidenceLabel = when {
            confidence >= 85 -> "HIGH (MEASURED)"
            confidence >= 65 -> "MODERATE (MEASURED)"
            else -> "EARLY (LIMITED SAMPLES)"
        }

        val prevRecord = history
            .filter { it.isStable && (it.frequencyMhz != bestRecord.frequencyMhz || it.voltageMv != bestRecord.voltageMv) }
            .maxByOrNull { it.timestampMillis }

        val effComparison = EfficiencyCalculator.buildComparison(
            currentEfficiencyJth = currentRecord.efficiencyJth,
            previousEfficiencyJth = prevRecord?.efficiencyJth,
            recommendedEfficiencyJth = bestRecord.efficiencyJth,
            currentPowerWatts = currentRecord.avgPowerWatts,
            recommendedPowerWatts = bestRecord.avgPowerWatts,
            currentHashrateThs = currentRecord.avgHashrateThs,
            recommendedHashrateThs = bestRecord.avgHashrateThs,
            recommendedIsMeasured = true
        )

        val explanations = buildList {
            add(
                "Based on ${bestRecord.sampleCount} real samples over ${bestRecord.durationSeconds}s at " +
                    "${bestRecord.frequencyMhz} MHz / ${bestRecord.voltageMv} mV under $profile mode."
            )
            val hashDiff = bestRecord.avgHashrateThs - currentRecord.avgHashrateThs
            val effDiff = bestRecord.efficiencyJth - currentRecord.efficiencyJth
            val powerDiff = bestRecord.avgPowerWatts - currentRecord.avgPowerWatts
            add(
                "Measured hashrate is ${"%.3f".format(bestRecord.avgHashrateThs)} TH/s " +
                    "(${formatSigned(hashDiff, 3)} TH/s vs current ${"%.3f".format(currentRecord.avgHashrateThs)} TH/s)."
            )
            add(
                "Measured efficiency is ${"%.2f".format(bestRecord.efficiencyJth)} J/TH " +
                    "(${formatSigned(effDiff, 2)} J/TH) drawing ${"%.2f".format(bestRecord.avgPowerWatts)} W (${formatSigned(powerDiff, 2)} W)."
            )
            bestRecord.avgAsicTempC?.let { t ->
                add("Average ASIC temperature remained ${"%.1f".format(t)}°C with stability score ${bestRecord.stabilityScore.toInt()}/100.")
            }
        }

        val advantages = mutableListOf<String>()
        val disadvantages = mutableListOf<String>()

        val hashDelta = bestRecord.avgHashrateThs - currentRecord.avgHashrateThs
        if (hashDelta > 0.005) advantages += "+${"%.3f".format(hashDelta)} TH/s higher measured hashrate"
        else if (hashDelta < -0.005) disadvantages += "${"%.3f".format(hashDelta)} TH/s lower measured hashrate"

        val effDelta = bestRecord.efficiencyJth - currentRecord.efficiencyJth
        if (effDelta < -0.05) advantages += "${"%.2f".format(-effDelta)} J/TH better energy efficiency"
        else if (effDelta > 0.05) disadvantages += "+${"%.2f".format(effDelta)} J/TH higher energy cost per TH"

        val powerDelta = bestRecord.avgPowerWatts - currentRecord.avgPowerWatts
        if (powerDelta < -0.2) advantages += "${"%.2f".format(-powerDelta)} W lower power draw"
        else if (powerDelta > 0.2) disadvantages += "+${"%.2f".format(powerDelta)} W higher power draw"

        val tempDelta = if (bestRecord.avgAsicTempC != null && currentRecord.avgAsicTempC != null) {
            bestRecord.avgAsicTempC - currentRecord.avgAsicTempC
        } else null
        if (tempDelta != null) {
            if (tempDelta < -0.5) advantages += "${"%.1f".format(-tempDelta)}°C cooler ASIC temperature"
            else if (tempDelta > 0.5) disadvantages += "+${"%.1f".format(tempDelta)}°C warmer ASIC temperature"
        }

        val errDelta = if (bestRecord.avgAsicErrorPct != null && currentRecord.avgAsicErrorPct != null) {
            bestRecord.avgAsicErrorPct - currentRecord.avgAsicErrorPct
        } else null
        if (errDelta != null) {
            if (errDelta < -0.05) advantages += "${"%.2f".format(-errDelta)}% fewer ASIC errors"
            else if (errDelta > 0.05) disadvantages += "+${"%.2f".format(errDelta)}% higher ASIC errors"
        }

        if (bestRecord.frequencyMhz == currentRecord.frequencyMhz && bestRecord.voltageMv == currentRecord.voltageMv) {
            advantages += "Current configuration is already the highest-scoring measured stable setting for $profile mode."
        }

        return OptimizationRecommendation(
            profile = profile,
            isInsufficientData = false,
            currentConfig = currentRecord,
            recommendedConfig = bestRecord,
            compositeScore = bestCandidate.compositeScore,
            confidencePercent = confidence,
            confidenceLabel = confidenceLabel,
            nextExperimentCandidate = nextStepCandidate,
            explanationPoints = explanations,
            advantages = advantages,
            disadvantages = disadvantages,
            warnings = warnings,
            efficiencyComparison = effComparison,
            rankedStableCandidates = stableCandidates
        )
    }

    fun selectNextControlledExperiment(
        grid: List<GridCandidate>,
        currentFreq: Int?,
        currentVolt: Int?,
        profile: AnalysisProfile
    ): GridCandidate? {
        val nextNeighbors = grid.filter { it.status == CandidateCellStatus.CANDIDATE_NEXT }
        if (nextNeighbors.isEmpty()) {
            return grid
                .filter { it.status == CandidateCellStatus.UNTESTED }
                .minByOrNull { it.stepDistanceFromBaseline ?: Int.MAX_VALUE }
        }
        val f = currentFreq ?: 625
        val v = currentVolt ?: 1150
        return when (profile) {
            AnalysisProfile.ECO -> nextNeighbors.sortedWith(
                compareBy<GridCandidate> { it.voltageMv - v }
                    .thenBy { it.frequencyMhz - f }
            ).firstOrNull()
            AnalysisProfile.BEAST -> nextNeighbors.sortedWith(
                compareByDescending<GridCandidate> { it.frequencyMhz - f }
                    .thenBy { abs(it.voltageMv - v) }
            ).firstOrNull()
            AnalysisProfile.BALANCED -> nextNeighbors.sortedWith(
                compareBy<GridCandidate> { abs(it.voltageMv - v) + abs(it.frequencyMhz - f) }
                    .thenByDescending { it.frequencyMhz }
                    .thenBy { it.voltageMv }
            ).firstOrNull()
        }
    }

    fun computeStabilityScore(
        hashrates: List<Double>,
        asicErrorPct: Double?,
        disconnectCount: Int,
        durationSeconds: Long,
        thresholds: SafetyThresholdConfig
    ): Double {
        if (hashrates.isEmpty()) return 0.0
        val mean = hashrates.average()
        if (mean <= 0.0) return 0.0
        val variance = hashrates.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        val cv = stdDev / mean

        val varianceScore = (1.0 - (cv / 0.20).coerceIn(0.0, 1.0)) * 45.0
        val errScore = if (asicErrorPct != null) {
            (1.0 - (asicErrorPct / max(thresholds.maxAsicErrorPct, 0.5)).coerceIn(0.0, 1.0)) * 25.0
        } else 20.0
        val disconnectPenalty = (disconnectCount * 15.0).coerceAtMost(30.0)
        val durationScore = (durationSeconds.toDouble() / max(thresholds.minObservationSeconds, 1L).toDouble())
            .coerceIn(0.0, 1.0) * 30.0

        return (varianceScore + errScore + durationScore - disconnectPenalty).coerceIn(0.0, 100.0)
    }

    private fun aggregateRecords(records: List<ExperimentRecord>): ExperimentRecord {
        if (records.size == 1) return records.first()
        val totalSamples = records.sumOf { it.sampleCount }.coerceAtLeast(1)
        val weightedHash = records.sumOf { it.avgHashrateThs * it.sampleCount } / totalSamples
        val weightedPower = records.sumOf { it.avgPowerWatts * it.sampleCount } / totalSamples
        val eff = EfficiencyCalculator.calculateEfficiencyJth(weightedPower, weightedHash) ?: records.last().efficiencyJth
        val temps = records.mapNotNull { it.avgAsicTempC }
        val vrTemps = records.mapNotNull { it.avgVrTempC }
        val errs = records.mapNotNull { it.avgAsicErrorPct }
        val latest = records.maxBy { it.timestampMillis }
        val totalDuration = records.sumOf { it.durationSeconds }
        val totalDisconnects = records.sumOf { it.disconnectCount }
        val anyStable = records.any { it.isStable }
        val avgStability = records.map { it.stabilityScore }.average()

        return latest.copy(
            avgHashrateThs = weightedHash,
            minHashrateThs = records.minOf { it.minHashrateThs },
            maxHashrateThs = records.maxOf { it.maxHashrateThs },
            avgPowerWatts = weightedPower,
            efficiencyJth = eff,
            avgAsicTempC = if (temps.isNotEmpty()) temps.average() else null,
            avgVrTempC = if (vrTemps.isNotEmpty()) vrTemps.average() else null,
            avgAsicErrorPct = if (errs.isNotEmpty()) errs.average() else null,
            acceptedShares = records.sumOf { it.acceptedShares },
            rejectedShares = records.sumOf { it.rejectedShares },
            staleShares = records.mapNotNull { it.staleShares }.takeIf { it.isNotEmpty() }?.sum(),
            sampleCount = totalSamples,
            disconnectCount = totalDisconnects,
            isStable = anyStable && totalDisconnects <= 2,
            stabilityScore = avgStability,
            durationSeconds = totalDuration
        )
    }

    private fun calculateMeasuredConfidence(
        minSamples: Int,
        minDurationSeconds: Long,
        disconnects: Int
    ): Int {
        val sampleFactor = (minSamples / 30.0).coerceIn(0.2, 1.0)
        val durationFactor = (minDurationSeconds / 180.0).coerceIn(0.2, 1.0)
        val raw = ((sampleFactor * 0.5 + durationFactor * 0.5) * 95.0) - (disconnects * 15.0)
        return raw.toInt().coerceIn(25, 96)
    }

    private fun normalizeHigherBetter(value: Double, min: Double, max: Double): Double {
        if (abs(max - min) < 1e-9) return 1.0
        return ((value - min) / (max - min)).coerceIn(0.0, 1.0)
    }

    private fun normalizeLowerBetter(value: Double, min: Double, max: Double): Double {
        if (abs(max - min) < 1e-9) return 1.0
        return ((max - value) / (max - min)).coerceIn(0.0, 1.0)
    }

    private fun formatSigned(value: Double, decimals: Int): String {
        val prefix = if (value >= 0) "+" else ""
        return "$prefix%.${decimals}f".format(value)
    }

    private data class ScoreWeights(
        val hashrate: Double,
        val efficiency: Double,
        val stability: Double,
        val errorRate: Double,
        val temperature: Double
    )
}
