package ch.passenger.kotlin.graphics.javafx.mesh.canvas

import ch.passenger.kotlin.graphics.geometry.AlignedCube
import ch.passenger.kotlin.graphics.geometry.Triangle
import ch.passenger.kotlin.graphics.math.LineSegment
import ch.passenger.kotlin.graphics.math.MatrixF
import ch.passenger.kotlin.graphics.math.Rectangle2D
import ch.passenger.kotlin.graphics.math.VectorF
import ch.passenger.kotlin.graphics.mesh.HalfEdge
import ch.passenger.kotlin.graphics.mesh.Mesh
import ch.passenger.kotlin.graphics.mesh.Vertex
import ch.passenger.kotlin.graphics.javafx.util.*
import ch.passenger.kotlin.graphics.mesh.Face
import ch.passenger.kotlin.graphics.util.collections.RingBuffer
import ch.passenger.kotlin.graphics.util.logging.d
import javafx.application.Application
import javafx.application.Platform
import javafx.beans.property.*
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.geometry.Point2D
import javafx.scene.Cursor
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.Tooltip
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseButton
import javafx.scene.input.ZoomEvent
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import javafx.scene.paint.Paint
import javafx.scene.paint.RadialGradient
import javafx.scene.shape.Line
import javafx.scene.text.Font
import javafx.scene.transform.Affine
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.reflect.KClass

/**
 * Created by svd on 06/05/2015.
 */
val log = LoggerFactory.getLogger(javaClass<FXMeshCanvas<*,*,*>>().getPackage().javaClass)
fun MatrixF.toFXAffine() : Affine {
    fun m(c:Int,r:Int) : Double = this[c,r].toDouble()
    log.debug("toFX: {}", this)
    return  Affine(m(0, 0), m(1, 0), m(3, 0), m(1, 0), m(1, 1), m(3, 1))
}

fun GraphicsContext.line(x0:Float, y0:Float, x1:Float, y1:Float) {
    beginPath()
    moveTo(x0.toDouble(), y0.toDouble())
    lineTo(x1.toDouble(), y1.toDouble())
    this.stroke()
    closePath()
}

fun GraphicsContext.line(v0:VectorF, v1:VectorF) {
    line(v0.x, v0.y, v1.x, v1.y)
}

fun GraphicsContext.clear(v0:VectorF, v1:VectorF) {
    val wh = v1-v0
    clearRect(v0.x.toDouble(), v0.y.toDouble(), wh.x.toDouble(), wh.y.toDouble())
}

fun GraphicsContext.line(ls:LineSegment) {
    line(ls.start, ls.end)
}

fun<H,V,F> GraphicsContext.line(e:HalfEdge<H,V,F>) {
    line(e.origin.v, e.destination.v)
}

fun GraphicsContext.moveTo(p:Point2D) {
    moveTo(p.getX(), p.getY())
}
fun GraphicsContext.lineTo(p:Point2D) {
    lineTo(p.getX(), p.getY())
}

fun GraphicsContext.moveTo(p:VectorF) {
    moveTo(p.x.toDouble(), p.y.toDouble())
}


fun GraphicsContext.moveTo(p:Vertex<*,*,*>) {
    moveTo(p.v)
}

fun GraphicsContext.lineTo(p:VectorF) {
    lineTo(p.x.toDouble(), p.y.toDouble())
}

fun GraphicsContext.lineTo(p:Vertex<*,*,*>) {
    lineTo(p.v)
}

fun GraphicsContext.halfarrow(p1:Point2D, p2:Point2D, len:Double, angle:Double=35.0)  {
    val dx = p2.getX()-p1.getX();
    val dy = p2.getY()-p1.getY();
    //val len = p1.distance(p2)

    val theta = Math.atan2(dy, dx);

    val rad = Math.toRadians(angle); //35 angle, can be adjusted
    val arrowLength = len
    val x = p2.getX() - arrowLength * Math.cos(theta + rad);
    val y = p2.getY() - arrowLength * Math.sin(theta + rad);

    //val phi2 = Math.toRadians(-35.0);//-35 angle, can be adjusted
    //val x2 = p2.getX() - arrowLength * Math.cos(theta + phi2);
    //val y2 = p2.getY() - arrowLength * Math.sin(theta + phi2);
    beginPath()
    moveTo(p1)
    lineTo(p2)
    lineTo(x, y)
    stroke()
}

