package dev.relay.music.extension

import dev.relay.music.model.Track

const val EXTENSION_API_VERSION = 2

enum class ExtensionKind { SOURCE }

enum class ExtensionPermission { NETWORK, PLAYBACK_EVENTS, METADATA_LOOKUP }

enum class AuthenticationMethod { NONE, SYSTEM_BROWSER_OAUTH, DEVICE_CODE, API_KEY }

data class ApiRange(val minimum: Int, val maximum: Int) {
    fun accepts(version: Int) = minimum <= version && version <= maximum
}

data class ExtensionHandshake(
    val id: String,
    val version: String,
    val kind: ExtensionKind,
    val api: ApiRange,
    val capabilities: Set<String>,
    val permissions: Set<ExtensionPermission>,
    val settingsSchemaVersion: Int,
    val authentication: Set<AuthenticationMethod>,
)

sealed interface ExtensionNegotiation {
    data class Accepted(val apiVersion: Int) : ExtensionNegotiation
    data class Refused(val reason: String) : ExtensionNegotiation
}

fun ExtensionHandshake.negotiate(hostApiVersion: Int = EXTENSION_API_VERSION): ExtensionNegotiation = when {
    id.isBlank() || id.length > 128 -> ExtensionNegotiation.Refused("Extension ID is invalid.")
    version.isBlank() || version.length > 64 -> ExtensionNegotiation.Refused("Extension version is invalid.")
    api.minimum < 1 || api.maximum < api.minimum -> ExtensionNegotiation.Refused("Extension API range is invalid.")
    settingsSchemaVersion < 1 -> ExtensionNegotiation.Refused("Settings schema version is invalid.")
    !api.accepts(hostApiVersion) -> ExtensionNegotiation.Refused("Requires extension API ${api.minimum}-${api.maximum}; Relay supports $hostApiVersion.")
    else -> ExtensionNegotiation.Accepted(hostApiVersion)
}

/** One browseable listing a source offers, for example "Popular" or a genre shelf. */
data class SourceListing(
    val id: String,
    val name: String,
)

enum class SourceSettingType { TEXT, TOGGLE, CHOICE }

/** One user-editable source preference, declared by the source and rendered by Relay's UI. */
data class SourceSettingDefinition(
    val id: String,
    val label: String,
    val type: SourceSettingType,
    val defaultValue: String = "",
    val choices: List<String> = emptyList(),
)

fun SourceSettingDefinition.validate(): String? = when {
    !id.matches(Regex("[a-z0-9][a-z0-9._-]{0,63}")) -> "Source setting ID is invalid."
    label.isBlank() || label.length > 64 -> "Source setting label is invalid."
    defaultValue.length > 1_024 -> "Source setting default is too long."
    type == SourceSettingType.CHOICE && (choices.isEmpty() || choices.size > 16) -> "Source setting choices are invalid."
    type == SourceSettingType.CHOICE && choices.any { it.isBlank() || it.length > 64 } -> "Source setting choice is invalid."
    type == SourceSettingType.CHOICE && defaultValue.isNotEmpty() && defaultValue !in choices -> "Source setting default is not a choice."
    type != SourceSettingType.CHOICE && choices.isNotEmpty() -> "Source setting choices are not applicable."
    else -> null
}

/** Keeps only values that match a declared setting and its bounds; unknown keys are dropped. */
fun sanitizeSourceSettingValues(
    definitions: List<SourceSettingDefinition>,
    values: Map<String, String>,
): Map<String, String> = definitions.mapNotNull { definition ->
    val value = values[definition.id] ?: return@mapNotNull null
    val valid = when (definition.type) {
        SourceSettingType.TEXT -> value.length <= 1_024
        SourceSettingType.TOGGLE -> value == "true" || value == "false"
        SourceSettingType.CHOICE -> value in definition.choices
    }
    if (valid) definition.id to value else null
}.toMap()

/** Results remain grouped by the installed source that returned them. */
data class ExtensionSourceResults(
    val extensionId: String,
    val extensionName: String,
    val tracks: List<Track>,
    val listings: List<SourceListing> = emptyList(),
    val page: Int = 1,
    val hasNextPage: Boolean = false,
)

/** One shared-UI request covering search, listing browse, and pagination. */
data class SourceBrowseRequest(
    val query: String = "",
    val field: SourceSearchField = SourceSearchField.ALL,
    val extensionId: String? = null,
    val listingId: String? = null,
    val page: Int = 1,
) {
    /** Pages after the first append to existing results instead of replacing them. */
    val appendsResults: Boolean
        get() = page > 1
}

data class ExtensionDownloadProgress(
    val extensionId: String,
    val name: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
) {
    val fraction: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

/** Progress for one user-requested source track download, never an automatic cache fill. */
data class RemoteTrackDownloadProgress(
    val sourceId: String,
    val trackId: String,
    val name: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
) {
    val fraction: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

enum class SourceSearchField { ALL, TITLE, ARTIST, ALBUM }

fun SourceSearchField.toSourceQuery(value: String): String = value.trim().let { query ->
    if (query.isEmpty()) "" else when (this) {
        SourceSearchField.ALL -> query
        SourceSearchField.TITLE -> "title:$query"
        SourceSearchField.ARTIST -> "artist:$query"
        SourceSearchField.ALBUM -> "album:$query"
    }
}
