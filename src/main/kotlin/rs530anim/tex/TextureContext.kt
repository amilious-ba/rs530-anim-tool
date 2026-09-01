package rs530anim.tex

/** Static size tables from rt4.Texture.setSize / setBrightness. */
object TextureContext {
    var width = 0
        private set
    var height = 0
        private set
    var widthMask = 0
        private set
    var heightMask = 0
        private set
    var widthFractions = IntArray(0)
        private set
    var heightFractions = IntArray(0)
        private set
    val brightnessMap = IntArray(256)
    val fade = IntArray(4096)
    val sine = IntArray(256)
    val cosine = IntArray(256)

    init {
        for (i in 0 until 256) {
            val r = i / 255.0 * 6.283185307179586
            sine[i] = (Math.sin(r) * 4096.0).toInt()
            cosine[i] = (Math.cos(r) * 4096.0).toInt()
        }
        for (i in 0 until 4096) {
            val a = i * (i * i shr 12) shr 12
            val b = i * 6 - 61440
            val c = (i * b shr 12) + 40960
            fade[i] = a * c shr 12
        }
        setBrightness(0.8)
    }

    fun setSize(h: Int, w: Int) {
        if (width != w) {
            widthFractions = IntArray(w) { (it shl 12) / w }
            widthMask = w - 1
            width = w
        }
        if (height != h) {
            heightFractions = IntArray(h) { (it shl 12) / h }
            heightMask = h - 1
            height = h
        }
    }

    fun setBrightness(value: Double) {
        for (i in 0 until 256) {
            val v = (Math.pow(i / 255.0, value) * 255.0).toInt()
            brightnessMap[i] = v.coerceAtMost(255)
        }
    }

    fun ensureTrig() {}

    fun perm(seed: Int): ByteArray {
        val out = ByteArray(512)
        val rng = java.util.Random(seed.toLong())
        for (i in 0 until 255) out[i] = i.toByte()
        for (i in 0 until 255) {
            val span = 255 - i
            val pick = if (span <= 0) 0 else rng.nextInt(span)
            val v = out[pick]
            out[pick] = out[span]
            out[span] = v
            out[511 - i] = v
        }
        return out
    }
}

class MonoCache(private val height: Int, width: Int) {
    private val rows = Array(height) { IntArray(width) }
    private val ready = BooleanArray(height)
    fun row(y: Int): Pair<IntArray, Boolean> {
        val miss = !ready[y]
        ready[y] = true
        return rows[y] to miss
    }
    fun allRows(): Pair<Array<IntArray>, Boolean> {
        val miss = ready.any { !it }
        ready.fill(true)
        return rows to miss
    }
}

class ColorCache(private val height: Int, width: Int) {
    private val rows = Array(height) { Array(3) { IntArray(width) } }
    private val ready = BooleanArray(height)
    fun row(y: Int): Pair<Array<IntArray>, Boolean> {
        val miss = !ready[y]
        ready[y] = true
        return rows[y] to miss
    }
}
