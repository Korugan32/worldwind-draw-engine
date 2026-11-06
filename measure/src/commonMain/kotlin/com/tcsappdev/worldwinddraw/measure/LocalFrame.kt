package com.tcsappdev.worldwinddraw.measure

import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A point on the local plane, in metres: [x] to the east and [y] to the north of the origin of its [LocalFrame]. */
internal class Plane(val x: Double, val y: Double)

/**
 * A flat map of a small piece of the globe (a plot of land) in metres, around [origin]: a metre to the east is `N cos(latitude)` and a
 * metre to the north is `M` on the WGS84 ellipsoid at the origin (`N` and `M` the radii of curvature across and along a meridian), so
 * distances on it are right at the origin and out by about `tan(latitude)` times the distance over the radius of the Earth away from it
 * (a fifth of a percent at 10 km at the latitude of Turkey).
 *
 * The map is **linear in latitude and longitude** (an affine map): a straight line on it is a straight line in latitude and longitude as
 * well, which is what an overlay engine, working on longitude and latitude as if they were flat, needs to cut along the same line.
 */
internal class LocalFrame(val origin: GeoPosition) {
    private val metersPerDegreeNorth: Double
    private val metersPerDegreeEast: Double

    init {
        val phi = origin.latitude * PI / 180.0
        val w = sqrt(1.0 - Wgs84.E2 * sin(phi) * sin(phi))
        val radiusAcross = Wgs84.A / w
        val radiusAlong = Wgs84.A * (1.0 - Wgs84.E2) / (w * w * w)
        metersPerDegreeNorth = radiusAlong * PI / 180.0
        metersPerDegreeEast = radiusAcross * cos(phi) * PI / 180.0
    }

    fun toPlane(p: GeoPosition): Plane {
        var dLon = p.longitude - origin.longitude
        // the short way round the antimeridian
        if (dLon > 180.0) dLon -= 360.0 else if (dLon < -180.0) dLon += 360.0
        return Plane(dLon * metersPerDegreeEast, (p.latitude - origin.latitude) * metersPerDegreeNorth)
    }

    fun toGeo(p: Plane): GeoPosition {
        var lon = origin.longitude + p.x / metersPerDegreeEast
        if (lon > 180.0) lon -= 360.0 else if (lon < -180.0) lon += 360.0
        return GeoPosition((origin.latitude + p.y / metersPerDegreeNorth).coerceIn(-90.0, 90.0), lon.coerceIn(-180.0, 180.0))
    }
}
