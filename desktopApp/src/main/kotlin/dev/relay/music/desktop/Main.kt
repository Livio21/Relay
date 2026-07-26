package dev.relay.music.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.relay.music.LibraryUiState
import dev.relay.music.RelayApp
import dev.relay.music.RelayAppActions
import dev.relay.music.RelayAppState
import dev.relay.music.lastfm.LastFmConnectionState
import dev.relay.music.model.Track
import dev.relay.music.model.TrackFlags
import dev.relay.music.settings.RelaySettings
import java.io.File

fun main() = application {
    val tracks = localTracks()
    val engine = DesktopPlayerEngine()
    Window(onCloseRequest = { engine.release(); exitApplication() }, title = "Relay") {
        val playback by engine.state.collectAsState()
        RelayApp(
            state = RelayAppState(
                tracks = tracks,
                playback = playback,
                library = LibraryUiState.Ready,
                favoriteTrackKeys = emptySet(),
                trackFlags = emptyMap<String, TrackFlags>(),
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
            ),
            actions = RelayAppActions(
                onTrackSelected = { track -> engine.setQueue(tracks, tracks.indexOf(track)) },
                onPlayPause = { if (playback.isPlaying) engine.pause() else engine.play() },
                onPrevious = engine::skipPrevious,
                onNext = engine::skipNext,
                onSeekTo = engine::seekTo,
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
                onSearchMetadata = { _, _, _ -> },
                onMetadataReviewIgnored = {},
                onLoadLyrics = {},
                onSaveLyrics = { _, _ -> },
                onFetchLyrics = {},
                onLastFmAction = {},
                onBackActionChanged = {},
            ),
        )
    }
}

private fun localTracks(): List<Track> {
    val music = File(System.getProperty("user.home"), "Music")
    return music.walkTopDown().filter { file -> file.isFile && file.extension.lowercase() in setOf("wav", "mp3", "flac") }.map { file ->
        Track(file.toURI().toString(), "desktop-local", file.toURI().toString(), file.nameWithoutExtension, "")
    }.toList().sortedBy { it.title.lowercase() }
}
