package com.example.domain.engine

import com.example.domain.model.DataProvenance
import com.example.domain.model.EfficiencyComparison

object EfficiencyCalculator {

    /**
     * Converts GH/s (as commonly reported by ESP-Miner / AxeOS /api/system/info)
     * into TH/s. 1 TH/s = 1000 GH/s.
     */
    fun hashrateGhsToThs(hashrateGhs: Double?): Double? {
        if (hashrateGhs == null || !hashrateGhs.isFinite() || hashrateGhs < 0.0) return null
        return hashrateGhs / 1000.0
    }

    /**
     * Calculates energy efficiency in Joules per Terahash (J/TH):
     * Efficiency (J/TH) = Power (W) / Hashrate (TH/s).
     * Lower J/TH indicates better energy efficiency.
     * Example: 18.7 W / 1.20 TH/s = 15.58 J/TH.
     */
    fun calculateEfficiencyJth(powerWatts: Double?, hashrateThs: Double?): Double? {
        if (powerWatts == null || hashrateThs == null) return null
        if (!powerWatts.isFinite() || !hashrateThs.isFinite()) return null
        if (powerWatts <= 0.0 || hashrateThs <= 0.0) return null
        return powerWatts / hashrateThs
    }

    fun calculateEfficiencyFromGhs(powerWatts: Double?, hashrateGhs: Double?): Double? {
        val ths = hashrateGhsToThs(hashrateGhs)
        return calculateEfficiencyJth(powerWatts, ths)
    }

    /**
     * Computes percentage improvement (positive = lower J/TH = better, negative = deterioration).
     */
    fun calculateImprovementPercent(currentJth: Double?, targetJth: Double?): Double? {
        if (currentJth == null || targetJth == null || currentJth <= 0.0 || targetJth <= 0.0) return null
        return ((currentJth - targetJth) / currentJth) * 100.0
    }

    fun buildComparison(
        currentEfficiencyJth: Double?,
        previousEfficiencyJth: Double?,
        recommendedEfficiencyJth: Double?,
        currentPowerWatts: Double?,
        recommendedPowerWatts: Double?,
        currentHashrateThs: Double?,
        recommendedHashrateThs: Double?,
        recommendedIsMeasured: Boolean
    ): EfficiencyComparison {
        val improvement = calculateImprovementPercent(currentEfficiencyJth, recommendedEfficiencyJth)
        val powerDiff = if (currentPowerWatts != null && recommendedPowerWatts != null) {
            recommendedPowerWatts - currentPowerWatts
        } else null
        val hashrateDiff = if (currentHashrateThs != null && recommendedHashrateThs != null) {
            recommendedHashrateThs - currentHashrateThs
        } else null

        return EfficiencyComparison(
            currentEfficiencyJth = currentEfficiencyJth,
            previousEfficiencyJth = previousEfficiencyJth,
            recommendedEfficiencyJth = recommendedEfficiencyJth,
            improvementPercentVsCurrent = improvement,
            powerDiffWatts = powerDiff,
            hashrateDiffThs = hashrateDiff,
            currentProvenance = if (currentEfficiencyJth != null) DataProvenance.MEASURED else DataProvenance.UNAVAILABLE,
            recommendedProvenance = when {
                recommendedEfficiencyJth == null -> DataProvenance.UNAVAILABLE
                recommendedIsMeasured -> DataProvenance.MEASURED
                else -> DataProvenance.ESTIMATED
            }
        )
    }
}
