package ch.passenger.kotlin.graphics.javafx.mesh.components

import ch.passenger.kotlin.graphics.geometry.Circle
import ch.passenger.kotlin.graphics.geometry.Triangle
import ch.passenger.kotlin.graphics.javafx.mesh.canvas.FXMeshCanvas
import ch.passenger.kotlin.graphics.javafx.mesh.canvas.fill
import ch.passenger.kotlin.graphics.javafx.mesh.canvas.stroke
import ch.passenger.kotlin.graphics.javafx.util.*
import ch.passenger.kotlin.graphics.math.MatrixF
import ch.passenger.kotlin.graphics.math.Rectangle2D
import ch.passenger.kotlin.graphics.mesh.*
import ch.passenger.kotlin.graphics.util.logging.d
import javafx.geometry.Orientation
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.SceneAntialiasing
import javafx.scene.control.*
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.paint.Paint
import javafx.stage.Stage
import javafx.stage.StageStyle
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

/**
 * Created by svd on 09/05/2015.
 */
class MeshCanvasToolbar<H:Any,V:Any,F:Any>(val canvas:FXMeshCanvas<H,V,F>) : javafx.scene.control.ToolBar() {
    init {
        val tb = this
        val tbg = ToggleGroup()
        val bView = ToggleButton("View")
        bView.id = FXMeshCanvas.Modes.VIEW.name()
        val bAddVertex = ToggleButton("v+")
        bAddVertex.id = FXMeshCanvas.Modes.ADDVERTEX.name()
        val bRemoveVertex = ToggleButton("v-")
        bRemoveVertex.id = FXMeshCanvas.Modes.REMOVEVERTEX.name()
        val bAddEdge = ToggleButton("e+")
        bAddEdge.id = FXMeshCanvas.Modes.ADDEDGE.name()
        tbg.toggles.addAll(bView, bAddVertex, bRemoveVertex, bAddEdge)
        tbg.selectedToggleProperty().addListener { ov, told, tnew: Toggle? ->
            val toggle = tbg.selectedToggle
            if (toggle is ToggleButton) {
                val m = FXMeshCanvas.Modes.valueOf(toggle.id)
                if (canvas.mode.get() != m) canvas.mode.set(m)
            }
        }
        canvas.mode.addListener { ov, mold, mnew ->
            val toggle = tbg.selectedToggle
            if (toggle is ToggleButton) {
                if (toggle.id != mnew.name()) {
                    tbg.selectToggle(tbg.toggles.first { it is ToggleButton && it.id == mnew.name() })
                }
            }
        }
        tbg.selectToggle(tbg.toggles.first())
        tb.items.addAll(bView, bAddVertex, bRemoveVertex, bAddEdge)
        tb.items.add(Separator(Orientation.VERTICAL))
        val angles = ToggleButton("|a"); angles.selectedProperty().bindBidirectional(canvas.lblangles)
        val vertices = ToggleButton("|v"); vertices.selectedProperty().bindBidirectional(canvas.lblvertices)
        val lnone = ToggleButton("| ")
        val lg = ToggleGroup()
        lg.toggles.addAll(lnone, vertices, angles)
        lg.selectToggle(lg.toggles[0])
        tb.items.addAll(lnone, vertices, angles)
    }
}

class MeshCanvasStatusline<H:Any,V:Any,F:Any>(val canvas:FXMeshCanvas<H,V,F>) : HBox() {
    init {
        val stx = TextField()
        stx.textProperty().bind(canvas.currentMouseTransformedX.asString("%.2f"))
        stx.isEditable = false
        val sty = TextField()
        sty.textProperty().bind(canvas.currentMouseTransformedY.asString("%.2f"))
        sty.isEditable = false
        val ctx = TextField()
        ctx.textProperty().bind(canvas.context.asString())
        val ctxNext = TextField()
        val ctxPrev = TextField()
        val ctxLeft = TextField()
        canvas.context.addListener { ov, oe, ne ->
            ctxNext.text = "${ne.next}"
            ctxPrev.text = "${ne.previous}"
            ctxLeft.text = "${ne.left}"
        }
        children.addAll(Label("X:"), stx, Label("Y:"), sty, Label("e:"), ctx, Label("n:"), ctxNext, Label("p:"), ctxPrev, Label("f:"), ctxLeft)

    }
}

