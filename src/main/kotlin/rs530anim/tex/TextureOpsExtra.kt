package rs530anim.tex

import rs530anim.model.Rs2Buffer

class OpCombine : TextureOp(2, false) {
    private var function = 6
    override fun decode(code: Int, b: Rs2Buffer) {
        when (code) {
            0 -> function = b.g1()
            1 -> monochrome = b.g1() == 1
        }
    }

    override fun monoOut(y: Int): IntArray {
        val (row, miss) = mono!!.row(y)
        if (miss) {
            val a = childMono(0, y)
            val c = childMono(1, y)
            combine(row, a, c)
        }
        return row
    }

    override fun colorOut(y: Int): Array<IntArray> {
        val (row, miss) = color!!.row(y)
        if (miss) {
            val a = childColor(0, y)
            val c = childColor(1, y)
            combine(row[0], a[0], c[0])
            combine(row[1], a[1], c[1])
            combine(row[2], a[2], c[2])
        }
        return row
    }

    private fun combine(dst: IntArray, a: IntArray, b: IntArray) {
        val w = TextureContext.width
        when (function) {
            1 -> for (x in 0 until w) dst[x] = a[x] + b[x]
            2 -> for (x in 0 until w) dst[x] = a[x] - b[x]
            3 -> for (x in 0 until w) dst[x] = b[x] * a[x] shr 12
            4 -> for (x in 0 until w) dst[x] = if (b[x] == 0) 4096 else (a[x] shl 12) / b[x]
            else -> for (x in 0 until w) dst[x] = b[x] * a[x] shr 12
        }
    }
}

class OpCurve : TextureOp(1, true) {
    private var mode = 0
    private var markers = arrayOf(intArrayOf(0, 0), intArrayOf(4096, 4096))
    private val lut = ShortArray(257)

    override fun decode(code: Int, b: Rs2Buffer) {
        if (code != 0) return
        mode = b.g1()
        markers = Array(b.g1()) { intArrayOf(b.g2(), b.g2()) }
    }

    override fun postDecode() {
        for (i in 0 until 257) {
            val t = i shl 4
            var k = 1
            while (k < markers.size - 1 && markers[k][0] <= t) k++
            val prev = markers[(k - 1).coerceAtLeast(0)]
            val next = markers[k.coerceAtMost(markers.lastIndex)]
            val den = (next[0] - prev[0]).coerceAtLeast(1)
            val u = ((t - prev[0]) shl 12) / den
            val v = prev[1] + ((next[1] - prev[1]) * u shr 12)
            lut[i] = v.coerceIn(-32767, 32767).toShort()
        }
    }

    override fun monoOut(y: Int): IntArray {
        val (row, miss) = mono!!.row(y)
        if (miss) {
            val src = childMono(0, y)
            for (x in src.indices) {
                val i = (src[x] shr 4).coerceIn(0, 256)
                row[x] = lut[i].toInt()
            }
        }
        return row
    }
}

class OpFractal : TextureOp(0, true) {
    private var normalize = true
    private var octaves = 4
    private var seedParam = 1638
    private var scaleX = 4
    private var scaleY = 4
    private var permSeed = 0
    private var amps: ShortArray? = null
    private var freqs: ShortArray? = null
    private var perm = ByteArray(512)

    override fun decode(code: Int, b: Rs2Buffer) {
        when (code) {
            0 -> normalize = b.g1() == 1
            1 -> octaves = b.g1()
            2 -> {
                seedParam = b.g2b()
                if (seedParam < 0) {
                    amps = ShortArray(octaves) { b.g2b().toShort() }
                }
            }
            3 -> {
                val v = b.g1(); scaleX = v; scaleY = v
            }
            4 -> permSeed = b.g1()
            5 -> scaleX = b.g1()
            6 -> scaleY = b.g1()
        }
    }

    override fun postDecode() {
        perm = TextureContext.perm(permSeed)
        if (seedParam > 0) {
            amps = ShortArray(octaves) { Math.pow(seedParam / 4096.0, it.toDouble()).times(4096.0).toInt().toShort() }
            freqs = ShortArray(octaves) { Math.pow(2.0, it.toDouble()).toInt().toShort() }
        } else if (amps != null && amps!!.size == octaves) {
            freqs = ShortArray(octaves) { Math.pow(2.0, it.toDouble()).toInt().toShort() }
        }
        var n = octaves
        while (n > 1) {
            val a = amps?.getOrNull(n - 1)?.toInt() ?: 0
            if (a > 8 || a < -8) break
            n--
        }
        octaves = n
    }

    override fun monoOut(y: Int): IntArray {
        val (row, miss) = mono!!.row(y)
        if (miss) fill(y, row)
        return row
    }

    private fun fill(rowY: Int, dest: IntArray) {
        dest.fill(0)
        val am = amps ?: return
        val fr = freqs ?: return
        val yf = scaleY * TextureContext.heightFractions[rowY]
        for (o in 0 until octaves) {
            val amp = am[o].toInt()
            if (amp in -8..8) continue
            val freq = fr[o].toInt() shl 12
            val yy = yf * freq shr 12
            val y0 = yy shr 12
            val y1 = if (y0 + 1 >= (scaleY * freq shr 12)) 0 else y0 + 1
            val p0 = perm[y0 and 0xFF].toInt() and 0xFF
            val p1 = perm[y1 and 0xFF].toInt() and 0xFF
            val fy = yy and 0xFFF
            val fadeY = TextureContext.fade[fy]
            val ySpan = scaleX * freq shr 12
            for (x in dest.indices) {
                val xx = TextureContext.widthFractions[x] * scaleX * freq shr 12
                val n = sample(xx, p1, p0, ySpan, fy, fadeY)
                dest[x] += amp * n shr 12
            }
        }
        if (normalize) {
            for (x in dest.indices) dest[x] = (dest[x] shr 1) + 2048
        }
    }

    private fun sample(x: Int, p1: Int, p0: Int, xSpan: Int, fy: Int, fadeY: Int): Int {
        val x0 = x shr 12
        var x1 = x0 + 1
        if (x1 >= xSpan) x1 = 0
        val fx = x and 0xFFF
        val fadeX = TextureContext.fade[fx]
        fun grad(hash: Int, dx: Int, dy: Int): Int {
            val h = perm[hash] .toInt() and 0x3
            return when {
                h > 1 -> if (h == 2) dx - dy else -dx - dy
                else -> if (h == 0) dy + dx else dy - dx
            }
        }
        val a = grad((x0 and 0xFF) + p0, fx, fy)
        val b = grad((x1 and 0xFF) + p0, fx - 4096, fy)
        val c = grad((x0 and 0xFF) + p1, fx, fy - 4096)
        val d = grad((x1 and 0xFF) + p1, fx - 4096, fy - 4096)
        val u = a + ((b - a) * fadeX shr 12)
        val v = c + ((d - c) * fadeX shr 12)
        return u + (fadeY * (v - u) shr 12)
    }
}
