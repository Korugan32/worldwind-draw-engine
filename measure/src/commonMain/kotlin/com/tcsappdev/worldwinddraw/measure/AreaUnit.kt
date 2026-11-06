package com.tcsappdev.worldwinddraw.measure

/**
 * A unit an area is told in, with how many square metres it is. The Turkish **dönüm** is 1,000 m² (the legal one; the old Ottoman dönüm of
 * about 919 m² is not used).
 */
public enum class AreaUnit(public val squareMeters: Double) {
    SquareMeter(1.0),
    Donum(1_000.0),
    Hectare(10_000.0),
    Acre(4_046.8564224),
    SquareKilometer(1_000_000.0);

    /** [squareMeters] (a number of square metres) in this unit. */
    public fun fromSquareMeters(squareMeters: Double): Double = squareMeters / this.squareMeters

    /** [value] in this unit as square metres. */
    public fun toSquareMeters(value: Double): Double = value * squareMeters
}
