package dev.relay.music

import dev.relay.music.model.activeLineIndex
import dev.relay.music.model.parseLrc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricsTest {
    @Test
    fun parsesTimestampsAndKeepsLinesInOrder() {
        val lyrics = parseLrc(
            """
            [ti:Signal Test]
            [00:12.50]First line
            [01:05.00]Second line
            [00:03.00]Actually the opening
            """.trimIndent(),
        )!!

        assertEquals(listOf(3_000L, 12_500L, 65_000L), lyrics.lines.map { it.timeMs })
        assertEquals("Actually the opening", lyrics.lines.first().text)
    }

    @Test
    fun repeatedTimestampsOnOneLineBecomeSeparateCues() {
        val lyrics = parseLrc("[00:12.00][01:30.00]Chorus")!!

        assertEquals(listOf(12_000L, 90_000L), lyrics.lines.map { it.timeMs })
        assertEquals(listOf("Chorus", "Chorus"), lyrics.lines.map { it.text })
    }

    @Test
    fun readsFractionsAtWhateverPrecisionTheFileUses() {
        assertEquals(1_500L, parseLrc("[00:01.5]x")!!.lines.single().timeMs)
        assertEquals(1_050L, parseLrc("[00:01.05]x")!!.lines.single().timeMs)
        assertEquals(1_050L, parseLrc("[00:01.050]x")!!.lines.single().timeMs)
        // Some writers use a colon before the fraction.
        assertEquals(1_500L, parseLrc("[00:01:5]x")!!.lines.single().timeMs)
    }

    @Test
    fun plainLyricsAreNotTreatedAsTimed() {
        assertNull(parseLrc("Just some words\nAnd another line"))
        assertNull(parseLrc(""))
        assertNull(parseLrc(null))
        // Metadata tags alone carry no cues.
        assertNull(parseLrc("[ar:Relay Demo]\n[ti:Signal Test]"))
    }

    @Test
    fun highlightsTheLineThatHasStartedAndNothingBeforeTheFirst() {
        val lyrics = parseLrc("[00:00.00]One\n[00:10.00]Two\n[00:20.00]Three")!!

        assertEquals(-1, lyrics.copy(offsetMs = -5_000).activeLineIndex(1_000))
        assertEquals(0, lyrics.activeLineIndex(0))
        assertEquals(0, lyrics.activeLineIndex(9_999))
        assertEquals(1, lyrics.activeLineIndex(10_000))
        assertEquals(2, lyrics.activeLineIndex(999_999))
    }

    @Test
    fun offsetTagShiftsEveryCue() {
        val lyrics = parseLrc("[offset:+500]\n[00:10.00]Two")!!

        assertEquals(500L, lyrics.offsetMs)
        // With a +500 ms offset the line is reached half a second earlier.
        assertEquals(0, lyrics.activeLineIndex(9_500))
        assertEquals(-1, lyrics.activeLineIndex(9_499))
    }
}
