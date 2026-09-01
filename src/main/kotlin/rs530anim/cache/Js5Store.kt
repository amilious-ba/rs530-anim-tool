package rs530anim.cache

/**
 * Disk-backed JS5: one TCP session, files written under cache/.
 * Indexes live at cache/js5/255/<archive>.dat
 */
class Js5Store(
    private val settings: CacheSettings,
    val cache: LocalCache = LocalCache(),
) : AutoCloseable {
    private var net: Js5Client? = null
    private val indexes = HashMap<Int, Js5Index>()

    private fun client(): Js5Client = net ?: Js5Client(settings).also { net = it }

    fun index(archive: Int): Js5Index {
        indexes[archive]?.let { return it }
        val path = cache.groupPath(255, archive)
        val container = cache.read(path) ?: run {
            val fetched = client().fetchGroup(255, archive, trailerLen = 0)
            cache.write(path, fetched)
            fetched
        }
        val idx = Js5Index.decode(Js5Compression.uncompress(container))
        indexes[archive] = idx
        return idx
    }

    fun groupFiles(archive: Int, group: Int): Map<Int, ByteArray> {
        val path = cache.groupPath(archive, group)
        val raw = cache.read(path) ?: run {
            val fetched = client().fetchUncompressed(archive, group)
            cache.write(path, fetched)
            fetched
        }
        val idx = index(archive)
        return Js5Group.unpack(raw, idx.sizeOf(group).coerceAtLeast(1), idx.filesOf(group).let { if (it.isEmpty()) null else it })
    }

    fun file(archive: Int, group: Int, file: Int): ByteArray {
        val files = groupFiles(archive, group)
        return files[file] ?: error("js5 $archive/$group has no file $file (have ${files.keys})")
    }

    fun model(id: Int): ByteArray {
        val path = cache.modelPath(id)
        cache.read(path)?.let { return it }
        val bundled = Js5Store::class.java.getResourceAsStream("/rs530anim/models/$id.dat")
        if (bundled != null) {
            val bytes = bundled.readBytes()
            cache.write(path, bytes)
            return bytes
        }
        val bytes = file(Js5Archives.MODELS, id, 0)
        cache.write(path, bytes)
        return bytes
    }

    fun seqBytes(id: Int): ByteArray {
        val path = cache.seqPath(id)
        cache.read(path)?.let { return it }
        bundled("seq/$id.dat")?.let {
            cache.write(path, it)
            return it
        }
        val bytes = file(Js5Archives.SEQUENCES, id ushr 7, id and 0x7F)
        cache.write(path, bytes)
        return bytes
    }

    fun baseBytes(id: Int): ByteArray {
        val path = cache.basePath(id)
        cache.read(path)?.let { return it }
        bundled("bases/$id.dat")?.let {
            cache.write(path, it)
            return it
        }
        val bytes = file(Js5Archives.BASES, id, 0)
        cache.write(path, bytes)
        return bytes
    }

    fun framesetFiles(id: Int): Map<Int, ByteArray> {
        val dir = cache.root.resolve("frames").resolve(id.toString())
        if (java.nio.file.Files.isDirectory(dir)) {
            val existing = java.nio.file.Files.newDirectoryStream(dir, "*.dat").use { it.toList() }
            if (existing.isNotEmpty()) {
                return existing.associate { p ->
                    val fid = p.fileName.toString().removeSuffix(".dat").toInt()
                    fid to java.nio.file.Files.readAllBytes(p)
                }
            }
        }
        val bundledDir = "/rs530anim/frames/$id"
        val fromJar = (0..63).mapNotNull { fid ->
            bundled("frames/$id/$fid.dat")?.let { fid to it }
        }.toMap()
        if (fromJar.isNotEmpty()) {
            for ((fid, bytes) in fromJar) cache.write(dir.resolve("$fid.dat"), bytes)
            return fromJar
        }
        val files = groupFiles(Js5Archives.FRAMES, id)
        for ((fid, bytes) in files) {
            cache.write(dir.resolve("$fid.dat"), bytes)
        }
        return files
    }

    private fun bundled(path: String): ByteArray? =
        Js5Store::class.java.getResourceAsStream("/rs530anim/$path")?.readBytes()

    override fun close() {
        net?.close()
        net = null
    }
}
