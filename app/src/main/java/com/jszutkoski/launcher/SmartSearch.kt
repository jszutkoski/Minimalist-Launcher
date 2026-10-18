package com.jszutkoski.launcher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.pow

/** One line in the app list: an app, or something else the search found. */
sealed interface SearchResult {
    class AppHit(val app: App) : SearchResult

    /** The search was a sum: its result, written the [numbers] way, copied on tap. */
    class Answer(val value: Double, val numbers: Locale) : SearchResult

    class ContactHit(val contact: Contact) : SearchResult

    class SettingHit(val setting: SystemSetting) : SearchResult

    /** A timer or an alarm for the clock app. */
    class Clock(val command: ClockCommand) : SearchResult

    /** Words for one app's own search: "yt receita de bolo". */
    class InApp(val search: InAppSearch) : SearchResult

    /** Always last: hand the words to the web. */
    class Web(val query: String) : SearchResult
}

// Calculator ---------------------------------------------------------------------

object Calculator {
    private val allowed = Regex("[0-9.,+\\-*/×xX÷^%() ]+")
    private val operator = Regex("[+\\-*/×xX÷^%]")
    /** Two numbers with only a space between: "11 98765-4321" is a phone number, not a sum. */
    private val spacedNumbers = Regex("[0-9.,]\\s+[0-9(]")
    private val phone = Regex("\\+?[\\d\\s()]*\\d{5}-\\d{4}")
    private val date = Regex("\\d{1,2}/\\d{1,2}/\\d{2,4}|\\d{4}-\\d{1,2}-\\d{1,2}")

