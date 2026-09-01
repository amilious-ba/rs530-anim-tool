package rs530anim.anim

import rs530anim.cache.Js5Store

object AnimLibrary {
    fun loadSeq(store: Js5Store, id: Int): SeqType = SeqType.decode(id, store.seqBytes(id))

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
