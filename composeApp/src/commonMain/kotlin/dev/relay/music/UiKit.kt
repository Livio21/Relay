package dev.relay.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.relay.music.model.Track
import dev.relay.music.ui.RelayColors
import dev.relay.music.ui.RelayChrome
import dev.relay.music.ui.RelayType
import dev.relay.music.ui.relayBorder
import dev.relay.music.ui.relayChrome

@Composable
internal fun TransportAction(
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionModifier = if (enabled) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier.semantics { disabled() }
    }

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .relayChrome()
            .relayBorder()
            .semantics { contentDescription = description }
            .then(actionModifier),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label.asThemeActionLabel(),
            style = RelayType.Utility.copy(color = if (enabled) RelayColors.Paper else RelayColors.Muted),
        )
    }
}

/** A bounded icon set can restyle standard controls without accepting icon files from packs. */
private fun String.asThemeActionLabel(): String = when (RelayChrome.iconSet) {
    dev.relay.music.extension.ThemeIconSet.TEXT -> this
    dev.relay.music.extension.ThemeIconSet.SYMBOLS -> when (this) {
        "PREV" -> "◀◀"
        "PLAY" -> "▶"
        "PAUSE" -> "Ⅱ"
        "NEXT" -> "▶▶"
        "MORE" -> "⋯"
        "CLOSE" -> "×"
        "QUEUE" -> "≡"
        "LYRICS" -> "♪"
        "SHUFFLE" -> "⇄"
        else -> this
    }
}

/** One numbered row of an ordered track list (playlist or queue), with optional edit controls. */
@Composable
internal fun TrackOrderRow(
    index: Int,
    track: Track,
    active: Boolean,
    editing: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().relayBorder()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics { contentDescription = "Play ${track.title}" }
                .clickable(role = Role.Button, onClick = onPlay),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = (index + 1).toString().padStart(2, '0'),
                style = RelayType.Utility.copy(color = if (active) RelayColors.Signal else RelayColors.Muted),
                modifier = Modifier.width(36.dp).padding(start = 12.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                BasicText(track.title, style = RelayType.Track.copy(color = if (active) RelayColors.Signal else RelayColors.Paper))
                BasicText(
                    listOfNotNull(track.artist, track.album).joinToString(" — "),
                    style = RelayType.Metadata,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            track.durationMs?.let { duration ->
                BasicText(
                    formatDuration(duration),
                    style = RelayType.Utility.copy(color = RelayColors.Muted),
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
        if (editing) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TransportAction("UP", "Move ${track.title} up", !isFirst, { onMove(-1) }, Modifier.weight(1f))
                TransportAction("DOWN", "Move ${track.title} down", !isLast, { onMove(1) }, Modifier.weight(1f))
                TransportAction("REMOVE", "Remove ${track.title} from playlist", true, onRemove, Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun Rule() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(RelayColors.Line),
    )
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0) / 1_000).toInt()
    val seconds = totalSeconds % 60
    val minutes = totalSeconds / 60
    return if (minutes >= 60) {
        "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}

fun formatFileSize(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0)
    return when {
        safe >= 1_000_000_000 -> "${safe / 100_000_000 / 10.0} GB"
        safe >= 1_000_000 -> "${safe / 100_000 / 10.0} MB"
        safe >= 1_000 -> "${safe / 1_000} KB"
        else -> "$safe B"
    }
}
