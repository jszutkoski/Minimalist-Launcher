package com.jszutkoski.launcher

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.view.View
import android.widget.Toast

/** What tapping a search result does, for everything but an app: sums, contacts, settings, the web. */
class SearchActions(
    private val activity: Activity,
    private val repo: AppRepository,
    private val launch: (App, View) -> Unit,
    private val closeDrawer: () -> Unit,
) {
    fun open(result: SearchResult, source: View) {
        when (result) {
            is SearchResult.AppHit -> launch(result.app, source)
            is SearchResult.Answer -> copy(Calculator.plain(result.value, result.numbers))
            is SearchResult.ContactHit -> showContact(result.contact)
            is SearchResult.SettingHit -> openSetting(result.setting)
            is SearchResult.Web -> SystemActions.webSearch(activity, result.query)
        }
    }

    fun whatsApp(contact: Contact) {
        val pkg = whatsAppPackage() ?: return
        val number = contact.whatsApp ?: return
        SystemActions.start(activity, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number")).setPackage(pkg))
    }

    /** The WhatsApp installed, or null: contacts offer it only then. */
    fun whatsAppPackage(): String? =
        Packages.WHATSAPP.firstOrNull { pkg -> repo.apps.any { it.component.packageName == pkg } }

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
            option(activity.getString(R.string.contact_card)) {
                SystemActions.start(
                    activity,
                    Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)),
                )
            }
            show()
        }
    }

    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()

    private fun openSetting(setting: SystemSetting) {
        if (setting.isTorch) {
            if (!Torch.toggle(activity)) toast(activity.getString(R.string.torch_unavailable))
            closeDrawer()
            return
        }
        setting.intents.any { SystemActions.start(activity, it()) }
    }
}
