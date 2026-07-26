package dev.relay.music.extension

import org.json.JSONObject

/** Parses an untrusted Theme Pack JSON document into the validated data-only model. */
object ThemePackReader {
    private const val MAX_BYTES = 64 * 1024

    fun parse(json: String): Result<ThemePack> = runCatching {
        require(json.length <= MAX_BYTES) { "Theme pack file is too large." }
        val root = JSONObject(json)
        val colors = root.getJSONObject("colors")
        val presentation = root.optJSONObject("presentation")
        val pack = ThemePack(
            schemaVersion = root.optInt("schemaVersion", 1),
            id = root.getString("id"),
            name = root.getString("name"),
            colors = ThemeColors(
                ink = colors.getString("ink"),
                panel = colors.getString("panel"),
                line = colors.getString("line"),
                paper = colors.getString("paper"),
                muted = colors.getString("muted"),
                signal = colors.getString("signal"),
                danger = colors.getString("danger"),
            ),
            presentation = presentation?.let {
                ThemePresentation(
                    libraryLayout = it.optString("libraryLayout", "LIST").asEnum(ThemeLibraryLayout.LIST),
                    playerLayout = it.optString("playerLayout", "STANDARD").asEnum(ThemePlayerLayout.STANDARD),
                    background = it.optString("background", "NONE").asEnum(ThemeBackground.NONE),
                    backgroundAsset = it.optString("backgroundAsset").ifBlank { null },
                    // Unknown future effect kinds are dropped rather than misread as another effect.
                    effects = it.optJSONArray("effects")?.let { effects ->
                        (0 until effects.length()).mapNotNull { index ->
                            val effect = effects.getJSONObject(index)
                            val kind = ThemeEffectKind.entries.firstOrNull { candidate -> candidate.name == effect.getString("kind") }
                            kind?.let { ThemeEffect(it, effect.optDouble("strength", 1.0).toFloat()) }
                        }
                    }.orEmpty(),
                    typography = it.optJSONObject("typography")?.let { typography ->
                        ThemeTypography(
                            contentFont = typography.optString("contentFont", "SANS").asEnum(ThemeFont.SANS),
                            utilityFont = typography.optString("utilityFont", "MONO").asEnum(ThemeFont.MONO),
                        )
                    } ?: ThemeTypography(),
                    chrome = it.optJSONObject("chrome")?.let { chrome ->
                        ThemeChrome(
                            borderWidthDp = chrome.optInt("borderWidthDp", 1),
                            cornerRadiusDp = chrome.optInt("cornerRadiusDp", 0),
                            fill = chrome.optString("fill", "OUTLINE").asEnum(ThemeFill.OUTLINE),
                            shadow = chrome.optString("shadow", "NONE").asEnum(ThemeShadow.NONE),
                        )
                    } ?: ThemeChrome(),
                    icons = it.optJSONObject("icons")?.let { icons ->
                        ThemeIcons(
                            set = icons.optString("set", "TEXT").asEnum(ThemeIconSet.TEXT),
                        )
                    } ?: ThemeIcons(),
                )
            } ?: ThemePresentation(),
        )
        pack.validate()?.let { error(it) }
        pack
    }

    fun toJson(pack: ThemePack): String = JSONObject()
        .put("schemaVersion", pack.schemaVersion)
        .put("id", pack.id)
        .put("name", pack.name)
        .put(
            "colors",
            JSONObject()
                .put("ink", pack.colors.ink).put("panel", pack.colors.panel).put("line", pack.colors.line)
                .put("paper", pack.colors.paper).put("muted", pack.colors.muted)
                .put("signal", pack.colors.signal).put("danger", pack.colors.danger),
        )
        .put(
            "presentation",
            JSONObject()
                .put("libraryLayout", pack.presentation.libraryLayout.name)
                .put("playerLayout", pack.presentation.playerLayout.name)
                .put("background", pack.presentation.background.name)
                .put("backgroundAsset", pack.presentation.backgroundAsset)
                .put("effects", org.json.JSONArray(pack.presentation.effects.map {
                    JSONObject().put("kind", it.kind.name).put("strength", it.strength)
                }))
                .put("typography", JSONObject()
                    .put("contentFont", pack.presentation.typography.contentFont.name)
                    .put("utilityFont", pack.presentation.typography.utilityFont.name))
                .put("chrome", JSONObject()
                    .put("borderWidthDp", pack.presentation.chrome.borderWidthDp)
                    .put("cornerRadiusDp", pack.presentation.chrome.cornerRadiusDp)
                    .put("fill", pack.presentation.chrome.fill.name)
                    .put("shadow", pack.presentation.chrome.shadow.name))
                .put("icons", JSONObject()
                    .put("set", pack.presentation.icons.set.name)),
        )
        .toString()

    private inline fun <reified T : Enum<T>> String.asEnum(fallback: T): T =
        enumValues<T>().firstOrNull { it.name == this } ?: fallback
}
