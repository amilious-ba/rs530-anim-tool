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
import javafx.scene.control.Label
import javafx.scene.control.Menu
import javafx.scene.control.MenuBar
import javafx.scene.control.MenuItem
import javafx.scene.control.ScrollPane
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.control.Slider
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
        val raw = parameters.raw
        val modelSpec = raw.getOrNull(0) ?: "1456"
        val modelIds = MonkeySkins.resolve(modelSpec)
        val seqId = raw.getOrNull(1)?.toIntOrNull()
        val frameNo = raw.getOrNull(2)?.toIntOrNull() ?: 0

        val settings = CacheSettings.load(null)
        var materials: TextureMaterials? = null
        val model = Js5Store(settings).use { store ->
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
        centerModel(model)
        val animator = ModelAnimator(model)
        val bind = animator.copyBindPose()
        var currentFrame = frameNo.coerceIn(0, (seqFrames.size - 1).coerceAtLeast(0))

        val world = Group()
        world.children += AmbientLight(Color.color(0.55, 0.55, 0.55))
        world.children += PointLight(Color.WHITE).apply {
            translateX = -80.0
            translateY = -120.0
            translateZ = -80.0
        }
        world.children += groundMarker(model)

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
        world.children += highlight
        val markMat = PhongMaterial(Color.YELLOW)
        val meshGroup = Group()
        meshGroup.children.addAll(buildMeshes(model, materials))
        world.children.add(0, meshGroup)

        val labels = model.uniqueVertexLabels()
        var pickedLabel: Int? = labels.firstOrNull()
        fun selectedLabel(): Int? = pickedLabel
        val pickedLabelUi = Label(
            pickedLabel?.let { "vskin $it  (${model.vertexCountForLabel(it)} verts)" } ?: "no vskin",
        )

        val frameSlider = Slider(0.0, (seqFrames.size - 1).coerceAtLeast(0).toDouble(), currentFrame.toDouble())
        frameSlider.isSnapToTicks = true
        frameSlider.majorTickUnit = 1.0
        frameSlider.blockIncrement = 1.0
        val frameLabel = Label(if (seqFrames.isEmpty()) "no seq" else "frame $currentFrame / ${seqFrames.size}")

        val typeGroup = ToggleGroup()
        val rotBtn = ToggleButton("rotate").apply { toggleGroup = typeGroup; isSelected = true }
        val moveBtn = ToggleButton("translate").apply { toggleGroup = typeGroup }

        fun editType(): Int = if (rotBtn.isSelected) TransformType.ROTATE else TransformType.TRANSLATE
        fun sliderMax(): Double = if (rotBtn.isSelected) 2047.0 else 512.0
        fun sliderMin(): Double = if (rotBtn.isSelected) 0.0 else -512.0
        val sx = Slider(sliderMin(), sliderMax(), 0.0)
        val sy = Slider(sliderMin(), sliderMax(), 0.0)
        val sz = Slider(sliderMin(), sliderMax(), 0.0)
        val xyzLabel = Label("dx 0  dy 0  dz 0")

        fun showLabel(label: Int?) {
            highlight.children.clear()
            if (label == null || label < 0 || label >= model.boneVertices.size) return
            for (vi in model.boneVertices[label]) {
                val ball = Sphere(2.4)
                ball.material = markMat
                ball.translateX = model.verticesX[vi].toDouble()
                ball.translateY = model.verticesY[vi].toDouble()
                ball.translateZ = model.verticesZ[vi].toDouble()
                highlight.children += ball
            }
        }

        fun rebuild() {
            meshGroup.children.setAll(buildMeshes(model, materials))
            showLabel(selectedLabel())
        }

        var syncingSliders = false
        var refreshTimeline: () -> Unit = {}
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

        fun applyPose() {
            animator.restore(bind)
            if (seqFrames.isNotEmpty()) {
                currentFrame = frameSlider.value.toInt().coerceIn(0, seqFrames.lastIndex)
                animator.apply(seqFrames[currentFrame])
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
            windowStatus(
                "model ${modelIds.joinToString("+")}   " +
                    statusText +
                    "   " + (lab?.let { "vskin $it $typeName" } ?: "no group") +
                    (baseId?.let { "   base $it" } ?: ""),
            )
            rebuild()
            refreshTimeline()
        }

        frameSlider.valueProperty().addListener { _, _, _ ->
            loadSlidersFromFrame()
            applyPose()
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
                loadSlidersFromFrame()
                applyPose()
            } catch (e: Exception) {
                extrasLabel.text = "import failed: ${e.message}"
                System.err.println("import extras $inId: ${e.message}")
            }
        }
        var playing = false
        var accNs = 0L
        val timer = object : AnimationTimer() {
            override fun handle(now: Long) {
                if (!playing || seqFrames.isEmpty()) return
                if (accNs == 0L) {
                    accNs = now
                    return
                }
                val delayTicks = seqDelays.getOrElse(currentFrame) { 5 }.coerceAtLeast(1)
                if (now - accNs >= delayTicks * 20_000_000L) {
                    accNs = now
                    val next = (currentFrame + 1) % seqFrames.size
                    frameSlider.value = next.toDouble()
                }
            }
        }
        fun togglePlay() {
            if (seqFrames.isEmpty()) return
            playing = !playing
            timelinePlaying(playing)
            accNs = 0L
            if (playing) timer.start() else timer.stop()
        }
        fun seekFrame(i: Int) {
            if (seqFrames.isEmpty()) return
            frameSlider.value = i.coerceIn(0, seqFrames.lastIndex).toDouble()
        }

        val side = VBox(
            6.0,
            Label("${model.vertexCount} verts  ${model.faceCount} faces"),
            pickedLabelUi,
            Label("extras seq id"),
            extrasIdField,
            exportBtn,
            importBtn,
            extrasLabel,
            Label("edit selected label"),
            rotBtn,
            moveBtn,
            Label("x"),
            sx,
            Label("y"),
            sy,
            Label("z"),
            sz,
            xyzLabel,
        )
        side.padding = Insets(8.0)
        side.prefWidth = 236.0
        side.minWidth = 236.0
        val sideScroll = ScrollPane(side).apply {
            isFitToWidth = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
            prefWidth = 248.0
            minWidth = 248.0
            style = "-fx-background-color: #f4f4f4;"
        }

        val sub = SubScene(root, 960.0, 720.0, true, SceneAntialiasing.BALANCED)
        sub.fill = Color.rgb(32, 32, 36)
        sub.camera = camera

        var lastX = 0.0
        var lastY = 0.0
        var dragDist = 0.0
        sub.setOnMousePressed { e ->
            lastX = e.sceneX
            lastY = e.sceneY
            dragDist = 0.0
        }
        sub.setOnMouseDragged { e ->
            if (e.button == MouseButton.PRIMARY || e.button == MouseButton.SECONDARY) {
                val dx = e.sceneX - lastX
                val dy = e.sceneY - lastY
                dragDist += kotlin.math.abs(dx) + kotlin.math.abs(dy)
                yaw.angle += dx * 0.4
                pitch.angle += dy * 0.4
                lastX = e.sceneX
                lastY = e.sceneY
            }
        }
        sub.setOnMouseReleased { e ->
            if (e.button != MouseButton.PRIMARY || dragDist > 5.0) return@setOnMouseReleased
            val pick = e.pickResult ?: return@setOnMouseReleased
            val node = pick.intersectedNode as? MeshView ?: return@setOnMouseReleased
            val faceMap = node.userData as? IntArray ?: return@setOnMouseReleased
            val local = pick.intersectedFace
            if (local < 0 || local >= faceMap.size) return@setOnMouseReleased
            val lab = vskinOfFace(model, faceMap[local]) ?: return@setOnMouseReleased
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
            onPlayToggle = { togglePlay() },
            onFirst = { seekFrame(0) },
            onPrev = { seekFrame(currentFrame - 1) },
            onNext = { seekFrame(currentFrame + 1) },
            onLast = { seekFrame(seqFrames.lastIndex) },
        )
        refreshTimeline = { timeline.refresh() }
        timelineStatus = { timeline.status.text = it }
        timelinePlaying = { timeline.setPlaying(it) }
        timelineEnabled = { timeline.setEnabled(it) }
        timeline.setEnabled(seqFrames.isNotEmpty())
        timeline.setPlaying(false)

        timeline.root.minHeight = 200.0
        timeline.root.prefHeight = 220.0
        timeline.root.maxHeight = 260.0

        val exportItem = MenuItem("Export extras…")
        exportItem.setOnAction { exportBtn.fire() }
        val importItem = MenuItem("Import extras…")
        importItem.setOnAction { importBtn.fire() }
        val exitItem = MenuItem("Exit")
        exitItem.setOnAction { Platform.exit() }
        val fileMenu = Menu("File", null, exportItem, importItem, SeparatorMenuItem(), exitItem)

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
        val helpMenu = Menu("Help", null, aboutItem)
        val menuBar = MenuBar(fileMenu, playMenu, helpMenu)

        val statusText = Label("ready").apply { textFill = Color.rgb(40, 40, 44) }
        windowStatus = { statusText.text = it }
        val statusBar = HBox(statusText).apply {
            padding = Insets(3.0, 8.0, 3.0, 8.0)
            style = "-fx-background-color: #e8e8ea; -fx-border-color: #c8c8cc; -fx-border-width: 1 0 0 0;"
        }
        HBox.setHgrow(statusText, Priority.ALWAYS)

        val work = BorderPane()
        work.center = stack
        work.left = sideScroll
        work.bottom = timeline.root
        val pane = BorderPane()
        pane.top = menuBar
        pane.center = work
        pane.bottom = statusBar
        sub.widthProperty().bind(work.widthProperty().subtract(sideScroll.widthProperty()))
        sub.heightProperty().bind(work.heightProperty().subtract(220))

        val title = buildString {
            append("rs530-anim-tool  model ${modelIds.joinToString("+")}")
            if (seqId != null) append("  seq $seqId")
        }
        stage.title = title
        stage.scene = Scene(pane, 1180.0, 760.0)
        if (seqFrames.isNotEmpty()) loadSlidersFromFrame()
        applyPose()
        stage.show()
    }

    companion object {
        fun open(args: List<String>) {
            launch(ModelViewer::class.java, *args.toTypedArray())
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

private fun buildMeshes(model: Rs2Model, materials: TextureMaterials?): List<MeshView> {
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
        view.material = PhongMaterial(Hsl.toFx(hsl)).apply {
            specularColor = Color.BLACK
            specularPower = 1.0
            if (tex != 0xFFFF) {
                val img = rs530anim.tex.TextureLibrary.image(tex)
                if (img != null) {
                    diffuseMap = img
                    diffuseColor = Hsl.toFx(hsl)
                } else {
                    diffuseColor = Hsl.toFx(hsl)
                }
            }
        }
        view
    }
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

/** Wire rectangle on XZ at the feet, plus a notch on −Z (toward the default camera). */
private fun groundMarker(model: Rs2Model): MeshView {
    var minX = 0; var maxX = 0; var maxY = 0; var minZ = 0; var maxZ = 0
    if (model.vertexCount > 0) {
        minX = model.verticesX.minOrNull() ?: 0
        maxX = model.verticesX.maxOrNull() ?: 0
        maxY = model.verticesY.maxOrNull() ?: 0
        minZ = model.verticesZ.minOrNull() ?: 0
        maxZ = model.verticesZ.maxOrNull() ?: 0
    }
    val pad = 12f
    val y = maxY.toFloat()
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

private fun centerModel(model: Rs2Model) {
    if (model.vertexCount == 0) return
    var minX = Int.MAX_VALUE
    var minY = Int.MAX_VALUE
    var minZ = Int.MAX_VALUE
    var maxX = Int.MIN_VALUE
    var maxY = Int.MIN_VALUE
    var maxZ = Int.MIN_VALUE
    for (i in 0 until model.vertexCount) {
        minX = minOf(minX, model.verticesX[i]); maxX = maxOf(maxX, model.verticesX[i])
        minY = minOf(minY, model.verticesY[i]); maxY = maxOf(maxY, model.verticesY[i])
        minZ = minOf(minZ, model.verticesZ[i]); maxZ = maxOf(maxZ, model.verticesZ[i])
    }
    val cx = (minX + maxX) / 2
    val cy = (minY + maxY) / 2
    val cz = (minZ + maxZ) / 2
    for (i in 0 until model.vertexCount) {
        model.verticesX[i] -= cx
        model.verticesY[i] -= cy
        model.verticesZ[i] -= cz
    }
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