class MeshItemsAccordion<H:Any,V:Any,F:Any>(canvas:FXMeshCanvas<H,V,F>) : Accordion() {
    private val log = LoggerFactory.getLogger(this.javaClass)
    val vertexListView: VertexListView<H,V,F> = VertexListView(canvas)
    val edgeListView: EdgeListView<H,V,F> = EdgeListView(canvas)
    val faceListView: FaceListView<H,V,F> = FaceListView(canvas)
    init {
        val contvb = makeVbox {
            val meshContainerTreeView = MeshContainerTreeView(canvas)
            this+ScrollPane(meshContainerTreeView)
            button("Refresh") {
                setOnAction { meshContainerTreeView.create() }
            }
            button("Faces") {
                setOnAction { MeshOperations(canvas.mesh).createFaces() }
            }
            button("Close Holes") {
                setOnAction { MeshOperations(canvas.mesh).closeHoles() }
            }
        }

        val fcont = makeVbox {
            scrollpane(faceListView)
            button("Monotonie") {
                setOnAction {
                    val f = faceListView.selectionModel.selectedItem
                    if(f!=null) {
                        faceListView.selectionModel.clearSelection()
                        val monos = MeshOperations(canvas.mesh).classifyMonotnie(f.edge)
                        canvas.painters.add {
                            save()
                            lineWidth = canvas.px
                            stroke = Color.BLACK
                            fill = Color.BLACK
                            monos.forEach {
                                log.d{"mono: ${it.value}"}
                                when (it.value) {
                                    MeshOperations.Monotonie.START -> {
                                        val r = Rectangle2D.around(it.key.origin.v, canvas.cpx(20))
                                        log.d{"stroke START $r"}
                                        stroke(r)
                                    }
                                    MeshOperations.Monotonie.END -> {
                                        val rf = Rectangle2D.around(it.key.origin.v, canvas.cpx(20))
                                        fill(rf)
                                    }
                                    MeshOperations.Monotonie.SPLIT -> {
                                        val t = Triangle.around(it.key.origin.v, canvas.cpx(20))
                                        fill(t)
                                    }
                                    MeshOperations.Monotonie.MERGE -> {
                                        val m = Triangle.around(it.key.origin.v, -canvas.cpx(20))
                                        log.d{"stroke MERGE $m"}
                                        fill(m)
                                    }
                                    MeshOperations.Monotonie.NORM -> {
                                        val c = Circle(it.key.origin.v, canvas.cpx(20))
                                        fill(c)
                                    }
                                }
                            }
                            restore()
                        }
                    }
                }
            }
        }
        val tpv = TitledPane("Vertices", ScrollPane(vertexListView))
        val tpe = TitledPane("Edges", ScrollPane(edgeListView))
        val tpf = TitledPane("Faces", fcont)
        val tph = TitledPane("Holes", contvb)
        val accordeon = this
        accordeon.panes add tpv
        accordeon.panes add tpe
        accordeon.panes add tpf
        accordeon.panes add tph
        accordeon.expandedPaneProperty().addListener { ov, old, new:TitledPane? ->
            when(new) {
                tpv -> {
                    edgeListView.selectionModel.clearSelection()
                    faceListView.selectionModel.clearSelection()
                }
                tpe -> {
                    vertexListView.selectionModel.clearSelection()
                    faceListView.selectionModel.clearSelection()
                }
                tpf -> {
                    edgeListView.selectionModel.clearSelection()
                    vertexListView.selectionModel.clearSelection()
                }
                tph -> {
                    vertexListView.selectionModel.clearSelection()
                    edgeListView.selectionModel.clearSelection()
                    faceListView.selectionModel.clearSelection()
                }
            }
        }
    }
}

class MeshScene<H:Any,V:Any,F:Any>(mesh: Mesh<H, V, F>, minMeshWidth: Double, kv: KClass<V>,
                       ke: KClass<H>, kf: KClass<F>,
                       width: Double, height: Double, fill: Paint = Color.WHITE,
                       depthBuffer: Boolean = false, antiAliasing: SceneAntialiasing = SceneAntialiasing.DISABLED) :
        Scene(BorderPane(), width, height, depthBuffer, antiAliasing) {
    var inspector : Stage? = null
    init {
        setFill(fill)
        val bp = root as BorderPane
        val canvas = FXMeshCanvas(mesh, minMeshWidth, kv, ke, kf)
        val slider = Slider(.2, 5.0, 1.0)
        slider.isShowTickLabels = true
        slider.isShowTickMarks = true
        slider.valueProperty().bindBidirectional(canvas.zoom)
        bp.left = VBox(MeshItemsAccordion(canvas), slider)
        bp.center = ScrollPane(canvas)
        bp.bottom = MeshCanvasStatusline(canvas)

        val mb = menubar {
            isUseSystemMenuBar = true
            menu("View") {
                item("Inspector") {
                    setOnAction {
                        if(inspector!=null && !(inspector?.isShowing ?:true)) {
                            inspector?.show()
                            inspector?.toFront()
                        }
                        else {
                            val ei = MeshInspector(canvas)
                            val scene = Scene(ei, 200.0, 300.0)
                            ei.prefWidthProperty().bind(scene.widthProperty())
                            ei.prefHeightProperty().bind(scene.heightProperty())
                            val stage = Stage()
                            inspector=stage
                            stage.initStyle(StageStyle.UTILITY)
                            stage.scene = scene
                            stage.show()
                        }
                    }
                }
                check("Show Edge Handles") {
                    selectedProperty().bindBidirectional(canvas.drawEdgeHandles)
                }
                check("InvertY") {
                    selectedProperty().bindBidirectional(canvas.invertY)
                }
            }
        }

        bp.top = VBox(mb, MeshCanvasToolbar(canvas))
    }
}

class MeshContainerTreeView<H:Any,V:Any,F:Any>(val canvas:FXMeshCanvas<H,V,F>) : TreeView<HalfEdge<H,V,F>>() {
    val log = LoggerFactory.getLogger(this.javaClass)
    init {
        val root = TreeItem(canvas.mesh.NOEDGE)

        this.root = root
        this.showRootProperty().set(false)
        create()
        selectionModel.selectedItemProperty().addListener { ov, oti, nti ->
            if(nti!=null && nti.value is HalfEdge<H,V,F>)
                canvas.transientMarksE.addAll(nti.value)
        }
    }

    fun create() {
        val root = TreeItem(canvas.mesh.NOEDGE)
        val containers = MeshOperations(canvas.mesh).containment()
        log.debug("received ${containers.size} holes $containers")
        containers.collect(root) {
            tn, e ->
            val ntn = TreeItem(e)
            tn.children add ntn
            ntn
        }
        setRoot(root)
        this.setCellFactory {
            tv ->
            object : TreeCell<HalfEdge<H, V, F>>() {
                override fun updateItem(item: HalfEdge<H, V, F>?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (!empty && item != null) {
                        text = "$item"
                        this.ttip = "p: ${containers.path(item)} lvl: ${containers.level(item)}"
                    }
                }
            }
        }
    }

}

