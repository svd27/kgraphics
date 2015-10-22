package ch.passenger.kotlin.graphics.javafx.mesh.components

import ch.passenger.kotlin.graphics.javafx.mesh.canvas.FXMeshCanvas
import ch.passenger.kotlin.graphics.mesh.Face
import ch.passenger.kotlin.graphics.util.logging.d
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.SelectionMode
import javafx.scene.control.Tooltip
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import org.slf4j.LoggerFactory

class FaceListView<H:Any,V:Any,F:Any>(val canvas: FXMeshCanvas<H, V, F>) : ListView<Face<H, V, F>>(FXCollections.observableList(canvas.mesh.faces.toList())) {
    private val log = LoggerFactory.getLogger(this.javaClass)
    val addhandler: (Face<H, V, F>) -> Unit = {
        log.d{"items: was ${items.size()} -> ${canvas.mesh.faces.count()}"}
        setItems(FXCollections.observableList(canvas.mesh.faces.toList()))
    }
    val removehandler: (Face<H, V, F>) -> Unit = {
        log.d{"items: was ${items.size()} -> ${canvas.mesh.faces.count()}"}
        setItems(FXCollections.observableList(canvas.mesh.faces.toList()))
    }
    val dblClickObservers : MutableSet<(Face<H, V, F>)->Unit> = hashSetOf()

    init {
        selectionModel.selectionMode = SelectionMode.MULTIPLE

        selectionModel.selectedItems.addListener(object: ListChangeListener<Face<H, V, F>> {
            override fun onChanged(c: ListChangeListener.Change<out Face<H, V, F>>) {
                while(c.next()) {
                    canvas.markedFaces.removeAll(c.removed)
                    canvas.markedFaces.addAll(c.addedSubList)
                    canvas.markedEdges.clear()
                    c.addedSubList.forEach {
                        canvas.markedEdges.addAll(it.edge())
                    }
                }
            }
        })

        canvas.mesh.addFaceHandler(addhandler, removehandler)
        setCellFactory {object : ListCell<Face<H, V, F>>() {

            init {
                tooltip = Tooltip()
                this.text
                addEventFilter(MouseEvent.MOUSE_CLICKED) {
                    if(it.button == MouseButton.PRIMARY && it.clickCount ==2) {
                        val lc = it.source as ListCell<Face<H, V, F>>
                        dblClickObservers.forEach { it(lc.item) }
                    }
                }
            }
            override fun updateItem(item: Face<H, V, F>?, empty: Boolean) {
                super.updateItem(item, empty)
                if(item!=null) {
                    tooltip.text = "${if(item.parent!=canvas.mesh.NOFACE) "p: ${item.parent}" else""} chain: ${item.edge().map { it.origin.id }.joinToString("->")}"
                    text = "${item}"
                }
            }
        }}
    }

}