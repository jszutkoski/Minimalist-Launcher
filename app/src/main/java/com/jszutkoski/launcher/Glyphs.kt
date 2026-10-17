package com.jszutkoski.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager

/** Packages the launcher recognises by name. */
object Packages {
    val WHATSAPP = listOf("com.whatsapp", "com.whatsapp.w4b")
    val EMAIL = listOf(
        "com.google.android.gm",
        "com.microsoft.office.outlook",
        "ch.protonmail.android",
        "com.yahoo.mobile.client.android.mail",
        "com.samsung.android.email.provider",
        "com.fsck.k9",
        "net.thunderbird.android",
        "com.fastmail.app",
    )

    /** Android's own call services, which post missed calls and voicemail with no app of their own. */
    val CALLS = listOf("com.android.server.telecom", "com.android.phone")
}

/** The phone's own apps, found by what they do rather than by name. */
private enum class Role {
    DIALER, SMS, CAMERA, BROWSER, CONTACTS, CALENDAR, CLOCK, CALCULATOR, SETTINGS, EMAIL, MAPS, MUSIC, GALLERY, FILES;

    /** The default app for this role, if there is exactly one. */
    fun app(context: Context): String? = when (this) {
        DIALER -> context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        SMS -> Telephony.Sms.getDefaultSmsPackage(context)
        else -> context.packageManager.resolveActivity(intent(), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
            // "android" is the chooser: there is more than one, and none is the default.
            ?.takeIf { it != "android" }
    }

    private fun intent(): Intent = when (this) {
        CAMERA -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        BROWSER -> Intent(Intent.ACTION_VIEW, Uri.parse("https://")).addCategory(Intent.CATEGORY_BROWSABLE)
        CLOCK -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
        SETTINGS -> Intent(Settings.ACTION_SETTINGS)
        CONTACTS -> selector(Intent.CATEGORY_APP_CONTACTS)
        CALENDAR -> selector(Intent.CATEGORY_APP_CALENDAR)
        CALCULATOR -> selector(Intent.CATEGORY_APP_CALCULATOR)
        EMAIL -> selector(Intent.CATEGORY_APP_EMAIL)
        MAPS -> selector(Intent.CATEGORY_APP_MAPS)
        MUSIC -> selector(Intent.CATEGORY_APP_MUSIC)
        GALLERY -> selector(Intent.CATEGORY_APP_GALLERY)
        FILES -> selector(Intent.CATEGORY_APP_FILES)
        DIALER, SMS -> error("found through their own service")
    }

    private fun selector(category: String) = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)
}

/**
 * The hand-drawn line glyphs and the apps each stands for: the best-known apps by name, and the
 * phone's own dialer, messages, camera and the like by what they are. The catalogue's order is
 * the order notification icons line up in on the bedside clock: calls and messages first.
 */
object AppGlyphs {
    private class Glyph(val drawable: Int, val packages: List<String>, val role: Role? = null)

