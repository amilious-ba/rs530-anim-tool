package rs530anim.cache

import rs530anim.model.Rs2Buffer

/** rt4.Js5.unpackGroup — split an uncompressed group into files. */
object Js5Group {
    fun unpack(uncompressed: ByteArray, groupSize: Int, fileIds: IntArray?): Map<Int, ByteArray> {
        if (groupSize <= 1) {
            val id = fileIds?.firstOrNull() ?: 0
            return mapOf(id to uncompressed)
        }
        val start = uncompressed.size
        val position = start - 1
        val stripes = uncompressed[position].toInt() and 0xFF
        val tableOff = position - groupSize * stripes * 4
        val buffer = Rs2Buffer(uncompressed)
        buffer.offset = tableOff
        val lens = IntArray(groupSize)
        for (s in 0 until stripes) {
            var len = 0
            for (j in 0 until groupSize) {
                len += buffer.g4()
                lens[j] += len
            }
        }
        val extracted = Array(groupSize) { ByteArray(lens[it]) }
        lens.fill(0)
        buffer.offset = tableOff
        var src = 0
        for (s in 0 until stripes) {
            var off = 0
            for (k in 0 until groupSize) {
                off += buffer.g4()
                System.arraycopy(uncompressed, src, extracted[k], lens[k], off)
                src += off
                lens[k] += off
            }
        }
        val out = LinkedHashMap<Int, ByteArray>()
        for (j in 0 until groupSize) {
            val id = fileIds?.getOrNull(j) ?: j
            out[id] = extracted[j]
        }
        return out
    }
}
