package rs530anim.anim

import rs530anim.cache.Js5Store

object AnimLibrary {
    fun loadSeq(store: Js5Store, id: Int): SeqType = SeqType.decode(id, store.seqBytes(id))

    /** Every sequence whose first frame uses this AnimBase (same skeleton as the model). */
    fun seqsUsingBase(store: Js5Store, baseId: Int): List<Int> {
        val idx = store.index(rs530anim.cache.Js5Archives.SEQUENCES)
        val framesetBase = HashMap<Int, Int>()
        val hits = ArrayList<Int>()
        for (group in idx.groupIds) {
            val files = try {
                store.groupFiles(rs530anim.cache.Js5Archives.SEQUENCES, group)
            } catch (_: Exception) {
                continue
            }
            for ((fileId, bytes) in files) {
                val seqId = (group shl 7) or fileId
                val seq = try {
                    SeqType.decode(seqId, bytes)
                } catch (_: Exception) {
                    continue
                }
                if (seq.frames.isEmpty()) continue
                val setId = seq.framesetId(0)
                val found = framesetBase.getOrPut(setId) {
                    try {
                        val packed = store.framesetFiles(setId).values.first()
                        ((packed[0].toInt() and 0xFF) shl 8) or (packed[1].toInt() and 0xFF)
                    } catch (_: Exception) {
                        -1
                    }
                }
                if (found == baseId) hits += seqId
            }
        }
        hits.sort()
        return hits
    }

    fun loadFrameset(store: Js5Store, id: Int): AnimFrameset {
        val files = store.framesetFiles(id)
        require(files.isNotEmpty()) { "frameset $id is empty" }
        val bases = HashMap<Int, AnimBase>()
        val frames = HashMap<Int, AnimFrame>()
        for ((fileId, bytes) in files) {
            val baseId = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
            val base = bases.getOrPut(baseId) { AnimBase.decode(baseId, store.baseBytes(baseId)) }
            frames[fileId] = AnimFrame.decode(bytes, base)
        }
        return AnimFrameset(id, frames)
    }

    fun frameOf(store: Js5Store, packed: Int): AnimFrame {
        val set = loadFrameset(store, packed ushr 16)
        val index = packed and 0xFFFF
        return set.frames[index] ?: error("frameset ${packed ushr 16} has no file $index")
    }
}
