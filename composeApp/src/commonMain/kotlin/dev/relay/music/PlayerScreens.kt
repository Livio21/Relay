package dev.relay.music

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import dev.relay.music.model.SleepTimer
import dev.relay.music.model.TimedLyrics
import dev.relay.music.model.Track
import dev.relay.music.model.activeLineIndex
import dev.relay.music.model.adjustLrcOffset
import dev.relay.music.model.currentLrcOffset
import dev.relay.music.model.formatLrcTimestamp
import dev.relay.music.model.nextSleepTimer
import dev.relay.music.model.parseLrc
import dev.relay.music.model.prepareLrcForSave
import dev.relay.music.model.resolveEffectiveMetadata
import dev.relay.music.model.stampLrcLine
import dev.relay.music.playback.PlaybackState
import dev.relay.music.playback.RepeatMode
import dev.relay.music.ui.RelayColors
import dev.relay.music.ui.RelayIcon
import dev.relay.music.ui.RelayIconButton
import dev.relay.music.ui.RelayIcons
import dev.relay.music.ui.RelayMarqueeText
import dev.relay.music.ui.RelayPresentation
import dev.relay.music.ui.RelayType
import dev.relay.music.ui.asThemedTitle
import dev.relay.music.extension.ThemePlayerLayout
import dev.relay.music.extension.ThemeTransportLayout
import dev.relay.music.haptics.HapticAction
import dev.relay.music.haptics.LocalRelayHaptics
import dev.relay.music.ui.relayBorder
import dev.relay.music.ui.relayButtonBorder
import dev.relay.music.ui.relayChrome
import coil3.compose.AsyncImage
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.milliseconds

internal enum class PlayerLayout { EXPANDED, COMPACT }

private enum class PlayerDrawer { NONE, QUEUE, LYRICS, OPTIONS }

