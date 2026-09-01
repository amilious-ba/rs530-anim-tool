package rs530anim.view

import javafx.application.Application
import javafx.scene.AmbientLight
import javafx.scene.Group
import javafx.scene.PerspectiveCamera
import javafx.scene.Scene
import javafx.scene.SceneAntialiasing
import javafx.scene.input.MouseButton
import javafx.scene.input.ScrollEvent
import javafx.scene.paint.Color
import javafx.scene.paint.PhongMaterial
import javafx.scene.shape.CullFace
import javafx.scene.shape.DrawMode
import javafx.scene.shape.MeshView
import javafx.scene.shape.TriangleMesh
import javafx.scene.transform.Rotate
import javafx.stage.Stage
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
        val modelId = raw.getOrNull(0)?.toIntOrNull() ?: 132
        val seqId = raw.getOrNull(1)?.toIntOrNull()
        val frameNo = raw.getOrNull(2)?.toIntOrNull() ?: 0

        val settings = CacheSettings.load(null)
        var materials: TextureMaterials? = null
        val model = Js5Store(settings).use { store ->
            materials = try {
                TextureMaterials.load(store).also { mat ->
                    println("texture 112 solid hsl=${mat.solidHsl(112)}  359=${mat.solidHsl(359)}")
                }
            } catch (e: Exception) {
                System.err.println("texture table failed: ${e.message}")
                e.printStackTrace()
                null
            }
            val loaded = Rs2ModelLoader.decode(store.model(modelId))
            if (seqId != null) {
                val seq = AnimLibrary.loadSeq(store, seqId)
                if (seq.length > 0) {
                    val packed = seq.frames[frameNo.coerceIn(0, seq.length - 1)]
                    ModelAnimator(loaded).apply(AnimLibrary.frameOf(store, packed))
                }
            }
            loaded
        }
        centerModel(model)

        val world = Group()
        world.children.addAll(buildMeshes(model, materials))
        // Lighting is already baked into face colours (SoftwareModel). Extra
        // JavaFX lights wash that out into a flat mask.
        world.children += AmbientLight(Color.WHITE)

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

        val scene = Scene(root, 960.0, 720.0, true, SceneAntialiasing.BALANCED)
        scene.fill = Color.rgb(32, 32, 36)
        scene.camera = camera

        var lastX = 0.0
        var lastY = 0.0
        scene.setOnMousePressed { e ->
            lastX = e.sceneX
            lastY = e.sceneY
        }
        scene.setOnMouseDragged { e ->
            if (e.button == MouseButton.PRIMARY || e.button == MouseButton.SECONDARY) {
                yaw.angle += (e.sceneX - lastX) * 0.4
                pitch.angle += (e.sceneY - lastY) * 0.4
                lastX = e.sceneX
                lastY = e.sceneY
            }
        }
        scene.addEventHandler(ScrollEvent.SCROLL) { e ->
            distance = (distance - e.deltaY * 0.4).coerceIn(20.0, 8000.0)
            camera.translateZ = -distance
        }

        val title = buildString {
            append("rs530-anim-tool  model $modelId")
            if (seqId != null) append("  seq $seqId frame $frameNo")
            append("  drag orbit  wheel zoom")
        }
        stage.title = title
        stage.scene = scene
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
    data class Tri(val a: Int, val b: Int, val c: Int, val color: Int)
    val tris = ArrayList<Tri>(model.faceCount)
    for (i in 0 until model.faceCount) {
        val shaded = shadeFace(model, i, materials)
        tris += Tri(model.faceA[i], model.faceB[i], model.faceC[i], shaded)
    }
    return tris.groupBy { it.color }.map { (hsl, group) ->
        val mesh = TriangleMesh()
        mesh.texCoords.addAll(0f, 0f)
        val points = FloatArray(group.size * 9)
        val faces = IntArray(group.size * 6)
        var p = 0
        var f = 0
        var vi = 0
        for (tri in group) {
            fun put(index: Int) {
                // RS Y is down; JavaFX Y is up.
                points[p++] = model.verticesX[index].toFloat()
                points[p++] = model.verticesY[index].toFloat()
                points[p++] = model.verticesZ[index].toFloat()
            }
            put(tri.a); put(tri.b); put(tri.c)
            faces[f++] = vi++; faces[f++] = 0
            faces[f++] = vi++; faces[f++] = 0
            faces[f++] = vi++; faces[f++] = 0
        }
        mesh.points.addAll(*points)
        mesh.faces.addAll(*faces)
        val view = MeshView(mesh)
        view.cullFace = CullFace.NONE
        view.drawMode = DrawMode.FILL
        val fx = Hsl.toFx(hsl)
        view.material = PhongMaterial(fx).apply {
            specularColor = Color.BLACK
            specularPower = 1.0
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
