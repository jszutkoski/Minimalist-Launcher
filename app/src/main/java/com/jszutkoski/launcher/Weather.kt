package com.jszutkoski.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.icu.util.LocaleData
import android.icu.util.ULocale
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.telephony.TelephonyManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Temperature and sky from Open-Meteo (free, no account, no key), for the phone's approximate
 * location. Kept for half an hour; the last place is remembered for when location is off.
 */
object Weather {
    class Now(val temperature: Int, val code: Int, val time: Long) {
        /** "18° Cloudy". */
        val label get() = describe(code).let { if (it.isEmpty()) "$temperature°" else "$temperature° $it" }
    }

    private val worker = Executors.newSingleThreadExecutor()
    private const val FRESH_MS = 30 * 60 * 1000L
    private const val SHOWN_MS = 3 * 60 * 60 * 1000L
    private const val LOCATION_MAX_AGE_MS = 60 * 60 * 1000L

    fun hasAccess(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The last reading, unless it is too old to be worth showing. */
    fun cached(context: Context): Now? {
        val store = store(context)
        val time = readingTime(context)
        if (System.currentTimeMillis() - time > SHOWN_MS) return null
        return Now(store.getInt("temperature", 0), store.getInt("code", -1), time)
    }

    fun isFresh(context: Context) = System.currentTimeMillis() - readingTime(context) < FRESH_MS

    /** When the last reading was taken, or never if it was in the other unit. */
    private fun readingTime(context: Context): Long {
        val store = store(context)
        return if (store.getString("unit", null) == unit(context)) store.getLong("time", 0) else 0
    }

    private fun unit(context: Context) = if (fahrenheit(context)) "fahrenheit" else "celsius"

    /** Finds the place, then asks for the weather there. [done] runs on a background thread. */
    fun refresh(context: Context, done: (Now?) -> Unit) {
        val app = context.applicationContext
        val locations = app.getSystemService(LocationManager::class.java)
        val last = locations?.let(::lastKnown)
        if (locations == null || (last != null && System.currentTimeMillis() - last.time < LOCATION_MAX_AGE_MS)) {
            worker.execute { done(fetch(app, last)) }
            return
        }
        val provider = provider(locations)
        if (provider == null) {
            worker.execute { done(fetch(app, last)) }
            return
        }
        try {
            locations.getCurrentLocation(provider, null, worker) { location -> done(fetch(app, location ?: last)) }
        } catch (_: RuntimeException) {
            worker.execute { done(fetch(app, last)) }
        }
    }

    private fun lastKnown(locations: LocationManager): Location? = try {
        locations.getProviders(true)
            .mapNotNull { locations.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
    } catch (_: SecurityException) {
        null
    }

    private fun provider(locations: LocationManager): String? {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        return candidates.firstOrNull { runCatching { locations.isProviderEnabled(it) }.getOrDefault(false) }
    }

    private fun fetch(context: Context, location: Location?): Now? {
        val store = store(context)
        val latitude: Double
        val longitude: Double
        if (location != null) {
            latitude = location.latitude
            longitude = location.longitude
            store.edit().putFloat("latitude", latitude.toFloat()).putFloat("longitude", longitude.toFloat()).apply()
        } else if (store.contains("latitude")) {
            latitude = store.getFloat("latitude", 0f).toDouble()
            longitude = store.getFloat("longitude", 0f).toDouble()
        } else {
            return null
        }

        val unit = unit(context)
        val url = String.format(
            Locale.ROOT,
            "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code&temperature_unit=%s",
            latitude,
            longitude,
            unit,
        )
        return try {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            val body = try {
                if (connection.responseCode != 200) return null
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
            val current = JSONObject(body).getJSONObject("current")
            val now = Now(current.getDouble("temperature_2m").roundToInt(), current.getInt("weather_code"), System.currentTimeMillis())
            store.edit().putInt("temperature", now.temperature).putInt("code", now.code).putLong("time", now.time).putString("unit", unit).apply()
            now
        } catch (_: Exception) {
            // Offline, a timeout or an unexpected answer: keep showing the last reading.
            null
        }
    }

    /**
     * The temperature unit picked in Android's regional preferences (Android 14+), else the one
     * of the country the SIM is in. Not the language: a phone in US English in Brazil wants °C.
     */
    private fun fahrenheit(context: Context): Boolean {
        when (Locale.getDefault().getUnicodeLocaleType("mu")) {
            "fahrenhe" -> return true
            "celsius", "kelvin" -> return false
        }
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val country = listOfNotNull(telephony?.networkCountryIso, telephony?.simCountryIso)
            .firstOrNull { it.isNotBlank() }
            ?.uppercase(Locale.ROOT)
            ?: return LocaleData.getMeasurementSystem(ULocale.getDefault()) == LocaleData.MeasurementSystem.US
        return country in FAHRENHEIT_COUNTRIES
    }

    private val FAHRENHEIT_COUNTRIES = setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW", "FM", "MH")

    /** WMO weather codes, as Open-Meteo reports them. */
    private fun describe(code: Int) = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67 -> "Rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Showers"
        85, 86 -> "Snow showers"
        95, 96, 99 -> "Thunderstorm"
        else -> ""
    }

    // Its own file: the launcher's settings file marks home for a rebuild on every change.
    private fun store(context: Context) = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
}
