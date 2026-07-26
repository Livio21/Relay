package dev.relay.music.extension

import android.content.Context
import android.util.Base64
import dev.relay.music.extension.ApiRange
import dev.relay.music.extension.ExtensionCatalogEntry
import dev.relay.music.extension.ExtensionKind
import dev.relay.music.extension.ExtensionPermission
import dev.relay.music.extension.RepositoryCatalog
import dev.relay.music.extension.RepositoryDescriptor
import dev.relay.music.extension.validate
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed interface CatalogRefreshResult {
    data class Success(val catalog: RepositoryCatalog, val fromCache: Boolean) : CatalogRefreshResult
    data class Failure(val message: String) : CatalogRefreshResult
}

class RepositoryCatalogClient(context: Context) {
    private val cacheDirectory = File(context.cacheDir, "relay/repository-catalogs")

    suspend fun cachedCatalog(descriptor: RepositoryDescriptor): RepositoryCatalog? = withContext(Dispatchers.IO) {
        loadCache(descriptor)?.catalog
    }

    suspend fun refresh(descriptor: RepositoryDescriptor): CatalogRefreshResult = withContext(Dispatchers.IO) {
        val cached = loadCache(descriptor)
        runCatching {
            require(descriptor.validate() == null) { descriptor.validate() ?: "Repository descriptor is invalid." }
            when (val index = readHttps(descriptor.indexUrl, MAX_INDEX_BYTES, cached?.etag, cached?.lastModified)) {
                is HttpResponse.NotModified -> requireNotNull(cached) { "Repository cache is missing." }.catalog
                is HttpResponse.Ok -> {
                    val signature = (readHttps("${descriptor.indexUrl}.sig", MAX_SIGNATURE_BYTES) as? HttpResponse.Ok)?.bytes
                        ?: error("Repository signature was not returned.")
                    require(verify(descriptor, index.bytes, signature)) { "Repository signature is invalid." }
                    parseCatalog(index.bytes, descriptor).also { catalog ->
                        require(catalog.validate(descriptor) == null) { catalog.validate(descriptor) ?: "Repository catalog is invalid." }
                        saveCache(descriptor, index.bytes, signature, index.etag, index.lastModified)
                    }
                }
            }
        }.fold(
            onSuccess = { catalog -> CatalogRefreshResult.Success(catalog, fromCache = false) },
            onFailure = { error ->
                cached?.let { CatalogRefreshResult.Success(it.catalog, fromCache = true) }
                    ?: CatalogRefreshResult.Failure(error.message ?: "Could not refresh repository.")
            },
        )
    }

    private fun loadCache(descriptor: RepositoryDescriptor): CachedCatalog? = runCatching {
        val metadata = JSONObject(cacheFile(descriptor, "meta").readText(Charsets.UTF_8))
        require(metadata.getString("repositoryId") == descriptor.id) { "Repository cache does not match its descriptor." }
        val index = cacheFile(descriptor, "index").readBytes().also { require(it.size <= MAX_INDEX_BYTES) }
        val signature = cacheFile(descriptor, "sig").readBytes().also { require(it.size <= MAX_SIGNATURE_BYTES) }
        require(verify(descriptor, index, signature)) { "Repository cache signature is invalid." }
        val catalog = parseCatalog(index, descriptor)
        require(catalog.validate(descriptor) == null) { "Repository cache is invalid." }
        CachedCatalog(catalog, metadata.optString("etag").ifBlank { null }, metadata.optString("lastModified").ifBlank { null })
    }.getOrElse {
        deleteCache(descriptor)
        null
    }

    private fun saveCache(
        descriptor: RepositoryDescriptor,
        index: ByteArray,
        signature: ByteArray,
        etag: String?,
        lastModified: String?,
    ) {
        cacheDirectory.mkdirs()
        require(cacheDirectory.isDirectory) { "Could not prepare repository cache." }
        writeAtomically(cacheFile(descriptor, "index"), index)
        writeAtomically(cacheFile(descriptor, "sig"), signature)
        writeAtomically(
            cacheFile(descriptor, "meta"),
            JSONObject().put("repositoryId", descriptor.id).put("etag", etag).put("lastModified", lastModified).toString().toByteArray(),
        )
    }

