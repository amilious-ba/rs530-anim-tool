package rs530anim.cache

import java.nio.file.Files
import java.nio.file.Path

/**
 * On-disk JS5 store at `<install>/cache`.
 *
 *   cache/models/132.dat
 *   cache/bases/<id>.dat
 *   cache/frames/<id>.dat
 *   cache/seq/<id>.dat
 *   cache/js5/<archive>/<group>.dat   (anything else)
 */
class LocalCache(val root: Path = AppPaths.cacheDir()) {

    init {
        Files.createDirectories(root)
    }

    fun modelPath(id: Int): Path = root.resolve("models").resolve("$id.dat")
    fun basePath(id: Int): Path = root.resolve("bases").resolve("$id.dat")
    fun framesetPath(id: Int): Path = root.resolve("frames").resolve("$id.dat")
    fun seqPath(id: Int): Path = root.resolve("seq").resolve("$id.dat")
    fun groupPath(archive: Int, group: Int): Path =
        root.resolve("js5").resolve(archive.toString()).resolve("$group.dat")

    fun read(path: Path): ByteArray? =
        if (Files.isRegularFile(path)) Files.readAllBytes(path) else null

    fun write(path: Path, bytes: ByteArray): Path {
        Files.createDirectories(path.parent)
        Files.write(path, bytes)
        return path
    }

    /** Cache hit or JS5 fetch + write. Connects only on a miss. */
    fun model(id: Int, settings: CacheSettings): Pair<ByteArray, Path> {
        val path = modelPath(id)
        read(path)?.let { return it to path }
        Js5Client(settings).use { js5 ->
            val bytes = js5.fetchModel(id)
            write(path, bytes)
            return bytes to path
        }
    }

    fun base(id: Int, settings: CacheSettings): Pair<ByteArray, Path> {
        val path = basePath(id)
        read(path)?.let { return it to path }
        Js5Client(settings).use { js5 ->
            val bytes = js5.fetchBase(id)
            write(path, bytes)
            return bytes to path
        }
    }

    fun frameset(id: Int, settings: CacheSettings): Pair<ByteArray, Path> {
        val path = framesetPath(id)
        read(path)?.let { return it to path }
        Js5Client(settings).use { js5 ->
            val bytes = js5.fetchUncompressed(Js5Archives.FRAMES, id)
            write(path, bytes)
            return bytes to path
        }
    }
}
