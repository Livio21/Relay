package dev.relay.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.relay.music.haptics.HapticAction
import dev.relay.music.haptics.LocalRelayHaptics
import dev.relay.music.model.Playlist
import dev.relay.music.model.Track
import dev.relay.music.model.TrackFlags
import dev.relay.music.playback.PlaybackState
import dev.relay.music.ui.RelayColors
import dev.relay.music.ui.RelayMarqueeText
import dev.relay.music.ui.RelayIcon
import dev.relay.music.ui.RelayIconButton
import dev.relay.music.ui.RelayIcons
import dev.relay.music.ui.RelayType
import dev.relay.music.ui.asThemedTitle
import dev.relay.music.ui.relayBorder

internal enum class LibraryBrowseMode(val label: String) {
    TRACKS("TRACKS"),
    ALBUMS("ALBUMS"),
    ARTISTS("ARTISTS"),
}

/** The library's three browse modes, styled as flat segmented text buttons. */
@Composable
internal fun LibraryBrowseModes(
    selected: LibraryBrowseMode,
    onSelect: (LibraryBrowseMode) -> Unit,
) {
    val haptics = LocalRelayHaptics.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibraryBrowseMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(if (isSelected) RelayColors.Panel else RelayColors.Ink)
                    .relayBorder()
                    .semantics(mergeDescendants = true) {
                        this.selected = isSelected
                        contentDescription = "Show ${mode.label.lowercase()}"
                    }
                    .clickable(role = Role.Tab) {
                        if (!isSelected) {
                            haptics.perform(HapticAction.TAB_CHANGE)
                            onSelect(mode)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    mode.label,
                    style = RelayType.Utility.copy(
                        color = if (isSelected) RelayColors.Signal else RelayColors.Muted,
                    ),
                )
            }
        }
    }
}

internal enum class LibraryDensity(val label: String) {
    THREE("3-UP"),
    TWO("2-UP"),
    LIST("LIST"),
}

/** Density is a host-rendered view choice, not a theme-pack value. */
@Composable
internal fun LibraryDensityPicker(
    selected: LibraryDensity,
    onSelect: (LibraryDensity) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        LibraryDensity.entries.forEach { density ->
            RelayIconButton(
                icon = when (density) {
                    LibraryDensity.THREE -> RelayIcons.Grid
                    LibraryDensity.TWO -> RelayIcons.Agenda
                    LibraryDensity.LIST -> RelayIcons.List
                },
                description = "Show ${density.label.lowercase()}",
                onClick = { onSelect(density) },
                tint = if (density == selected) RelayColors.Signal else RelayColors.Muted,
                hapticAction = HapticAction.TAB_CHANGE,
            )
        }
    }
}

@Composable
internal fun AlbumBrowser(
    albums: List<AlbumGroup>,
    emptyMessage: String,
    onOpen: (AlbumGroup) -> Unit,
    density: LibraryDensity = LibraryDensity.THREE,
    currentAlbumKey: String? = null,
    modifier: Modifier = Modifier,
) {
    if (albums.isEmpty()) {
        BrowseEmpty(emptyMessage, modifier)
        return
    }
    if (density == LibraryDensity.LIST) {
        LazyColumn(modifier = modifier.fillMaxSize()) {
            items(albums, key = { it.key }) { album ->
                AlbumListRow(
                    album = album,
                    current = album.key == currentAlbumKey,
                    onOpen = onOpen,
                )
                Rule()
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (density == LibraryDensity.THREE) 3 else 2),
        contentPadding = PaddingValues(0.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.key }) { album ->
            AlbumTile(
                album = album,
                density = density,
                current = album.key == currentAlbumKey,
                onOpen = onOpen,
            )
        }
    }
}

