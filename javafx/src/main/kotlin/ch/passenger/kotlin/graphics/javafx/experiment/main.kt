package ch.passenger.kotlin.graphics.javafx.experiment

import ch.passenger.kotlin.graphics.geometry.AlignedCube
import ch.passenger.kotlin.graphics.javafx.mesh.canvas.FXMeshCanvas
import ch.passenger.kotlin.graphics.javafx.mesh.components.*
import ch.passenger.kotlin.graphics.javafx.svg.SVGFontViewer
import ch.passenger.kotlin.graphics.math.MatrixF
import ch.passenger.kotlin.graphics.math.VectorF
import ch.passenger.kotlin.graphics.mesh.*
import ch.passenger.kotlin.graphics.util.collections.RingBuffer
import ch.passenger.kotlin.graphics.util.svg.font.FontawesomeNameMapper
import ch.passenger.kotlin.graphics.util.svg.font.SVGFont
import javafx.application.Application
import javafx.beans.binding.Binding
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.event.Event
import javafx.geometry.Orientation
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Stage
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Created by svd on 06/05/2015.
 */
class FXMain() : Application() {
    val vertexBuffer : RingBuffer<Vertex<Unit,Unit,Unit>> = RingBuffer(2)

    val m = Mesh<Unit,Unit,Unit>(AlignedCube(VectorF(-1, -1, -1), VectorF(1, 1, 1)), MeshDataFactory.from(Unit,
            {  },{ p, p1 ->  }, { e, p ->  })
    )
    val canvas = FXMeshCanvas(m, 500.0, Unit::class, Unit::class, Unit::class )
    override fun start(primaryStage: Stage) {
        val status = bottom()
        val sp = ScrollPane(canvas)
        sp.setPrefSize(400.0, 400.0)
        val bp = BorderPane()
        bp.center = sp
        bp.bottom = MeshCanvasStatusline(canvas)
        val meshItemsAccordion = MeshItemsAccordion(canvas)
        val vertexListView = meshItemsAccordion.vertexListView
        val edgeListView = meshItemsAccordion.edgeListView
        val faceListView = meshItemsAccordion.faceListView
        val lblV0 = Label("v0:    ")
        val lblV1 = Label("v1:    ")
        val bmem = Button("v->")
        bmem.setOnAction { vertexListView.selectionModel.selectedItems.forEach { vertexBuffer.push(it) }
            if(vertexBuffer[0]!=null) {
                lblV0.text = "v0: ${vertexBuffer[0]!!.id}"
            }
            if(vertexBuffer[1]!=null) {
                lblV1.text = "v1: ${vertexBuffer[1]!!.id}"
            }
        }
        vertexListView.dblClickObservers add {
            vertexBuffer.push(it)
            if(vertexBuffer[0]!=null) {
                lblV0.text = "v0: ${vertexBuffer[0]!!.id}"
            }
            if(vertexBuffer[1]!=null) {
                lblV1.text = "v1: ${vertexBuffer[1]!!.id}"
            }
        }
        val bedge = Button("v->e")
        bedge.setOnAction {
            if(vertexBuffer.length>1) {
                canvas.addEdge(vertexBuffer[0]!!, vertexBuffer[1]!!)
                vertexListView.fireEvent(Event(ListView.editAnyEvent<Vertex<Unit,Unit,Unit>>()))
                edgeListView.fireEvent(Event(ListView.editAnyEvent<HalfEdge<Unit,Unit,Unit>>()))
            }
        }



        val left = VBox(meshItemsAccordion, HBox(bmem), HBox(lblV0, lblV1), bedge)
        bp.left = left
        bp.top = toolBar()
        //val svgf = SVGFontViewer(loadFont()!!)
        //val scene = Scene(svgf, 400.0, 400.0)
        val scene = Scene(bp, 400.0, 400.0)
        primaryStage.scene = scene
        primaryStage.show()
    }

    private fun bottom(): HBox {
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

        val status = HBox(Label("X:"), stx, Label("Y:"), sty, Label("e:"), ctx, Label("n:"), ctxNext, Label("p:"), ctxPrev, Label("f:"), ctxLeft)
        return status
    }

    fun toolBar() : ToolBar {
        val tb = ToolBar()
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
        tbg.selectedToggleProperty().addListener { ov, told, tnew:Toggle? ->
            val toggle = tbg.selectedToggle
            if(toggle is ToggleButton) {
                val m =FXMeshCanvas.Modes.valueOf(toggle.id)
                if(canvas.mode.get()!=m) canvas.mode.set(m)
            }
        }
        canvas.mode.addListener { ov, mold, mnew ->
            val toggle = tbg.selectedToggle
            if(toggle is ToggleButton) {
                if(toggle.id !=mnew.name()) {
                    tbg.selectToggle(tbg.toggles.first {it is ToggleButton && it.id == mnew.name()})
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
        return tb
    }

    fun loadFont() : SVGFont? {
        val res = FontawesomeNameMapper::class.javaClass.getResourceAsStream("/fontawesome/_variables.scss")
        val map = FontawesomeNameMapper(BufferedReader(InputStreamReader(res)))
        val resf = SVGFont::class.javaClass.getResourceAsStream("/fontawesome/fontawesome-webfont.svg")
        val font = SVGFont.read(BufferedReader(InputStreamReader(resf)), map.names)
        return font
    }
}



fun main(args: Array<String>) {
    Application.launch(FXMain::class.java, *args)
}