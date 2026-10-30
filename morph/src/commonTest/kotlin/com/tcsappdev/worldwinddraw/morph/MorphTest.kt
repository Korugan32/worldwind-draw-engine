package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.MarkerLabel
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MorphTest {
    // a-4 at (5,5) between a-3 (1,0) and a-1 (0,0): its share of the length of the new stretch a-3, a-4, a-1
    private val SHARE_OF_A4 = hypot(4.0, 5.0) / (hypot(4.0, 5.0) + hypot(5.0, 5.0))

    private fun snapshot(vararg entities: DrawableEntity) = Snapshot(entities.toList())

    @Test
    fun atTheStartItIsExactlyTheFirstSnapshotAndAtTheEndExactlyTheSecond() {
        val from = snapshot(triangle("a", 1.0), marker("m", 1.0, 1.0))
        val to = snapshot(triangle("a", 5.0), marker("n", 2.0, 2.0))
        assertSame(from.entities, Morph.between(from, to, 0f))
        assertSame(to.entities, Morph.between(from, to, 1f))
        assertSame(from.entities, Morph.between(from, to, -0.5f))
        assertSame(to.entities, Morph.between(from, to, 2f))
    }

    @Test
    fun notANumberIsRefused() {
        assertFailsWith<IllegalArgumentException> { Morph.between(snapshot(), snapshot(), Float.NaN) }
    }

    @Test
    fun twoEmptySnapshotsHaveNothingInBetween() {
        assertEquals(emptyList(), Morph.between(snapshot(), snapshot(), 0.5f))
    }

    @Test
    fun aPolygonWithTheSameVerticesMovesThemHalfWayAndKeepsItself() {
        val from = snapshot(triangle("a", 2.0, lat = 0.0, lon = 0.0, style = solid(0, 0, 0)).copy(outlineWidth = 1f))
        val to = snapshot(triangle("a", 10.0, lat = 10.0, lon = 20.0, style = solid(100, 200, 50)).copy(outlineWidth = 5f))

        val middle = Morph.between(from, to, 0.5f).single() as DrawnPolygon

        assertEquals(EntityId("a"), middle.id)
        assertEquals(listOf("a-1", "a-2", "a-3"), middle.vertices.map { it.id.id })
        assertEquals(listOf(GeoPosition(5.0, 10.0), GeoPosition(5.0, 16.0), GeoPosition(11.0, 10.0)), middle.vertices.map { it.position })
        assertEquals(solid(50, 100, 25), middle.style)
        assertEquals(3f, middle.outlineWidth)
    }

    @Test
    fun theHolesAndPartsOfAPolygonMoveToo() {
        val hole = listOf(vertex("h-1", 1.0, 1.0), vertex("h-2", 1.0, 2.0), vertex("h-3", 2.0, 1.0))
        val movedHole = listOf(vertex("h-1", 3.0, 3.0), vertex("h-2", 3.0, 4.0), vertex("h-3", 4.0, 3.0))
        val from = snapshot(triangle("a", 5.0).copy(extraRings = listOf(hole)))
        val to = snapshot(triangle("a", 5.0).copy(extraRings = listOf(movedHole)))

        val middle = Morph.between(from, to, 0.5f).single() as DrawnPolygon

        assertEquals(listOf(listOf(GeoPosition(2.0, 2.0), GeoPosition(2.0, 3.0), GeoPosition(3.0, 2.0))), middle.extraRings.map { ring -> ring.map { it.position } })
    }

    @Test
    fun aPolygonThatGainedAVertexGrowsItOutOfTheOldEdge() {
        val from = triangle("a", 1.0) // (0,0) (0,1) (1,0)
        val to = from.copy(vertices = from.vertices + vertex("a-4", 5.0, 5.0)) // the new corner sits between a-3 and a-1

        val middle = Morph.between(snapshot(from), snapshot(to), 0.5f)

        assertEquals(listOf("a"), middle.map { it.id.id }) // one shape, not two that fade
        val corner = (middle.single() as DrawnPolygon).vertices
        assertEquals(listOf("a-1", "a-2", "a-3", "a-4"), corner.map { it.id.id })
        // it starts on the old edge from a-3 (1,0) to a-1 (0,0), where the new stretch a-3, a-4, a-1 has got to when it reaches a-4, and ends at (5,5)
        val startLatitude = 1 - SHARE_OF_A4
        assertEquals((startLatitude + 5.0) / 2, corner[3].position.latitude, 1e-9)
        assertEquals(2.5, corner[3].position.longitude, 1e-9)
        assertEquals(from.vertices.take(3).map { it.position }, corner.take(3).map { it.position }) // the others stayed
    }

    @Test
    fun aGrowingPolygonStartsOutAsTheOldOutline() {
        val from = triangle("a", 1.0)
        val to = from.copy(vertices = from.vertices + vertex("a-4", 5.0, 5.0))
        val soon = (Morph.between(snapshot(from), snapshot(to), 0.001f).single() as DrawnPolygon).vertices[3].position
        val startLatitude = 1 - SHARE_OF_A4
        assertEquals(startLatitude + (5.0 - startLatitude) * 0.001, soon.latitude, 1e-6) // still on the edge, not yet away from it
        assertEquals(0.005, soon.longitude, 1e-6)
    }

    @Test
    fun aPolygonThatLostAVertexShrinksIntoTheNewEdge() {
        val to = triangle("a", 1.0)
        val from = to.copy(vertices = to.vertices + vertex("a-4", 5.0, 5.0))
        val corner = (Morph.between(snapshot(from), snapshot(to), 0.5f).single() as DrawnPolygon).vertices[3]
        assertEquals("a-4", corner.id.id)
        assertEquals((1 - SHARE_OF_A4 + 5.0) / 2, corner.position.latitude, 1e-9)
        assertEquals(2.5, corner.position.longitude, 1e-9)
    }

    @Test
    fun newCornersBetweenTwoOldOnesStartOnTheOldEdgeAtTheirShareOfTheNewStretch() {
        // a square-ish outline; b goes from 2 out to the east and back to 3 instead of straight (the east side is taken over)
        val from = polygon("p", vertex("v1", 0.0, 0.0), vertex("v2", 0.0, 10.0), vertex("v3", 10.0, 10.0), vertex("v4", 10.0, 0.0))
        val to = polygon("p", vertex("v1", 0.0, 0.0), vertex("v2", 0.0, 10.0), vertex("n1", 0.0, 20.0), vertex("n2", 10.0, 20.0), vertex("v3", 10.0, 10.0), vertex("v4", 10.0, 0.0))

        val corners = (Morph.between(snapshot(from), snapshot(to), 0.5f).single() as DrawnPolygon).vertices.associate { it.id.id to it.position }

        // the new stretch v2-n1-n2-v3 has three equal legs, so n1 is a third and n2 two thirds along; on the old edge v2-v3 those are (10/3, 10) and (20/3, 10)
        assertEquals((10.0 / 3 + 0.0) / 2, corners.getValue("n1").latitude, 1e-9)
        assertEquals((10.0 + 20.0) / 2, corners.getValue("n1").longitude, 1e-9)
        assertEquals((20.0 / 3 + 10.0) / 2, corners.getValue("n2").latitude, 1e-9)
        assertEquals((10.0 + 20.0) / 2, corners.getValue("n2").longitude, 1e-9)
    }

    @Test
    fun theSameShapeGoingRoundTheOtherWayStillMoves() {
        val a = triangle("a", 1.0)
        val reversed = a.copy(vertices = a.vertices.reversed())
        val middle = Morph.between(snapshot(a), snapshot(reversed), 0.5f)
        assertEquals(listOf("a"), middle.map { it.id.id })
        assertEquals(a.vertices.map { it.position }.toSet(), (middle.single() as DrawnPolygon).vertices.map { it.position }.toSet())
    }

    @Test
    fun cornersThatAreNotInTheSameOrderAroundBothAreNotTheSameShape() {
        val from = polygon("a", vertex("c1", 0.0, 0.0), vertex("c2", 0.0, 1.0), vertex("c3", 1.0, 1.0), vertex("c4", 1.0, 0.0))
        val to = polygon("a", vertex("c1", 0.0, 0.0), vertex("c3", 1.0, 1.0), vertex("c2", 0.0, 1.0), vertex("c4", 1.0, 0.0))
        assertEquals(listOf("a", "a#to"), Morph.between(snapshot(from), snapshot(to), 0.5f).map { it.id.id })
    }

    @Test
    fun twoPolygonsWithFewerThanThreeCornersInCommonCrossfade() {
        val from = triangle("a", 1.0)
        val to = polygon("a", vertex("a-1", 0.0, 0.0), vertex("a-2", 0.0, 3.0), vertex("z-3", 4.0, 4.0), vertex("z-4", 5.0, 0.0))
        assertEquals(listOf("a", "a#to"), Morph.between(snapshot(from), snapshot(to), 0.25f).map { it.id.id })
    }

    @Test
    fun aDifferentNumberOfRingsCrossfades() {
        val hole = listOf(vertex("h-1", 0.1, 0.1), vertex("h-2", 0.1, 0.2), vertex("h-3", 0.2, 0.1))
        val without = triangle("a", 5.0)
        val with = without.copy(extraRings = listOf(hole))
        assertEquals(listOf("a", "a#to"), Morph.between(snapshot(without), snapshot(with), 0.5f).map { it.id.id })
    }

    @Test
    fun aShapeThatChangedKindCrossfades() {
        val middle = Morph.between(snapshot(triangle("a", 1.0)), snapshot(marker("a", 1.0, 1.0)), 0.5f)
        assertEquals(listOf("a", "a#to"), middle.map { it.id.id })
        assertTrue(middle[0] is DrawnPolygon)
        assertTrue(middle[1] is DrawnMarker)
    }

    @Test
    fun aMarkerThatBecameAPolygonCrossfadesToo() {
        val middle = Morph.between(snapshot(marker("a", 1.0, 1.0)), snapshot(triangle("a", 1.0)), 0.5f)
        assertEquals(listOf("a", "a#to"), middle.map { it.id.id })
        assertTrue(middle[0] is DrawnMarker)
        assertTrue(middle[1] is DrawnPolygon)
    }

    @Test
    fun aShapeOnlyTheFirstHasFadesOut() {
        val middle = Morph.between(snapshot(triangle("gone", 1.0, style = solid(1, 2, 3, 200))), snapshot(), 0.25f)
        assertEquals(listOf("gone"), middle.map { it.id.id })
        assertEquals(solid(1, 2, 3, 150), middle.single().style)
    }

    @Test
    fun aShapeOnlyTheSecondHasFadesInUnderItsOwnId() {
        val middle = Morph.between(snapshot(), snapshot(triangle("new", 1.0, style = solid(1, 2, 3, 200))), 0.25f)
        assertEquals(listOf("new"), middle.map { it.id.id })
        assertEquals(solid(1, 2, 3, 50), middle.single().style)
    }

    @Test
    fun theCopyOfACrossfadedShapeNeverTakesAnIdThatIsInUse() {
        val a = triangle("a", 1.0)
        val grown = polygon("a", vertex("a-1", 0.0, 0.0), vertex("z-2", 0.0, 3.0), vertex("z-3", 4.0, 4.0))
        val squatter = marker("a#to", 1.0, 1.0)
        val middle = Morph.between(snapshot(a, squatter), snapshot(grown, squatter), 0.5f)
        assertEquals(listOf("a", "a#to", "a#to#to"), middle.map { it.id.id })
        assertEquals(middle.size, middle.map { it.id }.toSet().size)
    }

    @Test
    fun theResultKeepsTheOrderOfTheFirstWithWhatFadesInAfterItInTheOrderOfTheSecond() {
        val from = snapshot(triangle("a", 1.0), marker("b", 1.0, 1.0), marker("c", 2.0, 2.0))
        val to = snapshot(marker("n2", 3.0, 3.0), marker("c", 2.0, 2.0), marker("n1", 4.0, 4.0), marker("a", 5.0, 5.0)) // a changes kind
        assertEquals(listOf("a", "b", "c", "n2", "n1", "a#to"), Morph.between(from, to, 0.5f).map { it.id.id })
    }

    @Test
    fun aMarkerMovesItsPositionAndSize() {
        val from = snapshot(marker("m", 10.0, 10.0).copy(size = 10f))
        val to = snapshot(marker("m", 30.0, 50.0).copy(size = 30f))
        val middle = Morph.between(from, to, 0.5f).single() as DrawnMarker
        assertEquals(GeoPosition(20.0, 30.0), middle.position)
        assertEquals(20f, middle.size)
    }

    @Test
    fun aMarkerCrossesTheAntimeridianTheShortWay() {
        val from = snapshot(marker("m", 0.0, 170.0))
        val to = snapshot(marker("m", 0.0, -170.0))
        val middle = Morph.between(from, to, 0.75f).single() as DrawnMarker
        assertEquals(-175.0, middle.position.longitude)
    }

    @Test
    fun aMarkersLabelBlendsWhenItIsTheSameTextAndStepsWhenItIsNot() {
        val a = marker("m", 0.0, 0.0).copy(label = MarkerLabel("x", ColorRgba(0, 0, 0), outlineColor = null))
        val same = a.copy(label = MarkerLabel("x", ColorRgba(100, 100, 100), outlineColor = null))
        val other = a.copy(label = MarkerLabel("y", ColorRgba(100, 100, 100), outlineColor = null))
        assertEquals(ColorRgba(50, 50, 50), ((Morph.between(snapshot(a), snapshot(same), 0.5f).single()) as DrawnMarker).label?.color)
        assertEquals("x", (Morph.between(snapshot(a), snapshot(other), 0.4f).single() as DrawnMarker).label?.text)
        assertEquals("y", (Morph.between(snapshot(a), snapshot(other), 0.6f).single() as DrawnMarker).label?.text)
    }

    @Test
    fun aLineMovesItsEndsAndItsWidth() {
        val from = snapshot(line("l", 0.0, 0.0, 10.0, 10.0).copy(lineWidth = 2f))
        val to = snapshot(line("l", 10.0, 20.0, 30.0, 50.0).copy(lineWidth = 6f))
        val middle = Morph.between(from, to, 0.5f).single() as DrawnPolyline
        assertEquals(listOf(GeoPosition(5.0, 10.0), GeoPosition(20.0, 30.0)), middle.vertices.map { it.position })
        assertEquals(4f, middle.lineWidth)
    }

    @Test
    fun whatHasNoMiddleStaysTheFirstOnesUpToTheHalfWayPointAndIsTheSecondOnesAfter() {
        val from = snapshot(triangle("a", 1.0).copy(description = "old", zIndex = 1, properties = mapOf("k" to "old")))
        val to = snapshot(triangle("a", 1.0).copy(description = "new", zIndex = 7, properties = mapOf("k" to "new")))

        val early = Morph.between(from, to, 0.49f).single() as DrawnPolygon
        val late = Morph.between(from, to, 0.5f).single() as DrawnPolygon

        assertEquals(Triple("old", 1, mapOf("k" to "old")), Triple(early.description, early.zIndex, early.properties))
        assertEquals(Triple("new", 7, mapOf("k" to "new")), Triple(late.description, late.zIndex, late.properties))
    }

    @Test
    fun aLookThatChangesKindOnAMovingShapeChangesAtTheHalfWayPoint() {
        val picture = ShapeStyle.ImagePattern("x")
        val from = snapshot(triangle("a", 1.0, style = solid(1, 2, 3)))
        val to = snapshot(triangle("a", 3.0, style = picture))
        assertEquals(solid(1, 2, 3), Morph.between(from, to, 0.25f).single().style)
        assertEquals(picture, Morph.between(from, to, 0.75f).single().style)
        assertEquals(GeoPosition(0.0, 1.5), (Morph.between(from, to, 0.25f).single() as DrawnPolygon).vertices[1].position)
    }

    @Test
    fun noIdIsEverUsedTwiceAtAnyPointOfAMixedTransition() {
        val a = triangle("a", 1.0)
        val grown = a.copy(vertices = a.vertices + vertex("a-4", 5.0, 5.0))
        val from = snapshot(a, marker("m", 0.0, 0.0), line("l", 0.0, 0.0, 1.0, 1.0), triangle("gone", 1.0))
        val to = snapshot(grown, marker("m", 5.0, 5.0), triangle("l", 2.0), marker("born", 1.0, 1.0))
        for (step in 1..9) {
            val ids = Morph.between(from, to, step / 10f).map { it.id }
            assertEquals(ids.size, ids.toSet().size, "at ${step / 10f}")
        }
    }

    @Test
    fun theInputsAreNotChanged() {
        val from = snapshot(triangle("a", 1.0), marker("m", 1.0, 1.0))
        val to = snapshot(triangle("a", 9.0), marker("m", 5.0, 5.0))
        val fromBefore = from.entities.toList()
        val toBefore = to.entities.toList()
        Morph.between(from, to, 0.5f)
        assertEquals(fromBefore, from.entities)
        assertEquals(toBefore, to.entities)
    }
}
