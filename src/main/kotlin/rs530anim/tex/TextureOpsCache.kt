package rs530anim.tex

import rs530anim.model.Rs2Buffer
import java.util.Random

class OpHGrad : TextureOp(0, true) {
    override fun monoOut(y: Int) = TextureContext.widthFractions
}

class OpVGrad : TextureOp(0, true) {
    override fun monoOut(y: Int): IntArray {
        val (row, miss) = mono!!.row(y)
        if (miss) row.fill(TextureContext.heightFractions[y])
        return row
    }
}

class OpRange : TextureOp(1, false) {
    private var add = 1024
    private var span = 2048
    private var hi = 3072
    override fun decode(code: Int, b: Rs2Buffer) {
        when (code) {
            0 -> add = b.g2()
            1 -> hi = b.g2()
            2 -> monochrome = b.g1() == 1
        }
    }
    override fun postDecode() { span = hi - add }
    override fun monoOut(y: Int): IntArray {
        val (row, miss) = mono!!.row(y)
        if (miss) {
            val src = childMono(0, y)
            for (x in src.indices) row[x] = add + (src[x] * span shr 12)
        }
        return row
    }
    override fun colorOut(y: Int): Array<IntArray> {
        val (row, miss) = color!!.row(y)
        if (miss) {
            val src = childColor(0, y)
            for (c in 0..2) for (x in src[c].indices) {
                row[c][x] = add + (src[c][x] * span shr 12)
            }
        }
        return row
    }
}

/** rt4.TextureOp15 — Voronoi / cell noise. */
class OpVoronoi : TextureOp(0, true) {
    private var function = 2
    private var range = 2048
    private var seed = 0
    private var metric = 1
    private var scaleY = 5
    private var scaleX = 5
    private var perm = ByteArray(512)
    private var offs = ShortArray(512)

    override fun decode(code: Int, b: Rs2Buffer) {
        when (code) {
            0 -> { val v = b.g1(); scaleX = v; scaleY = v }
            1 -> seed = b.g1()
            2 -> range = b.g2()
            3 -> function = b.g1()
            4 -> metric = b.g1()
            5 -> scaleX = b.g1()
            6 -> scaleY = b.g1()
        }
    }

    override fun postDecode() {
        perm = TextureContext.perm(seed)
        val rng = Random(seed.toLong())
        offs = ShortArray(512) {
            if (range > 0) rng.nextInt(range).toShort() else 0
        }
    }

    override fun monoOut(y: Int): IntArray {
        val (row, miss) = mono!!.row(y)
        if (!miss) return row
        val yv = scaleY * TextureContext.heightFractions[y] + 2048
        val y0 = yv shr 12
        val y1 = y0 + 1
        for (x in row.indices) {
            var d0 = Int.MAX_VALUE
            var d1 = Int.MAX_VALUE
            var d2 = Int.MAX_VALUE
            var d3 = Int.MAX_VALUE
            val xv = scaleX * TextureContext.widthFractions[x] + 2048
            val x0 = xv shr 12
            val x1 = x0 + 1
            for (yy in (y0 - 1)..y1) {
                val py = perm[(if (scaleY <= yy) yy - scaleY else yy) and 0xFF].toInt() and 0xFF
                for (xx in (x0 - 1)..x1) {
                    val i = (perm[(if (scaleX <= xx) xx - scaleX else xx) + py and 0xFF].toInt() and 0xFF) * 2
                    val dx = xv - offs[i] - (xx shl 12)
                    val dy = yv - offs[i + 1] - (yy shl 12)
                    val dist = when (metric) {
                        1 -> dx * dx + dy * dy shr 12
                        2 -> kotlin.math.abs(dx) + kotlin.math.abs(dy)
                        3 -> kotlin.math.max(kotlin.math.abs(dx), kotlin.math.abs(dy))
                        else -> (Math.sqrt((dx * dx + dy * dy) / 1.6777216E7) * 4096.0).toInt()
                    }
                    when {
                        dist < d0 -> { d3 = d2; d2 = d1; d1 = d0; d0 = dist }
                        dist < d1 -> { d3 = d2; d2 = d1; d1 = dist }
                        dist < d2 -> { d3 = d2; d2 = dist }
                        dist < d3 -> d3 = dist
                    }
                }
            }
            row[x] = when (function) {
                1 -> d1
                2 -> d1 - d0
                3 -> d2
                4 -> d3
                else -> d0
            }
        }
        return row
    }
}

/** rt4.TextureOp19 — polar displace sample of child 0. */
class OpDisplace : TextureOp(3, false) {
    private var amount = 32768
    override fun decode(code: Int, b: Rs2Buffer) {
        when (code) {
            0 -> amount = b.g2() shl 4
            1 -> monochrome = b.g1() == 1
        }
    }

