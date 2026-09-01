package rs530anim.view

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.ScrollPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.scene.control.Label
import javafx.scene.input.MouseEvent
import rs530anim.anim.AnimFrame
import rs530anim.anim.TransformType

/**
 * One cell per AnimFrame. Click seeks. A gold mark means the selected vskin
 * has a non-default rotate or translate in that frame.
 */
class TimelineBar(
    private val frames: () -> List<AnimFrame>,
    private val delays: () -> IntArray,
    private val current: () -> Int,
    private val selectedLabel: () -> Int?,
    private val onSeek: (Int) -> Unit,
) {
    private val row = HBox(4.0).apply {
        padding = Insets(6.0)
        alignment = Pos.CENTER_LEFT
    }
    val root = VBox(
        2.0,
        Label("timeline  • gold = selected vskin is keyed"),
        ScrollPane(row).apply {
            isFitToHeight = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
            vbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            prefHeight = 78.0
        },
    )

    fun refresh() {
        val list = frames()
        val d = delays()
        val cur = current()
        val lab = selectedLabel()
        row.children.setAll(*Array(list.size) { i ->
            cell(i, list[i], d.getOrElse(i) { 5 }, i == cur, lab)
        })
    }

    private fun cell(index: Int, frame: AnimFrame, ticks: Int, selected: Boolean, label: Int?): VBox {
        val keyed = label != null && labelKeyed(frame, label)
        val box = VBox(2.0).apply {
            alignment = Pos.CENTER
            padding = Insets(4.0, 8.0, 4.0, 8.0)
            prefWidth = 48.0
            style = if (selected) {
                "-fx-background-color: #3d4a2e; -fx-border-color: #c6e08a; -fx-border-width: 2;"
            } else {
                "-fx-background-color: #2a2a30; -fx-border-color: #555; -fx-border-width: 1;"
            }
            children += Label("$index")
            children += Label("${ticks}t")
            children += Circle(4.0, if (keyed) Color.GOLD else Color.rgb(70, 70, 76))
        }
        box.addEventHandler(MouseEvent.MOUSE_CLICKED) { onSeek(index) }
        return box
    }

    companion object {
        fun labelKeyed(frame: AnimFrame, label: Int): Boolean {
            for (type in intArrayOf(TransformType.ROTATE, TransformType.TRANSLATE, TransformType.SCALE)) {
                val v = frame.valuesForLabel(label, type) ?: continue
                val def = if (type == TransformType.SCALE) 128 else 0
                if (v.first != def || v.second != def || v.third != def) return true
            }
            return false
        }
    }
}
