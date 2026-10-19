package com.jszutkoski.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "yt receita de bolo": searching inside one app from the search. */
class InAppSearchTest {
    @Test
    fun theFirstWordNamesTheAppAndTheRestIsTheSearch() {
        assertEquals(InAppSearch(SearchTarget.YOUTUBE, "receita de bolo"), SearchTarget.parse("yt receita de bolo"))
        assertEquals(InAppSearch(SearchTarget.MAPS, "padaria"), SearchTarget.parse("  Maps   padaria "))
        assertEquals(InAppSearch(SearchTarget.TRANSLATE, "good morning"), SearchTarget.parse("traduzir good morning"))
    }

    @Test
    fun theNameAloneOrAnyOtherWordIsNoSearch() {
        assertNull(SearchTarget.parse("yt"))
        assertNull(SearchTarget.parse("youtube "))
        assertNull(SearchTarget.parse("receita de bolo"))
        assertNull(SearchTarget.parse("ytx gatos"))
    }

    @Test
    fun theWordsAreSafeInTheLink() {
        assertEquals(
            "https://www.youtube.com/results?search_query=p%C3%A3o%20%26%20caf%C3%A9",
            SearchTarget.YOUTUBE.link("pão & café"),
        )
        assertEquals("https://lista.mercadolivre.com.br/fone-de-ouvido", SearchTarget.MERCADO_LIVRE.link("fone de  ouvido"))
        assertEquals("https://pt.wikipedia.org/w/index.php?search=Curitiba", SearchTarget.WIKIPEDIA.link("Curitiba", "pt"))
        assertEquals("https://en.wikipedia.org/w/index.php?search=Curitiba", SearchTarget.WIKIPEDIA.link("Curitiba", "de"))
    }

    @Test
    fun aShortNameAloneIsOnlyTheStartOfASearch() {
        assertEquals(true, SearchTarget.isKeyword("sp"))
        assertEquals(true, SearchTarget.isKeyword("YT "))
        assertEquals(false, SearchTarget.isKeyword("spo"))
    }
}
