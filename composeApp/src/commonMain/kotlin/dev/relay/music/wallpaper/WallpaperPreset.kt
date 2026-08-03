package dev.relay.music.wallpaper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val WALLPAPER_PRESET_SCHEMA_VERSION = 1
const val MAX_WALLPAPER_ELEMENTS = 16
const val MAX_WALLPAPER_FILTERS = 8

/** Versioned, data-only composition. Hosts render this schema; presets never provide code or URLs. */
@Serializable
data class WallpaperPreset(
    val schemaVersion: Int = WALLPAPER_PRESET_SCHEMA_VERSION,
    val canvas: WallpaperCanvas = WallpaperCanvas(),
    val elements: List<WallpaperElement> = listOf(WallpaperElement.Artwork()),
    val filters: List<ArtworkFilter> = emptyList(),
    val visualizer: WallpaperVisualizer = WallpaperVisualizer.OFF,
    val soundReactive: Boolean = false,
    val batterySaver: Boolean = false,
    val showMetadata: Boolean = false,
    @Transient val warnings: List<String> = emptyList(),
)

@Serializable
data class WallpaperCanvas(
    val background: WallpaperCanvasBackground = WallpaperCanvasBackground.SOLID,
    /** Unsigned ARGB stored in a Long so the schema is identical on every platform. */
    val solidColorArgb: Long = 0xFF101010,
    val artworkFit: WallpaperArtworkFit = WallpaperArtworkFit.FILL,
    val pageOffset: WallpaperPageOffset = WallpaperPageOffset.FIXED,
)

@Serializable
data class WallpaperElementLayout(
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val width: Float = 1f,
    val height: Float = 1f,
    val anchor: WallpaperAnchor = WallpaperAnchor.CENTER,
    val opacity: Float = 1f,
    val visibility: WallpaperVisibility = WallpaperVisibility.BOTH,
    val font: WallpaperFont = WallpaperFont.BODY,
    val alignment: WallpaperTextAlignment = WallpaperTextAlignment.START,
)

@Serializable
sealed class WallpaperElement {
    abstract val layout: WallpaperElementLayout

    @Serializable
    @SerialName("artwork")
    data class Artwork(
        override val layout: WallpaperElementLayout = WallpaperElementLayout(),
    ) : WallpaperElement()

    @Serializable
    @SerialName("title")
    data class Title(
        override val layout: WallpaperElementLayout = WallpaperElementLayout(
            x = 0.08f, y = 0.76f, width = 0.84f, height = 0.08f,
            anchor = WallpaperAnchor.TOP_LEFT, font = WallpaperFont.TITLE,
        ),
    ) : WallpaperElement()

    @Serializable
    @SerialName("artist")
    data class Artist(
        override val layout: WallpaperElementLayout = WallpaperElementLayout(
            x = 0.08f, y = 0.84f, width = 0.84f, height = 0.05f,
            anchor = WallpaperAnchor.TOP_LEFT,
        ),
    ) : WallpaperElement()

    @Serializable
    @SerialName("album")
    data class Album(
        override val layout: WallpaperElementLayout = WallpaperElementLayout(
            x = 0.08f, y = 0.90f, width = 0.84f, height = 0.04f,
            anchor = WallpaperAnchor.TOP_LEFT, font = WallpaperFont.METADATA,
        ),
    ) : WallpaperElement()

    @Serializable
    @SerialName("clock")
    data class Clock(
        override val layout: WallpaperElementLayout = WallpaperElementLayout(
            x = 0.92f, y = 0.08f, width = 0.5f, height = 0.10f,
            anchor = WallpaperAnchor.TOP_RIGHT, font = WallpaperFont.DISPLAY,
            alignment = WallpaperTextAlignment.END,
        ),
    ) : WallpaperElement()

    @Serializable
    @SerialName("progress")
    data class Progress(
        override val layout: WallpaperElementLayout = WallpaperElementLayout(
            x = 0.08f, y = 0.96f, width = 0.84f, height = 0.012f,
            anchor = WallpaperAnchor.BOTTOM_LEFT,
        ),
    ) : WallpaperElement()
}

@Serializable
sealed class ArtworkFilter {
    @Serializable
    @SerialName("grayscale")
    data class Grayscale(val amount: Float = 1f) : ArtworkFilter()

    @Serializable
    @SerialName("blur")
    data class Blur(val radius: Float = 12f) : ArtworkFilter()

    @Serializable
    @SerialName("duotone")
    data class Duotone(
        val shadowColorArgb: Long = 0xFF101010,
        val highlightColorArgb: Long = 0xFFF3F0E8,
        val amount: Float = 1f,
    ) : ArtworkFilter()

