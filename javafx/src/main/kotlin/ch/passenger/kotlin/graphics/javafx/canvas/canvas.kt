package ch.passenger.kotlin.graphics.javafx.canvas

import ch.passenger.kotlin.graphics.geometry.AlignedCube
import ch.passenger.kotlin.graphics.math.LineSegment
import ch.passenger.kotlin.graphics.math.MatrixF
import ch.passenger.kotlin.graphics.math.Rectangle2D
import ch.passenger.kotlin.graphics.math.VectorF
import ch.passenger.kotlin.graphics.mesh.HalfEdge
import ch.passenger.kotlin.graphics.mesh.Mesh
import ch.passenger.kotlin.graphics.mesh.Vertex
import ch.passenger.kotlin.graphics.javafx.util.*
import ch.passenger.kotlin.graphics.mesh.Face
import javafx.application.Application
import javafx.application.Platform
import javafx.beans.property.DoubleProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.geometry.Point2D
import javafx.scene.Cursor
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.Tooltip
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import javafx.scene.paint.Paint
import javafx.scene.paint.RadialGradient
import javafx.scene.transform.Affine
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate

/**
 * Created by svd on 06/05/2015.
 */
fun MatrixF.toFXAffine() : Affine {
    fun m(c:Int,r:Int) : Double = this[c,r].toDouble()
    println("toFX: $this")
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

fun<H,V,F> GraphicsContext.line(e:HalfEdge<H,V,F>, px:Double) {
    halfarrow(e.origin.v.p2d, e.destination.v.p2d, 10*px)
}

fun GraphicsContext.moveTo(p:Point2D) {
    moveTo(p.getX(), p.getY())
}
fun GraphicsContext.lineTo(p:Point2D) {
    lineTo(p.getX(), p.getY())
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

val VectorF.p2d : Point2D get() = Point2D(x.toDouble(), y.toDouble())
val Point2D.vec : VectorF get() = VectorF(getX(), getY(), 0)


class FXMeshCanvas<H,V,F>(val mesh: Mesh<H, V, F>, val base:MatrixF, val vertexFactory:(VectorF)->V, val edgeFactory:(Vertex<H,V,F>, Vertex<H,V,F>)->H) : Canvas() {
    enum class Modes {
        ADDVERTEX REMOVEVERTEX VIEW
    }
    val inverse : Affine
    val mode : ObjectProperty<Modes> = SimpleObjectProperty(Modes.VIEW)
    val markedVertices: ObservableList<Vertex<H, V, F>> = FXCollections.observableArrayList()
    val markedEdges: ObservableList<HalfEdge<H, V, F>> = FXCollections.observableArrayList()
    val markedFaces: ObservableList<Face<H, V, F>> = FXCollections.observableArrayList()
    val transientMarksV: ObservableList<Vertex<H, V, F>> = FXCollections.observableArrayList()
    val transientMarksE: ObservableList<HalfEdge<H, V, F>> = FXCollections.observableArrayList()
    val transientMarksF: ObservableList<Face<H, V, F>> = FXCollections.observableArrayList()
    val hotzone : DoubleProperty = SimpleDoubleProperty(5.0)
    val focus : ObjectProperty<HalfEdge<H,V,F>> = SimpleObjectProperty(mesh.NOEDGE)
    init {
        println("base: $base extent: ${mesh.extent}")
        val r2d = Rectangle2D.create(base(mesh.extent.min), base(mesh.extent.max))
        setWidth(r2d.w.toDouble());setHeight(r2d.h.toDouble())
        //val trmin = MatrixF.translate(-mesh.extent.min)
        //println("trmin $trmin")
        //println("trmin*base: ${trmin*base}")
        val tcenter = MatrixF.translate(VectorF(getWidth() / 2, getHeight() / 2, 0, 1))
        println("tc: $tcenter")
        val complete = (base * tcenter)
        println("complete: $complete")
        getGraphicsContext2D().transform(complete.toFXAffine())
        inverse = getGraphicsContext2D().getTransform().createInverse()
        println("w: ${getWidth()}x${getHeight()}")
        println("tf: ${getGraphicsContext2D().getTransform()}")


        mode.addListener { obv, om, nm ->
            selectMode()
        }


        this.setOnMouseMoved {
            val tp = inverse.transform(it.getX(), it.getY())
            currentMouseTransformedX.set(tp.getX())
            currentMouseTransformedY.set(tp.getY())
            currentMouseX.set(it.getX())
            currentMouseY.set(it.getY())
            val tpv = tp.vec
            val hotzone = AlignedCube.around(tpv, hotzone.getValue().toFloat()*px.toFloat())
            val res = mesh.find(hotzone)
            val cv = res.vertices.sortBy{VectorF.distance(tpv, it.v)}.firstOrNull()
            if(cv!=null) transientMarksV add cv
            val ce = res.edges.sortBy {VectorF.distance(tpv, it.origin.v)}.firstOrNull()
            if(ce!=null) transientMarksE add ce
            val cf = res.faces.sortBy { it.edge().map {VectorF.distance(tpv, it.origin.v)}.min()?:Float.MAX_VALUE }.firstOrNull()
            if(cf!=null) transientMarksF add cf
            dirty()
        }


        this.setOnMouseClicked {
            when(mode.get()) {
                Modes.VIEW -> {
                    val hotzone = AlignedCube.around(tpos, hotzone.getValue().toFloat()*px.toFloat())
                    val e = mesh.findEdges(hotzone)
                    if(e.count()>0) {
                        focus.setValue(e.first())
                    }
                }
                Modes.ADDVERTEX -> {
                    val x = currentMouseTransformedX.get()
                    val y = currentMouseTransformedY.get()
                    val v = VectorF(x, y, 0)
                    mesh.add(v, vertexFactory(v))
                }
            }
        }


        markedVertices.addListener({ change: ListChangeListener.Change<out Any?>? -> dirty() })
        markedEdges.addListener({ change: ListChangeListener.Change<out Any?>? -> dirty() })
        markedFaces.addListener({ change: ListChangeListener.Change<out Any?>? -> dirty() })
        transientMarksV.addListener({ change: ListChangeListener.Change<out Any?>? -> dirty() })
        transientMarksE.addListener({ change: ListChangeListener.Change<out Any?>? -> dirty() })
        transientMarksF.addListener({ change: ListChangeListener.Change<out Any?>? -> dirty() })
        focus.addListener { ov, oe, ne -> dirty() }
        Timer().scheduleAtFixedRate(100L, 100L) {
            if(dirty) {
                Platform.runLater { draw() }
            }
        }
        dirty()
    }

    var dirty:Boolean = false
    fun dirty() {dirty = true}


    val tpos : VectorF get() = VectorF(currentMouseTransformedX.getValue(), currentMouseTransformedY.getValue(), 0f)
    val nodepos :  VectorF get() = VectorF(currentMouseX.get(), currentMouseY.get(), 0f)

    fun selectMode() {
        when(mode.get()) {
            Modes.VIEW -> setCursor(Cursor.DEFAULT)
            Modes.ADDVERTEX -> setCursor(Cursor.CROSSHAIR)
            Modes.REMOVEVERTEX -> setCursor(Cursor.CROSSHAIR)
        }
    }

    fun addEdge(v0:Vertex<H,V,F>, v1:Vertex<H,V,F>) {
        mesh.plus(v0, v1, edgeFactory(v0, v1), edgeFactory(v1, v0))
        dirty()
    }

    override fun isResizable(): Boolean = false
    val zoom : DoubleProperty = SimpleDoubleProperty(1.0)
    val px : Double get() = ((mesh.extent.max.x-mesh.extent.min.x)*zoom.getValue()) / getWidth()

    val currentMouseTransformedX : DoubleProperty = SimpleDoubleProperty()
    val currentMouseTransformedY : DoubleProperty = SimpleDoubleProperty()
    val currentMouseX : DoubleProperty = SimpleDoubleProperty()
    val currentMouseY : DoubleProperty = SimpleDoubleProperty()
    fun draw() {
        val g = getGraphicsContext2D()
        g.save()
        val px = (mesh.extent.max.x-mesh.extent.min.x)/getWidth()
        println("1px: $px")
        val p1 = inverse.transform(1.0, 1.0)
        val p2 = getGraphicsContext2D().getTransform().transform(1.0, 1.0)
        println("p1: $p1 p2: $p2")
        g.clear(mesh.extent.min, mesh.extent.max)
        g.setStroke(Color.DARKSLATEGRAY)
        g.setLineWidth(px)
        g.line(mesh.extent.min.x, 0f, mesh.extent.max.x, 0f)
        g.line(0f, mesh.extent.min.y, 0f, mesh.extent.max.y)
        g.setStroke(Color.BLACK)
        mesh.edges.forEach {
            g.line(it, px)
        }
        g.setStroke(Color.DODGERBLUE)
        markedVertices.forEach { g.strokeOval(it.v.x.toDouble()-2.5*px, it.v.y.toDouble()-2.5*px, 5*px, 5*px) }
        markedEdges.forEach {
            g.save()
            g.setLineWidth(2*px)
            g.setStroke(Color.DODGERBLUE)
            g.line(it, px)
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
        transientMarksE.forEach { g.line(it, px) }
        transientMarksE.clear()
        transientMarksF.forEach { g.fill(it) }
        transientMarksF.clear()
        if(focus.get()!=mesh.NOEDGE) {
            g.setLineWidth(3*px)
            g.setStroke(Color.FORESTGREEN)
            g.line(focus.get(), px)
        }
        g.restore()
        dirty = false
    }
}