@Composable
internal fun PlayerSurface(
    layout: PlayerLayout,
    playbackState: PlaybackState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    lyricsText: String? = null,
    lyricsMessage: String? = null,
    onLoadLyrics: ((Track) -> Unit)? = null,
    onSaveLyrics: ((Track, String) -> Unit)? = null,
    onFetchLyrics: ((Track) -> Unit)? = null,
    onOpenNowPlaying: () -> Unit = {},
    shuffleProfileName: String = "DEFAULT",
    onShuffleEnabledChange: ((Boolean) -> Unit)? = null,
    onRepeatModeChange: ((RepeatMode) -> Unit)? = null,
    onReshuffle: (() -> Unit)? = null,
    onOpenShuffleSettings: (() -> Unit)? = null,
    onOpenQueue: (() -> Unit)? = null,
    lyricsOpen: Boolean = false,
    onToggleLyrics: (() -> Unit)? = null,
    optionsOpen: Boolean = false,
    onToggleOptions: (() -> Unit)? = null,
    sleepTimer: SleepTimer = SleepTimer.OFF,
    onSelectSleepTimer: ((SleepTimer) -> Unit)? = null,
    onImportLyricsFile: (() -> Unit)? = null,
    onExportLyricsFile: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = layout,
        transitionSpec = { (EnterTransition.None togetherWith ExitTransition.None).using(SizeTransform(clip = false)) },
        modifier = modifier,
        label = "player surface",
    ) { targetLayout ->
        when (targetLayout) {
            PlayerLayout.COMPACT -> CompactPlayerSurface(
                playbackState,
                onPlayPause,
                onPrevious,
                onSeekTo,
                onNext,
                Modifier.fillMaxWidth(),
                onOpenNowPlaying,
            )
            PlayerLayout.EXPANDED -> ExpandedPlayerSurface(
                playbackState = playbackState,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onSeekTo = onSeekTo,
                lyricsText = lyricsText,
                lyricsMessage = lyricsMessage,
                onLoadLyrics = onLoadLyrics,
                onSaveLyrics = onSaveLyrics,
                onFetchLyrics = onFetchLyrics,
                shuffleProfileName = shuffleProfileName,
                onShuffleEnabledChange = onShuffleEnabledChange,
                onRepeatModeChange = onRepeatModeChange,
                onReshuffle = onReshuffle,
                onOpenShuffleSettings = onOpenShuffleSettings,
                onOpenQueue = onOpenQueue,
                lyricsOpen = lyricsOpen,
                onToggleLyrics = onToggleLyrics,
                optionsOpen = optionsOpen,
                onToggleOptions = onToggleOptions,
                sleepTimer = sleepTimer,
                onSelectSleepTimer = onSelectSleepTimer,
                onImportLyricsFile = onImportLyricsFile,
                onExportLyricsFile = onExportLyricsFile,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun CompactPlayerSurface(
    playbackState: PlaybackState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenNowPlaying: () -> Unit = {},
) {
    val track = playbackState.currentTrack
    Box(
        modifier = modifier
            .border(1.dp, RelayColors.Line)
            .fillMaxWidth(),
    ) {
        track?.artworkUri?.let { artworkUri ->
            AsyncImage(
                model = artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().blur(24.dp).alpha(0.30f),
            )
        }
        Box(Modifier.matchParentSize().background(RelayColors.Panel.copy(alpha = 0.82f)))
        Column(modifier = Modifier.fillMaxWidth()) {
            PlayerProgress(
            progressText = false,
            playbackState = playbackState,
            onSeekTo = onSeekTo,
            modifier = Modifier.fillMaxWidth(),
            barHeight = 3.dp,
            thumbSize = 0.dp,
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerArtwork(
                track = track,
                emptyLabel = "NO ART",
                modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                onClick = onOpenNowPlaying,
            )
            PlayerIdentity(
                track = track,
                expanded = false,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            RelayIconButton(RelayIcons.Previous, "Previous track", onPrevious)
            RelayIconButton(
                icon = if (playbackState.isPlaying) RelayIcons.Pause else RelayIcons.Play,
                description = if (playbackState.isPlaying) "Pause" else "Play",
                onClick = onPlayPause,
                tint = RelayColors.Signal,
                size = 28.dp,
            )
            RelayIconButton(RelayIcons.Next, "Next track", onNext)
        }
            playbackState.error?.let { message ->
                BasicText(
                    message,
                    style = RelayType.Metadata.copy(color = RelayColors.Danger),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
internal fun ExpandedPlayerSurface(
    playbackState: PlaybackState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    lyricsText: String?,
    lyricsMessage: String?,
    onLoadLyrics: ((Track) -> Unit)?,
    onSaveLyrics: ((Track, String) -> Unit)?,
    onFetchLyrics: ((Track) -> Unit)?,
    shuffleProfileName: String = "DEFAULT",
    onShuffleEnabledChange: ((Boolean) -> Unit)? = null,
    onRepeatModeChange: ((RepeatMode) -> Unit)? = null,
    onReshuffle: (() -> Unit)? = null,
    onOpenShuffleSettings: (() -> Unit)? = null,
    onOpenQueue: (() -> Unit)? = null,
    lyricsOpen: Boolean = false,
    onToggleLyrics: (() -> Unit)? = null,
    optionsOpen: Boolean = false,
    onToggleOptions: (() -> Unit)? = null,
    sleepTimer: SleepTimer = SleepTimer.OFF,
    onSelectSleepTimer: ((SleepTimer) -> Unit)? = null,
    onImportLyricsFile: (() -> Unit)? = null,
    onExportLyricsFile: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val track = playbackState.currentTrack
    // Reset when the lyrics view is reopened, so closing and returning never lands in the editor.
    var editingLyrics by remember(lyricsOpen, track?.let(::trackKey)) { mutableStateOf(false) }
    LaunchedEffect(track?.let(::trackKey), lyricsOpen) { if (track != null && lyricsOpen) onLoadLyrics?.invoke(track) }
    if (track == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BasicText("Nothing is playing. Choose a track from your library.", style = RelayType.Metadata)
        }
        return
    }
    // Outside the scrolling column below: a scrollable parent hands children unbounded height,
    // which would collapse the editor's weighted text area to nothing.
    if (lyricsOpen && editingLyrics && onSaveLyrics != null) {
        LyricsEditor(
            track = track,
            lyricsText = lyricsText,
            positionMs = playbackState.positionMs,
            onSaveLyrics = onSaveLyrics,
            onDone = { editingLyrics = false },
            modifier = modifier.fillMaxSize().padding(16.dp),
        )
        return
    }
    var drawer by remember(trackKey(track)) { mutableStateOf(PlayerDrawer.NONE) }
    var drawerHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val baseDrawerHeight = 44.dp
    val baseDrawerPx = with(density) { baseDrawerHeight.toPx() }.toInt()
    val targetOffsetPx = if (drawer == PlayerDrawer.NONE) 0 else (drawerHeightPx - baseDrawerPx).coerceAtLeast(0)
    val contentOffset by animateDpAsState(
        targetValue = with(density) { targetOffsetPx.toDp() },
        label = "playerDrawerOffset",
    )
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = -contentOffset.toPx() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            val compactPresentation = RelayPresentation.playerLayout == ThemePlayerLayout.COMPACT
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (compactPresentation) 0.62f else 1f)
                    .aspectRatio(1f)
            ) {
                PlayerArtwork(track, "NO ARTWORK", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                if (lyricsOpen) LyricsOverlay(lyricsText, lyricsMessage, playbackState.positionMs)
            }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                MetroPlayerIdentity(track)
                PlayerProgress(progressText = true, playbackState, onSeekTo, Modifier.padding(top = 8.dp))
                playbackState.error?.let { message ->
                    BasicText(
                        message,
                        style = RelayType.Metadata.copy(color = RelayColors.Danger),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
                // Transport stays on screen; the arrangement is theme-selected, the actions never change.
                when (RelayPresentation.transportLayout) {
                    ThemeTransportLayout.TEXT_ROW -> Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TransportAction("PREV", "Previous track", true, onPrevious, Modifier.weight(1f))
                        TransportAction(if (playbackState.isPlaying) "PAUSE" else "PLAY", "Play or pause", true, onPlayPause, Modifier.weight(1f))
                        TransportAction("NEXT", "Next track", true, onNext, Modifier.weight(1f))
                    }
                    ThemeTransportLayout.CENTERED_ICONS -> CenteredIconTransport(
                        isPlaying = playbackState.isPlaying,
                        shuffleEnabled = playbackState.shuffleEnabled,
                        repeatMode = playbackState.repeatMode,
                        onPrevious = onPrevious,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onShuffle = onShuffleEnabledChange,
                        onRepeat = onRepeatModeChange,
                    )
                    ThemeTransportLayout.SPACED_BAR -> SpacedBarTransport(playbackState.isPlaying, onPrevious, onPlayPause, onNext)
                }
                MetroSecondaryActions(
                    drawer = drawer,
                    onQueue = { drawer = if (drawer == PlayerDrawer.QUEUE) PlayerDrawer.NONE else PlayerDrawer.QUEUE },
                    onLyrics = { drawer = if (drawer == PlayerDrawer.LYRICS) PlayerDrawer.NONE else PlayerDrawer.LYRICS },
                    onOptions = { drawer = if (drawer == PlayerDrawer.OPTIONS) PlayerDrawer.NONE else PlayerDrawer.OPTIONS },
                )
            }
            }
            }
            Spacer(Modifier.fillMaxWidth().height(baseDrawerHeight))
        }
        AnimatedVisibility(
            visible = drawer != PlayerDrawer.NONE,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { drawerHeightPx = it.height },
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        ) {
            MetroPlayerDrawer(
                drawer = drawer,
                onClose = { drawer = PlayerDrawer.NONE },
                playbackState = playbackState,
                lyricsText = lyricsText,
                lyricsMessage = lyricsMessage,
                canEditLyrics = onSaveLyrics != null,
                canFetchLyrics = onFetchLyrics != null && track.title.isNotBlank() && track.artist.isNotBlank(),
                shuffleProfileName = shuffleProfileName,
                sleepTimer = sleepTimer,
                onShuffleEnabledChange = onShuffleEnabledChange,
                onRepeatModeChange = onRepeatModeChange,
                onReshuffle = onReshuffle,
                onOpenShuffleSettings = onOpenShuffleSettings,
                onOpenLyricsEditor = { editingLyrics = true },
                onFetchLyrics = { onFetchLyrics?.invoke(track) },
                onSelectSleepTimer = onSelectSleepTimer,
                onImportLyricsFile = onImportLyricsFile,
                onExportLyricsFile = onExportLyricsFile,
            )
        }
    }
}

@Composable
private fun MetroPlayerDrawer(
    drawer: PlayerDrawer,
    onClose: () -> Unit,
    playbackState: PlaybackState,
    lyricsText: String?,
    lyricsMessage: String?,
    canEditLyrics: Boolean,
    canFetchLyrics: Boolean,
    shuffleProfileName: String,
    sleepTimer: SleepTimer,
    onShuffleEnabledChange: ((Boolean) -> Unit)?,
    onRepeatModeChange: ((RepeatMode) -> Unit)?,
    onReshuffle: (() -> Unit)?,
    onOpenShuffleSettings: (() -> Unit)?,
    onOpenLyricsEditor: () -> Unit,
    onFetchLyrics: () -> Unit,
    onSelectSleepTimer: ((SleepTimer) -> Unit)?,
    onImportLyricsFile: (() -> Unit)?,
    onExportLyricsFile: (() -> Unit)?,
) {
    val label = when (drawer) {
        PlayerDrawer.QUEUE -> "QUEUE"
        PlayerDrawer.LYRICS -> "LYRICS"
        PlayerDrawer.OPTIONS -> "PLAYER OPTIONS"
        PlayerDrawer.NONE -> ""
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .background(RelayColors.Panel)
            .navigationBarsPadding(),
    ) {
        Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(label, style = RelayType.Kicker.copy(color = RelayColors.Signal))
                    if (drawer == PlayerDrawer.QUEUE) {
                        BasicText(
                            "  ${playbackState.queue.size} TRACKS",
                            style = RelayType.Kicker,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    RelayIconButton(
                        icon = RelayIcons.Close,
                        description = "Close $label drawer",
                        onClick = onClose,
                        tint = RelayColors.Paper,
                        hapticAction = HapticAction.TAB_CHANGE,
                        modifier = Modifier.background(RelayColors.Line.copy(alpha = 0.55f), CircleShape),
                    )
                }
                AnimatedContent(
            targetState = drawer,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "player drawer content",
        ) { targetDrawer ->
        when (targetDrawer) {
            PlayerDrawer.QUEUE -> {
                if (playbackState.queue.isEmpty()) {
                    BasicText(
                        "The queue is empty.",
                        style = RelayType.Metadata,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        itemsIndexed(
                            playbackState.queue,
                            key = { index, track -> "$index-${track.sourceId}-${track.id}" },
                        ) { index, track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .background(if (index == playbackState.currentIndex) RelayColors.Panel else RelayColors.Ink)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                PlayerArtwork(track, "NO ART", Modifier.size(32.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    RelayMarqueeText(
                                        text = track.title,
                                        style = RelayType.Track.copy(
                                            color = if (index == playbackState.currentIndex) RelayColors.Signal else RelayColors.Paper,
                                        ),
                                    )
                                    RelayMarqueeText(text = track.artist, style = RelayType.Metadata)
                                }
                                BasicText(
                                    track.durationMs?.let(::formatDuration) ?: "--:--",
                                    style = RelayType.Utility,
                                )
                            }
                        }
                    }
                }
            }
            PlayerDrawer.LYRICS -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    BasicText(
                        text = lyricsText?.takeIf { it.isNotBlank() } ?: lyricsMessage ?: "No lyrics yet.",
                        style = RelayType.Metadata.copy(color = RelayColors.Paper),
                    )
                }
            }
            PlayerDrawer.OPTIONS -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    PlayerOptionsMenu(
                        playbackState = playbackState,
                        lyricsOpen = lyricsText != null,
                        canEditLyrics = canEditLyrics,
                        canFetchLyrics = canFetchLyrics,
                        shuffleProfileName = shuffleProfileName,
                        sleepTimer = sleepTimer,
                        onShuffleEnabledChange = onShuffleEnabledChange,
                        onRepeatModeChange = onRepeatModeChange,
                        onReshuffle = onReshuffle,
                        onOpenShuffleSettings = onOpenShuffleSettings,
                        onOpenQueue = null,
                        onToggleLyrics = null,
                        onEditLyrics = onOpenLyricsEditor,
                        onFetchLyrics = onFetchLyrics,
                        onSelectSleepTimer = onSelectSleepTimer,
                        onImportLyricsFile = onImportLyricsFile,
                        onExportLyricsFile = onExportLyricsFile,
                    )
                }
            }
                    PlayerDrawer.NONE -> Unit
                }
                }
                Spacer(Modifier.height(16.dp))
    }
}

