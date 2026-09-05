package rs530anim.anim

import rs530anim.model.Rs2Model

data class BindPose(
    val x: IntArray,
    val y: IntArray,
    val z: IntArray,
    val colors: ShortArray,
    val alpha: ByteArray?,
)

/**
 * SoftwareModel.method4569 / method4577 + Model.method4553 tween.
 */
class ModelAnimator(private val model: Rs2Model) {
    var originX = 0
        private set
    var originY = 0
        private set
    var originZ = 0
        private set

    fun copyBindPose(): BindPose = BindPose(
        model.verticesX.copyOf(),
        model.verticesY.copyOf(),
        model.verticesZ.copyOf(),
        model.faceColors.copyOf(),
        model.faceAlpha?.copyOf(),
    )

    fun restore(pose: BindPose) {
        System.arraycopy(pose.x, 0, model.verticesX, 0, model.vertexCount)
        System.arraycopy(pose.y, 0, model.verticesY, 0, model.vertexCount)
        System.arraycopy(pose.z, 0, model.verticesZ, 0, model.vertexCount)
        System.arraycopy(pose.colors, 0, model.faceColors, 0, model.faceCount)
        val a = model.faceAlpha
        if (a != null && pose.alpha != null) {
            System.arraycopy(pose.alpha, 0, a, 0, minOf(a.size, pose.alpha.size))
        }
        originX = 0
        originY = 0
        originZ = 0
    }

    fun apply(frame: AnimFrame, isolateLabel: Int? = null) {
        applyTweened(frame, null, 0, 1, isolateLabel)
    }

    fun applyTweened(
        frame: AnimFrame,
        next: AnimFrame?,
        step: Int,
        duration: Int,
        isolateLabel: Int? = null,
    ) {
        val base = frame.base
        if (next == null || duration <= 1 || step <= 0 || next.base.id != base.id) {
            applyOne(frame, isolateLabel)
            return
        }
        var i1 = 0
        var i2 = 0
        while (i1 < frame.length || i2 < next.length) {
            val slot1 = if (i1 < frame.length) frame.indices[i1].toInt() else Int.MAX_VALUE
            val slot2 = if (i2 < next.length) next.indices[i2].toInt() else Int.MAX_VALUE
            val slot = minOf(slot1, slot2)
            if (slot !in base.types.indices) break
            val type = base.types[slot]
            val def = if (type == TransformType.SCALE) 128 else 0
            var ax = def
            var ay = def
            var az = def
            var prev = -1
            var flagsA = 0
            if (slot1 == slot) {
                ax = frame.x[i1].toInt()
                ay = frame.y[i1].toInt()
                az = frame.z[i1].toInt()
                prev = frame.prevOriginIndices[i1].toInt()
                flagsA = frame.flags[i1].toInt()
                i1++
            }
            var bx = def
            var by = def
            var bz = def
            var prevB = -1
            var flagsB = 0
            if (slot2 == slot) {
                bx = next.x[i2].toInt()
                by = next.y[i2].toInt()
                bz = next.z[i2].toInt()
                prevB = next.prevOriginIndices[i2].toInt()
                flagsB = next.flags[i2].toInt()
                i2++
            }
            val mx: Int
            val my: Int
            val mz: Int
            if (flagsA and 0x2 != 0 || flagsB and 0x1 != 0) {
                mx = ax; my = ay; mz = az
            } else if (type == TransformType.ROTATE) {
                fun wrap(d: Int): Int {
                    var v = d and 0x7FF
                    if (v >= 1024) v -= 2048
                    return v
                }
                mx = ax + wrap(bx - ax) * step / duration and 0x7FF
                my = ay + wrap(by - ay) * step / duration and 0x7FF
                mz = az + wrap(bz - az) * step / duration and 0x7FF
            } else if (type == TransformType.COLOR) {
                var dh = bx - ax and 0x3F
                if (dh >= 32) dh -= 64
                mx = ax + dh * step / duration and 0x3F
                my = ay + (by - ay) * step / duration
                mz = az + (bz - az) * step / duration
            } else {
                mx = ax + (bx - ax) * step / duration
                my = ay + (by - ay) * step / duration
                mz = az + (bz - az) * step / duration
            }
            val originSlot = if (prev != -1) prev else prevB
            if (originSlot in base.bones.indices) {
                method4569(TransformType.ORIGIN, base.bones[originSlot], 0, 0, 0, base.parts.getOrElse(originSlot) { 65535 }, isolateLabel)
            }
            val labels = base.bones[slot]
            method4569(type, labels, mx, my, mz, base.parts.getOrElse(slot) { 65535 }, isolateLabel)
        }
    }

    private fun applyOne(frame: AnimFrame, isolateLabel: Int?) {
        val base = frame.base
        for (i in 0 until frame.length) {
            val slot = frame.indices[i].toInt()
            if (slot !in base.types.indices) continue
            val prev = frame.prevOriginIndices[i].toInt()
            if (prev in base.bones.indices) {
                method4569(TransformType.ORIGIN, base.bones[prev], 0, 0, 0, base.parts.getOrElse(prev) { 65535 }, isolateLabel)
            }
            method4569(
                base.types[slot],
                base.bones[slot],
                frame.x[i].toInt(),
                frame.y[i].toInt(),
                frame.z[i].toInt(),
                base.parts.getOrElse(slot) { 65535 },
                isolateLabel,
            )
        }
    }

