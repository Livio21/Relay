package dev.relay.music

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import dev.relay.music.model.TimedLyrics
import dev.relay.music.model.Track
import dev.relay.music.model.activeLineIndex
import dev.relay.music.model.parseLrc
import dev.relay.music.playback.PlaybackState
import dev.relay.music.ui.RelayColors
import dev.relay.music.ui.RelayType
import coil3.compose.AsyncImage

internal enum class PlayerLayout { EXPANDED, COMPACT }

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
    onViewSwipe: (Int) -> Unit = {},
    onOpenNowPlaying: () -> Unit = {},
    shuffleProfileName: String = "DEFAULT",
    onShuffleEnabledChange: ((Boolean) -> Unit)? = null,
    onReshuffle: (() -> Unit)? = null,
    onOpenShuffleSettings: (() -> Unit)? = null,
    onOpenQueue: (() -> Unit)? = null,
    lyricsOpen: Boolean = false,
    onToggleLyrics: (() -> Unit)? = null,
    optionsOpen: Boolean = false,
    onToggleOptions: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    val swipeThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    AnimatedContent(
        targetState = layout,
        transitionSpec = { (EnterTransition.None togetherWith ExitTransition.None).using(SizeTransform(clip = false)) },
        modifier = modifier.pointerInput(layout, swipeThresholdPx) {
            if (layout == PlayerLayout.EXPANDED) {
                detectHorizontalDragGestures(
                    onDragStart = { horizontalDrag = 0f },
                    onHorizontalDrag = { change, amount ->
                        horizontalDrag += amount
                        change.consume()
                    },
                    onDragEnd = {
                        when {
                            horizontalDrag >= swipeThresholdPx -> onViewSwipe(-1)
                            horizontalDrag <= -swipeThresholdPx -> onViewSwipe(1)
                        }
                    },
                )
            }
        },
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
                onReshuffle = onReshuffle,
                onOpenShuffleSettings = onOpenShuffleSettings,
                onOpenQueue = onOpenQueue,
                lyricsOpen = lyricsOpen,
                onToggleLyrics = onToggleLyrics,
                optionsOpen = optionsOpen,
                onToggleOptions = onToggleOptions,
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
    Column(
        modifier = modifier
            .border(1.dp, RelayColors.Line)
            .fillMaxWidth()
            .background(RelayColors.Panel)
    ) {
        PlayerProgress(
            progressText = false,
            playbackState = playbackState,
            onSeekTo = onSeekTo,
            modifier = Modifier.fillMaxWidth(),
            barHeight = 6.dp,
            thumbSize = 14.dp,
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
            BasicText(
                "PREV",
                style = RelayType.Utility,
                modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onPrevious).padding(horizontal = 10.dp, vertical = 16.dp),
            )
            BasicText(
                if (playbackState.isPlaying) "PAUSE" else "PLAY",
                style = RelayType.Utility.copy(color = RelayColors.Danger),
                modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onPlayPause).padding(horizontal = 10.dp, vertical = 16.dp),
            )
            BasicText(
                "NEXT",
                style = RelayType.Utility,
                modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onNext).padding(horizontal = 10.dp, vertical = 16.dp),
            )
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
    onReshuffle: (() -> Unit)? = null,
    onOpenShuffleSettings: (() -> Unit)? = null,
    onOpenQueue: (() -> Unit)? = null,
    lyricsOpen: Boolean = false,
    onToggleLyrics: (() -> Unit)? = null,
    optionsOpen: Boolean = false,
    onToggleOptions: (() -> Unit)? = null,
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
            onSaveLyrics = onSaveLyrics,
            onDone = { editingLyrics = false },
            modifier = modifier.fillMaxSize().padding(16.dp),
        )
        return
    }
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            track.artworkUri?.let { artworkUri ->
                AmbientArtwork(artworkUri, Modifier.fillMaxWidth().height(32.dp))
            }
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                PlayerArtwork(track, "NO ARTWORK", Modifier.fillMaxSize())
                if (lyricsOpen) LyricsOverlay(lyricsText, lyricsMessage, playbackState.positionMs)
            }
            track.artworkUri?.let { artworkUri -> AmbientArtwork(artworkUri, Modifier.fillMaxWidth().height(48.dp)) }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                PlayerIdentity(track, expanded = true, modifier = Modifier.fillMaxWidth().padding(top = 20.dp))
                PlayerProgress(progressText = true ,playbackState, onSeekTo, Modifier.padding(top = 20.dp))
                // Transport stays on screen; everything secondary lives behind MORE.
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransportAction("PREV", "Previous track", true, onPrevious, Modifier.weight(1f))
                    TransportAction(if (playbackState.isPlaying) "PAUSE" else "PLAY", "Play or pause", true, onPlayPause, Modifier.weight(1f))
                    TransportAction("NEXT", "Next track", true, onNext, Modifier.weight(1f))
                }
                onToggleOptions?.let {
                    TransportAction(
                        label = if (optionsOpen) "CLOSE" else "MORE",
                        description = if (optionsOpen) "Hide player options" else "Show player options",
                        enabled = true,
                        onClick = it,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                if (optionsOpen && onToggleOptions != null) {
                    PlayerOptionsMenu(
                        playbackState = playbackState,
                        lyricsOpen = lyricsOpen,
                        canEditLyrics = onSaveLyrics != null,
                        canFetchLyrics = onFetchLyrics != null && track.title.isNotBlank() && track.artist.isNotBlank(),
                        shuffleProfileName = shuffleProfileName,
                        onShuffleEnabledChange = onShuffleEnabledChange,
                        onReshuffle = onReshuffle,
                        onOpenShuffleSettings = onOpenShuffleSettings,
                        onOpenQueue = onOpenQueue,
                        onToggleLyrics = onToggleLyrics,
                        onEditLyrics = { editingLyrics = true },
                        onFetchLyrics = { onFetchLyrics?.invoke(track) },
                    )
                }
            }
        }
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
    onShuffleEnabledChange: ((Boolean) -> Unit)?,
    onReshuffle: (() -> Unit)?,
    onOpenShuffleSettings: (() -> Unit)?,
    onOpenQueue: (() -> Unit)?,
    onToggleLyrics: (() -> Unit)?,
    onEditLyrics: () -> Unit,
    onFetchLyrics: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(RelayColors.Panel)
            .border(1.dp, RelayColors.Line)
            .padding(12.dp),
    ) {
        BasicText("PLAYER OPTIONS", style = RelayType.Utility.copy(color = RelayColors.Muted))
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
) {
    val artworkModifier = modifier.then(
        if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick),
    )
    track?.artworkUri?.let { artworkUri ->
        AsyncImage(
            model = artworkUri,
            contentDescription = "Album cover for ${track.album ?: track.title}",
            contentScale = ContentScale.Crop,
            modifier = artworkModifier,
        )
    } ?: Box(
        modifier = artworkModifier.border(1.dp, RelayColors.Line),
        contentAlignment = Alignment.Center,
    ) { BasicText(emptyLabel, style = RelayType.Utility) }
}

