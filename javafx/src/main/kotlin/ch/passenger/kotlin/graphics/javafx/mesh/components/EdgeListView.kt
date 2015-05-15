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

class EdgeListView<H,V,F>(val canvas: FXMeshCanvas<H, V, F>) : ListView<HalfEdge<H, V, F>>(FXCollections.observableList(canvas.mesh.edges.toList())) {
    val addhandler: (HalfEdge<H, V, F>) -> Unit = { getItems().add(it) }
    val removehandler: (HalfEdge<H, V, F>) -> Unit = { getItems().remove(it) }

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
        setCellFactory {object : ListCell<HalfEdge<H, V, F>>() {

            init {
                setTooltip(Tooltip())
                this.getText()
                addEventFilter(MouseEvent.MOUSE_CLICKED) {
                    if(it.getButton()== MouseButton.PRIMARY && it.getClickCount()==2) {
                        val lc = it.getSource() as ListCell<HalfEdge<H, V, F>>
                        if(lc.getItem()!=canvas.mesh.NOEDGE)
                        canvas.focus.set(lc.getItem())
                    }
                }
            }
            override fun updateItem(item: HalfEdge<H, V, F>?, empty: Boolean) {
                super.updateItem(item, empty)
                if(item!=null) {
                    getTooltip().setText("n: ${item.next} p: ${item.previous} f: ${item.left}")
                    setText("$item")
                }
            }
        }}
    }

}