    @Serializable
    @SerialName("brightness_contrast")
    data class BrightnessContrast(
        val brightness: Float = 0f,
        val contrast: Float = 1f,
    ) : ArtworkFilter()

    @Serializable
    @SerialName("vignette")
    data class Vignette(val strength: Float = 0.5f) : ArtworkFilter()

    @Serializable
    @SerialName("grain")
    data class Grain(val strength: Float = 0.12f, val seed: Int = 0) : ArtworkFilter()
}

@Serializable enum class WallpaperCanvasBackground { SOLID, ARTWORK_AVERAGE }
@Serializable enum class WallpaperArtworkFit { FILL, FIT }
@Serializable enum class WallpaperPageOffset { FIXED, FOLLOW }
@Serializable enum class WallpaperAnchor { TOP_LEFT, TOP_CENTER, TOP_RIGHT, CENTER_LEFT, CENTER, CENTER_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT }
@Serializable enum class WallpaperVisibility { HOME, LOCK, BOTH }
@Serializable enum class WallpaperFont { METADATA, BODY, TITLE, DISPLAY }
@Serializable enum class WallpaperTextAlignment { START, CENTER, END }
@Serializable enum class WallpaperVisualizer { OFF, BARS, WAVE }

/** Legacy Room fields retained only so pre-composition installs and backups can migrate. */
enum class WallpaperBackground { INK, PAPER }
enum class WallpaperTitlePosition { TOP_LEFT, TOP_CENTER, TOP_RIGHT, CENTER, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT }
enum class WallpaperTitleSize { SMALL, NORMAL, LARGE }
enum class WallpaperEffect { NONE, AMBIENT_BLUR, REFLECTION }

data class WallpaperPresetDecodeResult(
    val preset: WallpaperPreset,
    val valid: Boolean,
    val warnings: List<String> = emptyList(),
)

data class WallpaperElementBounds(val left: Float, val top: Float, val width: Float, val height: Float)

fun wallpaperElementBounds(layout: WallpaperElementLayout, canvasWidth: Float, canvasHeight: Float): WallpaperElementBounds {
    val width = layout.width * canvasWidth
    val height = layout.height * canvasHeight
    val anchorX = when (layout.anchor) {
        WallpaperAnchor.TOP_LEFT, WallpaperAnchor.CENTER_LEFT, WallpaperAnchor.BOTTOM_LEFT -> 0f
        WallpaperAnchor.TOP_CENTER, WallpaperAnchor.CENTER, WallpaperAnchor.BOTTOM_CENTER -> 0.5f
        WallpaperAnchor.TOP_RIGHT, WallpaperAnchor.CENTER_RIGHT, WallpaperAnchor.BOTTOM_RIGHT -> 1f
    }
    val anchorY = when (layout.anchor) {
        WallpaperAnchor.TOP_LEFT, WallpaperAnchor.TOP_CENTER, WallpaperAnchor.TOP_RIGHT -> 0f
        WallpaperAnchor.CENTER_LEFT, WallpaperAnchor.CENTER, WallpaperAnchor.CENTER_RIGHT -> 0.5f
        WallpaperAnchor.BOTTOM_LEFT, WallpaperAnchor.BOTTOM_CENTER, WallpaperAnchor.BOTTOM_RIGHT -> 1f
    }
    return WallpaperElementBounds(
        left = layout.x * canvasWidth - width * anchorX,
        top = layout.y * canvasHeight - height * anchorY,
        width = width,
        height = height,
    )
}

fun WallpaperVisibility.includes(target: WallpaperVisibility): Boolean =
    target == WallpaperVisibility.BOTH || this == WallpaperVisibility.BOTH || this == target

fun WallpaperPreset.validationError(): String? {
    if (schemaVersion != WALLPAPER_PRESET_SCHEMA_VERSION) return "Unsupported wallpaper preset schema."
    if (elements.size > MAX_WALLPAPER_ELEMENTS) return "Wallpaper has too many elements."
    if (filters.size > MAX_WALLPAPER_FILTERS) return "Wallpaper has too many filters."
    if (canvas.solidColorArgb !in 0..0xFFFFFFFFL) return "Wallpaper canvas color is invalid."
    elements.forEach { element ->
        with(element.layout) {
            if (!x.isFinite() || !y.isFinite() || x !in 0f..1f || y !in 0f..1f ||
                !width.isFinite() || !height.isFinite() || width !in 0.01f..1f || height !in 0.005f..1f ||
                !opacity.isFinite() || opacity !in 0f..1f
            ) return "Wallpaper element properties are invalid."
        }
    }
    filters.forEach { filter ->
        val valid = when (filter) {
            is ArtworkFilter.Grayscale -> filter.amount.isFinite() && filter.amount in 0f..1f
            is ArtworkFilter.Blur -> filter.radius.isFinite() && filter.radius in 0f..25f
            is ArtworkFilter.Duotone -> filter.amount.isFinite() && filter.amount in 0f..1f &&
                filter.shadowColorArgb in 0..0xFFFFFFFFL && filter.highlightColorArgb in 0..0xFFFFFFFFL
            is ArtworkFilter.BrightnessContrast -> filter.brightness.isFinite() && filter.brightness in -1f..1f &&
                filter.contrast.isFinite() && filter.contrast in 0.25f..2f
            is ArtworkFilter.Vignette -> filter.strength.isFinite() && filter.strength in 0f..1f
            is ArtworkFilter.Grain -> filter.strength.isFinite() && filter.strength in 0f..1f
        }
        if (!valid) return "Wallpaper filter properties are invalid."
    }
    return null
}