    /**
     * The value of a typed sum such as "12*7", "2,5 + 1", "1.500 + 300" or "200 x 15%", or null
     * if it isn't one. [decimal] is the local decimal mark; the other one groups thousands.
     */
    fun evaluate(input: String, decimal: Char = DecimalFormatSymbols.getInstance().decimalSeparator): Double? {
        val text = input.trim()
        if (text.length < 3 || !allowed.matches(text) || text.none(Char::isDigit)) return null
        // A leading minus alone ("-5") is a number, not a sum.
        if (!operator.containsMatchIn(text.drop(1))) return null
        if (spacedNumbers.containsMatchIn(text) || phone.matches(text) || date.matches(text)) return null
        val plain = text.replace(" ", "")
            .replace('×', '*').replace('x', '*').replace('X', '*').replace('÷', '/')
        return try {
            Parser(plain, decimal).parse()
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Grouped the [numbers] way, for reading: "1.234,5" in Brazil. */
    fun format(value: Double, numbers: Locale): String = formatter(numbers, grouping = true).format(value)

    /** Without grouping, for pasting elsewhere. */
    fun plain(value: Double, numbers: Locale): String = formatter(numbers, grouping = false).format(value)

    private fun formatter(numbers: Locale, grouping: Boolean) = NumberFormat.getNumberInstance(numbers).apply {
        maximumFractionDigits = 8
        isGroupingUsed = grouping
    }

    /**
     * A number as typed, with either mark: "1.500" is fifteen hundred in Brazil and "1,500" in
     * English; "2,5" and "2.5" are two and a half anywhere. A lone mark is a thousands mark only
     * when it is the local one and three digits follow it. Throws when it is no number.
     */
    fun number(token: String, decimal: Char): Double {
        val grouping = if (decimal == ',') '.' else ','
        val dots = token.count { it == '.' }
        val commas = token.count { it == ',' }
        val decimalMark: Char? = when {
            dots == 0 && commas == 0 -> null
            dots > 0 && commas > 0 -> if (token.lastIndexOf('.') > token.lastIndexOf(',')) '.' else ','
            else -> {
                val mark = if (dots > 0) '.' else ','
                val parts = token.split(mark)
                val grouped = parts.first().length in 1..3 && parts.drop(1).all { it.length == 3 }
                when {
                    parts.size > 2 -> if (grouped) null else throw IllegalArgumentException(token)
                    mark == grouping && grouped -> null
                    else -> mark
                }
            }
        }
        if (decimalMark != null) require(token.count { it == decimalMark } == 1)
        val digits = buildString {
            token.forEach { c ->
                when (c) {
                    decimalMark -> append('.')
                    '.', ',' -> Unit
                    else -> append(c)
                }
            }
        }
        // NumberFormatException is an IllegalArgumentException: "1..2" is no number.
        return digits.toDouble()
    }

    /**
     * + - * / ^ %, brackets and signs, with the usual precedence: a sign covers the whole power
     * (-2^2 is -4), and a percentage added or taken away is of what comes before it (200 + 10%
     * is 220). Throws on anything else.
     */
    private class Parser(private val s: String, private val decimal: Char) {
        private var i = 0
        /** The factor just read ended in %. */
        private var percentRead = false

        fun parse(): Double {
            val value = sum()
            require(i == s.length && value.isFinite())
            return value
        }

        private fun peek(c: Char) = i < s.length && s[i] == c

        private fun sum(): Double {
            var value = product().first
            while (peek('+') || peek('-')) {
                val op = s[i++]
                val (right, percentage) = product()
                val amount = if (percentage) value * right else right
                value = if (op == '+') value + amount else value - amount
            }
            return value
        }

        /** A product, and whether it was a lone percentage such as "10%". */
        private fun product(): Pair<Double, Boolean> {
            percentRead = false
            var value = unary()
            var percentage = percentRead
            while (peek('*') || peek('/')) {
                percentage = false
                val op = s[i++]
                val right = unary()
                if (op == '/') require(right != 0.0)
                value = if (op == '*') value * right else value / right
            }
            return value to percentage
        }

        private fun unary(): Double = when {
            peek('-') -> { i++; -unary() }
            peek('+') -> { i++; unary() }
            else -> power()
        }

        private fun power(): Double {
            val base = percent()
            if (!peek('^')) return base
            i++
            return base.pow(unary())
        }

        private fun percent(): Double {
            var value = number()
            while (peek('%')) {
                i++
                value /= 100
                percentRead = true
            }
            return value
        }

        private fun number(): Double {
            require(i < s.length)
            if (peek('(')) {
                i++
                val value = sum()
                require(peek(')'))
                i++
                // A percentage inside the brackets doesn't make the bracket one.
                percentRead = false
                return value
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == ',')) i++
            require(i > start)
            return number(s.substring(start, i), decimal)
        }
    }
}

// Contacts -----------------------------------------------------------------------

/** A contact with a phone number; [whatsApp] is the number WhatsApp knows, digits only, when there is one. */
class Contact(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val phone: String,
    val whatsApp: String?,
) {
    val searchKey = AppSearch.normalize(name)
}

object ContactSearch {
    private const val WHATSAPP_PROFILE = "vnd.android.cursor.item/vnd.com.whatsapp.profile"
    private const val WHATSAPP_BUSINESS_PROFILE = "vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile"

    fun hasAccess(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Every contact with a number, one line each, preferring the default and then a mobile number. */
    fun load(context: Context): List<Contact> {
        if (!hasAccess(context)) return emptyList()
        val resolver = context.contentResolver

        class Row(val lookupKey: String, val name: String, val number: String, val score: Int)
        val best = HashMap<Long, Row>()
        try {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val name = cursor.getString(2)?.trim().orEmpty()
                    val number = cursor.getString(3)?.trim().orEmpty()
                    if (name.isEmpty() || number.isEmpty()) continue
                    val score = (if (cursor.getInt(4) != 0) 2 else 0) +
                        (if (cursor.getInt(5) == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) 1 else 0)
                    val current = best[id]
                    if (current == null || score > current.score) best[id] = Row(cursor.getString(1).orEmpty(), name, number, score)
                }
            }
        } catch (e: RuntimeException) {
            // Permission withdrawn mid-query, or a broken contacts provider.
            warn("Could not read contacts", e)
            return emptyList()
        }

