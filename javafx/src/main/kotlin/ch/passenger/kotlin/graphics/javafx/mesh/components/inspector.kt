package ch.passenger.kotlin.graphics.javafx.mesh.components

import ch.passenger.kotlin.graphics.geometry.Curve
import ch.passenger.kotlin.graphics.javafx.mesh.canvas.EdgeHandler
import ch.passenger.kotlin.graphics.javafx.mesh.canvas.FXMeshCanvas
import ch.passenger.kotlin.graphics.javafx.mesh.canvas.line
import ch.passenger.kotlin.graphics.javafx.util.*
import ch.passenger.kotlin.graphics.math.ImmutableVectorF
import ch.passenger.kotlin.graphics.math.MutableVectorF
import ch.passenger.kotlin.graphics.math.VectorF
import ch.passenger.kotlin.graphics.mesh.HalfEdge
import ch.passenger.kotlin.graphics.mesh.svg.*
import ch.passenger.kotlin.graphics.util.logging.d
import ch.passenger.kotlin.graphics.util.logging.e
import javafx.beans.binding.Bindings
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.collections.FXCollections
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.*
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import org.slf4j.LoggerFactory
import java.util.IllegalFormatConversionException

/**
* svd coded this on 11/05/2015.
*/
class MeshInspector<H,V,F>(val canvas:FXMeshCanvas<H,V,F>) : Pane() {
    private val log = LoggerFactory.getLogger(javaClass<MeshInspector<H,V,F>>())
    var e:HalfEdge<H,V,F> = canvas.mesh.NOEDGE
    set(v) {
        $e = v

        to.setText("${v.destination.v}")
        from.setText("${v.origin.v}")
        coords.getItems().clear()
        id.setText("")
        svg.setText("")
        ia.setText(""); sia.setText(""); pi.setText(""); cross.setText(""); scross.setText("")
        interpolate.setSelected(v in canvas.edgeHandlers && canvas.edgeHandlers[v]==interpolator)
        interpolate.setDisable(true)
        interpolator = canvas.edgeHandlers[e]
        if(v!=canvas.mesh.NOEDGE) {
            ia.setText("%.4f".format(v.innerAngle))
            cross.setText("%.4f".format(v.cross.z))
            if(cross.getTooltip()==null) {
                cross.setTooltip(Tooltip())
            }
            cross.getTooltip().setText("${v.cross}")
            cycle.setSelected(v.cycle)
            if(v.cycle) {
                pi.setText("%.4f".format(v.piLaw))
                sia.setText("%.4f".format(v.sumInnerAngles))
                if(scross.getTooltip()==null) {
                    scross.setTooltip(Tooltip())
                }
                try {
                    scross.setText("%.4f".format(v.sumCross.z))
                } catch(e: IllegalFormatConversionException) {
                    log.e(e){
                        "${v.sumCross.javaClass} vec : ${v.sumCross is VectorF} " +
                                "ivf: ${v.sumCross is ImmutableVectorF} " +
                                "mvf: ${v.sumCross is MutableVectorF}"}
                }
                scross.getTooltip().setText("${v.sumCross}")
                inner.setSelected(v.insideLooking)

            }
            val data = v.data
            when(data) {
                is SVGPathMeshData -> {
                    val pe = data.pathElement
                    coords.setItems(FXCollections.observableList(pe.coords.toList()))
                    id.setText("${pe.id}")
                    if(id.getTooltip()==null) {
                        id.setTooltip(Tooltip())
                    }
                    id.getTooltip().setText("coord offset ${data.coordOffset}")
                    if(from.getTooltip()==null) {
                        from.setTooltip(Tooltip())
                    }
                    from.getTooltip().setText("${data.origin}")
                    if(to.getTooltip()==null) {
                        to.setTooltip(Tooltip())
                    }
                    to.getTooltip().setText("${data.target}")
                    svg.setText("${pe.svg}")
                    if(data.pathElement is SVGCurve) {
                        interpolate.setDisable(false)
                    }
                }
                is SVGReversePath -> svg.setText("reverse edge")
            }
        }
    }
    val from = TextField("     ")
    val to = TextField("     ")
    val cycle = CheckBox("cycle")
    val inner = CheckBox("inner")
    val ia : TextField
    val sia = TextField("00.00")
    val pi = TextField("00.00")
    val cross = TextField("00000.00")
    val scross = TextField("0000.00")
    val id = TextField("     ")
    val svg = TextArea("     ")
    val coords = TableView<VectorF>()
    val interpolate  : CheckBox = CheckBox("interpolate")
    var interpolator : EdgeHandler<H,V,F>? = null
    init {
        val cx = TableColumn<VectorF, Float>()
        val cy = TableColumn<VectorF, Float>()
        val cz = TableColumn<VectorF, Float>()
        cx.setCellValueFactory {
            ReadOnlyObjectWrapper(it.getValue().x)
        }
        cy.setCellValueFactory {
            ReadOnlyObjectWrapper(it.getValue().y)
        }
        cz.setCellValueFactory {
            ReadOnlyObjectWrapper(it.getValue().z)
        }
        coords.getColumns().addAll(cx, cy, cz)

        canvas.focus.addListener { ov, oe, ne -> e = ne }
        from.setEditable(false); to.setEditable(false);
        var tfia : TextField? = null
        splitpane {
            vbox {
                hbox { this+"id"; this+id; this+interpolate; button("all") {
                    setOnAction { e().forEach {
                        if(it.data is SVGCurveMeshData) {
                            canvas.edgeHandlers[it] = interpolator(it, canvas.px)
                            canvas.dirty()
                        }
                    } }
                } }
                hbox { this+"From:"; this+from; this+ makeLabel("To"); this+to }
                hbox { this+cycle; this+inner; this+"\u03c0";this+pi }
                hbox { label("ia:"); tfia=textfield { setText("00.0000"); }; this+"sum:";this+sia }
                hbox { this+"cross:"; this+cross; this+"sum:"; this+scross }
                vbox { label("svg"); this+svg}
            }
            scrollpane(coords) {
                prefWidthProperty().bind(Bindings.select(parentProperty(), "width"))
                prefHeightProperty().bind(Bindings.select(parentProperty(), "height"))
            }
            setDividerPositions(.4)
        }
        ia = tfia!!
        prefWidthProperty().bind(Bindings.select(parentProperty(), "width"))
        prefHeightProperty().bind(Bindings.select(parentProperty(), "height"))
        interpolate.selectedProperty().addListener { ov, of, nf ->
            if(nf) {
                if(e.data is SVGMeshData) {
                    interpolate()
                }
            } else {
                if(e in canvas.edgeHandlers && canvas.edgeHandlers[e]==interpolator) {
                    canvas.edgeHandlers.remove(e)
                    canvas.dirty()
                }
                interpolator = null
            }
        }
        e = canvas.focus.get()
    }

    fun interpolate() {
        if(interpolator!=null) {
            canvas.edgeHandlers[e] = interpolator!!
            canvas.dirty()
            return
        }
        val data = e.data
        if(data is SVGCurveMeshData) {
            val c = data.curve
            interpolator = interpolator(e, canvas.px)
            canvas.edgeHandlers[e] = interpolator!!
            canvas.dirty()
        }
    }
}

fun<H,V,F> interpolator(e:HalfEdge<H,V,F>, px:Double) : EdgeHandler<H,V,F> = object: EdgeHandler<H, V, F> {
    val c : Curve
    val pxlen = e.dir.magnitude()/px
    val delta = (1f/pxlen).toFloat()
    val pstart : VectorF
    val pend : VectorF
    init {
        val data = e.data
        if(data !is SVGCurveMeshData) throw IllegalStateException()
        c = data.curve
        pstart = data.origin
        pend = data.target
    }
    override fun draw(e: HalfEdge<H, V, F>, g: GraphicsContext) {
        var p0 = pstart
            for(t in 0f..1f-delta step delta) {
                val p1 = c.at(t)
                g.line(p0, p1)
                p0 = p1
            }
            g.line(p0, pend)
        }
    }
