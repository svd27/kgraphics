package ch.passenger.kotlin.graphics.javafx.mesh.components

import ch.passenger.kotlin.graphics.javafx.mesh.canvas.FXMeshCanvas
import ch.passenger.kotlin.graphics.javafx.util.fromEvents
import ch.passenger.kotlin.graphics.mesh.HalfEdge
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.SelectionMode
import javafx.scene.control.Tooltip
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent

class EdgeListView<H:Any,V:Any,F:Any>(val canvas: FXMeshCanvas<H, V, F>) : ListView<HalfEdge<H, V, F>>(FXCollections.observableList(canvas.mesh.edges.toList())) {
    val addhandler: (HalfEdge<H, V, F>) -> Unit = { items.add(it) }
    val removehandler: (HalfEdge<H, V, F>) -> Unit = { items.remove(it) }

    init {
        selectionModel.selectionMode = SelectionMode.MULTIPLE

        selectionModel.selectedItems.addListener(object: ListChangeListener<HalfEdge<H, V, F>> {
            override fun onChanged(c: ListChangeListener.Change<out HalfEdge<H, V, F>>) {
                while(c.next()) {
                    canvas.markedEdges.removeAll(c.removed)
                    canvas.markedEdges.addAll(c.addedSubList)
                }
            }
        })

        canvas.mesh.addEdgeHandler(addhandler, removehandler)
        setCellFactory {object : ListCell<HalfEdge<H, V, F>>() {

            init {
                tooltip = Tooltip()
                this.text
                addEventFilter(MouseEvent.MOUSE_CLICKED) {
                    if(it.button == MouseButton.PRIMARY && it.clickCount ==2) {
                        val lc = it.source as ListCell<HalfEdge<H, V, F>>
                        if(lc.item !=canvas.mesh.NOEDGE)
                        canvas.focus.set(lc.item)
                    }
                }
            }
            override fun updateItem(item: HalfEdge<H, V, F>?, empty: Boolean) {
                super.updateItem(item, empty)
                if(item!=null) {
                    tooltip.text = "n: ${item.next} p: ${item.previous} f: ${item.left}"
                    text = "$item"
                }
            }
        }}
    }

}