    fun method4569(
        type: Int,
        labels: IntArray,
        dx: Int,
        dy: Int,
        dz: Int,
        parts: Int = 65535,
        isolateLabel: Int? = null,
    ) {
        val targets = if (isolateLabel != null && type != TransformType.ORIGIN && isolateLabel in labels) {
            intArrayOf(isolateLabel)
        } else {
            labels
        }
        when (type) {
            TransformType.ORIGIN -> {
                var count = 0
                var sx = 0
                var sy = 0
                var sz = 0
                forEachVert(targets, parts) { v ->
                    sx += model.verticesX[v]
                    sy += model.verticesY[v]
                    sz += model.verticesZ[v]
                    count++
                }
                if (count > 0) {
                    originX = sx / count + dx
                    originY = sy / count + dy
                    originZ = sz / count + dz
                } else {
                    originX = dx
                    originY = dy
                    originZ = dz
                }
            }
            TransformType.TRANSLATE -> {
                forEachVert(targets, parts) { v ->
                    model.verticesX[v] += dx
                    model.verticesY[v] += dy
                    model.verticesZ[v] += dz
                }
            }
            TransformType.ROTATE -> {
                forEachVert(targets, parts) { v ->
                    model.verticesX[v] -= originX
                    model.verticesY[v] -= originY
                    model.verticesZ[v] -= originZ
                    if (dz != 0) {
                        val s = Rs530Math.sin[dz and 2047]
                        val c = Rs530Math.cos[dz and 2047]
                        val nx = model.verticesY[v] * s + model.verticesX[v] * c + 32767 shr 16
                        model.verticesY[v] = model.verticesY[v] * c + 32767 - model.verticesX[v] * s shr 16
                        model.verticesX[v] = nx
                    }
                    if (dx != 0) {
                        val s = Rs530Math.sin[dx and 2047]
                        val c = Rs530Math.cos[dx and 2047]
                        val ny = model.verticesY[v] * c + 32767 - model.verticesZ[v] * s shr 16
                        model.verticesZ[v] = model.verticesY[v] * s + model.verticesZ[v] * c + 32767 shr 16
                        model.verticesY[v] = ny
                    }
                    if (dy != 0) {
                        val s = Rs530Math.sin[dy and 2047]
                        val c = Rs530Math.cos[dy and 2047]
                        val nx = model.verticesZ[v] * s + model.verticesX[v] * c + 32767 shr 16
                        model.verticesZ[v] = model.verticesZ[v] * c + 32767 - model.verticesX[v] * s shr 16
                        model.verticesX[v] = nx
                    }
                    model.verticesX[v] += originX
                    model.verticesY[v] += originY
                    model.verticesZ[v] += originZ
                }
            }
            TransformType.SCALE -> {
                forEachVert(targets, parts) { v ->
                    model.verticesX[v] -= originX
                    model.verticesY[v] -= originY
                    model.verticesZ[v] -= originZ
                    model.verticesX[v] = model.verticesX[v] * dx / 128
                    model.verticesY[v] = model.verticesY[v] * dy / 128
                    model.verticesZ[v] = model.verticesZ[v] * dz / 128
                    model.verticesX[v] += originX
                    model.verticesY[v] += originY
                    model.verticesZ[v] += originZ
                }
            }
            TransformType.ALPHA -> {
                val alpha = model.faceAlpha ?: return
                for (label in targets) {
                    if (label < 0 || label >= model.boneTriangles.size) continue
                    for (face in model.boneTriangles[label]) {
                        if (face !in alpha.indices) continue
                        val next = (alpha[face].toInt() and 0xFF) + dx * 8
                        alpha[face] = next.coerceIn(0, 255).toByte()
                    }
                }
            }
            TransformType.COLOR -> {
                for (label in targets) {
                    if (label < 0 || label >= model.boneTriangles.size) continue
                    for (face in model.boneTriangles[label]) {
                        if (face !in model.faceColors.indices) continue
                        val hsl = model.faceColors[face].toInt() and 0xFFFF
                        var h = hsl shr 10 and 0x3F
                        var s = hsl shr 7 and 0x7
                        var l = hsl and 0x7F
                        h = h + dx and 0x3F
                        s = (s + dy).coerceIn(0, 7)
                        l = (l + dz).coerceIn(0, 127)
                        model.faceColors[face] = (h shl 10 or (s shl 7) or l).toShort()
                    }
                }
            }
        }
    }

    private inline fun forEachVert(labels: IntArray, parts: Int, fn: (Int) -> Unit) {
        for (label in labels) {
            val verts = vertsOf(label) ?: continue
            for (v in verts) {
                fn(v)
            }
        }
        // parts != 65535 is method4577; without vertexSources the client still visits every vert.
        if (parts != 65535) {
            // kept for the call shape; sources are not on this mesh format yet
        }
    }

    private fun vertsOf(label: Int): IntArray? {
        if (label < 0 || label >= model.boneVertices.size) return null
        return model.boneVertices[label]
    }
}
