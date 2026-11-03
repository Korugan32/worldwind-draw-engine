package com.tcsappdev.worldwinddraw.adapter.engine

import com.tcsappdev.worldwinddraw.adapter.input.PickInputBridge
import com.tcsappdev.worldwinddraw.adapter.layer.DrawLayer
import com.tcsappdev.worldwinddraw.core.animation.Animatable
import com.tcsappdev.worldwinddraw.core.animation.AnimationController
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.overlay.OverlayEngine
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.state.DrawEngineState
import com.tcsappdev.worldwinddraw.core.state.DrawEvent
import com.tcsappdev.worldwinddraw.core.state.DrawMode
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import com.tcsappdev.worldwinddraw.core.state.DrawableEntityType
import com.tcsappdev.worldwinddraw.core.style.HandleStyle
import com.tcsappdev.worldwinddraw.core.style.HighlightStyle
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import earth.worldwind.gesture.SelectDragCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

/**
 * The whole draw engine in one object: the shapes, the undo history, the drawing state, the layer that puts them on the map and the
 * input that lets a person draw and edit on it. It builds and connects those, so a host does not have to.
 *
 * ```
 * val engine = WorldWindDrawEngine(scope, overlay = JtsOverlayEngine())
 * engine.attachTo(worldWindow)          // adds the layer and takes the touches (a platform function, see below)
 * // once per frame, from the host's own clock:
 * engine.tick(deltaMs)
 * engine.startDrawing(DrawableEntityType.Polygon)
 * ```
 *
 * **What the host does:** [attachTo] its map window (there is one for each platform, because WorldWind's `WorldWindow` is not one common
 * type), and call [tick] once per frame so that animations (the selected shape's pulse, an effect that is playing) advance: the engine has
 * no clock of its own. Everything else is the engine's.
 *
 * **What a person does** reaches it through [input] (taps and drags on the map), and the host's own buttons call the functions here
 * ([startDrawing], [finishDrawing], [select], [edit], [undo], ...). What the engine shows is in [state], [entities], [canUndo] and
 * [canRedo], all of them flows to collect.
 *
 * The parts it is made of are public for a host that needs more than this: [repository] (to put shapes in without a history step, or to
 * play an effect on), [history], [layer] and [send] (any [DrawEvent]).
 *
 * @param scope the scope the layer builds the shapes in (the host's own, so that it ends when the host's does).
 * @param overlay what merges and cuts polygons ([mergeWith], [cutBy]); null if the host does not need them, then they do nothing.
 * @param historyCapacity how many steps can be undone.
 * @param handleStyle how the handles of a shape being edited look; can be changed later through [layer].
 * @param highlightStyle how a selected shape is marked; can be changed later through [layer].
 * @param buildOnFirstFrame whether the layer builds the shapes in the first frame instead of in the background (see [DrawLayer]).
 */
