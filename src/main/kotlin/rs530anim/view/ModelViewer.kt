package rs530anim.view

import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.layout.StackPane
import javafx.scene.shape.Cylinder
import javafx.scene.AmbientLight
import javafx.scene.Group
import javafx.scene.PerspectiveCamera
import javafx.scene.Scene
import javafx.scene.SceneAntialiasing
import javafx.scene.SubScene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.Slider
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import rs530anim.anim.AnimFrame
import rs530anim.anim.SeqType
import rs530anim.anim.TransformType
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
        var seqFrames: List<AnimFrame> = emptyList()
        var seqDelays = IntArray(0)
        if (seqId != null) {
            try {
                Js5Store(settings).use { store ->
                    val seq = AnimLibrary.loadSeq(store, seqId)
                    seqFrames = seq.frames.map { AnimLibrary.frameOf(store, it) }
                    seqDelays = seq.delays
                    println("seq $seqId frames=${seq.length}")
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
        world.children += AmbientLight(Color.WHITE)
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
        val list = ListView<String>()
        list.items.add("none")
        for (lab in labels) {
            list.items.add("vskin $lab  (${model.vertexCountForLabel(lab)} verts)")
        }
        list.selectionModel.select(0)
        fun selectedLabel(): Int? {
            val i = list.selectionModel.selectedIndex
            return if (i <= 0) null else labels.getOrNull(i - 1)
        }

        val frameSlider = Slider(0.0, (seqFrames.size - 1).coerceAtLeast(0).toDouble(), currentFrame.toDouble())
        frameSlider.isSnapToTicks = true
        frameSlider.majorTickUnit = 1.0
        frameSlider.blockIncrement = 1.0
        val frameLabel = Label(if (seqFrames.isEmpty()) "no seq" else "frame $currentFrame / ${seqFrames.size}")

        val typeGroup = ToggleGroup()
        val rotBtn = ToggleButton("rotate").apply { toggleGroup = typeGroup; isSelected = true }
        val moveBtn = ToggleButton("translate").apply { toggleGroup = typeGroup }

        fun sliderMax(): Double = if (rotBtn.isSelected) 2047.0 else 128.0
        fun sliderMin(): Double = if (rotBtn.isSelected) 0.0 else -128.0
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

        fun applyPose() {
            animator.restore(bind)
            if (seqFrames.isNotEmpty()) {
                currentFrame = frameSlider.value.toInt().coerceIn(0, seqFrames.lastIndex)
                animator.apply(seqFrames[currentFrame])
            }
            val lab = selectedLabel()
            val dx = sx.value.toInt()
            val dy = sy.value.toInt()
            val dz = sz.value.toInt()
            xyzLabel.text = "dx $dx  dy $dy  dz $dz"
            if (lab != null && (dx != 0 || dy != 0 || dz != 0)) {
                val kind = if (rotBtn.isSelected) TransformType.ROTATE else TransformType.TRANSLATE
                if (kind == TransformType.ROTATE) {
                    animator.method4569(TransformType.ORIGIN, intArrayOf(lab), 0, 0, 0)
                }
                animator.method4569(kind, intArrayOf(lab), dx, dy, dz)
            }
            frameLabel.text = if (seqFrames.isEmpty()) {
                "no seq"
            } else {
                val d = seqDelays.getOrElse(currentFrame) { 5 }
                "frame $currentFrame / ${seqFrames.size}  ${d} ticks (${d * 20} ms)"
            }
            rebuild()
        }

        list.selectionModel.selectedIndexProperty().addListener { _, _, _ -> applyPose() }
        frameSlider.valueProperty().addListener { _, _, _ -> applyPose() }
        sx.valueProperty().addListener { _, _, _ -> applyPose() }
        sy.valueProperty().addListener { _, _, _ -> applyPose() }
        sz.valueProperty().addListener { _, _, _ -> applyPose() }
        typeGroup.selectedToggleProperty().addListener { _, _, _ ->
            sx.min = sliderMin(); sx.max = sliderMax(); sx.value = 0.0
            sy.min = sliderMin(); sy.max = sliderMax(); sy.value = 0.0
            sz.min = sliderMin(); sz.max = sliderMax(); sz.value = 0.0
        }

        val playBtn = Button(if (seqFrames.isEmpty()) "play (no seq)" else "play")
        playBtn.isDisable = seqFrames.isEmpty()
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
                // 530 client cycle is 20ms; frameDelay is in those cycles.
                if (now - accNs >= delayTicks * 20_000_000L) {
                    accNs = now
                    val next = (currentFrame + 1) % seqFrames.size
                    frameSlider.value = next.toDouble()
                }
            }
        }
        playBtn.setOnAction {
            playing = !playing
            playBtn.text = if (playing) "stop" else "play"
            accNs = 0L
            if (playing) timer.start() else timer.stop()
        }

        val side = VBox(
            6.0,
            Label("vskin labels"),
            list,
            Label("${model.vertexCount} verts  ${model.faceCount} faces"),
            frameLabel,
            frameSlider,
            playBtn,
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
        side.prefWidth = 220.0
        list.prefHeight = 240.0

        val sub = SubScene(root, 960.0, 720.0, true, SceneAntialiasing.BALANCED)
        sub.fill = Color.rgb(32, 32, 36)
        sub.camera = camera

        var lastX = 0.0
        var lastY = 0.0
        sub.setOnMousePressed { e ->
            lastX = e.sceneX
            lastY = e.sceneY
        }
        sub.setOnMouseDragged { e ->
            if (e.button == MouseButton.PRIMARY || e.button == MouseButton.SECONDARY) {
                yaw.angle += (e.sceneX - lastX) * 0.4
                pitch.angle += (e.sceneY - lastY) * 0.4
                lastX = e.sceneX
                lastY = e.sceneY
            }
        }
        sub.addEventHandler(ScrollEvent.SCROLL) { e ->
            distance = (distance - e.deltaY * 0.4).coerceIn(20.0, 8000.0)
            camera.translateZ = -distance
        }

        val compass = axisCompass(yaw, pitch)
        val stack = StackPane(sub, compass)
        StackPane.setAlignment(compass, Pos.TOP_RIGHT)
        StackPane.setMargin(compass, Insets(8.0))

        val pane = BorderPane()
        pane.center = stack
        pane.left = side
        sub.widthProperty().bind(pane.widthProperty().subtract(side.prefWidth))
        sub.heightProperty().bind(pane.heightProperty())

        val title = buildString {
            append("rs530-anim-tool  model ${modelIds.joinToString("+")}")
            if (seqId != null) append("  seq $seqId frame $frameNo")
            append("  click a label")
        }
        stage.title = title
        stage.scene = Scene(pane, 1180.0, 720.0)
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

private fun buildMeshes(model: Rs2Model, materials: TextureMaterials?): List<MeshView> {
    data class Tri(val a: Int, val b: Int, val c: Int, val color: Int, val tex: Int)
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
        tris += Tri(model.faceA[i], model.faceB[i], model.faceC[i], shadeFace(model, i, materials), tex)
    }
    return tris.groupBy { it.tex }.map { (tex, group) ->
        val mesh = TriangleMesh()
        val points = FloatArray(group.size * 9)
        val uvs = FloatArray(group.size * 6)
        val faces = IntArray(group.size * 6)
        var p = 0
        var t = 0
        var f = 0
        var vi = 0
        var ti = 0
        fun put(index: Int, u: Float, v: Float) {
            points[p++] = model.verticesX[index].toFloat()
            points[p++] = model.verticesY[index].toFloat()
            points[p++] = model.verticesZ[index].toFloat()
            uvs[t++] = u
            uvs[t++] = v
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
            put(tri.a, ua.first, ua.second)
            put(tri.b, ub.first, ub.second)
            put(tri.c, uc.first, uc.second)
            faces[f++] = vi++; faces[f++] = ti++
            faces[f++] = vi++; faces[f++] = ti++
            faces[f++] = vi++; faces[f++] = ti++
        }
        mesh.points.addAll(*points)
        mesh.texCoords.addAll(*uvs)
        mesh.faces.addAll(*faces)
        val view = MeshView(mesh)
        view.cullFace = CullFace.NONE
        view.drawMode = DrawMode.FILL
        val hsl = group.first().color
        val base = if (tex != 0xFFFF) materials?.solidHsl(tex) ?: (hsl) else hsl
        view.material = PhongMaterial(Hsl.toFx(hsl)).apply {
            specularColor = Color.BLACK
            specularPower = 1.0
            if (tex != 0xFFFF) {
                val img = rs530anim.tex.TextureLibrary.image(tex)
                if (img != null) {
                    diffuseMap = img
                    diffuseColor = Hsl.toFx(base)
                } else {
                    diffuseColor = Hsl.toFx(base)
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

private fun faceHsl(model: Rs2Model, face: Int, materials: TextureMaterials?): Int {
    val tex = model.faceTextures?.getOrNull(face)?.toInt()?.and(0xFFFF) ?: 0xFFFF
    if (tex != 0xFFFF && materials != null) {
        materials.solidHsl(tex)?.let { return it }
    }
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
