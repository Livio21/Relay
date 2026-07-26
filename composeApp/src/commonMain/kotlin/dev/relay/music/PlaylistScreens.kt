package dev.relay.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.relay.music.model.Track
import dev.relay.music.model.Playlist
import dev.relay.music.ui.RelayColors
import dev.relay.music.ui.RelayType

@Composable
internal fun PlaylistDetailScreen(
    playlist: Playlist,
    tracks: List<Track>?,
    playingTrackKey: String?,
    onPlayTrack: (Int) -> Unit,
    onRemoveEntry: (Int) -> Unit,
    onMoveEntry: (Int, Int) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onAddTracks: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(playlist.name) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        BasicText(playlist.name.uppercase(), style = RelayType.Title)
        BasicText(
            when {
                tracks == null -> "LOADING…"
                tracks.isEmpty() -> "EMPTY PLAYLIST"
                else -> "${tracks.size} TRACKS"
            },
            style = RelayType.Utility.copy(color = RelayColors.Muted),
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TransportAction(
                label = "PLAY",
                description = "Play playlist ${playlist.name}",
                enabled = !tracks.isNullOrEmpty(),
                onClick = { onPlayTrack(0) },
                modifier = Modifier.weight(1f),
            )
            TransportAction(
                label = "ADD TRACKS",
                description = "Add library tracks to ${playlist.name}",
                enabled = true,
                onClick = onAddTracks,
                modifier = Modifier.weight(1f),
            )
            TransportAction(
                label = if (editing) "DONE" else "EDIT",
                description = if (editing) "Finish editing" else "Reorder or remove tracks",
                enabled = !tracks.isNullOrEmpty(),
                onClick = { editing = !editing },
                modifier = Modifier.weight(1f),
            )
        }
        if (editing) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TransportAction(
                    label = "RENAME",
                    description = "Rename playlist",
                    enabled = !renaming,
                    onClick = { renameValue = playlist.name; renaming = true },
                    modifier = Modifier.weight(1f),
                )
                TransportAction(
                    label = if (confirmDelete) "CONFIRM DELETE" else "DELETE",
                    description = if (confirmDelete) "Permanently delete ${playlist.name}" else "Delete playlist",
                    enabled = true,
                    onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            confirmDelete = false
        }
        if (renaming) {
            BasicTextField(
                value = renameValue,
                onValueChange = { renameValue = it },
                textStyle = RelayType.Track.copy(color = RelayColors.Paper),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .border(width = 1.dp, color = RelayColors.Line)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .semantics { contentDescription = "Playlist name" },
            )
            TransportAction(
                label = "SAVE NAME",
                description = "Save playlist name",
                enabled = renameValue.isNotBlank(),
                onClick = { onRename(renameValue); renaming = false },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        tracks?.forEachIndexed { index, track ->
            TrackOrderRow(
                index = index,
                track = track,
                active = playingTrackKey == trackKey(track),
                editing = editing,
                isFirst = index == 0,
                isLast = index == tracks.lastIndex,
                onPlay = { onPlayTrack(index) },
                onRemove = { onRemoveEntry(index) },
                onMove = { delta -> onMoveEntry(index, delta) },
            )
        }
    }
}

@Composable
internal fun PlaylistScreen(
    playlists: List<Playlist>,
    onCreatePlaylist: (String) -> Unit,
    onPlayPlaylist: (Long) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onSelect: (Long) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var menuPlaylist by remember { mutableStateOf<Playlist?>(null) }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            BasicText("PLAYLISTS", style = RelayType.Title)
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                textStyle = RelayType.Track.copy(color = RelayColors.Paper),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .border(width = 1.dp, color = RelayColors.Line)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .semantics { contentDescription = "New playlist name" },
            )
            TransportAction(
                label = "CREATE PLAYLIST",
                description = "Create playlist",
                enabled = name.isNotBlank(),
                onClick = { onCreatePlaylist(name); name = "" },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            playlists.forEach { playlist ->
                BasicText(
                    text = playlist.name,
                    style = RelayType.Track,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .border(width = 1.dp, color = RelayColors.Line)
                        .semantics { contentDescription = "Open playlist ${playlist.name}" }
                        .combinedClickable(
                            role = Role.Button,
                            onClick = { onSelect(playlist.id) },
                            onLongClick = { menuPlaylist = playlist },
                        )
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                )
            }
        }
        menuPlaylist?.let { playlist ->
            PlaylistOptionsMenu(
                playlist = playlist,
                onPlay = { onPlayPlaylist(playlist.id); menuPlaylist = null },
                onOpen = { onSelect(playlist.id); menuPlaylist = null },
                onDelete = { onDeletePlaylist(playlist.id); menuPlaylist = null },
                onDismiss = { menuPlaylist = null },
            )
        }
    }
}

@Composable
private fun BoxScope.PlaylistOptionsMenu(
    playlist: Playlist,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmDelete by remember(playlist.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(RelayColors.Panel)
            .border(1.dp, RelayColors.Line)
            .padding(12.dp),
    ) {
        BasicText(playlist.name, style = RelayType.Track)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportAction("PLAY", "Play playlist ${playlist.name}", true, onPlay, Modifier.weight(1f))
            TransportAction("OPEN", "Open playlist ${playlist.name}", true, onOpen, Modifier.weight(1f))
            TransportAction(
                if (confirmDelete) "CONFIRM" else "DELETE",
                if (confirmDelete) "Permanently delete ${playlist.name}" else "Delete playlist ${playlist.name}",
                true,
                { if (confirmDelete) onDelete() else confirmDelete = true },
                Modifier.weight(1f),
            )
        }
        TransportAction("CLOSE", "Close playlist options", true, onDismiss, Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

/** Bottom overlay used anywhere a track can be added to a playlist. */
@Composable
internal fun BoxScope.PlaylistPickerOverlay(
    track: Track,
    playlists: List<Playlist>,
    onPick: (Long) -> Unit,
    onCreateAndAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember(track) { mutableStateOf("") }
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(RelayColors.Panel)
            .border(1.dp, RelayColors.Line)
            .padding(12.dp),
    ) {
        BasicText("ADD TO PLAYLIST", style = RelayType.Utility.copy(color = RelayColors.Muted))
        BasicText(
            track.title,
            style = RelayType.Track,
            modifier = Modifier.padding(top = 4.dp),
        )
        Column(modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            if (playlists.isEmpty()) {
                BasicText("No playlists yet — create one below.", style = RelayType.Metadata)
            }
            playlists.forEach { playlist ->
                BasicText(
                    playlist.name,
                    style = RelayType.Track,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .border(1.dp, RelayColors.Line)
                        .semantics { contentDescription = "Add to ${playlist.name}" }
                        .clickable(role = Role.Button) { onPick(playlist.id) }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value = newName,
                onValueChange = { newName = it },
                textStyle = RelayType.Track.copy(color = RelayColors.Paper),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, RelayColors.Line)
                    .padding(horizontal = 12.dp, vertical = 14.dp)
                    .semantics { contentDescription = "New playlist name" },
            )
            TransportAction(
                label = "CREATE + ADD",
                description = "Create playlist and add ${track.title}",
                enabled = newName.isNotBlank(),
                onClick = { onCreateAndAdd(newName) },
            )
        }
        TransportAction("CLOSE", "Close playlist picker", true, onDismiss, Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}