/** One primary media row: shuffle, previous, play, next, repeat. */
@Composable
private fun CenteredIconTransport(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onShuffle: ((Boolean) -> Unit)?,
    onRepeat: ((RepeatMode) -> Unit)?,
) {
    val haptics = LocalRelayHaptics.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MetroCircleControl(
            icon = RelayIcons.Shuffle,
            description = "Toggle shuffle",
            onClick = { onShuffle?.invoke(!shuffleEnabled) },
            size = 48.dp,
            iconSize = 20.dp,
            tint = if (shuffleEnabled) RelayColors.Signal else RelayColors.Muted,
            haptics = haptics,
        )
        MetroCircleControl(
            icon = RelayIcons.Previous,
            description = "Previous track",
            onClick = onPrevious,
            size = 48.dp,
            iconSize = 24.dp,
            haptics = haptics,
        )
        MetroCircleControl(
            icon = if (isPlaying) RelayIcons.Pause else RelayIcons.Play,
            description = if (isPlaying) "Pause" else "Play",
            onClick = onPlayPause,
            size = 56.dp,
            iconSize = 32.dp,
            emphasized = true,
            haptics = haptics,
        )
        MetroCircleControl(
            icon = RelayIcons.Next,
            description = "Next track",
            onClick = onNext,
            size = 48.dp,
            iconSize = 24.dp,
            haptics = haptics,
        )
        MetroCircleControl(
            icon = RelayIcons.Repeat,
            description = "Change repeat mode",
            onClick = { onRepeat?.invoke(repeatMode.next()) },
            size = 48.dp,
            iconSize = 20.dp,
            tint = if (repeatMode == RepeatMode.OFF) RelayColors.Muted else RelayColors.Signal,
            haptics = haptics,
        )
    }
}

