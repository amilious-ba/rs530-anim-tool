package rs530anim.anim

import rs530anim.model.Rs2Buffer

/**
 * Port of rt4.AnimBase (vendor/rt4/AnimBase.java).
 * bones[slot] = vskin / tskin label ids that method4569 walks.
 */
class AnimBase(
    val id: Int,
    val types: IntArray,
    val shadow: BooleanArray,
    val parts: IntArray,
    val bones: Array<IntArray>,
) {
    val transforms: Int get() = types.size

    fun slotFor(label: Int, type: Int): Int? {
        var best: Int? = null
        var bestSize = Int.MAX_VALUE
        for (slot in types.indices) {
            if (types[slot] != type) continue
            if (label !in bones[slot]) continue
            val size = bones[slot].size
            if (size < bestSize) {
                best = slot
                bestSize = size
            }
        }
        return best
    }

    fun encode(): ByteArray {
        val labels = bones.sumOf { it.size }
        val buf = Rs2Buffer(1 + transforms * (1 + 1 + 2 + 1) + labels)
        buf.p1(transforms)
        for (t in types) buf.p1(t)
        for (s in shadow) buf.p1(if (s) 1 else 0)
        for (p in parts) buf.p2(p)
        for (b in bones) buf.p1(b.size)
        for (b in bones) {
            for (label in b) buf.p1(label)
        }
        return buf.writtenBytes()
    }

    companion object {
        fun decode(id: Int, bytes: ByteArray): AnimBase {
            val buffer = Rs2Buffer(bytes)
            val transforms = buffer.g1()
            val types = IntArray(transforms) { buffer.g1() }
            val shadow = BooleanArray(transforms) { buffer.g1() == 1 }
            val parts = IntArray(transforms) { buffer.g2() }
            val bones = Array(transforms) { IntArray(buffer.g1()) }
            for (i in 0 until transforms) {
                for (j in bones[i].indices) {
                    bones[i][j] = buffer.g1()
                }
            }
            return AnimBase(id, types, shadow, parts, bones)
        }
    }
}
