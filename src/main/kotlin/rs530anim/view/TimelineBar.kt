package rs530anim.view

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.input.KeyCode
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.RowConstraints
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import rs530anim.anim.AnimFrame
import rs530anim.anim.TransformType

/**
 * Frozen group column + frame header. Body scrolls; bars stay put.
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
    private val onEdit: (frame: Int, label: Int, type: Int, axis: Int, value: Int) -> Unit,
) {
    private var editor: TextField? = null
    private val corner = GridPane()
    private val head = GridPane()
    private val names = GridPane()
    private val body = GridPane()

    private val headScroll = ScrollPane(head).apply {
        hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        vbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        isFitToHeight = true
        prefHeight = HEADER_H
        minHeight = HEADER_H
        maxHeight = HEADER_H
        style = PANE
    }
    private val nameScroll = ScrollPane(names).apply {
        hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        vbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        isFitToWidth = true
        prefWidth = NAME_W
        minWidth = NAME_W
        maxWidth = NAME_W
        style = PANE
    }
    private val bodyScroll = ScrollPane(body).apply {
        hbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
        vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
        prefHeight = 176.0
        style = PANE
    }

    private val firstBtn = Button("|<<").apply { setOnAction { onFirst() } }
    private val prevBtn = Button("<").apply { setOnAction { onPrev() } }
    val playBtn = Button("Play").apply { setOnAction { onPlayToggle() } }
    private val nextBtn = Button(">").apply { setOnAction { onNext() } }
    private val lastBtn = Button(">>|").apply { setOnAction { onLast() } }
    val status = Label()
    private val transport = HBox(6.0).apply {
        alignment = Pos.CENTER_LEFT
        padding = Insets(4.0, 8.0, 4.0, 8.0)
        children.addAll(firstBtn, prevBtn, playBtn, nextBtn, lastBtn)
        style = "-fx-background-color: #16161a;"
    }
    private val tools = HBox(6.0).apply { alignment = Pos.CENTER_LEFT }
    init {
        transport.children += tools
    }

    fun setTools(vararg nodes: javafx.scene.Node) {
        tools.children.setAll(*nodes)
    }

    private val sheet = GridPane().apply {
        add(StackPane(headerCell("group", NAME_W, header = true)).apply {
            prefWidth = NAME_W
            prefHeight = HEADER_H
            minHeight = HEADER_H
            maxHeight = HEADER_H
        }, 0, 0)
        add(headScroll, 1, 0)
        add(nameScroll, 0, 1)
        add(bodyScroll, 1, 1)
        ColumnConstraints(NAME_W).also { columnConstraints += it }
        ColumnConstraints().also {
            it.hgrow = Priority.ALWAYS
            columnConstraints += it
        }
        RowConstraints(HEADER_H).also { rowConstraints += it }
        RowConstraints().also {
            it.vgrow = Priority.ALWAYS
            rowConstraints += it
        }
    }

    val root = VBox(0.0, transport, sheet).apply {
        style = "-fx-background-color: #1e1e22;"
        VBox.setVgrow(sheet, Priority.ALWAYS)
    }

    init {
        headScroll.hvalueProperty().bind(bodyScroll.hvalueProperty())
        nameScroll.vvalueProperty().bind(bodyScroll.vvalueProperty())
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
        if (editor != null) return
        val list = frames()
        val d = delays()
        val cur = current()
        val labs = labels()
        val selLab = selectedLabel()
        val selType = selectedType()
        for (g in arrayOf(head, names, body)) {
            g.children.clear()
            g.columnConstraints.clear()
            g.rowConstraints.clear()
        }

        val frameCount = list.size.coerceAtLeast(1)
        repeat(frameCount * 3) {
            head.columnConstraints += ColumnConstraints(AXIS_W)
            body.columnConstraints += ColumnConstraints(AXIS_W)
        }
        names.columnConstraints += ColumnConstraints(NAME_W)

        headerFrames(list, d, cur)
        headerAxes(list, cur)

        if (list.isEmpty()) {
            body.add(Label("no sequence loaded").apply { textFill = Color.GRAY; padding = Insets(8.0) }, 0, 0)
            return
        }

        var row = 0
        for (lab in labs) {
            names.rowConstraints += RowConstraints(ROW_H)
            body.rowConstraints += RowConstraints(ROW_H)
            trackRow(lab, TransformType.ROTATE, "vskin $lab  rot", list, cur, selLab, selType, row)
            row++
            names.rowConstraints += RowConstraints(ROW_H)
            body.rowConstraints += RowConstraints(ROW_H)
            trackRow(lab, TransformType.TRANSLATE, "vskin $lab  pos", list, cur, selLab, selType, row)
            row++
        }
    }

    private fun colOf(frame: Int, axis: Int): Int = frame * 3 + axis

    private fun headerFrames(list: List<AnimFrame>, delays: IntArray, cur: Int) {
        if (list.isEmpty()) return
        list.forEachIndexed { i, _ ->
            val ticks = delays.getOrElse(i) { 5 }
            val cell = headerCell("f$i  ${ticks}t", AXIS_W * 3, playhead = i == cur, header = true)
            cell.addEventHandler(MouseEvent.MOUSE_CLICKED) { onSeek(i) }
            head.add(cell, colOf(i, 0), 0, 3, 1)
        }
    }

    private fun headerAxes(list: List<AnimFrame>, cur: Int) {
        if (list.isEmpty()) return
        list.forEachIndexed { i, _ ->
            for ((axis, name) in listOf(0 to "x", 1 to "y", 2 to "z")) {
                val cell = headerCell(name, AXIS_W, playhead = i == cur, header = true)
                cell.addEventHandler(MouseEvent.MOUSE_CLICKED) { onSeek(i) }
                head.add(cell, colOf(i, axis), 1)
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
        names.add(name, 0, row)
        val def = if (type == TransformType.SCALE) 128 else 0
        list.forEachIndexed { i, frame ->
            val values = frame.valuesForLabel(label, type) ?: Triple(def, def, def)
            val parts = intArrayOf(values.first, values.second, values.third)
            parts.forEachIndexed { axis, value ->
                val cell = valueCell(value, def, i == cur, active)
                Tooltip.install(cell, Tooltip("double-click to edit  vskin $label ${TransformType.nameOf(type)}  f$i"))
                cell.addEventHandler(MouseEvent.MOUSE_CLICKED) { e ->
                    if (e.clickCount >= 2) {
                        beginEdit(cell, i, label, type, axis, value)
                    } else {
                        onPick(i, label, type)
                    }
                }
                body.add(cell, colOf(i, axis), row)
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
            alignment = Pos.CENTER_LEFT
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

    private fun beginEdit(cell: StackPane, frame: Int, label: Int, type: Int, axis: Int, value: Int) {
        if (editor != null) return
        val field = TextField(value.toString()).apply {
            prefWidth = AXIS_W
            prefHeight = ROW_H
            style = "-fx-font-size: 10px; -fx-background-color: #111; -fx-text-fill: #ffe08a; -fx-padding: 0 2 0 2;"
        }
        editor = field
        cell.children.add(field)
        field.requestFocus()
        field.selectAll()
        fun commit() {
            if (editor !== field) return
            editor = null
            val parsed = field.text.trim().toIntOrNull()
            cell.children.remove(field)
            if (parsed != null && parsed != value) {
                val lo = if (type == TransformType.ROTATE) 0 else -2047
                val hi = if (type == TransformType.ROTATE) 2047 else 2047
                onEdit(frame, label, type, axis, parsed.coerceIn(lo, hi))
            }
        }
        fun cancel() {
            if (editor !== field) return
            editor = null
            cell.children.remove(field)
        }
        field.setOnAction { commit() }
        field.focusedProperty().addListener { _, _, focus -> if (!focus) commit() }
        field.setOnKeyPressed { e -> if (e.code == KeyCode.ESCAPE) cancel() }
    }

    companion object {
        private const val NAME_W = 110.0
        private const val AXIS_W = 36.0
        private const val ROW_H = 22.0
        private const val HEADER_H = 44.0
        private const val PANE = "-fx-background: #1e1e22; -fx-background-color: #1e1e22;"
    }
}
