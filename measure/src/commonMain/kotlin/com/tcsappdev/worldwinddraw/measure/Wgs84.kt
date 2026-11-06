package com.tcsappdev.worldwinddraw.measure

import kotlin.math.PI
import kotlin.math.atanh
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The WGS84 ellipsoid, the shape of the Earth that GPS and WorldWind use. A plot of land is measured on it, and not on a ball: a ball of the
 * mean radius is out by a tenth of a percent of an area at the latitude of Turkey (a metre of every dönüm) and by nearly half a percent at the
 * equator, which is more than a surveyor's figure leaves room for.
 */
internal object Wgs84 {
    /** The equatorial radius, in metres. */
    const val A: Double = 6_378_137.0

    /** The flattening. */
    const val F: Double = 1.0 / 298.257223563

    /** The polar radius, in metres. */
    const val B: Double = A * (1.0 - F)

    /** The square of the first eccentricity. */
    const val E2: Double = F * (2.0 - F)

    val E: Double = sqrt(E2)

    /** `q` of the authalic latitude at the pole (see [authalicSine]). */
    private val QP: Double = q(1.0)

    /**
     * The radius of the ball that has the same surface as the ellipsoid, in metres (about 6,371,007.18). A region measured on that ball, with
     * its latitudes turned into authalic latitudes, has the area it has on the ellipsoid.
     */
    val AUTHALIC_RADIUS: Double = A * sqrt(QP / 2.0)

    private fun q(sinLatitude: Double): Double {
        val eSin = E * sinLatitude
        return (1.0 - E2) * (sinLatitude / (1.0 - eSin * eSin) + atanh(eSin) / E)
    }

    /**
     * The sine of the authalic latitude of the geodetic latitude [latitudeDegrees]: the latitude on the equal-area ball that has the same
     * share of the surface between it and the equator as the geodetic one has on the ellipsoid.
     */
    fun authalicSine(latitudeDegrees: Double): Double = q(sin(latitudeDegrees * PI / 180.0)) / QP
}