        // WhatsApp adds a row of its own to each contact it knows, holding the number it uses.
        val onWhatsApp = HashMap<Long, String>()
        try {
            resolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DATA1),
                "${ContactsContract.Data.MIMETYPE} IN (?, ?)",
                arrayOf(WHATSAPP_PROFILE, WHATSAPP_BUSINESS_PROFILE),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val digits = cursor.getString(1)?.substringBefore('@')?.filter(Char::isDigit).orEmpty()
                    if (digits.isNotEmpty()) onWhatsApp[cursor.getLong(0)] = digits
                }
            }
        } catch (e: RuntimeException) {
            warn("Could not read WhatsApp's contact rows", e)
        }
        // WhatsApp's contact sync turned off: no way to tell who uses it, so offer it for everyone.
        val guess = onWhatsApp.isEmpty()
        val country = countryIso(context)

        return best.map { (id, row) ->
            Contact(
                id = id,
                lookupKey = row.lookupKey,
                name = row.name,
                phone = row.number,
                whatsApp = onWhatsApp[id] ?: if (guess) international(row.number, country) else null,
            )
        }.sortedBy { it.searchKey }
    }

    /** Names that start with the query, then names with a later word that does. */
    fun filter(contacts: List<Contact>, query: String): List<Contact> {
        val q = AppSearch.normalize(query.trim())
        if (q.isEmpty()) return emptyList()
        return contacts
            .mapNotNull { contact ->
                when {
                    contact.searchKey.startsWith(q) -> 0 to contact
                    contact.searchKey.contains(" $q") -> 1 to contact
                    else -> null
                }
            }
            .sortedBy { it.first }
            .map { it.second }
    }

    /** "+55 11 98765-4321" as WhatsApp wants it: "5511987654321". Local numbers get the phone's country code. */
    private fun international(number: String, country: String): String? {
        val e164 = PhoneNumberUtils.formatNumberToE164(number, country)
        return when {
            e164 != null -> e164.removePrefix("+")
            number.startsWith("+") -> number.filter(Char::isDigit)
            else -> null
        }
    }

    private fun countryIso(context: Context): String =
        Region.country(context) ?: Locale.getDefault().country.takeIf { it.isNotBlank() } ?: "US"
}

// System settings ----------------------------------------------------------------

/**
 * A page of Android's settings, found by English or Portuguese words; [label] is shown. Several ways in are
 * tried in order, as phone makers move pages around. [isTorch] switches the flashlight instead.
 */
class SystemSetting(
    val label: Int,
    keywords: List<String>,
    val intents: List<() -> Intent> = emptyList(),
    val isTorch: Boolean = false,
) {
    val keys = keywords.map(AppSearch::normalize)
}

object SettingSearch {
    private fun page(action: String): () -> Intent = { Intent(action) }

    private fun component(pkg: String, cls: String): () -> Intent = { Intent().setClassName(pkg, cls) }

