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

class VertexListView<H,V,F>(canvas: FXMeshCanvas<H, V, F>) : ListView<Vertex<H, V, F>>() {
    val addhandler: (Vertex<H, V, F>) -> Unit = { getItems().add(it) }
    val removehandler: (Vertex<H, V, F>) -> Unit = { getItems().remove(it) }
    val dblClickObservers : MutableSet<(Vertex<H, V, F>)->Unit> = hashSetOf()

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
        setCellFactory {object : ListCell<Vertex<H, V, F>>() {

            init {
                setTooltip(Tooltip())
                this.getText()
                addEventFilter(MouseEvent.MOUSE_CLICKED) {
                    if(it.getButton()== MouseButton.PRIMARY && it.getClickCount()==2) {
                        val lc = it.getSource() as ListCell<Vertex<H, V, F>>
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