@Composable
internal fun PlayerIdentity(track: Track?, expanded: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        BasicText(
            text = track?.title?.ifBlank { "Untitled track" } ?: "NO TRACK SELECTED",
            style = if (expanded) RelayType.Title else RelayType.Track,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        track?.let {
            BasicText(
                text = if (expanded) {
                    "${it.artist.ifBlank { "Unknown artist" }} — ${it.album ?: "Unknown album"}"
                } else {
                    it.artist.ifBlank { "Unknown artist" }
                },
                style = RelayType.Metadata,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
                            if (duration > 0 && widthPx > 0) onSeekTo((duration * (offset.x / widthPx).coerceIn(0f, 1f)).toLong())
                        },
                        onDrag = { change, _ ->
                            if (duration > 0 && widthPx > 0) {
                                change.consume()
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
    onSaveLyrics: (Track, String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(trackKey(track), lyricsText) { mutableStateOf(lyricsText.orEmpty()) }
    Column(modifier = modifier.fillMaxSize()) {
        BasicText(track.title, style = RelayType.Track)
        BasicText(
            "Paste .lrc text with [mm:ss.xx] timestamps to make lyrics follow playback.",
            style = RelayType.Metadata,
            modifier = Modifier.padding(top = 4.dp),
        )
        BasicTextField(
            draft,
            { draft = it },
            textStyle = RelayType.Metadata.copy(color = RelayColors.Paper),
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 16.dp).border(1.dp, RelayColors.Line).padding(12.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportAction("CANCEL", "Discard lyric changes", true, onDone, Modifier.weight(1f))
            TransportAction("SAVE LYRICS", "Save local lyrics", true, { onSaveLyrics(track, draft); onDone() }, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun NowPlaying(
    playbackState: PlaybackState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    val track = playbackState.currentTrack
    val progress = playbackProgress(playbackState)
    val durationMs = playbackState.durationMs
    var progressWidth by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RelayColors.Panel)
            .border(width = 1.dp, color = RelayColors.Line)
            .padding(16.dp),
    ) {
        BasicText(
            text = track?.title?.ifBlank { "Untitled track" } ?: "NO TRACK SELECTED",
            style = RelayType.Track,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BasicText(
            text = if (track == null) {
                "Select a track from the index."
            } else {
                "${track.artist.ifBlank { "Unknown artist" }} — ${track.album?.takeIf { it.isNotBlank() } ?: "Unknown album"}"
            },
            style = RelayType.Metadata,
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        playbackState.error?.let { message ->
            BasicText(
                text = message,
                style = RelayType.Metadata.copy(color = RelayColors.Danger),
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(RelayColors.Line)
                .onSizeChanged { progressWidth = it.width }
                .pointerInput(durationMs, progressWidth) {
                    detectTapGestures { offset ->
                        if (durationMs > 0 && progressWidth > 0) {
                            onSeekTo((durationMs * offset.x / progressWidth).toLong())
                        }
                    }
                }
                .semantics {
                    contentDescription = "Seek within current track"
                    if (durationMs > 0) {
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current = playbackState.positionMs.coerceIn(0, durationMs).toFloat(),
                            range = 0f..durationMs.toFloat(),
                            steps = 0,
                        )
                        setProgress { target ->
                            onSeekTo(target.toLong())
                            true
                        }
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(RelayColors.Signal),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText(
                text = if (track == null) "--:--" else formatDuration(playbackState.positionMs),
                style = RelayType.Utility,
            )
            BasicText(
                text = track?.durationMs?.let(::formatDuration) ?: "--:--",
                style = RelayType.Utility,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TransportAction(
                label = "PREV",
                description = "Previous track",
                enabled = track != null,
                onClick = onPrevious,
                modifier = Modifier.weight(1f),
            )
            TransportAction(
                label = if (playbackState.isPlaying) "PAUSE" else "PLAY",
                description = if (playbackState.isPlaying) "Pause playback" else "Play",
                enabled = track != null,
                onClick = onPlayPause,
                modifier = Modifier.weight(1f),
            )
            TransportAction(
                label = "NEXT",
                description = "Next track",
                enabled = track != null,
                onClick = onNext,
                modifier = Modifier.weight(1f),
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
