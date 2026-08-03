package dev.relay.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.relay.music.model.metadataHealth
import kotlinx.coroutines.launch
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import dev.relay.music.model.Track
import dev.relay.music.model.MetadataHealth
import dev.relay.music.settings.activeShuffleProfile
import dev.relay.music.ui.RelayColors
import dev.relay.music.ui.RelayPresentation
import dev.relay.music.ui.RelayTheme
import dev.relay.music.ui.RelayType
import dev.relay.music.extension.ThemeBackground

sealed interface LibraryUiState {
    data object Ready : LibraryUiState
    data object StorageRequired : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

@Composable
internal fun RelayAppContent(
    state: RelayAppState,
    actions: RelayAppActions,
    modifier: Modifier = Modifier,
) {
    var navigation by remember { mutableStateOf(RelayNavigationState()) }
    val destination = navigation.destination
    val reviewTrack = destination.reviewTrack
    val selectedPlaylistId = destination.selectedPlaylistId
    val settingsSubmenu = destination.settingsSubmenu
    val extensionsSubmenu = destination.extensionsSubmenu
    val extensionsTab = destination.extensionsTab
    val selectedCatalogExtension = destination.selectedCatalogExtension
    val browsedExtensionId = destination.browsedExtensionId
    val queueOpen = destination.queueOpen
    val lyricsOpen = destination.lyricsOpen
    val playerOptionsOpen = destination.playerOptionsOpen
    val openPlaylist = state.playlists.firstOrNull { it.id == selectedPlaylistId }
    var libraryQuery by remember { mutableStateOf("") }
    var librarySort by remember { mutableStateOf(LibrarySort.TITLE) }
    val visibleTracks = libraryView(state.tracks, state.trackFlags)
    val listedTracks = libraryView(state.tracks, state.trackFlags, libraryQuery, librarySort)
    val tracksNeedingMetadata = visibleTracks.filter {
        it.metadataHealth() == MetadataHealth.NEEDS_REVIEW && trackKey(it) !in state.metadataIgnoredTrackKeys
    }
    val pagerState = rememberPagerState(initialPage = PAGER_START_PAGE) { Int.MAX_VALUE }
    val navigationScope = rememberCoroutineScope()
    val currentView = pagerState.currentPage % PAGE_TITLES.size
    var pagerTarget by remember { mutableStateOf<Int?>(null) }
    fun movePagerTo(view: Int) {
        val delta = pagerPageDelta(currentView, view)
        if (delta != 0) {
            pagerTarget = view
            navigationScope.launch {
                try {
                    pagerState.animateScrollToPage(pagerState.currentPage + delta)
                } finally {
                    if (pagerTarget == view) pagerTarget = null
                }
            }
        }
    }
    fun navigateTo(next: RelayDestination) {
        val updated = navigation.navigate(next)
        if (updated === navigation) return
        navigation = updated
        movePagerTo(next.view)
    }
    fun goBack() {
        val updated = navigation.back() ?: return
        navigation = updated
        movePagerTo(updated.destination.view)
    }
    val isSubmenu = destination.hasSubState
    LaunchedEffect(pagerState.isScrollInProgress, currentView, pagerTarget) {
        if (pagerTarget == null && !pagerState.isScrollInProgress && currentView != destination.view) {
            navigateTo(destination.forView(currentView))
        }
    }
    // A repository link handed to Relay from outside opens the importer, but never trusts it:
    // the descriptor and its signing key still have to be reviewed and accepted by hand.
    LaunchedEffect(state.openRepositoryImportVersion) {
        if (state.openRepositoryImportVersion > 0L) {
            navigateTo(destination.copy(view = SETTINGS_VIEW, settingsSubmenu = SettingsSubmenu.REPOSITORIES))
        }
    }
    SideEffect { actions.onBackActionChanged(if (navigation.canGoBack) ::goBack else null) }

    RelayTheme(backgroundArtworkUri = state.playback.currentTrack?.artworkUri) {
        BoxWithConstraints(
            modifier = modifier.fillMaxSize().background(
                if (RelayPresentation.background == ThemeBackground.NONE) RelayColors.Ink
                else RelayColors.Ink.copy(alpha = 0.72f),
            ),
        ) {
        val landscape = maxWidth > maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            val immersiveCoverFlow = landscape && currentView == NOW_PLAYING_VIEW && !isSubmenu
            if (!immersiveCoverFlow) {
            PageHeading(
                previousTitle = PAGE_TITLES[(currentView + PAGE_TITLES.size - 1) % PAGE_TITLES.size],
                title = PAGE_TITLES[currentView],
                nextTitle = PAGE_TITLES[(currentView + 1) % PAGE_TITLES.size],
                onBack = if (isSubmenu) ::goBack else null,
                onPrevious = { navigateTo(destination.forView((currentView + PAGE_TITLES.size - 1) % PAGE_TITLES.size)) },
                onNext = { navigateTo(destination.forView((currentView + 1) % PAGE_TITLES.size)) },
            )
            Rule()
            }
            Box(modifier = Modifier.weight(1f)) {
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(bottom = if (immersiveCoverFlow) 0.dp else 78.dp),
                    userScrollEnabled = !isSubmenu,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                when (page % PAGE_TITLES.size) {
                    0 -> when (state.library) {
                    LibraryUiState.StorageRequired -> LibraryMessage(
                        message = "Choose a Relay folder before indexing music.",
                        actionLabel = "CHOOSE FOLDER",
                        onAction = actions.onChooseStorageRoot,
                    )
                    is LibraryUiState.Error -> LibraryMessage(
                        message = state.library.message,
                        actionLabel = "TRY AGAIN",
                        onAction = actions.onRetry,
                    )

                    LibraryUiState.Ready -> if (state.tracks.isEmpty()) {
                        EmptyLibrary()
                    } else {
                        if (reviewTrack == null) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                if (tracksNeedingMetadata.isNotEmpty()) {
                                    MetadataBanner(onReview = {
                                        navigateTo(destination.copy(view = 0, reviewTrack = tracksNeedingMetadata.first()))
                                    })
                                    Rule()
                                }
                                LibraryControls(
                                    query = libraryQuery,
                                    sort = librarySort,
                                    resultCount = listedTracks.size,
                                    onQueryChange = { libraryQuery = it },
                                    onSortChange = { librarySort = it },
                                )
                                Rule()
                                TrackList(
                                    tracks = listedTracks,
                                    playbackState = state.playback,
                                    favoriteTrackKeys = state.favoriteTrackKeys,
                                    trackFlags = state.trackFlags,
                                    onTrackSelected = actions.onTrackSelected,
                                    onFavoriteToggle = actions.onFavoriteToggle,
                                    onTrackFlagsChange = actions.onTrackFlagsChange,
                                    onMetadataReview = { navigateTo(destination.copy(view = 0, reviewTrack = it)) },
                                    playlists = state.playlists,
                                    onAddToPlaylist = actions.onAddToPlaylist,
                                    onCreateAndAddToPlaylist = actions.onCreateAndAddToPlaylist,
                                    onPlayNext = actions.onPlayNext,
                                    onEnqueue = actions.onEnqueue,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else {
                            MetadataReview(
                                track = reviewTrack,
                                candidates = state.metadataCandidates.takeIf { trackKey(reviewTrack) == state.metadataCandidateTrackKey }.orEmpty(),
                                searchMessage = state.metadataSearchMessage.takeIf { trackKey(reviewTrack) == state.metadataCandidateTrackKey },
                                onCancel = ::goBack,
                                onSearch = actions.onSearchMetadata,
                                onIgnore = {
                                    actions.onMetadataReviewIgnored(reviewTrack)
                                    goBack()
                                },
                                onSave = { override ->
                                    actions.onSaveMetadataOverride(reviewTrack, override)
                                    goBack()
                                },
                            )
                        }
                    }
                    }
                    NOW_PLAYING_VIEW -> if (landscape && !queueOpen) {
                        val playingQueue = state.playback.queue
                        val browseTracks = playingQueue.ifEmpty { listedTracks }
                        CoverFlow(
                            tracks = browseTracks,
                            selectedIndex = if (playingQueue.isEmpty()) {
                                val playingKey = state.playback.currentTrack?.let(::trackKey)
                                browseTracks.indexOfFirst { trackKey(it) == playingKey }
                            } else {
                                state.playback.currentIndex
                            },
                            isPlaying = state.playback.isPlaying,
                            onPlayPause = actions.onPlayPause,
                            onPrevious = actions.onPrevious,
                            onNext = actions.onNext,
                            onPlay = { index ->
                                if (playingQueue.isEmpty()) {
                                    browseTracks.getOrNull(index)?.let(actions.onTrackSelected)
                                } else {
                                    actions.onPlayQueueIndex(index)
                                }
                            },
                        )
                    } else if (queueOpen) {
                        QueueScreen(
                            playbackState = state.playback,
                            shuffleProfileName = state.settings.activeShuffleProfile().name,
                            onPlayIndex = actions.onPlayQueueIndex,
                            onRemoveIndex = actions.onRemoveQueueIndex,
                            onMoveIndex = actions.onMoveQueueIndex,
                            onShuffleEnabledChange = actions.onShuffleEnabledChange,
                            onRepeatModeChange = actions.onRepeatModeChange,
                            onReshuffle = actions.onShuffleQueue,
                            onOpenShuffleSettings = {
                                navigateTo(destination.copy(view = SETTINGS_VIEW, settingsSubmenu = SettingsSubmenu.PLAYBACK))
                            },
                            onClear = actions.onClearQueue,
                        )
                    } else {
                        Spacer(Modifier.fillMaxSize())
                    }
                    PLAYLISTS_VIEW -> {
                        if (openPlaylist == null) {
                            PlaylistScreen(
                                playlists = state.playlists,
                                onCreatePlaylist = actions.onCreatePlaylist,
                                onPlayPlaylist = { playlistId -> actions.onPlayPlaylist(playlistId, 0) },
                                onDeletePlaylist = actions.onDeletePlaylist,
                            ) { playlistId ->
                                actions.onOpenPlaylist(playlistId)
                                navigateTo(destination.copy(view = PLAYLISTS_VIEW, selectedPlaylistId = playlistId))
                            }
                        } else {
                            PlaylistDetailScreen(
                                playlist = openPlaylist,
                                tracks = state.playlistTracks[openPlaylist.id],
                                playingTrackKey = state.playback.currentTrack?.let(::trackKey),
                                onPlayTrack = { index -> actions.onPlayPlaylist(openPlaylist.id, index) },
                                onRemoveEntry = { index -> actions.onRemovePlaylistEntry(openPlaylist.id, index) },
                                onMoveEntry = { index, delta -> actions.onMovePlaylistEntry(openPlaylist.id, index, delta) },
                                onRename = { name -> actions.onRenamePlaylist(openPlaylist.id, name) },
                                onDelete = {
                                    actions.onDeletePlaylist(openPlaylist.id)
                                    goBack()
                                },
                                onAddTracks = { navigateTo(destination.forView(LIBRARY_VIEW)) },
                            )
                        }
                    }
                    INSIGHTS_VIEW -> InsightsScreen(
                        events = state.listeningEvents,
                        nowEpochMs = state.nowEpochMs,
                        onImportLastFmHistory = actions.onImportLastFmHistory.takeIf {
                            state.lastFmConnectionState == dev.relay.music.lastfm.LastFmConnectionState.CONNECTED
                        },
                        importMessage = state.lastFmHistoryImportMessage,
                        profile = state.localProfile,
                        onProfileNameChange = actions.onProfileNameChange,
                        onUnlinkLastFmHistory = actions.onUnlinkLastFmHistory,
                        chartSpecs = state.albumChartSpecs,
                        onCreateAlbumChart = actions.onCreateAlbumChart,
                        onRemoveAlbumChart = actions.onRemoveAlbumChart,
                        onExportAlbumChart = actions.onExportAlbumChart,
                    )
                    EXTENSIONS_VIEW -> ExtensionsScreen(
                        settings = state.settings,
                        repositoryCatalogs = state.repositoryCatalogs,
                        repositoryMessages = state.repositoryMessages,
                        onAddTrustedRepository = actions.onAddTrustedRepository,
                        onImportRepository = actions.onImportRepository,
                        onRemoveTrustedRepository = actions.onRemoveTrustedRepository,
                        onRefreshRepository = actions.onRefreshRepository,
                        onInstallExtension = actions.onInstallExtension,
                        onSetExtensionEnabled = actions.onSetExtensionEnabled,
                        onUninstallExtension = actions.onUninstallExtension,
                        importedRepository = state.importedRepository,
                        repositoryImportMessage = state.repositoryImportMessage,
                        repositoryImportVersion = state.repositoryImportVersion,
                        submenu = extensionsSubmenu,
                        selectedTab = extensionsTab,
                        selectedExtension = selectedCatalogExtension,
                        onSubmenuChange = { submenu ->
                            navigateTo(destination.copy(
                                view = EXTENSIONS_VIEW,
                                extensionsSubmenu = submenu,
                                selectedCatalogExtension = null,
                                browsedExtensionId = if (submenu == ExtensionsSubmenu.SOURCE_SEARCH) null else browsedExtensionId,
                            ))
                        },
                        onTabChange = { tab ->
                            navigateTo(destination.copy(view = EXTENSIONS_VIEW, extensionsTab = tab))
                        },
                        onExtensionSelected = { extension ->
                            navigateTo(destination.copy(
                                view = EXTENSIONS_VIEW,
                                extensionsSubmenu = ExtensionsSubmenu.DETAILS,
                                selectedCatalogExtension = extension,
                            ))
                        },
                        extensionSourceResults = state.extensionSourceResults,
                        extensionSourceMessage = state.extensionSourceMessage,
                        sourceSettingSchemas = state.sourceSettingSchemas,
                        onLoadSourceSettings = actions.onLoadSourceSettings,
                        onSourceSettingsChange = actions.onSourceSettingsChange,
                        onSearchExtensionSources = actions.onSearchExtensionSources,
                        onTrackSelected = actions.onTrackSelected,
                        extensionDownload = state.extensionDownload,
                        remoteTrackDownload = state.remoteTrackDownload,
                        downloadedRemoteTrackKeys = state.downloadedRemoteTrackKeys,
                        onRefreshExtensions = actions.onRefreshExtensions,
                        onDownloadRemoteTrack = actions.onDownloadRemoteTrack,
                        playlists = state.playlists,
                        onAddToPlaylist = actions.onAddToPlaylist,
                        onCreateAndAddToPlaylist = actions.onCreateAndAddToPlaylist,
                        onPlayNext = actions.onPlayNext,
                        onEnqueue = actions.onEnqueue,
                        browsedExtensionId = browsedExtensionId,
                        onBrowseExtension = { extensionId ->
                            navigateTo(destination.copy(
                                view = EXTENSIONS_VIEW,
                                extensionsSubmenu = ExtensionsSubmenu.SOURCE_SEARCH,
                                selectedCatalogExtension = null,
                                browsedExtensionId = extensionId,
                            ))
                        },
                        onOpenSupportUrl = actions.onOpenExternalUrl,
                    )
                    SETTINGS_VIEW -> SettingsScreen(
                        state.settings,
                        actions.onResumeQueueChange,
                        actions.onChooseStorageRoot,
                        actions.onBackupExport,
                        actions.onBackupImport,
                        onSyncExport = actions.onSyncExport,
                        onSyncImport = actions.onSyncImport,
                        syncConflicts = state.syncConflicts,
                        onDismissSyncConflict = actions.onDismissSyncConflict,
                        onUseReceivedSyncConflict = actions.onUseReceivedSyncConflict,
                        lanSync = state.lanSync,
                        pairedDevices = state.pairedDevices,
                        playTogether = state.playTogether,
                        onStartLanSyncHost = actions.onStartLanSyncHost,
                        onJoinLanSync = actions.onJoinLanSync,
                        onConfirmLanSync = actions.onConfirmLanSync,
                        onCancelLanSync = actions.onCancelLanSync,
                        onUnpairDevice = actions.onUnpairDevice,
                        onSelectMusicTransfer = actions.onSelectMusicTransfer,
                        onImportMusicTransfer = actions.onImportMusicTransfer,
                        onPrepareLanMusicTransfer = actions.onPrepareLanMusicTransfer,
                        onStartPlayTogetherHost = actions.onStartPlayTogetherHost,
                        onJoinPlayTogether = actions.onJoinPlayTogether,
                        onConfirmPlayTogether = actions.onConfirmPlayTogether,
                        onLeavePlayTogether = actions.onLeavePlayTogether,
                        onResyncPlayTogether = actions.onResyncPlayTogether,
                        onBackupScheduleChange = actions.onBackupScheduleChange,
                        onAutoBackupExpiryChange = actions.onAutoBackupExpiryChange,
                        state.lastFmConnectionState,
                        state.lastFmErrorMessage,
                        actions.onDebugScrobble,
                        actions.onLastFmAction,
                        actions.onAddTrustedRepository,
                        actions.onImportRepository,
                        actions.onRemoveTrustedRepository,
                        state.repositoryCatalogs,
                        state.repositoryMessages,
                        state.importedRepository,
                        state.repositoryImportMessage,
                        state.repositoryImportVersion,
                        actions.onRefreshRepository,
                        actions.onAudioSettingsChange,
                        onWallpaperSettingsChange = actions.onWallpaperSettingsChange,
                        onImportThemePack = actions.onImportThemePack,
                        onApplyThemePack = actions.onApplyThemePack,
                        onRemoveThemePack = actions.onRemoveThemePack,
                        onOpenAlbumWallpaperPicker = actions.onOpenAlbumWallpaperPicker,
                        submenu = settingsSubmenu,
                        onSubmenuChange = { submenu ->
                            navigateTo(destination.copy(view = SETTINGS_VIEW, settingsSubmenu = submenu))
                        },
                    )
                }
                }
                if (!immersiveCoverFlow) PlayerSurface(
                    layout = if (currentView == NOW_PLAYING_VIEW && !queueOpen && !landscape) PlayerLayout.EXPANDED else PlayerLayout.COMPACT,
                    playbackState = state.playback,
                    onPlayPause = actions.onPlayPause,
                    onPrevious = actions.onPrevious,
                    onNext = actions.onNext,
                    onSeekTo = actions.onSeekTo,
                    lyricsText = state.lyricsText.takeIf { state.playback.currentTrack?.let(::trackKey) == state.lyricsTrackKey },
                    lyricsMessage = state.lyricsMessage,
                    onLoadLyrics = actions.onLoadLyrics,
                    onSaveLyrics = actions.onSaveLyrics,
                    onFetchLyrics = actions.onFetchLyrics,
                    onViewSwipe = { delta -> navigateTo(destination.forView((currentView + delta + PAGE_TITLES.size) % PAGE_TITLES.size)) },
                    shuffleProfileName = state.settings.activeShuffleProfile().name,
                    onShuffleEnabledChange = actions.onShuffleEnabledChange,
                    onRepeatModeChange = actions.onRepeatModeChange,
                    onReshuffle = actions.onShuffleQueue,
                    onOpenShuffleSettings = {
                        navigateTo(destination.copy(view = SETTINGS_VIEW, settingsSubmenu = SettingsSubmenu.PLAYBACK))
                    },
                    onOpenQueue = { navigateTo(destination.copy(view = NOW_PLAYING_VIEW, queueOpen = true)) },
                    lyricsOpen = lyricsOpen,
                    onToggleLyrics = {
                        // Keep lyric actions visible after opening the overlay.
                        if (lyricsOpen) {
                            goBack()
                        } else {
                            navigateTo(destination.copy(view = NOW_PLAYING_VIEW, lyricsOpen = true, playerOptionsOpen = true))
                        }
                    },
                    optionsOpen = playerOptionsOpen,
                    onToggleOptions = {
                        if (playerOptionsOpen) {
                            goBack()
                        } else {
                            navigateTo(destination.copy(view = NOW_PLAYING_VIEW, playerOptionsOpen = true))
                        }
                    },
                    onOpenNowPlaying = { navigateTo(destination.forView(NOW_PLAYING_VIEW)) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                )
            }
        }
        }
    }
}

private val PAGE_TITLES = listOf("LIBRARY", "NOW PLAYING", "PLAYLISTS", "INSIGHTS", "EXTENSIONS", "SETTINGS")
internal const val LIBRARY_VIEW = 0
internal const val NOW_PLAYING_VIEW = 1
internal const val PLAYLISTS_VIEW = 2
internal const val INSIGHTS_VIEW = 3
internal const val EXTENSIONS_VIEW = 4
internal const val SETTINGS_VIEW = 5

/** Mid-range page that maps to NOW PLAYING, recomputed so adding a page cannot shift the start. */
private val PAGER_START_PAGE =
    (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % PAGE_TITLES.size) + NOW_PLAYING_VIEW

internal data class RelayDestination(
    val view: Int,
    val reviewTrack: Track? = null,
    val selectedPlaylistId: Long? = null,
    val settingsSubmenu: SettingsSubmenu? = null,
    val extensionsSubmenu: ExtensionsSubmenu? = null,
    val extensionsTab: ExtensionsTab = ExtensionsTab.SOURCES,
    val selectedCatalogExtension: CatalogExtension? = null,
    val browsedExtensionId: String? = null,
    val queueOpen: Boolean = false,
    val lyricsOpen: Boolean = false,
    val playerOptionsOpen: Boolean = false,
) {
    /** True when this destination is a sub-screen rather than a plain top-level page. */
    val hasSubState: Boolean
        get() = reviewTrack != null || settingsSubmenu != null || extensionsSubmenu != null || queueOpen || lyricsOpen || playerOptionsOpen ||
            (view == PLAYLISTS_VIEW && selectedPlaylistId != null)

    fun forView(view: Int) = copy(
        view = view,
        reviewTrack = null,
        selectedPlaylistId = null,
        settingsSubmenu = null,
        extensionsSubmenu = null,
        extensionsTab = ExtensionsTab.SOURCES,
        selectedCatalogExtension = null,
        browsedExtensionId = null,
        queueOpen = false,
        lyricsOpen = false,
        playerOptionsOpen = false,
    )
}

private const val MAX_RELAY_BACK_STACK = 64
private val ROOT_DESTINATION = RelayDestination(NOW_PLAYING_VIEW)

internal data class RelayNavigationState(
    val destination: RelayDestination = ROOT_DESTINATION,
    val backStack: List<RelayDestination> = emptyList(),
) {
    val canGoBack: Boolean
        get() = backStack.isNotEmpty() || destination != ROOT_DESTINATION

    fun navigate(next: RelayDestination): RelayNavigationState = when (next) {
        destination -> this
        else -> RelayNavigationState(next, (backStack + destination).takeLast(MAX_RELAY_BACK_STACK))
    }

    fun back(): RelayNavigationState? = when {
        backStack.isNotEmpty() -> RelayNavigationState(backStack.last(), backStack.dropLast(1))
        destination != ROOT_DESTINATION -> RelayNavigationState()
        else -> null
    }
}

internal fun pagerPageDelta(current: Int, target: Int): Int {
    val forward = (target - current + PAGE_TITLES.size) % PAGE_TITLES.size
    return if (forward > PAGE_TITLES.size / 2) forward - PAGE_TITLES.size else forward
}

@Composable
private fun PageHeading(
    previousTitle: String,
    title: String,
    nextTitle: String,
    onBack: (() -> Unit)?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().clipToBounds().padding(vertical = 16.dp)) {
        BasicText(
            text = if (onBack == null) previousTitle else "<- BACK",
            style = RelayType.Track.copy(color = if (onBack == null) RelayColors.Muted else RelayColors.Paper),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .heightIn(min = 48.dp)
                .then(if (onBack == null) Modifier.clickable(role = Role.Button, onClick = onPrevious) else Modifier.clickable(role = Role.Button, onClick = onBack))
                .padding(start = 16.dp, top = 15.dp),
        )
        BasicText(title, style = RelayType.Title, modifier = Modifier.align(Alignment.Center))
        if (onBack == null) {
            BasicText(
                nextTitle,
                style = RelayType.Track.copy(color = RelayColors.Muted),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onNext)
                    .padding(end = 16.dp, top = 15.dp),
            )
        }
    }
}