private val wallpaperJson = Json {
    classDiscriminator = "type"
    encodeDefaults = true
    ignoreUnknownKeys = true
}
private val knownElementTypes = setOf("artwork", "title", "artist", "album", "clock", "progress")
private val knownFilterTypes = setOf("grayscale", "blur", "duotone", "brightness_contrast", "vignette", "grain")

fun encodeWallpaperPreset(preset: WallpaperPreset): String {
    require(preset.validationError() == null) { preset.validationError().orEmpty() }
    return wallpaperJson.encodeToString(preset.copy(warnings = emptyList()))
}

/** Unknown future element/filter kinds are dropped; malformed known data falls back safely. */
fun decodeWallpaperPreset(raw: String?): WallpaperPresetDecodeResult {
    if (raw.isNullOrBlank()) return WallpaperPresetDecodeResult(WallpaperPreset(), valid = true)
    return runCatching {
        val root = wallpaperJson.parseToJsonElement(raw).jsonObject
        val schema = root["schemaVersion"]?.jsonPrimitive?.content?.toIntOrNull()
        require(schema == WALLPAPER_PRESET_SCHEMA_VERSION) { "Unsupported wallpaper preset schema $schema." }
        val rawElements = root["elements"]?.jsonArray ?: JsonArray(emptyList())
        val rawFilters = root["filters"]?.jsonArray ?: JsonArray(emptyList())
        require(rawElements.size <= MAX_WALLPAPER_ELEMENTS && rawFilters.size <= MAX_WALLPAPER_FILTERS) {
            "Wallpaper preset exceeds its size limits."
        }
        val warnings = mutableListOf<String>()
        fun keepKnown(values: JsonArray, known: Set<String>, label: String): JsonArray = JsonArray(values.filter { value ->
            val type = value.jsonObject["type"]?.jsonPrimitive?.content
            (type in known).also { if (!it) warnings += "Ignored unknown wallpaper $label: ${type ?: "missing type"}." }
        })
        val sanitized = JsonObject(root + mapOf(
            "elements" to keepKnown(rawElements, knownElementTypes, "element"),
            "filters" to keepKnown(rawFilters, knownFilterTypes, "filter"),
        ))
        val preset = wallpaperJson.decodeFromString<WallpaperPreset>(sanitized.toString())
        preset.validationError()?.let { error(it) }
        WallpaperPresetDecodeResult(preset.copy(warnings = warnings), valid = true, warnings = warnings)
    }.getOrElse { error ->
        val warning = error.message?.take(160) ?: "Wallpaper preset is invalid."
        WallpaperPresetDecodeResult(WallpaperPreset(warnings = listOf(warning)), valid = false, warnings = listOf(warning))
    }
}

fun WallpaperElement.withLayout(layout: WallpaperElementLayout): WallpaperElement = when (this) {
    is WallpaperElement.Artwork -> copy(layout = layout)
    is WallpaperElement.Title -> copy(layout = layout)
    is WallpaperElement.Artist -> copy(layout = layout)
    is WallpaperElement.Album -> copy(layout = layout)
    is WallpaperElement.Clock -> copy(layout = layout)
    is WallpaperElement.Progress -> copy(layout = layout)
}

val WallpaperElement.label: String
    get() = when (this) {
        is WallpaperElement.Artwork -> "ARTWORK"
        is WallpaperElement.Title -> "TITLE"
        is WallpaperElement.Artist -> "ARTIST"
        is WallpaperElement.Album -> "ALBUM"
        is WallpaperElement.Clock -> "CLOCK"
        is WallpaperElement.Progress -> "PROGRESS"
    }

fun defaultWallpaperElement(label: String): WallpaperElement = when (label) {
    "TITLE" -> WallpaperElement.Title()
    "ARTIST" -> WallpaperElement.Artist()
    "ALBUM" -> WallpaperElement.Album()
    "CLOCK" -> WallpaperElement.Clock()
    "PROGRESS" -> WallpaperElement.Progress()
    else -> WallpaperElement.Artwork()
}
