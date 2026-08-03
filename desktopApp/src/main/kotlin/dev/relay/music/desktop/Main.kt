package dev.relay.music.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.relay.music.LibraryUiState
import dev.relay.music.RelayApp
import dev.relay.music.RelayAppActions
import dev.relay.music.RelayAppState
import dev.relay.music.lastfm.LastFmConnectionState
import dev.relay.music.model.Track
import dev.relay.music.model.TrackFlags
import dev.relay.music.playback.QueueEdit
import dev.relay.music.playback.appendToQueue
import dev.relay.music.playback.moveInQueue
import dev.relay.music.playback.playNextInQueue
import dev.relay.music.playback.removeFromQueue
import dev.relay.music.playback.shuffledQueue
import dev.relay.music.settings.RelaySettings
import dev.relay.music.settings.activeShuffleProfile
import java.io.File
import java.util.prefs.Preferences
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun main() = application {
    val engine = remember { DesktopPlayerEngine() }
    var musicRoot by remember { mutableStateOf(savedMusicRoot()) }
    var libraryRevision by remember { mutableStateOf(0) }
    var settings by remember {
        mutableStateOf(RelaySettings(storageRootUri = musicRoot?.toURI()?.toString()))
    }
    val library by produceState<DesktopLibrary>(DesktopLibrary.Loading, musicRoot, libraryRevision) {
        value = musicRoot?.let { root ->
            withContext(Dispatchers.IO) {
                runCatching { DesktopLibrary.Ready(localTracks(root)) }
                    .getOrElse { DesktopLibrary.Failed(it.message ?: "Could not read the music folder.") }
            }
        } ?: DesktopLibrary.FolderRequired
    }

    Window(onCloseRequest = { engine.release(); exitApplication() }, title = "Relay") {
        val playback by engine.state.collectAsState()
        val tracks = (library as? DesktopLibrary.Ready)?.tracks.orEmpty()

        LaunchedEffect(settings.playbackSpeed) {
            engine.setPlaybackSpeed(settings.playbackSpeed)
        }

        fun applyQueueEdit(edit: QueueEdit?) {
            edit ?: return
            val oldTrack = playback.currentTrack
            val newTrack = edit.queue.getOrNull(edit.currentIndex)
            val keepPosition = oldTrack != null && newTrack?.sourceId == oldTrack.sourceId && newTrack.id == oldTrack.id
            engine.setQueue(
                edit.queue,
                edit.currentIndex,
                playWhenReady = playback.isPlaying,
                startPositionMs = if (keepPosition) playback.positionMs else 0,
            )
        }

        fun reshuffle() {
            if (playback.queue.size < 2) return
            val queue = shuffledQueue(playback.queue, playback.currentIndex, settings.activeShuffleProfile())
            engine.setQueue(queue, 0, playback.isPlaying, playback.positionMs)
        }

        RelayApp(
            state = RelayAppState(
                tracks = tracks,
                playback = playback,
                library = when (val result = library) {
                    DesktopLibrary.Loading -> LibraryUiState.Ready
                    DesktopLibrary.FolderRequired -> LibraryUiState.StorageRequired
                    is DesktopLibrary.Failed -> LibraryUiState.Error(result.message)
                    is DesktopLibrary.Ready -> LibraryUiState.Ready
                },
                favoriteTrackKeys = emptySet(),
                trackFlags = emptyMap<String, TrackFlags>(),
                settings = settings,
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
                onTrackSelected = { track ->
                    tracks.indexOfFirst { it.sourceId == track.sourceId && it.id == track.id }
                        .takeIf { it >= 0 }
                        ?.let { engine.setQueue(tracks, it) }
                },
                onPlayPause = { if (playback.isPlaying) engine.pause() else engine.play() },
                onPrevious = engine::skipPrevious,
                onNext = engine::skipNext,
                onSeekTo = engine::seekTo,
                onRetry = { libraryRevision++ },
                onFavoriteToggle = {},
                onTrackFlagsChange = { _, _ -> },
                onResumeQueueChange = { settings = settings.copy(resumeQueue = it) },
                onChooseStorageRoot = {
                    JFileChooser(musicRoot).apply {
                        dialogTitle = "Choose Relay music folder"
                        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        isAcceptAllFileFilterUsed = false
                    }.takeIf { it.showOpenDialog(window) == JFileChooser.APPROVE_OPTION }
                        ?.selectedFile
                        ?.takeIf(File::isDirectory)
                        ?.absoluteFile
                        ?.let { selected ->
                            saveMusicRoot(selected)
                            musicRoot = selected
                            settings = settings.copy(storageRootUri = selected.toURI().toString())
                            engine.setQueue(emptyList(), -1, playWhenReady = false)
                        }
                },
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
                onAudioSettingsChange = { updated -> settings = updated },
                onShuffleEnabledChange = { enabled ->
                    engine.setShuffleEnabled(enabled)
                    if (enabled && !playback.shuffleEnabled) reshuffle()
                },
                onRepeatModeChange = engine::setRepeatMode,
                onShuffleQueue = ::reshuffle,
                onPlayQueueIndex = engine::seekToIndex,
                onRemoveQueueIndex = { index ->
                    applyQueueEdit(removeFromQueue(playback.queue, playback.currentIndex, index))
                },
                onMoveQueueIndex = { from, to ->
                    applyQueueEdit(moveInQueue(playback.queue, playback.currentIndex, from, to))
                },
                onClearQueue = { engine.setQueue(emptyList(), -1, playWhenReady = false) },
                onPlayNext = { track ->
                    applyQueueEdit(playNextInQueue(playback.queue, playback.currentIndex, track))
                },
                onEnqueue = { track ->
                    applyQueueEdit(appendToQueue(playback.queue, playback.currentIndex, track))
                },
                onApplyThemePack = { id -> settings = settings.copy(activeThemePackId = id) },
                onRemoveThemePack = { id ->
                    settings = settings.copy(
                        themePacks = settings.themePacks.filterNot { it.id == id },
                        activeThemePackId = settings.activeThemePackId.takeUnless { it == id },
                    )
                },
                onWallpaperSettingsChange = { settings = it },
            ),
        )
    }
}

private sealed interface DesktopLibrary {
    data object Loading : DesktopLibrary
    data object FolderRequired : DesktopLibrary
    data class Ready(val tracks: List<Track>) : DesktopLibrary
    data class Failed(val message: String) : DesktopLibrary
}

private fun localTracks(music: File): List<Track> {
    require(music.isDirectory) { "The selected music folder is unavailable." }
    return music.walkTopDown()
        .onFail { _, error -> throw error }
        .filter { file -> file.isFile && file.extension.lowercase() in SUPPORTED_EXTENSIONS }
        .map { file ->
            val uri = file.absoluteFile.toURI().toString()
            Track(uri, "desktop-local", uri, file.nameWithoutExtension, "")
        }
        .toList()
        .sortedBy { it.title.lowercase() }
}

private fun savedMusicRoot(): File? {
    val saved = preferences.get(MUSIC_ROOT_KEY, null)?.let(::File)?.takeIf(File::isDirectory)
    return saved ?: File(System.getProperty("user.home"), "Music").takeIf(File::isDirectory)
}

private fun saveMusicRoot(root: File) {
    preferences.put(MUSIC_ROOT_KEY, root.absolutePath)
}

private val preferences: Preferences by lazy { Preferences.userRoot().node("dev/relay/music") }
private val SUPPORTED_EXTENSIONS = setOf("wav", "mp3", "flac")
private const val MUSIC_ROOT_KEY = "musicRoot"
