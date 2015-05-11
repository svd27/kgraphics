package ch.passenger.kotlin.graphics.javafx.mesh.components

import ch.passenger.kotlin.graphics.javafx.mesh.canvas.FXMeshCanvas
import ch.passenger.kotlin.graphics.math.MatrixF
import ch.passenger.kotlin.graphics.math.VectorF
import ch.passenger.kotlin.graphics.mesh.Face
import ch.passenger.kotlin.graphics.mesh.HalfEdge
import ch.passenger.kotlin.graphics.mesh.Mesh
import ch.passenger.kotlin.graphics.mesh.Vertex
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
import kotlin.reflect.KClass

/**
 * Created by svd on 09/05/2015.
 */
class MeshCanvasToolbar<H,V,F>(val canvas:FXMeshCanvas<H,V,F>) : javafx.scene.control.ToolBar() {
    init {
        val tb = this
        val tbg = ToggleGroup()
        val bView = ToggleButton("View")
        bView.setId(FXMeshCanvas.Modes.VIEW.name())
        val bAddVertex = ToggleButton("v+")
        bAddVertex.setId(FXMeshCanvas.Modes.ADDVERTEX.name())
        val bRemoveVertex = ToggleButton("v-")
        bRemoveVertex.setId(FXMeshCanvas.Modes.REMOVEVERTEX.name())
        val bAddEdge = ToggleButton("e+")
        bAddEdge.setId(FXMeshCanvas.Modes.ADDEDGE.name())
        tbg.getToggles().addAll(bView, bAddVertex, bRemoveVertex, bAddEdge)
        tbg.selectedToggleProperty().addListener { ov, told, tnew: Toggle? ->
            val toggle = tbg.getSelectedToggle()
            if (toggle is ToggleButton) {
                val m = FXMeshCanvas.Modes.valueOf(toggle.getId())
                if (canvas.mode.get() != m) canvas.mode.set(m)
            }
        }
        canvas.mode.addListener { ov, mold, mnew ->
            val toggle = tbg.getSelectedToggle()
            if (toggle is ToggleButton) {
                if (toggle.getId() != mnew.name()) {
                    tbg.selectToggle(tbg.getToggles().first { it is ToggleButton && it.getId() == mnew.name() })
                }
            }
        }
        tbg.selectToggle(tbg.getToggles().first())
        tb.getItems().addAll(bView, bAddVertex, bRemoveVertex, bAddEdge)
        tb.getItems().add(Separator(Orientation.VERTICAL))
        val angles = ToggleButton("|a"); angles.selectedProperty().bindBidirectional(canvas.lblangles)
        val vertices = ToggleButton("|v"); vertices.selectedProperty().bindBidirectional(canvas.lblvertices)
        val lnone = ToggleButton("| ")
        val lg = ToggleGroup()
        lg.getToggles().addAll(lnone, vertices, angles)
        lg.selectToggle(lg.getToggles()[0])
        tb.getItems().addAll(lnone, vertices, angles)
    }
}

class MeshCanvasStatusline<H,V,F>(val canvas:FXMeshCanvas<H,V,F>) : HBox() {
    init {
        val stx = TextField()
        stx.textProperty().bind(canvas.currentMouseTransformedX.asString("%.2f"))
        stx.setEditable(false)
        val sty = TextField()
        sty.textProperty().bind(canvas.currentMouseTransformedY.asString("%.2f"))
        sty.setEditable(false)
        val ctx = TextField()
        ctx.textProperty().bind(canvas.context.asString())
        val ctxNext = TextField()
        val ctxPrev = TextField()
        val ctxLeft = TextField()
        canvas.context.addListener { ov, oe, ne ->
            ctxNext.setText("${ne.next}")
            ctxPrev.setText("${ne.previous}")
            ctxLeft.setText("${ne.left}")
        }
        getChildren().addAll(Label("X:"), stx, Label("Y:"), sty, Label("e:"), ctx, Label("n:"), ctxNext, Label("p:"), ctxPrev, Label("f:"), ctxLeft)

    }
}

class MeshItemsAccordion<H,V,F>(canvas:FXMeshCanvas<H,V,F>) : Accordion() {
    val vertexListView: VertexListView<H,V,F> = VertexListView(canvas)
    val edgeListView: EdgeListView<H,V,F> = EdgeListView(canvas)
    val faceListView: FaceListView<H,V,F> = FaceListView(canvas)
    init {
        val tpv = TitledPane("Vertices", ScrollPane(vertexListView))
        val tpe = TitledPane("Edges", ScrollPane(edgeListView))
        val tpf = TitledPane("Faces", ScrollPane(faceListView))
        val accordeon = this
        accordeon.getPanes() add tpv
        accordeon.getPanes() add tpe
        accordeon.getPanes() add tpf
        accordeon.expandedPaneProperty().addListener { ov, old, new:TitledPane? ->
            when(new) {
                tpv -> {
                    edgeListView.getSelectionModel().clearSelection()
                    faceListView.getSelectionModel().clearSelection()
                }
                tpe -> {
                    vertexListView.getSelectionModel().clearSelection()
                    faceListView.getSelectionModel().clearSelection()
                }
                tpf -> {
                    edgeListView.getSelectionModel().clearSelection()
                    vertexListView.getSelectionModel().clearSelection()
                }
            }
        }
    }
}

class MeshScene<H,V,F>(mesh: Mesh<H,V,F>, meshWidth:Double, invertY:Boolean,
                       ef:(Vertex<H,V,F>, Vertex<H,V,F>)->H , vf:(VectorF)->V,
                       kv:KClass<V>, ke:KClass<H>, kf:KClass<F>,
                       width:Double, height:Double, fill: Paint = Color.WHITE, depthBuffer:Boolean=false,
                antiAliasing:SceneAntialiasing = SceneAntialiasing.DISABLED) :
        Scene(BorderPane(), width, height, depthBuffer, antiAliasing) {
    init {
        setFill(fill)
        val bp = getRoot() as BorderPane
        val dim = Math.max(mesh.extent.max.x-mesh.extent.min.x, mesh.extent.max.y-mesh.extent.min.y)
        val delta = meshWidth/dim
        val base = MatrixF.scale(VectorF(delta, if(invertY) -delta else delta, delta))
        val canvas = FXMeshCanvas(mesh, base, vf, ef, kv, ke, kf)
        val slider = Slider(.2, 5.0, 1.0)
        slider.setShowTickLabels(true)
        slider.setShowTickMarks(true)
        slider.valueProperty().bindBidirectional(canvas.zoom)
        bp.setLeft(VBox(MeshItemsAccordion(canvas), slider))
        bp.setCenter(ScrollPane(canvas))
        bp.setBottom(MeshCanvasStatusline(canvas))
        bp.setTop(MeshCanvasToolbar(canvas))
    }
}

