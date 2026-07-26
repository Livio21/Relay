package dev.relay.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.relay.music.model.Playlist
import dev.relay.music.model.Track
import dev.relay.music.model.TrackFlags
import dev.relay.music.playback.PlaybackState
import dev.relay.music.ui.RelayColors
import dev.relay.music.ui.RelayType
import coil3.compose.AsyncImage

@Composable
internal fun TrackList(
    tracks: List<Track>,
    playbackState: PlaybackState,
    favoriteTrackKeys: Set<String>,
    trackFlags: Map<String, TrackFlags>,
    onTrackSelected: (Track) -> Unit,
    onFavoriteToggle: (Track) -> Unit,
    onTrackFlagsChange: (Track, TrackFlags) -> Unit,
    onMetadataReview: (Track) -> Unit,
    playlists: List<Playlist>,
    onAddToPlaylist: (Long, Track) -> Unit,
    onCreateAndAddToPlaylist: (String, Track) -> Unit,
    onPlayNext: (Track) -> Unit = {},
    onEnqueue: (Track) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var pickerTrack by remember { mutableStateOf<Track?>(null) }
    Box(modifier = modifier.fillMaxSize()) {
        if (tracks.isEmpty()) {
            BasicText(
                text = "No tracks match this search.",
                style = RelayType.Metadata,
                modifier = Modifier.padding(16.dp).semantics { contentDescription = "No tracks match this search" },
            )
        }
        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(count = tracks.size, key = { tracks[it].sourceId + ":" + tracks[it].id }) { index ->
                TrackRow(
                    track = tracks[index],
                    active = playbackState.currentTrack?.let(::trackKey) == trackKey(tracks[index]),
                    favorite = trackKey(tracks[index]) in favoriteTrackKeys,
                    progress = if (playbackState.currentTrack?.let(::trackKey) == trackKey(tracks[index])) playbackProgress(playbackState) else 0f,
                    onClick = { onTrackSelected(tracks[index]) },
                    onOptions = { menuTrack = tracks[index] },
                )
                Rule()
            }
        }
        menuTrack?.let { track ->
            TrackOptionsMenu(
                track = track,
                favorite = trackKey(track) in favoriteTrackKeys,
                flags = trackFlags[trackKey(track)] ?: TrackFlags(),
                onDismiss = { menuTrack = null },
                onFavoriteToggle = { onFavoriteToggle(track); menuTrack = null },
                onMetadata = { onMetadataReview(track); menuTrack = null },
                onAddToPlaylist = { pickerTrack = track; menuTrack = null },
                onPlayNext = { onPlayNext(track); menuTrack = null },
                onEnqueue = { onEnqueue(track); menuTrack = null },
                onFlagsChange = { flags -> onTrackFlagsChange(track, flags); menuTrack = null },
            )
        }
        pickerTrack?.let { track ->
            PlaylistPickerOverlay(
                track = track,
                playlists = playlists,
                onPick = { playlistId -> onAddToPlaylist(playlistId, track); pickerTrack = null },
                onCreateAndAdd = { name -> onCreateAndAddToPlaylist(name, track); pickerTrack = null },
                onDismiss = { pickerTrack = null },
            )
        }
    }
}

