package dev.relay.music.extension

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface RepositoryImportResult {
    data class Success(val descriptor: RepositoryDescriptor) : RepositoryImportResult
    data class Failure(val message: String) : RepositoryImportResult
}

/** Reads a small repository descriptor. Importing is deliberately separate from trusting it. */
class RepositoryDescriptorClient {
    suspend fun import(descriptorUrl: String): RepositoryImportResult = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(descriptorUrl.trim())
            require(url.protocol == "https" && url.host.isNotBlank() && url.userInfo == null) {
                "Repository descriptor must use HTTPS."
            }
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
            }
            try {
                require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    "Repository descriptor returned HTTP ${connection.responseCode}."
                }
                require(connection.contentLengthLong !in (MAX_DESCRIPTOR_BYTES + 1L)..Long.MAX_VALUE) {
                    "Repository descriptor is too large."
                }
                val payload = connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(4 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= MAX_DESCRIPTOR_BYTES) { "Repository descriptor is too large." }
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                }
                parseRepositoryDescriptor(payload)
            } finally {
                connection.disconnect()
            }
        }.fold(
            onSuccess = RepositoryImportResult::Success,
            onFailure = { RepositoryImportResult.Failure(it.message ?: "Could not import repository.") },
        )
    }

    private companion object {
        const val MAX_DESCRIPTOR_BYTES = 32 * 1024
    }
}

/** Parses the portable, unsigned descriptor; users must review and trust it explicitly. */
internal fun parseRepositoryDescriptor(payload: String): RepositoryDescriptor {
    val root = Json.parseToJsonElement(payload).jsonObject
    require(root.requiredDescriptorInt("schemaVersion") == 1) { "Repository descriptor schema is unsupported." }
    return RepositoryDescriptor(
        id = root.requiredDescriptorString("repositoryId"),
        name = root.requiredDescriptorString("name"),
        indexUrl = root.requiredDescriptorString("indexUrl"),
        signingPublicKey = root.requiredDescriptorString("signingPublicKey"),
        signingAlgorithm = root["signingAlgorithm"]?.jsonPrimitive?.content ?: "ECDSA_P256_SHA256",
    ).also { descriptor ->
        require(descriptor.validate() == null) { descriptor.validate() ?: "Repository descriptor is invalid." }
    }
}

private fun JsonObject.requiredDescriptorString(name: String): String =
    this[name]?.jsonPrimitive?.content ?: error("Repository descriptor $name is missing.")

private fun JsonObject.requiredDescriptorInt(name: String): Int =
    this[name]?.jsonPrimitive?.content?.toIntOrNull() ?: error("Repository descriptor $name is invalid.")
