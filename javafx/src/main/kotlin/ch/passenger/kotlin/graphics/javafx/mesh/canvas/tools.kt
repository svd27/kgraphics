package ch.passenger.kotlin.graphics.javafx.mesh.canvas

import ch.passenger.kotlin.graphics.geometry.AlignedCube
import ch.passenger.kotlin.graphics.javafx.util.fromEvents
import ch.passenger.kotlin.graphics.mesh.Vertex
import ch.passenger.kotlin.graphics.util.cast
import ch.passenger.kotlin.graphics.util.collections.RingBuffer
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.shape.Line
import rx.Observable
import rx.Subscription
import kotlin.reflect
import kotlin.reflect.KClass

/**
 * Created by svd on 10/05/2015.
 */
abstract class CanvasTool<H,V,F>(val canvas:FXMeshCanvas<H,V,F>, var id:Int) {
    var active : Boolean = false
    set(v) {$active=v; start()}
    abstract fun start()
    abstract fun cancel()
}

class CanvasSelectTool<H : Any, V : Any, F : Any>(canvas: FXMeshCanvas<H, V, F>, id: Int) : CanvasTool<H, V, F>(canvas, id) {
    override fun start() {

    }

    override fun cancel() {

    }

    companion object {
        init {
            CanvasToolFactory.tools add object : CanvasToolCreator() {
                override fun <H, V, F> fits(vc: KClass<V>, ec: KClass<H>, fc: KClass<F>): Boolean = true
                override fun <H, V, F> invoke(c: FXMeshCanvas<H, V, F>, id: Int): CanvasTool<H, V, F> = CanvasSelectTool(c, id)
            }
        }
        fun hi() {}
    }
}

class CanvasCreateEdgeTool<H : Any, V : Any, F : Any>(canvas: FXMeshCanvas<H, V, F>, id: Int) : CanvasTool<H, V, F>(canvas, id) {
    val ringbuffer : RingBuffer<Vertex<H,V,F>> = RingBuffer(2)
    var subclick : Subscription? = null
    var submove : Subscription? = null
    var line : Line? = null
    override fun start() {
        subclick = canvas.fromEvents(MouseEvent.MOUSE_CLICKED).doOnCompleted{submove?.unsubscribe(); submove=null; subclick=null}.subscribe {
            if(it.getButton()== MouseButton.SECONDARY) cancel()
            val vs = canvas.mesh.findVertices(AlignedCube.around(canvas.tpos, canvas.hotzone.get().toFloat()))
            val v= vs.firstOrNull()
            if(v!=null && v !in ringbuffer) ringbuffer.push(v)
            if(ringbuffer.size==1) {

            }

        }
    }

    override fun cancel() {
        subclick?.unsubscribe()
        submove?.unsubscribe()
        line=null
        canvas.dirty()
    }

    companion object {
        init {
            CanvasToolFactory.tools add object : CanvasToolCreator() {
                override fun <H, V, F> fits(vc: KClass<V>, ec: KClass<H>, fc: KClass<F>): Boolean = true
                override fun <H, V, F> invoke(c: FXMeshCanvas<H, V, F>, id: Int): CanvasTool<H, V, F> = CanvasSelectTool(c, id)
            }
        }
        fun hi() {}
    }
}


abstract class CanvasToolCreator() {
    abstract fun<H,V, F> fits(vc:KClass<V>, ec:KClass<H>, fc:KClass<F>) : Boolean
    abstract fun<H,V,F> invoke(c:FXMeshCanvas<H,V,F>, id:Int) : CanvasTool<H,V,F>
}

object CanvasToolFactory {
    val tools : MutableSet<CanvasToolCreator> = hashSetOf()

    fun<H,V,F> invoke(vc:KClass<V>, ec:KClass<H>, fc:KClass<F>) : Iterable<CanvasToolCreator> = tools.filter { it.fits(vc, ec, fc) }
    init {
        CanvasSelectTool.hi()
    }
}