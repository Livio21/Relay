package dev.relay.music

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.relay.music.model.InsightEntry
import dev.relay.music.model.InsightsRange
import dev.relay.music.model.AlbumChartSpec
import dev.relay.music.model.ListeningDay
import dev.relay.music.model.ListeningEvent
import dev.relay.music.model.LocalProfile
import dev.relay.music.model.albumChartEntries
import dev.relay.music.model.insightsFor
import dev.relay.music.ui.RelayColors
import dev.relay.music.ui.RelayType

@Composable
internal fun InsightsScreen(
    events: List<ListeningEvent>,
    nowEpochMs: Long,
    onImportLastFmHistory: (() -> Unit)? = null,
    importMessage: String? = null,
    profile: LocalProfile? = null,
    onProfileNameChange: (String) -> Unit = {},
    onUnlinkLastFmHistory: (Boolean) -> Unit = {},
    chartSpecs: List<AlbumChartSpec> = emptyList(),
    onCreateAlbumChart: (InsightsRange, Int) -> Unit = { _, _ -> },
    onRemoveAlbumChart: (String) -> Unit = {},
    onExportAlbumChart: (AlbumChartSpec) -> Unit = {},
) {
    var range by remember { mutableStateOf(InsightsRange.MONTH) }
    val insights = remember(events, range, nowEpochMs) { insightsFor(events, range, nowEpochMs) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        profile?.let { localProfile ->
            LocalProfilePanel(localProfile, onProfileNameChange, onUnlinkLastFmHistory)
        }
        onImportLastFmHistory?.let { import ->
            TransportAction(
                label = "IMPORT LAST.FM HISTORY",
                description = "Import the latest 1000 Last.fm scrobbles into this device's local history",
                enabled = true,
                onClick = import,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        importMessage?.let { message ->
            BasicText(
                message,
                style = RelayType.Metadata.copy(color = RelayColors.Muted),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            InsightsRange.entries.forEach { candidate ->
                BasicText(
                    text = candidate.label(),
                    style = RelayType.Utility.copy(
                        color = if (candidate == range) RelayColors.Signal else RelayColors.Muted,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .border(1.dp, RelayColors.Line)
                        .semantics { contentDescription = "Show ${candidate.label()} insights" }
                        .clickable(role = Role.Tab) { range = candidate }
                        .padding(horizontal = 4.dp, vertical = 13.dp),
                )
            }
        }

        if (insights.plays == 0) {
            BasicText(
                "No plays recorded in this period. Relay counts a play when a track starts.",
                style = RelayType.Metadata,
                modifier = Modifier.padding(top = 16.dp),
            )
            return@Column
        }

        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp).border(1.dp, RelayColors.Line).padding(12.dp)) {
            BasicText("${insights.plays} PLAYS", style = RelayType.Title)
            BasicText(
                text = if (insights.timedPlays > 0) {
                    "≈ ${formatListeningTime(insights.estimatedMs)} of music"
                } else {
                    "Listening time unavailable — no track lengths recorded"
                },
                style = RelayType.Metadata,
                modifier = Modifier.padding(top = 4.dp),
            )
            BasicText(
                text = "DATA SOURCE · THIS DEVICE'S PLAYBACK HISTORY",
                style = RelayType.Utility.copy(color = RelayColors.Muted),
                modifier = Modifier.padding(top = 8.dp),
            )
            // The plan requires unknowns be visible rather than quietly excluded.
            if (insights.untimedPlays > 0) {
                BasicText(
                    "${insights.untimedPlays} plays have no known length and are counted but not timed.",
                    style = RelayType.Metadata,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        InsightsSection("TOP TRACKS", insights.topTracks)
        InsightsSection("TOP ARTISTS", insights.topArtists)
        InsightsSection("TOP ALBUMS", insights.topAlbums)
        AlbumCharts(
            events = events,
            nowEpochMs = nowEpochMs,
            range = range,
            specs = chartSpecs,
            onCreate = onCreateAlbumChart,
            onRemove = onRemoveAlbumChart,
            onExport = onExportAlbumChart,
        )
        ListeningCalendar(insights.listeningDays, nowEpochMs)
    }
}

@Composable
private fun LocalProfilePanel(
    profile: LocalProfile,
    onProfileNameChange: (String) -> Unit,
    onUnlinkLastFmHistory: (Boolean) -> Unit,
) {
    var editing by remember(profile.displayName) { mutableStateOf(false) }
    var draft by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var confirmingImportedRemoval by remember(profile.lastFmUsername) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().border(1.dp, RelayColors.Line).padding(12.dp),
    ) {
        BasicText("LOCAL PROFILE", style = RelayType.Utility.copy(color = RelayColors.Muted))
        if (editing) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it.take(64) },
                textStyle = RelayType.Track.copy(color = RelayColors.Paper),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).border(1.dp, RelayColors.Line).padding(12.dp),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransportAction("SAVE", "Save local profile name", draft.trim().isNotEmpty(), {
                    onProfileNameChange(draft)
                    editing = false
                }, Modifier.weight(1f))
                TransportAction("CANCEL", "Cancel profile name edit", true, { editing = false }, Modifier.weight(1f))
            }
        } else {
            BasicText(profile.displayName, style = RelayType.Track, modifier = Modifier.padding(top = 4.dp))
            profile.lastFmUsername?.let { username ->
                BasicText("LAST.FM · $username", style = RelayType.Metadata, modifier = Modifier.padding(top = 4.dp))
            }
            TransportAction(
                label = "EDIT PROFILE",
                description = "Edit local profile name",
                enabled = true,
                onClick = { editing = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            if (profile.lastFmUsername != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TransportAction(
                        "UNLINK LAST.FM",
                        "Remove the Last.fm association and keep imported listening history",
                        true,
                        { onUnlinkLastFmHistory(false) },
                        Modifier.weight(1f),
                    )
                    TransportAction(
                        "REMOVE IMPORTED",
                        "Remove the Last.fm association and its imported listening history",
                        true,
                        { confirmingImportedRemoval = true },
                        Modifier.weight(1f),
                    )
                }
            }
            if (confirmingImportedRemoval) {
                BasicText(
                    "Remove imported Last.fm history from this device? Local plays stay untouched.",
                    style = RelayType.Metadata.copy(color = RelayColors.Muted),
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TransportAction(
                        "REMOVE",
                        "Confirm removal of imported Last.fm history",
                        true,
                        {
                            onUnlinkLastFmHistory(true)
                            confirmingImportedRemoval = false
                        },
                        Modifier.weight(1f),
                    )
                    TransportAction(
                        "CANCEL",
                        "Keep imported Last.fm history",
                        true,
                        { confirmingImportedRemoval = false },
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumCharts(
    events: List<ListeningEvent>,
    nowEpochMs: Long,
    range: InsightsRange,
    specs: List<AlbumChartSpec>,
    onCreate: (InsightsRange, Int) -> Unit,
    onRemove: (String) -> Unit,
    onExport: (AlbumChartSpec) -> Unit,
) {
    var size by remember { mutableStateOf(9) }
    BasicText("ALBUM CHARTS", style = RelayType.Track, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TransportAction(
            "SIZE · $size",
            "Set album chart size",
            true,
            { size = when (size) { 4 -> 9; 9 -> 16; else -> 4 } },
            Modifier.weight(1f),
        )
        TransportAction(
            "SAVE $size-ALBUM CHART",
            "Save a reproducible album chart for the selected time range",
            events.isNotEmpty(),
            { onCreate(range, size) },
            Modifier.weight(1f),
        )
    }
    specs.forEach { spec ->
        val entries = remember(events, spec, nowEpochMs) { albumChartEntries(events, spec, nowEpochMs) }
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).border(1.dp, RelayColors.Line).padding(12.dp)) {
            BasicText("${spec.range.label()} · ${spec.limit} ALBUMS", style = RelayType.Utility.copy(color = RelayColors.Muted))
            if (entries.isEmpty()) {
                BasicText("No album data for this chart.", style = RelayType.Metadata, modifier = Modifier.padding(top = 4.dp))
            } else {
                entries.forEachIndexed { index, entry ->
                    BasicText(
                        "${(index + 1).toString().padStart(2, '0')}  ${entry.label}",
                        style = RelayType.Metadata,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransportAction("SHARE", "Create and share this album chart", entries.isNotEmpty(), { onExport(spec) }, Modifier.weight(1f))
                TransportAction("REMOVE", "Remove this saved album chart", true, { onRemove(spec.id) }, Modifier.weight(1f))
            }
        }
    }
}

/** A four-week, UTC-aligned activity view; detailed daily counts remain available to TalkBack. */
@Composable
private fun ListeningCalendar(days: List<ListeningDay>, nowEpochMs: Long) {
    val playsByDay = remember(days) { days.associate { it.epochDay to it.plays } }
    val today = nowEpochMs / DAY_MS
    val start = today - 27
    val peak = playsByDay.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    BasicText("LISTENING CALENDAR", style = RelayType.Track, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
    Column(modifier = Modifier.fillMaxWidth().border(1.dp, RelayColors.Line).padding(4.dp)) {
        (0 until 28).chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { offset ->
                    val day = start + offset
                    val plays = playsByDay[day] ?: 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 28.dp)
                            .background(if (plays == 0) RelayColors.Panel else RelayColors.Signal.copy(alpha = 0.25f + 0.75f * plays / peak))
                            .semantics { contentDescription = if (plays == 0) "No plays" else "$plays plays" },
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightsSection(title: String, entries: List<InsightEntry>) {
    if (entries.isEmpty()) return
    BasicText(title, style = RelayType.Track, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
    entries.forEachIndexed { index, entry ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .border(1.dp, RelayColors.Line)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = (index + 1).toString().padStart(2, '0'),
                style = RelayType.Utility.copy(color = RelayColors.Muted),
                modifier = Modifier.width(28.dp),
            )
            BasicText(
                text = entry.label,
                style = RelayType.Track,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            BasicText(
                text = "${entry.plays}",
                style = RelayType.Utility.copy(color = RelayColors.Signal),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private fun InsightsRange.label(): String = when (this) {
    InsightsRange.WEEK -> "7 DAYS"
    InsightsRange.MONTH -> "30 DAYS"
    InsightsRange.YEAR -> "YEAR"
    InsightsRange.ALL -> "ALL"
}

private const val DAY_MS = 24 * 60 * 60 * 1000L

/** Hours and minutes; listening time is an estimate, so seconds would imply false precision. */
internal fun formatListeningTime(totalMs: Long): String {
    val minutes = (totalMs.coerceAtLeast(0) / 60_000).toInt()
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}
