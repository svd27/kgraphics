package ch.passenger.kotlin.graphics.javafx.mesh.components

import ch.passenger.kotlin.graphics.javafx.mesh.canvas.FXMeshCanvas
import ch.passenger.kotlin.graphics.mesh.Face
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.SelectionMode
import javafx.scene.control.Tooltip
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent

class FaceListView<H,V,F>(val canvas: FXMeshCanvas<H, V, F>) : ListView<Face<H, V, F>>(FXCollections.observableList(canvas.mesh.faces.toList())) {
    val addhandler: (Face<H, V, F>) -> Unit = { getItems().add(it) }
    val removehandler: (Face<H, V, F>) -> Unit = { getItems().remove(it) }
    val dblClickObservers : MutableSet<(Face<H, V, F>)->Unit> = hashSetOf()

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
        setCellFactory {object : ListCell<Face<H, V, F>>() {

            init {
                setTooltip(Tooltip())
                this.getText()
                addEventFilter(MouseEvent.MOUSE_CLICKED) {
                    if(it.getButton()== MouseButton.PRIMARY && it.getClickCount()==2) {
                        val lc = it.getSource() as ListCell<Face<H, V, F>>
                        dblClickObservers.forEach { it(lc.getItem()) }
                    }
                }
            }
            override fun updateItem(item: Face<H, V, F>?, empty: Boolean) {
                super.updateItem(item, empty)
                if(item!=null) {
                    getTooltip().setText("chain: ${item.edge().map { it.origin.id }.joinToString("->")}")
                    setText("${item.name}")
                }
            }
        }}
    }

}