    private fun deleteCache(descriptor: RepositoryDescriptor) {
        listOf("index", "sig", "meta").forEach { cacheFile(descriptor, it).delete() }
    }

    private fun cacheFile(descriptor: RepositoryDescriptor, suffix: String) = File(cacheDirectory, "${descriptor.id}.$suffix")

    private fun writeAtomically(file: File, bytes: ByteArray) {
        val temporary = File(file.parentFile, ".${file.name}.tmp")
        temporary.outputStream().use { it.write(bytes) }
        require(temporary.renameTo(file)) { "Could not save repository cache." }
    }

    private fun readHttps(
        url: String,
        maximumBytes: Int,
        etag: String? = null,
        lastModified: String? = null,
    ): HttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json, text/plain")
            etag?.let { setRequestProperty("If-None-Match", it) }
            lastModified?.let { setRequestProperty("If-Modified-Since", it) }
        }
        return try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> HttpResponse.NotModified
                HttpURLConnection.HTTP_OK -> {
                    require(connection.contentLengthLong !in (maximumBytes + 1L)..Long.MAX_VALUE) { "Repository response is too large." }
                    HttpResponse.Ok(
                        bytes = connection.inputStream.use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= maximumBytes) { "Repository response is too large." }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        },
                        etag = connection.getHeaderField("ETag"),
                        lastModified = connection.getHeaderField("Last-Modified"),
                    )
                }
                else -> error("Repository returned HTTP ${connection.responseCode}.")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun verify(descriptor: RepositoryDescriptor, bytes: ByteArray, signatureBytes: ByteArray): Boolean {
        val publicKey = Base64.decode(descriptor.signingPublicKey, Base64.DEFAULT)
        val signature = Base64.decode(signatureBytes.toString(Charsets.US_ASCII).trim(), Base64.DEFAULT)
        return Signature.getInstance("SHA256withECDSA").run {
            initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKey)))
            update(bytes)
            verify(signature)
        }
    }

    private fun parseCatalog(bytes: ByteArray, descriptor: RepositoryDescriptor): RepositoryCatalog {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.getInt("schemaVersion") == 1) { "Repository schema is unsupported." }
        val entries = root.getJSONArray("extensions")
        require(entries.length() <= 500) { "Repository contains too many extensions." }
        return RepositoryCatalog(
            repositoryId = root.getString("repositoryId"),
            extensions = buildList {
                for (index in 0 until entries.length()) add(entries.getJSONObject(index).toCatalogEntry())
            },
        )
    }

    private fun JSONObject.toCatalogEntry() = ExtensionCatalogEntry(
        id = getString("id"),
        name = getString("name"),
        version = getString("version"),
        kind = ExtensionKind.valueOf(getString("kind")),
        api = getJSONObject("api").let { ApiRange(it.getInt("minimum"), it.getInt("maximum")) },
        artifactUrl = getString("artifactUrl"),
        artifactSha256 = getString("sha256"),
        artifactSizeBytes = getLong("artifactSizeBytes"),
        permissions = getJSONArray("permissions").toPermissions(),
        androidPackageName = optString("androidPackageName").ifBlank { null },
        androidSigningCertificateSha256 = optString("androidSigningCertificateSha256").ifBlank { null },
    )

    private fun JSONArray.toPermissions(): Set<ExtensionPermission> = buildSet {
        require(length() <= 16) { "Extension requests too many permissions." }
        for (index in 0 until length()) add(ExtensionPermission.valueOf(getString(index)))
    }

    private sealed interface HttpResponse {
        data class Ok(val bytes: ByteArray, val etag: String?, val lastModified: String?) : HttpResponse
        data object NotModified : HttpResponse
    }

    private data class CachedCatalog(
        val catalog: RepositoryCatalog,
        val etag: String?,
        val lastModified: String?,
    )

    private companion object {
        const val MAX_INDEX_BYTES = 2 * 1024 * 1024
        const val MAX_SIGNATURE_BYTES = 512
    }
}
