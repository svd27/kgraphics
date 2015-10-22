package ch.passenger.kotlin.graphics.javafx.mesh.components

import ch.passenger.kotlin.graphics.javafx.mesh.canvas.FXMeshCanvas
import ch.passenger.kotlin.graphics.mesh.Vertex
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.SelectionMode
import javafx.scene.control.Tooltip
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent

class VertexListView<H:Any,V:Any,F:Any>(canvas: FXMeshCanvas<H, V, F>) : ListView<Vertex<H, V, F>>() {
    val addhandler: (Vertex<H, V, F>) -> Unit = { items.add(it) }
    val removehandler: (Vertex<H, V, F>) -> Unit = { items.remove(it) }
    val dblClickObservers : MutableSet<(Vertex<H, V, F>)->Unit> = hashSetOf()

    init {
        selectionModel.selectionMode = SelectionMode.MULTIPLE
        items = FXCollections.observableList(canvas.mesh.vertices.toList())
        selectionModel.selectedItems.addListener(object: ListChangeListener<Vertex<H, V, F>> {
            override fun onChanged(c: ListChangeListener.Change<out Vertex<H, V, F>>) {
                while(c.next()) {
                    canvas.markedVertices.removeAll(c.removed)
                    canvas.markedVertices.addAll(c.addedSubList)
                }
            }
        })

        canvas.mesh.addVertexHandler(addhandler, removehandler)
        setCellFactory {object : ListCell<Vertex<H, V, F>>() {

            init {
                tooltip = Tooltip()
                this.text
                addEventFilter(MouseEvent.MOUSE_CLICKED) {
                    if(it.button == MouseButton.PRIMARY && it.clickCount ==2) {
                        val lc = it.source as ListCell<Vertex<H, V, F>>
                        dblClickObservers.forEach { it(lc.item) }
                    }
                }
            }
            override fun updateItem(item: Vertex<H, V, F>?, empty: Boolean) {
                super.updateItem(item, empty)
                if(item!=null) {
                    tooltip.text = "${item.v} ${if(item.leaving!=canvas.mesh.NOEDGE) "-> ${item.leaving.destination}" else ""}"
                    text = "${item.id}"
                }
            }
        }}
    }
}