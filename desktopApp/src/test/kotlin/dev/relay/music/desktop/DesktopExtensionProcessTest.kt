package dev.relay.music.desktop

import dev.relay.music.extension.ExtensionKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DesktopExtensionProcessTest {
    @Test
    fun templateNegotiatesOutOfProcess() {
        val script = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .map { File(it, "relay-extension-template/relay_extension.py") }
            .firstOrNull(File::isFile)
            ?: error("Relay extension template is missing.")
        val directory = Files.createTempDirectory("relay-extension-test-").toFile()
        try {
            DesktopExtensionProcess(listOf("python3", script.absolutePath), directory).use { process ->
                val result = assertIs<DesktopExtensionStartResult.Ready>(process.open())
                assertEquals("example.relay.source", result.handshake.id)
                assertEquals(ExtensionKind.SOURCE, result.handshake.kind)
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
