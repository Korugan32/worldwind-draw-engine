package com.tcsappdev.worldwinddraw.core.state

import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class DrawStateMachineTest {
    @Test
    fun onEventUpdatesState() {
        val machine = DrawStateMachine(HistoryManager(), EntityRepository())
        machine.onEvent(DrawEvent.ModeChange(DrawMode.Pan))
        assertEquals(DrawMode.Pan, machine.state.value.mode)
    }
}
