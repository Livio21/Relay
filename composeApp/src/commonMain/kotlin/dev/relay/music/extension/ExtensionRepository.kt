package dev.relay.music.extension

data class RepositoryDescriptor(
    val id: String,
    val name: String,
    val indexUrl: String,
    /** P-256 SubjectPublicKeyInfo, Base64 encoded. */
    val signingPublicKey: String,
    val signingAlgorithm: String = "ECDSA_P256_SHA256",
)

data class ExtensionCatalogEntry(
    val id: String,
    val name: String,
    val version: String,
    val kind: ExtensionKind,
    val api: ApiRange,
    val artifactUrl: String,
    val artifactSha256: String,
    val artifactSizeBytes: Long,
    val permissions: Set<ExtensionPermission>,
    /** Required only when this artifact is an Android APK. */
    val androidPackageName: String? = null,
    /** SHA-256 of the Android APK signing certificate, lowercase hexadecimal. */
    val androidSigningCertificateSha256: String? = null,
)

data class RepositoryCatalog(
    val repositoryId: String,
    val extensions: List<ExtensionCatalogEntry>,
)

/** Host-owned state only; binaries and credentials are never represented here. */
data class InstalledExtension(
    val repositoryId: String,
    val extensionId: String,
    val version: String,
    val kind: ExtensionKind,
    val enabled: Boolean,
    /** Present only when Android installed the APK but Relay could not enable it. */
    val disabledReason: String? = null,
    val permissions: Set<ExtensionPermission>,
    val androidPackageName: String? = null,
    val androidSigningCertificateSha256: String? = null,
)

/** Validates parsed catalog fields after the platform has verified the signed catalog bytes. */
fun RepositoryDescriptor.validate(): String? = when {
    !id.matches(Regex("[a-z0-9][a-z0-9._-]{0,127}")) -> "Repository ID is invalid."
    name.isBlank() || name.length > 128 -> "Repository name is invalid."
    !indexUrl.isSecureUrl() -> "Repository index must use HTTPS."
    signingAlgorithm != "ECDSA_P256_SHA256" -> "Repository signature algorithm is unsupported."
    !signingPublicKey.matches(Regex("[A-Za-z0-9+/]{40,2048}={0,2}")) -> "Repository signing key is invalid."
    else -> null
}

fun RepositoryCatalog.validate(descriptor: RepositoryDescriptor): String? = when {
    descriptor.validate() != null -> descriptor.validate()
    repositoryId != descriptor.id -> "Catalog repository ID does not match its descriptor."
    extensions.size > 500 -> "Catalog contains too many extensions."
    extensions.groupingBy { it.id }.eachCount().any { it.value > 1 } -> "Catalog has duplicate extension IDs."
    else -> extensions.asSequence().mapNotNull { it.validate() }.firstOrNull()
}

/**
 * Structural validity only. An entry for an older or newer Relay API is still a valid catalog
 * row — check [isCompatible] separately so one incompatible extension never invalidates the
 * whole repository catalog.
 */
fun ExtensionCatalogEntry.validate(): String? = when {
    !id.matches(Regex("[a-z0-9][a-z0-9._-]{0,127}")) -> "Extension ID is invalid."
    name.isBlank() || name.length > 128 || version.isBlank() || version.length > 64 -> "Extension name or version is invalid."
    api.minimum < 1 || api.maximum < api.minimum -> "Extension API range is invalid."
    !artifactUrl.isSecureUrl() -> "Extension artifact must use HTTPS."
    !artifactSha256.matches(Regex("[0-9a-f]{64}")) -> "Extension digest is invalid."
    artifactSizeBytes !in 1..(100L * 1024 * 1024) -> "Extension artifact size is invalid."
    (androidPackageName == null) != (androidSigningCertificateSha256 == null) -> "Android extension package identity is incomplete."
    androidPackageName != null && !androidPackageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")) -> "Android extension package name is invalid."
    androidSigningCertificateSha256 != null && !androidSigningCertificateSha256.matches(Regex("[0-9a-f]{64}")) -> "Android extension signer is invalid."
    else -> null
}

val ExtensionCatalogEntry.isCompatible: Boolean
    get() = api.accepts(EXTENSION_API_VERSION)

fun ExtensionCatalogEntry.asInstalled(repositoryId: String, disabledReason: String? = null) = InstalledExtension(
    repositoryId = repositoryId,
    extensionId = id,
    version = version,
    kind = kind,
    enabled = disabledReason == null,
    disabledReason = disabledReason,
    permissions = permissions,
    androidPackageName = androidPackageName,
    androidSigningCertificateSha256 = androidSigningCertificateSha256,
)

fun InstalledExtension.validate(): String? = when {
    !repositoryId.matches(Regex("[a-z0-9][a-z0-9._-]{0,127}")) -> "Installed extension repository ID is invalid."
    !extensionId.matches(Regex("[a-z0-9][a-z0-9._-]{0,127}")) || version.isBlank() || version.length > 64 -> "Installed extension identity is invalid."
    enabled && disabledReason != null -> "Enabled extension cannot have a disabled reason."
    !enabled && (disabledReason.isNullOrBlank() || disabledReason.length > 240) -> "Disabled extension reason is invalid."
    (androidPackageName == null) != (androidSigningCertificateSha256 == null) -> "Installed Android extension identity is incomplete."
    androidPackageName != null && !androidPackageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")) -> "Installed Android extension package is invalid."
    androidSigningCertificateSha256 != null && !androidSigningCertificateSha256.matches(Regex("[0-9a-f]{64}")) -> "Installed Android extension signer is invalid."
    else -> null
}

fun InstalledExtension.disabled(reason: String?) = copy(
    enabled = false,
    disabledReason = reason?.trim()?.take(240).takeUnless { it.isNullOrBlank() } ?: "Extension failed to respond.",
)

private fun String.isSecureUrl(): Boolean {
    val host = removePrefix("https://").substringBefore('/')
    return startsWith("https://") && host.isNotBlank() && length <= 2_048 &&
        none(Char::isWhitespace) && !contains('@') && !contains('#') && !contains('?')
}