@Composable
private fun MetroCircleControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    size: Dp,
    iconSize: Dp,
    emphasized: Boolean = false,
    tint: Color = RelayColors.Paper,
    haptics: dev.relay.music.haptics.Haptics,
) {
    Box(
        modifier = Modifier
            .size(size)
            .then(if (emphasized) Modifier.background(RelayColors.Signal, CircleShape) else Modifier.border(1.dp, RelayColors.Line, CircleShape))
            .semantics { contentDescription = description }
            .clickable(role = Role.Button) {
                haptics.perform(HapticAction.TRANSPORT_PRESS)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        RelayIcon(
            icon = icon,
            description = null,
            tint = if (emphasized) RelayColors.Ink else tint,
            size = iconSize,
        )
    }
}

@Composable
private fun MetroPlayerIdentity(track: Track?) {
    val effective = track?.resolveEffectiveMetadata()
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        RelayMarqueeText(
            text = (effective?.display?.title ?: "NO TRACK SELECTED").asThemedTitle(),
            style = RelayType.HeroTitle,
        )
        effective?.let {
            RelayMarqueeText(
                text = it.display.artist,
                style = RelayType.HeroArtist,
                modifier = Modifier.padding(top = 4.dp),
            )
            RelayMarqueeText(
                text = it.display.album,
                style = RelayType.Kicker,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun MetroSecondaryActions(
    drawer: PlayerDrawer,
    onQueue: () -> Unit,
    onLyrics: () -> Unit,
    onOptions: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, start = 22.dp, end = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RelayIconButton(
            RelayIcons.Queue,
            "Queue drawer",
            onQueue,
            tint = if (drawer == PlayerDrawer.QUEUE) RelayColors.Signal else RelayColors.Muted,
            hapticAction = HapticAction.TAB_CHANGE,
        )
        RelayIconButton(
            RelayIcons.Lyrics,
            "Lyrics drawer",
            onLyrics,
            tint = if (drawer == PlayerDrawer.LYRICS) RelayColors.Signal else RelayColors.Muted,
            hapticAction = HapticAction.TAB_CHANGE,
        )
        RelayIconButton(
            RelayIcons.More,
            "Player options drawer",
            onOptions,
            tint = if (drawer == PlayerDrawer.OPTIONS) RelayColors.Signal else RelayColors.Muted,
            hapticAction = HapticAction.TAB_CHANGE,
        )
    }
}

/** Zune-style justified bar: one bordered container, transport spread edge to edge. */
@Composable
private fun SpacedBarTransport(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val haptics = LocalRelayHaptics.current
    fun barAction(description: String, onClick: () -> Unit): Modifier = Modifier
        .heightIn(min = 48.dp)
        .clickable(role = Role.Button) {
            haptics.perform(HapticAction.TRANSPORT_PRESS)
            onClick()
        }
        .padding(horizontal = 16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .relayChrome()
            .relayBorder()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText("◀◀", style = RelayType.Track.copy(color = RelayColors.Paper), modifier = Modifier.semantics { contentDescription = "Previous track" }.then(barAction("Previous track") { onPrevious() }))
        BasicText(if (isPlaying) "Ⅱ" else "▶", style = RelayType.Track.copy(color = RelayColors.Signal), modifier = Modifier.semantics { contentDescription = "Play or pause" }.then(barAction("Play or pause") { onPlayPause() }))
        BasicText("▶▶", style = RelayType.Track.copy(color = RelayColors.Paper), modifier = Modifier.semantics { contentDescription = "Next track" }.then(barAction("Next track") { onNext() }))
    }
}

/** Secondary player actions, kept off the main surface so transport is always one tap away. */
@Composable
private fun PlayerOptionsMenu(
    playbackState: PlaybackState,
    shuffleProfileName: String,
    lyricsOpen: Boolean,
    canEditLyrics: Boolean,
    canFetchLyrics: Boolean,
    sleepTimer: SleepTimer,
    onShuffleEnabledChange: ((Boolean) -> Unit)?,
    onRepeatModeChange: ((RepeatMode) -> Unit)?,
    onReshuffle: (() -> Unit)?,
    onOpenShuffleSettings: (() -> Unit)?,
    onOpenQueue: (() -> Unit)?,
    onToggleLyrics: (() -> Unit)?,
    onEditLyrics: () -> Unit,
    onFetchLyrics: () -> Unit,
    onSelectSleepTimer: ((SleepTimer) -> Unit)?,
    onImportLyricsFile: (() -> Unit)?,
    onExportLyricsFile: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
    ) {
        BasicText("PLAYER OPTIONS", style = RelayType.Utility.copy(color = RelayColors.Muted))
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            onRepeatModeChange?.let {
                TransportAction(
                    repeatLabel(playbackState.repeatMode),
                    "Change repeat mode; current setting ${repeatLabel(playbackState.repeatMode).lowercase()}",
                    playbackState.queue.isNotEmpty(),
                    { it(playbackState.repeatMode.next()) },
                    Modifier.weight(1f),
                )
            }
            onShuffleEnabledChange?.let {
                TransportAction(
                    "SHUFFLE ${if (playbackState.shuffleEnabled) "ON" else "OFF"}",
                    "Turn playback shuffle ${if (playbackState.shuffleEnabled) "off" else "on"}",
                    playbackState.queue.size > 1,
                    { it(!playbackState.shuffleEnabled) },
                    Modifier.weight(1f),
                )
            }
            onOpenQueue?.let {
                TransportAction("QUEUE", "Show the play queue", true, it, Modifier.weight(1f))
            }
            onToggleLyrics?.let {
                TransportAction(
                    label = if (lyricsOpen) "HIDE LYRICS" else "LYRICS",
                    description = if (lyricsOpen) "Hide lyrics" else "Show lyrics over the artwork",
                    enabled = true,
                    onClick = it,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (onReshuffle != null || onOpenShuffleSettings != null) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onReshuffle?.let {
                    TransportAction("RESHUFFLE", "Apply the selected shuffle profile to this queue", playbackState.queue.size > 1, it, Modifier.weight(1f))
                }
                onOpenShuffleSettings?.let {
                    TransportAction("MODE: $shuffleProfileName", "Choose how Relay reshuffles the queue", true, it, Modifier.weight(1f))
                }
            }
        }
        if (lyricsOpen) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canEditLyrics) {
                    TransportAction("EDIT LOCAL", "Edit local lyrics", true, onEditLyrics, Modifier.weight(1f))
                }
                TransportAction("FETCH LYRICS", "Fetch lyrics using confirmed metadata", canFetchLyrics, onFetchLyrics, Modifier.weight(1f))
            }
            if (onImportLyricsFile != null || onExportLyricsFile != null) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onImportLyricsFile?.let {
                        TransportAction("IMPORT .LRC", "Import an .lrc file for this track", true, it, Modifier.weight(1f))
                    }
                    onExportLyricsFile?.let {
                        TransportAction("EXPORT .LRC", "Save this track's lyrics as an .lrc file", true, it, Modifier.weight(1f))
                    }
                }
            }
        }
        onSelectSleepTimer?.let {
            TransportAction(
                label = "SLEEP: ${sleepTimer.label}",
                description = "Choose when playback should pause; currently ${sleepTimer.label.lowercase()}. " +
                    if (sleepTimer == SleepTimer.END_OF_TRACK) "Pauses after this track finishes" else "Tap to change",
                enabled = true,
                onClick = { it(sleepTimer.nextSleepTimer) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

/** Lyrics laid over the artwork behind a dark gradient: the line being sung, then the next. */
@Composable
private fun BoxScope.LyricsOverlay(lyricsText: String?, message: String?, positionMs: Long) {
    val timed = remember(lyricsText) { parseLrc(lyricsText) }
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        RelayColors.Ink.copy(alpha = 0.45f),
                        RelayColors.Ink.copy(alpha = 0.80f),
                        RelayColors.Ink.copy(alpha = 0.95f),
                    ),
                ),
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            timed != null -> {
                val index = timed.activeLineIndex(positionMs)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText(
                        // Before the first cue, and during instrumental gaps, there is no line to show.
                        text = timed.lines.getOrNull(index)?.text?.takeIf { it.isNotBlank() } ?: "♪",
                        style = RelayType.Title.copy(color = RelayColors.Paper, textAlign = TextAlign.Center),
                    )
                    timed.lines.getOrNull(index + 1)?.text?.takeIf { it.isNotBlank() }?.let { next ->
                        BasicText(
                            text = next,
                            style = RelayType.Track.copy(color = RelayColors.Muted, textAlign = TextAlign.Center),
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
            !lyricsText.isNullOrBlank() -> BasicText(
                text = lyricsText,
                style = RelayType.Metadata.copy(color = RelayColors.Paper, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            )
            else -> BasicText(
                text = message ?: "No saved lyrics for this track.",
                style = RelayType.Metadata.copy(color = RelayColors.Paper, textAlign = TextAlign.Center),
            )
        }
    }
}

@Composable
internal fun PlayerArtwork(
    track: Track?,
    emptyLabel: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val effective = track?.resolveEffectiveMetadata()
    val artworkModifier = modifier.then(
        if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick),
    )
    effective?.track?.artworkUri?.let { artworkUri ->
        AsyncImage(
            model = artworkUri,
            contentDescription = "Album cover for ${effective.display.album}",
            contentScale = contentScale,
            modifier = artworkModifier,
        )
    } ?: Box(
        modifier = artworkModifier,
        contentAlignment = Alignment.Center,
    ) { BasicText(emptyLabel, style = RelayType.Utility) }
}

@Composable
internal fun PlayerIdentity(track: Track?, expanded: Boolean, modifier: Modifier = Modifier) {
    val effective = track?.resolveEffectiveMetadata()
    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        RelayMarqueeText(
            text = (effective?.display?.title ?: "NO TRACK SELECTED").asThemedTitle(),
            style = if (expanded) RelayType.Title else RelayType.Track,
        )
        effective?.let {
            RelayMarqueeText(
                text = if (expanded) {
                    "${it.display.artist} — ${it.display.album}"
                } else {
                    it.display.artist
                },
                style = RelayType.Metadata,
                modifier = Modifier.padding(top = 4.dp),
                fadeColor = if (expanded) RelayColors.Ink else RelayColors.Panel,
            )
        }
    }
}

/**
 * Ambient strip above/below the artwork. Deliberately takes only the artwork URI and a fixed
 * height so playback position ticks skip this composable instead of re-running the blur layers.
 */
@Composable
private fun PlayerAmbientStrip(artworkUri: String?, height: Dp) {
    if (RelayPresentation.playerLayout == ThemePlayerLayout.COMPACT) return
    artworkUri ?: return
    AmbientArtwork(artworkUri, Modifier.fillMaxWidth().height(height))
}

@Composable
internal fun AmbientArtwork(artworkUri: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clipToBounds().background(RelayColors.Ink)) {
        AsyncImage(
            model = artworkUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = 1.2f, scaleY = 1.2f)
                .blur(28.dp)
                .alpha(0.34f),
        )
        Box(Modifier.fillMaxSize().background(RelayColors.Ink.copy(alpha = 0.36f)))
    }
}

