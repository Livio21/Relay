package dev.relay.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import dev.relay.music.extension.ExtensionCatalogEntry
import dev.relay.music.extension.ExtensionSourceResults
import dev.relay.music.extension.ExtensionDownloadProgress
import dev.relay.music.extension.RemoteTrackDownloadProgress
import dev.relay.music.extension.InstalledExtension
import dev.relay.music.extension.SourceBrowseRequest
import dev.relay.music.extension.SourceSettingDefinition
import dev.relay.music.extension.RepositoryDescriptor
import dev.relay.music.lastfm.LastFmConnectionState
import dev.relay.music.model.MetadataCandidate
import dev.relay.music.model.MetadataOverride
import dev.relay.music.model.ListeningEvent
import dev.relay.music.model.OfflineDownload
import dev.relay.music.model.Playlist
import dev.relay.music.model.Track
import dev.relay.music.model.TrackFlags
import dev.relay.music.playback.PlaybackState
import dev.relay.music.settings.BackupSchedule
import dev.relay.music.settings.RelaySettings
import dev.relay.music.ui.applyThemePack

/** Everything Relay renders. Platform hosts assemble this from their own services and storage. */
data class RelayAppState(
    val tracks: List<Track>,
    val playback: PlaybackState,
    val library: LibraryUiState,
    val favoriteTrackKeys: Set<String>,
    val trackFlags: Map<String, TrackFlags>,
    val settings: RelaySettings,
    val playlists: List<Playlist>,
    /** Resolved tracks per playlist, loaded when a playlist is opened. */
    val playlistTracks: Map<Long, List<Track>> = emptyMap(),
    val metadataCandidates: List<MetadataCandidate>,
    val metadataSearchMessage: String?,
    val metadataCandidateTrackKey: String?,
    val metadataIgnoredTrackKeys: Set<String>,
    val lyricsText: String?,
    val lyricsTrackKey: String?,
    val lyricsMessage: String?,
    val lastFmConnectionState: LastFmConnectionState,
    val lastFmErrorMessage: String?,
    val repositoryCatalogs: Map<String, List<ExtensionCatalogEntry>>,
    val repositoryMessages: Map<String, String>,
    val importedRepository: RepositoryDescriptor? = null,
    val repositoryImportMessage: String? = null,
    val repositoryImportVersion: Long = 0L,
    /** Bumped when a `relay://add-repo` link arrives, so the UI opens the importer. */
    val openRepositoryImportVersion: Long = 0L,
    val extensionSourceResults: List<ExtensionSourceResults> = emptyList(),
    val extensionSourceMessage: String? = null,
    /** Loaded source preference schemas, keyed by extension ID. */
    val sourceSettingSchemas: Map<String, List<SourceSettingDefinition>> = emptyMap(),
    val extensionDownload: ExtensionDownloadProgress? = null,
    val remoteTrackDownload: RemoteTrackDownloadProgress? = null,
    val downloadedRemoteTrackKeys: Set<String> = emptySet(),
    val offlineDownloads: List<OfflineDownload> = emptyList(),
    /** Recorded plays behind the insights view. */
    val listeningEvents: List<ListeningEvent> = emptyList(),
    /** Supplied by the host so insight ranges are computed against a real clock. */
    val nowEpochMs: Long = 0L,
)

/** User intents emitted by the shared UI. Platform hosts decide how each intent is fulfilled. */
data class RelayAppActions(
    val onTrackSelected: (Track) -> Unit,
    val onPlayPause: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onSeekTo: (Long) -> Unit,
    val onRetry: () -> Unit,
    val onFavoriteToggle: (Track) -> Unit,
    val onTrackFlagsChange: (Track, TrackFlags) -> Unit,
    val onResumeQueueChange: (Boolean) -> Unit,
    val onChooseStorageRoot: () -> Unit,
    val onBackupExport: () -> Unit,
    val onBackupImport: () -> Unit,
    val onBackupScheduleChange: (BackupSchedule) -> Unit,
    val onAutoBackupExpiryChange: (Int) -> Unit,
    val onCreatePlaylist: (String) -> Unit,
    val onAddToPlaylist: (Long, Track) -> Unit,
    val onCreateAndAddToPlaylist: (String, Track) -> Unit = { _, _ -> },
    val onOpenPlaylist: (Long) -> Unit = {},
    val onPlayPlaylist: (Long, Int) -> Unit = { _, _ -> },
    val onRemovePlaylistEntry: (Long, Int) -> Unit = { _, _ -> },
    val onMovePlaylistEntry: (Long, Int, Int) -> Unit = { _, _, _ -> },
    val onRenamePlaylist: (Long, String) -> Unit = { _, _ -> },
    val onDeletePlaylist: (Long) -> Unit = {},
    val onSaveMetadataOverride: (Track, MetadataOverride) -> Unit,
    val onSearchMetadata: (Track, String, String) -> Unit,
    val onMetadataReviewIgnored: (Track) -> Unit,
    val onLoadLyrics: (Track) -> Unit,
    val onSaveLyrics: (Track, String) -> Unit,
    val onFetchLyrics: (Track) -> Unit,
    val onLastFmAction: () -> Unit,
    val onBackActionChanged: ((() -> Unit)?) -> Unit,
    val onDebugScrobble: (() -> Unit)? = null,
    val onAddTrustedRepository: (RepositoryDescriptor) -> Unit = {},
    val onImportRepository: (String) -> Unit = {},
    val onRemoveTrustedRepository: (String) -> Unit = {},
    val onRefreshRepository: (RepositoryDescriptor) -> Unit = {},
    val onInstallExtension: (RepositoryDescriptor, ExtensionCatalogEntry) -> Unit = { _, _ -> },
    val onSetExtensionEnabled: (RepositoryDescriptor, ExtensionCatalogEntry, Boolean) -> Unit = { _, _, _ -> },
    val onUninstallExtension: (InstalledExtension) -> Unit = {},
    val onSearchExtensionSources: (SourceBrowseRequest) -> Unit = {},
    val onLoadSourceSettings: (String) -> Unit = {},
    val onSourceSettingsChange: (String, Map<String, String>) -> Unit = { _, _ -> },
    val onRefreshExtensions: () -> Unit = {},
    val onDownloadRemoteTrack: (Track) -> Unit = {},
    val onDeleteDownload: (String, String) -> Unit = { _, _ -> },
    val onDeleteAllDownloads: () -> Unit = {},
    val onAudioSettingsChange: ((RelaySettings) -> Unit)? = null,
    val onShuffleEnabledChange: (Boolean) -> Unit = {},
    val onShuffleQueue: () -> Unit = {},
    val onPlayQueueIndex: (Int) -> Unit = {},
    val onRemoveQueueIndex: (Int) -> Unit = {},
    val onMoveQueueIndex: (Int, Int) -> Unit = { _, _ -> },
    val onClearQueue: () -> Unit = {},
    val onPlayNext: (Track) -> Unit = {},
    val onEnqueue: (Track) -> Unit = {},
    val onPickShuffleSeed: ((String) -> Unit)? = null,
    val onImportThemePack: (() -> Unit)? = null,
    val onApplyThemePack: (String?) -> Unit = {},
    val onRemoveThemePack: (String) -> Unit = {},
)

@Composable
fun RelayApp(state: RelayAppState, actions: RelayAppActions, modifier: Modifier = Modifier) {
    SideEffect {
        applyThemePack(state.settings.themePacks.firstOrNull { it.id == state.settings.activeThemePackId })
    }
    RelayAppContent(state, actions, modifier)
}
