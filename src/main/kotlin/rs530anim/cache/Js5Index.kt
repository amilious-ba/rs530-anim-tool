package rs530anim.cache

import rs530anim.model.Rs2Buffer

/** Port of rt4.Js5Index.method2293 (protocol 5/6). */
class Js5Index(
    val groupIds: IntArray,
    val groupSizes: IntArray,
    val groupCapacities: IntArray,
    val fileIds: Array<IntArray?>,
) {
    val capacity: Int get() = groupSizes.size

    fun sizeOf(group: Int): Int = if (group in groupSizes.indices) groupSizes[group] else 0

    fun filesOf(group: Int): IntArray {
        val packed = if (group in fileIds.indices) fileIds[group] else null
        if (packed != null) return packed
        val n = sizeOf(group)
        return IntArray(n) { it }
    }

    companion object {
        fun decode(uncompressed: ByteArray): Js5Index {
            val b = Rs2Buffer(uncompressed)
            val protocol = b.g1()
            require(protocol == 5 || protocol == 6) { "js5 index protocol $protocol" }
            if (protocol >= 6) b.g4()
            val named = b.g1()
            val size = b.g2()
            val groupIds = IntArray(size)
            var acc = 0
            var maxId = -1
            for (i in 0 until size) {
                acc += b.g2()
                groupIds[i] = acc
                if (acc > maxId) maxId = acc
            }
            val cap = maxId + 1
            val groupSizes = IntArray(cap)
            val groupCapacities = IntArray(cap)
            val fileIds = arrayOfNulls<IntArray>(cap)
            if (named != 0) {
                repeat(size) { b.g4() }
            }
            repeat(size) { b.g4() } // checksums
            repeat(size) { b.g4() } // versions
            for (i in 0 until size) {
                groupSizes[groupIds[i]] = b.g2()
            }
            for (i in 0 until size) {
                acc = 0
                val g = groupIds[i]
                val n = groupSizes[g]
                var maxFile = -1
                val ids = IntArray(n)
                for (j in 0 until n) {
                    acc += b.g2()
                    ids[j] = acc
                    if (acc > maxFile) maxFile = acc
                }
                groupCapacities[g] = maxFile + 1
                fileIds[g] = if (maxFile + 1 == n) null else ids
            }
            return Js5Index(groupIds, groupSizes, groupCapacities, fileIds)
        }
    }
}
