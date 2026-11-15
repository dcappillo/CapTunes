package app.captunes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcTest {

    @Test fun readsMinutesSecondsAndHundredths() {
        assertEquals(listOf(83_450L to "Hello"), Lrc.parse("[01:23.45] Hello"))
    }

    @Test fun readsTenthsAndThousandths() {
        assertEquals(listOf(1_500L to "a"), Lrc.parse("[00:01.5]a"))
        assertEquals(listOf(1_234L to "b"), Lrc.parse("[00:01.234]b"))
        assertEquals(listOf(61_000L to "c"), Lrc.parse("[1:01]c"))
    }

    @Test fun aLineWithSeveralStampsRepeats() {
        assertEquals(
            listOf(10_000L to "Chorus", 70_000L to "Chorus"),
            Lrc.parse("[01:10.00][00:10.00]Chorus"),
        )
    }

    @Test fun linesComeBackInTimeOrder() {
        val lines = Lrc.parse("[00:30.00]second\n[00:05.00]first\n[01:00.00]third")
        assertEquals(listOf("first", "second", "third"), lines.map { it.second })
    }

    @Test fun headersAndPlainLinesAreSkipped() {
        val lines = Lrc.parse("[ar:Some Artist]\n[ti:Some Song]\njust words\n\n[00:02.00]Real line")
        assertEquals(listOf(2_000L to "Real line"), lines)
    }

    @Test fun emptyInputGivesNoLines() {
        assertTrue(Lrc.parse("").isEmpty())
    }

    @Test fun instrumentalGapsKeepAnEmptyLine() {
        assertEquals(listOf(12_000L to ""), Lrc.parse("[00:12.00]"))
    }
}
