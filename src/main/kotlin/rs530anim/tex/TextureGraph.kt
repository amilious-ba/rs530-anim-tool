package rs530anim.tex

import javafx.scene.image.WritableImage
import javafx.scene.paint.Color
import rs530anim.model.Rs2Buffer

class TextureGraph(val ops: Array<TextureOp>, val root: TextureOp) {
    fun raster(size: Int = 128): WritableImage {
        TextureContext.setSize(size, size)
        TextureContext.setBrightness(0.8)
        for (op in ops) op.prepare(size, size)
        val img = WritableImage(size, size)
        val pw = img.pixelWriter
        val map = TextureContext.brightnessMap
        for (y in 0 until size) {
            val r: IntArray
            val g: IntArray
            val b: IntArray
            if (root.monochrome) {
                val m = root.monoOut(y)
                r = m; g = m; b = m
            } else {
                val c = root.colorOut(y)
                r = c[0]; g = c[1]; b = c[2]
            }
            for (x in 0 until size) {
                fun ch(v: Int): Int = map[(v shr 4).coerceIn(0, 255)]
                pw.setColor(x, y, Color.rgb(ch(r[x]), ch(g[x]), ch(b[x])))
            }
        }
        return img
    }

    companion object {
        fun decode(bytes: ByteArray): TextureGraph {
            val b = Rs2Buffer(bytes)
            val n = b.g1()
            val ops = Array(n) { TextureOp.create(0) }
            val links = Array(n) { IntArray(0) }
            for (i in 0 until n) {
                b.g1()
                val type = b.g1()
                val op = TextureOp.create(type)
                op.cacheHeight = b.g1()
                val codes = b.g1()
                repeat(codes) { op.decode(b.g1(), b) }
                op.postDecode()
                links[i] = IntArray(op.inputs) { b.g1() }
                ops[i] = op
            }
            for (i in 0 until n) {
                for (s in links[i].indices) {
                    ops[i].children[s] = ops[links[i][s]]
                }
            }
            val root = ops[b.g1()]
            if (b.offset < bytes.size) b.g1()
            return TextureGraph(ops, root)
        }
    }
}