@Composable
internal fun PlayerProgress(
    progressText: Boolean = true,
    playbackState: PlaybackState,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    barHeight: Dp = 3.dp,
    thumbSize: Dp = 0.dp,
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val duration = playbackState.durationMs
    val progress = playbackProgress(playbackState)
    val haptics = LocalRelayHaptics.current
    var lastTick by remember { mutableStateOf(TimeSource.Monotonic.markNow()) }
    fun seekTick() {
        // Throttle: drag events arrive per frame; haptics stay perceptible, not buzzy.
        if (lastTick.elapsedNow() >= 150.milliseconds) {
            lastTick = TimeSource.Monotonic.markNow()
            haptics.perform(HapticAction.SEEK_TICK)
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxOf(barHeight, thumbSize))
                .background(RelayColors.Line)
                .onSizeChanged { widthPx = it.width }
                .pointerInput(duration, widthPx) {
                    detectTapGestures { offset -> if (duration > 0 && widthPx > 0) onSeekTo((duration * offset.x / widthPx).toLong()) }
                }
                .pointerInput(duration, widthPx) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            seekTick()
                            if (duration > 0 && widthPx > 0) onSeekTo((duration * (offset.x / widthPx).coerceIn(0f, 1f)).toLong())
                        },
                        onDrag = { change, _ ->
                            if (duration > 0 && widthPx > 0) {
                                change.consume()
                                seekTick()
                                onSeekTo((duration * (change.position.x / widthPx).coerceIn(0f, 1f)).toLong())
                            }
                        },
                    )
                }
                .semantics {
                    contentDescription = "Seek within current track"
                    if (duration > 0) {
                        progressBarRangeInfo = ProgressBarRangeInfo(playbackState.positionMs.coerceIn(0, duration).toFloat(), 0f..duration.toFloat(), 0)
                        setProgress { onSeekTo(it.toLong()); true }
                    }
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(progress)
                    .height(barHeight)
                    .background(RelayColors.Signal),
            )
            if (thumbSize > 0.dp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(progress)
                        .height(thumbSize),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(Modifier.size(thumbSize).background(RelayColors.Paper))
                }
            }
        }
        if (progressText) {
        PlayerProgressText(playbackState)
        }
    }
}

