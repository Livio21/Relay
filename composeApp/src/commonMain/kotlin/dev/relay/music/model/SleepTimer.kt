package dev.relay.music.model

/**
 * Sleep timer choices for the player. Timed options pause after the duration elapses;
 * END OF TRACK lets the playing track finish, then pauses instead of advancing.
 */
enum class SleepTimer(val label: String, val durationMs: Long?) {
    OFF("OFF", null),
    END_OF_TRACK("END OF TRACK", null),
    MINUTES_5("5 MINUTES", 5 * 60_000L),
    MINUTES_15("15 MINUTES", 15 * 60_000L),
    MINUTES_30("30 MINUTES", 30 * 60_000L),
    MINUTES_60("60 MINUTES", 60 * 60_000L),
}

/** The next choice when the player's single SLEEP action is tapped repeatedly. */
val SleepTimer.nextSleepTimer: SleepTimer
    get() = SleepTimer.values()[(ordinal + 1) % SleepTimer.values().size]
