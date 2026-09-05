package rs530anim.view

import javafx.scene.Group
import javafx.scene.Node
import javafx.scene.paint.Color
import javafx.scene.paint.PhongMaterial
import javafx.scene.shape.Box
import javafx.scene.shape.Cylinder
import javafx.scene.transform.Rotate
import rs530anim.anim.TransformType

class TransformGizmo {
    val root = Group()
    var axis: Int? = null
        private set

    fun rebuild(mode: Int, cx: Double, cy: Double, cz: Double, scale: Double) {
        root.children.clear()
        root.translateX = cx
        root.translateY = cy
        root.translateZ = cz
        val s = scale.coerceIn(12.0, 80.0)
        if (mode == TransformType.TRANSLATE) {
            root.children += arrow(0, Color.web("#d45a4a"), s)
            root.children += arrow(1, Color.web("#6dbf5b"), s)
            root.children += arrow(2, Color.web("#4a8fd4"), s)
        } else {
            root.children += ring(0, Color.web("#d45a4a"), s)
            root.children += ring(1, Color.web("#6dbf5b"), s)
            root.children += ring(2, Color.web("#4a8fd4"), s)
        }
    }

    fun hitAxis(node: Node?): Int? {
        var n: Node? = node
        while (n != null) {
            val data = n.userData as? String ?: run {
                n = n.parent
                continue
            }
            if (data.startsWith("gizmo:")) return data.removePrefix("gizmo:").toIntOrNull()
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

    private fun arrow(axis: Int, color: Color, len: Double): Group {
        val mat = PhongMaterial(color)
        val shaft = Cylinder(1.4, len)
        val tip = javafx.scene.shape.Box(3.6, 6.0, 3.6)
        tip.material = mat
        shaft.material = mat
        tip.translateY = -len / 2.0 - 2.0
        shaft.userData = "gizmo:$axis"
        tip.userData = "gizmo:$axis"
        val g = Group(shaft, tip)
        g.userData = "gizmo:$axis"
        when (axis) {
            0 -> g.transforms += Rotate(90.0, Rotate.Z_AXIS)
            2 -> g.transforms += Rotate(90.0, Rotate.X_AXIS)
        }
        return g
    }

    private fun ring(axis: Int, color: Color, radius: Double): Group {
        val mat = PhongMaterial(color)
        val bits = Group()
        val n = 28
        for (i in 0 until n) {
            val box = Box(radius * 0.22, 1.3, 1.3)
            box.material = mat
            val a = i * (360.0 / n)
            box.transforms += Rotate(a, Rotate.Z_AXIS)
            box.translateX = kotlin.math.cos(Math.toRadians(a)) * radius
            box.translateY = kotlin.math.sin(Math.toRadians(a)) * radius
            box.userData = "gizmo:$axis"
            bits.children += box
        }
        bits.userData = "gizmo:$axis"
        when (axis) {
            0 -> bits.transforms += Rotate(90.0, Rotate.Y_AXIS)
            2 -> bits.transforms += Rotate(90.0, Rotate.X_AXIS)
        }
        return bits
    }
}