/** Small, low-priority recent strip for the bottom of Library. */
@Composable
internal fun RecentlyPlayedStrip(
    tracks: List<Track>,
    currentTrackKey: String?,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText("RECENT", style = RelayType.Kicker.copy(fontSize = 8.sp))
        tracks.take(3).forEachIndexed { index, track ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clickable(role = Role.Button) { onPlay(index) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(32.dp).then(if (trackKey(track) == currentTrackKey) Modifier.border(1.dp, RelayColors.Signal) else Modifier)) {
                    artworkOrPlaceholder(track.artworkUri, "NO ART")
                }
                RelayMarqueeText(
                    text = track.title.asThemedTitle(),
                    style = RelayType.TileTitle.copy(fontSize = 9.sp),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Artist sections: a vertical list, each with a horizontal 2.5-card rail of tracks. */
@Composable
internal fun ArtistSections(
    artists: List<ArtistGroup>,
    currentTrackKey: String?,
    onOpenArtist: (ArtistGroup) -> Unit,
    onPlayTrack: (ArtistGroup, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (artists.isEmpty()) {
        BrowseEmpty("No artists found.", modifier)
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(artists, key = { it.key }) { artist ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onOpenArtist(artist) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    RelayMarqueeText(
                        text = artist.name.asThemedTitle(),
                        style = RelayType.HeroArtist.copy(fontSize = 24.sp, lineHeight = 26.sp),
                        modifier = Modifier.weight(1f),
                    )
                    BasicText(
                        "${albumCountLabel(artist.albumCount)} · ${trackCountLabel(artist.trackCount)}",
                        style = RelayType.Kicker.copy(fontSize = 8.sp),
                        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                    )
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    itemsIndexed(
                        artist.tracks.take(8),
                        key = { _, track -> "${artist.key}-${track.sourceId}-${track.id}" },
                    ) { index, track ->
                        Column(
                            modifier = Modifier
                                .width(150.dp)
                                .clickable(role = Role.Button) { onPlayTrack(artist, index) },
                        ) {
                            Box(Modifier.size(150.dp)) {
                                artworkOrPlaceholder(track.artworkUri, "NO ART")
                                if (trackKey(track) == currentTrackKey) {
                                    Box(Modifier.matchParentSize().border(2.dp, RelayColors.Signal))
                                }
                            }
                            RelayMarqueeText(
                                text = track.title.asThemedTitle(),
                                style = RelayType.TileTitle.copy(fontSize = 11.sp),
                                modifier = Modifier.padding(top = 5.dp, end = 4.dp),
                            )
                            RelayMarqueeText(
                                text = track.album.orEmpty(),
                                style = RelayType.Kicker.copy(fontSize = 8.sp),
                                modifier = Modifier.padding(top = 1.dp, bottom = 4.dp, end = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumTile(
    album: AlbumGroup,
    density: LibraryDensity,
    current: Boolean,
    onOpen: (AlbumGroup) -> Unit,
) {
    val lineWidth = with(androidx.compose.ui.platform.LocalDensity.current) { 1.dp.toPx() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpen(album) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .drawBehind {
                    drawLine(RelayColors.Line, Offset(size.width - lineWidth, 0f), Offset(size.width - lineWidth, size.height), lineWidth)
                    drawLine(RelayColors.Line, Offset(0f, size.height - lineWidth), Offset(size.width, size.height - lineWidth), lineWidth)
                }
                .then(if (current) Modifier.border(2.dp, RelayColors.Signal) else Modifier),
        ) {
            artworkOrPlaceholder(album.artworkUri, "NO ART")
        }
        Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
            RelayMarqueeText(
                text = album.title.asThemedTitle(),
                style = if (density == LibraryDensity.THREE) RelayType.TileTitle else RelayType.TileTitleLarge,
            )
            if (density == LibraryDensity.TWO) {
                RelayMarqueeText(
                    text = listOfNotNull(album.artist, album.year).joinToString(" · "),
                    style = RelayType.Kicker.copy(fontSize = 10.sp),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun AlbumListRow(
    album: AlbumGroup,
    current: Boolean,
    onOpen: (AlbumGroup) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(role = Role.Button) { onOpen(album) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .size(52.dp)
                .then(if (current) Modifier.border(2.dp, RelayColors.Signal) else Modifier),
        ) {
            artworkOrPlaceholder(album.artworkUri, "NO ART")
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            RelayMarqueeText(
                text = album.title.asThemedTitle(),
                style = RelayType.TileTitleLarge,
            )
            RelayMarqueeText(
                text = listOfNotNull(album.artist, album.year).joinToString(" · "),
                style = RelayType.Kicker.copy(fontSize = 10.sp),
            )
        }
    }
}

@Composable
private fun artworkOrPlaceholder(uri: String?, label: String) {
    if (uri != null) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize().background(RelayColors.Panel),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(label, style = RelayType.Utility)
        }
    }
}

@Composable
internal fun ArtistBrowser(
    artists: List<ArtistGroup>,
    emptyMessage: String,
    onOpen: (ArtistGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (artists.isEmpty()) {
        BrowseEmpty(emptyMessage, modifier)
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(artists, key = { it.key }) { artist ->
            ArtistRow(artist, onOpen)
            Rule()
        }
    }
}

@Composable
internal fun AlbumDetail(
    album: AlbumGroup,
    playbackState: PlaybackState,
    favoriteTrackKeys: Set<String>,
    trackFlags: Map<String, TrackFlags>,
    playlists: List<Playlist>,
    onPlayIndex: (Int) -> Unit,
    onFavoriteToggle: (Track) -> Unit,
    onTrackFlagsChange: (Track, TrackFlags) -> Unit,
    onMetadataReview: (Track) -> Unit,
    onAddToPlaylist: (Long, Track) -> Unit,
    onCreateAndAddToPlaylist: (String, Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onEnqueue: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    CollectionDetail(
        artworkUri = album.artworkUri,
        title = album.title,
        subtitle = listOfNotNull(album.artist, album.year).joinToString(" · "),
        meta = "${trackCountLabel(album.trackCount)} · ${formatDuration(album.durationMs)}",
        playDescription = "Play album ${album.title}",
        onPlay = { onPlayIndex(0) },
        modifier = modifier,
    ) {
        TrackList(
            tracks = album.tracks,
            playbackState = playbackState,
            favoriteTrackKeys = favoriteTrackKeys,
            trackFlags = trackFlags,
            onTrackSelected = { track ->
                val index = album.tracks.indexOfFirst { trackKey(it) == trackKey(track) }
                if (index >= 0) onPlayIndex(index)
            },
            onFavoriteToggle = onFavoriteToggle,
            onTrackFlagsChange = onTrackFlagsChange,
            onMetadataReview = onMetadataReview,
            playlists = playlists,
            onAddToPlaylist = onAddToPlaylist,
            onCreateAndAddToPlaylist = onCreateAndAddToPlaylist,
            onPlayNext = onPlayNext,
            onEnqueue = onEnqueue,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun ArtistDetail(
    artist: ArtistGroup,
    playbackState: PlaybackState,
    favoriteTrackKeys: Set<String>,
    trackFlags: Map<String, TrackFlags>,
    playlists: List<Playlist>,
    onPlayIndex: (Int) -> Unit,
    onOpenAlbum: (AlbumGroup) -> Unit,
    onFavoriteToggle: (Track) -> Unit,
    onTrackFlagsChange: (Track, TrackFlags) -> Unit,
    onMetadataReview: (Track) -> Unit,
    onAddToPlaylist: (Long, Track) -> Unit,
    onCreateAndAddToPlaylist: (String, Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onEnqueue: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    CollectionDetail(
        artworkUri = artist.artworkUri,
        title = artist.name,
        subtitle = "ARTIST",
        meta = "${albumCountLabel(artist.albumCount)} · ${trackCountLabel(artist.trackCount)} · ${formatDuration(artist.durationMs)}",
        playDescription = "Play all tracks by ${artist.name}",
        onPlay = { onPlayIndex(0) },
        modifier = modifier,
    ) {
        if (artist.albums.isNotEmpty()) {
            BasicText(
                "ALBUMS",
                style = RelayType.Track,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(artist.albums, key = { it.key }) { album ->
                    AlbumCard(album, width = 156.dp, showArtist = false, onOpen = onOpenAlbum)
                }
            }
            Spacer(Modifier.height(16.dp))
            Rule()
        }
        BasicText(
            "TRACKS",
            style = RelayType.Track,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        TrackList(
            tracks = artist.tracks,
            playbackState = playbackState,
            favoriteTrackKeys = favoriteTrackKeys,
            trackFlags = trackFlags,
            onTrackSelected = { track ->
                val index = artist.tracks.indexOfFirst { trackKey(it) == trackKey(track) }
                if (index >= 0) onPlayIndex(index)
            },
            onFavoriteToggle = onFavoriteToggle,
            onTrackFlagsChange = onTrackFlagsChange,
            onMetadataReview = onMetadataReview,
            playlists = playlists,
            onAddToPlaylist = onAddToPlaylist,
            onCreateAndAddToPlaylist = onCreateAndAddToPlaylist,
            onPlayNext = onPlayNext,
            onEnqueue = onEnqueue,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Shared header for an album, artist or playlist. */
@Composable
internal fun CollectionHeader(
    artworkUri: String?,
    title: String,
    subtitle: String,
    meta: String,
    playDescription: String,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = Modifier.size(150.dp).background(RelayColors.Panel)) {
            artworkOrPlaceholder(artworkUri, "NO ART")
        }
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            BasicText(subtitle, style = RelayType.Kicker)
            RelayMarqueeText(
                text = title.asThemedTitle(),
                style = RelayType.HeroTitle.copy(fontSize = 36.sp, lineHeight = 34.sp),
                modifier = Modifier.padding(top = 4.dp),
            )
            RelayMarqueeText(
                text = meta,
                style = RelayType.Kicker,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(RelayColors.Signal, CircleShape)
                        .semantics { contentDescription = playDescription }
                        .clickable(role = Role.Button, onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    RelayIcon(RelayIcons.Play, description = null, tint = RelayColors.Ink, size = 28.dp)
                }
                Row(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}

/** Universal detail layout: shared header + the track/section content below. */
@Composable
internal fun CollectionDetail(
    artworkUri: String?,
    title: String,
    subtitle: String,
    meta: String,
    playDescription: String,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    trackContent: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        CollectionHeader(
            artworkUri = artworkUri,
            title = title,
            subtitle = subtitle,
            meta = meta,
            playDescription = playDescription,
            onPlay = onPlay,
            actions = actions,
        )
        Rule()
        trackContent()
    }
}

@Composable
private fun AlbumCard(
    album: AlbumGroup,
    width: Dp? = null,
    showArtist: Boolean = true,
    onOpen: (AlbumGroup) -> Unit,
) {
    Column(
        modifier = Modifier
            .then(if (width == null) Modifier.fillMaxWidth() else Modifier.width(width))
            .semantics(mergeDescendants = true) {
                contentDescription = "Open album ${album.title} by ${album.artist}"
            }
            .clickable(role = Role.Button) { onOpen(album) },
    ) {
        Artwork(album.artworkUri, album.title, Modifier.fillMaxWidth().aspectRatio(1f))
        RelayMarqueeText(
            text = album.title.asThemedTitle(),
            style = RelayType.Track,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp),
        )
        val meta = if (showArtist) {
            listOfNotNull(album.artist, album.year).joinToString(" · ")
        } else {
            album.year.orEmpty()
        }
        if (meta.isNotEmpty()) {
            RelayMarqueeText(
                text = meta,
                style = RelayType.Metadata,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp),
            )
        }
        BasicText(
            "${trackCountLabel(album.trackCount)} · ${formatDuration(album.durationMs)}",
            style = RelayType.Utility,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun ArtistRow(artist: ArtistGroup, onOpen: (ArtistGroup) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Open artist ${artist.name}"
            }
            .clickable(role = Role.Button) { onOpen(artist) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(RelayColors.Panel).relayBorder(),
            contentAlignment = Alignment.Center,
        ) {
            if (artist.artworkUri != null) {
                AsyncImage(
                    model = artist.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                BasicText(
                    artistInitials(artist.name),
                    style = RelayType.Track.copy(color = RelayColors.Muted),
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            RelayMarqueeText(
                text = artist.name.asThemedTitle(),
                style = RelayType.Track,
            )
            BasicText(
                "${albumCountLabel(artist.albumCount)} · ${trackCountLabel(artist.trackCount)}",
                style = RelayType.Metadata,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun Artwork(artworkUri: String?, title: String, modifier: Modifier) {
    Box(
        modifier = modifier.background(RelayColors.Panel).relayBorder(),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUri != null) {
            AsyncImage(
                model = artworkUri,
                contentDescription = "Artwork for $title",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            BasicText(
                "NO ARTWORK",
                style = RelayType.Utility,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Composable
private fun BrowseEmpty(message: String, modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopStart) {
        BasicText(message, style = RelayType.Metadata)
    }
}

private fun trackCountLabel(count: Int): String = if (count == 1) "1 track" else "$count tracks"

private fun albumCountLabel(count: Int): String = if (count == 1) "1 album" else "$count albums"

private fun artistInitials(name: String): String =
    name.split(' ')
        .asSequence()
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifBlank { "?" }
