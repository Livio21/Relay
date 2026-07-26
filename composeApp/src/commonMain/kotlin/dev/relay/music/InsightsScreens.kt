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
import dev.relay.music.model.ListeningDay
import dev.relay.music.model.ListeningEvent
import dev.relay.music.model.insightsFor
import dev.relay.music.ui.RelayColors
import dev.relay.music.ui.RelayType

@Composable
internal fun InsightsScreen(events: List<ListeningEvent>, nowEpochMs: Long) {
    var range by remember { mutableStateOf(InsightsRange.MONTH) }
    val insights = remember(events, range, nowEpochMs) { insightsFor(events, range, nowEpochMs) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
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
        ListeningCalendar(insights.listeningDays, nowEpochMs)
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
