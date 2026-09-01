package rs530anim.anim

/** One JS5 group in archive 0. frames[fileId] as in rt4.AnimFrameset. */
class AnimFrameset(
    val id: Int,
    val frames: Map<Int, AnimFrame>,
) {
    val baseId: Int get() = frames.values.first().base.id
}

object Rs530Math {
    val sin = IntArray(2048)
    val cos = IntArray(2048)

    init {
        for (i in 0 until 2048) {
            sin[i] = (kotlin.math.sin(i * 0.0030679615) * 65536.0).toInt()
            cos[i] = (kotlin.math.cos(i * 0.0030679615) * 65536.0).toInt()
        }
    }
}
