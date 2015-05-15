package ch.passenger.kotlin.graphics.javafx.util

import javafx.css.Styleable
import javafx.scene.Group
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.text.Text
import javax.swing.plaf.SplitPaneUI

/**
 * svd coded this on 12/05/2015.
 */
fun Parent.plus(n: Node) {
    val me = this
    when(me) {
        is Pane -> me.getChildren() add n
        is SplitPane -> me.getItems() add n
        is Group -> me.getChildren() add n
        is MenuBar -> if(n is Menu) me.getMenus() add n else
            throw UnsupportedOperationException("u cant add $n to a menubar")
        is Menu -> if(n is MenuItem) me.getItems() add n else
            throw UnsupportedOperationException("u cant add $n to a menu")
        else -> throw UnsupportedOperationException("plus not supported for parent $this ${this.javaClass}")
    }
}


fun Pane.plus(n: Node) = getChildren().add(n)
fun Parent.plus(t:String) : Label {
    val label = Label(t)
    this+label
    return label
}

fun makeText(init:Text.()->Unit) : Text {
    val t = Text()
    t.init()
    return t
}

fun Parent.text(init:Text.()->Unit) : Text {
    val t = Text()
    this+t
    t.init()
    return t
}

fun makeTextfield(init:TextField.()->Unit) :TextField {
    val t = TextField()
    t.init()
    return t
}

fun Parent.textfield(init:TextField.()->Unit) :TextField {
    val t = TextField()
    this+t
    t.init()
    return t
}

fun makeLabel(txt:String, init:Label.()->Unit={}) : Label {
    val l = Label(txt)
    l.init()
    return l
}

fun Parent.label(txt:String, init:Label.()->Unit={}) : Label {
    val l = Label(txt)
    this+l
    l.init()

    return l
}

fun makeHbox(init:HBox.()->Unit) : HBox {
    val hb = HBox()
    hb.init()
    return hb
}

fun makeVbox(init: VBox.()->Unit) : VBox {
    val vb = VBox()
    vb.init()
    return vb
}

fun Parent.hbox(init:HBox.()->Unit) : HBox {
    val hb = HBox()
    this+hb
    hb.init()
    return hb
}

fun Parent.vbox(init: VBox.()->Unit) : VBox {
    val vb = VBox()
    this+vb
    vb.init()
    return vb
}


fun BorderPane.west(action:BorderPane.()->Node) = setLeft(this.action())
fun BorderPane.east(action:BorderPane.()->Node) = setRight(this.action())
fun BorderPane.north(action:BorderPane.()->Node) = setTop(this.action())
fun BorderPane.south(action:BorderPane.()->Node) = setBottom(this.action())
fun BorderPane.center(action:BorderPane.()->Node) = setCenter(this.action())
fun borderpane(init:BorderPane.()->Unit) : BorderPane {
    val bp = BorderPane()
    bp.init()
    return bp
}


fun makeSplitpane(init:SplitPane.()->Unit): SplitPane {
    val p = SplitPane()
    p.init()
    return p
}

fun Parent.splitpane(init:SplitPane.()->Unit): SplitPane {
    val p = SplitPane()
    this+p
    p.init()
    return p
}


fun makeScrollpane(content:Node, init:ScrollPane.()->Unit={}) : ScrollPane {
    val sp = ScrollPane(content)
    sp.init()
    return sp
}

fun Parent.scrollpane(content:Node, init:ScrollPane.()->Unit={}) : ScrollPane {
    val sp = ScrollPane(content)
    this+sp
    sp.init()
    return sp
}

fun makeAccordion(init:Accordion.()->Unit) : Accordion {
    val acc = Accordion()
    acc.init()
    return acc
}

fun Parent.accordion(init:Accordion.()->Unit) : Accordion {
    val acc = Accordion()
    acc.init()
    this+acc
    return acc
}

fun Accordion.pane(t:String, init:TitledPane.()->Unit) : TitledPane{
    val tp = TitledPane()
    tp.setText(t)
    tp.init()
    getPanes() add tp
    return tp
}


fun MenuBar.menu(title:String, init:Menu.()->Unit) : Menu {
    val m = Menu(title)
    m.init()
    getMenus().add(m)
    return m
}

fun Menu.item(title:String, init:MenuItem.()->Unit) : MenuItem {
    val mi = MenuItem(title)
    mi.init()
    getItems().add(mi)
    return mi
}

fun Menu.check(title:String, init:CheckMenuItem.()->Unit) : MenuItem {
    val mi = CheckMenuItem(title)
    mi.init()
    getItems().add(mi)
    return mi
}

fun menubar(init:MenuBar.()->Unit) : MenuBar {
    val mb = MenuBar()
    mb.init()
    return mb
}

fun makeButton(t:String, init:Button.()->Unit) : Button {
    val b = Button(t)
    b.init()
    return b
}

fun Parent.button(t:String, init:Button.()->Unit) : Button {
    val b = Button(t)
    b.init()
    this+b
    return b
}

var Control.ttip : String?
get() = if(getTooltip()==null) null else getTooltip().getText()
set(v) {
    if(getTooltip()==null) {
        setTooltip(Tooltip())
    }
    getTooltip().setText(v)
}

