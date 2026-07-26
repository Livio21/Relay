package dev.relay.music.playback

import dev.relay.music.model.Track
import kotlin.random.Random
import okio.ByteString.Companion.toByteString

enum class ShuffleGrouping {
    ARTIST,
    ALBUM,
    TITLE,
    ALBUM_ARTIST,
    RELEASE_DATE,
    RAINBOW,
}

enum class MissingShuffleValue { LAST, FIRST }

/** A persisted, deterministic queue recipe. Empty rules means normal shuffle. */
data class ShuffleProfile(
    val id: String = "default",
    val name: String = "DEFAULT",
    val rules: List<ShuffleGrouping> = emptyList(),
    val missingValue: MissingShuffleValue = MissingShuffleValue.LAST,
    val seed: Long? = null,
    val seedLabel: String? = null,
    val seedSalt: String = "",
)

fun newShuffleProfile(existing: List<ShuffleProfile>): ShuffleProfile {
    val number = generateSequence(2) { it + 1 }
        .first { candidate -> existing.none { it.id == "profile-$candidate" } }
    return ShuffleProfile(id = "profile-$number", name = "PROFILE $number")
}

/**
 * A Fisher-Yates permutation driven by Relay's specified PRNG. The currently playing track stays
 * at the head; rules order metadata groups and shuffle only inside a matching group.
 */
fun shuffledQueue(
    queue: List<Track>,
    currentIndex: Int,
    profile: ShuffleProfile,
    seedOverride: Long? = null,
): List<Track> {
    if (queue.size < 2) return queue
    val random = RelayShuffleRandom(seedOverride ?: profile.seed ?: Random.nextLong())
    val current = queue.getOrNull(currentIndex)
    val remaining = if (current == null) queue.toMutableList() else {
        queue.filterIndexed { index, _ -> index != currentIndex }.toMutableList()
    }
    val rules = profile.rules.distinct()
    val shuffled = if (rules.isEmpty()) {
        remaining.also { it.fisherYates(random) }
    } else {
        remaining
            .groupBy { it.groupKey(rules, profile.missingValue) }
            .toList()
            .sortedBy { it.first }
            .flatMap { (_, group) -> group.toMutableList().also { it.fisherYates(random) } }
    }
    return if (current == null) shuffled else listOf(current) + shuffled
}

fun Track.groupKey(rules: List<ShuffleGrouping>, missingValue: MissingShuffleValue): String =
    rules.joinToString("\u0001") { rule -> groupValue(rule, missingValue) }

private fun Track.groupValue(rule: ShuffleGrouping, missingValue: MissingShuffleValue): String {
    val value = when (rule) {
        ShuffleGrouping.ARTIST -> artist
        ShuffleGrouping.ALBUM -> album
        ShuffleGrouping.TITLE -> title
        ShuffleGrouping.ALBUM_ARTIST -> albumArtist
        ShuffleGrouping.RELEASE_DATE -> releaseDate
        ShuffleGrouping.RAINBOW -> artworkHue?.takeIf { it in 0..359 }?.toString()?.padStart(3, '0')
    }?.trim()?.lowercase().orEmpty()
    return value.ifEmpty { if (missingValue == MissingShuffleValue.FIRST) "\u0000" else "\uFFFF" }
}

/** Derives a stable seed and short fingerprint from original image bytes and an optional salt. */
fun shuffleSeedFromBytes(bytes: ByteArray, salt: String = ""): ShuffleProfile {
    val saltedBytes = bytes + byteArrayOf(0) + salt.trim().encodeToByteArray()
    val digest = saltedBytes.toByteString().sha256()
    var seed = 0L
    for (index in 0 until 8) seed = (seed shl 8) or (digest[index].toLong() and 0xFF)
    return ShuffleProfile(seed = seed, seedLabel = digest.hex().take(8).uppercase(), seedSalt = salt.trim().take(64))
}

private fun <T> MutableList<T>.fisherYates(random: RelayShuffleRandom) {
    for (index in lastIndex downTo 1) {
        val swap = random.nextInt(index + 1)
        set(index, set(swap, get(index)))
    }
}

/** XorShift64* is deliberately specified here so a fixed seed gives the same queue on every target. */
private class RelayShuffleRandom(seed: Long) {
    private var state = if (seed == 0L) FALLBACK_SEED else seed

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        val unsignedBound = bound.toULong()
        val limit = ULong.MAX_VALUE - ULong.MAX_VALUE % unsignedBound
        var value: ULong
        do value = nextLong().toULong() while (value >= limit)
        return (value % unsignedBound).toInt()
    }

    private fun nextLong(): Long {
        var value = state
        value = value xor (value ushr 12)
        value = value xor (value shl 25)
        value = value xor (value ushr 27)
        state = value
        return value * 2_685_821_657_736_338_717L
    }

    private companion object {
        const val FALLBACK_SEED = -7_046_029_254_386_353_131L
    }
}
