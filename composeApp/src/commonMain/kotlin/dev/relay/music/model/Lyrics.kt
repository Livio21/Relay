package dev.relay.music.model

/** One timed line of an LRC file. */
data class LyricLine(
    val timeMs: Long,
    val text: String,
)

/**
 * Lyrics that follow playback. Only local `.lrc` content and manually saved timestamps produce
 * these; plain lyrics stay plain rather than being guessed at.
 */
data class TimedLyrics(
    val lines: List<LyricLine>,
    /** `[offset:]` shifts every timestamp — positive means the words come earlier. */
    val offsetMs: Long = 0,
)

private val TIMESTAMP = Regex("""\[(\d{1,3}):([0-5]?\d)(?:[.:](\d{1,3}))?]""")
private val OFFSET_TAG = Regex("""\[offset:\s*([+-]?\d{1,6})\s*]""", RegexOption.IGNORE_CASE)

/**
 * Parses LRC content, returning null when the text carries no timestamps at all — that is
 * ordinary lyrics and the caller should display it as written. A line may repeat a lyric at
 * several times (`[00:12.00][01:30.00] chorus`), which the format uses for choruses.
 */
fun parseLrc(content: String?): TimedLyrics? {
    if (content.isNullOrBlank()) return null
    val lines = mutableListOf<LyricLine>()
    content.lineSequence().forEach { raw ->
        val stamps = TIMESTAMP.findAll(raw).toList()
        if (stamps.isEmpty()) return@forEach
        val text = raw.substring(stamps.last().range.last + 1).trim()
        stamps.forEach { stamp ->
            val (minutes, seconds, fraction) = stamp.destructured
            val fractionMs = fraction.takeIf { it.isNotEmpty() }?.let {
                // "5" means 500 ms, "05" 50 ms, "050" 50 ms.
                it.padEnd(3, '0').take(3).toLong()
            } ?: 0L
            lines += LyricLine(minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fractionMs, text)
        }
    }
    if (lines.isEmpty()) return null
    val offset = OFFSET_TAG.find(content)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    return TimedLyrics(lines.sortedBy { it.timeMs }, offset)
}

/**
 * Index of the line that should be highlighted at this position, or -1 before the first one.
 * Lines with no text (LRC uses them to mark instrumental gaps) still take their turn.
 */
fun TimedLyrics.activeLineIndex(positionMs: Long): Int {
    val target = positionMs + offsetMs
    var low = 0
    var high = lines.lastIndex
    var found = -1
    while (low <= high) {
        val middle = (low + high) / 2
        if (lines[middle].timeMs <= target) {
            found = middle
            low = middle + 1
        } else {
            high = middle - 1
        }
    }
    return found
}
