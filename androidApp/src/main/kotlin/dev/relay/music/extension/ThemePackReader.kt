package dev.relay.music.extension

import org.json.JSONObject

/** Parses an untrusted Theme Pack JSON document into the validated data-only model. */
object ThemePackReader {
    fun parse(json: String): Result<ThemePack> = runCatching {
        require(json.toByteArray(Charsets.UTF_8).size <= THEME_PACK_MAX_BYTES) { "Theme pack file is too large." }
        val root = JSONObject(json).requireOnly("schemaVersion", "id", "version", "name", "colors", "presentation")
        val colors = root.getJSONObject("colors").requireOnly("ink", "panel", "line", "paper", "muted", "signal", "danger")
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
                it.requireOnly(
                    "libraryLayout", "playerLayout", "background", "backgroundAsset", "effects",
                    "typography", "chrome", "icons",
                )
                ThemePresentation(
                    libraryLayout = it.optString("libraryLayout", "LIST").asEnum(),
                    playerLayout = it.optString("playerLayout", "STANDARD").asEnum(),
                    background = it.optString("background", "NONE").asEnum(),
                    backgroundAsset = it.optString("backgroundAsset").ifBlank { null },
                    effects = it.optJSONArray("effects")?.let { effects ->
                        require(effects.length() <= 4) { "Theme pack has too many effects." }
                        (0 until effects.length()).map { index ->
                            val effect = effects.getJSONObject(index).requireOnly("kind", "strength")
                            ThemeEffect(effect.getString("kind").asEnum(), effect.optDouble("strength", 1.0).toFloat())
                        }
                    }.orEmpty(),
                    typography = it.optJSONObject("typography")?.let { typography ->
                        typography.requireOnly("contentFont", "utilityFont")
                        ThemeTypography(
                            contentFont = typography.optString("contentFont", "SANS").asEnum(),
                            utilityFont = typography.optString("utilityFont", "MONO").asEnum(),
                        )
                    } ?: ThemeTypography(),
                    chrome = it.optJSONObject("chrome")?.let { chrome ->
                        chrome.requireOnly("borderWidthDp", "cornerRadiusDp", "fill", "shadow")
                        ThemeChrome(
                            borderWidthDp = chrome.optInt("borderWidthDp", 1),
                            cornerRadiusDp = chrome.optInt("cornerRadiusDp", 0),
                            fill = chrome.optString("fill", "OUTLINE").asEnum(),
                            shadow = chrome.optString("shadow", "NONE").asEnum(),
                        )
                    } ?: ThemeChrome(),
                    icons = it.optJSONObject("icons")?.let { icons ->
                        icons.requireOnly("set")
                        ThemeIcons(
                            set = icons.optString("set", "TEXT").asEnum(),
                        )
                    } ?: ThemeIcons(),
                )
            } ?: ThemePresentation(),
        )
        pack.validate()?.let { error(it) }
        pack
    }

    /** Signed repositories currently carry one JSON document, never packaged assets or code. */
    fun parseCatalogArtifact(json: String, expectedId: String, expectedVersion: String): Result<ThemePack> = runCatching {
        require(json.toByteArray(Charsets.UTF_8).size <= THEME_PACK_MAX_BYTES) { "Theme pack file is too large." }
        require(JSONObject(json).getString("version") == expectedVersion) {
            "Theme pack version does not match its catalog entry."
        }
        parse(json).getOrThrow().also { pack ->
            require(pack.id == expectedId) { "Theme pack ID does not match its catalog entry." }
            require(pack.presentation.background != ThemeBackground.ASSET) { "Repository theme packs cannot contain assets." }
        }
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

    private inline fun <reified T : Enum<T>> String.asEnum(): T =
        enumValues<T>().firstOrNull { it.name == this } ?: error("Theme pack option is unsupported.")

    private fun JSONObject.requireOnly(vararg allowed: String): JSONObject = apply {
        val names = keys()
        while (names.hasNext()) require(names.next() in allowed) { "Theme pack contains an unsupported field." }
    }
}
