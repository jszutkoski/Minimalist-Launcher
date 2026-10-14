package com.jszutkoski.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
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

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    fun start() {
        launcherApps.registerCallback(callback, mainHandler)
        reload()
    }

    fun stop() {
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

    private fun query(): List<Entry> {
        val me = Process.myUserHandle()
        return launcherApps.profiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            val activities = runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
            activities
                .filter { it.componentName.packageName != context.packageName }
                .map { info ->
                    val label = info.label.toString().trim()
                    Entry(
                        key = "${info.componentName.flattenToShortString()}#$serial",
                        label = if (user == me) label else "$label · work",
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

    /** Prefix matches first, then word starts, then anywhere; alphabetical within each. */
    fun filter(apps: List<App>, query: String): List<App> {
        val q = normalize(query.trim())
        if (q.isEmpty()) return apps
        return apps
            .mapNotNull { app -> rank(app.searchKey, q)?.let { it to app } }
            .sortedBy { it.first }
            .map { it.second }
    }

    private fun rank(key: String, q: String): Int? = when {
        key.startsWith(q) -> 0
        key.contains(" $q") -> 1
        key.contains(q) -> 2
        else -> null
    }
}
