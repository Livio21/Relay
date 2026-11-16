package dev.relay.music

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import relay.composeapp.generated.resources.Res
import relay.composeapp.generated.resources.inter_variable
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import dev.relay.music.model.Track
import dev.relay.music.model.recentlyPlayedTracks
import dev.relay.music.model.MetadataHealth
import dev.relay.music.settings.activeShuffleProfile
import dev.relay.music.wallpaper.WallpaperEditorScreen
import dev.relay.music.ui.RelayColors
import dev.relay.music.ui.RelayFonts
import dev.relay.music.ui.RelayPresentation
import dev.relay.music.ui.RelayTheme
import dev.relay.music.ui.RelayType
import dev.relay.music.ui.RelayIcons
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
    initialView: Int = NOW_PLAYING_VIEW,
) {
    val thin = Font(Res.font.inter_variable, FontWeight.Thin, variationSettings = FontVariation.Settings(FontVariation.weight(100)))
    val extraLight = Font(Res.font.inter_variable, FontWeight.ExtraLight, variationSettings = FontVariation.Settings(FontVariation.weight(200)))
    val light = Font(Res.font.inter_variable, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300)))
    val normal = Font(Res.font.inter_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400)))
    val medium = Font(Res.font.inter_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500)))
    val semiBold = Font(Res.font.inter_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600)))
    val bold = Font(Res.font.inter_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
    val extraBold = Font(Res.font.inter_variable, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800)))
    val black = Font(Res.font.inter_variable, FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(900)))
    val inter = remember(thin, extraLight, light, normal, medium, semiBold, bold, extraBold, black) {
        FontFamily(thin, extraLight, light, normal, medium, semiBold, bold, extraBold, black)
    }
    SideEffect { if (RelayFonts.sans !== inter) RelayFonts.sans = inter }
    var navigation by remember {
        mutableStateOf(
            RelayNavigationState(
                destination = if (initialView == NOW_PLAYING_VIEW) ROOT_DESTINATION else RelayDestination(initialView),
            ),
        )
    }
    val destination = navigation.destination
    val reviewTrack = destination.reviewTrack
    val selectedPlaylistId = destination.selectedPlaylistId
    val albumKey = destination.albumKey
    val artistKey = destination.artistKey
    val settingsSubmenu = destination.settingsSubmenu
    val extensionsSubmenu = destination.extensionsSubmenu
    val extensionsTab = destination.extensionsTab
    val selectedCatalogExtension = destination.selectedCatalogExtension
    val browsedExtensionId = destination.browsedExtensionId
    val queueOpen = destination.queueOpen
    val lyricsOpen = destination.lyricsOpen
    val playerOptionsOpen = destination.playerOptionsOpen
    val wallpaperEditorOpen = destination.wallpaperEditorOpen
    val openPlaylist = state.playlists.firstOrNull { it.id == selectedPlaylistId }
    var libraryQuery by remember { mutableStateOf("") }
    var librarySort by remember { mutableStateOf(LibrarySort.TITLE) }
    var libraryBrowse by remember { mutableStateOf(LibraryBrowseMode.ALBUMS) }
    var libraryDensity by remember { mutableStateOf(LibraryDensity.THREE) }
    var sectionMenuOpen by remember { mutableStateOf(false) }
    // Keyed so playback position ticks and other unrelated recompositions never re-sort the library.
    val visibleTracks = remember(state.tracks, state.trackFlags) {
        libraryView(state.tracks, state.trackFlags)
    }
    val listedTracks = remember(state.tracks, state.trackFlags, libraryQuery, librarySort) {
        libraryView(state.tracks, state.trackFlags, libraryQuery, librarySort)
    }
    val allAlbums = remember(visibleTracks) { albumGroups(visibleTracks) }
    val allArtists = remember(visibleTracks) { artistGroups(visibleTracks) }
    val listedAlbums = remember(allAlbums, libraryQuery) { filterAlbums(allAlbums, libraryQuery) }
    val listedArtists = remember(allArtists, libraryQuery) { filterArtists(allArtists, libraryQuery) }
    val recentTracks = remember(state.listeningEvents, visibleTracks) {
        recentlyPlayedTracks(state.listeningEvents, visibleTracks)
    }
    val playingAlbumKey = remember(state.playback.currentTrack, allAlbums) {
        val playingKey = state.playback.currentTrack?.let(::trackKey) ?: return@remember null
        allAlbums.firstOrNull { album -> album.tracks.any { trackKey(it) == playingKey } }?.key
    }
    val openAlbum = albumKey?.let { key -> allAlbums.firstOrNull { it.key == key } }
    val openArtist = artistKey?.let { key -> allArtists.firstOrNull { it.key == key } }
    val tracksNeedingMetadata = remember(visibleTracks, state.metadataIgnoredTrackKeys) {
        visibleTracks.filter {
            it.metadataHealth() == MetadataHealth.NEEDS_REVIEW && trackKey(it) !in state.metadataIgnoredTrackKeys
        }
    }
    val pagerStartPage = PAGER_START_PAGE + pagerPageDelta(NOW_PLAYING_VIEW, initialView)
    val pagerState = rememberPagerState(initialPage = pagerStartPage) { Int.MAX_VALUE }
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
        sectionMenuOpen = false
        val updated = navigation.navigate(next)
        if (updated === navigation) return
        navigation = updated
        movePagerTo(next.view)
    }
    fun goBack() {
        val current = navigation.destination
        if (current.view == SETTINGS_VIEW && current.settingsSubmenu != null) {
            // Settings submenus are tabs inside Settings, not a deep history: one back exits
            // the submenu to the Settings root.
            navigation = RelayNavigationState(destination = RelayDestination(SETTINGS_VIEW))
            movePagerTo(SETTINGS_VIEW)
            return
        }
        val updated = navigation.back() ?: return
        navigation = updated
        movePagerTo(updated.destination.view)
    }
    fun playTracks(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val index = startIndex.coerceIn(0, tracks.lastIndex)
        val play = actions.onPlayTracks
        if (play != null) play(tracks, index) else tracks.getOrNull(index)?.let(actions.onTrackSelected)
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
    SideEffect {
        actions.onBackActionChanged(
            when {
                sectionMenuOpen -> ({ sectionMenuOpen = false })
                navigation.canGoBack -> ::goBack
                else -> null
            },
        )
    }

    RelayTheme(backgroundArtworkUri = state.playback.currentTrack?.artworkUri) {
        BoxWithConstraints(
            modifier = modifier.fillMaxSize().background(
                if (RelayPresentation.background == ThemeBackground.NONE) RelayColors.Ink
                else RelayColors.Ink.copy(alpha = 0.72f),
            ),
        ) {
        val landscape = maxWidth > maxHeight
        if (wallpaperEditorOpen) {
            WallpaperEditorScreen(
                initial = state.settings.wallpaperPreset,
                track = state.playback.currentTrack,
                positionMs = state.playback.positionMs,
                durationMs = state.playback.durationMs,
                isPlaying = state.playback.isPlaying,
                nowEpochMs = state.nowEpochMs,
                onSave = { preset ->
                    actions.onWallpaperSettingsChange?.invoke(state.settings.copy(wallpaperPreset = preset))
                    goBack()
                },
                onCancel = ::goBack,
                modifier = Modifier.fillMaxSize(),
            )
            return@BoxWithConstraints
        }
        val renderPlayer: @Composable (PlayerLayout, Modifier) -> Unit = { playerLayout, playerModifier ->
            PlayerSurface(
                layout = playerLayout,
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
                sleepTimer = state.sleepTimer,
                onSelectSleepTimer = actions.onSelectSleepTimer,
                onImportLyricsFile = actions.onImportLyricsFile,
                onExportLyricsFile = actions.onExportLyricsFile,
                onOpenNowPlaying = { navigateTo(destination.forView(NOW_PLAYING_VIEW)) },
                modifier = playerModifier,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .clipToBounds()
                .then(if (sectionMenuOpen) Modifier.blur(18.dp) else Modifier),
        ) {
            val immersiveCoverFlow = landscape && currentView == NOW_PLAYING_VIEW && !isSubmenu
            val nowPlayingExpanded = currentView == NOW_PLAYING_VIEW && !queueOpen && !landscape && !isSubmenu
            if (!immersiveCoverFlow) {
            MetroPageHeading(
                title = when {
                    albumKey != null -> "ALBUM"
                    artistKey != null -> "ARTIST"
                    currentView == NOW_PLAYING_VIEW -> "PLAYING"
                    currentView == EXTENSIONS_VIEW -> "SOURCES"
                    else -> PAGE_TITLES[currentView]
                },
                nextTitle = PAGE_TITLES[(currentView + 1) % PAGE_TITLES.size],
                onOpenSections = { sectionMenuOpen = true },
                onNext = { navigateTo(destination.forView((currentView + 1) % PAGE_TITLES.size)) },
                onBack = if (isSubmenu) ::goBack else null,
            )
            if (!(currentView == NOW_PLAYING_VIEW && !isSubmenu)) Rule()
            }
            Box(modifier = Modifier.weight(1f)) {
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(bottom = if (immersiveCoverFlow || nowPlayingExpanded) 0.dp else 78.dp),
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
                            when {
                                openAlbum != null -> AlbumDetail(
                                    album = openAlbum,
                                    playbackState = state.playback,
                                    favoriteTrackKeys = state.favoriteTrackKeys,
                                    trackFlags = state.trackFlags,
                                    playlists = state.playlists,
                                    onPlayIndex = { index -> playTracks(openAlbum.tracks, index) },
                                    onFavoriteToggle = actions.onFavoriteToggle,
                                    onTrackFlagsChange = actions.onTrackFlagsChange,
                                    onMetadataReview = { navigateTo(destination.copy(view = LIBRARY_VIEW, reviewTrack = it)) },
                                    onAddToPlaylist = actions.onAddToPlaylist,
                                    onCreateAndAddToPlaylist = actions.onCreateAndAddToPlaylist,
                                    onPlayNext = actions.onPlayNext,
                                    onEnqueue = actions.onEnqueue,
                                )
                                openArtist != null -> ArtistDetail(
                                    artist = openArtist,
                                    playbackState = state.playback,
                                    favoriteTrackKeys = state.favoriteTrackKeys,
                                    trackFlags = state.trackFlags,
                                    playlists = state.playlists,
                                    onPlayIndex = { index -> playTracks(openArtist.tracks, index) },
                                    onOpenAlbum = { album ->
                                        navigateTo(destination.copy(view = LIBRARY_VIEW, artistKey = null, albumKey = album.key))
                                    },
                                    onFavoriteToggle = actions.onFavoriteToggle,
                                    onTrackFlagsChange = actions.onTrackFlagsChange,
                                    onMetadataReview = { navigateTo(destination.copy(view = LIBRARY_VIEW, reviewTrack = it)) },
                                    onAddToPlaylist = actions.onAddToPlaylist,
                                    onCreateAndAddToPlaylist = actions.onCreateAndAddToPlaylist,
                                    onPlayNext = actions.onPlayNext,
                                    onEnqueue = actions.onEnqueue,
                                )
                                else -> Column(modifier = Modifier.fillMaxSize()) {
                                    if (tracksNeedingMetadata.isNotEmpty()) {
                                        MetadataBanner(onReview = {
                                            navigateTo(destination.copy(view = LIBRARY_VIEW, reviewTrack = tracksNeedingMetadata.first()))
                                        })
                                        Rule()
                                    }
                                    LibraryBrowseModes(
                                        selected = libraryBrowse,
                                        onSelect = { libraryBrowse = it },
                                    )
                                    Rule()
                                    if (libraryBrowse == LibraryBrowseMode.ALBUMS) {
                                        LibraryDensityPicker(
                                            selected = libraryDensity,
                                            onSelect = { libraryDensity = it },
                                        )
                                        Rule()
                                    }
                                    LibraryControls(
                                        query = libraryQuery,
                                        sort = librarySort,
                                        resultCount = when (libraryBrowse) {
                                            LibraryBrowseMode.TRACKS -> listedTracks.size
                                            LibraryBrowseMode.ALBUMS -> listedAlbums.size
                                            LibraryBrowseMode.ARTISTS -> listedArtists.size
                                        },
                                        onQueryChange = { libraryQuery = it },
                                        onSortChange = { librarySort = it },
                                        resultLabel = libraryBrowse.label,
                                        showSort = libraryBrowse == LibraryBrowseMode.TRACKS,
                                    )
                                    Rule()
                                    when (libraryBrowse) {
                                        LibraryBrowseMode.TRACKS -> TrackList(
                                            tracks = listedTracks,
                                            playbackState = state.playback,
                                            favoriteTrackKeys = state.favoriteTrackKeys,
                                            trackFlags = state.trackFlags,
                                            onTrackSelected = actions.onTrackSelected,
                                            onFavoriteToggle = actions.onFavoriteToggle,
                                            onTrackFlagsChange = actions.onTrackFlagsChange,
                                            onMetadataReview = { navigateTo(destination.copy(view = LIBRARY_VIEW, reviewTrack = it)) },
                                            playlists = state.playlists,
                                            onAddToPlaylist = actions.onAddToPlaylist,
                                            onCreateAndAddToPlaylist = actions.onCreateAndAddToPlaylist,
                                            onPlayNext = actions.onPlayNext,
                                            onEnqueue = actions.onEnqueue,
                                            modifier = Modifier.weight(1f),
                                        )
                                        LibraryBrowseMode.ALBUMS -> AlbumBrowser(
                                            albums = listedAlbums,
                                            emptyMessage = if (libraryQuery.isBlank()) {
                                                "No albums found."
                                            } else {
                                                "No albums match this search."
                                            },
                                            onOpen = { album ->
                                                navigateTo(destination.copy(view = LIBRARY_VIEW, albumKey = album.key, artistKey = null))
                                            },
                                            density = libraryDensity,
                                            currentAlbumKey = playingAlbumKey,
                                            modifier = Modifier.weight(1f),
                                        )
                                        LibraryBrowseMode.ARTISTS -> ArtistSections(
                                            artists = listedArtists,
                                            currentTrackKey = state.playback.currentTrack?.let(::trackKey),
                                            onOpenArtist = { artist ->
                                                navigateTo(destination.copy(view = LIBRARY_VIEW, artistKey = artist.key, albumKey = null))
                                            },
                                            onPlayTrack = { artist, index -> playTracks(artist.tracks, index) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (libraryQuery.isBlank() && recentTracks.isNotEmpty()) {
                                        RecentlyPlayedStrip(
                                            tracks = recentTracks,
                                            currentTrackKey = state.playback.currentTrack?.let(::trackKey),
                                            onPlay = { index -> playTracks(recentTracks, index) },
                                        )
                                    }
                                }
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
                        renderPlayer(PlayerLayout.EXPANDED, Modifier.fillMaxSize())
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
                        offlineDownloads = state.offlineDownloads,
                        onDeleteDownload = actions.onDeleteDownload,
                        onDeleteAllDownloads = actions.onDeleteAllDownloads,
                        onDownloadLimitChange = actions.onDownloadLimitChange,
                        onDownloadAutoCleanupChange = actions.onDownloadAutoCleanupChange,
                        onEvictExceededDownloads = actions.onEvictExceededDownloads,
                        onImportThemePack = actions.onImportThemePack,
                        onApplyThemePack = actions.onApplyThemePack,
                        onRemoveThemePack = actions.onRemoveThemePack,
                        onImportThemeBackground = actions.onImportThemeBackground,
                        onExportDiagnostics = actions.onExportDiagnostics,
                        qrImage = state.lanPairingQr,
                        discoveredHosts = state.discoveredLanHosts,
                        discoveringMode = state.discoveringLanMode,
                        onScanNetwork = actions.onScanNetwork,
                        onJoinDiscovered = actions.onJoinDiscovered,
                        onScanPairingLink = actions.onScanPairingLink,
                        onEditComposition = {
                            navigateTo(
                                destination.copy(
                                    view = SETTINGS_VIEW,
                                    settingsSubmenu = SettingsSubmenu.WALLPAPER,
                                    wallpaperEditorOpen = true,
                                ),
                            )
                        },
                        onOpenAlbumWallpaperPicker = actions.onOpenAlbumWallpaperPicker,
                        submenu = settingsSubmenu,
                        onSubmenuChange = { submenu ->
                            navigateTo(destination.copy(view = SETTINGS_VIEW, settingsSubmenu = submenu))
                        },
                    )
                }
                }
                if (!immersiveCoverFlow && !nowPlayingExpanded) {
                    renderPlayer(
                        PlayerLayout.COMPACT,
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = sectionMenuOpen,
            enter = slideInVertically(initialOffsetY = { -it / 3 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it / 3 }) + fadeOut(),
        ) {
            MetroSectionMenu(
                sections = METRO_SECTIONS,
                currentIndex = currentView,
                artworkUri = state.playback.currentTrack?.artworkUri,
                signature = state.localProfile?.displayName.orEmpty(),
                onSelect = { index ->
                    sectionMenuOpen = false
                    navigateTo(destination.forView(index))
                },
                onClose = { sectionMenuOpen = false },
            )
        }
        }
    }
}

private val PAGE_TITLES = listOf("LIBRARY", "NOW PLAYING", "PLAYLISTS", "INSIGHTS", "EXTENSIONS", "SETTINGS")

internal val METRO_SECTIONS = listOf(
    MetroSection("Library", "albums · artists · songs", RelayIcons.Library),
    MetroSection("Now playing", "what's on", RelayIcons.Play),
    MetroSection("Playlists", "your lists", RelayIcons.Queue),
    MetroSection("Insights", "listening", RelayIcons.Insights),
    MetroSection("Extensions", "sources", RelayIcons.Extension),
    MetroSection("Settings", "relay", RelayIcons.Settings),
)
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
    val albumKey: String? = null,
    val artistKey: String? = null,
    val settingsSubmenu: SettingsSubmenu? = null,
    val extensionsSubmenu: ExtensionsSubmenu? = null,
    val extensionsTab: ExtensionsTab = ExtensionsTab.SOURCES,
    val selectedCatalogExtension: CatalogExtension? = null,
    val browsedExtensionId: String? = null,
    val queueOpen: Boolean = false,
    val lyricsOpen: Boolean = false,
    val playerOptionsOpen: Boolean = false,
    val wallpaperEditorOpen: Boolean = false,
) {
    /** True when this destination is a sub-screen rather than a plain top-level page. */
    val hasSubState: Boolean
        get() = reviewTrack != null ||
            (view == LIBRARY_VIEW && (albumKey != null || artistKey != null)) ||
            settingsSubmenu != null || extensionsSubmenu != null || queueOpen || lyricsOpen ||
            playerOptionsOpen || wallpaperEditorOpen ||
            (view == PLAYLISTS_VIEW && selectedPlaylistId != null)

    fun forView(view: Int) = copy(
        view = view,
        reviewTrack = null,
        selectedPlaylistId = null,
        albumKey = null,
        artistKey = null,
        settingsSubmenu = null,
        extensionsSubmenu = null,
        extensionsTab = ExtensionsTab.SOURCES,
        selectedCatalogExtension = null,
        browsedExtensionId = null,
        queueOpen = false,
        lyricsOpen = false,
        playerOptionsOpen = false,
        wallpaperEditorOpen = false,
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
