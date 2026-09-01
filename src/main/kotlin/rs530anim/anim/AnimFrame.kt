package rs530anim.anim

import rs530anim.model.Rs2Buffer

/**
 * Port of rt4.AnimFrame.<init>(byte[], AnimBase).
 *
 * On disk, type-2 (rotate) values are packed. After decode, x/y/z are the
 * values SoftwareModel.method4569 receives (0..2047 for rotate).
 */
class AnimFrame(
    val base: AnimBase,
    val indices: ShortArray,
    val x: ShortArray,
    val y: ShortArray,
    val z: ShortArray,
    val prevOriginIndices: ShortArray,
    val flags: ByteArray,
    val transformsAlpha: Boolean,
    val transformsColor: Boolean,
) {
    val length: Int get() = indices.size

    fun valueAt(index: Int): Triple<Int, Int, Int> =
        Triple(x[index].toInt(), y[index].toInt(), z[index].toInt())

    /** First used group whose base slot includes this vskin and type. */
    fun indexForLabel(label: Int, type: Int): Int? {
        for (i in indices.indices) {
            val slot = indices[i].toInt()
            if (base.types[slot] == type && label in base.bones[slot]) return i
        }
        return null
    }

    fun valuesForLabel(label: Int, type: Int): Triple<Int, Int, Int>? {
        val i = indexForLabel(label, type) ?: return null
        return valueAt(i)
    }

    fun edits(): List<GroupEdit> = indices.indices.map { i ->
        GroupEdit(
            slot = indices[i].toInt(),
            x = x[i].toInt(),
            y = y[i].toInt(),
            z = z[i].toInt(),
            flags = flags[i].toInt() and 0x3,
        )
    }

    fun withLabelValues(label: Int, type: Int, dx: Int, dy: Int, dz: Int): AnimFrame {
        val slot = base.slotFor(label, type)
            ?: return this
        val next = edits().toMutableList()
        val existing = next.indexOfFirst { it.slot == slot }
        val edit = GroupEdit(slot, dx, dy, dz, if (existing >= 0) next[existing].flags else 0)
        if (existing >= 0) next[existing] = edit else next += edit
        return fromEdits(base, next)
    }

    data class GroupEdit(
        val slot: Int,
        val x: Int = 0,
        val y: Int = 0,
        val z: Int = 0,
        val flags: Int = 0,
    )

    companion object {
        fun decode(bytes: ByteArray, base: AnimBase): AnimFrame {
            val headerBuffer = Rs2Buffer(bytes)
            val buffer = Rs2Buffer(bytes)
            headerBuffer.offset = 2
            val headerLen = headerBuffer.g1()
            require(headerLen <= base.transforms) {
                "frame groupCount $headerLen > base.transforms ${base.transforms}"
            }
            buffer.offset = headerBuffer.offset + headerLen

            val tempIndices = ShortArray(500)
            val tempX = ShortArray(500)
            val tempY = ShortArray(500)
            val tempZ = ShortArray(500)
            val tempFlags = ByteArray(500)
            val tempPrev = ShortArray(500)

            var len = 0
            var prevOriginIndex = -1
            var prevUsedOriginIndex = -1
            var transformsAlpha = false
            var transformsColor = false

            for (i in 0 until headerLen) {
                val type = base.types[i]
                if (type == TransformType.ORIGIN) {
                    prevOriginIndex = i
                }
                val attributes = headerBuffer.g1()
                if (attributes > 0) {
                    if (type == TransformType.ORIGIN) {
                        prevUsedOriginIndex = i
                    }
                    tempIndices[len] = i.toShort()
                    val defaultValue: Short = if (type == TransformType.SCALE) 128 else 0
                    tempX[len] = if (attributes and 0x1 == 0) defaultValue else buffer.gsmart().toShort()
                    tempY[len] = if (attributes and 0x2 == 0) defaultValue else buffer.gsmart().toShort()
                    tempZ[len] = if (attributes and 0x4 == 0) defaultValue else buffer.gsmart().toShort()
                    tempFlags[len] = ((attributes ushr 3) and 0x3).toByte()
                    if (type == TransformType.ROTATE) {
                        tempX[len] = unpackRotate(tempX[len])
                        tempY[len] = unpackRotate(tempY[len])
                        tempZ[len] = unpackRotate(tempZ[len])
                    }
                    tempPrev[len] = -1
                    when (type) {
                        TransformType.TRANSLATE, TransformType.ROTATE, TransformType.SCALE -> {
                            if (prevOriginIndex > prevUsedOriginIndex) {
                                tempPrev[len] = prevOriginIndex.toShort()
                                prevUsedOriginIndex = prevOriginIndex
                            }
                        }
                        TransformType.ALPHA -> transformsAlpha = true
                        TransformType.COLOR -> transformsColor = true
                    }
                    len++
                }
            }
            require(buffer.offset == bytes.size) {
                "frame payload ended at ${buffer.offset}, file is ${bytes.size}"
            }

            return AnimFrame(
                base = base,
                indices = tempIndices.copyOf(len),
                x = tempX.copyOf(len),
                y = tempY.copyOf(len),
                z = tempZ.copyOf(len),
                prevOriginIndices = tempPrev.copyOf(len),
                flags = tempFlags.copyOf(len),
                transformsAlpha = transformsAlpha,
                transformsColor = transformsColor,
            )
        }

        /**
         * Build a frame from editor values (already in method4569 space).
         * Encodes then decodes so prevOriginIndices match the client walk.
         */
        fun fromEdits(base: AnimBase, edits: List<GroupEdit>): AnimFrame {
            val bySlot = edits.associateBy { it.slot }
            val used = mutableListOf<GroupEdit>()
            for (slot in 0 until base.transforms) {
                val edit = bySlot[slot] ?: continue
                val type = base.types[slot]
                val def = if (type == TransformType.SCALE) 128 else 0
                if (edit.x != def || edit.y != def || edit.z != def || edit.flags != 0) {
                    used += edit
                }
            }
            val n = used.size
            val draft = AnimFrame(
                base = base,
                indices = ShortArray(n) { used[it].slot.toShort() },
                x = ShortArray(n) { used[it].x.toShort() },
                y = ShortArray(n) { used[it].y.toShort() },
                z = ShortArray(n) { used[it].z.toShort() },
                prevOriginIndices = ShortArray(n) { -1 },
                flags = ByteArray(n) { (used[it].flags and 0x3).toByte() },
                transformsAlpha = used.any { base.types[it.slot] == TransformType.ALPHA },
                transformsColor = used.any { base.types[it.slot] == TransformType.COLOR },
            )
            return decode(draft.encode(), base)
        }

        /** Client post-read for type 2. */
        fun unpackRotate(stored: Short): Short {
            val v = stored.toInt()
            return (((v and 0xFF) shl 3) + (v shr 8 and 0x7)).toShort()
        }

        /** Inverse of unpackRotate for values that came from method4569-space. */
        fun packRotate(applied: Int): Int {
            val a = applied and 0x7FF
            return ((a and 0x7) shl 8) or ((a shr 3) and 0xFF)
        }
    }

    fun encode(): ByteArray {
        val groupCount = base.transforms
        val payload = Rs2Buffer(8 + groupCount * 8)
        val attributes = IntArray(groupCount)
        val used = HashMap<Int, Int>()
        for (i in indices.indices) {
            used[indices[i].toInt()] = i
        }
        for (slot in 0 until groupCount) {
            val type = base.types[slot]
            val def = if (type == TransformType.SCALE) 128 else 0
            val idx = used[slot]
            if (idx == null) {
                attributes[slot] = 0
                continue
            }
            var ax = x[idx].toInt()
            var ay = y[idx].toInt()
            var az = z[idx].toInt()
            if (type == TransformType.ROTATE) {
                ax = packRotate(ax)
                ay = packRotate(ay)
                az = packRotate(az)
            }
            var attr = (flags[idx].toInt() and 0x3) shl 3
            if (ax != def) {
                attr = attr or 0x1
                payload.psmart(ax)
            }
            if (ay != def) {
                attr = attr or 0x2
                payload.psmart(ay)
            }
            if (az != def) {
                attr = attr or 0x4
                payload.psmart(az)
            }
            if (attr == 0 && (flags[idx].toInt() and 0x3) != 0) {
                attr = (flags[idx].toInt() and 0x3) shl 3
            }
            attributes[slot] = attr
        }

        val out = Rs2Buffer(2 + 1 + groupCount + payload.offset)
        out.p2(base.id)
        out.p1(groupCount)
        for (a in attributes) out.p1(a)
        val raw = payload.writtenBytes()
        for (b in raw) out.p1(b.toInt() and 0xFF)
        return out.writtenBytes()
    }
}
