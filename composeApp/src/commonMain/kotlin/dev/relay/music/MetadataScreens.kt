package dev.relay.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.relay.music.model.Track
import dev.relay.music.model.MetadataCandidate
import dev.relay.music.model.MetadataOverride
import dev.relay.music.ui.RelayColors
import dev.relay.music.ui.RelayType

@Composable
internal fun MetadataBanner(onReview: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RelayColors.Panel)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = "Some tracks need metadata",
            style = RelayType.Metadata,
            modifier = Modifier.weight(1f),
        )
        BasicText(
            text = "REVIEW",
            style = RelayType.Utility.copy(color = RelayColors.Signal),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Review tracks that need metadata" }
                .clickable(role = Role.Button, onClick = onReview)
                .padding(start = 16.dp),
        )
    }
}

@Composable
internal fun MetadataReview(
    track: Track,
    candidates: List<MetadataCandidate>,
    searchMessage: String?,
    onCancel: () -> Unit,
    onSearch: (Track, String, String, Boolean) -> Unit,
    onIgnore: () -> Unit,
    onSave: (MetadataOverride) -> Unit,
) {
    var title by remember(track.sourceId, track.id) { mutableStateOf(track.title) }
    var artist by remember(track.sourceId, track.id) { mutableStateOf(track.artist) }
    var album by remember(track.sourceId, track.id) { mutableStateOf(track.album.orEmpty()) }
    var albumArtist by remember(track.sourceId, track.id) { mutableStateOf(track.albumArtist.orEmpty()) }
    var artworkUri by remember(track.sourceId, track.id) { mutableStateOf(track.artworkUri.orEmpty()) }
    var musicBrainzId by remember(track.sourceId, track.id) { mutableStateOf(track.musicBrainzId.orEmpty()) }
    var musicBrainzReleaseId by remember(track.sourceId, track.id) { mutableStateOf(track.musicBrainzReleaseId.orEmpty()) }
    var trackNumber by remember(track.sourceId, track.id) { mutableStateOf(track.trackNumber?.toString().orEmpty()) }
    var discNumber by remember(track.sourceId, track.id) { mutableStateOf(track.discNumber?.toString().orEmpty()) }
    var manualSearchOpen by remember(track.sourceId, track.id) { mutableStateOf(false) }
    var manualTitle by remember(track.sourceId, track.id) { mutableStateOf(track.title) }
    var manualArtist by remember(track.sourceId, track.id) { mutableStateOf(track.artist) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        BasicText("METADATA REVIEW", style = RelayType.Title)
        BasicText(
            "Edit the values kept by Relay. Your original audio files are unchanged.",
            style = RelayType.Metadata,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        MetadataField("TITLE", title, { title = it })
        MetadataField("ARTIST", artist, { artist = it })
        MetadataField("ALBUM", album, { album = it })
        MetadataField("ALBUM ARTIST", albumArtist, { albumArtist = it })
        MetadataField("TRACK NUMBER", trackNumber, { trackNumber = it.filter(Char::isDigit).take(3) })
        MetadataField("DISC NUMBER", discNumber, { discNumber = it.filter(Char::isDigit).take(2) })
        MetadataField("ARTWORK URI", artworkUri, { artworkUri = it })
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportAction(
                label = "AUTOCOMPLETE (MUSICBRAINZ)",
                description = "Find MusicBrainz suggestions from current metadata",
                enabled = title.isNotBlank(),
                onClick = { onSearch(track, title, artist, false) },
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .border(1.dp, RelayColors.Line)
                    .semantics { contentDescription = "Open manual MusicBrainz search" }
                    .clickable(role = Role.Button) { manualSearchOpen = !manualSearchOpen },
                contentAlignment = Alignment.Center,
            ) { BasicText("⌕", style = RelayType.Title.copy(textAlign = TextAlign.Center)) }
        }
        if (manualSearchOpen) {
            BasicText("MANUAL SEARCH", style = RelayType.Utility, modifier = Modifier.padding(top = 16.dp))
            MetadataField("SEARCH TITLE", manualTitle, { manualTitle = it })
            MetadataField("SEARCH ARTIST", manualArtist, { manualArtist = it })
            TransportAction(
                label = "SEARCH / REFRESH",
                description = "Refresh MusicBrainz results without using Relay's lookup cache",
                enabled = manualTitle.isNotBlank(),
                onClick = { onSearch(track, manualTitle, manualArtist, true) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        searchMessage?.let { message ->
            BasicText(message, style = RelayType.Metadata, modifier = Modifier.padding(top = 8.dp))
        }
        candidates.forEach { candidate ->
            MetadataCandidateRow(candidate) { selected ->
                title = selected.title
                artist = selected.artist
                album = selected.album.orEmpty()
                albumArtist = selected.albumArtist.orEmpty()
                musicBrainzId = selected.recordingId
                musicBrainzReleaseId = selected.releaseId.orEmpty()
                artworkUri = selected.artworkUri.orEmpty()
                trackNumber = selected.trackNumber?.toString().orEmpty()
                discNumber = selected.discNumber?.toString().orEmpty()
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TransportAction(
                label = "CANCEL",
                description = "Cancel metadata review",
                enabled = true,
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            TransportAction(
                label = "SAVE",
                description = "Save metadata changes",
                enabled = true,
                onClick = {
                    onSave(
                        MetadataOverride(
                            title = title.trim().takeIf { it.isNotEmpty() },
                            artist = artist.trim().takeIf { it.isNotEmpty() },
                            album = album.trim().takeIf { it.isNotEmpty() },
                            albumArtist = albumArtist.trim().takeIf { it.isNotEmpty() },
                            artworkUri = artworkUri.trim().takeIf { it.isNotEmpty() },
                            musicBrainzId = musicBrainzId.takeIf { it.isNotEmpty() },
                            trackNumber = trackNumber.toIntOrNull()?.takeIf { it > 0 },
                            discNumber = discNumber.toIntOrNull()?.takeIf { it > 0 },
                            musicBrainzReleaseId = musicBrainzReleaseId.takeIf { it.isNotEmpty() },
                        ),
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportAction("SKIP", "Do not suggest this metadata again until the file changes", true, onIgnore, Modifier.weight(1f))
            TransportAction("NOT A PROBLEM", "Do not suggest this metadata again until the file changes", true, onIgnore, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun MetadataCandidateRow(candidate: MetadataCandidate, onApply: (MetadataCandidate) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = RelayColors.Line)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(candidate.title, style = RelayType.Track, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(
                "${candidate.artist} — ${candidate.album ?: "Unknown album"}",
                style = RelayType.Metadata,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicText(
            text = "APPLY",
            style = RelayType.Utility.copy(color = RelayColors.Signal),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Apply metadata suggestion for ${candidate.title}" }
                .clickable(role = Role.Button) { onApply(candidate) }
                .padding(horizontal = 12.dp),
        )
    }
}

@Composable
internal fun MetadataField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        BasicText(label, style = RelayType.Utility)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = RelayType.Track,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = RelayColors.Line)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .semantics { contentDescription = label.lowercase() },
        )
    }
}
