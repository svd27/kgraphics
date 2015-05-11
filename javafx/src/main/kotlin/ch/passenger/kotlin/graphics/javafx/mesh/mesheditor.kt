package ch.passenger.kotlin.graphics.javafx.mesh

import ch.passenger.kotlin.graphics.geometry.AlignedCube
import ch.passenger.kotlin.graphics.javafx.mesh.components.MeshScene
import ch.passenger.kotlin.graphics.math.VectorF
import ch.passenger.kotlin.graphics.mesh.Mesh
import ch.passenger.kotlin.graphics.mesh.Vertex
import ch.passenger.kotlin.graphics.mesh.svg.SVGMeshData
import ch.passenger.kotlin.graphics.mesh.svg.createMesh
import ch.passenger.kotlin.graphics.mesh.svg.parseSVGPath
import ch.passenger.kotlin.graphics.util.svg.font.SVGFont
import ch.passenger.kotlin.graphics.util.svg.font.SVGGlyph
import javafx.application.Application
import javafx.collections.FXCollections
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.BorderPane
import javafx.scene.paint.Color
import javafx.stage.FileChooser
import javafx.stage.Stage
import javafx.stage.StageStyle
import java.io.BufferedReader
import java.io.FileReader
import kotlin.reflect.KClass

/**
 * Created by svd on 09/05/2015.
 */

class MeshEditor() : Application() {
    override fun start(primaryStage: Stage) {
        val mb = MenuBar()
        val mn = MenuItem("New Generic")
        mn.setOnAction {
            val stage = Stage()
            val mesh = Mesh<Unit,Unit,Unit >(AlignedCube.around(VectorF(0, 0, 0), 1f), {e, p ->})
            stage.setScene(MeshScene(mesh, 1000.0, false, {v0, v1 -> }, {},
                    Unit::class, Unit::class, Unit::class,
                    600.0, 600.0))
            stage.show()
        }
        val msvg = MenuItem("Load SVG")
        msvg.setOnAction {
            val fl = FileChooser()
            fl.getExtensionFilters().add(FileChooser.ExtensionFilter("SVG Font", "*.svg"))
            val file = fl.showOpenDialog(primaryStage)
            if(file!=null) {
                val f = SVGFont.read(BufferedReader(FileReader(file)))
                if (f!=null) {
                    val l = ListView<SVGGlyph>(FXCollections.observableList(f.names.values().sortBy{it.name}))
                    l.setCellFactory {object : ListCell<SVGGlyph>() {
                        init {
                            setTooltip(Tooltip())
                            this.getText()
                        }
                        override fun updateItem(item: SVGGlyph?, empty: Boolean) {
                            super.updateItem(item, empty)
                            if(item!=null) {
                                getTooltip().setText("${item.unicode} ${item.xadv}")
                                setText("${item.name}")
                            }
                        }
                    }}

                    val bopen = Button("Open")
                    bopen.disableProperty().bind(l.getSelectionModel().selectedIndexProperty().lessThan(0))
                    bopen.setOnAction {
                        val g = l.getSelectionModel().getSelectedItems()
                        g.forEach {
                            val ps = parseSVGPath(it.path)
                            val klass = SVGMeshData::class
                            val m = createMesh<SVGMeshData, SVGMeshData, SVGMeshData>(ps, null, {md, v -> md},
                                    {md, v0, v1 -> md}, {md, e,p -> md}) as Mesh<SVGMeshData,SVGMeshData,SVGMeshData>
                            val ef:(v0: Vertex<SVGMeshData,SVGMeshData,SVGMeshData>, v1: Vertex<SVGMeshData,SVGMeshData,SVGMeshData>)->SVGMeshData =
                                    {v0: Vertex<SVGMeshData,SVGMeshData,SVGMeshData>, v1: Vertex<SVGMeshData,SVGMeshData,SVGMeshData> -> SVGMeshData.reverse()}
                            val med = MeshScene(m, 1000.0, false, ef = ef,
                            vf = {v -> SVGMeshData.reverse()}, kv = klass, ke = klass, kf = klass, width = 800.0, height = 600.0)
                            val stage = Stage(); stage.setTitle("${it.name}")
                            stage.setScene(med)
                            stage.show()
                        }
                    }
                    val bp = BorderPane()
                    bp.setCenter(ScrollPane(l)); bp.setBottom(bopen)
                    val scene = Scene(bp, 400.0, 200.0)
                    val stage = Stage()
                    stage.setTitle("Path Chooser ${file.getName()}")
                    stage.setScene(scene)
                    stage.show()
                }
            }



        }
        val m = Menu("File")

        m.getItems().addAll(mn, msvg)
        mb.getMenus().addAll(m)
        mb.useSystemMenuBarProperty().set(true)

        val scene = Scene(mb, 1.0, 1.0, Color.TRANSPARENT)

        primaryStage.initStyle(StageStyle.TRANSPARENT)
        primaryStage.setScene(scene)
        primaryStage.show()
        //primaryStage.setIconified(true)
    }
}

fun main(args: Array<String>) {
    Application.launch(javaClass<MeshEditor>(), *args)
}