@Composable
internal fun PlayerProgressText(playbackState: PlaybackState) {
    val duration = playbackState.durationMs

    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        BasicText(formatDuration(playbackState.positionMs), style = RelayType.Utility)
        BasicText(formatDuration(duration), style = RelayType.Utility)
    }
}

@Composable
internal fun LyricsEditor(
    track: Track,
    lyricsText: String?,
    positionMs: Long,
    onSaveLyrics: (Track, String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(trackKey(track), lyricsText) { mutableStateOf(TextFieldValue(lyricsText.orEmpty())) }
    var saveError by remember(trackKey(track), lyricsText) { mutableStateOf<String?>(null) }
    Column(modifier = modifier.fillMaxSize()) {
        BasicText(track.title, style = RelayType.Track)
        BasicText(
            "Place the cursor on a lyric line, then stamp it when the line starts. Plain lyrics can still be saved.",
            style = RelayType.Metadata,
            modifier = Modifier.padding(top = 4.dp),
        )
        BasicTextField(
            value = draft,
            onValueChange = { draft = it; saveError = null },
            textStyle = RelayType.Metadata.copy(color = RelayColors.Paper),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 16.dp)
                .border(1.dp, RelayColors.Line)
                .semantics { contentDescription = "Local lyrics editor" }
                .padding(12.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportAction(
                "STAMP ${formatLrcTimestamp(positionMs)}",
                "Stamp the selected lyric line at the current playback position",
                true,
                {
                    val selectedLineStart = draft.text.lastIndexOf('\n', draft.selection.start - 1) + 1
                    val stamped = stampLrcLine(draft.text, draft.selection.start, positionMs)
                    val nextLine = stamped.indexOf('\n', selectedLineStart)
                    val nextCursor = if (nextLine >= 0) nextLine + 1 else stamped.length
                    draft = TextFieldValue(stamped, TextRange(nextCursor))
                    saveError = null
                },
                Modifier.fillMaxWidth(),
            )
        }
        // Offset nudge: shifts every timestamp via the [offset:] tag without retiming each line.
        val draftOffset = remember(draft.text) { currentLrcOffset(draft.text) }
        if (parseLrc(draft.text) != null) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransportAction(
                    "-100 MS",
                    "Make lyric lines appear one hundred milliseconds later",
                    true,
                    {
                        val text = adjustLrcOffset(draft.text, -100)
                        draft = TextFieldValue(text, TextRange(draft.selection.start.coerceAtMost(text.length)))
                    },
                    Modifier.weight(1f),
                )
                TransportAction(
                    "OFFSET ${if (draftOffset >= 0) "+" else ""}$draftOffset MS",
                    "Current lyric timing offset; positive values show words earlier",
                    false,
                    {},
                    Modifier.weight(1f),
                )
                TransportAction(
                    "+100 MS",
                    "Make lyric lines appear one hundred milliseconds earlier",
                    true,
                    {
                        val text = adjustLrcOffset(draft.text, 100)
                        draft = TextFieldValue(text, TextRange(draft.selection.start.coerceAtMost(text.length)))
                    },
                    Modifier.weight(1f),
                )
            }
        }
        saveError?.let {
            BasicText(
                it,
                style = RelayType.Metadata.copy(color = RelayColors.Danger),
                modifier = Modifier.padding(top = 8.dp).semantics { contentDescription = "Lyrics error: $it" },
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportAction("CANCEL", "Discard lyric changes", true, onDone, Modifier.weight(1f))
            TransportAction(
                "SAVE LYRICS",
                "Validate, order, and save local lyrics",
                true,
                {
                    prepareLrcForSave(draft.text).fold(
                        onSuccess = { onSaveLyrics(track, it); onDone() },
                        onFailure = { saveError = it.message ?: "Invalid LRC lyrics." },
                    )
                },
                Modifier.weight(1f),
            )
        }
    }
}

internal fun playbackProgress(playbackState: PlaybackState): Float {
    if (playbackState.durationMs <= 0) return 0f
    return playbackState.positionMs
        .coerceIn(0, playbackState.durationMs)
        .toFloat() / playbackState.durationMs
}
