package ch.passenger.kotlin.graphics.javafx.experiment

import ch.passenger.kotlin.graphics.geometry.AlignedCube
import ch.passenger.kotlin.graphics.javafx.canvas.FXMeshCanvas
import ch.passenger.kotlin.graphics.javafx.svg.SVGFontViewer
import ch.passenger.kotlin.graphics.math.MatrixF
import ch.passenger.kotlin.graphics.math.VectorF
import ch.passenger.kotlin.graphics.mesh.Face
import ch.passenger.kotlin.graphics.mesh.HalfEdge
import ch.passenger.kotlin.graphics.mesh.Mesh
import ch.passenger.kotlin.graphics.mesh.Vertex
import ch.passenger.kotlin.graphics.util.collections.RingBuffer
import ch.passenger.kotlin.graphics.util.svg.font.FontawesomeNameMapper
import ch.passenger.kotlin.graphics.util.svg.font.SVGFont
import javafx.application.Application
import javafx.beans.binding.Binding
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.event.Event
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Stage
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.script.Bindings

/**
 * Created by svd on 06/05/2015.
 */
class FXMain() : Application() {
    val vertexBuffer : RingBuffer<Vertex<Unit,Unit,Unit>> = RingBuffer(2)
    val m = Mesh<Unit,Unit,Unit>(AlignedCube(VectorF(-1, -1, -1), VectorF(1, 1, 1)), {})
    val canvas = FXMeshCanvas(m, MatrixF.scale(VectorF(500, 500, 500, 1)), {}, {v0, v1 ->})
    override fun start(primaryStage: Stage) {

        val stx = TextField()
        stx.textProperty().bind(canvas.currentMouseTransformedX.asString("%.2f"))
        stx.setEditable(false)
        val sty = TextField()
        sty.textProperty().bind(canvas.currentMouseTransformedY.asString("%.2f"))
        sty.setEditable(false)
        val status = HBox(Label("X:"), stx, Label("Y:"), sty)
        val sp = ScrollPane(canvas)
        sp.setPrefSize(400.0, 400.0)
        val bp = BorderPane()
        bp.setCenter(sp)
        bp.setBottom(status)
        val vertexListView = VertexListView(canvas)
        val edgeListView = EdgeListView(canvas)
        val faceListView = FaceListView(canvas)
        val lblV0 = Label("v0:    ")
        val lblV1 = Label("v1:    ")
        val bmem = Button("v->")
        bmem.setOnAction { vertexListView.getSelectionModel().getSelectedItems().forEach { vertexBuffer.push(it) }
            if(vertexBuffer[0]!=null) {
                lblV0.setText("v0: ${vertexBuffer[0]!!.id}")
            }
            if(vertexBuffer[1]!=null) {
                lblV1.setText("v1: ${vertexBuffer[1]!!.id}")
            }
        }
        vertexListView.dblClickObservers add {
            vertexBuffer.push(it)
            if(vertexBuffer[0]!=null) {
                lblV0.setText("v0: ${vertexBuffer[0]!!.id}")
            }
            if(vertexBuffer[1]!=null) {
                lblV1.setText("v1: ${vertexBuffer[1]!!.id}")
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
        val accordeon = Accordion()
        val tpv = TitledPane("Vertices", ScrollPane(vertexListView))
        val tpe = TitledPane("Edges", ScrollPane(edgeListView))
        val tpf = TitledPane("Faces", ScrollPane(faceListView))
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
        val left = VBox(accordeon, HBox(bmem), HBox(lblV0, lblV1), bedge)
        bp.setLeft(left)
        bp.setTop(toolBar())
        //val svgf = SVGFontViewer(loadFont()!!)
        //val scene = Scene(svgf, 400.0, 400.0)
        val scene = Scene(bp, 400.0, 400.0)
        scene.setOnKeyPressed {
            if(it.getCharacter()=="[") {
                if(canvas.focus.get()!=canvas.mesh.NOEDGE) {
                    val e = canvas.focus.get()
                    if(e.previous!=e.NOEDGE) {
                        canvas.focus.set(e.previous)
                    }
                }
            }
            if(it.getCharacter()=="]") {
                if(canvas.focus.get()!=canvas.mesh.NOEDGE) {
                    val e = canvas.focus.get()
                    if(e.next!=e.NOEDGE) {
                        canvas.focus.set(e.next)
                    }
                }            }
        }
        primaryStage.setScene(scene)
        primaryStage.show()
    }

    fun toolBar() : ToolBar {
        val tb = ToolBar()
        val tbg = ToggleGroup()
        val bView = ToggleButton("View")
        bView.setId(FXMeshCanvas.Modes.VIEW.name())
        val bAddVertex = ToggleButton("v+")
        bAddVertex.setId(FXMeshCanvas.Modes.ADDVERTEX.name())
        val bRemoveVertex = ToggleButton("v-")
        bRemoveVertex.setId(FXMeshCanvas.Modes.REMOVEVERTEX.name())
        tbg.getToggles().addAll(bView, bAddVertex, bRemoveVertex)
        tbg.selectedToggleProperty().addListener { ov, told, tnew:Toggle? ->
            val toggle = tbg.getSelectedToggle()
            if(toggle is ToggleButton) {
                val m =FXMeshCanvas.Modes.valueOf(toggle.getId())
                if(canvas.mode.get()!=m) canvas.mode.set(m)
            }
        }
        canvas.mode.addListener { ov, mold, mnew ->
            val toggle = tbg.getSelectedToggle()
            if(toggle is ToggleButton) {
                if(toggle.getId()!=mnew.name()) {
                    tbg.selectToggle(tbg.getToggles().first {it is ToggleButton && it.getId() == mnew.name()})
                }
            }
        }
        tbg.selectToggle(tbg.getToggles().first())
        tb.getItems().addAll(bView, bAddVertex, bRemoveVertex)
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

class VertexListView<H,V,F>(canvas:FXMeshCanvas<H,V,F>) : ListView<Vertex<H,V,F>>() {
    val addhandler: (Vertex<H, V, F>) -> Unit = { getItems().add(it) }
    val removehandler: (Vertex<H, V, F>) -> Unit = { getItems().remove(it) }
    val dblClickObservers : MutableSet<(Vertex<H,V,F>)->Unit> = hashSetOf()

    init {
        getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE)
        setItems(FXCollections.observableList(canvas.mesh.vertices.toList()))
        getSelectionModel().getSelectedItems().addListener(object: ListChangeListener<Vertex<H, V, F>> {
            override fun onChanged(c: ListChangeListener.Change<out Vertex<H, V, F>>) {
                while(c.next()) {
                    canvas.markedVertices.removeAll(c.getRemoved())
                    canvas.markedVertices.addAll(c.getAddedSubList())
                }
            }
        })

        canvas.mesh.addVertexHandler(addhandler, removehandler)
        setCellFactory {object : ListCell<Vertex<H,V,F>>() {

            init {
                setTooltip(Tooltip())
                this.getText()
                addEventFilter(MouseEvent.MOUSE_CLICKED) {
                    if(it.getButton()== MouseButton.PRIMARY && it.getClickCount()==2) {
                        val lc = it.getSource() as ListCell<Vertex<H,V,F>>
                        dblClickObservers.forEach { it(lc.getItem()) }
                    }
                }
            }
            override fun updateItem(item: Vertex<H, V, F>?, empty: Boolean) {
                super.updateItem(item, empty)
                if(item!=null) {
                    getTooltip().setText("${item.v} ${if(item.leaving!=canvas.mesh.NOEDGE) "-> ${item.leaving.destination}" else ""}")
                    setText("${item.id}")
                }
            }
        }}
    }
}

class EdgeListView<H,V,F>(val canvas:FXMeshCanvas<H,V,F>) : ListView<HalfEdge<H,V,F>>(FXCollections.observableList(canvas.mesh.edges.toList())) {
    val addhandler: (HalfEdge<H, V, F>) -> Unit = { getItems().add(it) }
    val removehandler: (HalfEdge<H, V, F>) -> Unit = { getItems().remove(it) }
    val dblClickObservers : MutableSet<(HalfEdge<H,V,F>)->Unit> = hashSetOf()

    init {
        getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE)

        getSelectionModel().getSelectedItems().addListener(object: ListChangeListener<HalfEdge<H, V, F>> {
            override fun onChanged(c: ListChangeListener.Change<out HalfEdge<H, V, F>>) {
                while(c.next()) {
                    canvas.markedEdges.removeAll(c.getRemoved())
                    canvas.markedEdges.addAll(c.getAddedSubList())
                }
            }
        })

        canvas.mesh.addEdgeHandler(addhandler, removehandler)
        setCellFactory {object : ListCell<HalfEdge<H,V,F>>() {

            init {
                setTooltip(Tooltip())
                this.getText()
                addEventFilter(MouseEvent.MOUSE_CLICKED) {
                    if(it.getButton()== MouseButton.PRIMARY && it.getClickCount()==2) {
                        val lc = it.getSource() as ListCell<HalfEdge<H,V,F>>
                        dblClickObservers.forEach { it(lc.getItem()) }
                    }
                }
            }
            override fun updateItem(item: HalfEdge<H, V, F>?, empty: Boolean) {
                super.updateItem(item, empty)
                if(item!=null) {
                    getTooltip().setText("n: ${item.next} p: ${item.previous}")
                    setText("$item")
                }
            }
        }}
    }

}

class FaceListView<H,V,F>(val canvas:FXMeshCanvas<H,V,F>) : ListView<Face<H,V,F>>(FXCollections.observableList(canvas.mesh.faces.toList())) {
    val addhandler: (Face<H, V, F>) -> Unit = { getItems().add(it) }
    val removehandler: (Face<H, V, F>) -> Unit = { getItems().remove(it) }
    val dblClickObservers : MutableSet<(Face<H,V,F>)->Unit> = hashSetOf()

    init {
        getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE)

        getSelectionModel().getSelectedItems().addListener(object: ListChangeListener<Face<H, V, F>> {
            override fun onChanged(c: ListChangeListener.Change<out Face<H, V, F>>) {
                while(c.next()) {
                    canvas.markedFaces.removeAll(c.getRemoved())
                    canvas.markedFaces.addAll(c.getAddedSubList())
                }
            }
        })

        canvas.mesh.addFaceHandler(addhandler, removehandler)
        setCellFactory {object : ListCell<Face<H,V,F>>() {

            init {
                setTooltip(Tooltip())
                this.getText()
                addEventFilter(MouseEvent.MOUSE_CLICKED) {
                    if(it.getButton()== MouseButton.PRIMARY && it.getClickCount()==2) {
                        val lc = it.getSource() as ListCell<Face<H,V,F>>
                        dblClickObservers.forEach { it(lc.getItem()) }
                    }
                }
            }
            override fun updateItem(item: Face<H, V, F>?, empty: Boolean) {
                super.updateItem(item, empty)
                if(item!=null) {
                    getTooltip().setText("chain: ${item.edge().map { it.origin }.joinToString("->")}")
                    setText("${item.name}")
                }
            }
        }}
    }

}



fun main(args: Array<String>) {
    Application.launch(javaClass<FXMain>(), *args)
}