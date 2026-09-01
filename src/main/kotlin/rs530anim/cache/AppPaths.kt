package rs530anim.cache

import java.nio.file.Files
import java.nio.file.Path

/** Directories next to the running jar (or the working directory when launched from Gradle/IDE). */
object AppPaths {
    fun installDir(): Path {
        val codeSource = try {
            AppPaths::class.java.protectionDomain?.codeSource?.location
        } catch (_: Exception) {
            null
        }
        if (codeSource != null && codeSource.protocol == "file") {
            val loc = Path.of(codeSource.toURI())
            val dir = if (Files.isRegularFile(loc)) loc.parent else loc
            // Gradle puts classes under build/classes/... — treat project root as install dir.
            val text = dir.toString().replace('\\', '/')
            if (text.contains("/build/")) {
                return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
            }
            return dir.toAbsolutePath().normalize()
        }
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    }

    fun cacheDir(): Path = installDir().resolve("cache")
}
