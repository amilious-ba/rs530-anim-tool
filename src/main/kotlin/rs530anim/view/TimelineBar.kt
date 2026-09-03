package rs530anim.view

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tooltip
import javafx.scene.input.MouseEvent
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.shape.Polygon
import javafx.scene.shape.Rectangle
import rs530anim.anim.AnimFrame
import rs530anim.anim.TransformType

/**
 * Unity / Animate-style sheet: header = frames, rows = vskin × (rot|pos).
 * Client does not interpolate; each column is one AnimFrame.
 */
class TimelineBar(
    private val frames: () -> List<AnimFrame>,
    private val delays: () -> IntArray,
    private val current: () -> Int,
    private val labels: () -> List<Int>,
    private val selectedLabel: () -> Int?,
    private val selectedType: () -> Int,
    private val onSeek: (Int) -> Unit,
    private val onPick: (frame: Int, label: Int, type: Int) -> Unit,
) {
    private val grid = GridPane().apply {
        hgap = 0.0
        vgap = 0.0
        padding = Insets(0.0)
        style = "-fx-background-color: #1e1e22;"
    }
    private val scroll = ScrollPane(grid).apply {
        isFitToWidth = false
        isFitToHeight = false
        hbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
        vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
        prefHeight = 220.0
        style = "-fx-background: #1e1e22; -fx-background-color: #1e1e22;"
    }
    val root = VBox(
        0.0,
        Label("timeline  ·  rows = vskin groups  ·  diamond = keyed  ·  click cell to seek + select").apply {
            padding = Insets(4.0, 8.0, 2.0, 8.0)
            textFill = Color.rgb(180, 180, 186)
        },
        scroll,
    ).apply {
        style = "-fx-background-color: #1e1e22;"
    }

    fun refresh() {
        val list = frames()
        val d = delays()
        val cur = current()
        val labs = labels()
        val selLab = selectedLabel()
        val selType = selectedType()
        grid.children.clear()
        grid.columnConstraints.clear()
        grid.rowConstraints.clear()

        grid.columnConstraints += ColumnConstraints(NAME_W)
        repeat(list.size.coerceAtLeast(1)) {
            grid.columnConstraints += ColumnConstraints(CELL_W)
        }

        var row = 0
        headerRow(list, d, cur, row)
        row++

        if (list.isEmpty()) {
            val empty = Label("no sequence loaded").apply { textFill = Color.GRAY; padding = Insets(8.0) }
            grid.add(empty, 0, row, 2, 1)
            return
        }

        for (lab in labs) {
            trackRow(lab, TransformType.ROTATE, "vskin $lab  rot", list, cur, selLab, selType, row)
            row++
            trackRow(lab, TransformType.TRANSLATE, "vskin $lab  pos", list, cur, selLab, selType, row)
            row++
        }
    }

    private fun headerRow(list: List<AnimFrame>, delays: IntArray, cur: Int, row: Int) {
        val name = headerCell("frame / ticks", NAME_W, header = true)
        grid.add(name, 0, row)
        if (list.isEmpty()) {
            grid.add(headerCell("—", CELL_W, header = true), 1, row)
            return
        }
        list.forEachIndexed { i, _ ->
            val ticks = delays.getOrElse(i) { 5 }
            val cell = headerCell("$i\n${ticks}t", CELL_W, playhead = i == cur, header = true)
            cell.addEventHandler(MouseEvent.MOUSE_CLICKED) { onSeek(i) }
            grid.add(cell, i + 1, row)
        }
    }

    private fun trackRow(
        label: Int,
        type: Int,
        title: String,
        list: List<AnimFrame>,
        cur: Int,
        selLab: Int?,
        selType: Int,
        row: Int,
    ) {
        val active = selLab == label && selType == type
        val name = headerCell(title, NAME_W, selected = active)
        name.addEventHandler(MouseEvent.MOUSE_CLICKED) {
            if (list.isNotEmpty()) onPick(cur.coerceIn(0, list.lastIndex), label, type)
        }
        grid.add(name, 0, row)
        list.forEachIndexed { i, frame ->
            val values = frame.valuesForLabel(label, type)
            val def = if (type == TransformType.SCALE) 128 else 0
            val keyed = values != null && (values.first != def || values.second != def || values.third != def)
            val cell = keyCell(keyed, i == cur, active)
            if (values != null) {
                Tooltip.install(
                    cell,
                    Tooltip("vskin $label ${TransformType.nameOf(type)}  f$i   ${values.first}, ${values.second}, ${values.third}"),
                )
            }
            cell.addEventHandler(MouseEvent.MOUSE_CLICKED) { onPick(i, label, type) }
            grid.add(cell, i + 1, row)
        }
    }

    private fun headerCell(
        text: String,
        width: Double,
        playhead: Boolean = false,
        header: Boolean = false,
        selected: Boolean = false,
    ): StackPane {
        val bg = when {
            playhead -> Color.rgb(70, 88, 44)
            selected -> Color.rgb(58, 58, 28)
            header -> Color.rgb(36, 36, 42)
            else -> Color.rgb(28, 28, 32)
        }
        val fill = Rectangle(width, ROW_H, bg)
        fill.stroke = if (playhead) Color.rgb(198, 224, 138) else Color.rgb(48, 48, 54)
        val label = Label(text).apply {
            textFill = if (playhead) Color.rgb(220, 230, 180) else Color.rgb(200, 200, 206)
            style = "-fx-font-size: 10px;"
            isWrapText = true
            alignment = Pos.CENTER_LEFT
            padding = Insets(0.0, 6.0, 0.0, 6.0)
            prefWidth = width
        }
        return StackPane(fill, label).apply {
            alignment = if (header && text.contains('\n')) Pos.CENTER else Pos.CENTER_LEFT
            prefWidth = width
            prefHeight = ROW_H
        }
    }

    private fun keyCell(keyed: Boolean, playhead: Boolean, selectedTrack: Boolean): StackPane {
        val bg = when {
            playhead && selectedTrack -> Color.rgb(70, 88, 44)
            playhead -> Color.rgb(48, 56, 36)
            selectedTrack -> Color.rgb(42, 42, 22)
            else -> Color.rgb(24, 24, 28)
        }
        val fill = Rectangle(CELL_W, ROW_H, bg)
        fill.stroke = Color.rgb(40, 40, 46)
        val pane = StackPane(fill)
        if (keyed) {
            val diamond = Polygon(0.0, 5.0, 5.0, 0.0, 10.0, 5.0, 5.0, 10.0)
            diamond.fill = Color.GOLD
            pane.children += diamond
        }
        pane.prefWidth = CELL_W
        pane.prefHeight = ROW_H
        return pane
    }

    companion object {
        private const val NAME_W = 110.0
        private const val CELL_W = 36.0
        private const val ROW_H = 22.0
    }
}
