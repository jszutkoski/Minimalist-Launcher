package com.jszutkoski.launcher

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import java.text.Collator
import java.text.Normalizer
import java.util.concurrent.Executors

class App(
    /** Stable id: component plus user, so work-profile copies stay distinct. */
    val key: String,
    val label: String,
    val originalLabel: String,
    val component: ComponentName,
    val user: UserHandle,
    val isSystem: Boolean,
) {
    val searchKey: String = AppSearch.normalize(if (label == originalLabel) label else "$label $originalLabel")
}

/** Loads launchable apps off the main thread and reloads when packages change. */
class AppRepository(private val context: Context, private val prefs: Prefs) {
    private class Entry(
        val key: String,
        val label: String,
        val component: ComponentName,
        val user: UserHandle,
        val isSystem: Boolean,
    )

    private companion object {
        /** UserManager.USER_TYPE_PROFILE_PRIVATE, Android 15's private space. */
        const val USER_TYPE_PRIVATE = "android.os.usertype.profile.PRIVATE"
    }

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var entries: List<Entry> = emptyList()
    private var byKey: Map<String, App> = emptyMap()

    /** Every launchable app, hidden ones included, sorted by label. */
    var apps: List<App> = emptyList()
        private set
    var loaded = false
        private set
    var onChanged: (() -> Unit)? = null

    /** A work profile or private space turned on or off, added or removed. */
    private val profiles = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reload()
    }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    fun start() {
        launcherApps.registerCallback(callback, mainHandler)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            // Android 14's names for any profile, the private space included.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                listOf("ADDED", "REMOVED", "AVAILABLE", "UNAVAILABLE").forEach { addAction("android.intent.action.PROFILE_$it") }
            }
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0
        context.registerReceiver(profiles, filter, flags)
        reload()
    }

    fun stop() {
        context.unregisterReceiver(profiles)
        launcherApps.unregisterCallback(callback)
        executor.shutdownNow()
    }

    fun reload() {
        if (executor.isShutdown) return
        executor.execute {
            val result = query()
            mainHandler.post {
                entries = result
                loaded = true
                rebuild()
            }
        }
    }

    /** Re-applies custom names without asking the system for the app list again. */
    fun rebuild() {
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        apps = entries
            .map { App(it.key, prefs.customName(it.key) ?: it.label, it.label, it.component, it.user, it.isSystem) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
        byKey = apps.associateBy { it.key }
        onChanged?.invoke()
    }

    fun find(key: String): App? = byKey[key]

    fun visibleApps(): List<App> {
        val hidden = prefs.hidden
        return apps.filter { it.key !in hidden }
    }

    private fun isPrivateSpace(user: UserHandle): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            runCatching { launcherApps.getLauncherUserInfo(user)?.userType == USER_TYPE_PRIVATE }.getOrDefault(false)

    private fun query(): List<Entry> {
        val me = Process.myUserHandle()
        return launcherApps.profiles.flatMap { user ->
            val private = user != me && isPrivateSpace(user)
            // A locked private space shows nothing, as on Android's own home screen.
            if (private && userManager.isQuietModeEnabled(user)) return@flatMap emptyList()
            val tag = when {
                user == me -> null
                private -> R.string.profile_private
                else -> R.string.profile_work
            }
            val serial = userManager.getSerialNumberForUser(user)
            val activities = runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
            activities
                .filter { it.componentName.packageName != context.packageName }
                .map { info ->
                    val label = info.label.toString().trim()
                    Entry(
                        key = "${info.componentName.flattenToShortString()}#$serial",
                        label = if (tag == null) label else context.getString(tag, label),
                        component = info.componentName,
                        user = user,
                        isSystem = info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    )
                }
        }
    }
}

object AppSearch {
    private val marks = "\\p{Mn}+".toRegex()

    /** Lowercase without accents, so "camera" finds "Câmera". */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(marks, "").lowercase()

    fun filter(apps: List<App>, query: String): List<App> = filter(apps, query) { it.searchKey }

    /** Prefix matches first, then word starts, then anywhere; in the given order within each. */
    fun <T> filter(items: List<T>, query: String, key: (T) -> String): List<T> {
        val q = normalize(query.trim())
        if (q.isEmpty()) return items
        return items
            .mapNotNull { item -> rank(key(item), q)?.let { it to item } }
            .sortedBy { it.first }
            .map { it.second }
    }

    /** The query begins the name or one of its words: "ai" or "fome" for aiqfome, never "q". */
    fun startsWord(key: String, query: String): Boolean {
        val q = normalize(query.trim())
        return q.isNotEmpty() && (key.startsWith(q) || key.contains(" $q"))
    }

    /**
     * Whether the search should open its one app on its own: from two letters that begin the
     * name or a word of it, so a question can still be typed; never while a contact matches;
     * and a setting stops it only from three letters, as two ("in", "ba") match so many.
     */
    fun opensAlone(query: String, appKeys: List<String>, contacts: Int, settings: Int): Boolean {
        val q = query.trim()
        return q.length >= 2 && appKeys.size == 1 && startsWord(appKeys[0], q) &&
            contacts == 0 && (settings == 0 || q.length < 3)
    }

    private fun rank(key: String, q: String): Int? = when {
        key.startsWith(q) -> 0
        key.contains(" $q") -> 1
        key.contains(q) -> 2
        else -> null
    }
}
