package com.jszutkoski.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private val names = listOf("agenda", "aiqfome", "banco inter", "instagram", "pinterest", "spotify")

    @Test
    fun normalizeIgnoresAccentsAndCase() {
        assertEquals("camera", AppSearch.normalize("Câmera"))
    }

    @Test
    fun prefixThenWordThenAnywhere() {
        assertEquals(listOf("instagram", "banco inter", "pinterest"), AppSearch.filter(names, "in") { it })
        assertEquals(names, AppSearch.filter(names, "  ") { it })
    }

    @Test
    fun opensAloneFromTwoLettersThatStartAWord() {
        assertTrue(AppSearch.opensAlone("ai", listOf("aiqfome"), contacts = 0, settings = 0))
        assertFalse(AppSearch.opensAlone("qf", listOf("aiqfome"), contacts = 0, settings = 0))
        assertFalse(AppSearch.opensAlone("q", listOf("aiqfome"), contacts = 0, settings = 0))
        assertFalse(AppSearch.opensAlone("in", listOf("instagram", "banco inter"), contacts = 0, settings = 0))
    }

    @Test
    fun twoLetterSettingsNoLongerStopAnApp() {
        // "in" also finds Wi-Fi ("internet"), "ba" Battery ("bateria").
        assertTrue(SettingSearch.filter("in").isNotEmpty())
        assertTrue(AppSearch.opensAlone("in", listOf("instagram"), contacts = 0, settings = SettingSearch.filter("in").size))
        assertTrue(AppSearch.opensAlone("ba", listOf("banco inter"), contacts = 0, settings = SettingSearch.filter("ba").size))
        // From three letters a matching setting is probably what was meant.
        assertFalse(AppSearch.opensAlone("bat", listOf("batman"), contacts = 0, settings = SettingSearch.filter("bat").size))
        // A contact always wins over opening an app.
        assertFalse(AppSearch.opensAlone("ma", listOf("mapas"), contacts = 1, settings = 0))
    }

    @Test
    fun settingsFoundInEitherLanguage() {
        assertEquals(listOf(R.string.setting_display), SettingSearch.filter("brilho").map { it.label })
        assertEquals(listOf(R.string.setting_flashlight), SettingSearch.filter("lanterna").map { it.label })
        assertEquals(listOf(R.string.setting_flashlight), SettingSearch.filter("flashlight").map { it.label })
        assertTrue(SettingSearch.filter("w").isEmpty())
    }

    @Test
    fun contactsByNameOrLaterWord() {
        val maria = Contact(1, "a", "Maria Souza", "1", null)
        val joao = Contact(2, "b", "João Mariano", "2", null)
        assertEquals(listOf(maria, joao), ContactSearch.filter(listOf(joao, maria), "mari"))
        assertEquals(listOf(joao), ContactSearch.filter(listOf(joao, maria), "joao"))
        assertTrue(ContactSearch.filter(listOf(joao, maria), "").isEmpty())
    }
}
