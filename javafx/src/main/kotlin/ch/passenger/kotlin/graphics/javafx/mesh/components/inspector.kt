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
class MeshInspector<H:Any,V:Any,F:Any>(val canvas:FXMeshCanvas<H,V,F>) : Pane() {
    private val log = LoggerFactory.getLogger(MeshInspector::class.java)
    var e: HalfEdge<H, V, F> = canvas.mesh.NOEDGE
    set(v) {
        field = v

        to.text = "${v.destination.v}"
        from.text = "${v.origin.v}"
        coords.items.clear()
        id.text = ""
        svg.text = ""
        ia.text = ""; sia.text = ""; pi.text = ""; cross.text = ""; scross.text = ""
        interpolate.isSelected = v in canvas.edgeHandlers && canvas.edgeHandlers[v]==interpolator
        interpolate.isDisable = true
        interpolator = canvas.edgeHandlers[e]
        if(v!=canvas.mesh.NOEDGE) {
            ia.text = "%.4f".format(v.innerAngle)
            ia.ttip = "%.4f".format(v.outerAngle)

            cross.text = "%.4f".format(v.cross.z)
            cross.ttip = "${v.cross}"
            cycle.isSelected = v.cycle
            if(v.cycle) {
                pi.text = "%.4f".format(v.piLaw)
                sia.text = "%.4f".format(v.sumInnerAngles)
                sia.ttip = "%.4f".format(v.sumOuterAngles)
                if(scross.tooltip ==null) {
                    scross.tooltip = Tooltip()
                }
                try {
                    scross.text = "%.4f".format(v.sumCross.z)
                } catch(e: IllegalFormatConversionException) {
                    log.e(e){
                        "${v.sumCross.javaClass} vec : ${v.sumCross is VectorF} " +
                                "ivf: ${v.sumCross is ImmutableVectorF} " +
                                "mvf: ${v.sumCross is MutableVectorF}"}
                }
                scross.ttip = "${v.sumCross}"
                inner.isSelected = v.insideLooking

            }
            val data = v.data
            when(data) {
                is SVGPathMeshData -> {
                    val pe = data.pathElement
                    coords.items = FXCollections.observableList(pe.coords.toList())
                    id.text = "${pe.id}"
                    if(id.tooltip ==null) {
                        id.tooltip = Tooltip()
                    }
                    id.tooltip.text = "coord offset ${data.coordOffset}"
                    if(from.tooltip ==null) {
                        from.tooltip = Tooltip()
                    }
                    from.tooltip.text = "${data.origin}"
                    if(to.tooltip ==null) {
                        to.tooltip = Tooltip()
                    }
                    to.tooltip.text = "${data.target}"
                    svg.text = "${pe.svg}"
                    if(data.pathElement is SVGCurve) {
                        interpolate.isDisable = false
                    }
                }
                is SVGReversePath -> svg.text = "reverse edge"
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
            ReadOnlyObjectWrapper(it.value.x)
        }
        cy.setCellValueFactory {
            ReadOnlyObjectWrapper(it.value.y)
        }
        cz.setCellValueFactory {
            ReadOnlyObjectWrapper(it.value.z)
        }
        coords.columns.addAll(cx, cy, cz)

        canvas.focus.addListener { ov, oe, ne -> e = ne }
        from.isEditable = false; to.isEditable = false;
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
                hbox { label("ia:"); tfia=textfield { text = "00.0000"; }; this+"sum:";this+sia }
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
