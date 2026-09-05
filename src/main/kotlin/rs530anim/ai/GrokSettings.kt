package rs530anim.ai

import java.nio.file.Files
import java.nio.file.Path

object GrokSettings {
    private fun keyFile(): Path =
        Path.of(System.getProperty("user.home"), ".rs530-anim-tool", "xai.key")

    fun apiKey(): String? {
        val env = System.getenv("XAI_API_KEY")?.trim().orEmpty()
        if (env.isNotEmpty()) return env
        val file = keyFile()
        if (Files.isRegularFile(file)) {
            val text = Files.readString(file).trim()
            if (text.isNotEmpty()) return text
        }
        return null
    }

    fun saveKey(key: String) {
        val file = keyFile()
        Files.createDirectories(file.parent)
        Files.writeString(file, key.trim())
    }

    fun model(): String = System.getenv("XAI_MODEL")?.trim().takeUnless { it.isNullOrEmpty() } ?: "grok-4.3"
}
