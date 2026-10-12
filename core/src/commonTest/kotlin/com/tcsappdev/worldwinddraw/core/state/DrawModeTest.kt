package com.tcsappdev.worldwinddraw.core.state

import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.style.StyleState
import kotlin.test.Test
import kotlin.test.assertEquals

class DrawModeTest {
    private val a = EntityId("A")
    private val b = EntityId("B")

    @Test
    fun selectedEntityIsSelectedAndOthersAreDefault() {
        val mode = DrawMode.Select(a)

        assertEquals(StyleState.SELECTED, mode.styleStateFor(a))
        assertEquals(StyleState.DEFAULT, mode.styleStateFor(b))
    }

    @Test
    fun editedEntityIsEditingAndOthersAreDefault() {
        val mode = DrawMode.Edit(a)

        assertEquals(StyleState.EDITING, mode.styleStateFor(a))
        assertEquals(StyleState.DEFAULT, mode.styleStateFor(b))
    }

    @Test
    fun editingStaysEditingWhileADragIsActive() {
        val mode = DrawMode.Edit(a, DrawMode.Edit.ActiveDrag.NewVertex(edgeIndex = 0, position = com.tcsappdev.worldwinddraw.core.geometry.GeoPosition(0.0, 0.0)))

        assertEquals(StyleState.EDITING, mode.styleStateFor(a))
    }

    @Test
    fun modesThatNameNoEntityLeaveEveryEntityDefault() {
        listOf(
            DrawMode.Idle,
            DrawMode.Pan,
            DrawMode.Draw(DrawableEntityType.Polygon, emptyList()),
        ).forEach { mode ->
            assertEquals(StyleState.DEFAULT, mode.styleStateFor(a), "mode $mode")
        }
    }
}
