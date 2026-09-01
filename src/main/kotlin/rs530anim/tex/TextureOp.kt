package rs530anim.tex

import rs530anim.model.Rs2Buffer

abstract class TextureOp(val inputs: Int, var monochrome: Boolean) {
    val children = arrayOfNulls<TextureOp>(inputs)
    var cacheHeight = 255
    protected var mono: MonoCache? = null
    protected var color: ColorCache? = null

    open fun decode(code: Int, b: Rs2Buffer) {}
    open fun postDecode() {}
    open fun spriteId(): Int = -1
    open fun nestedTextureId(): Int = -1

    fun prepare(h: Int, w: Int) {
        val rows = if (cacheHeight == 255) h else cacheHeight
        if (monochrome) mono = MonoCache(h, w) else color = ColorCache(h, w)
    }

    open fun monoOut(y: Int): IntArray {
        error("${this::class.simpleName} has no mono output")
    }

    open fun colorOut(y: Int): Array<IntArray> {
        error("${this::class.simpleName} has no colour output")
    }

    fun childMono(slot: Int, y: Int): IntArray {
        val c = children[slot] ?: error("missing child $slot")
        return if (c.monochrome) c.monoOut(y) else c.colorOut(y)[0]
    }

    fun childColor(slot: Int, y: Int): Array<IntArray> {
        val c = children[slot] ?: error("missing child $slot")
        return if (c.monochrome) {
            val m = c.monoOut(y)
            arrayOf(m, m, m)
        } else {
            c.colorOut(y)
        }
    }

    companion object {
        fun create(type: Int): TextureOp = when (type) {
            0 -> OpMonoFill()
            1 -> OpColorFill()
            2 -> OpHGrad()
            3 -> OpVGrad()
            5 -> OpBlur()
            6 -> OpClamp()
            7 -> OpCombine()
            8 -> OpCurve()
            9 -> OpFlip()
            10 -> OpColorGradient()
            13 -> OpNoiseFlag()
            15 -> OpVoronoi()
            16 -> OpBrick()
            19 -> OpDisplace()
            22 -> OpInvert()
            27 -> OpStripes()
            30 -> OpRange()
            32 -> OpEmboss()
            34 -> OpFractal()
            36 -> OpNestedTexture()
            38 -> OpScribble()
            else -> OpUnsupported(type)
        }
    }
}

class OpUnsupported(val type: Int) : TextureOp(0, true) {
    override fun decode(code: Int, b: Rs2Buffer) = error("texture op $type not ported (code $code)")
}

class OpMonoFill : TextureOp(0, true) {
    private var value = 4096
    override fun decode(code: Int, b: Rs2Buffer) {
        if (code == 0) value = (b.g1() shl 12) / 255
    }
    override fun monoOut(y: Int): IntArray {
        val (row, miss) = mono!!.row(y)
        if (miss) row.fill(value)
        return row
    }
}

class OpColorFill : TextureOp(0, false) {
    private var r = 0
    private var g = 0
    private var bl = 0
    private fun set(rgb: Int) {
        g = rgb shr 4 and 0xFF0
        bl = (rgb and 0xFF) shl 4
        r = rgb shr 12 and 0xFF0
    }
    override fun decode(code: Int, b: Rs2Buffer) {
        if (code == 0) set(b.g3())
    }
    override fun colorOut(y: Int): Array<IntArray> {
        val (row, miss) = color!!.row(y)
        if (miss) {
            row[0].fill(r); row[1].fill(g); row[2].fill(bl)
        }
        return row
    }
}

class OpClamp : TextureOp(1, false) {
    private var lo = 0
    private var hi = 4096
    override fun decode(code: Int, b: Rs2Buffer) {
        when (code) {
            0 -> lo = b.g2()
            1 -> hi = b.g2()
            2 -> monochrome = b.g1() == 1
        }
    }
    override fun monoOut(y: Int): IntArray {
        val (row, miss) = mono!!.row(y)
        if (miss) {
            val src = childMono(0, y)
            for (x in src.indices) row[x] = src[x].coerceIn(lo, hi)
        }
        return row
    }
}

class OpFlip : TextureOp(1, false) {
    private var flipX = false
    private var flipY = false
    override fun decode(code: Int, b: Rs2Buffer) {
        when (code) {
            0 -> flipX = b.g1() == 1
            1 -> flipY = b.g1() == 1
            2 -> monochrome = b.g1() == 1
        }
    }
    override fun monoOut(y: Int): IntArray {
        val yy = if (flipY) TextureContext.heightMask - y else y
        val src = childMono(0, yy)
        val (row, miss) = mono!!.row(y)
        if (miss) {
            if (!flipX) src.copyInto(row) else {
                var i = 0
                var j = src.lastIndex
                while (i < src.size) row[i++] = src[j--]
            }
        }
        return row
    }
}

class OpInvert : TextureOp(1, false) {
    override fun decode(code: Int, b: Rs2Buffer) {
        if (code == 0) monochrome = b.g1() == 1
    }
    override fun monoOut(y: Int): IntArray {
        val (row, miss) = mono!!.row(y)
        if (miss) {
            val src = childMono(0, y)
            for (x in src.indices) row[x] = 4096 - src[x]
        }
        return row
    }
}

class OpNoiseFlag : TextureOp(0, true) {
    override fun decode(code: Int, b: Rs2Buffer) {
        if (code == 0) monochrome = b.g1() == 1
    }
    override fun monoOut(y: Int): IntArray {
        val (row, miss) = mono!!.row(y)
        if (miss) {
            for (x in row.indices) {
                val n = TextureContext.widthFractions[x] + TextureContext.heightFractions[y]
                row[x] = n and 4095
            }
        }
        return row
    }
}
