# Minimalist

A quiet, text-only Android home screen. Black background, a clock, the few apps
you actually use, and everything else one swipe away.

No app icons, no widgets, no ads, no tracking, no internet permission. Nothing it reads ever leaves the phone.

## What it does

| | |
|---|---|
| **Home** | Clock, a battery line as wide as the time, then the date and battery percentage. Up to 8 pinned apps. Everything is centred by default, or left aligned. A row of line icons along the bottom: WhatsApp, Waze, Google Photos, Spotify and the camera (an app that isn't installed leaves its slot empty). Tap the clock for alarms, the date for your calendar, the battery for battery usage. The line breathes while charging and turns red below 15%. |
| **App list** | Swipe up. The keyboard opens and search starts as you type; when only one app matches, it opens on its own. Press *Go* to open the top match. Accents are ignored, so `camera` finds *Câmera*. |
| **Long-press an app** | Add to home, remove, reorder, rename, hide, app info, uninstall. |
| **Gestures** | Swipe up for the app list, down for notifications. Drag the list down (or press back) to close it. |
| **Bottom icons** | Long-press one to swap in any app. Apps with a hand-drawn glyph keep it; others show their initial in a thin circle. |
| **Extras** (off by default, in Settings) | *Screen time*: today's time in apps under the date (usage access). *Now playing*: the song with ⏮ ⏯ ⏭ under the date (notification access). *Double-tap to lock*: double-tap empty space to turn the screen off (accessibility service). |
| **Settings** | Long-press empty space on the home screen: text size, auto-open, keyboard, layout (centred or left aligned), status bar (visible, dimmed or hidden), hidden apps, default home app. |

## Install on your phone

1. Open the [latest build](../../releases/tag/latest) on your phone and download
   `minimalist-launcher.apk`.
2. Open it and allow your browser or Files app to install unknown apps when asked.
3. Open **Minimalist** and tap **Set as home app** at the bottom of the screen
   (or go to *Settings → Apps → Default apps → Home app*).

Every push to `main` builds a new APK. Installing it over the previous one keeps
your favourites and settings.

From a computer, `adb install -r minimalist-launcher.apk` works too.

### Restricted settings

Android blocks notification access and accessibility services for apps installed
outside an app store. If the switch is greyed out, open *Settings → Apps →
Minimalist*, tap ⋮ and choose **Allow restricted settings**, then try again.
Minimalist's settings screen has a shortcut to that page.

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
