package rs530anim.view

import javafx.scene.Group
import javafx.scene.control.Button
import javafx.scene.control.Tooltip
import javafx.scene.paint.Color
import javafx.scene.shape.Polygon
import javafx.scene.shape.Rectangle

object DarkUi {
    val ink = Color.rgb(216, 216, 222)

    fun iconButton(tip: String, graphic: javafx.scene.Node, action: () -> Unit): Button =
        Button().apply {
            this.graphic = graphic
            tooltip = Tooltip(tip)
            prefWidth = 32.0
            prefHeight = 26.0
            minWidth = 32.0
            setOnAction { action() }
        }

    fun playGraphic(): javafx.scene.Node {
        val tri = Polygon(5.0, 2.0, 16.0, 10.0, 5.0, 18.0)
        tri.fill = ink
        return Group(tri)
    }

    fun pauseGraphic(): javafx.scene.Node {
        val a = Rectangle(4.0, 2.0, 4.0, 16.0)
        val b = Rectangle(11.0, 2.0, 4.0, 16.0)
        a.fill = ink
        b.fill = ink
        return Group(a, b)
    }

    fun prevGraphic(): javafx.scene.Node {
        val tri = Polygon(15.0, 2.0, 4.0, 10.0, 15.0, 18.0)
        tri.fill = ink
        return Group(tri)
    }

    fun nextGraphic(): javafx.scene.Node {
        val tri = Polygon(4.0, 2.0, 15.0, 10.0, 4.0, 18.0)
        tri.fill = ink
        return Group(tri)
    }

    fun firstGraphic(): javafx.scene.Node {
        val bar = Rectangle(2.0, 2.0, 3.0, 16.0)
        bar.fill = ink
        val tri = Polygon(16.0, 2.0, 6.0, 10.0, 16.0, 18.0)
        tri.fill = ink
        return Group(bar, tri)
    }

    fun lastGraphic(): javafx.scene.Node {
        val tri = Polygon(2.0, 2.0, 12.0, 10.0, 2.0, 18.0)
        tri.fill = ink
        val bar = Rectangle(13.0, 2.0, 3.0, 16.0)
        bar.fill = ink
        return Group(tri, bar)
    }
}