fun<H,V,F> GraphicsContext.fill(f:Face<H,V,F>) {
    moveTo(f.edge.origin.v.p2d)
    f.edge().forEach { lineTo(it.destination.v.p2d) }
    closePath()
    fill()
}

fun<H,V,F> GraphicsContext.fill(vararg vs:Vertex<H,V,F>) {
    moveTo(vs.first())
    vs.drop(1).forEach { lineTo(it) }
    closePath()
    fill()
}


fun GraphicsContext.fill(text:String, v:VectorF) {
    println("ft: $v")
    val tp = getTransform().transform(v.x.toDouble(), v.y.toDouble())
    println("ft t: $tp")
    strokeText(text, v.x.toDouble(), v.y.toDouble())
    //fillText(text, v.x.toDouble(), v.y.toDouble())
}

val VectorF.p2d : Point2D get() = Point2D(x.toDouble(), y.toDouble())
val Point2D.vec : VectorF get() = VectorF(getX(), getY(), 0)

trait EdgeHandler<H,V,F> {
    fun draw(e:HalfEdge<H,V,F>, g:GraphicsContext)
}


class FXMeshCanvas<H,V,F>(val mesh: Mesh<H, V, F>, val base:MatrixF, val vertexFactory:(VectorF)->V, val edgeFactory:(Vertex<H,V,F>, Vertex<H,V,F>)->H,
                          val kv:KClass<V>, val ke:KClass<H>, val kf:KClass<F>) : Canvas() {
    private val log = LoggerFactory.getLogger(javaClass<FXMeshCanvas<H,V,F>>())
    enum class Modes {
        ADDVERTEX REMOVEVERTEX VIEW ADDEDGE
    }
    var inverse : Affine = Affine()
    val mode : ObjectProperty<Modes> = SimpleObjectProperty(Modes.VIEW)
    val markedVertices: ObservableList<Vertex<H, V, F>> = FXCollections.observableArrayList()
    val markedEdges: ObservableList<HalfEdge<H, V, F>> = FXCollections.observableArrayList()
    val markedFaces: ObservableList<Face<H, V, F>> = FXCollections.observableArrayList()
    val transientMarksV: ObservableList<Vertex<H, V, F>> = FXCollections.observableArrayList()
    val transientMarksE: ObservableList<HalfEdge<H, V, F>> = FXCollections.observableArrayList()
    val transientMarksF: ObservableList<Face<H, V, F>> = FXCollections.observableArrayList()
    val hotzone : DoubleProperty = SimpleDoubleProperty(5.0)
    val focus : ObjectProperty<HalfEdge<H,V,F>> = SimpleObjectProperty(mesh.NOEDGE)
    val context : ObjectProperty<HalfEdge<H,V,F>> = SimpleObjectProperty(mesh.NOEDGE)
    val lblangles : BooleanProperty = SimpleBooleanProperty(false)
    val lblvertices : BooleanProperty = SimpleBooleanProperty(false)
    val vbuffer : RingBuffer<Vertex<H,V,F>> = RingBuffer(2)
    val zoom : DoubleProperty = SimpleDoubleProperty(1.0)

    val vertexWalker = VertexWalker(this)
    val triangleWalker = TriangleWalker(this)
    val edgeHandlers : MutableMap<HalfEdge<H,V,F>,EdgeHandler<H,V,F>> = hashMapOf()
    val drawEdgeHandles : BooleanProperty = SimpleBooleanProperty(true)

    init {
        setFocusTraversable(true)
        /*
        val r2d = Rectangle2D.create(base(mesh.extent.min), base(mesh.extent.max))
        setWidth(r2d.w.toDouble());setHeight(r2d.h.toDouble())
        val tcenter = MatrixF.translate(VectorF(getWidth() / 2, getHeight() / 2, 0, 1))
        val tzoom = MatrixF.scale(VectorF(zoom.get(), zoom.get(), zoom.get()))
        val complete = (base * tcenter * tzoom)
        getGraphicsContext2D().transform(complete.toFXAffine())
                inverse = getGraphicsContext2D().getTransform().createInverse()
                */
        initTransform()
        zoom.addListener { ov, no, nn ->
            initTransform(); dirty()
        }
        fromEvents(ZoomEvent.ZOOM).subscribe {
            zoom.set(it.getTotalZoomFactor())
        }

        mode.addListener { obv, om, nm ->
            selectMode()
        }
        val tools = CanvasToolFactory.invoke(kv, ke, kf)
        val ti = tools.mapIndexed { i, it -> it(this, i) }
        ti.forEach { log.debug("$it ${it.active} ${it.javaClass}") }

        this.setOnMouseMoved {
            if(mode.get()==Modes.ADDEDGE) {
                if(vbuffer.size==1) {
                    val g2 = getGraphicsContext2D()
                    g2.save()
                    val tp = inverse.transform(it.getX(), it.getY())
                    val v :VectorF = vbuffer[0]!!.v
                    g2.setLineWidth(0.5*px)
                    g2.setStroke(Color.LIGHTGRAY)
                    g2.setLineDashes(8.0, 2.0)
                    g2.moveTo(v.p2d); g2.lineTo(tp)
                    g2.stroke()
                    g2.restore()
                }

            }
            val tp = inverse.transform(it.getX(), it.getY())
            currentMouseTransformedX.set(tp.getX())
            currentMouseTransformedY.set(tp.getY())
            currentMouseX.set(it.getX())
            currentMouseY.set(it.getY())
            val tpv = tp.vec
            //val hotzone = AlignedCube.around(tpv, hotzone.getValue().toFloat()*px.toFloat())
            val hotzone = hotzone(tpv)
            val res = mesh.find(hotzone)
            log.trace("MM HZ: ${hotzone} v: ${res.vertices.count()} e: ${res.edges.count()}")
            transientMarksV.addAll(res.vertices)
            transientMarksE.addAll(res.edges)
            transientMarksF.addAll(res.faces)
            if(res.edges.count()>0) {
                context.set(res.edges.sortBy {VectorF.distance(tpos, it.origin.v)}.first())
            } else if(context.get()!=focus.get()) context.set(focus.get())
            dirty()
        }




        this.setOnMouseClicked {
            requestFocus()
            when(mode.get()) {
                Modes.VIEW -> {
                    val hotzone = hotzone(tpos)
                    val e = mesh.findEdges(hotzone)
                    if(e.count()>0) {
                        focus.setValue(e.sortBy{VectorF.distance(tpos, it.origin.v)}.first())
                    }
                }
                Modes.ADDVERTEX -> {
                    val x = currentMouseTransformedX.get()
                    val y = currentMouseTransformedY.get()
                    val v = VectorF(x, y, 0)
                    mesh.add(v, vertexFactory(v))
                }
                Modes.ADDEDGE -> {
                    if(it.getButton()== MouseButton.SECONDARY) vbuffer.clear()
                    else {
                        val hotzone = hotzone(tpos)
                        val vs = mesh.findVertices(hotzone)
                        if (vs.count() > 0) {
                            vbuffer.push(vs.first())
                            if (vbuffer.size == 2) {
                                val v0 = vbuffer[1]!!
                                val v1 = vbuffer[0]!!
                                mesh.plus(v0, v1, edgeFactory(v0, v1), edgeFactory(v1, v0))
                                vbuffer.clear(); vbuffer.push(v1)
                            }
                        }
                    }
                }
            }
        }

        setOnKeyPressed {
            if (it.getCode() == KeyCode.OPEN_BRACKET) {
                if (focus.get() != mesh.NOEDGE) {
                    val e = focus.get()
                    if (e.previous != e.NOEDGE) {
                        focus.set(e.previous)
                    }
                }
            }
            if (it.getCode() == KeyCode.CLOSE_BRACKET) {
                if (focus.get() != mesh.NOEDGE) {
                    val e = focus.get()
                    if (e.next != e.NOEDGE) {
                        focus.set(e.next)
                    }
                }
            }
            if (it.getCode() == KeyCode.T) {
                if (focus.get() != mesh.NOEDGE) {
                    val e = focus.get()
                    if (e.twin != e.NOEDGE) {
                        focus.set(e.twin)
                    }
                }
            }
            if (it.getCode() == KeyCode.Q) {
                if (focus.get() != mesh.NOEDGE) {
                    val e = focus.get()
                    if (e.left.properFace) {
                        val p0 = e.previous.origin
                        val p1 = e.origin
                        val p2 = e.destination
                        getGraphicsContext2D().fill(p0, p1, p2)
                    }
                }
            }
            if(it.getCode()==KeyCode.SEMICOLON) {
                vertexWalker.walk()
            }
            if(it.getCode()==KeyCode.COMMA) {
                triangleWalker.walk()
            }

        }

        markedVertices.addListener({ change: ListChangeListener.Change<out Any?>? -> dirty() })
        markedEdges.addListener({ change: ListChangeListener.Change<out Any?>? -> dirty() })
        markedFaces.addListener({ change: ListChangeListener.Change<out Any?>? -> dirty() })
        transientMarksV.addListener({ change: ListChangeListener.Change<out Any?>? -> dirty() })
        transientMarksE.addListener({ change: ListChangeListener.Change<out Any?>? -> dirty() })
        transientMarksF.addListener({ change: ListChangeListener.Change<out Any?>? -> dirty() })
        focus.addListener { ov, oe, ne -> dirty(); context.set(ne) }
        Timer().scheduleAtFixedRate(100L, 100L) {
            if(dirty) {
                Platform.runLater { draw() }
            }
        }
        dirty()
    }

    fun hotzone(v:VectorF) : AlignedCube {
        val w = VectorF(3) {if(it<2) hotzone.getValue().toFloat()*px.toFloat()/2 else 0f}
        return  AlignedCube(v-w, v+w)
    }

    fun initTransform() {
        val r2d = Rectangle2D.create(base(mesh.extent.min)*zoom.get().toFloat(), base(mesh.extent.max)*zoom.get().toFloat())
        setWidth(r2d.w.toDouble());setHeight(r2d.h.toDouble())
        log.trace("${zoom.get()}: ${getWidth()}x${getHeight()}")
        val tzoom = MatrixF.scale(zoom.get().toFloat())
        val tzero = MatrixF.translate(mesh.extent.min)
        val scale = tzero * base * tzoom
        val tmin = scale(mesh.extent.min); val tmax = scale(mesh.extent.max)
        val tr = Rectangle2D.create(tmin, tmax).scale(.1f)
        setWidth(tr.w.toDouble());setHeight(tr.h.toDouble())
        log.d{"${mesh.extent.min}x${mesh.extent.max} -> ${tmin}x$tmax"}
        log.d{"canvas wxh ${getWidth()}x${getHeight()}"}
        //VectorF(getWidth() / 2, getHeight() / 2, 0, 1)
        val tcenter = MatrixF.translate(-(tmin-tmax)*.05f-tmin)
        val complete = (scale * tcenter)
        getGraphicsContext2D().setTransform(complete.toFXAffine())

        inverse = getGraphicsContext2D().getTransform().createInverse()
        dirty()
    }

    var dirty:Boolean = false
    fun dirty() {dirty = true}


    val tpos : VectorF get() = VectorF(currentMouseTransformedX.getValue(), currentMouseTransformedY.getValue(), 0f)
    val nodepos :  VectorF get() = VectorF(currentMouseX.get(), currentMouseY.get(), 0f)

    fun selectMode() {
        vbuffer.clear()
        when(mode.get()) {
            Modes.VIEW -> setCursor(Cursor.DEFAULT)
            Modes.ADDVERTEX -> setCursor(Cursor.CROSSHAIR)
            Modes.REMOVEVERTEX -> setCursor(Cursor.CROSSHAIR)
            Modes.ADDEDGE -> setCursor(Cursor.MOVE)
            else -> setCursor(Cursor.DEFAULT)
        }
    }

    fun addEdge(v0:Vertex<H,V,F>, v1:Vertex<H,V,F>) {
        if(v0==v1) return
        mesh.plus(v0, v1, edgeFactory(v0, v1), edgeFactory(v1, v0))
        dirty()
    }

    override fun isResizable(): Boolean = false
    val px : Double get() = ((mesh.extent.max.x-mesh.extent.min.x)) / getWidth()

    val currentMouseTransformedX : DoubleProperty = SimpleDoubleProperty()
    val currentMouseTransformedY : DoubleProperty = SimpleDoubleProperty()
    val currentMouseX : DoubleProperty = SimpleDoubleProperty()
    val currentMouseY : DoubleProperty = SimpleDoubleProperty()
    fun draw() {
        val g = getGraphicsContext2D()
        g.save()
        val px = (mesh.extent.max.x-mesh.extent.min.x)/getWidth()
        g.setFont(Font.font(16*px))
        g.save(); g.setTransform(Affine()); g.clearRect(0.0, 0.0, getWidth(), getHeight()); g.restore()
        g.setStroke(Color.DARKSLATEGRAY)
        g.setLineWidth(px)
        g.line(mesh.extent.min.x, 0f, mesh.extent.max.x, 0f)
        g.line(0f, mesh.extent.min.y, 0f, mesh.extent.max.y)
        g.setStroke(Color.BLACK)
        mesh.edges.forEach {
            draw(it, g)
        }
        g.setStroke(Color.DODGERBLUE)
        markedVertices.forEach { g.strokeOval(it.v.x.toDouble()-2.5*px, it.v.y.toDouble()-2.5*px, 5*px, 5*px) }
        markedEdges.forEach {
            g.save()
            g.setLineWidth(2*px)
            g.setStroke(Color.DODGERBLUE)
            draw(it, g)
        }
        markedFaces.forEach { g.save();
            val p : Paint = Color.DODGERBLUE.deriveColor(0.0, 1.0, 1.0, .5)
            g.setFill(p)
            g.fill(it)
            g.restore()
        }
        g.setLineWidth(2*px)
        g.setStroke(Color.BLUE)
        val p : Paint = Color.BLUE.deriveColor(0.0, 1.0, 1.0, .5)
        g.setFill(p)
        transientMarksV.forEach { g.strokeOval(it.v.x.toDouble()-2.5*px, it.v.y.toDouble()-2.5*px, 5*px, 5*px) }
        transientMarksV.clear()
        transientMarksE.forEach {
            g.setLineWidth(2*px)
            draw(it, g)
            g.setLineWidth(px)
            if(lblangles.get()) {
                g.fill("%.2f".format(it.innerAngle), it.origin.v)
            }
            if(lblvertices.get()) {
                g.fill("${it.origin.id}", it.origin.v)
                g.fill("${it.destination.id}", it.destination.v)
            }
        }
        transientMarksE.clear()
        //transientMarksF.forEach { g.fill(it) }
        transientMarksF.clear()
        if(focus.get()!=mesh.NOEDGE) {
            g.setLineWidth(3*px)
            g.setStroke(Color.FORESTGREEN)
            val fe = focus.get()
            draw(fe, g)
            g.setLineWidth(px)
            if(lblangles.get()) {
                g.fill("%.2f".format(fe.innerAngle), fe.origin.v)
            }
            if(lblvertices.get()) {
                g.fill("${fe.origin.id}", fe.origin.v)
                g.fill("${fe.destination.id}", fe.destination.v)
            }
        }
        g.restore()
        dirty = false
    }

    fun draw(e:HalfEdge<H,V,F>, g:GraphicsContext) {
        if(e in edgeHandlers || e.twin in edgeHandlers) {
            if(e in edgeHandlers) edgeHandlers[e]!!.draw(e, g)
        } else {
            if(drawEdgeHandles.get()) {
                g.halfarrow(e.origin.v.p2d, e.destination.v.p2d, 8*px)
            } else {
                g.line(e)
            }
        }
        if(e !in edgeHandlers && e.twin !in edgeHandlers) {

        } else {

        }
    }
}

