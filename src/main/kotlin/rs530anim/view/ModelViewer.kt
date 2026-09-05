package rs530anim.view

import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.layout.StackPane
import javafx.scene.shape.Cylinder
import javafx.scene.AmbientLight
import javafx.scene.PointLight
import javafx.scene.Group
import javafx.scene.PerspectiveCamera
import javafx.scene.Scene
import javafx.scene.SceneAntialiasing
import javafx.scene.SubScene
import javafx.application.Platform
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckMenuItem
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.Menu
import javafx.scene.control.MenuBar
import javafx.scene.control.MenuItem
import javafx.scene.Node
import javafx.scene.control.ScrollPane
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.control.Slider
import javafx.scene.control.SplitPane
import javafx.scene.control.TextField
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import rs530anim.anim.AnimFrame
import rs530anim.anim.SeqType
import rs530anim.anim.TransformType
import rs530anim.extras.ExtrasStore
import rs530anim.extras.SeqExtras
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.paint.PhongMaterial
import javafx.scene.shape.CullFace
import javafx.scene.shape.DrawMode
import javafx.scene.shape.MeshView
import javafx.scene.shape.Sphere
import javafx.scene.shape.TriangleMesh
import javafx.scene.transform.Rotate
import javafx.stage.Stage
import rs530anim.MonkeySkins
import rs530anim.NpcCatalog
import rs530anim.NpcRow
import rs530anim.anim.AnimLibrary
import rs530anim.anim.ModelAnimator
import rs530anim.cache.CacheSettings
import rs530anim.cache.Js5Store
import rs530anim.cache.TextureMaterials
import rs530anim.model.Rs2Model
import rs530anim.model.Rs2ModelLoader
import kotlin.math.max

