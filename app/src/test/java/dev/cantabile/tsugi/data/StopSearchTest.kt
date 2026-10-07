package dev.cantabile.tsugi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopSearchTest {
    private val stops = listOf(
        BusStop("53009", "Bishan Rd", "Bishan Int", 0.0, 0.0),
        BusStop("53239", "Bishan Rd", "Opp Bishan Stn", 0.0, 0.0),
        BusStop("01019", "Victoria St", "Hotel Grand Pacific", 0.0, 0.0),
        BusStop("46971", "Woodlands Ave 3", "Bef Admiralty Pr Sch", 0.0, 0.0),
    )

    private fun names(q: String) = searchStops(stops, q).map { it.description }

    @Test
    fun codesAndPlainMatchesComeFirst() {
        assertEquals(listOf("Bishan Int"), names("53009"))
        assertEquals("Bishan Int", names("bishan").first())
        assertEquals(listOf("Hotel Grand Pacific"), names("victoria"))
    }

    @Test
    fun spelledOutWordsMatchAbbreviations() {
        assertEquals(listOf("Opp Bishan Stn"), names("bishan station"))
        assertEquals(listOf("Opp Bishan Stn"), names("opposite bishan mrt"))
        assertEquals(listOf("Bishan Int"), names("bishan interchange"))
        assertEquals(listOf("Bef Admiralty Pr Sch"), names("admiralty primary school"))
    }

    @Test
    fun wordsInAnyOrder() {
        assertEquals(listOf("Opp Bishan Stn"), names("stn bishan"))
        assertEquals(listOf("Hotel Grand Pacific"), names("pacific hotel"))
    }

    @Test
    fun forgivesOneTypoInLongerWords() {
        assertEquals(listOf("Bef Admiralty Pr Sch"), names("admirality"))
        assertTrue(names("bihsan").containsAll(listOf("Bishan Int", "Opp Bishan Stn")))
        assertTrue(names("xyz").isEmpty())
    }

    @Test
    fun oneEdit() {
        assertTrue(withinOneEdit("bishan", "bihsan"))
        assertTrue(withinOneEdit("bishan", "bishn"))
        assertTrue(withinOneEdit("bishan", "bishann"))
        assertTrue(withinOneEdit("bishan", "bushan"))
        assertFalse(withinOneEdit("bishan", "bushn"))
        assertFalse(withinOneEdit("ab", "ba1"))
    }
}