public class WorldWindDrawEngine(
    scope: CoroutineScope,
    overlay: OverlayEngine? = null,
    historyCapacity: Int = 100,
    handleStyle: HandleStyle = HandleStyle(),
    highlightStyle: HighlightStyle = HighlightStyle(),
    buildOnFirstFrame: Boolean = false,
) {
    /** The shapes that exist. Putting one in here directly is not a step of the history (it cannot be undone); see [DrawEvent] for what is. */
    public val repository: EntityRepository = EntityRepository()

    /** The undo history; [undo] and [redo] are what a host needs of it. */
    public val history: HistoryManager = HistoryManager(historyCapacity)

    private val stateMachine = DrawStateMachine(history, repository, overlay)

    // The map window is only known when the host attaches it, and the layer asks for a redraw from the start: it goes through this.
    private var requestRedraw: () -> Unit = {}

    /** The layer that shows the shapes on the map; [attachTo] adds it to a window. */
    public val layer: DrawLayer = DrawLayer(repository, stateMachine, scope, { requestRedraw() }, handleStyle, highlightStyle, buildOnFirstFrame)

    /** Where the touches on the map go: taps and drags to select, draw and edit. [attachTo] gives it to the window. */
    public val input: SelectDragCallback = PickInputBridge(stateMachine)

    private val animation = AnimationController().also { it.register(layer) }

    /** What the engine is doing: idle, drawing, selecting, editing... */
    public val state: StateFlow<DrawEngineState> = stateMachine.state

    /** The shapes that exist, as a flow. */
    public val entities: StateFlow<List<com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity>> = repository.entities

    /** Whether there is a step to undo. */
    public val canUndo: StateFlow<Boolean> = history.canUndo

    /** Whether there is a step to redo. */
    public val canRedo: StateFlow<Boolean> = history.canRedo

    /**
     * Lets the engine ask the host's window to draw again when something changed (a shape, a selection, a step undone). [attachTo] does this;
     * call it yourself to use a window [attachTo] does not know.
     */
    public fun onRedrawNeeded(request: () -> Unit) {
        requestRedraw = request
    }

    /** Advances what moves by [deltaMs] milliseconds: call it once per frame. The engine has no clock of its own. */
    public fun tick(deltaMs: Long): Unit = animation.tick(deltaMs)

    /** Moves [animatable] (an effect, see the `morph` module) along with every [tick] until [stop] is called for it. */
    public fun play(animatable: Animatable): Unit = animation.register(animatable)

    /** Stops moving [animatable] (an effect that has finished); the opposite of [play]. */
    public fun stop(animatable: Animatable): Unit = animation.unregister(animatable)

    public fun undo(): Unit = history.undo()

    public fun redo(): Unit = history.redo()

    /** Gives the engine any [event] (what the buttons below do, and more: see [DrawEvent]). */
    public fun send(event: DrawEvent): Unit = stateMachine.onEvent(event)

    /** Starts drawing a new shape of [type]; the person taps the map to place its points, then [finishDrawing]. [style] is how it looks while drawn. */
    public fun startDrawing(type: DrawableEntityType, style: ShapeStyle? = null) {
        send(DrawEvent.ModeChange(if (style == null) DrawMode.Draw(type, emptyList()) else DrawMode.Draw(type, emptyList(), style)))
    }

    /**
     * Turns what is being drawn into a shape with the id [id] (a new one by default) and returns the id; null, and nothing happens, if nothing
     * is being drawn. Whether the points make a shape (a polygon needs 3) is for the engine to say: if they do not, it stays as it was.
     */
    public fun finishDrawing(id: EntityId = EntityId(Random.nextLong().toString())): EntityId? {
        val draft = state.value.mode as? DrawMode.Draw ?: return null
        send(DrawEvent.FinishDrawing(id, draft.pendingVertices.map { VertexId(Random.nextLong().toString()) }))
        return if (repository.entities.value.any { it.id == id }) id else null
    }

    /** Gives up what is being drawn, or what is being edited or selected: back to idle. */
    public fun cancel(): Unit = send(DrawEvent.ModeChange(DrawMode.Idle))

    /** Selects the shape [id] (it is marked; [edit], [deleteSelected], [mergeWith] and the like then apply to it). */
    public fun select(id: EntityId): Unit = send(DrawEvent.ModeChange(DrawMode.Select(id)))

    /** Starts editing the shape [id]: its corners can be dragged and new ones added. */
    public fun edit(id: EntityId): Unit = send(DrawEvent.ModeChange(DrawMode.Edit(id)))

    /** Ends editing and leaves the shape selected. */
    public fun stopEditing() {
        val editing = state.value.mode as? DrawMode.Edit ?: return
        select(editing.entityId)
    }

    /** Deletes the selected shape (or the one being edited); it can be undone. */
    public fun deleteSelected(): Unit = send(DrawEvent.DeleteSelected)

    /** Merges the selected polygon with the polygon [otherId]; needs an `overlay`. New ids are made from [idPrefix]. */
    public fun mergeWith(otherId: EntityId, idPrefix: String = Random.nextLong().toString()): Unit = send(DrawEvent.MergeWith(otherId, idPrefix))

    /** Cuts the polygon [cutterId] out of the selected polygon; needs an `overlay`. New ids are made from [idPrefix]. */
    public fun cutBy(cutterId: EntityId, idPrefix: String = Random.nextLong().toString()): Unit = send(DrawEvent.CutBy(cutterId, idPrefix))
}
