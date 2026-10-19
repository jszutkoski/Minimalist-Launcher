package com.jszutkoski.launcher

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.view.View
import android.widget.Toast

/**
 * What tapping a search result does, for everything but an app: sums, timers, contacts,
 * settings, a search in one app, the web.
 */
class SearchActions(
    private val activity: Activity,
    private val repo: AppRepository,
    private val launch: (App, View) -> Unit,
    /** Runs the second argument now, or after the pause when that package's app is paused. */
    private val throughPause: (String?, () -> Unit) -> Unit,
    private val closeDrawer: () -> Unit,
) {
    fun open(result: SearchResult, source: View) {
        when (result) {
            is SearchResult.AppHit -> launch(result.app, source)
            is SearchResult.Answer -> copy(Calculator.plain(result.value, result.numbers))
            is SearchResult.ContactHit -> showContact(result.contact)
            is SearchResult.SettingHit -> openSetting(result.setting)
            is SearchResult.Clock -> openClock(result.command)
            is SearchResult.InApp -> searchIn(result.search)
            is SearchResult.Web -> webSearch(result.query)
        }
    }

    /** The web search, through the pause when the app that answers it (the browser) is paused. */
    private fun webSearch(query: String) {
        val handler = handler(Intent(Intent.ACTION_WEB_SEARCH))
            ?: handler(Intent(Intent.ACTION_VIEW, Uri.parse("https://")).addCategory(Intent.CATEGORY_BROWSABLE))
        throughPause(handler) { SystemActions.webSearch(activity, query) }
    }

    /** The one app that opens [intent], or null when there is none or Android would ask which. */
    private fun handler(intent: Intent): String? =
        activity.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
            // "android" is the chooser: more than one, none the default.
            ?.takeIf { it != "android" }

    /** Installed for this user: an app only in the work profile can't be started from here. */
    private fun installed(packageName: String) =
        repo.apps.any { it.component.packageName == packageName && it.user == Process.myUserHandle() }

    /** In the app itself when it is installed, otherwise the same link in the browser. */
    private fun searchIn(search: InAppSearch) {
        val link = Intent(Intent.ACTION_VIEW, Uri.parse(search.target.link(search.query)))
        val pkg = search.target.packageName
        if (installed(pkg)) {
            // A way into the app like any other: a paused app pauses first.
            throughPause(pkg) {
                if (!SystemActions.start(activity, Intent(link).setPackage(pkg))) SystemActions.start(activity, link)
            }
            return
        }
        throughPause(handler(link)) { SystemActions.start(activity, link) }
    }

    fun whatsApp(contact: Contact) {
        val pkg = whatsAppPackage() ?: return
        val number = contact.whatsApp ?: return
        throughPause(pkg) {
            SystemActions.start(activity, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number")).setPackage(pkg))
        }
    }

    /** The WhatsApp installed for this user, or null: contacts offer it only then. */
    fun whatsAppPackage(): String? = Packages.WHATSAPP.firstOrNull(::installed)

    private fun copy(value: String) {
        activity.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Result", value))
        // Android 13+ confirms a copy on its own.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) toast(activity.getString(R.string.copied, value))
    }

    private fun showContact(contact: Contact) {
        fun open(action: String, scheme: String) =
            SystemActions.start(activity, Intent(action, Uri.fromParts(scheme, contact.phone, null)))
        Sheet(activity, "${contact.name} · ${contact.phone}").apply {
            if (contact.whatsApp != null && whatsAppPackage() != null) option("WhatsApp") { whatsApp(contact) }
            option(activity.getString(R.string.contact_call)) { open(Intent.ACTION_DIAL, "tel") }
            option(activity.getString(R.string.contact_message)) { open(Intent.ACTION_SENDTO, "smsto") }
            // No lookup key, no card to open.
            ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)?.let { card ->
                option(activity.getString(R.string.contact_card)) { SystemActions.start(activity, Intent(Intent.ACTION_VIEW, card)) }
            }
            show()
        }
    }

    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()

    /** Handed to the clock app, which shows the timer running or the alarm set, to change or undo. */
    private fun openClock(command: ClockCommand) {
        val intent = when (command) {
            is ClockCommand.Timer -> Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, command.seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            is ClockCommand.Alarm -> Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, command.hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, command.minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            is ClockCommand.Page -> Intent(if (command.alarms) AlarmClock.ACTION_SHOW_ALARMS else AlarmClock.ACTION_SHOW_TIMERS)
        }
        if (!SystemActions.start(activity, intent)) toast(activity.getString(R.string.no_clock_app))
    }

    private fun openSetting(setting: SystemSetting) {
        if (setting.isTorch) {
            if (!Torch.toggle(activity)) toast(activity.getString(R.string.torch_unavailable))
            closeDrawer()
            return
        }
        setting.intents.any { SystemActions.start(activity, it()) }
    }
}
