package ch.passenger.kotlin.graphics.javafx.svg

import ch.passenger.kotlin.graphics.javafx.mesh.canvas.line
import ch.passenger.kotlin.graphics.javafx.util.*
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableBooleanValue
import javafx.beans.value.ObservableStringValue
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.control.CheckBox
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextField
import javafx.scene.input.MouseEvent
import javafx.scene.layout.BorderPane
import javafx.scene.paint.Color
import javafx.scene.text.Text

/**
 * svd coded this on 12/05/2015.
 */
class SVGCanvas(width:Double, height:Double) : Canvas(width, height) {
    val path : SimpleStringProperty = SimpleStringProperty("")
    val fill : SimpleBooleanProperty = SimpleBooleanProperty(false)
    val grid : SimpleBooleanProperty = SimpleBooleanProperty(true)
    val gridw : SimpleIntegerProperty = SimpleIntegerProperty(5)

    override fun isResizable(): Boolean = false

    init {
        path.addListener { ov, os, ns ->
            println("append ${path.get()}")
            draw()
        }
    }

    fun draw() {
        val g = graphicsContext2D
        g.clearRect(0.0, 0.0, width, height)

        if(grid.get()) {
            g.save()
            g.stroke = Color.LIGHTGRAY
            val d :Double = width /gridw.get()
            for(i in d..width) {
                g.line(0f, i.toFloat(), width.toFloat(), i.toFloat())
                g.line(i.toFloat(), 0f, i.toFloat(), height.toFloat())
            }
            g.restore()
        }
        val p = path.get()
        if(!p.isEmpty()) {
            g.beginPath()
            g.appendSVGPath(p)
            if(fill.get()) g.fill()
            else g.stroke()
        }

    }
}

class SVGCanvasScene(width:Double, height:Double, cw:Double=width, ch:Double=height) : Scene(BorderPane(), width, height) {
    val tx= TextField()
    val ty = TextField()
    init {
        val canvas = SVGCanvas(cw, ch)
        val chkFill = CheckBox("Fill")
        chkFill.selectedProperty().bindBidirectional(canvas.fill)
        val path = TextField("                     ")
        path.textProperty().bindBidirectional(canvas.path)
        path.minWidth(50.0)
        val bp = root as BorderPane
        bp.west {
            makeVbox {
                this+chkFill
                this+ makeHbox { this+"X:"; this+tx }
                this+ makeHbox { this+"Y:"; this+ty }
            }
        }
        bp.south {
            makeHbox { this+"Path:"; this+path }
        }
        bp.center { makeScrollpane(canvas) }
        val sp = bp.center as ScrollPane
        canvas.fromEvents(MouseEvent.MOUSE_MOVED).subscribe {
            tx.text = "%.2f".format(it.x)
            ty.text = "%.2f".format(it.y)
        }
    }
}