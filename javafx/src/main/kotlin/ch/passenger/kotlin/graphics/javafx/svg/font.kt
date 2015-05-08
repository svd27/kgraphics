package ch.passenger.kotlin.graphics.javafx.svg

import ch.passenger.kotlin.graphics.util.svg.font.SVGFont
import ch.passenger.kotlin.graphics.util.svg.font.SVGGlyph
import javafx.beans.binding.Bindings
import javafx.beans.property.*
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.scene.canvas.Canvas
import javafx.scene.control.*
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Font
import org.slf4j.LoggerFactory

/**
 * Created by svd on 07/05/2015.
 */
class GlyphNode(font:SVGFont, name:String, size:Double=1.0) : Region() {
    val canvas :GlyphCanvas = GlyphCanvas(font, name, size)
    val size : SimpleDoubleProperty = canvas.zoom
    var glyph: ObjectProperty<SVGGlyph> = canvas.glyph
    val filled : BooleanProperty = canvas.filled
    val color : ObjectProperty<Color> = canvas.color

    init {
        prefWidthProperty().bind(canvas.widthProperty())
        prefHeightProperty().bind(canvas.heightProperty())
        widthProperty().addListener { ov, old, new ->
            val width = new.toDouble()
            val height =getHeight()
            if(width<height) {
                val nz = width/canvas.glyph.get().xadv
                //canvas.zoom.set(nz)
            } else {
                val nz = height/canvas.font.ems
                //canvas.zoom.set(nz)
            }
        }
        heightProperty().addListener { ov, old, new ->
            val width = getWidth()
            val height = new.toDouble()
            if(width<height) {
                val nz = width/canvas.glyph.get().xadv
                //canvas.zoom.set(nz)
            } else {
                val nz = height/canvas.font.ems
                //canvas.zoom.set(nz)
            }
        }

    }

    override fun resize(width: Double, height: Double) {
        super.resize(width, height)
        println("resize: ${width}x$height")
    }
}

class GlyphCanvas(val font: SVGFont, name: String, size: Double = 3.0) : Canvas() {
    override fun isResizable(): Boolean = false
    val zoom: SimpleDoubleProperty = SimpleDoubleProperty(1.0)
    var glyph: ObjectProperty<SVGGlyph> = SimpleObjectProperty()
    val filled: BooleanProperty = SimpleBooleanProperty(true)
    val color: ObjectProperty<Color> = SimpleObjectProperty(Color.BLACK)
    var boxw = 0.0;
    var boxh = 0.0
    var inset = font.ems * .1

    init {
        glyph.set(font.names[name])
        zoom.addListener { value, old, new ->
            val xa = if (glyph.get() != null) glyph.get().xadv else 1f
            boxw = xa + font.ems * .2
            boxh = font.ems + font.ems * .2
            setWidth(new.toDouble() * boxw)
            setHeight(new.toDouble() * boxh)
            println("glyph ${getWidth()}x${getHeight()}")
            draw()
        }
        color.addListener { value, c1, cn -> draw() }
        filled.addListener { ov, fl, fln -> draw() }
        zoom.set(size)
        draw()
    }

    fun naturalSize() {
        zoom.set(16.0 / font.ems)
    }

    fun draw() {
        val g = glyph.get()
        if (g != null) {
            val g2 = getGraphicsContext2D()
            g2.save()
            g2.setFill(color.get())
            g2.scale(1.0, 1.0)
            g2.clearRect(0.0, 0.0, getWidth(), getHeight())
            g2.setLineWidth(1.0)
            g2.scale(zoom.get(), -zoom.get())
            g2.translate(inset * zoom.get(), (-font.ems * 1.0 - font.descent - inset * zoom.get()))
            g2.beginPath()
            g2.setLineWidth(30.0 / 14)
            g2.appendSVGPath(g.path)
            if (filled.get())
                g2.fill() else g2.stroke()
            g2.restore()
        }
    }
}


class SVGFontViewer(font: SVGFont) : Region() {
    val log = LoggerFactory.getLogger(SVGFontViewer::class.javaClass)
    init {
        val bp : BorderPane = BorderPane()
        val list = ListView(FXCollections.observableList(font.names.keySet().toList()))
        val canvas = GlyphCanvas(font, font.names.keySet().first())


        list.getSelectionModel().getSelectedItems().addListener({ change: ListChangeListener.Change<out Any?> ->
            change.getList().forEach {
                val sel = it.toString()
                val g = font.names[sel]
                canvas.glyph.set(g)
                canvas.draw()
            }
        })

        val slider : Slider = Slider(1.0/(font.ems), 2.0, 1.0)
        slider.setShowTickLabels(true); slider.setShowTickMarks(true)
        slider.valueProperty().bindBidirectional(canvas.zoom)

        val spc = ScrollPane(canvas)
        spc.setMinSize(200.0, 200.0)
        spc.setPrefSize(400.0, 400.0)
        val tb = TextArea()
        tb.textProperty().bind(Bindings.select(canvas.glyph, "path"))
        bp.setBottom(tb)
        val chkFill = CheckBox("Filled")
        chkFill.selectedProperty().bindBidirectional(canvas.filled)
        val colorPicker : ColorPicker = ColorPicker()
        colorPicker.valueProperty().bindBidirectional(canvas.color)
        val bnat = Button("1")
        val glyphNode = GlyphNode(font, "at", 1.0)
        println("gn size: ${glyphNode.getWidth()}x${glyphNode.getHeight()}")
        bnat.setGraphic(glyphNode)
        bnat.setOnAction {
            //println("${(bnat.getGraphic() as GlyphNode).getWidth()}x${(bnat.getGraphic() as GlyphNode).getHeight()} ${(bnat.getGraphic() as GlyphNode).size.get()}")
            canvas.naturalSize()
        }
        val lblZoom = Label("1.00")
        lblZoom.textProperty().bind(canvas.zoom.multiply(100.0).asString("%.2f"))
        val lblSize = Label("0x0")
        lblSize.textProperty().bind(canvas.widthProperty().asString("%.2f").concat(canvas.heightProperty().asString("x%.2f")))
        val hbox = HBox(lblZoom, chkFill)
        val vb = VBox(ScrollPane(list), slider, hbox, colorPicker, bnat, lblSize)
        bp.setLeft(vb)
        bp.setCenter(spc)
        getChildren() add bp
        bp.prefHeightProperty().bind(this.heightProperty())
        bp.prefWidthProperty().bind(this.widthProperty())
    }


}