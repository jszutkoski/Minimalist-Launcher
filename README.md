# Minimalist

A quiet, text-only Android home screen. Black background, a clock, the few apps
you actually use, and everything else one swipe away.

No app icons, no widgets, no ads, no tracking. The only thing that ever leaves the phone is the weather request, when that option is on: an approximate position (rounded to about a kilometre) sent to Open-Meteo. Contacts, notifications and usage never do.

## What it does

| | |
|---|---|
| **Home** | Clock, a battery line as wide as the time with the percentage set into it right where the fill ends, the line parting around the number, then the date (and the next alarm). Up to 8 pinned apps. Everything is centred by default, or left aligned. A row of line icons along the bottom: WhatsApp, Waze, Google Photos, Spotify and the camera (an app that isn't installed leaves its slot empty). Tap the clock for alarms, the date for your calendar, the battery for battery usage. While charging a small bolt sits beside the number and the line breathes; full on the charger it all turns green and pulses slowly; below 15% it turns red. |
| **App list** | Swipe up. The keyboard opens and search starts as you type; when only one app matches, it opens on its own. Press *Go* to open the top match. Accents are ignored, so `camera` finds *Câmera*. |
| **Long-press an app** | The app's own shortcuts first (Waze "Drive home", WhatsApp chats…), then add to home, remove, reorder, rename, pause before opening, hide, app info, uninstall. |
| **Gestures** | Swipe up for the app list, down for notifications. Drag the list down (or press back) to close it. |
| **Standby** | A dim bedside clock (time, battery line, date, the weather when that option is on, plus a WhatsApp or envelope glyph when those apps have notifications waiting at half strength, at the system brightness) that drifts a little every minute. Opens after 20 seconds on the home screen while charging, or with a two-finger pinch; tap to leave; the charging bolt and green "full" show here too. Leaving always asks for the fingerprint or PIN: over the lock screen through Android's own unlock, and on a phone that is still unlocked (opened from home, or kept awake by the charger) through the fingerprint prompt; pressing home or recents instead locks the phone (with Minimalist's accessibility service on). Keeps the screen on only while charging. If the phone locks while it is open, pressing power shows it again over the lock screen; tapping then asks for your PIN or fingerprint, and unlocking any other way closes it. With *Standby on every wake* on (it needs Minimalist's accessibility service, the same one as double-tap to lock), it is put on top each time the screen turns off (except during calls), so it is the first thing you see on every wake; after unlocking you are back in the app you were using. |
| **Next alarm** | Beside the date and battery: "6:30" within a day, "Sat 6:30" further out. Tap for alarms. |
| **Notification dots** (setting) | A small dot beside pinned apps and bottom icons with notifications waiting (notification access). |
| **Smart search** | The app search also answers sums (`12*7`, `2,5 + 1`, `200 x 15%`; tap to copy), finds Android settings by English or Portuguese words (`wifi`, `brilho`, `hotspot`, `bateria`…), switches the flashlight (`lanterna`), and ends with a web search. With *Contacts in search* on (contacts permission), names appear from two letters: the chat glyph opens their WhatsApp chat directly; tapping the name offers WhatsApp, call, message and the contact card. A single app opens on its own only from two letters that begin its name or a word in it ("ai" for aiqfome, never "q"), and only when nothing else matches. |
| **Weather** (setting) | Temperature and sky under the date on the bedside clock (standby), from Open-Meteo (free, no account) for the approximate location, in °C unless Android's regional preferences say °F or the SIM is from the US; refreshed at most every half hour, last place remembered when location is off. |
| **Search only** (setting) | The app list stays empty until you type: apps appear only as their name is searched, so there is nothing to scroll through idly. The keyboard always opens with it. When a name escapes you, *Show all apps* under the search lists everything until the list closes. |
| **Mindful pause** (setting) | Chosen apps open only after a five-second "Breathe in / Breathe out", with today's time in the app when usage access is on. |
| **Bottom icons** | Long-press one to swap in any app. Apps with a hand-drawn glyph keep it; others show their initial in a thin circle. |
| **Extras** (off by default, in Settings) | *Screen time*: today's time in apps, on its own line under the date (usage access). *Now playing*: the song, artist and ⏮ ⏯ ⏭ just above the bottom icons while a player holds a track, paused or playing; its space stays reserved so nothing moves (notification access). Tap screen time to open Digital Wellbeing. *Double-tap to lock*: double-tap empty space to turn the screen off (accessibility service). |
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

Android blocks usage access, notification access and accessibility services for
apps installed outside an app store ("App was denied access"). After one blocked
attempt, open *Settings → Apps → Minimalist*, tap ⋮ and choose **Allow restricted
settings**, then try again. Minimalist's settings screen has a shortcut to that
page.

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
