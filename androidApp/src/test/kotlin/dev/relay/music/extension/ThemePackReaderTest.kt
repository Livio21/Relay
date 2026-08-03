package dev.relay.music.extension

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemePackReaderTest {
    private val zuneJson = """
        {
          "schemaVersion": 1,
          "id": "relay.theme.zune",
          "version": "1.0.0",
          "name": "Zune",
          "colors": {
            "ink": "#0B0B0B", "panel": "#151515", "line": "#454545", "paper": "#F4F4F4",
            "muted": "#A8A8A8", "signal": "#FF4F00", "danger": "#FF453A"
          },
          "presentation": {
            "libraryLayout": "LIST",
            "playerLayout": "STANDARD",
            "background": "ARTWORK_BLEED",
            "icons": { "set": "SYMBOLS" },
            "effects": [
              { "kind": "GRAIN", "strength": 0.2 },
              { "kind": "FUTURE_SHADER", "strength": 1.0 },
              { "kind": "VIGNETTE", "strength": 0.3 }
            ]
          }
        }
    """.trimIndent()
    private val safeZuneJson = zuneJson.lineSequence().filterNot { "FUTURE_SHADER" in it }.joinToString("\n")

    @Test
    fun parsesTheZunePackAndRejectsExecutableOrUnknownData() {
        val pack = ThemePackReader.parse(safeZuneJson).getOrThrow()

        assertEquals("relay.theme.zune", pack.id)
        assertEquals("#FF4F00", pack.colors.signal)
        assertEquals(ThemeBackground.ARTWORK_BLEED, pack.presentation.background)
        assertEquals(listOf(ThemeEffectKind.GRAIN, ThemeEffectKind.VIGNETTE), pack.presentation.effects.map { it.kind })
        assertEquals(ThemeIconSet.SYMBOLS, pack.presentation.icons.set)
        assertEquals(zuneJson.length <= 64 * 1024, true)
        assertTrue(ThemePackReader.parse(zuneJson).isFailure)
        assertTrue(ThemePackReader.parse(zuneJson.replace("\"name\": \"Zune\"", "\"name\": \"Zune\", \"css\": \"*{}\"")).isFailure)
    }

    @Test
    fun roundTripsThroughJsonAndRejectsInvalidPacks() {
        val pack = ThemePackReader.parse(safeZuneJson).getOrThrow()
        val reparsed = ThemePackReader.parse(ThemePackReader.toJson(pack)).getOrThrow()
        assertEquals(pack, reparsed)

        assertTrue(ThemePackReader.parse(zuneJson.replace("#FF4F00", "orange")).isFailure)
        assertTrue(ThemePackReader.parse("{}").isFailure)
        assertTrue(ThemePackReader.parse(zuneJson.replace("relay.theme.zune", "Bad Id!")).isFailure)
    }

    @Test
    fun catalogArtifactMustMatchIdAndVersionAndCannotReferenceAssets() {
        assertTrue(ThemePackReader.parseCatalogArtifact(safeZuneJson, "relay.theme.zune", "1.0.0").isSuccess)
        assertTrue(ThemePackReader.parseCatalogArtifact(safeZuneJson, "other.theme", "1.0.0").isFailure)
        assertTrue(ThemePackReader.parseCatalogArtifact(safeZuneJson, "relay.theme.zune", "2.0.0").isFailure)
        assertTrue(
            ThemePackReader.parseCatalogArtifact(
                safeZuneJson.replace("\"background\": \"ARTWORK_BLEED\"", "\"background\": \"ASSET\", \"backgroundAsset\": \"image.png\""),
                "relay.theme.zune",
                "1.0.0",
            ).isFailure,
        )
    }
}
