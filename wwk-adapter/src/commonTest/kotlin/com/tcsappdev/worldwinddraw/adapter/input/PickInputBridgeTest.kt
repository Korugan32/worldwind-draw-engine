package com.tcsappdev.worldwinddraw.adapter.input

import com.tcsappdev.worldwinddraw.adapter.layer.DrawLayer
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.state.DrawMode
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import earth.worldwind.geom.Position
import earth.worldwind.shape.Polygon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PickInputBridgeTest {
    private fun bridge() = PickInputBridge(DrawStateMachine(HistoryManager()))

    @Test
    fun canPickObjectsIsAlwaysTrue() {
        // Required for onObjectPicked to ever fire at all — see SelectDragCallback's KDoc.
        assertTrue(bridge().canPickObjects(userObject = "anything"))
    }

    @Test
    fun onObjectPickedResolvesTheEntityIdTaggedByDrawLayer() {
        val stateMachine = DrawStateMachine(HistoryManager())
        val bridge = PickInputBridge(stateMachine)
        val taggedRenderable = Polygon().apply { putUserProperty(DrawLayer.ENTITY_ID_KEY, EntityId("demo-polygon")) }

        bridge.onObjectPicked(taggedRenderable, Position.fromDegrees(1.0, 2.0, 0.0))

        // Idle + a non-null hitEntityId transitions to Select per DrawEngineReducer — this
        // only happens if the EntityId round-tripped through putUserProperty/getUserProperty
        // correctly.
        assertEquals(DrawMode.Select(EntityId("demo-polygon")), stateMachine.state.value.mode)
    }

    @Test
    fun onObjectPickedWithAnUntaggedRenderableTreatsItAsNoHit() {
        val stateMachine = DrawStateMachine(HistoryManager())
        val bridge = PickInputBridge(stateMachine)

        bridge.onObjectPicked(Polygon(), Position.fromDegrees(1.0, 2.0, 0.0))

        // No EntityId tag means hitEntityId is null, so Idle stays Idle (Select requires a hit).
        assertEquals(DrawMode.Idle, stateMachine.state.value.mode)
    }

    @Test
    fun onTerrainPickedDispatchesAClickWithNoHitEntity() {
        val stateMachine = DrawStateMachine(HistoryManager())
        val bridge = PickInputBridge(stateMachine)

        bridge.onTerrainPicked(Position.fromDegrees(1.0, 2.0, 0.0))

        assertEquals(DrawMode.Idle, stateMachine.state.value.mode)
    }
}
