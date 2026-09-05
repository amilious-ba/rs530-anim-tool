package rs530anim.view

import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Tooltip
import javafx.scene.layout.StackPane
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
        val tri = Polygon(4.0, 2.0, 16.0, 10.0, 4.0, 18.0)
        tri.fill = ink
        return StackPane(tri).apply { prefWidth = 18.0; prefHeight = 20.0 }
    }

    fun pauseGraphic(): javafx.scene.Node {
        val a = Rectangle(4.0, 3.0, 4.0, 14.0)
        val b = Rectangle(11.0, 3.0, 4.0, 14.0)
        a.fill = ink
        b.fill = ink
        return StackPane(a, b).apply {
            prefWidth = 18.0
            prefHeight = 20.0
            alignment = Pos.CENTER
        }
    }

    fun prevGraphic(): javafx.scene.Node = skipGraphic(endRight = false)

    fun nextGraphic(): javafx.scene.Node = skipGraphic(endRight = true)

    fun firstGraphic(): javafx.scene.Node {
        val bar = Rectangle(2.0, 3.0, 3.0, 14.0)
        bar.fill = ink
        val tri = Polygon(16.0, 3.0, 6.0, 10.0, 16.0, 17.0)
        tri.fill = ink
        return StackPane(bar, tri).apply { prefWidth = 18.0; prefHeight = 20.0 }
    }

    fun lastGraphic(): javafx.scene.Node {
        val bar = Rectangle(13.0, 3.0, 3.0, 14.0)
        bar.fill = ink
        val tri = Polygon(2.0, 3.0, 12.0, 10.0, 2.0, 17.0)
        tri.fill = ink
        return StackPane(bar, tri).apply { prefWidth = 18.0; prefHeight = 20.0 }
    }

    private fun skipGraphic(endRight: Boolean): javafx.scene.Node {
        val tri = if (endRight) {
            Polygon(3.0, 3.0, 13.0, 10.0, 3.0, 17.0)
        } else {
            Polygon(15.0, 3.0, 5.0, 10.0, 15.0, 17.0)
        }
        tri.fill = ink
        return StackPane(tri).apply { prefWidth = 18.0; prefHeight = 20.0 }
    }
}
