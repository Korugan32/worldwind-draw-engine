package com.tcsappdev.worldwinddraw.measure

import kotlin.math.floor

/**
 * The two coefficients of a Turkish zoning plan (*imar planı*) that say how much may be built on a plot.
 *
 * @property taks *Taban alanı katsayısı*: the share of the plot that the ground floor of the building may cover, from more than 0 up to 1 (0.30 is 30% of
 * the plot's area).
 * @property kaks *Emsal* (*kat alanları katsayısı*): the total area of all the floors of the building as a multiple of the plot's area; more
 * than 0 (0.60 is 60% of the plot's area, spread over the floors).
 */
public class ZoningLimits(public val taks: Double, public val kaks: Double) {
    init {
        require(taks.isFinite() && taks > 0.0 && taks <= 1.0) { "taks has to be more than 0 and at most 1, was $taks" }
        require(kaks.isFinite() && kaks > 0.0) { "kaks has to be more than 0, was $kaks" }
    }
}

/**
 * What may be built on a plot.
 *
 * @property maxFootprint the most the ground floor may cover, in square metres.
 * @property maxTotalArea the most all the floors together may come to, in square metres.
 * @property floors how many floors the building has if it covers [maxFootprint] and uses all of [maxTotalArea] (not a whole number: 1.5 is
 * a ground floor and a half floor); 0 if nothing can be built.
 */
public class BuildingAllowance(
    public val maxFootprint: Double,
    public val maxTotalArea: Double,
    public val floors: Double,
) {
    /** [floors] without its fraction: the whole floors of that building. */
    public val wholeFloors: Int get() = floor(floors + 1e-9).toInt()
}

/**
 * How a building that has been drawn stands against the limits.
 *
 * @property taks the footprint over the plot's area.
 * @property kaks the total area of the floors over the plot's area.
 * @property footprintAllowed whether the footprint is within what [ZoningLimits.taks] and the buildable part of the plot allow.
 * @property totalAllowed whether the total area is within what [ZoningLimits.kaks] allows.
 */
public class ZoningCheck(
    public val taks: Double,
    public val kaks: Double,
    public val footprintAllowed: Boolean,
    public val totalAllowed: Boolean,
) {
    /** Whether the building is within both limits. */
    public val allowed: Boolean get() = footprintAllowed && totalAllowed
}

/**
 * Taks and emsal arithmetic. **A simulation, not a ruling:** the coefficients, the setbacks and which areas count (a basement, a balcony, a
 * stairwell) differ between municipalities and zoning plans and have exceptions, so the numbers here are for planning, and the municipality's
 * own calculation is the one that counts.
 */
public object Zoning {
    private const val TOLERANCE = 1e-9

    /** The taks of a building: its [footprint] (the area its ground floor covers, in square metres) over the area of the plot. */
    public fun taks(footprint: Double, parcelArea: Double): Double {
        require(parcelArea > 0.0) { "the plot has no area" }
        require(footprint >= 0.0) { "the footprint cannot be negative" }
        return footprint / parcelArea
    }

    /** The emsal of a building: the area of all its floors together ([totalArea], in square metres) over the area of the plot. */
    public fun kaks(totalArea: Double, parcelArea: Double): Double {
        require(parcelArea > 0.0) { "the plot has no area" }
        require(totalArea >= 0.0) { "the total area cannot be negative" }
        return totalArea / parcelArea
    }

    /**
     * What may be built on a plot of [parcelArea] square metres under [limits]. The footprint is also held inside [buildableArea], the part of
     * the plot the setbacks leave (see [Setback]); leave it out when there is no setback.
     */
    public fun allowance(parcelArea: Double, limits: ZoningLimits, buildableArea: Double = parcelArea): BuildingAllowance {
        require(parcelArea > 0.0) { "the plot has no area" }
        require(buildableArea >= 0.0) { "the buildable area cannot be negative" }
        val footprint = minOf(limits.taks * parcelArea, buildableArea)
        val total = limits.kaks * parcelArea
        return BuildingAllowance(footprint, total, if (footprint > 0.0) total / footprint else 0.0)
    }

    /**
     * Whether a building with the ground floor [footprint] and all floors [totalArea] (square metres) is within [limits] on a plot of
     * [parcelArea], with [buildableArea] the part of it the setbacks leave.
     */
    public fun check(parcelArea: Double, footprint: Double, totalArea: Double, limits: ZoningLimits, buildableArea: Double = parcelArea): ZoningCheck {
        val allowed = allowance(parcelArea, limits, buildableArea)
        return ZoningCheck(
            taks = taks(footprint, parcelArea),
            kaks = kaks(totalArea, parcelArea),
            footprintAllowed = footprint <= allowed.maxFootprint * (1.0 + TOLERANCE),
            totalAllowed = totalArea <= allowed.maxTotalArea * (1.0 + TOLERANCE),
        )
    }
}