class VertexWalker<H,V,F>(val canvas:FXMeshCanvas<H,V,F>) {
    var focus:HalfEdge<H,V,F>? = canvas.focus.get()
    var it:Iterator<HalfEdge<H,V,F>>? = null
    fun walk() {
        if(focus==null) focus = canvas.focus.get()
        if(focus==null) return
        if(focus?.origin!=canvas.focus.get()?.origin) {
            focus = canvas.focus.get()
            it = null
        }
        if(focus==null) return
        if(it==null) it=focus!!.origin().iterator()
        val lit = it
        if(lit!=null && lit.hasNext()) {
            focus = lit.next()
            canvas.focus.setValue(focus)
        } else it = null
    }
}

class TriangleWalker<H,V,F>(val canvas:FXMeshCanvas<H,V,F>) {
    var focus:HalfEdge<H,V,F>? = canvas.focus.get()
    var it:Iterator<Triangle>? = null
    fun walk() {
        if(focus==null) focus = canvas.focus.get()
        if(focus==null) return
        if(focus?.left!=canvas.focus.get()?.left) {
            focus = canvas.focus.get()
            it = null
        }
        if(focus==null) return
        if(it==null) it=focus!!.left.triangles.iterator()
        val lit = it
        if(lit!=null && lit.hasNext()) {
            val t = lit.next()
            val g2 = canvas.getGraphicsContext2D()
            g2.save()
            g2.setFill(Color.AQUAMARINE.deriveColor(0.0, 1.0, 1.0, 0.6))
            g2.beginPath()
            g2.moveTo(t.p0)
            g2.lineTo(t.p1)
            g2.lineTo(t.p2)
            g2.closePath()
            g2.fill()
        } else it = null
    }
}