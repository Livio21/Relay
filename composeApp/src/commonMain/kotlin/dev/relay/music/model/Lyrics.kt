package dev.relay.music.model

/** One timed line of an LRC file. */
data class LyricLine(
    val timeMs: Long,
    val text: String,
)

/** LRC's compact timestamp form for the manual current-playback insertion control. */
fun formatLrcTimestamp(positionMs: Long): String {
    val totalCentiseconds = positionMs.coerceAtLeast(0) / 10
    val minutes = (totalCentiseconds / 6_000).toString().padStart(2, '0')
    val seconds = (totalCentiseconds / 100 % 60).toString().padStart(2, '0')
    val centiseconds = (totalCentiseconds % 100).toString().padStart(2, '0')
    return "[$minutes:$seconds.$centiseconds]"
}

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
private val LEADING_TIME_TAGS = Regex("""^(?:\[\d{1,3}:[^]\r\n]*]\s*)+""")
private val METADATA_TAG = Regex("""^\[[a-z][a-z0-9_-]*:.*]$""", RegexOption.IGNORE_CASE)

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

/** Adds or replaces the timestamp on the line containing [cursorOffset]. */
fun stampLrcLine(content: String, cursorOffset: Int, positionMs: Long): String {
    val cursor = cursorOffset.coerceIn(0, content.length)
    val lineStart = content.lastIndexOf('\n', cursor - 1) + 1
    val lineEnd = content.indexOf('\n', cursor).takeIf { it >= 0 } ?: content.length
    val line = content.substring(lineStart, lineEnd)
    val indentation = line.takeWhile { it.isWhitespace() }
    val lyric = LEADING_TIME_TAGS.replaceFirst(line.drop(indentation.length), "")
    val stamped = indentation + formatLrcTimestamp(positionMs) + lyric
    return content.replaceRange(lineStart, lineEnd, stamped)
}

/**
 * Validates and orders timed lyrics for local storage. Plain lyrics are returned unchanged apart
 * from the same outer whitespace trimming already applied by the persistence path.
 */
fun prepareLrcForSave(content: String): Result<String> = runCatching {
    val headers = mutableListOf<String>()
    val untimedLines = mutableListOf<Int>()
    var hasTimestamp = false

    content.lineSequence().forEachIndexed { index, raw ->
        val line = raw.trim()
        if (line.isEmpty()) return@forEachIndexed
        val stamps = TIMESTAMP.findAll(line).toList()
        if (stamps.isNotEmpty()) {
            hasTimestamp = true
            val prefix = line.substring(0, stamps.last().range.last + 1)
            require(TIMESTAMP.replace(prefix, "").isBlank()) {
                "Line ${index + 1}: timestamps must start the line."
            }
        } else when {
            LEADING_TIME_TAGS.containsMatchIn(line) -> error("Line ${index + 1}: invalid LRC timestamp.")
            METADATA_TAG.matches(line) -> headers += line
            else -> untimedLines += index + 1
        }
    }

    if (!hasTimestamp) return@runCatching content.trim()
    require(untimedLines.isEmpty()) {
        "Line ${untimedLines.first()} needs a timestamp before timed lyrics can be saved."
    }
    val timed = requireNotNull(parseLrc(content)) { "No valid LRC timestamps found." }
    (headers + timed.lines.map { formatLrcTimestamp(it.timeMs) + it.text })
        .joinToString("\n")
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
