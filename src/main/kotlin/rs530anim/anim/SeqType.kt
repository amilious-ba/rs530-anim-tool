package rs530anim.anim

import rs530anim.model.Rs2Buffer

/** Port of rt4.SeqType decode opcodes we need for playback + export. */
class SeqType(
    val id: Int,
    val frames: IntArray,
    val delays: IntArray,
    val looptype: Int,
    val priority: Int,
    val replayoff: Int,
) {
    val length: Int get() = frames.size

    fun framesetId(index: Int): Int = frames[index] ushr 16
    fun frameIndex(index: Int): Int = frames[index] and 0xFFFF

    companion object {
        fun decode(id: Int, bytes: ByteArray): SeqType {
            val b = Rs2Buffer(bytes)
            var frames = IntArray(0)
            var delays = IntArray(0)
            var looptype = -1
            var priority = 5
            var replayoff = -1
            while (true) {
                val op = b.g1()
                if (op == 0) break
                when (op) {
                    1 -> {
                        val n = b.g2()
                        delays = IntArray(n) { b.g2() }
                        frames = IntArray(n) { b.g2() }
                        for (i in 0 until n) frames[i] += b.g2() shl 16
                    }
                    2 -> replayoff = b.g2()
                    3 -> {
                        val n = b.g1()
                        repeat(n) { b.g1() }
                    }
                    4 -> { /* stretches */ }
                    5 -> priority = b.g1()
                    6, 7 -> b.g2()
                    8 -> b.g1()
                    9 -> looptype = b.g1()
                    10 -> b.g1()
                    11 -> b.g1()
                    12 -> {
                        val n = b.g1()
                        repeat(n) { b.g2() }
                        repeat(n) { b.g2() }
                    }
                    13 -> {
                        val n = b.g2()
                        repeat(n) {
                            val c = b.g1()
                            if (c > 0) {
                                b.g3()
                                repeat(c - 1) { b.g2() }
                            }
                        }
                    }
                    14, 15, 16 -> { }
                    else -> error("unknown seq opcode $op at ${b.offset}")
                }
            }
            if (looptype == -1) looptype = 0
            return SeqType(id, frames, delays, looptype, priority, replayoff)
        }
    }
}
