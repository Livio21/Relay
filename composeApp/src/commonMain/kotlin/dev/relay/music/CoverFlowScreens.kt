package dev.relay.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import dev.relay.music.model.Track
import dev.relay.music.ui.RelayColors
import dev.relay.music.ui.RelayType
import kotlin.math.absoluteValue

/**
 * Landscape browse view: covers stand in a row, angled away from the one facing you, with a
 * reflection underneath. Flicking moves the selection; tapping the centre cover plays it.
 */
@Composable
internal fun CoverFlow(
    tracks: List<Track>,
    selectedIndex: Int,
    isPlaying: Boolean,
    onPlay: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BasicText("Nothing to browse yet.", style = RelayType.Metadata)
        }
        return
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val availableWidth = maxWidth
        // Landscape is short: size covers from the height left after the transport row.
        val coverSize = minOf(
            (maxHeight - CONTROLS_HEIGHT) / (1f + REFLECTION_FRACTION),
            availableWidth * 0.42f,
        )
        val state = rememberPagerState(
            initialPage = selectedIndex.coerceIn(0, tracks.lastIndex),
        ) { tracks.size }
        // Follow the player when the track changes underneath us, but never fight a drag.
        // Keyed on the list too: it swaps from library to queue once the session connects.
        LaunchedEffect(selectedIndex, tracks) {
            if (selectedIndex in tracks.indices && !state.isScrollInProgress) {
                state.animateScrollToPage(selectedIndex)
            }
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalPager(
                state = state,
                pageSize = PageSize.Fixed(coverSize),
                contentPadding = PaddingValues(horizontal = (availableWidth - coverSize) / 2),
                // Negative spacing is what makes neighbours tuck behind the centre cover.
                pageSpacing = -(coverSize * 0.3f),
                beyondViewportPageCount = 3,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                val offset = (page - state.currentPage) - state.currentPageOffsetFraction
                CoverFlowItem(
                    track = tracks[page],
                    offset = offset,
                    size = coverSize,
                    onClick = { onPlay(page) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TransportAction("PREV", "Previous track", true, onPrevious, Modifier.weight(1f))
                TransportAction(if (isPlaying) "PAUSE" else "PLAY", "Play or pause", true, onPlayPause, Modifier.weight(1f))
                TransportAction("NEXT", "Next track", true, onNext, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CoverFlowItem(
    track: Track,
    offset: Float,
    size: Dp,
    onClick: () -> Unit,
) {
    val distance = offset.absoluteValue.coerceAtMost(MAX_TURN_DISTANCE)
    val settled = 1f - distance / MAX_TURN_DISTANCE
    Column(
        modifier = Modifier
            .size(width = size, height = size * (1f + REFLECTION_FRACTION))
            // The centre cover must draw over the ones tucked behind it.
            .zIndex(settled)
            .graphicsLayer {
                // Without a near camera the rotation reads as a flat squash, not a turn.
                cameraDistance = 14f * this.density
                rotationY = -offset.coerceIn(-MAX_TURN_DISTANCE, MAX_TURN_DISTANCE) * TURN_DEGREES
                val scale = MIN_SCALE + (1f - MIN_SCALE) * settled
                scaleX = scale
                scaleY = scale
            }
            .semantics { contentDescription = "Play ${track.title}" }
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Cover(track, Modifier.size(size))
        Box(modifier = Modifier.size(width = size, height = size * REFLECTION_FRACTION)) {
            // Show the cover's bottom edge, then mirror the whole strip so that edge ends up
            // against the cover — flipping the full image instead would move it out of view.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleY = -1f
                        alpha = 0.4f
                        clip = true
                    },
            ) {
                Cover(track, Modifier.size(size).align(Alignment.BottomCenter))
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, RelayColors.Ink))),
            )
        }
    }
}

@Composable
private fun Cover(track: Track, modifier: Modifier = Modifier) {
    val artwork = track.artworkUri
    if (artwork.isNullOrBlank()) {
        Box(
            modifier = modifier.background(RelayColors.Panel),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("NO ARTWORK", style = RelayType.Utility.copy(color = RelayColors.Muted))
        }
    } else {
        AsyncImage(
            model = artwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

private val CONTROLS_HEIGHT = 72.dp
private const val REFLECTION_FRACTION = 0.34f
private const val TURN_DEGREES = 52f
private const val MAX_TURN_DISTANCE = 2.2f
private const val MIN_SCALE = 0.68f
