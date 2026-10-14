# Minimalist

A quiet, text-only Android home screen. Black background, a clock, the few apps
you actually use, and everything else one swipe away.

No icons, no widgets, no ads, no tracking, no internet permission.

## What it does

| | |
|---|---|
| **Home** | Clock and date at the top, up to 8 pinned apps near your thumb. Tap the clock for alarms, the date for your calendar. |
| **App list** | Swipe up. The keyboard opens and search starts as you type; when only one app matches, it opens on its own. Press *Go* to open the top match. Accents are ignored, so `camera` finds *Câmera*. |
| **Long-press an app** | Add to home, remove, reorder, rename, hide, app info, uninstall. |
| **Gestures** | Swipe down for notifications, left for the camera, right for the phone. Drag the list down (or press back) to close it. |
| **Settings** | Long-press empty space on the home screen: text size, auto-open, keyboard, hidden apps, default home app. |

## Install on your phone

1. Open the [latest build](../../releases/tag/latest) on your phone and download
   `minimalist-launcher.apk`.
2. Open it and allow your browser or Files app to install unknown apps when asked.
3. Open **Minimalist** and tap **Set as home app** at the bottom of the screen
   (or go to *Settings → Apps → Default apps → Home app*).

Every push to `main` builds a new APK. Installing it over the previous one keeps
your favourites and settings.

From a computer, `adb install -r minimalist-launcher.apk` works too.

## Build it yourself

Requires JDK 17 and the Android SDK (Android Studio installs both).

```sh
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

The project uses only the Android framework, with no AndroidX or other libraries,
so the APK stays tiny.

## Signing

`app/personal.keystore` signs every build with the same key, so new builds install
as updates. It's fine for sideloading onto your own phone. Because the key is in
the repository, anyone with access to the repository could sign an APK that
installs over yours, so only install builds from this repository. For Google Play,
create a separate upload key and keep it out of the repository.
