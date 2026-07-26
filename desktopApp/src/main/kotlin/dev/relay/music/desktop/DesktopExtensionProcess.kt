package dev.relay.music.desktop

import dev.relay.music.extension.ApiRange
import dev.relay.music.extension.AuthenticationMethod
import dev.relay.music.extension.ExtensionHandshake
import dev.relay.music.extension.ExtensionKind
import dev.relay.music.extension.ExtensionNegotiation
import dev.relay.music.extension.ExtensionPermission
import dev.relay.music.extension.negotiate
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

sealed interface DesktopExtensionStartResult {
    data class Ready(val handshake: ExtensionHandshake) : DesktopExtensionStartResult
    data class Refused(val reason: String) : DesktopExtensionStartResult
}

/** Bounded JSON-RPC child-process boundary for desktop executable extensions. */
class DesktopExtensionProcess(
    private val command: List<String>,
    private val privateDirectory: File,
    private val requestTimeoutMs: Long = 10_000,
) : AutoCloseable {
    private val responseExecutor = Executors.newSingleThreadExecutor()
    private var process: Process? = null

    init {
        require(command.isNotEmpty() && command.none(String::isBlank)) { "Extension command is invalid." }
        require(requestTimeoutMs in 1_000..30_000) { "Extension timeout is invalid." }
    }

    fun open(): DesktopExtensionStartResult = runCatching {
        val handshake = handshake()
        when (val negotiation = handshake.negotiate()) {
            is ExtensionNegotiation.Accepted -> DesktopExtensionStartResult.Ready(handshake)
            is ExtensionNegotiation.Refused -> error(negotiation.reason)
        }
    }.fold(
        onSuccess = { it },
        onFailure = { error ->
            close()
            DesktopExtensionStartResult.Refused(error.message ?: "Extension did not start.")
        },
    )

    @Synchronized
    private fun handshake(): ExtensionHandshake {
        val response = request("handshake")
        return ExtensionHandshake(
            id = response.requiredString("id"),
            version = response.requiredString("version"),
            kind = ExtensionKind.valueOf(response.requiredString("kind")),
            api = response.requiredObject("api").let { ApiRange(it.requiredInt("minimum"), it.requiredInt("maximum")) },
            capabilities = response.requiredStringSet("capabilities"),
            permissions = response.requiredStringSet("permissions").mapTo(linkedSetOf(), ExtensionPermission::valueOf),
            settingsSchemaVersion = response.requiredInt("settingsSchemaVersion"),
            authentication = response.requiredStringSet("authentication").mapTo(linkedSetOf(), AuthenticationMethod::valueOf),
        )
    }

    private fun request(method: String): JsonObject {
        val requestId = UUID.randomUUID().toString()
        val request = buildJsonObject {
            put("id", requestId)
            put("method", method)
        }.toString().toByteArray(Charsets.UTF_8)
        require(request.size <= MAX_MESSAGE_BYTES) { "Extension request is too large." }
        val child = process ?: startProcess().also { process = it }
        child.outputStream.write(request)
        child.outputStream.write('\n'.code)
        child.outputStream.flush()
        val response = responseExecutor.submit<String?> { readMessage(child) }
            .get(requestTimeoutMs, TimeUnit.MILLISECONDS)
            ?: error("Extension closed without a response.")
        val payload = Json.parseToJsonElement(response).jsonObject
        require(payload["id"]?.jsonPrimitive?.contentOrNull == requestId) { "Extension response ID is invalid." }
        payload["error"]?.jsonPrimitive?.contentOrNull?.let(::error)
        return payload["result"]?.jsonObject ?: error("Extension response is missing a result.")
    }

    private fun startProcess(): Process {
        privateDirectory.mkdirs()
        require(privateDirectory.isDirectory) { "Could not prepare extension directory." }
        return ProcessBuilder(command)
            .directory(privateDirectory)
            .redirectError(ProcessBuilder.Redirect.appendTo(File(privateDirectory, "extension.stderr.log")))
            .apply {
                environment().clear()
                environment()["PATH"] = System.getenv("PATH") ?: "/usr/bin:/bin"
            }
            .start()
    }

    private fun readMessage(process: Process): String? {
        val output = ByteArrayOutputStream()
        while (true) {
            val next = process.inputStream.read()
            if (next < 0) return output.takeIf { it.size() > 0 }?.toString(Charsets.UTF_8)
            if (next == '\n'.code) return output.toString(Charsets.UTF_8)
            require(output.size() < MAX_MESSAGE_BYTES) { "Extension response is too large." }
            output.write(next)
        }
    }

    override fun close() {
        process?.let { child ->
            child.destroy()
            if (!child.waitFor(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) child.destroyForcibly()
        }
        process = null
        responseExecutor.shutdownNow()
    }

    private fun JsonObject.requiredString(name: String): String =
        get(name)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: error("Extension $name is missing.")

    private fun JsonObject.requiredInt(name: String): Int =
        get(name)?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: error("Extension $name is invalid.")

    private fun JsonObject.requiredObject(name: String): JsonObject =
        get(name)?.jsonObject ?: error("Extension $name is missing.")

    private fun JsonObject.requiredStringSet(name: String): Set<String> =
        (get(name)?.jsonArray ?: error("Extension $name is missing.")).mapTo(linkedSetOf()) { value ->
            value.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() } ?: error("Extension $name is invalid.")
        }

    private companion object {
        const val MAX_MESSAGE_BYTES = 64 * 1024
        const val SHUTDOWN_TIMEOUT_MS = 2_000L
    }
}