@Composable
internal fun TrackRow(
    track: Track,
    active: Boolean,
    favorite: Boolean,
    progress: Float,
    onClick: () -> Unit,
    onOptions: () -> Unit,
) {
    val metadata = buildString {
        append(track.artist.ifBlank { "Unknown artist" })
        append(" — ")
        append(track.album?.takeIf { it.isNotBlank() } ?: "Unknown album")
    }
    val title = track.title.ifBlank { "Untitled track" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(if (active) RelayColors.Panel else RelayColors.Ink)
            .semantics { contentDescription = "Play $title by ${track.artist}" }
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onOptions),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(RelayColors.Line),
        ) {
            if (active) Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(progress)
                    .background(RelayColors.Signal),
            )
        }

        track.artworkUri?.let { artworkUri ->
            AsyncImage(
                model = artworkUri,
                contentDescription = "Album cover for ${track.album ?: title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .width(48.dp)
                    .height(48.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            BasicText(
                text = title,
                style = RelayType.Track,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = metadata,
                style = RelayType.Metadata,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        BasicText(
            text = track.durationMs?.let(::formatDuration) ?: "--:--",
            style = RelayType.Utility,
            modifier = Modifier.padding(start = 8.dp),
        )
        BasicText(
            text = "⋮",
            style = RelayType.Title.copy(color = RelayColors.Muted),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Options for $title" }
                .clickable(role = Role.Button, onClick = onOptions)
                .padding(horizontal = 16.dp),
        )
    }
}

@Composable
internal fun BoxScope.TrackOptionsMenu(
    track: Track,
    favorite: Boolean,
    flags: TrackFlags,
    onDismiss: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onMetadata: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onEnqueue: () -> Unit,
    onFlagsChange: (TrackFlags) -> Unit,
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(RelayColors.Panel)
            .border(1.dp, RelayColors.Line)
            .padding(12.dp),
    ) {
        BasicText(track.title.ifBlank { "Untitled track" }, style = RelayType.Track, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportAction(if (favorite) "UNSAVE" else "SAVE", "Toggle favorite", true, onFavoriteToggle, Modifier.weight(1f))
            TransportAction("METADATA", "Edit metadata", true, onMetadata, Modifier.weight(1f))
            TransportAction("PLAYLIST", "Add to a playlist", true, onAddToPlaylist, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportAction("PLAY NEXT", "Play ${track.title} after the current track", true, onPlayNext, Modifier.weight(1f))
            TransportAction("QUEUE", "Add ${track.title} to the end of the queue", true, onEnqueue, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportAction(if (flags.hidden) "UNHIDE" else "HIDE", "Toggle hidden", true, { onFlagsChange(flags.copy(hidden = !flags.hidden)) }, Modifier.weight(1f))
            TransportAction(if (flags.pinned) "UNPIN" else "PIN", "Toggle pinned", true, { onFlagsChange(flags.copy(pinned = !flags.pinned)) }, Modifier.weight(1f))
            TransportAction(if (flags.archived) "UNARCHIVE" else "ARCHIVE", "Toggle archived", true, { onFlagsChange(flags.copy(archived = !flags.archived)) }, Modifier.weight(1f))
        }
        TransportAction("CLOSE", "Close track options", true, onDismiss, Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

internal fun trackKey(track: Track): String = "${track.sourceId}\u0000${track.id}"

@Composable
internal fun EmptyLibrary() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "No music found in the Relay music folder.",
            style = RelayType.Metadata,
            modifier = Modifier.semantics { contentDescription = "No music found in the Relay music folder" },
        )
    }
}

@Composable
internal fun LibraryMessage(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = message,
            style = RelayType.Metadata.copy(textAlign = TextAlign.Center),
            modifier = Modifier.semantics { contentDescription = message },
        )
        TransportAction(
            label = actionLabel,
            description = actionLabel.lowercase(),
            enabled = true,
            onClick = onAction,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/** Search and sort controls above the library list. */
@Composable
internal fun LibraryControls(
    query: String,
    sort: LibrarySort,
    resultCount: Int,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        MetadataField("SEARCH LIBRARY", query, onQueryChange)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "$resultCount TRACKS",
                style = RelayType.Utility.copy(color = RelayColors.Muted),
                modifier = Modifier.weight(1f),
            )
            TransportAction(
                label = "SORT ${sort.name}",
                description = "Change library sort order",
                enabled = true,
                onClick = { onSortChange(LibrarySort.entries[(sort.ordinal + 1) % LibrarySort.entries.size]) },
            )
            if (query.isNotEmpty()) {
                TransportAction("CLEAR", "Clear the library search", true, { onQueryChange("") })
            }
        }
    }
}
