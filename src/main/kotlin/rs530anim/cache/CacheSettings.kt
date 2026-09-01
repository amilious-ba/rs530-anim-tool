package rs530anim.cache

import java.nio.file.Files
import java.nio.file.Path

/**
 * Same knobs as Amilious client/config.json.
 * JS5 port is server_port + world when js5_port is omitted
 * (client.js5Connect: port = server_port + worldListId).
 */
data class CacheSettings(
    val host: String = "play.2009scape.org",
    val world: Int = 1,
    val serverPort: Int = 43594,
    val js5Port: Int? = 43595,
    val revision: Int = 530,
) {
    val connectPort: Int get() = js5Port ?: (serverPort + world)

    companion object {
        fun load(path: Path?): CacheSettings {
            if (path == null || !Files.isRegularFile(path)) {
                val fallback = listOf("config.json", "client/config.json")
                    .map { Path.of(it) }
                    .firstOrNull { Files.isRegularFile(it) }
                return if (fallback != null) load(fallback) else CacheSettings()
            }
            val text = Files.readString(path)
            fun field(name: String): String? {
                val re = Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"")
                val reNum = Regex("\"$name\"\\s*:\\s*(-?\\d+)")
                return re.find(text)?.groupValues?.get(1) ?: reNum.find(text)?.groupValues?.get(1)
            }
            return CacheSettings(
                host = field("ip_address") ?: field("ip_management") ?: "play.2009scape.org",
                world = field("world")?.toIntOrNull() ?: 1,
                serverPort = field("server_port")?.toIntOrNull() ?: 43594,
                js5Port = field("js5_port")?.toIntOrNull(),
                revision = field("revision")?.toIntOrNull() ?: 530,
            )
        }
    }
}
