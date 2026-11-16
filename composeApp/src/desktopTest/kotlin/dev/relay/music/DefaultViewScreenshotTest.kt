package dev.relay.music

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import java.io.File
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.Density
import dev.relay.music.lastfm.LastFmConnectionState
import dev.relay.music.model.ListeningEvent
import dev.relay.music.model.Track
import dev.relay.music.playback.PlaybackState
import dev.relay.music.settings.RelaySettings
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders the default shell offscreen so layout regressions in the Metro hub and player are
 * visible in CI artifacts without a device or window server. Not a pixel comparison.
 */
class DefaultViewScreenshotTest {
    private val tracks = listOf(
        Track("1", "local", "file:///one.wav", "The Long Way Home (Live at the Harbor)", "Harbor Lights", "Low Tide", durationMs = 222_000),
        Track("2", "local", "file:///two.wav", "Slow Return", "Harbor Lights", "Low Tide", durationMs = 258_000),
        Track("3", "local", "file:///three.wav", "Field Notes", "North Signal", "Field Notes", durationMs = 201_000),
    )

    private fun sampleState() = RelayAppState(
            tracks = tracks,
            playback = PlaybackState(queue = tracks, currentIndex = 0, isPlaying = true, positionMs = 72_000, durationMs = 222_000),
            library = LibraryUiState.Ready,
            favoriteTrackKeys = emptySet(),
            trackFlags = emptyMap(),
            settings = RelaySettings(),
            playlists = emptyList(),
            metadataCandidates = emptyList(),
            metadataSearchMessage = null,
            metadataCandidateTrackKey = null,
            metadataIgnoredTrackKeys = emptySet(),
            lyricsText = null,
            lyricsTrackKey = null,
            lyricsMessage = null,
            lastFmConnectionState = LastFmConnectionState.SETUP_REQUIRED,
            lastFmErrorMessage = null,
            repositoryCatalogs = emptyMap(),
            repositoryMessages = emptyMap(),
            listeningEvents = listOf(
                ListeningEvent("local", "2", 3_000),
                ListeningEvent("local", "1", 2_000),
                ListeningEvent("local", "3", 1_000),
            ),
            nowEpochMs = 10_000,
        )

    private fun sampleActions() = RelayAppActions(
            onTrackSelected = {},
            onPlayPause = {},
            onPrevious = {},
            onNext = {},
            onSeekTo = {},
            onRetry = {},
            onFavoriteToggle = {},
            onTrackFlagsChange = { _, _ -> },
            onResumeQueueChange = {},
            onChooseStorageRoot = {},
            onBackupExport = {},
            onBackupImport = {},
            onBackupScheduleChange = {},
            onAutoBackupExpiryChange = {},
            onCreatePlaylist = {},
            onAddToPlaylist = { _, _ -> },
            onSaveMetadataOverride = { _, _ -> },
            onSearchMetadata = { _, _, _, _ -> },
            onMetadataReviewIgnored = {},
            onLoadLyrics = {},
            onSaveLyrics = { _, _ -> },
            onFetchLyrics = {},
            onLastFmAction = {},
            onBackActionChanged = {},
        )

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun rendersDefaultShell() {
        val image = renderComposeScene(width = 780, height = 1688, density = Density(2f)) {
            RelayApp(state = sampleState(), actions = sampleActions())
        }
        write(image, "relay-default.png")
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun rendersInsightsShell() {
        val image = renderComposeScene(width = 780, height = 1688, density = Density(2f)) {
            RelayAppContent(state = sampleState(), actions = sampleActions(), initialView = INSIGHTS_VIEW)
        }
        write(image, "relay-insights.png")
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun rendersSourcesShell() {
        val image = renderComposeScene(width = 780, height = 1688, density = Density(2f)) {
            RelayAppContent(state = sampleState(), actions = sampleActions(), initialView = EXTENSIONS_VIEW)
        }
        write(image, "relay-sources.png")
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun rendersSettingsShell() {
        val image = renderComposeScene(width = 780, height = 1688, density = Density(2f)) {
            RelayAppContent(
                state = sampleState(),
                actions = sampleActions(),
                initialView = SETTINGS_VIEW,
            )
        }
        write(image, "relay-settings.png")
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun rendersSectionsMenu() {
        val image = renderComposeScene(width = 780, height = 1688, density = Density(2f)) {
            MetroSectionMenu(
                sections = METRO_SECTIONS,
                currentIndex = 1,
                artworkUri = null,
                signature = "Filan Fisteku",
                onSelect = {},
                onClose = {},
            )
        }
        write(image, "relay-sections-menu.png")
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun sectionsMenuClosesFromArtArea() {
        var closed = false
        val scene = ImageComposeScene(width = 780, height = 1688, density = Density(2f)) {
            MetroSectionMenu(
                sections = METRO_SECTIONS,
                currentIndex = 1,
                artworkUri = null,
                signature = "Filan Fisteku",
                onSelect = {},
                onClose = { closed = true },
            )
        }
        scene.render()
        scene.sendPointerEvent(PointerEventType.Press, Offset(390f, 220f))
        scene.sendPointerEvent(PointerEventType.Release, Offset(390f, 220f))
        assertTrue(closed)
        scene.close()
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun rendersLibraryShell() {
        val image = renderComposeScene(width = 780, height = 1688, density = Density(2f)) {
            RelayAppContent(state = sampleState(), actions = sampleActions(), initialView = LIBRARY_VIEW)
        }
        write(image, "relay-library.png")
    }

    private fun write(image: org.jetbrains.skia.Image, name: String) {
        val data = image.encodeToData(EncodedImageFormat.PNG)
        assertTrue(data != null && data.bytes.isNotEmpty())
        val output = File("build/screenshots/$name")
        output.parentFile.mkdirs()
        output.writeBytes(data.bytes)
    }
}
