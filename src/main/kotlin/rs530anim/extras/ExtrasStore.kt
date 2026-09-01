package rs530anim.extras

import rs530anim.anim.AnimBase
import rs530anim.anim.AnimFrame
import rs530anim.cache.AppPaths
import java.nio.file.Files
import java.nio.file.Path

/**
 * Read/write extras/ next to the app. No JavaFX. The client can call the same
 * functions with its own extras root.
 *
 * Layout:
 *   extras/seq/<seqId>.json
 *   extras/frames/<seqId>/<index>.dat
 *
 * Frame bytes are client AnimFrame bytes (u16 baseId at offset 0). We never
 * write a new AnimBase.
 */
object ExtrasStore {
    fun defaultRoot(): Path = AppPaths.installDir().resolve("extras")

    fun seqFile(root: Path, seqId: Int): Path = root.resolve("seq").resolve("$seqId.json")
    fun frameFile(root: Path, seqId: Int, index: Int): Path =
        root.resolve("frames").resolve(seqId.toString()).resolve("$index.dat")

    fun save(
        def: SeqExtras,
        frames: List<AnimFrame>,
        root: Path = defaultRoot(),
    ): Path {
        require(frames.size == def.frames.size) {
            "seq ${def.id} frames ${def.frames.size} != payload ${frames.size}"
        }
        require(frames.isNotEmpty()) { "seq ${def.id} has no frames" }
        val baseId = frames[0].base.id
        require(frames.all { it.base.id == baseId }) { "mixed bases in seq ${def.id}" }
        val written = def.copy(
            baseId = baseId,
            frames = frames.indices.toList(),
        )
        val seqPath = seqFile(root, written.id)
        Files.createDirectories(seqPath.parent)
        Files.writeString(seqPath, written.toJson())
        val frameDir = root.resolve("frames").resolve(written.id.toString())
        Files.createDirectories(frameDir)
        frames.forEachIndexed { i, frame ->
            Files.write(frameFile(root, written.id, i), frame.encode())
        }
        return seqPath
    }

    fun load(
        seqId: Int,
        bases: (Int) -> AnimBase,
        root: Path = defaultRoot(),
    ): Pair<SeqExtras, List<AnimFrame>> {
        val seqPath = seqFile(root, seqId)
        require(Files.isRegularFile(seqPath)) { "no extras seq $seqId at $seqPath" }
        val def = SeqExtras.fromJson(Files.readString(seqPath))
        val base = bases(def.baseId)
        val frames = def.frames.map { index ->
            val path = frameFile(root, def.id, index)
            require(Files.isRegularFile(path)) { "missing frame $path" }
            AnimFrame.decode(Files.readAllBytes(path), base)
        }
        return def to frames
    }

    fun exists(seqId: Int, root: Path = defaultRoot()): Boolean =
        Files.isRegularFile(seqFile(root, seqId))
}
