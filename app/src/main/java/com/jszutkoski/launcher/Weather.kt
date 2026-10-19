package com.jszutkoski.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.icu.util.LocaleData
import android.icu.util.ULocale
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * Temperature and sky from Open-Meteo (free, no account, no key), for the phone's approximate
 * location. Kept for half an hour; the last place is remembered for when location is off.
 */
object Weather {
    class Now(val temperature: Int, val code: Int, val time: Long, val day: Boolean = true) {
        /** "18° Cloudy". */
        fun label(context: Context) = describe(context)?.let { "$degrees $it" } ?: degrees

        /** "18°". */
        val degrees get() = "$temperature°"

        /** "Cloudy", or null for a code it doesn't know. */
        fun describe(context: Context): String? = describe(code)?.let(context::getString)

        /** The sky as a line glyph, the sun or the moon as it is day or night. */
        val icon get() = icon(code, day)
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val inFlight = AtomicBoolean(false)

    @Volatile
    private var lastTry = 0L
    /** After an attempt, the next waits this long: offline, a night of wakes asks only a few times. */
    const val RETRY_MS = 10 * 60 * 1000L
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
        return Now(store.getInt("temperature", 0), store.getInt("code", -1), time, store.getBoolean("day", true))
    }

    fun isFresh(context: Context) = System.currentTimeMillis() - readingTime(context) < FRESH_MS

    /** When the last reading was taken, or never if it was in the other unit. */
    private fun readingTime(context: Context): Long {
        val store = store(context)
        return if (store.getString("unit", null) == unit(context)) store.getLong("time", 0) else 0
    }

    private fun unit(context: Context) = if (fahrenheit(context)) "fahrenheit" else "celsius"

    /** Whether another attempt may start, [lastTry] and [now] on the same clock. */
    fun due(lastTry: Long, now: Long) = lastTry == 0L || now - lastTry >= RETRY_MS

    /**
     * Finds the place, then asks for the weather there. [done] runs on a background thread. Does
     * nothing while a request is under way or one was made in the last [RETRY_MS].
     */
    fun refresh(context: Context, onResult: (Now?) -> Unit) {
        val now = SystemClock.elapsedRealtime()
        if (!due(lastTry, now) || !inFlight.compareAndSet(false, true)) return
        lastTry = now
        val done: (Now?) -> Unit = { result ->
            inFlight.set(false)
            onResult(result)
        }
        val app = context.applicationContext
        // Asking for the place, as well as the weather, happens off the main thread.
        worker.execute { locate(app, done) }
    }

    private fun locate(app: Context, done: (Now?) -> Unit) {
        val locations = app.getSystemService(LocationManager::class.java)
        val last = locations?.let(::lastKnown)
        if (locations == null || (last != null && System.currentTimeMillis() - last.time < LOCATION_MAX_AGE_MS)) {
            done(fetch(app, last))
            return
        }
        val provider = provider(locations)
        if (provider == null) {
            done(fetch(app, last))
            return
        }
        try {
            locations.getCurrentLocation(provider, null, worker) { location -> done(fetch(app, location ?: last)) }
        } catch (e: SecurityException) {
            // Location access withdrawn since it was checked.
            warn("No current location", e)
            worker.execute { done(fetch(app, last)) }
        } catch (e: RuntimeException) {
            warn("No current location", e)
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
            // Kept only as precise as what is sent: about a kilometre.
            latitude = rounded(location.latitude)
            longitude = rounded(location.longitude)
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
            "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code,is_day&temperature_unit=%s",
            latitude,
            longitude,
            unit,
        )
        return try {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            // Android's own would add the phone's model and build to the request.
            connection.setRequestProperty("User-Agent", "Minimalist")
            val body = try {
                if (connection.responseCode != 200) return null
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
            val current = JSONObject(body).getJSONObject("current")
            val now = Now(
                temperature = current.getDouble("temperature_2m").roundToInt(),
                code = current.getInt("weather_code"),
                time = System.currentTimeMillis(),
                day = current.optInt("is_day", 1) != 0,
            )
            store.edit()
                .putInt("temperature", now.temperature)
                .putInt("code", now.code)
                .putLong("time", now.time)
                .putBoolean("day", now.day)
                .putString("unit", unit)
                .apply()
            now
        } catch (e: Exception) {
            // Offline, a timeout or an unexpected answer: keep showing the last reading.
            warn("Weather request failed", e)
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
        val country = Region.country(context)
            ?: return LocaleData.getMeasurementSystem(ULocale.getDefault()) == LocaleData.MeasurementSystem.US
        return country in FAHRENHEIT_COUNTRIES
    }

    private val FAHRENHEIT_COUNTRIES = setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW", "FM", "MH")

    /** WMO weather codes, as Open-Meteo reports them. */
    private fun describe(code: Int): Int? = when (code) {
        0 -> R.string.weather_clear
        1 -> R.string.weather_mostly_clear
        2 -> R.string.weather_partly_cloudy
        3 -> R.string.weather_cloudy
        45, 48 -> R.string.weather_fog
        51, 53, 55, 56, 57 -> R.string.weather_drizzle
        61, 63, 65, 66, 67 -> R.string.weather_rain
        71, 73, 75, 77 -> R.string.weather_snow
        80, 81, 82 -> R.string.weather_showers
        85, 86 -> R.string.weather_snow_showers
        95, 96, 99 -> R.string.weather_thunderstorm
        else -> null
    }

    /** WMO weather codes as glyphs; a code it doesn't know gets the plain cloud. */
    fun icon(code: Int, day: Boolean): Int = when (code) {
        0 -> if (day) R.drawable.ic_weather_clear_day else R.drawable.ic_weather_clear_night
        1, 2 -> if (day) R.drawable.ic_weather_partly_day else R.drawable.ic_weather_partly_night
        45, 48 -> R.drawable.ic_weather_fog
        51, 53, 55, 56, 57 -> R.drawable.ic_weather_drizzle
        61, 63, 65, 66, 67, 80, 81, 82 -> R.drawable.ic_weather_rain
        71, 73, 75, 77, 85, 86 -> R.drawable.ic_weather_snow
        95, 96, 99 -> R.drawable.ic_weather_thunder
        else -> R.drawable.ic_weather_cloudy
    }

    private fun rounded(degrees: Double) = Math.round(degrees * 100) / 100.0

    /** Weather turned off everywhere: the last place and reading go too. */
    fun forget(context: Context) = store(context).edit().clear().apply()

    // Its own file: the launcher's settings file marks home for a rebuild on every change.
    private fun store(context: Context) = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
}
