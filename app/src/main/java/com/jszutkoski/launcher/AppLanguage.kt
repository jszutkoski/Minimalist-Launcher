package com.jszutkoski.launcher

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

/**
 * The languages Minimalist speaks, chosen for it alone (Android 13 and later); null is the
 * phone's own. Each is named in itself, so it can be found whatever the current one is.
 */
enum class AppLanguage(val tag: String, val label: String) {
    PORTUGUESE("pt-BR", "Português (Brasil)"),
    ENGLISH("en", "English");

    companion object {
        fun current(context: Context): AppLanguage? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales == null || locales.isEmpty) return null
            val chosen = locales[0]
            return entries.firstOrNull { it.tag.substringBefore('-') == chosen.language }
        }

        /** The phone's, then each language in turn, then the phone's again. */
        fun after(current: AppLanguage?): AppLanguage? = when (current) {
            null -> entries.first()
            entries.last() -> null
            else -> entries[current.ordinal + 1]
        }

        /** Android keeps it and redraws the open screens in it. */
        fun set(context: Context, language: AppLanguage?) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (language == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
        }
    }
}