    private val all = listOf(
        SystemSetting(R.string.setting_flashlight, listOf("flashlight", "lanterna", "torch", "luz", "flash"), isTorch = true),
        SystemSetting(
            R.string.setting_wifi,
            listOf("wifi", "wi-fi", "wi fi", "wireless", "internet", "rede"),
            listOf(page(Settings.ACTION_WIFI_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_bluetooth,
            listOf("bluetooth", "fone", "headphones"),
            listOf(page(Settings.ACTION_BLUETOOTH_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_mobile_data,
            listOf("dados moveis", "dados", "mobile data", "4g", "5g", "chip", "sim", "operadora"),
            listOf(page(Settings.ACTION_DATA_USAGE_SETTINGS), page(Settings.ACTION_WIRELESS_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_hotspot,
            listOf("hotspot", "roteador", "ancoragem", "tethering", "compartilhar internet", "ponto de acesso"),
            listOf(
                component("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity"),
                component("com.android.settings", "com.android.settings.TetherSettings"),
                page(Settings.ACTION_WIRELESS_SETTINGS),
            ),
        ),
        SystemSetting(
            R.string.setting_airplane,
            listOf("airplane mode", "modo aviao", "aviao", "airplane", "flight", "voo"),
            listOf(page(Settings.ACTION_AIRPLANE_MODE_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_display,
            listOf("tela", "brilho", "brightness", "tema escuro", "dark theme", "fonte", "font", "display"),
            listOf(page(Settings.ACTION_DISPLAY_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_sound,
            listOf("som", "volume", "toque", "ringtone", "vibrar", "vibration", "sound"),
            listOf(page(Settings.ACTION_SOUND_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_dnd,
            listOf("nao perturbe", "do not disturb", "dnd", "silencio", "silent", "foco"),
            listOf(page("android.settings.ZEN_MODE_SETTINGS"), page(Settings.ACTION_SOUND_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_battery,
            listOf("bateria", "battery", "economia de energia", "battery saver"),
            listOf(page(Intent.ACTION_POWER_USAGE_SUMMARY), page(Settings.ACTION_BATTERY_SAVER_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_location,
            listOf("localizacao", "local", "gps", "location"),
            listOf(page(Settings.ACTION_LOCATION_SOURCE_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_notifications,
            listOf("notificacoes", "notifications"),
            listOf(page("android.settings.NOTIFICATION_SETTINGS"), page(Settings.ACTION_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_apps,
            listOf("aplicativos", "apps"),
            listOf(page(Settings.ACTION_APPLICATION_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_default_apps,
            listOf("apps padrao", "padrao", "default apps"),
            listOf(page(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_storage,
            listOf("armazenamento", "memoria", "espaco", "storage"),
            listOf(page(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_security,
            listOf(
                "seguranca", "senha", "bloqueio de tela", "digital", "biometria",
                "security", "password", "fingerprint", "pin",
            ),
            listOf(page(Settings.ACTION_SECURITY_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_privacy,
            listOf("privacidade", "privacy", "permissoes", "permissions"),
            listOf(page(Settings.ACTION_PRIVACY_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_accessibility,
            listOf("acessibilidade", "accessibility"),
            listOf(page(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_date,
            listOf("date & time", "data", "hora", "fuso horario", "date", "time", "timezone"),
            listOf(page(Settings.ACTION_DATE_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_language,
            listOf("idioma", "lingua", "language"),
            listOf(page(Settings.ACTION_LOCALE_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_keyboard,
            listOf("teclado", "keyboard"),
            listOf(page(Settings.ACTION_INPUT_METHOD_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_accounts,
            listOf("contas", "accounts", "sincronizar", "sync"),
            listOf(page(Settings.ACTION_SYNC_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_nfc,
            listOf("nfc", "aproximacao", "contactless"),
            listOf(page(Settings.ACTION_NFC_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_cast,
            listOf("transmitir", "espelhar", "espelhamento", "cast", "chromecast"),
            listOf(page(Settings.ACTION_CAST_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_update,
            listOf("system update", "atualizacao", "atualizar sistema", "update"),
            listOf(page("android.settings.SYSTEM_UPDATE_SETTINGS"), page(Settings.ACTION_DEVICE_INFO_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_about,
            listOf("sobre o telefone", "sobre", "about phone", "versao do android"),
            listOf(page(Settings.ACTION_DEVICE_INFO_SETTINGS)),
        ),
        SystemSetting(
            R.string.setting_developer,
            listOf("developer options", "desenvolvedor", "developer"),
            listOf(page(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)),
        ),
    )

    /** Settings with a word that starts with the query; two letters at least, so single letters stay with the apps. */
    fun filter(query: String): List<SystemSetting> {
        val q = AppSearch.normalize(query.trim())
        if (q.length < 2) return emptyList()
        return all.filter { setting -> setting.keys.any { it.startsWith(q) || it.contains(" $q") } }
    }
}

// Flashlight ---------------------------------------------------------------------

/** Follows the back flash, so search can offer "Turn on" or "Turn off". No permission needed. */
object Torch {
    var on = false
        private set
    private var cameraId: String? = null

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (cameraId == null) cameraId = id
            if (id == cameraId) on = enabled
        }
    }

    fun start(context: Context) {
        context.getSystemService(CameraManager::class.java)
            ?.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun stop(context: Context) {
        context.getSystemService(CameraManager::class.java)?.unregisterTorchCallback(callback)
    }

    /** False when the phone has no flash, or the camera app holds it. */
    fun toggle(context: Context): Boolean {
        val id = cameraId ?: return false
        return try {
            context.getSystemService(CameraManager::class.java)?.setTorchMode(id, !on)
            true
        } catch (e: CameraAccessException) {
            warn("Flashlight unavailable", e)
            false
        } catch (e: IllegalArgumentException) {
            warn("Flashlight unavailable", e)
            false
        }
    }
}