    override fun monoOut(y: Int): IntArray {
        val (row, miss) = mono!!.row(y)
        if (miss) {
            val ang = childMono(1, y)
            val mag = childMono(2, y)
            val wmask = TextureContext.widthMask
            val hmask = TextureContext.heightMask
            for (x in row.indices) {
                val a = ang[x] shr 4 and 0xFF
                val m = amount * mag[x] shr 12
                val dx = TextureContext.cosine[a] * m shr 12
                val dy = TextureContext.sine[a] * m shr 12
                val sx = wmask and ((dx shr 12) + x)
                val sy = hmask and ((dy shr 12) + y)
                row[x] = childMono(0, sy)[sx]
            }
        }
        return row
    }

    override fun colorOut(y: Int): Array<IntArray> {
        val (row, miss) = color!!.row(y)
        if (miss) {
            val ang = childMono(1, y)
            val mag = childMono(2, y)
            val wmask = TextureContext.widthMask
            val hmask = TextureContext.heightMask
            for (x in row[0].indices) {
                val a = ang[x] * 255 shr 12 and 0xFF
                val m = mag[x] * amount shr 12
                val dx = TextureContext.cosine[a] * m shr 12
                val dy = TextureContext.sine[a] * m shr 12
                val sx = wmask and ((dx shr 12) + x)
                val sy = hmask and ((dy shr 12) + y)
                val src = childColor(0, sy)
                row[0][x] = src[0][sx]
                row[1][x] = src[1][sx]
                row[2][x] = src[2][sx]
            }
        }
        return row
    }
}

/** rt4.TextureOp27 — stripe / saw bands. */
class OpStripes : TextureOp(0, true) {
    private var count = 10
    private var width = 2048
    private var mode = 0
    private var starts = IntArray(0)
    private var ends = IntArray(0)

    override fun decode(code: Int, b: Rs2Buffer) {
        when (code) {
            0 -> count = b.g1().coerceAtLeast(1)
            1 -> width = b.g2()
            2 -> mode = b.g1()
        }
    }

    override fun postDecode() {
        val step = 4096 / count
        val fat = width * step shr 12
        starts = IntArray(count + 1)
        ends = IntArray(count + 1)
        var t = 0
        for (i in 0 until count) {
            starts[i] = t
            ends[i] = t + fat
            t += step
        }
        starts[count] = 4096
        ends[count] = ends[0] + 4096
    }

    override fun monoOut(y: Int): IntArray {
        val (row, miss) = mono!!.row(y)
        if (!miss) return row
        val yf = TextureContext.heightFractions[y]
        if (mode == 0) {
            var v = 0
            for (i in 0 until count) {
                if (starts[i] <= yf && yf < starts[i + 1]) {
                    if (yf < ends[i]) v = 4096
                    break
                }
            }
            row.fill(v)
        } else {
            for (x in row.indices) {
                val xf = TextureContext.widthFractions[x]
                val t = when (mode) {
                    1 -> xf
                    2 -> (xf + yf - 4096 shr 1) + 2048
                    3 -> (xf - yf shr 1) + 2048
                    else -> xf
                }
                var v = 0
                for (i in 0 until count) {
                    if (starts[i] <= t && t < starts[i + 1]) {
                        if (t < ends[i]) v = 4096
                        break
                    }
                }
                row[x] = v
            }
        }
        return row
    }
}

/** rt4.TextureOpTexture (type 36) — sample another archive-9 texture. */
class OpNestedTexture : TextureOp(0, false) {
    private var textureId = -1
    override fun decode(code: Int, b: Rs2Buffer) {
        if (code == 0) textureId = b.g2()
    }
    override fun nestedTextureId(): Int = textureId

    override fun colorOut(y: Int): Array<IntArray> {
        val (row, miss) = color!!.row(y)
        if (miss) {
            val pix = NestedTextures.pixels(textureId)
            val size = NestedTextures.size
            if (pix != null && size > 0) {
                val srcY = y * size / TextureContext.height
                val rowOff = srcY * size
                for (x in row[0].indices) {
                    val srcX = x * size / TextureContext.width
                    val rgb = pix[rowOff + srcX]
                    row[0][x] = rgb shr 12 and 0xFF0
                    row[1][x] = rgb shr 4 and 0xFF0
                    row[2][x] = (rgb and 0xFF) shl 4
                }
            }
        }
        return row
    }
}

object NestedTextures {
    var size = 128
        private set
    private val cache = HashMap<Int, IntArray>()
    private val loading = HashSet<Int>()

    fun pixels(id: Int): IntArray? {
        if (id < 0) return null
        cache[id]?.let { return it }
        if (!loading.add(id)) return null
        return try {
            val img = TextureLibrary.image(id) ?: return null
            val w = img.width.toInt().coerceAtLeast(1)
            size = w
            val out = IntArray(w * w)
            val r = img.pixelReader
            var i = 0
            for (y in 0 until w) for (x in 0 until w) {
                val c = r.getArgb(x, y)
                out[i++] = c and 0xFFFFFF
            }
            cache[id] = out
            out
        } finally {
            loading.remove(id)
        }
    }
}
