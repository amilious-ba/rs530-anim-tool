package rs530anim.view

import javafx.scene.Group
import javafx.scene.Node
import javafx.scene.paint.Color
import javafx.scene.paint.PhongMaterial
import javafx.scene.shape.Cylinder
import javafx.scene.shape.MeshView
import javafx.scene.shape.TriangleMesh
import javafx.scene.transform.Rotate
import rs530anim.anim.TransformType
import kotlin.math.cos
import kotlin.math.sin

class TransformGizmo {
    val root = Group()
    var axis: Int? = null
        private set
    private var builtMode: Int? = null
    private var builtScale = 0.0

    fun rebuild(mode: Int, cx: Double, cy: Double, cz: Double, scale: Double) {
        root.translateX = cx
        root.translateY = cy
        root.translateZ = cz
        val s = scale.coerceIn(16.0, 70.0)
        if (builtMode == mode && kotlin.math.abs(builtScale - s) < 1.0 && root.children.isNotEmpty()) return
        builtMode = mode
        builtScale = s
        root.children.clear()
        if (mode == TransformType.ROTATE) {
            root.children += ring(0, Color.web("#e23b2e"), s)
            root.children += ring(1, Color.web("#5ebd3e"), s)
            root.children += ring(2, Color.web("#2f7de1"), s)
        } else {
            root.children += arrow(0, Color.web("#e23b2e"), s)
            root.children += arrow(1, Color.web("#5ebd3e"), s)
            root.children += arrow(2, Color.web("#2f7de1"), s)
        }
        val hub = Cylinder(2.0, 2.0)
        hub.material = PhongMaterial(Color.web("#d8d8de"))
        hub.isMouseTransparent = true
        root.children += hub
    }

    fun hitAxis(node: Node?): Int? {
        var n: Node? = node
        while (n != null) {
            val data = n.userData as? String
            if (data != null && data.startsWith("gizmo:")) {
                return data.removePrefix("gizmo:").toIntOrNull()
            }
            n = n.parent
        }
        return null
    }

    fun begin(node: Node?): Boolean {
        axis = hitAxis(node)
        return axis != null
    }

    fun end() {
        axis = null
    }

    private fun mark(node: Node, axis: Int): Node {
        node.userData = "gizmo:$axis"
        if (node is Group) node.children.forEach { mark(it, axis) }
        return node
    }

    private fun arrow(axis: Int, color: Color, len: Double): Group {
        val mat = PhongMaterial(color)
        val shaftLen = len * 0.72
        val shaft = Cylinder(1.35, shaftLen)
        shaft.material = mat
        shaft.translateY = -shaftLen / 2.0
        val tip = MeshView(coneMesh(3.4, len * 0.28)).apply {
            material = mat
            translateY = -shaftLen - (len * 0.14)
        }
        val g = Group(shaft, tip)
        when (axis) {
            0 -> g.transforms += Rotate(90.0, Rotate.Z_AXIS)
            2 -> g.transforms += Rotate(-90.0, Rotate.X_AXIS)
        }
        return mark(g, axis) as Group
    }

    private fun ring(axis: Int, color: Color, radius: Double): Group {
        val mat = PhongMaterial(color)
        val bits = Group()
        val segs = 40
        val tube = 1.15
        val step = 2.0 * Math.PI / segs
        val segLen = 2.0 * Math.PI * radius / segs
        for (i in 0 until segs) {
            val a = i * step + step / 2.0
            val cyl = Cylinder(tube, segLen * 1.08)
            cyl.material = mat
            cyl.translateX = cos(a) * radius
            cyl.translateY = sin(a) * radius
            cyl.transforms += Rotate(Math.toDegrees(a) + 90.0, Rotate.Z_AXIS)
            bits.children += cyl
        }
        when (axis) {
            0 -> bits.transforms += Rotate(90.0, Rotate.Y_AXIS)
            1 -> bits.transforms += Rotate(90.0, Rotate.X_AXIS)
        }
        return mark(bits, axis) as Group
    }

    private fun coneMesh(radius: Double, height: Double): TriangleMesh {
        val n = 12
        val mesh = TriangleMesh()
        val pts = FloatArray((n + 2) * 3)
        pts[0] = 0f
        pts[1] = (-height / 2.0).toFloat()
        pts[2] = 0f
        pts[3] = 0f
        pts[4] = (height / 2.0).toFloat()
        pts[5] = 0f
        for (i in 0 until n) {
            val a = i * (2.0 * Math.PI / n)
            val o = (i + 2) * 3
            pts[o] = (cos(a) * radius).toFloat()
            pts[o + 1] = (height / 2.0).toFloat()
            pts[o + 2] = (sin(a) * radius).toFloat()
        }
        val faces = IntArray(n * 2 * 6)
        var f = 0
        for (i in 0 until n) {
            val a = i + 2
            val b = if (i == n - 1) 2 else i + 3
            faces[f++] = 0; faces[f++] = 0; faces[f++] = a; faces[f++] = 0; faces[f++] = b; faces[f++] = 0
            faces[f++] = 1; faces[f++] = 0; faces[f++] = b; faces[f++] = 0; faces[f++] = a; faces[f++] = 0
        }
        mesh.points.addAll(*pts)
        mesh.texCoords.addAll(0f, 0f)
        mesh.faces.addAll(*faces)
        return mesh
    }
}
