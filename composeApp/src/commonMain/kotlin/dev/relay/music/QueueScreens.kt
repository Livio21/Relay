package dev.relay.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.relay.music.playback.PlaybackState
import dev.relay.music.playback.RepeatMode
import dev.relay.music.ui.RelayColors
import dev.relay.music.ui.RelayType

@Composable
internal fun QueueScreen(
    playbackState: PlaybackState,
    shuffleProfileName: String,
    onPlayIndex: (Int) -> Unit,
    onRemoveIndex: (Int) -> Unit,
    onMoveIndex: (Int, Int) -> Unit,
    onShuffleEnabledChange: (Boolean) -> Unit,
    onRepeatModeChange: (RepeatMode) -> Unit,
    onReshuffle: () -> Unit,
    onOpenShuffleSettings: () -> Unit,
    onClear: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    val queue = playbackState.queue
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        BasicText("QUEUE", style = RelayType.Title)
        BasicText(
            text = when {
                queue.isEmpty() -> "NOTHING QUEUED"
                playbackState.currentIndex in queue.indices ->
                    "${queue.size} TRACKS · PLAYING ${playbackState.currentIndex + 1}"
                else -> "${queue.size} TRACKS"
            },
            style = RelayType.Utility.copy(color = RelayColors.Muted),
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TransportAction(
                label = repeatLabel(playbackState.repeatMode),
                description = "Change repeat mode; current setting ${repeatDescription(playbackState.repeatMode)}",
                enabled = queue.isNotEmpty(),
                onClick = { onRepeatModeChange(playbackState.repeatMode.next()) },
                modifier = Modifier.weight(1f),
            )
            TransportAction(
                label = "SHUFFLE ${if (playbackState.shuffleEnabled) "ON" else "OFF"}",
                description = "Turn playback shuffle ${if (playbackState.shuffleEnabled) "off" else "on"}",
                enabled = queue.size > 1,
                onClick = { onShuffleEnabledChange(!playbackState.shuffleEnabled) },
                modifier = Modifier.weight(1f),
            )
            TransportAction(
                label = "MODE: $shuffleProfileName",
                description = "Choose how Relay reshuffles the queue",
                enabled = true,
                onClick = onOpenShuffleSettings,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TransportAction(
                label = "RESHUFFLE",
                description = "Apply the selected shuffle profile to this queue",
                enabled = queue.size > 1,
                onClick = onReshuffle,
                modifier = Modifier.weight(1f),
            )
            TransportAction(
                label = if (editing) "DONE" else "EDIT",
                description = if (editing) "Finish editing the queue" else "Reorder or remove queued tracks",
                enabled = queue.isNotEmpty(),
                onClick = { editing = !editing },
                modifier = Modifier.weight(1f),
            )
            TransportAction(
                label = "CLEAR",
                description = "Clear the queue and stop playback",
                enabled = queue.isNotEmpty(),
                onClick = { editing = false; onClear() },
                modifier = Modifier.weight(1f),
            )
        }
        if (queue.isEmpty()) {
            BasicText(
                "Play a track, or use PLAY NEXT and QUEUE from any track's options.",
                style = RelayType.Metadata,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        queue.forEachIndexed { index, track ->
            TrackOrderRow(
                index = index,
                track = track,
                active = index == playbackState.currentIndex,
                editing = editing,
                isFirst = index == 0,
                isLast = index == queue.lastIndex,
                onPlay = { onPlayIndex(index) },
                onRemove = { onRemoveIndex(index) },
                onMove = { delta -> onMoveIndex(index, delta) },
            )
        }
    }
}

internal fun RepeatMode.next(): RepeatMode = when (this) {
    RepeatMode.OFF -> RepeatMode.ALL
    RepeatMode.ALL -> RepeatMode.ONE
    RepeatMode.ONE -> RepeatMode.OFF
}

internal fun repeatLabel(mode: RepeatMode): String = when (mode) {
    RepeatMode.OFF -> "REPEAT OFF"
    RepeatMode.ALL -> "REPEAT QUEUE"
    RepeatMode.ONE -> "REPEAT TRACK"
}

private fun repeatDescription(mode: RepeatMode): String = when (mode) {
    RepeatMode.OFF -> "repeat off"
    RepeatMode.ALL -> "repeat the queue"
    RepeatMode.ONE -> "repeat the current track"
}
