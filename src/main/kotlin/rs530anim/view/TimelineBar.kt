package rs530anim.view

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.input.MouseEvent
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
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
    private val onPlayToggle: () -> Unit,
    private val onFirst: () -> Unit,
    private val onPrev: () -> Unit,
    private val onNext: () -> Unit,
    private val onLast: () -> Unit,
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
        prefHeight = 176.0
        style = "-fx-background: #1e1e22; -fx-background-color: #1e1e22;"
    }
    private val firstBtn = Button("|<<").apply { setOnAction { onFirst() } }
    private val prevBtn = Button("<").apply { setOnAction { onPrev() } }
    val playBtn = Button("Play").apply { setOnAction { onPlayToggle() } }
    private val nextBtn = Button(">").apply { setOnAction { onNext() } }
    private val lastBtn = Button(">>|").apply { setOnAction { onLast() } }
    val status = Label("no seq").apply { textFill = Color.rgb(200, 200, 206) }
    private val transport = HBox(6.0).apply {
        alignment = Pos.CENTER_LEFT
        padding = Insets(4.0, 8.0, 4.0, 8.0)
        children.addAll(firstBtn, prevBtn, playBtn, nextBtn, lastBtn, status)
        style = "-fx-background-color: #16161a;"
    }
    val root = VBox(0.0, transport, scroll).apply {
        style = "-fx-background-color: #1e1e22;"
    }

    fun setPlaying(playing: Boolean) {
        playBtn.text = if (playing) "Pause" else "Play"
    }

    fun setEnabled(on: Boolean) {
        firstBtn.isDisable = !on
        prevBtn.isDisable = !on
        playBtn.isDisable = !on
        nextBtn.isDisable = !on
        lastBtn.isDisable = !on
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
        val frameCount = list.size.coerceAtLeast(1)
        repeat(frameCount * 3) {
            grid.columnConstraints += ColumnConstraints(AXIS_W)
        }

        var row = 0
        headerFrames(list, d, cur, row)
        row++
        headerAxes(list, cur, row)
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

    private fun colOf(frame: Int, axis: Int): Int = 1 + frame * 3 + axis

    private fun headerFrames(list: List<AnimFrame>, delays: IntArray, cur: Int, row: Int) {
        grid.add(headerCell("group", NAME_W, header = true), 0, row)
        if (list.isEmpty()) return
        list.forEachIndexed { i, _ ->
            val ticks = delays.getOrElse(i) { 5 }
            val cell = headerCell("f$i  ${ticks}t", AXIS_W * 3, playhead = i == cur, header = true)
            cell.addEventHandler(MouseEvent.MOUSE_CLICKED) { onSeek(i) }
            grid.add(cell, colOf(i, 0), row, 3, 1)
        }
    }

    private fun headerAxes(list: List<AnimFrame>, cur: Int, row: Int) {
        grid.add(headerCell("", NAME_W, header = true), 0, row)
        if (list.isEmpty()) return
        list.forEachIndexed { i, _ ->
            for ((axis, name) in listOf(0 to "x", 1 to "y", 2 to "z")) {
                val cell = headerCell(name, AXIS_W, playhead = i == cur, header = true)
                cell.addEventHandler(MouseEvent.MOUSE_CLICKED) { onSeek(i) }
                grid.add(cell, colOf(i, axis), row)
            }
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
        val def = if (type == TransformType.SCALE) 128 else 0
        list.forEachIndexed { i, frame ->
            val values = frame.valuesForLabel(label, type) ?: Triple(def, def, def)
            val parts = intArrayOf(values.first, values.second, values.third)
            parts.forEachIndexed { axis, value ->
                val cell = valueCell(value, def, i == cur, active)
                Tooltip.install(cell, Tooltip("vskin $label ${TransformType.nameOf(type)}  f$i"))
                cell.addEventHandler(MouseEvent.MOUSE_CLICKED) { onPick(i, label, type) }
                grid.add(cell, colOf(i, axis), row)
            }
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

    private fun valueCell(value: Int, def: Int, playhead: Boolean, selectedTrack: Boolean): StackPane {
        val nonzero = value != def
        val bg = when {
            playhead && selectedTrack -> Color.rgb(70, 88, 44)
            playhead -> Color.rgb(48, 56, 36)
            selectedTrack -> Color.rgb(42, 42, 22)
            else -> Color.rgb(24, 24, 28)
        }
        val fill = Rectangle(AXIS_W, ROW_H, bg)
        fill.stroke = Color.rgb(40, 40, 46)
        val label = Label(value.toString()).apply {
            textFill = when {
                nonzero -> Color.rgb(236, 210, 96)
                playhead -> Color.rgb(180, 190, 160)
                else -> Color.rgb(120, 120, 126)
            }
            style = "-fx-font-size: 10px;"
            alignment = Pos.CENTER
            prefWidth = AXIS_W
        }
        return StackPane(fill, label).apply {
            alignment = Pos.CENTER
            prefWidth = AXIS_W
            prefHeight = ROW_H
        }
    }

    companion object {
        private const val NAME_W = 110.0
        private const val AXIS_W = 36.0
        private const val ROW_H = 22.0
    }
}
