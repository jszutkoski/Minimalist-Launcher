package com.jszutkoski.launcher

import android.content.Context
import android.icu.util.ULocale
import android.telephony.TelephonyManager
import java.util.Locale

/** Where the phone is, as opposed to the language it speaks: a phone in US English in Brazil is in Brazil. */
object Region {
    /** The mobile network's country, else the SIM's, as "BR"; null with neither (a Wi-Fi tablet). */
    fun country(context: Context): String? {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        return listOfNotNull(telephony?.networkCountryIso, telephony?.simCountryIso)
            .firstOrNull { it.isNotBlank() }
            ?.uppercase(Locale.ROOT)
    }

    /** The way numbers are written where the phone is: 1.500,5 in Brazil whatever the language. */
    fun numbers(context: Context): Locale = numbers(Locale.getDefault(), country(context))

    /**
     * The phone's own locale when it is set to the country it is in (or the country is unknown),
     * else that country's main language there, as Android itself would guess it: pt-BR for Brazil.
     */
    fun numbers(language: Locale, country: String?): Locale {
        if (country == null || language.country.equals(country, ignoreCase = true)) return language
        val likely = ULocale.addLikelySubtags(ULocale("und", country)).toLocale()
        if (likely.language.isEmpty() || likely.language == "und") return language
        return Locale.Builder().setLanguage(likely.language).setRegion(country).build()
    }
}
