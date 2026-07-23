package com.tcsappdev.worldwinddraw.core.state

import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import kotlin.test.Test
import kotlin.test.assertEquals

class DrawEngineReducerTest {
    @Test
    fun modeChangeAlwaysChangesMode() {
        val startState = DrawEngineState(mode = DrawMode.Idle)
        val result = DrawEngineReducer.reduce(startState, DrawEvent.ModeChange(DrawMode.Pan))
        assertEquals(DrawMode.Pan, result.mode)
    }

    @Test
    fun clickInDrawModeAddsPendingVertex() {
        val startState = DrawEngineState(mode = DrawMode.Draw(DrawableEntityType.Polygon, emptyList()))
        val event = DrawEvent.Click(GeoPosition(0.0, 0.0), null)
        val result = DrawEngineReducer.reduce(startState, event)
        assertEquals(1, (result.mode as DrawMode.Draw).pendingVertices.size)
    }

    @Test
    fun clickOnEntityWhileIdleSelectsIt() {
        val startState = DrawEngineState(mode = DrawMode.Idle)
        val event = DrawEvent.Click(GeoPosition(0.0, 0.0), EntityId("x"))
        val result = DrawEngineReducer.reduce(startState, event)
        assertEquals(DrawMode.Select(EntityId("x")), result.mode)
    }

    @Test
    fun clickOnEmptySpaceWhileIdleStaysIdle() {
        val startState = DrawEngineState(mode = DrawMode.Idle)
        val event = DrawEvent.Click(GeoPosition(0.0, 0.0), null)
        val result = DrawEngineReducer.reduce(startState, event)
        assertEquals(result.mode, DrawMode.Idle)
    }

    @Test
    fun clickingSameSelectedShapeEntersEdit() {
        // Clicking the shape that's already selected is treated as "I want to edit this
        // one now" — a deliberate second click, not just a re-selection.
        val startState = DrawEngineState(mode = DrawMode.Select(EntityId("A")))
        val event = DrawEvent.Click(GeoPosition(0.0, 0.0), EntityId("A"))
        val result = DrawEngineReducer.reduce(startState, event)
        assertEquals(result.mode, DrawMode.Edit(EntityId("A")))
    }

    @Test
    fun clickingDifferentShapeJustSelectsIt() {
        // Clicking a *different* shape while one is already selected only switches the
        // selection — it does not jump straight into editing the new shape.
        val startState = DrawEngineState(mode = DrawMode.Select(EntityId("A")))
        val event = DrawEvent.Click(GeoPosition(0.0, 0.0), EntityId("B"))
        val result = DrawEngineReducer.reduce(startState, event)
        assertEquals(result.mode, DrawMode.Select(EntityId("B")))
    }

    @Test
    fun clickingEmptySpaceDeselects() {
        val startState = DrawEngineState(mode = DrawMode.Select(EntityId("A")))
        val event = DrawEvent.Click(GeoPosition(0.0, 0.0), null)
        val result = DrawEngineReducer.reduce(startState, event)
        assertEquals(result.mode, DrawMode.Idle)
    }

}