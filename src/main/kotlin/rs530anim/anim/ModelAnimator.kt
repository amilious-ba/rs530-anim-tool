package rs530anim.anim

import rs530anim.model.Rs2Model

/**
 * SoftwareModel.method4569 + Model.method4553 (no tween, parts == 65535).
 * Origin / translate / rotate / scale only. Alpha and colour skipped this slice.
 */
class ModelAnimator(private val model: Rs2Model) {
    var originX = 0
        private set
    var originY = 0
        private set
    var originZ = 0
        private set

    fun copyBindPose(): Triple<IntArray, IntArray, IntArray> = Triple(
        model.verticesX.copyOf(),
        model.verticesY.copyOf(),
        model.verticesZ.copyOf(),
    )

    fun restore(pose: Triple<IntArray, IntArray, IntArray>) {
        System.arraycopy(pose.first, 0, model.verticesX, 0, model.vertexCount)
        System.arraycopy(pose.second, 0, model.verticesY, 0, model.vertexCount)
        System.arraycopy(pose.third, 0, model.verticesZ, 0, model.vertexCount)
        originX = 0
        originY = 0
        originZ = 0
    }

    fun apply(frame: AnimFrame) {
        val base = frame.base
        for (i in 0 until frame.length) {
            val slot = frame.indices[i].toInt()
            val prev = frame.prevOriginIndices[i].toInt()
            if (prev != -1) {
                method4569(TransformType.ORIGIN, base.bones[prev], 0, 0, 0)
            }
            method4569(
                base.types[slot],
                base.bones[slot],
                frame.x[i].toInt(),
                frame.y[i].toInt(),
                frame.z[i].toInt(),
            )
        }
    }

    fun method4569(type: Int, labels: IntArray, dx: Int, dy: Int, dz: Int) {
        when (type) {
            TransformType.ORIGIN -> {
                var count = 0
                var sx = 0
                var sy = 0
                var sz = 0
                for (label in labels) {
                    val verts = vertsOf(label) ?: continue
                    for (v in verts) {
                        sx += model.verticesX[v]
                        sy += model.verticesY[v]
                        sz += model.verticesZ[v]
                        count++
                    }
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
                for (label in labels) {
                    val verts = vertsOf(label) ?: continue
                    for (v in verts) {
                        model.verticesX[v] += dx
                        model.verticesY[v] += dy
                        model.verticesZ[v] += dz
                    }
                }
            }
            TransformType.ROTATE -> {
                for (label in labels) {
                    val verts = vertsOf(label) ?: continue
                    for (v in verts) {
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
            }
            TransformType.SCALE -> {
                for (label in labels) {
                    val verts = vertsOf(label) ?: continue
                    for (v in verts) {
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
            }
        }
    }

    private fun vertsOf(label: Int): IntArray? {
        if (label < 0 || label >= model.boneVertices.size) return null
        return model.boneVertices[label]
    }
}
