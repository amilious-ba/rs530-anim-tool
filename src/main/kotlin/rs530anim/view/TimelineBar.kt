package rs530anim.view

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.input.KeyCode
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
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
    private val onClearLabel: () -> Unit = {},
    private val onPlayToggle: () -> Unit,
    private val onFirst: () -> Unit,
    private val onPrev: () -> Unit,
    private val onNext: () -> Unit,
    private val onLast: () -> Unit,
    private val onEdit: (frame: Int, label: Int, type: Int, axis: Int, value: Int) -> Unit,
    private val onDelay: (frame: Int, ticks: Int) -> Unit = { _, _ -> },
) {
    private var editor: TextField? = null
    private val expanded = mutableSetOf<Int>()
    private val head = GridPane()
    private val names = GridPane()
    private val body = GridPane()

    private val headClip = Rectangle(0.0, 0.0, 0.0, HEADER_H)
    private val nameClip = Rectangle(NAME_W, 0.0)
    private val headPane = Pane(head).apply {
        prefHeight = HEADER_H
        minHeight = HEADER_H
        maxHeight = HEADER_H
        clip = headClip
        style = PANE
    }
    private val namePane = Pane(names).apply {
        prefWidth = NAME_W
        minWidth = NAME_W
        maxWidth = NAME_W
        clip = nameClip
        style = PANE
    }
    private val bodyScroll = ScrollPane(body).apply {
        hbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
        vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
        minHeight = 80.0
        prefHeight = 140.0
        style = PANE
    }

    private val firstBtn = DarkUi.iconButton("First frame", DarkUi.firstGraphic()) { onFirst() }
    private val prevBtn = DarkUi.iconButton("Previous frame", DarkUi.prevGraphic()) { onPrev() }
    val playBtn = DarkUi.iconButton("Play / Pause", DarkUi.playGraphic()) { onPlayToggle() }
    private val nextBtn = DarkUi.iconButton("Next frame", DarkUi.nextGraphic()) { onNext() }
    private val lastBtn = DarkUi.iconButton("Last frame", DarkUi.lastGraphic()) { onLast() }
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
        add(headPane, 1, 0)
        add(namePane, 0, 1)
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
        fun syncFreeze() {
            val view = bodyScroll.viewportBounds
            headClip.width = view.width.coerceAtLeast(0.0)
            headClip.height = HEADER_H
            nameClip.width = NAME_W
            nameClip.height = view.height.coerceAtLeast(0.0)
            val yRange = (body.layoutBounds.height - view.height).coerceAtLeast(0.0)
            val xRange = (body.layoutBounds.width - view.width).coerceAtLeast(0.0)
            names.translateY = -bodyScroll.vvalue * yRange
            head.translateX = -bodyScroll.hvalue * xRange
        }
        bodyScroll.vvalueProperty().addListener { _, _, _ -> syncFreeze() }
        bodyScroll.hvalueProperty().addListener { _, _, _ -> syncFreeze() }
        bodyScroll.viewportBoundsProperty().addListener { _, _, _ -> syncFreeze() }
        body.layoutBoundsProperty().addListener { _, _, _ -> syncFreeze() }
        fun wheelToBody(e: ScrollEvent) {
            val viewH = bodyScroll.viewportBounds.height
            val range = (body.layoutBounds.height - viewH).coerceAtLeast(1.0)
            bodyScroll.vvalue = (bodyScroll.vvalue - e.deltaY / range).coerceIn(0.0, 1.0)
            e.consume()
        }
        namePane.addEventHandler(ScrollEvent.SCROLL) { wheelToBody(it) }
        names.addEventHandler(ScrollEvent.SCROLL) { wheelToBody(it) }
        sheet.addEventFilter(ScrollEvent.SCROLL) { e ->
            if (e.x < NAME_W) wheelToBody(e)
        }
    }

    fun setPlaying(playing: Boolean) {
        playBtn.graphic = if (playing) DarkUi.pauseGraphic() else DarkUi.playGraphic()
        playBtn.tooltip = javafx.scene.control.Tooltip(if (playing) "Pause" else "Play")
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

        val base = list.first().base

        var row = 0
        for (lab in labs) {
            val types = typesForLabel(base, lab)
            addRow(row)
            groupHeader(lab, types, selLab, row)
            row++
            for (type in types) {
                addRow(row)
                trackRow(lab, type, "    ${shortName(type)}", list, cur, selLab, selType, row)
                row++
            }
        }
    }

    private fun addRow(row: Int) {
        names.rowConstraints += RowConstraints(ROW_H)
        body.rowConstraints += RowConstraints(ROW_H)
    }

    private fun typesForLabel(base: rs530anim.anim.AnimBase, label: Int): List<Int> {
        val found = LinkedHashSet<Int>()
        for (i in base.types.indices) {
            if (label in base.bones[i]) found += base.types[i]
        }
        return CHANNELS.filter { it in found }
    }

    private fun groupHeader(label: Int, types: List<Int>, selLab: Int?, row: Int) {
        val extra = types.joinToString(" ") { shortName(it) }
        val name = headerCell("vskin $label", NAME_W, selected = selLab == label)
        Tooltip.install(name, Tooltip("vskin $label  $extra  · click to select/deselect"))
        name.addEventHandler(MouseEvent.MOUSE_CLICKED) {
            if (selLab == label) onClearLabel()
            else if (frames().isNotEmpty()) onPick(current().coerceAtLeast(0), label, types.firstOrNull() ?: TransformType.ROTATE)
        }
        names.add(name, 0, row)
        val span = (frames().size * 3).coerceAtLeast(1)
        val spacer = Rectangle(AXIS_W * span, ROW_H, Color.rgb(24, 24, 28))
        spacer.stroke = Color.rgb(40, 40, 46)
        body.add(spacer, 0, row, span, 1)
    }

    private fun colOf(frame: Int, axis: Int): Int = frame * 3 + axis

    private fun headerFrames(list: List<AnimFrame>, delays: IntArray, cur: Int) {
        if (list.isEmpty()) return
        list.forEachIndexed { i, _ ->
            val ticks = delays.getOrElse(i) { 5 }
            val cell = headerCell("f$i  ${ticks}t", AXIS_W * 3, playhead = i == cur, header = true)
            Tooltip.install(cell, Tooltip("click seek · double-click edit ticks"))
            cell.addEventHandler(MouseEvent.MOUSE_CLICKED) { e ->
                if (e.clickCount >= 2) beginDelayEdit(cell, i, ticks) else onSeek(i)
            }
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
            if (selLab == label && selType == type) onClearLabel()
            else if (list.isNotEmpty()) onPick(cur.coerceIn(0, list.lastIndex), label, type)
        }
        names.add(name, 0, row)
        fillValues(label, type, list, cur, active, row)
    }

    private fun fillValues(
        label: Int,
        type: Int,
        list: List<AnimFrame>,
        cur: Int,
        active: Boolean,
        row: Int,
    ) {
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

    private fun beginDelayEdit(cell: StackPane, frame: Int, ticks: Int) {
        if (editor != null) return
        val field = TextField(ticks.toString()).apply {
            prefWidth = AXIS_W * 3
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
            if (parsed != null && parsed != ticks) onDelay(frame, parsed.coerceIn(1, 255))
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
        private const val NAME_W = 120.0
        private const val AXIS_W = 36.0
        private const val ROW_H = 22.0
        private const val HEADER_H = 44.0
        private const val PANE = "-fx-background: #1e1e22; -fx-background-color: #1e1e22;"
        private val CHANNELS = listOf(TransformType.TRANSLATE, TransformType.ROTATE, TransformType.SCALE)

        private fun shortName(type: Int): String = when (type) {
            TransformType.TRANSLATE -> "pos"
            TransformType.ROTATE -> "rot"
            TransformType.SCALE -> "scl"
            else -> TransformType.nameOf(type)
        }
    }
}
