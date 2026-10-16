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
import android.telephony.TelephonyManager
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.pow

/** One line in the app list: an app, or something else the search found. */
sealed interface SearchResult {
    class AppHit(val app: App) : SearchResult

    /** The search was a sum: its result, copied on tap. */
    class Answer(val value: Double) : SearchResult

    class ContactHit(val contact: Contact) : SearchResult

    class SettingHit(val setting: SystemSetting) : SearchResult

    /** Always last: hand the words to the web. */
    class Web(val query: String) : SearchResult
}

// Calculator ---------------------------------------------------------------------

object Calculator {
    private val allowed = Regex("[0-9.,+\\-*/×xX÷^%() ]+")
    private val operator = Regex("[+\\-*/×xX÷^%]")

    /** The value of a typed sum such as "12*7", "2,5 + 1" or "200 x 15%", or null if it isn't one. */
    fun evaluate(input: String): Double? {
        val text = input.trim()
        if (text.length < 3 || !allowed.matches(text) || text.none(Char::isDigit)) return null
        // A leading minus alone ("-5") is a number, not a sum.
        if (!operator.containsMatchIn(text.drop(1))) return null
        val plain = text.replace(" ", "").replace(',', '.')
            .replace('×', '*').replace('x', '*').replace('X', '*').replace('÷', '/')
        return try {
            Parser(plain).parse()
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Grouped the local way, for reading: "1.234,5" in Brazil. */
    fun format(value: Double): String = formatter(grouping = true).format(value)

    /** Without grouping, for pasting elsewhere. */
    fun plain(value: Double): String = formatter(grouping = false).format(value)

    private fun formatter(grouping: Boolean) = NumberFormat.getNumberInstance().apply {
        maximumFractionDigits = 8
        isGroupingUsed = grouping
    }

    /** + - * / ^ %, brackets and unary minus, with the usual precedence. Throws on anything else. */
    private class Parser(private val s: String) {
        private var i = 0

        fun parse(): Double {
            val value = sum()
            require(i == s.length && value.isFinite())
            return value
        }

        private fun peek(c: Char) = i < s.length && s[i] == c

        private fun sum(): Double {
            var value = product()
            while (peek('+') || peek('-')) {
                val op = s[i++]
                val right = product()
                value = if (op == '+') value + right else value - right
            }
            return value
        }

        private fun product(): Double {
            var value = power()
            while (peek('*') || peek('/')) {
                val op = s[i++]
                val right = power()
                if (op == '/') require(right != 0.0)
                value = if (op == '*') value * right else value / right
            }
            return value
        }

        private fun power(): Double {
            val base = unary()
            if (!peek('^')) return base
            i++
            return base.pow(power())
        }

        private fun unary(): Double = when {
            peek('-') -> { i++; -unary() }
            peek('+') -> { i++; unary() }
            else -> percent()
        }

        private fun percent(): Double {
            var value = number()
            while (peek('%')) {
                i++
                value /= 100
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
                return value
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            require(i > start)
            // NumberFormatException is an IllegalArgumentException: "1.2.3" is no number.
            return s.substring(start, i).toDouble()
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
        } catch (_: RuntimeException) {
            // Permission withdrawn mid-query, or a broken contacts provider.
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
        } catch (_: RuntimeException) {
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

    private fun countryIso(context: Context): String {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        return listOfNotNull(telephony?.networkCountryIso, telephony?.simCountryIso, Locale.getDefault().country)
            .firstOrNull { it.isNotBlank() }
            ?.uppercase(Locale.ROOT)
            ?: "US"
    }
}

// System settings ----------------------------------------------------------------

/**
 * A page of Android's settings, found by English or Portuguese words. Several ways in are
 * tried in order, as phone makers move pages around. [isTorch] switches the flashlight instead.
 */
class SystemSetting(
    val label: String,
    keywords: List<String>,
    val intents: List<() -> Intent> = emptyList(),
    val isTorch: Boolean = false,
) {
    val keys = (keywords + label).map(AppSearch::normalize)
}

object SettingSearch {
    private fun page(action: String): () -> Intent = { Intent(action) }

    private fun component(pkg: String, cls: String): () -> Intent = { Intent().setClassName(pkg, cls) }

    private val all = listOf(
        SystemSetting("Flashlight", listOf("lanterna", "torch", "luz", "flash"), isTorch = true),
        SystemSetting(
            "Wi-Fi",
            listOf("wifi", "wi-fi", "wi fi", "wireless", "internet", "rede"),
            listOf(page(Settings.ACTION_WIFI_SETTINGS)),
        ),
        SystemSetting("Bluetooth", listOf("bluetooth", "fone", "headphones"), listOf(page(Settings.ACTION_BLUETOOTH_SETTINGS))),
        SystemSetting(
            "Mobile data",
            listOf("dados moveis", "dados", "mobile data", "4g", "5g", "chip", "sim", "operadora"),
            listOf(page(Settings.ACTION_DATA_USAGE_SETTINGS), page(Settings.ACTION_WIRELESS_SETTINGS)),
        ),
        SystemSetting(
            "Hotspot",
            listOf("hotspot", "roteador", "ancoragem", "tethering", "compartilhar internet", "ponto de acesso"),
            listOf(
                component("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity"),
                component("com.android.settings", "com.android.settings.TetherSettings"),
                page(Settings.ACTION_WIRELESS_SETTINGS),
            ),
        ),
        SystemSetting(
            "Airplane mode",
            listOf("modo aviao", "aviao", "airplane", "flight", "voo"),
            listOf(page(Settings.ACTION_AIRPLANE_MODE_SETTINGS)),
        ),
        SystemSetting(
            "Display",
            listOf("tela", "brilho", "brightness", "tema escuro", "dark theme", "fonte", "font", "display"),
            listOf(page(Settings.ACTION_DISPLAY_SETTINGS)),
        ),
        SystemSetting(
            "Sound",
            listOf("som", "volume", "toque", "ringtone", "vibrar", "vibration", "sound"),
            listOf(page(Settings.ACTION_SOUND_SETTINGS)),
        ),
        SystemSetting(
            "Do not disturb",
            listOf("nao perturbe", "do not disturb", "dnd", "silencio", "silent", "foco"),
            listOf(page("android.settings.ZEN_MODE_SETTINGS"), page(Settings.ACTION_SOUND_SETTINGS)),
        ),
        SystemSetting(
            "Battery",
            listOf("bateria", "battery", "economia de energia", "battery saver"),
            listOf(page(Intent.ACTION_POWER_USAGE_SUMMARY), page(Settings.ACTION_BATTERY_SAVER_SETTINGS)),
        ),
        SystemSetting(
            "Location",
            listOf("localizacao", "local", "gps", "location"),
            listOf(page(Settings.ACTION_LOCATION_SOURCE_SETTINGS)),
        ),
        SystemSetting(
            "Notifications",
            listOf("notificacoes", "notifications"),
            listOf(page("android.settings.NOTIFICATION_SETTINGS"), page(Settings.ACTION_SETTINGS)),
        ),
        SystemSetting("Apps", listOf("aplicativos", "apps"), listOf(page(Settings.ACTION_APPLICATION_SETTINGS))),
        SystemSetting(
            "Default apps",
            listOf("apps padrao", "padrao", "default apps"),
            listOf(page(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)),
        ),
        SystemSetting(
            "Storage",
            listOf("armazenamento", "memoria", "espaco", "storage"),
            listOf(page(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)),
        ),
        SystemSetting(
            "Security",
            listOf("seguranca", "senha", "bloqueio de tela", "digital", "biometria", "security", "password", "fingerprint", "pin"),
            listOf(page(Settings.ACTION_SECURITY_SETTINGS)),
        ),
        SystemSetting("Privacy", listOf("privacidade", "privacy", "permissoes", "permissions"), listOf(page(Settings.ACTION_PRIVACY_SETTINGS))),
        SystemSetting(
            "Accessibility",
            listOf("acessibilidade", "accessibility"),
            listOf(page(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
        ),
        SystemSetting(
            "Date & time",
            listOf("data", "hora", "fuso horario", "date", "time", "timezone"),
            listOf(page(Settings.ACTION_DATE_SETTINGS)),
        ),
        SystemSetting("Language", listOf("idioma", "lingua", "language"), listOf(page(Settings.ACTION_LOCALE_SETTINGS))),
        SystemSetting("Keyboard", listOf("teclado", "keyboard"), listOf(page(Settings.ACTION_INPUT_METHOD_SETTINGS))),
        SystemSetting("Accounts", listOf("contas", "accounts", "sincronizar", "sync"), listOf(page(Settings.ACTION_SYNC_SETTINGS))),
        SystemSetting("NFC", listOf("nfc", "aproximacao", "contactless"), listOf(page(Settings.ACTION_NFC_SETTINGS))),
        SystemSetting(
            "Cast",
            listOf("transmitir", "espelhar", "espelhamento", "cast", "chromecast"),
            listOf(page(Settings.ACTION_CAST_SETTINGS)),
        ),
        SystemSetting(
            "System update",
            listOf("atualizacao", "atualizar sistema", "update"),
            listOf(page("android.settings.SYSTEM_UPDATE_SETTINGS"), page(Settings.ACTION_DEVICE_INFO_SETTINGS)),
        ),
        SystemSetting(
            "About phone",
            listOf("sobre o telefone", "sobre", "about phone", "versao do android"),
            listOf(page(Settings.ACTION_DEVICE_INFO_SETTINGS)),
        ),
        SystemSetting(
            "Developer options",
            listOf("desenvolvedor", "developer"),
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
        } catch (_: CameraAccessException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
