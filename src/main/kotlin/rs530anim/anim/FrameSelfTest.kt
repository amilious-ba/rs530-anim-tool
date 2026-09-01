package rs530anim.anim

internal fun runFrameSelfTest() {
    val base = AnimBase(
        id = 132,
        types = intArrayOf(
            TransformType.ORIGIN,
            TransformType.TRANSLATE,
            TransformType.ROTATE,
            TransformType.SCALE,
        ),
        shadow = booleanArrayOf(true, true, true, false),
        parts = intArrayOf(65535, 65535, 65535, 65535),
        bones = arrayOf(
            intArrayOf(0),
            intArrayOf(1, 2),
            intArrayOf(3),
            intArrayOf(4),
        ),
    )

    val baseBytes = base.encode()
    val base2 = AnimBase.decode(132, baseBytes)
    check(base2.types.contentEquals(base.types)) { "base types" }
    check(base2.shadow.contentEquals(base.shadow)) { "base shadow" }
    check(base2.parts.contentEquals(base.parts)) { "base parts" }
    check(base2.bones.size == base.bones.size) { "base bone groups" }
    for (i in base.bones.indices) {
        check(base2.bones[i].contentEquals(base.bones[i])) { "base bones[$i]" }
    }
    println("base encode/decode ok  id=${base.id} transforms=${base.transforms}")

    check(AnimFrame.unpackRotate(AnimFrame.packRotate(0).toShort()).toInt() == 0)
    check(AnimFrame.unpackRotate(AnimFrame.packRotate(7).toShort()).toInt() == 7)
    check(AnimFrame.unpackRotate(AnimFrame.packRotate(200).toShort()).toInt() == 200)
    check(AnimFrame.unpackRotate(AnimFrame.packRotate(2047).toShort()).toInt() == 2047)
    println("rotate pack/unpack ok")

    val frame = AnimFrame.fromEdits(
        base,
        listOf(
            AnimFrame.GroupEdit(slot = 0, x = 4, y = 0, z = -2),
            AnimFrame.GroupEdit(slot = 1, x = 10, y = -20, z = 30),
            AnimFrame.GroupEdit(slot = 2, x = 200, y = 0, z = 16),
            AnimFrame.GroupEdit(slot = 3, x = 128, y = 140, z = 128),
        ),
    )
    val bytes = frame.encode()
    val again = AnimFrame.decode(bytes, base)

    check(bytes[0].toInt() and 0xFF == 0 && bytes[1].toInt() and 0xFF == 132) {
        "frame baseId prefix"
    }
    check(again.length == frame.length) { "length ${again.length} vs ${frame.length}" }
    check(again.indices.contentEquals(frame.indices)) { "indices" }
    check(again.x.contentEquals(frame.x)) { "x ${again.x.toList()} vs ${frame.x.toList()}" }
    check(again.y.contentEquals(frame.y)) { "y" }
    check(again.z.contentEquals(frame.z)) { "z" }
    check(again.flags.contentEquals(frame.flags)) { "flags" }
    check(again.prevOriginIndices.contentEquals(frame.prevOriginIndices)) { "prevOrigin" }

    println("frame encode/decode ok  bytes=${bytes.size} groups=${frame.length}")
    for (i in 0 until frame.length) {
        val slot = frame.indices[i].toInt()
        println(
            "  slot $slot ${TransformType.nameOf(base.types[slot])} " +
                "xyz=${frame.x[i]},${frame.y[i]},${frame.z[i]} " +
                "prevOrigin=${frame.prevOriginIndices[i]} labels=${base.bones[slot].toList()}",
        )
    }
}