    private val catalog = listOf(
        Glyph(
            R.drawable.ic_phone,
            listOf("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer") + Packages.CALLS,
            Role.DIALER,
        ),
        Glyph(
            R.drawable.ic_message,
            listOf("com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms"),
            Role.SMS,
        ),
        Glyph(R.drawable.ic_chat, Packages.WHATSAPP),
        Glyph(R.drawable.ic_telegram, listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram")),
        Glyph(R.drawable.ic_messenger, listOf("com.facebook.orca", "com.facebook.mlite")),
        Glyph(R.drawable.ic_instagram, listOf("com.instagram.android")),
        Glyph(R.drawable.ic_mail, Packages.EMAIL, Role.EMAIL),
        Glyph(R.drawable.ic_facebook, listOf("com.facebook.katana", "com.facebook.lite")),
        Glyph(R.drawable.ic_x, listOf("com.twitter.android")),
        Glyph(R.drawable.ic_tiktok, listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")),
        Glyph(R.drawable.ic_linkedin, listOf("com.linkedin.android")),
        Glyph(R.drawable.ic_youtube, listOf("com.google.android.youtube")),
        Glyph(R.drawable.ic_video_call, listOf("com.google.android.apps.tachyon", "us.zoom.videomeetings", "com.microsoft.teams")),
        Glyph(R.drawable.ic_calendar, listOf("com.google.android.calendar", "com.samsung.android.calendar"), Role.CALENDAR),
        Glyph(R.drawable.ic_clock, listOf("com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock"), Role.CLOCK),
        Glyph(
            R.drawable.ic_bank,
            listOf(
                "com.nu.production", "com.itau", "br.com.bb.android", "com.bradesco", "br.com.gabba.Caixa",
                "com.santander.app", "br.com.intermedium", "com.c6bank.app", "com.picpay", "com.mercadopago.wallet",
            ),
        ),
        Glyph(R.drawable.ic_wallet, listOf("com.google.android.apps.walletnfcrel", "com.samsung.android.spay")),
        Glyph(
            R.drawable.ic_shopping,
            listOf(
                "com.mercadolibre", "com.shopee.br", "com.amazon.mShop.android.shopping", "com.alibaba.aliexpresshd",
                "com.luizalabs.mlapp", "com.zzkko",
            ),
        ),
        Glyph(R.drawable.ic_food, listOf("br.com.brainweb.ifood", "com.grability.rappi", "com.ubercab.eats")),
        Glyph(R.drawable.ic_car, listOf("com.ubercab", "com.taxis99")),
        Glyph(R.drawable.ic_waze, listOf("com.waze")),
        Glyph(R.drawable.ic_maps, listOf("com.google.android.apps.maps"), Role.MAPS),
        Glyph(R.drawable.ic_spotify, listOf("com.spotify.music")),
        Glyph(
            R.drawable.ic_music,
            listOf("com.google.android.apps.youtube.music", "deezer.android.app", "com.amazon.mp3", "com.soundcloud.android"),
            Role.MUSIC,
        ),
        Glyph(R.drawable.ic_netflix, listOf("com.netflix.mediaclient")),
        Glyph(
            R.drawable.ic_tv,
            listOf("com.amazon.avod.thirdpartyclient", "com.disney.disneyplus", "com.wbd.stream", "com.globo.globotv"),
        ),
        Glyph(R.drawable.ic_assistant, listOf("com.google.android.apps.bard", "com.openai.chatgpt", "com.anthropic.claude")),
        Glyph(R.drawable.ic_photos, listOf("com.google.android.apps.photos")),
        Glyph(R.drawable.ic_gallery, listOf("com.sec.android.gallery3d"), Role.GALLERY),
        Glyph(
            R.drawable.ic_camera,
            listOf("com.google.android.GoogleCamera", "com.motorola.camera3", "com.sec.android.app.camera", "com.android.camera2"),
            Role.CAMERA,
        ),
        Glyph(R.drawable.ic_chrome, listOf("com.android.chrome")),
        Glyph(
            R.drawable.ic_browser,
            listOf("org.mozilla.firefox", "com.sec.android.app.sbrowser", "com.brave.browser", "com.microsoft.emmx", "com.opera.browser"),
            Role.BROWSER,
        ),
        Glyph(R.drawable.ic_contacts, listOf("com.google.android.contacts", "com.samsung.android.app.contacts"), Role.CONTACTS),
        Glyph(R.drawable.ic_notes, listOf("com.google.android.keep", "com.samsung.android.app.notes")),
        Glyph(R.drawable.ic_files, listOf("com.google.android.apps.nbu.files", "com.sec.android.app.myfiles"), Role.FILES),
        Glyph(
            R.drawable.ic_calculator,
            listOf("com.google.android.calculator", "com.sec.android.app.popupcalculator"),
            Role.CALCULATOR,
        ),
        Glyph(R.drawable.ic_play_store, listOf("com.android.vending")),
        Glyph(R.drawable.ic_settings, listOf("com.android.settings"), Role.SETTINGS),
    )

    private var cached: Map<String, Int> = emptyMap()
    private var cachedAt = Long.MIN_VALUE

    /**
     * Package name to glyph. The default apps are looked up again at most once a minute, so a new
     * default dialer or browser takes its glyph soon without asking Android on every redraw.
     */
    fun lookup(context: Context): Map<String, Int> {
        val now = SystemClock.elapsedRealtime()
        if (now - cachedAt < CACHE_MS && cachedAt != Long.MIN_VALUE) return cached
        val map = HashMap<String, Int>()
        // By role first, then by name, so a named app keeps its own glyph whatever its role.
        for (glyph in catalog) {
            val app = glyph.role?.let { role ->
                try {
                    role.app(context)
                } catch (e: RuntimeException) {
                    warn("Could not find the default app for $role", e)
                    null
                }
            }
            if (app != null) map.putIfAbsent(app, glyph.drawable)
        }
        for (glyph in catalog) glyph.packages.forEach { map[it] = glyph.drawable }
        cached = map
        cachedAt = now
        return map
    }

    /** Where [drawable] comes in the catalogue; unknown ones go last. */
    private fun rank(drawable: Int): Int = catalog.indexOfFirst { it.drawable == drawable }.let { if (it < 0) Int.MAX_VALUE else it }

    /** One icon on the bedside clock: a glyph, and the apps with notifications it stands for. */
    class Waiting(val drawable: Int, val packages: List<String>)

    /**
     * The icons for [packages] with notifications waiting: one per glyph, in the catalogue's
     * order, at most [max]. Apps with no glyph, and any past [max], share three dots at the end.
     */
    fun waiting(packages: List<String>, glyphs: Map<String, Int>, max: Int = MAX_WAITING): List<Waiting> {
        val (known, others) = packages.distinct().partition { it in glyphs }
        val groups = known.groupBy { glyphs.getValue(it) }.entries.sortedBy { rank(it.key) }
        val rest = others + groups.drop(max).flatMap { it.value }
        return groups.take(max).map { Waiting(it.key, it.value) } +
            listOfNotNull(rest.takeIf { it.isNotEmpty() }?.let { Waiting(R.drawable.ic_more, it) })
    }

    private const val CACHE_MS = 60_000L

    /** As many as fit across a narrow phone with room to spare, the three dots aside. */
    const val MAX_WAITING = 5
}
