package com.jszutkoski.launcher

import java.net.URLEncoder
import java.util.Locale

/**
 * An app the search can hand words to: its short and long names, its package, and the link
 * that opens its own search. Every link also works in a browser, for when the app is missing.
 */
enum class SearchTarget(val label: String, private val words: List<String>, val packageName: String) {
    YOUTUBE("YouTube", listOf("yt", "youtube"), "com.google.android.youtube"),
    MAPS("Maps", listOf("maps", "mapa", "mapas"), "com.google.android.apps.maps"),
    WAZE("Waze", listOf("waze", "wz"), "com.waze"),
    SPOTIFY("Spotify", listOf("spotify", "sp"), "com.spotify.music"),
    PLAY_STORE("Play Store", listOf("play", "loja"), "com.android.vending"),
    NETFLIX("Netflix", listOf("netflix", "nf"), "com.netflix.mediaclient"),
    MERCADO_LIVRE("Mercado Livre", listOf("ml", "mercadolivre"), "com.mercadolibre"),
    WIKIPEDIA("Wikipedia", listOf("wiki", "wikipedia"), "org.wikipedia"),
    TRANSLATE("Google Translate", listOf("traduzir", "translate", "tradutor"), "com.google.android.apps.translate");

    /** The link to [query] in this app, the [language] picking Wikipedia's. */
    fun link(query: String, language: String = Locale.getDefault().language): String {
        val q = encode(query)
        return when (this) {
            YOUTUBE -> "https://www.youtube.com/results?search_query=$q"
            MAPS -> "https://www.google.com/maps/search/?api=1&query=$q"
            WAZE -> "https://waze.com/ul?q=$q"
            SPOTIFY -> "https://open.spotify.com/search/$q"
            PLAY_STORE -> "https://play.google.com/store/search?q=$q"
            NETFLIX -> "https://www.netflix.com/search?q=$q"
            // Mercado Livre's own search pages put dashes between the words.
            MERCADO_LIVRE -> "https://lista.mercadolivre.com.br/${encode(query.trim().replace(Regex("\\s+"), "-"))}"
            WIKIPEDIA -> "https://${if (language == "pt") "pt" else "en"}.wikipedia.org/w/index.php?search=$q"
            TRANSLATE -> "https://translate.google.com/?sl=auto&text=$q&op=translate"
        }
    }

    companion object {
        /**
         * "yt receita de bolo": the app named by the first word, and the rest to search for it;
         * null when the first word names none or nothing follows it.
         */
        fun parse(query: String): InAppSearch? {
            val trimmed = query.trim()
            val space = trimmed.indexOfFirst(Char::isWhitespace)
            if (space < 0) return null
            val word = AppSearch.normalize(trimmed.substring(0, space))
            val rest = trimmed.substring(space).trim()
            if (rest.isEmpty()) return null
            val target = entries.firstOrNull { word in it.words } ?: return null
            return InAppSearch(target, rest)
        }

        /** [query] is one of the short names ("yt", "sp", "wiki"): a search in that app may follow. */
        fun isKeyword(query: String): Boolean {
            val word = AppSearch.normalize(query.trim())
            return entries.any { word in it.words }
        }

        private fun encode(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
    }
}

/** Words to search for in one app: "yt receita de bolo" is "receita de bolo" on YouTube. */
data class InAppSearch(val target: SearchTarget, val query: String)