class ModelViewer : Application() {
    override fun start(stage: Stage) {
        val raw = bootArgs.ifEmpty { parameters?.raw ?: emptyList() }
        val modelSpec = raw.getOrNull(0) ?: "3004"
        var modelIds = MonkeySkins.resolve(modelSpec)
        val seqId = raw.getOrNull(1)?.toIntOrNull()
        val frameNo = raw.getOrNull(2)?.toIntOrNull() ?: 0

        val settings = CacheSettings.load(null)
        var materials: TextureMaterials? = null
        var model = Js5Store(settings).use { store ->
            materials = try {
                TextureMaterials.load().also { mat ->
                    println("texture 112 solid hsl=${mat.solidHsl(112)}  359=${mat.solidHsl(359)}")
                }
            } catch (e: Exception) {
                System.err.println("texture table failed: ${e.message}")
                e.printStackTrace()
                null
            }
            var loaded = Rs2ModelLoader.decode(store.model(modelIds.first()))
            for (id in modelIds.drop(1)) {
                loaded = loaded.attach(Rs2ModelLoader.decode(store.model(id)))
            }
            val usedTex = loaded.faceTextures
                ?.map { it.toInt() and 0xFFFF }
                ?.filter { it != 0xFFFF }
                ?.groupingBy { it }
                ?.eachCount()
                ?: emptyMap()
            println("models ${modelIds.joinToString("+")} verts=${loaded.vertexCount} faces=${loaded.faceCount} textures=$usedTex")
            for (tid in usedTex.keys.sorted()) {
                val img = rs530anim.tex.TextureLibrary.image(tid)
                println("texture $tid ${if (img != null) "graph-ok" else "graph-fail solid=${materials?.solidHsl(tid)}"}")
            }
            loaded
        }
        var seqFrames: MutableList<AnimFrame> = mutableListOf()
        var seqIdLoaded = seqId
        var seqLoop = 0
        var seqPriority = 5
        var seqDelays = IntArray(0)
        var markBaseline: () -> Unit = {}
        var pushHist: () -> Unit = {}
        var undoEdit: () -> Unit = {}
        var redoEdit: () -> Unit = {}
        var resetEdits: () -> Unit = {}
        if (seqId != null) {
            try {
                Js5Store(settings).use { store ->
                    val seq = AnimLibrary.loadSeq(store, seqId)
                    seqFrames = seq.frames.map { AnimLibrary.frameOf(store, it) }.toMutableList()
                    seqDelays = seq.delays
                    seqLoop = seq.looptype
                    seqPriority = seq.priority
                    println("seq $seqId frames=${seq.length} base=${seqFrames.firstOrNull()?.base?.id}")
                }
            } catch (e: Exception) {
                System.err.println("seq $seqId not loaded: ${e.message}")
            }
        }
        fun loadSequence(id: Int): Boolean {
            return try {
                Js5Store(settings).use { store ->
                    val seq = AnimLibrary.loadSeq(store, id)
                    seqFrames = seq.frames.map { AnimLibrary.frameOf(store, it) }.toMutableList()
                    seqDelays = seq.delays
                    seqLoop = seq.looptype
                    seqPriority = seq.priority
                    seqIdLoaded = id
                    println("seq $id frames=${seq.length} base=${seqFrames.firstOrNull()?.base?.id}")
                }
                markBaseline()
                true
            } catch (e: Exception) {
                System.err.println("seq $id not loaded: ${e.message}")
                false
            }
        }
        var animator = ModelAnimator(model)
        var bind = animator.copyBindPose()
        var currentFrame = frameNo.coerceIn(0, (seqFrames.size - 1).coerceAtLeast(0))
        data class AnimSnap(val frames: List<AnimFrame>, val delays: IntArray, val frame: Int)
        fun takeSnap() = AnimSnap(seqFrames.toList(), seqDelays.copyOf(), currentFrame)
        var baseline: AnimSnap? = if (seqFrames.isNotEmpty()) takeSnap() else null
        val undoStack = ArrayDeque<AnimSnap>()
        val redoStack = ArrayDeque<AnimSnap>()
        var histLock = false
        var applySnap: (AnimSnap) -> Unit = {}
        markBaseline = {
            baseline = takeSnap()
            undoStack.clear()
            redoStack.clear()
        }
        pushHist = {
            if (!histLock && seqFrames.isNotEmpty()) {
                undoStack.addLast(takeSnap())
                while (undoStack.size > 64) undoStack.removeFirst()
                redoStack.clear()
            }
        }
        undoEdit = {
            if (undoStack.isNotEmpty()) {
                histLock = true
                redoStack.addLast(takeSnap())
                applySnap(undoStack.removeLast())
                histLock = false
            }
        }
        redoEdit = {
            if (redoStack.isNotEmpty()) {
                histLock = true
                undoStack.addLast(takeSnap())
                applySnap(redoStack.removeLast())
                histLock = false
            }
        }
        resetEdits = {
            val snap = baseline
            if (snap != null) {
                histLock = true
                undoStack.clear()
                redoStack.clear()
                applySnap(snap)
                histLock = false
            }
        }

        val world = Group()
        world.children += AmbientLight(Color.color(0.55, 0.55, 0.55))
        world.children += PointLight(Color.WHITE).apply {
            translateX = -80.0
            translateY = -120.0
            translateZ = -80.0
        }
        world.children += groundMarker(model).also { it.userData = "ground" }

        val yaw = Rotate(30.0, Rotate.Y_AXIS)
        val pitch = Rotate(-20.0, Rotate.X_AXIS)
        val pivot = Group(world)
        pivot.transforms.addAll(yaw, pitch)

        val root = Group(pivot)
        val camera = PerspectiveCamera(true)
        camera.nearClip = 1.0
        camera.farClip = 20000.0
        var distance = fitDistance(model)
        camera.translateZ = -distance

        val highlight = Group()
        highlight.isMouseTransparent = true
        world.children += highlight
        val gizmo = TransformGizmo()
        world.children += gizmo.root
        val meshGroup = Group()
        meshGroup.children.addAll(buildMeshes(model, materials))
        world.children.add(0, meshGroup)

        var labels = model.uniqueVertexLabels()
        var pickedLabel: Int? = labels.firstOrNull()
        fun selectedLabel(): Int? = pickedLabel
        var clearVskin: () -> Unit = {}
        val pickedLabelUi = Label(
            pickedLabel?.let { "vskin $it  (${model.vertexCountForLabel(it)} verts)" } ?: "no vskin",
        )

        val frameSlider = Slider(0.0, (seqFrames.size - 1).coerceAtLeast(0).toDouble(), currentFrame.toDouble())
        frameSlider.isSnapToTicks = true
        frameSlider.majorTickUnit = 1.0
        frameSlider.blockIncrement = 1.0
        val frameLabel = Label(if (seqFrames.isEmpty()) "no seq" else "frame $currentFrame / ${seqFrames.size}")

        val typeGroup = ToggleGroup()
        val selectBtn = ToggleButton("select").apply { toggleGroup = typeGroup; isSelected = true }
        val moveBtn = ToggleButton("move").apply { toggleGroup = typeGroup }
        val rotBtn = ToggleButton("rotate").apply { toggleGroup = typeGroup }

        fun editType(): Int = if (rotBtn.isSelected) TransformType.ROTATE else TransformType.TRANSLATE
        fun toolIsGizmo(): Boolean = moveBtn.isSelected || rotBtn.isSelected
        fun sliderMax(): Double = if (rotBtn.isSelected) 2047.0 else 512.0
        fun sliderMin(): Double = if (rotBtn.isSelected) 0.0 else -512.0
        val sx = Slider(sliderMin(), sliderMax(), 0.0)
        val sy = Slider(sliderMin(), sliderMax(), 0.0)
        val sz = Slider(sliderMin(), sliderMax(), 0.0)
        val xyzLabel = Label("dx 0  dy 0  dz 0")
        var pointScratch = FloatArray(model.vertexCount * 3)

        var highlightLabel: Int? = null
        fun showLabel(label: Int?) {
            highlightLabel = label
            highlight.children.setAll(vskinGlow(model, label))
        }

        fun labelCenter(label: Int?): Triple<Double, Double, Double>? {
            if (label == null || label !in model.boneVertices.indices) return null
            val ids = model.boneVertices[label]
            if (ids.isEmpty()) return null
            var x = 0.0; var y = 0.0; var z = 0.0
            for (i in ids) {
                x += model.verticesX[i]
                y += model.verticesY[i]
                z += model.verticesZ[i]
            }
            val n = ids.size.toDouble()
            return Triple(x / n, y / n, z / n)
        }

        fun refreshGizmo() {
            val lab = selectedLabel()
            val c = labelCenter(lab)
            if (!toolIsGizmo() || c == null) {
                gizmo.root.isVisible = false
                return
            }
            gizmo.root.isVisible = true
            gizmo.rebuild(editType(), c.first, c.second, c.third, fitDistance(model) * 0.08)
        }

        fun syncDeformedGeometry() {
            var p = 0
            for (i in 0 until model.vertexCount) {
                pointScratch[p++] = model.verticesX[i].toFloat()
                pointScratch[p++] = model.verticesY[i].toFloat()
                pointScratch[p++] = model.verticesZ[i].toFloat()
            }
            fun pushPoints(node: Node) {
                when (node) {
                    is MeshView -> {
                        val mesh = node.mesh as? TriangleMesh ?: return
                        if (mesh.points.size() == pointScratch.size) {
                            mesh.points.set(0, pointScratch, 0, pointScratch.size)
                        }
                    }
                    is Group -> {
                        if (node.userData == "verts") {
                            node.children.forEachIndexed { i, child ->
                                val s = child as? Sphere ?: return@forEachIndexed
                                if (i < model.vertexCount) {
                                    s.translateX = model.verticesX[i].toDouble()
                                    s.translateY = model.verticesY[i].toDouble()
                                    s.translateZ = model.verticesZ[i].toDouble()
                                }
                            }
                        } else {
                            node.children.forEach { pushPoints(it) }
                        }
                    }
                }
            }
            meshGroup.children.forEach { pushPoints(it) }
            showLabel(selectedLabel())
            if (gizmo.axis == null) refreshGizmo()
        }

        var showTextures = true
        var showWire = false
        var showVerts = false
        var playing = false
        var selectedTex: Int? = null
        fun rebuild() {
            val nodes = buildMeshes(model, materials, showTextures, showWire, selectedTex).toMutableList<Node>()
            if (showVerts) nodes += vertexDots(model)
            meshGroup.children.setAll(nodes)
            highlightLabel = null
            showLabel(selectedLabel())
        }

        var syncingSliders = false
        var refreshTimeline: () -> Unit = {}
        var markPlayhead: (Int) -> Unit = {}
        var timelineStatus: (String) -> Unit = {}
        var timelinePlaying: (Boolean) -> Unit = {}
        var timelineEnabled: (Boolean) -> Unit = {}
        var windowStatus: (String) -> Unit = {}

        fun loadSlidersFromFrame() {
            if (seqFrames.isEmpty()) return
            currentFrame = frameSlider.value.toInt().coerceIn(0, seqFrames.lastIndex)
            val frame = seqFrames[currentFrame]
            val lab = selectedLabel()
            val kind = editType()
            val values = if (lab == null) null else frame.valuesForLabel(lab, kind)
            val slot = if (lab == null) null else frame.base.slotFor(lab, kind)
            syncingSliders = true
            sx.min = sliderMin(); sx.max = sliderMax()
            sy.min = sliderMin(); sy.max = sliderMax()
            sz.min = sliderMin(); sz.max = sliderMax()
            sx.value = (values?.first ?: 0).toDouble()
            sy.value = (values?.second ?: 0).toDouble()
            sz.value = (values?.third ?: 0).toDouble()
            syncingSliders = false
            val typeName = TransformType.nameOf(kind)
            xyzLabel.text = if (lab == null) {
                "select a vskin"
            } else if (slot == null) {
                "vskin $lab has no $typeName slot in base ${frame.base.id}"
            } else {
                "base ${frame.base.id} slot $slot $typeName  dx ${sx.value.toInt()} dy ${sy.value.toInt()} dz ${sz.value.toInt()}"
            }
        }

        fun writeSlidersIntoFrame() {
            if (syncingSliders || seqFrames.isEmpty()) return
            val lab = selectedLabel() ?: return
            val kind = editType()
            val frame = seqFrames[currentFrame]
            if (frame.base.slotFor(lab, kind) == null) return
            seqFrames[currentFrame] = frame.withLabelValues(
                lab,
                kind,
                sx.value.toInt(),
                sy.value.toInt(),
                sz.value.toInt(),
            )
        }

        fun applyPose(fullUi: Boolean = true) {
            animator.restore(bind)
            if (seqFrames.isNotEmpty()) {
                currentFrame = frameSlider.value.toInt().coerceIn(0, seqFrames.lastIndex)
                try {
                    animator.apply(seqFrames[currentFrame])
                } catch (e: Exception) {
                    System.err.println("apply frame $currentFrame: ${e.javaClass.simpleName}: ${e.message}")
                    e.printStackTrace()
                }
            }
            val statusText = if (seqFrames.isEmpty()) {
                "no seq"
            } else {
                val d = seqDelays.getOrElse(currentFrame) { 5 }
                "seq $seqIdLoaded  frame $currentFrame / ${seqFrames.size}  ${d} ticks (${d * 20} ms)"
            }
            frameLabel.text = statusText
            timelineStatus(statusText)
            val lab = selectedLabel()
            val typeName = TransformType.nameOf(editType())
            val baseId = seqFrames.firstOrNull()?.base?.id
            val posedMinY = model.verticesY.minOrNull() ?: 0
            val posedMaxY = model.verticesY.maxOrNull() ?: 0
            windowStatus(
                "model ${modelIds.joinToString("+")}   " +
                    statusText +
                    "   " + (lab?.let { "vskin $it $typeName" } ?: "no group") +
                    (baseId?.let { "   base $it" } ?: "") +
                    "   posedY $posedMinY..$posedMaxY  tile=0",
            )
            syncDeformedGeometry()
            if (fullUi) refreshTimeline() else markPlayhead(currentFrame)
        }
        clearVskin = {
            pickedLabel = null
            pickedLabelUi.text = "no vskin"
            showLabel(null)
            gizmo.root.isVisible = false
            refreshTimeline()
            applyPose()
        }
        applySnap = { snap ->
            seqFrames = snap.frames.toMutableList()
            seqDelays = snap.delays.copyOf()
            if (seqFrames.isNotEmpty()) {
                currentFrame = snap.frame.coerceIn(0, seqFrames.lastIndex)
                frameSlider.value = currentFrame.toDouble()
            }
            loadSlidersFromFrame()
            applyPose(fullUi = true)
        }

        frameSlider.valueProperty().addListener { _, _, _ ->
            if (!playing) loadSlidersFromFrame()
            applyPose(fullUi = !playing)
        }
        sx.valueProperty().addListener { _, _, _ ->
            writeSlidersIntoFrame()
            if (!syncingSliders) {
                loadSlidersFromFrame()
                applyPose()
            }
        }
        sy.valueProperty().addListener { _, _, _ ->
            writeSlidersIntoFrame()
            if (!syncingSliders) {
                loadSlidersFromFrame()
                applyPose()
            }
        }
        sz.valueProperty().addListener { _, _, _ ->
            writeSlidersIntoFrame()
            if (!syncingSliders) {
                loadSlidersFromFrame()
                applyPose()
            }
        }
        typeGroup.selectedToggleProperty().addListener { _, _, _ ->
            loadSlidersFromFrame()
            applyPose()
        }

        val extrasIdField = TextField((seqIdLoaded ?: 9220).toString())
        extrasIdField.prefWidth = 80.0
        val exportBtn = Button("export extras")
        val importBtn = Button("import extras")
        val resetBtn = Button("reset")
        resetBtn.setOnAction { resetEdits() }
        val extrasLabel = Label(ExtrasStore.defaultRoot().toString())
        extrasLabel.isWrapText = true
        exportBtn.isDisable = seqFrames.isEmpty()
        exportBtn.setOnAction {
            val outId = extrasIdField.text.toIntOrNull() ?: return@setOnAction
            val baseId = seqFrames.first().base.id
            val delays = IntArray(seqFrames.size) { seqDelays.getOrElse(it) { 5 } }
            val def = SeqExtras(
                id = outId,
                baseId = baseId,
                loop = seqLoop,
                priority = seqPriority,
                frames = seqFrames.indices.toList(),
                delays = delays.toList(),
            )
            val path = ExtrasStore.save(def, seqFrames)
            extrasLabel.text = "wrote $path"
            println("exported extras seq $outId base=$baseId frames=${seqFrames.size} -> $path")
        }
        importBtn.setOnAction {
            val inId = extrasIdField.text.toIntOrNull() ?: return@setOnAction
            val loadedBase = seqFrames.firstOrNull()?.base
            if (loadedBase == null) {
                extrasLabel.text = "load a cache seq first (need AnimBase)"
                return@setOnAction
            }
            try {
                val (def, frames) = ExtrasStore.load(inId) { requested ->
                    require(requested == loadedBase.id) {
                        "extras base $requested != loaded base ${loadedBase.id}"
                    }
                    loadedBase
                }
                seqFrames = frames.toMutableList()
                seqDelays = def.delays.toIntArray()
                seqLoop = def.loop
                seqPriority = def.priority
                seqIdLoaded = def.id
                frameSlider.max = (seqFrames.size - 1).coerceAtLeast(0).toDouble()
                frameSlider.value = 0.0
                timelineEnabled(seqFrames.isNotEmpty())
                exportBtn.isDisable = seqFrames.isEmpty()
                extrasLabel.text = "loaded extras seq ${def.id}"
                markBaseline()
                loadSlidersFromFrame()
                applyPose()
            } catch (e: Exception) {
                extrasLabel.text = "import failed: ${e.message}"
                System.err.println("import extras $inId: ${e.message}")
            }
        }
        var accNs = 0L
        val timer = object : AnimationTimer() {
            override fun handle(now: Long) {
                if (!playing || seqFrames.isEmpty()) return
                if (accNs == 0L) {
                    accNs = now
                    return
                }
                val delayNs = seqDelays.getOrElse(currentFrame) { 5 }.coerceAtLeast(1) * 20_000_000L
                if (now - accNs < delayNs) return
                accNs += delayNs
                if (now - accNs > delayNs * 4) accNs = now
                val next = (currentFrame + 1) % seqFrames.size
                frameSlider.value = next.toDouble()
            }
        }
        fun togglePlay() {
            if (seqFrames.isEmpty()) return
            playing = !playing
            timelinePlaying(playing)
            accNs = 0L
            if (playing) {
                applyPose(fullUi = false)
                timer.start()
            } else {
                timer.stop()
                loadSlidersFromFrame()
                applyPose(fullUi = true)
            }
        }
        fun seekFrame(i: Int) {
            if (seqFrames.isEmpty()) return
            frameSlider.value = i.coerceIn(0, seqFrames.lastIndex).toDouble()
        }

        var applyNpc: (Int) -> Unit = {}
        val modelBox = ComboBox<NpcRow>()
        modelBox.prefWidth = 220.0
        val pinned = listOf(132, 4344, 1455, 1456, 1457, 1465, 1466, 1467)
        modelBox.items.addAll(pinned.mapNotNull { NpcCatalog.get(it) }.distinctBy { it.id })
        val currentNpc = NpcCatalog.all.firstOrNull { NpcCatalog.modelsFor(it.id) == modelIds }
            ?: NpcCatalog.get(132)
        if (currentNpc != null) modelBox.selectionModel.select(currentNpc)
        var loadedNpcId = currentNpc?.id
        modelBox.setOnAction {
            val row = modelBox.selectionModel.selectedItem ?: return@setOnAction
            if (row.id == loadedNpcId) return@setOnAction
            applyNpc(row.id)
        }

        val seqBox = ComboBox<NpcCatalog.SeqRef>()
        seqBox.prefWidth = 220.0
        seqBox.items.addAll(NpcCatalog.sequencesForModels(modelIds))
        val currentSeq = seqIdLoaded
        if (currentSeq != null) {
            val match = seqBox.items.firstOrNull { it.id == currentSeq }
            if (match != null) {
                seqBox.selectionModel.select(match)
            } else {
                val extra = NpcCatalog.SeqRef(currentSeq, "loaded")
                seqBox.items.add(0, extra)
                seqBox.selectionModel.select(extra)
            }
        }
        seqBox.setOnAction {
            val id = seqBox.selectionModel.selectedItem?.id ?: return@setOnAction
            if (id == seqIdLoaded) return@setOnAction
            if (playing) togglePlay()
            if (!loadSequence(id)) return@setOnAction
            extrasIdField.text = id.toString()
            frameSlider.max = (seqFrames.size - 1).coerceAtLeast(0).toDouble()
            frameSlider.value = 0.0
            timelineEnabled(seqFrames.isNotEmpty())
            exportBtn.isDisable = seqFrames.isEmpty()
            loadSlidersFromFrame()
            applyPose(fullUi = true)
        }

        data class TexRef(val id: Int?, val label: String) {
            override fun toString(): String = label
        }
        val texCounts = linkedMapOf<Int, Int>()
        val rawTex = model.faceTextures
        if (rawTex != null) {
            for (i in rawTex.indices) {
                val id = rawTex[i].toInt() and 0xFFFF
                if (id != 0xFFFF) texCounts[id] = (texCounts[id] ?: 0) + 1
            }
        }
        val texBox = ComboBox<TexRef>()
        texBox.prefWidth = 220.0
        texBox.items += TexRef(null, "all textures  (${texCounts.values.sum()} faces)")
        for ((id, n) in texCounts.entries.sortedBy { it.key }) {
            val graph = rs530anim.tex.TextureLibrary.image(id) != null
            val hsl = materials?.solidHsl(id)
            texBox.items += TexRef(
                id,
                "$id  $n faces  ${if (graph) "graph" else "solid"}" + if (hsl != null) "  hsl $hsl" else "",
            )
        }
        texBox.selectionModel.selectFirst()
        texBox.setOnAction {
            selectedTex = texBox.selectionModel.selectedItem?.id
            rebuild()
        }

        applyNpc = apply@{ id ->
            if (id == loadedNpcId) return@apply
            val next = NpcCatalog.modelsFor(id)
            try {
                val loaded = Js5Store(settings).use { store ->
                    var m = Rs2ModelLoader.decode(store.model(next.first()))
                    for (mid in next.drop(1)) {
                        m = m.attach(Rs2ModelLoader.decode(store.model(mid)))
                    }
                    println("npc $id models=${next.joinToString("+")} verts=${m.vertexCount} faces=${m.faceCount}")
                    m
                }
                if (playing) togglePlay()
                model = loaded
                modelIds = next
                loadedNpcId = id
                animator = ModelAnimator(model)
                bind = animator.copyBindPose()
                labels = model.uniqueVertexLabels()
                pointScratch = FloatArray(model.vertexCount * 3)
                pickedLabel = labels.firstOrNull()
                pickedLabelUi.text = pickedLabel?.let { "vskin $it  (${model.vertexCountForLabel(it)} verts)" } ?: "no vskin"
                selectedTex = null
                world.children.removeIf { it.userData == "ground" }
                world.children += groundMarker(model).also { it.userData = "ground" }
                val atk = NpcCatalog.get(id)?.attack?.takeIf { it > 0 }
                if (atk != null) loadSequence(atk) else {
                    seqFrames = mutableListOf()
                    seqDelays = IntArray(0)
                    seqIdLoaded = null
                }
                currentFrame = 0
                frameSlider.max = (seqFrames.size - 1).coerceAtLeast(0).toDouble()
                frameSlider.value = 0.0
                extrasIdField.text = (seqIdLoaded ?: 9220).toString()
                timelineEnabled(seqFrames.isNotEmpty())
                exportBtn.isDisable = seqFrames.isEmpty()
                texBox.items.clear()
                val counts = linkedMapOf<Int, Int>()
                model.faceTextures?.forEach { t ->
                    val tid = t.toInt() and 0xFFFF
                    if (tid != 0xFFFF) counts[tid] = (counts[tid] ?: 0) + 1
                }
                texBox.items += TexRef(null, "all textures  (${counts.values.sum()} faces)")
                for ((tid, n) in counts.entries.sortedBy { it.key }) {
                    val graph = rs530anim.tex.TextureLibrary.image(tid) != null
                    texBox.items += TexRef(tid, "$tid  $n faces  ${if (graph) "graph" else "solid"}")
                }
                texBox.selectionModel.selectFirst()
                seqBox.items.setAll(NpcCatalog.sequencesForModels(modelIds))
                seqIdLoaded?.let { sid ->
                    val match = seqBox.items.firstOrNull { it.id == sid }
                    if (match != null) seqBox.selectionModel.select(match)
                    else {
                        val extra = NpcCatalog.SeqRef(sid, "loaded")
                        seqBox.items.add(0, extra)
                        seqBox.selectionModel.select(extra)
                    }
                }
                val row = NpcCatalog.get(id)
                if (row != null && modelBox.items.none { it.id == id }) modelBox.items.add(0, row)
                if (row != null) modelBox.selectionModel.select(row)
                distance = fitDistance(model)
                camera.translateZ = -distance
                rebuild()
                applyPose(fullUi = true)
                stage.title = buildString {
                    append("rs530-anim-tool  model ${modelIds.joinToString("+")}")
                    seqIdLoaded?.let { append("  seq $it") }
                }
            } catch (e: Exception) {
                System.err.println("npc $id failed: ${e.message}")
                e.printStackTrace()
            }
        }

        val side = VBox(
            8.0,
            Label("model"),
            modelBox,
            Label("texture"),
            texBox,
            Label("animation"),
            seqBox,
            pickedLabelUi,
            Label("${model.vertexCount} verts  ${model.faceCount} faces"),
            extrasIdField,
            exportBtn,
            importBtn,
            resetBtn,
            extrasLabel,
            xyzLabel,
        )
        side.padding = Insets(10.0)
        side.prefWidth = 236.0
        side.minWidth = 236.0
        side.style = "-fx-background-color: #1e1e22;"
        val sideScroll = ScrollPane(side).apply {
            isFitToWidth = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
            prefWidth = 248.0
            minWidth = 248.0
            style = "-fx-background-color: #1e1e22; -fx-background: #1e1e22;"
        }

        val sub = SubScene(root, 200.0, 200.0, true, SceneAntialiasing.BALANCED)
        sub.fill = Color.rgb(32, 32, 36)
        sub.camera = camera
        sub.isManaged = false

        var lastX = 0.0
        var lastY = 0.0
        var dragDist = 0.0
        var gizmoDrag = false
        var gizmoAccum = 0.0
        sub.setOnMousePressed { e ->
            lastX = e.sceneX
            lastY = e.sceneY
            dragDist = 0.0
            gizmoAccum = 0.0
            gizmoDrag = e.button == MouseButton.PRIMARY && toolIsGizmo() &&
                gizmo.begin(e.pickResult?.intersectedNode)
            if (gizmoDrag) pushHist()
        }
        sub.setOnMouseDragged { e ->
            val dx = e.sceneX - lastX
            val dy = e.sceneY - lastY
            dragDist += kotlin.math.abs(dx) + kotlin.math.abs(dy)
            if (gizmoDrag && gizmo.axis != null && seqFrames.isNotEmpty()) {
                val lab = selectedLabel()
                val axis = gizmo.axis
                if (lab != null && axis != null) {
                    val type = editType()
                    if (currentFrame !in seqFrames.indices) return@setOnMouseDragged
                    val frame = seqFrames[currentFrame]
                    val def = if (type == TransformType.SCALE) 128 else 0
                    val cur = frame.valuesForLabel(lab, type) ?: Triple(def, def, def)
                    val parts = intArrayOf(cur.first, cur.second, cur.third)
                    val sens = if (type == TransformType.ROTATE) 2.2 else 1.4
                    gizmoAccum += (dx - dy) * sens
                    val delta = gizmoAccum.toInt()
                    if (delta != 0) {
                        gizmoAccum -= delta
                        val lo = if (type == TransformType.ROTATE) 0 else -2047
                        val hi = 2047
                        parts[axis] = (parts[axis] + delta).coerceIn(lo, hi)
                        seqFrames[currentFrame] = frame.withLabelValues(lab, type, parts[0], parts[1], parts[2])
                        loadSlidersFromFrame()
                        applyPose(fullUi = false)
                        refreshTimeline()
                    }
                }
            } else if (e.button == MouseButton.PRIMARY || e.button == MouseButton.SECONDARY) {
                yaw.angle += dx * 0.4
                pitch.angle += dy * 0.4
            }
            lastX = e.sceneX
            lastY = e.sceneY
        }
        sub.setOnMouseReleased { e ->
            val wasGizmo = gizmoDrag
            gizmo.end()
            gizmoDrag = false
            if (wasGizmo) {
                applyPose(fullUi = true)
                return@setOnMouseReleased
            }
            if (e.button != MouseButton.PRIMARY || dragDist > 5.0) return@setOnMouseReleased
            val pick = e.pickResult
            val node = pick?.intersectedNode as? MeshView
            val faceMap = node?.userData as? IntArray
            if (pick == null || node == null || faceMap == null) {
                clearVskin()
                return@setOnMouseReleased
            }
            val local = pick.intersectedFace
            if (local < 0 || local >= faceMap.size) return@setOnMouseReleased
            val lab = vskinOfFace(model, faceMap[local])
            if (lab == null || lab == pickedLabel) {
                clearVskin()
                return@setOnMouseReleased
            }
            pickedLabel = lab
            pickedLabelUi.text = "vskin $lab  (${model.vertexCountForLabel(lab)} verts)"
            loadSlidersFromFrame()
            applyPose()
        }
        sub.addEventHandler(ScrollEvent.SCROLL) { e ->
            distance = (distance - e.deltaY * 0.4).coerceIn(20.0, 8000.0)
            camera.translateZ = -distance
        }

        val compass = axisCompass(yaw, pitch)
        val stack = StackPane(sub, compass)
        StackPane.setAlignment(compass, Pos.TOP_RIGHT)
        StackPane.setMargin(compass, Insets(8.0))

        val timeline = TimelineBar(
            frames = { seqFrames },
            delays = { seqDelays },
            current = { currentFrame },
            labels = { labels },
            selectedLabel = { selectedLabel() },
            selectedType = { editType() },
            onSeek = { i -> seekFrame(i) },
            onPick = { i, lab, type ->
                seekFrame(i)
                pickedLabel = lab
                pickedLabelUi.text = "vskin $lab  (${model.vertexCountForLabel(lab)} verts)"
                if (type == TransformType.ROTATE) rotBtn.isSelected = true else moveBtn.isSelected = true
                loadSlidersFromFrame()
                applyPose()
            },
            onClearLabel = { clearVskin() },
            onPlayToggle = { togglePlay() },
            onFirst = { seekFrame(0) },
            onPrev = { seekFrame(currentFrame - 1) },
            onNext = { seekFrame(currentFrame + 1) },
            onLast = { seekFrame(seqFrames.lastIndex) },
            onDelay = { frame, ticks ->
                if (frame in seqDelays.indices) {
                    pushHist()
                    seqDelays[frame] = ticks
                    refreshTimeline()
                    applyPose(fullUi = true)
                }
            },
            onEdit = { frame, lab, type, axis, value ->
                if (frame in seqFrames.indices) {
                    pushHist()
                    val src = seqFrames[frame]
                    val def = if (type == TransformType.SCALE) 128 else 0
                    val cur = src.valuesForLabel(lab, type) ?: Triple(def, def, def)
                    val nx = if (axis == 0) value else cur.first
                    val ny = if (axis == 1) value else cur.second
                    val nz = if (axis == 2) value else cur.third
                    seqFrames[frame] = src.withLabelValues(lab, type, nx, ny, nz)
                    pickedLabel = lab
                    pickedLabelUi.text = "vskin $lab  (${model.vertexCountForLabel(lab)} verts)"
                    if (type == TransformType.ROTATE) rotBtn.isSelected = true else moveBtn.isSelected = true
                    seekFrame(frame)
                    loadSlidersFromFrame()
                    applyPose(fullUi = true)
                }
            },
        )
        refreshTimeline = { timeline.refresh() }
        markPlayhead = { timeline.markPlayhead(it) }
        timelineStatus = { timeline.status.text = it }
        timelinePlaying = { timeline.setPlaying(it) }
        timelineEnabled = { timeline.setEnabled(it) }
        timeline.setEnabled(seqFrames.isNotEmpty())
        timeline.setPlaying(false)

        timeline.root.minHeight = 140.0
        timeline.root.prefHeight = 240.0

        val exportItem = MenuItem("Export extras…")
        exportItem.setOnAction { exportBtn.fire() }
        val importItem = MenuItem("Import extras…")
        importItem.setOnAction { importBtn.fire() }
        val openNpcItem = MenuItem("Open NPC…")
        openNpcItem.setOnAction {
            val dlg = javafx.scene.control.TextInputDialog("132")
            dlg.title = "Open NPC"
            dlg.headerText = "Load an NPC by id. 132 is the default Monkey."
            val id = dlg.showAndWait().orElse(null)?.trim()?.toIntOrNull() ?: return@setOnAction
            applyNpc(id)
        }
        val exitItem = MenuItem("Exit")
        exitItem.setOnAction { Platform.exit() }
        val fileMenu = Menu("File", null, openNpcItem, SeparatorMenuItem(), exportItem, importItem, SeparatorMenuItem(), exitItem)
        val undoItem = MenuItem("Undo")
        undoItem.accelerator = KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN)
        undoItem.setOnAction { undoEdit() }
        val redoItem = MenuItem("Redo")
        redoItem.accelerator = KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN)
        redoItem.setOnAction { redoEdit() }
        val resetItem = MenuItem("Reset sequence")
        resetItem.setOnAction { resetEdits() }
        val grokKeyItem = MenuItem("Grok API key…")
        grokKeyItem.setOnAction { GrokDialog.askKey() }
        val grokItem = MenuItem("Generate with Grok…")
        grokItem.setOnAction {
            if (seqFrames.isEmpty()) return@setOnAction
            GrokDialog.promptAndGenerate(
                seqId = seqIdLoaded,
                baseId = seqFrames.first().base.id,
                npcId = loadedNpcId,
                npcName = loadedNpcId?.let { NpcCatalog.get(it)?.name },
                modelIds = modelIds,
                labels = labels,
                selectedLabel = selectedLabel(),
                vertsOf = { model.vertexCountForLabel(it) },
                frames = seqFrames,
                delays = seqDelays,
            ) { patches ->
                pushHist()
                for (p in patches) {
                    if (p.frame !in seqFrames.indices) continue
                    seqFrames[p.frame] = seqFrames[p.frame].withLabelValues(p.label, p.type, p.x, p.y, p.z)
                    if (p.delay != null && p.frame in seqDelays.indices) {
                        seqDelays[p.frame] = p.delay.coerceIn(1, 255)
                    }
                }
                loadSlidersFromFrame()
                applyPose(fullUi = true)
            }
        }
        val grokNewItem = MenuItem("New animation with Grok…")
        grokNewItem.setOnAction {
            if (seqFrames.isEmpty()) return@setOnAction
            val base = seqFrames.first().base
            GrokDialog.promptAndCreateNew(
                seqId = seqIdLoaded,
                baseId = base.id,
                npcId = loadedNpcId,
                npcName = loadedNpcId?.let { NpcCatalog.get(it)?.name },
                modelIds = modelIds,
                labels = labels,
                vertsOf = { model.vertexCountForLabel(it) },
                frames = seqFrames,
                delays = seqDelays,
            ) { patches, frameCount ->
                pushHist()
                val n = frameCount.coerceIn(4, 12)
                val next = MutableList(n) { AnimFrame.fromEdits(base, emptyList()) }
                val nextDelays = IntArray(n) { 5 }
                for (p in patches) {
                    if (p.frame !in next.indices) continue
                    next[p.frame] = next[p.frame].withLabelValues(p.label, p.type, p.x, p.y, p.z)
                    if (p.delay != null) nextDelays[p.frame] = p.delay.coerceIn(1, 255)
                }
                seqFrames = next
                seqDelays = nextDelays
                currentFrame = 0
                frameSlider.max = (seqFrames.size - 1).toDouble()
                frameSlider.value = 0.0
                timelineEnabled(true)
                exportBtn.isDisable = false
                loadSlidersFromFrame()
                applyPose(fullUi = true)
            }
        }
        val editMenu = Menu("Edit", null, undoItem, redoItem, SeparatorMenuItem(), resetItem, SeparatorMenuItem(), grokItem, grokNewItem, grokKeyItem)

        val playItem = MenuItem("Play / Pause")
        playItem.setOnAction { togglePlay() }
        val firstItem = MenuItem("First frame")
        firstItem.setOnAction { seekFrame(0) }
        val prevItem = MenuItem("Previous frame")
        prevItem.setOnAction { seekFrame(currentFrame - 1) }
        val nextItem = MenuItem("Next frame")
        nextItem.setOnAction { seekFrame(currentFrame + 1) }
        val lastItem = MenuItem("Last frame")
        lastItem.setOnAction { seekFrame(seqFrames.lastIndex) }
        val playMenu = Menu("Playback", null, playItem, SeparatorMenuItem(), firstItem, prevItem, nextItem, lastItem)

        val aboutItem = MenuItem("About")
        aboutItem.setOnAction {
            Alert(Alert.AlertType.INFORMATION, "530 / 2009scape label animation editor.\nNot a cache packer. Reuses the model's existing AnimBase.", ButtonType.OK).apply {
                title = "rs530-anim-tool"
                headerText = "rs530-anim-tool"
                showAndWait()
            }
        }
        val texToggle = ToggleButton("textures").apply { isSelected = true }
        val wireToggle = ToggleButton("wire")
        val vertToggle = ToggleButton("verts")
        fun applyView() {
            showTextures = texToggle.isSelected
            showWire = wireToggle.isSelected
            showVerts = vertToggle.isSelected
            rebuild()
        }

        val texItem = CheckMenuItem("Textures").apply { isSelected = true }
        val wireItem = CheckMenuItem("Triangle outline")
        val vertItem = CheckMenuItem("Vertices")
        fun syncView(fromMenu: Boolean) {
            if (fromMenu) {
                texToggle.isSelected = texItem.isSelected
                wireToggle.isSelected = wireItem.isSelected
                vertToggle.isSelected = vertItem.isSelected
            } else {
                texItem.isSelected = texToggle.isSelected
                wireItem.isSelected = wireToggle.isSelected
                vertItem.isSelected = vertToggle.isSelected
            }
            applyView()
        }
        texItem.setOnAction { syncView(true) }
        wireItem.setOnAction { syncView(true) }
        vertItem.setOnAction { syncView(true) }
        texToggle.setOnAction { syncView(false) }
        wireToggle.setOnAction { syncView(false) }
        vertToggle.setOnAction { syncView(false) }
        val viewMenu = Menu("View", null, texItem, wireItem, vertItem)

        val helpMenu = Menu("Help", null, aboutItem)
        val menuBar = MenuBar(fileMenu, editMenu, viewMenu, playMenu, helpMenu)
        timeline.setTools(selectBtn, moveBtn, rotBtn, texToggle, wireToggle, vertToggle)

        val statusText = Label("ready").apply { textFill = Color.rgb(200, 200, 206) }
        windowStatus = { statusText.text = it }
        val statusBar = HBox(statusText).apply {
            padding = Insets(4.0, 8.0, 4.0, 8.0)
            style = "-fx-background-color: #16161a;"
        }
        HBox.setHgrow(statusText, Priority.ALWAYS)

        stack.minHeight = 0.0
        val work = BorderPane()
        work.minHeight = 0.0
        work.center = stack
        work.left = sideScroll
        timeline.root.minHeight = 220.0
        timeline.root.prefHeight = 260.0
        timeline.root.maxHeight = 320.0
        val south = VBox(timeline.root, statusBar)
        val pane = BorderPane()
        pane.top = menuBar
        pane.center = work
        pane.bottom = south
        fun fitSub() {
            val w = stack.width
            val h = stack.height
            if (w > 2 && h > 2) {
                sub.width = w
                sub.height = h
            }
        }
        stack.widthProperty().addListener { _, _, _ -> fitSub() }
        stack.heightProperty().addListener { _, _, _ -> fitSub() }
        stage.widthProperty().addListener { _, _, _ -> Platform.runLater { fitSub() } }
        stage.heightProperty().addListener { _, _, _ -> Platform.runLater { fitSub() } }

        val title = buildString {
            append("rs530-anim-tool  model ${modelIds.joinToString("+")}")
            if (seqId != null) append("  seq $seqId")
        }
        stage.title = title
        stage.minWidth = 900.0
        stage.minHeight = 700.0
        stage.scene = Scene(pane, 1180.0, 820.0).also { sc ->
            val css = ModelViewer::class.java.getResource("/rs530anim/dark.css")
            if (css != null) sc.stylesheets += css.toExternalForm()
            sc.fill = Color.rgb(30, 30, 34)
            sc.addEventHandler(KeyEvent.KEY_PRESSED) { e ->
                if (e.code == KeyCode.ESCAPE) {
                    clearVskin()
                    e.consume()
                }
            }
        }
        if (seqFrames.isNotEmpty()) loadSlidersFromFrame()
        applyPose()
        stage.maximizedProperty().addListener { _, _, _ ->
            Platform.runLater {
                pane.requestLayout()
                stack.requestLayout()
            }
        }
        stage.show()
        Platform.runLater {
            pane.requestLayout()
            fitSub()
        }
        val scanBase = seqFrames.firstOrNull()?.base?.id
        if (scanBase != null) {
            Thread {
                try {
                    Js5Store(settings).use { store ->
                        val found = AnimLibrary.seqsUsingBase(store, scanBase)
                        println("seqs on base $scanBase: ${found.size}  ${found.take(24)}")
                        Platform.runLater {
                            for (id in found) {
                                if (seqBox.items.none { it.id == id }) {
                                    seqBox.items += NpcCatalog.SeqRef(id, "base $scanBase")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    System.err.println("seq scan skipped: ${e.message}")
                }
            }.apply {
                isDaemon = true
                name = "seq-scan"
                start()
            }
        }
    }

    companion object {
        private var bootArgs: List<String> = emptyList()

        fun open(args: List<String>) {
            bootArgs = args
            launch(ModelViewer::class.java, *args.toTypedArray())
        }

        fun openWindow(args: List<String>) {
            bootArgs = args
            ModelViewer().start(Stage())
        }
    }
}

private fun fitDistance(model: Rs2Model): Double {
    var max = 1
    for (i in 0 until model.vertexCount) {
        max = max(max, kotlin.math.abs(model.verticesX[i]))
        max = max(max, kotlin.math.abs(model.verticesY[i]))
        max = max(max, kotlin.math.abs(model.verticesZ[i]))
    }
    return max * 3.0 + 80.0
}

private fun vskinOfFace(model: Rs2Model, face: Int): Int? {
    val bones = model.vertexBones ?: return null
    if (face < 0 || face >= model.faceCount) return null
    val labels = intArrayOf(
        bones.getOrElse(model.faceA[face]) { -1 },
        bones.getOrElse(model.faceB[face]) { -1 },
        bones.getOrElse(model.faceC[face]) { -1 },
    ).filter { it >= 0 }
    if (labels.isEmpty()) return null
    return labels.groupingBy { it }.eachCount().maxBy { it.value }.key
}

private fun vskinGlow(model: Rs2Model, label: Int?): List<Node> {
    if (label == null) return emptyList()
    val bones = model.vertexBones ?: return emptyList()
    val faces = ArrayList<Int>()
    for (i in 0 until model.faceCount) {
        val a = model.faceA[i]
        val b = model.faceB[i]
        val c = model.faceC[i]
        if (bones.getOrElse(a) { -1 } == label || bones.getOrElse(b) { -1 } == label || bones.getOrElse(c) { -1 } == label) {
            faces += i
        }
    }
    if (faces.isEmpty()) return emptyList()
    val used = HashSet<Int>()
    for (face in faces) {
        used += model.faceA[face]
        used += model.faceB[face]
        used += model.faceC[face]
    }
    val nx = FloatArray(model.vertexCount)
    val ny = FloatArray(model.vertexCount)
    val nz = FloatArray(model.vertexCount)
    for (face in faces) {
        val a = model.faceA[face]
        val b = model.faceB[face]
        val c = model.faceC[face]
        val ux = (model.verticesX[b] - model.verticesX[a]).toFloat()
        val uy = (model.verticesY[b] - model.verticesY[a]).toFloat()
        val uz = (model.verticesZ[b] - model.verticesZ[a]).toFloat()
        val vx = (model.verticesX[c] - model.verticesX[a]).toFloat()
        val vy = (model.verticesY[c] - model.verticesY[a]).toFloat()
        val vz = (model.verticesZ[c] - model.verticesZ[a]).toFloat()
        val fx = uy * vz - uz * vy
        val fy = uz * vx - ux * vz
        val fz = ux * vy - uy * vx
        for (vi in intArrayOf(a, b, c)) {
            nx[vi] += fx
            ny[vi] += fy
            nz[vi] += fz
        }
    }
    val points = FloatArray(model.vertexCount * 3)
    var p = 0
    for (i in 0 until model.vertexCount) {
        var x = model.verticesX[i].toFloat()
        var y = model.verticesY[i].toFloat()
        var z = model.verticesZ[i].toFloat()
        if (i in used) {
            val len = kotlin.math.sqrt(nx[i] * nx[i] + ny[i] * ny[i] + nz[i] * nz[i])
            if (len > 1e-4f) {
                val s = 1.1f / len
                x += nx[i] * s
                y += ny[i] * s
                z += nz[i] * s
            }
        }
        points[p++] = x
        points[p++] = y
        points[p++] = z
    }
    val mesh = TriangleMesh()
    val uvs = floatArrayOf(0f, 0f)
    val idx = IntArray(faces.size * 6)
    var f = 0
    for (face in faces) {
        idx[f++] = model.faceA[face]; idx[f++] = 0
        idx[f++] = model.faceB[face]; idx[f++] = 0
        idx[f++] = model.faceC[face]; idx[f++] = 0
    }
    mesh.points.addAll(*points)
    mesh.texCoords.addAll(*uvs)
    mesh.faces.addAll(*idx)
    val line = MeshView(mesh).apply {
        cullFace = CullFace.NONE
        drawMode = DrawMode.LINE
        isMouseTransparent = true
        material = PhongMaterial(Color.web("#c9b25a"))
    }
    return listOf(line)
}

private fun vertexDots(model: Rs2Model): Group {
    val g = Group()
    val mat = PhongMaterial(Color.rgb(220, 220, 230))
    for (i in 0 until model.vertexCount) {
        val s = Sphere(1.2)
        s.material = mat
        s.translateX = model.verticesX[i].toDouble()
        s.translateY = model.verticesY[i].toDouble()
        s.translateZ = model.verticesZ[i].toDouble()
        s.isMouseTransparent = true
        g.children += s
    }
    g.isMouseTransparent = true
    g.userData = "verts"
    return g
}

private fun buildMeshes(
    model: Rs2Model,
    materials: TextureMaterials?,
    textures: Boolean = true,
    wire: Boolean = false,
    highlightTex: Int? = null,
): List<Node> {
    data class Tri(val face: Int, val a: Int, val b: Int, val c: Int, val color: Int, val tex: Int)
    val minX = (model.verticesX.minOrNull() ?: 0).toFloat()
    val maxX = (model.verticesX.maxOrNull() ?: 1).toFloat()
    val minY = (model.verticesY.minOrNull() ?: 0).toFloat()
    val maxY = (model.verticesY.maxOrNull() ?: 1).toFloat()
    val minZ = (model.verticesZ.minOrNull() ?: 0).toFloat()
    val maxZ = (model.verticesZ.maxOrNull() ?: 1).toFloat()
    val spanX = (maxX - minX).coerceAtLeast(1f)
    val spanY = (maxY - minY).coerceAtLeast(1f)
    val spanZ = (maxZ - minZ).coerceAtLeast(1f)
    val tris = ArrayList<Tri>(model.faceCount)
    for (i in 0 until model.faceCount) {
        val tex = model.faceTextures?.getOrNull(i)?.toInt()?.and(0xFFFF) ?: 0xFFFF
        tris += Tri(i, model.faceA[i], model.faceB[i], model.faceC[i], shadeFace(model, i, materials), tex)
    }
    return tris.groupBy { it.tex }.map { (tex, group) ->
        val mesh = TriangleMesh()
        val points = FloatArray(model.vertexCount * 3)
        var p = 0
        for (i in 0 until model.vertexCount) {
            points[p++] = model.verticesX[i].toFloat()
            points[p++] = model.verticesY[i].toFloat()
            points[p++] = model.verticesZ[i].toFloat()
        }
        val uvs = FloatArray(group.size * 6)
        val faces = IntArray(group.size * 6)
        var t = 0
        var f = 0
        var ti = 0
        fun wrap(s: Float): Float {
            val w = s - kotlin.math.floor(s.toDouble()).toFloat()
            return if (w < 0f) w + 1f else w
        }
        for (tri in group) {
            val ax = model.verticesX[tri.a].toFloat()
            val ay = model.verticesY[tri.a].toFloat()
            val az = model.verticesZ[tri.a].toFloat()
            val nx = (model.verticesY[tri.b] - model.verticesY[tri.a]) * (model.verticesZ[tri.c] - model.verticesZ[tri.a]) -
                (model.verticesY[tri.c] - model.verticesY[tri.a]) * (model.verticesZ[tri.b] - model.verticesZ[tri.a])
            val ny = (model.verticesZ[tri.b] - model.verticesZ[tri.a]) * (model.verticesX[tri.c] - model.verticesX[tri.a]) -
                (model.verticesZ[tri.c] - model.verticesZ[tri.a]) * (model.verticesX[tri.b] - model.verticesX[tri.a])
            val nz = (model.verticesX[tri.b] - model.verticesX[tri.a]) * (model.verticesY[tri.c] - model.verticesY[tri.a]) -
                (model.verticesX[tri.c] - model.verticesX[tri.a]) * (model.verticesY[tri.b] - model.verticesY[tri.a])
            val anx = kotlin.math.abs(nx)
            val any = kotlin.math.abs(ny)
            val anz = kotlin.math.abs(nz)
            fun uv(index: Int): Pair<Float, Float> {
                clientPmnUv(model, tri.face, index)?.let { return it }
                val x = model.verticesX[index].toFloat()
                val y = model.verticesY[index].toFloat()
                val z = model.verticesZ[index].toFloat()
                return when {
                    anx >= any && anx >= anz -> (z - minZ) / spanZ to (y - minY) / spanY
                    any >= anx && any >= anz -> (x - minX) / spanX to (z - minZ) / spanZ
                    else -> (x - minX) / spanX to (y - minY) / spanY
                }
            }
            val ua = uv(tri.a); val ub = uv(tri.b); val uc = uv(tri.c)
            uvs[t++] = wrap(ua.first); uvs[t++] = wrap(ua.second)
            uvs[t++] = wrap(ub.first); uvs[t++] = wrap(ub.second)
            uvs[t++] = wrap(uc.first); uvs[t++] = wrap(uc.second)
            faces[f++] = tri.a; faces[f++] = ti++
            faces[f++] = tri.b; faces[f++] = ti++
            faces[f++] = tri.c; faces[f++] = ti++
        }
        mesh.points.addAll(*points)
        mesh.texCoords.addAll(*uvs)
        mesh.faces.addAll(*faces)
        mesh.faceSmoothingGroups.addAll(*IntArray(group.size) { 1 })
        val view = MeshView(mesh)
        view.userData = IntArray(group.size) { group[it].face }
        view.cullFace = CullFace.NONE
        view.drawMode = DrawMode.FILL
        val hsl = group.first().color
        val dim = highlightTex != null && tex != highlightTex
        view.material = PhongMaterial(Hsl.toFx(hsl)).apply {
            specularColor = Color.BLACK
            specularPower = 1.0
            if (textures && tex != 0xFFFF) {
                val img = rs530anim.tex.TextureLibrary.image(tex)
                if (img != null) {
                    diffuseMap = img
                    diffuseColor = Hsl.toFx(hsl)
                }
            }
            if (dim) {
                diffuseColor = (diffuseColor ?: Color.GRAY).deriveColor(0.0, 0.4, 0.35, 1.0)
                diffuseMap = null
            }
        }
        val nodes = mutableListOf<Node>(view)
        if (wire) {
            val outline = MeshView(mesh)
            outline.cullFace = CullFace.NONE
            outline.drawMode = DrawMode.LINE
            outline.material = PhongMaterial(Color.rgb(20, 20, 24))
            outline.isMouseTransparent = true
            nodes += outline
        }
        nodes
    }.flatten()
}

/** Same sun as RawModel.createModel(..., -50, -10, -50) with ambient 64 / attenuation 768. */
private const val LIGHT_AMBIENT = 64
private const val LIGHT_ATTEN = 768
private const val LIGHT_X = -50
private const val LIGHT_Y = -10
private const val LIGHT_Z = -50

private fun axisRod(length: Double, color: Color): Cylinder {
    val rod = Cylinder(0.7, length)
    rod.material = PhongMaterial(color)
    return rod
}

/** Tiny XYZ gizmo; yaw/pitch bound to the main orbit so it tracks the camera. */
private fun axisCompass(yaw: Rotate, pitch: Rotate): SubScene {
    val x = axisRod(18.0, Color.web("#e74c3c")).apply {
        transforms.add(Rotate(90.0, Rotate.Z_AXIS))
    }
    val y = axisRod(18.0, Color.web("#2ecc71"))
    val z = axisRod(18.0, Color.web("#3498db")).apply {
        transforms.add(Rotate(90.0, Rotate.X_AXIS))
    }
    val pivot = Group(x, y, z, AmbientLight(Color.WHITE))
    val cy = Rotate(0.0, Rotate.Y_AXIS).apply { angleProperty().bind(yaw.angleProperty()) }
    val cp = Rotate(0.0, Rotate.X_AXIS).apply { angleProperty().bind(pitch.angleProperty()) }
    pivot.transforms.addAll(cy, cp)
    val cam = PerspectiveCamera(true)
    cam.nearClip = 0.1
    cam.farClip = 200.0
    cam.translateZ = -48.0
    val scene = SubScene(Group(pivot), 112.0, 112.0, true, SceneAntialiasing.BALANCED)
    scene.fill = Color.rgb(20, 20, 24, 0.35)
    scene.camera = cam
    scene.isMouseTransparent = true
    return scene
}

/** Tile plane at model origin Y=0 — same place the client plants an NPC. */
private fun groundMarker(model: Rs2Model): MeshView {
    var minX = 0; var maxX = 0; var minZ = 0; var maxZ = 0
    if (model.vertexCount > 0) {
        minX = model.verticesX.minOrNull() ?: 0
        maxX = model.verticesX.maxOrNull() ?: 0
        minZ = model.verticesZ.minOrNull() ?: 0
        maxZ = model.verticesZ.maxOrNull() ?: 0
    }
    val pad = 12f
    val y = 0f
    val x0 = minX.toFloat() - pad
    val x1 = maxX.toFloat() + pad
    val z0 = minZ.toFloat() - pad
    val z1 = maxZ.toFloat() + pad
    val midX = (x0 + x1) / 2f
    val notch = z0 - 18f
    val mesh = TriangleMesh()
    mesh.points.addAll(
        x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1,
        midX - 8f, y, z0, midX + 8f, y, z0, midX, y, notch,
    )
    mesh.texCoords.addAll(0f, 0f)
    mesh.faces.addAll(
        0, 0, 1, 0, 2, 0,
        0, 0, 2, 0, 3, 0,
        4, 0, 5, 0, 6, 0,
    )
    val view = MeshView(mesh)
    view.cullFace = CullFace.NONE
    view.drawMode = DrawMode.LINE
    view.material = PhongMaterial(Color.web("#8ec07c"))
    return view
}




/** GlModel type-0 P/M/N barycentric, type-2 cylindrical (method4095/4097). */
private fun clientPmnUv(model: Rs2Model, face: Int, vertex: Int): Pair<Float, Float>? {
    val slot = model.textureIndex?.getOrNull(face)?.toInt() ?: return null
    if (slot < 0) return null
    val type = model.textureTypes?.getOrNull(slot)?.toInt() ?: return null
    if (type == 2) return clientType2Uv(model, slot, vertex)
    if (type != 0) return null
    val p = model.textureP?.getOrNull(slot)?.toInt()?.and(0xFFFF) ?: return null
    val m = model.textureM?.getOrNull(slot)?.toInt()?.and(0xFFFF) ?: return null
    val n = model.textureN?.getOrNull(slot)?.toInt()?.and(0xFFFF) ?: return null
    if (p >= model.vertexCount || m >= model.vertexCount || n >= model.vertexCount) return null
    fun vx(i: Int) = model.verticesX[i].toFloat()
    fun vy(i: Int) = model.verticesY[i].toFloat()
    fun vz(i: Int) = model.verticesZ[i].toFloat()
    val px = vx(p); val py = vy(p); val pz = vz(p)
    val mx = vx(m) - px; val my = vy(m) - py; val mz = vz(m) - pz
    val nxv = vx(n) - px; val ny = vy(n) - py; val nz = vz(n) - pz
    val cx = vx(vertex) - px; val cy = vy(vertex) - py; val cz = vz(vertex) - pz
    val i = my * nz - mz * ny
    val j = mz * nxv - mx * nz
    val k = mx * ny - my * nxv
    val f = i * mx + j * my + k * mz
    if (f == 0f) return null
    val u = (i * cx + j * cy + k * cz) / f
    val ii = my * k - mz * j
    val jj = mz * i - mx * k
    val kk = mx * j - my * i
    val g = ii * nxv + jj * ny + kk * nz
    if (g == 0f) return null
    val v = (ii * cx + jj * cy + kk * cz) / g
    return u to v
}

private fun clientType2Uv(model: Rs2Model, slot: Int, vertex: Int): Pair<Float, Float>? {
    val sx = 64f / ((model.textureScaleX?.getOrNull(slot)?.toInt()?.and(0xFFFF) ?: 64).coerceAtLeast(1))
    val sy = 64f / ((model.textureScaleY?.getOrNull(slot)?.toInt()?.and(0xFFFF) ?: 64).coerceAtLeast(1))
    val sz = 64f / ((model.textureScaleZ?.getOrNull(slot)?.toInt()?.and(0xFFFF) ?: 64).coerceAtLeast(1))
    val p = model.textureP?.getOrNull(slot)?.toInt() ?: 0
    val m = model.textureM?.getOrNull(slot)?.toInt() ?: 0
    val n = model.textureN?.getOrNull(slot)?.toInt() ?: 0
    val rot = model.textureRotY?.getOrNull(slot)?.toInt()?.and(0xFF) ?: 0
    val basis = glMethod4097(p, m, n, rot, sx, sy, sz)
    var minX = Int.MAX_VALUE; var maxX = Int.MIN_VALUE
    var minY = Int.MAX_VALUE; var maxY = Int.MIN_VALUE
    var minZ = Int.MAX_VALUE; var maxZ = Int.MIN_VALUE
    val idx = model.textureIndex ?: return null
    for (f in 0 until model.faceCount) {
        if (idx[f].toInt() != slot) continue
        for (v in intArrayOf(model.faceA[f], model.faceB[f], model.faceC[f])) {
            minX = minOf(minX, model.verticesX[v]); maxX = maxOf(maxX, model.verticesX[v])
            minY = minOf(minY, model.verticesY[v]); maxY = maxOf(maxY, model.verticesY[v])
            minZ = minOf(minZ, model.verticesZ[v]); maxZ = maxOf(maxZ, model.verticesZ[v])
        }
    }
    val ox = (minX + maxX) / 2
    val oy = (minY + maxY) / 2
    val oz = (minZ + maxZ) / 2
    val dx = model.verticesX[vertex] - ox
    val dy = model.verticesY[vertex] - oy
    val dz = model.verticesZ[vertex] - oz
    val lx = dx * basis[0] + dy * basis[1] + dz * basis[2]
    val ly = dx * basis[3] + dy * basis[4] + dz * basis[5]
    val lz = dx * basis[6] + dy * basis[7] + dz * basis[8]
    var u = (kotlin.math.atan2(lx, lz) / 6.2831855f) + 0.5f
    var v = ly + ((model.textureOff?.getOrNull(slot)?.toInt() ?: 0) / 256f) + 0.5f
    val dir = model.textureDir?.getOrNull(slot)?.toInt() ?: 0
    when (dir) {
        1 -> { val t = u; u = -v; v = t }
        2 -> { u = -u; v = -v }
        3 -> { val t = u; u = v; v = -t }
    }
    return u to v
}

private fun glMethod4097(arg0: Int, arg1: Int, arg2: Int, arg3: Int, arg4: Float, arg5: Float, arg6: Float): FloatArray {
    val c = kotlin.math.cos(arg3 * 0.024543693f)
    val s = kotlin.math.sin(arg3 * 0.024543693f)
    val r = floatArrayOf(c, 0f, s, 0f, 1f, 0f, -s, 0f, c)
    val dip = arg1 / 32767f
    val dipZ = -kotlin.math.sqrt((1f - dip * dip).coerceAtLeast(0f))
    val om = 1f - dip
    val len = kotlin.math.sqrt((arg0.toFloat() * arg0 + arg2.toFloat() * arg2))
    var gx = 1f
    var gz = 0f
    val out = FloatArray(9)
    if (len == 0f && dip == 0f) {
        r.copyInto(out)
    } else {
        if (len != 0f) {
            gx = -arg2 / len
            gz = arg0 / len
        }
        val b = floatArrayOf(
            dip + gx * gx * om, gz * dipZ, gz * gx * om,
            -gz * dipZ, dip, gx * dipZ,
            gx * gz * om, -gx * dipZ, dip + gz * gz * om,
        )
        out[0] = r[0] * b[0] + r[1] * b[3] + r[2] * b[6]
        out[1] = r[0] * b[1] + r[1] * b[4] + r[2] * b[7]
        out[2] = r[0] * b[2] + r[1] * b[5] + r[2] * b[8]
        out[3] = r[3] * b[0] + r[4] * b[3] + r[5] * b[6]
        out[4] = r[3] * b[1] + r[4] * b[4] + r[5] * b[7]
        out[5] = r[3] * b[2] + r[4] * b[5] + r[5] * b[8]
        out[6] = r[6] * b[0] + r[7] * b[3] + r[8] * b[6]
        out[7] = r[6] * b[1] + r[7] * b[4] + r[8] * b[7]
        out[8] = r[6] * b[2] + r[7] * b[5] + r[8] * b[8]
    }
    out[0] *= arg4; out[1] *= arg4; out[2] *= arg4
    out[3] *= arg5; out[4] *= arg5; out[5] *= arg5
    out[6] *= arg6; out[7] *= arg6; out[8] *= arg6
    return out
}

private fun faceHsl(model: Rs2Model, face: Int, materials: TextureMaterials?): Int {
    return model.faceColors[face].toInt() and 0xFFFF
}

private fun shadeFace(model: Rs2Model, face: Int, materials: TextureMaterials?): Int {
    val a = model.faceA[face]
    val b = model.faceB[face]
    val c = model.faceC[face]
    var nx = (model.verticesY[b] - model.verticesY[a]) * (model.verticesZ[c] - model.verticesZ[a]) -
        (model.verticesY[c] - model.verticesY[a]) * (model.verticesZ[b] - model.verticesZ[a])
    var ny = (model.verticesZ[b] - model.verticesZ[a]) * (model.verticesX[c] - model.verticesX[a]) -
        (model.verticesZ[c] - model.verticesZ[a]) * (model.verticesX[b] - model.verticesX[a])
    var nz = (model.verticesX[b] - model.verticesX[a]) * (model.verticesY[c] - model.verticesY[a]) -
        (model.verticesX[c] - model.verticesX[a]) * (model.verticesY[b] - model.verticesY[a])
    // RawModel.calculateNormals: shrink to ±8192 then scale to length 256.
    while (
        nx > 8192 || ny > 8192 || nz > 8192 ||
        nx < -8192 || ny < -8192 || nz < -8192
    ) {
        nx = nx shr 1
        ny = ny shr 1
        nz = nz shr 1
    }
    var mag = kotlin.math.sqrt((nx.toLong() * nx + ny.toLong() * ny + nz.toLong() * nz).toDouble()).toInt()
    if (mag <= 0) mag = 1
    nx = nx * 256 / mag
    ny = ny * 256 / mag
    nz = nz * 256 / mag
    val sun = kotlin.math.sqrt(
        (LIGHT_X * LIGHT_X + LIGHT_Y * LIGHT_Y + LIGHT_Z * LIGHT_Z).toDouble(),
    ).toInt()
    val local108 = LIGHT_ATTEN * sun shr 8
    val denom = (local108 + local108 / 2).coerceAtLeast(1)
    var lightness = LIGHT_AMBIENT + (LIGHT_X * nx + LIGHT_Y * ny + LIGHT_Z * nz) / denom
    if (lightness < 2) lightness = 2
    if (lightness > 126) lightness = 126
    return Hsl.multiplyLightness(faceHsl(model, face, materials), lightness)
}
