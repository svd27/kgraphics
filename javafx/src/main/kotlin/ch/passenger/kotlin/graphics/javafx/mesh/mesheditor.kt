package ch.passenger.kotlin.graphics.javafx.mesh

import ch.passenger.kotlin.graphics.geometry.AlignedCube
import ch.passenger.kotlin.graphics.javafx.mesh.components.MeshScene
import ch.passenger.kotlin.graphics.javafx.svg.SVGCanvasScene
import ch.passenger.kotlin.graphics.javafx.util.item
import ch.passenger.kotlin.graphics.javafx.util.menu
import ch.passenger.kotlin.graphics.javafx.util.menubar
import ch.passenger.kotlin.graphics.math.VectorF
import ch.passenger.kotlin.graphics.mesh.Mesh
import ch.passenger.kotlin.graphics.mesh.MeshDataFactory
import ch.passenger.kotlin.graphics.mesh.Vertex
import ch.passenger.kotlin.graphics.mesh.svg.SVGMeshData
import ch.passenger.kotlin.graphics.mesh.svg.createMesh
import ch.passenger.kotlin.graphics.mesh.svg.parseSVGPath
import ch.passenger.kotlin.graphics.util.logging.e
import ch.passenger.kotlin.graphics.util.logging.em
import ch.passenger.kotlin.graphics.util.svg.font.SVGFont
import ch.passenger.kotlin.graphics.util.svg.font.SVGGlyph
import javafx.application.Application
import javafx.collections.FXCollections
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.BorderPane
import javafx.scene.layout.FlowPane
import javafx.scene.paint.Color
import javafx.stage.FileChooser
import javafx.stage.Stage
import javafx.stage.StageStyle
import org.slf4j.LoggerFactory
import org.slf4j.helpers.BasicMarker
import java.io.BufferedReader
import java.io.FileReader
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.reflect.KClass

/**
 * Created by svd on 09/05/2015.
 */

class MeshEditor() : Application() {
    private val log = LoggerFactory.getLogger(MeshEditor::class.java)
    override fun start(primaryStage: Stage) {
        val mb = menubar {
            useSystemMenuBarProperty().set(true)
            menu("File") {
                item("New Generic") {
                    setOnAction {
                        val stage = Stage()
                        val mesh = Mesh<Unit,Unit,Unit >(AlignedCube.around(VectorF(0, 0, 0), 1f), MeshDataFactory.from(Unit,
                                {v-> }, {v0,v1->}, {e, p -> }))
                        stage.scene = MeshScene(mesh, 1000.0, Unit::class, Unit::class, Unit::class, 600.0, 600.0)
                        stage.show()
                    }
                }
                item("Load SVG") {
                    setOnAction {
                        val fl = FileChooser()
                        fl.extensionFilters.add(FileChooser.ExtensionFilter("SVG Font", "*.svg"))
                        val file = fl.showOpenDialog(primaryStage)
                        if(file!=null) {
                            val f = SVGFont.read(BufferedReader(FileReader(file)))
                            if (f!=null) {
                                val l = ListView<SVGGlyph>(FXCollections.observableList(f.names.values().sortedBy { it.name }))
                                l.setCellFactory {object : ListCell<SVGGlyph>() {
                                    init {
                                        tooltip = Tooltip()
                                        this.text
                                    }
                                    override fun updateItem(item: SVGGlyph?, empty: Boolean) {
                                        super.updateItem(item, empty)
                                        if(item!=null) {
                                            tooltip.text = "${item.unicode} ${item.xadv}"
                                            text = "${item.name}"
                                        }
                                    }
                                }}
                                val taError = TextArea()

                                val bopen = Button("Open")
                                bopen.disableProperty().bind(l.selectionModel.selectedIndexProperty().lessThan(0))
                                bopen.setOnAction {
                                    val g = l.selectionModel.selectedItems
                                    taError.text = ""
                                    g.forEach {
                                        try {
                                            taError.appendText(it.path)
                                            val ps = parseSVGPath(it.path)
                                            val klass = SVGMeshData::class
                                            val m = createMesh<SVGMeshData, SVGMeshData, SVGMeshData>(ps)
                                            val ef:(v0: Vertex<SVGMeshData, SVGMeshData, SVGMeshData>, v1: Vertex<SVGMeshData,SVGMeshData,SVGMeshData>)->SVGMeshData =
                                                    {v0: Vertex<SVGMeshData,SVGMeshData,SVGMeshData>, v1: Vertex<SVGMeshData,SVGMeshData,SVGMeshData> -> SVGMeshData.reverse()}
                                            val med = MeshScene(m, 1000.0, kv = klass, ke = klass, kf = klass, width = 800.0, height = 600.0)
                                            val stage = Stage(); stage.title = "${it.name}"
                                            stage.scene = med
                                            stage.show()
                                        } catch(e:Exception) {
                                            log.e(e) {
                                                "${it.name}:${it.unicode} {}"
                                            }
                                            taError.isWrapText = true
                                            taError.text = ""
                                            taError.appendText(it.path+"\n")
                                            taError.appendText(e.getMessage())
                                            taError.appendText("\n")
                                            val ss = StringWriter()
                                            e.printStackTrace(PrintWriter(ss))
                                            taError.appendText(ss.toString())
                                        }
                                    }
                                }
                                val bp = BorderPane()
                                l.minWidthProperty().set(100.0)
                                bp.center = ScrollPane(l); bp.bottom = bopen; bp.right = taError
                                val scene = Scene(bp, 400.0, 200.0)
                                val stage = Stage()
                                stage.title = "Path Chooser ${file.name}"
                                stage.scene = scene
                                stage.show()
                            }
                        }
                    }
                }
                item("SVG Playground") {
                    setOnAction {
                        val scene = SVGCanvasScene(400.0, 400.0)
                        val stage = Stage()
                        stage.title = "Play SVG"
                        stage.scene = scene
                        stage.show()
                    }
                }
            }
        }

        val bexit = Button("Exit")
        bexit.setOnAction { System.exit(0) }
        val flow = FlowPane(mb, bexit)
        val scene = Scene(flow, 50.0, 50.0, Color.TRANSPARENT)

        primaryStage.scene = scene
        primaryStage.title = "Main"
        primaryStage.x = 0.0; primaryStage.y = 0.0
        primaryStage.show()
        //primaryStage.setIconified(true)
    }
}

fun main(args: Array<String>) {
    Application.launch(MeshEditor::class.java, *args)
}