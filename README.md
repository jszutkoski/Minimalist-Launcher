# Minimalist

A quiet, text-only Android home screen. Black background, a clock, the few apps
you actually use, and everything else one swipe away.

No app icons, no widgets, no ads, no tracking. The only thing that ever leaves the phone is the weather request, when that option is on: an approximate position (rounded to about a kilometre) sent to Open-Meteo. Contacts, notifications and usage never do.

## What it does

| | |
|---|---|
| **Home** | Clock, a battery line as wide as the time with the percentage set into it right where the fill ends, the line parting around the number, then the date (and the next alarm). Up to 8 pinned apps. Everything is centred by default, or left aligned. A row of line icons along the bottom: WhatsApp, Waze, Google Photos, Spotify and the camera (an app that isn't installed leaves its slot empty). Tap the clock for alarms, the date for your calendar, the battery for battery usage. While charging a small bolt sits beside the number and the line breathes; full on the charger it all turns green and pulses slowly; below 15% it turns red. |
| **App list** | Swipe up. The keyboard opens and search starts as you type; when only one app matches, it opens on its own. Press *Go* to open the top match (with nothing typed, *Go* just closes the keyboard). Accents are ignored, so `camera` finds *Câmera*. Apps in a work profile or Android 15's private space are marked as such; a locked private space shows nothing. |
| **Long-press an app** | The app's own shortcuts first (Waze "Drive home", WhatsApp chats…), then add to home, remove, reorder, rename, pause before opening, hide, app info, uninstall. A shortcut into a paused app goes through the pause too. Pressing home closes the menu. |
| **Gestures** | Swipe up for the app list, down for notifications. Drag the list down (or press back) to close it. |
| **Standby** | A dim bedside clock (time, battery line, date, the weather when that option is on, plus a WhatsApp or envelope glyph when those apps have notifications waiting; over the lock screen only if *Message icons on the lock screen* is on) at half strength, at the system brightness, that drifts a little every minute. Opens after 20 seconds on the home screen while charging, or with a two-finger pinch; tap to leave; the charging bolt and green "full" show here too. Leaving always asks for the fingerprint or PIN: over the lock screen through Android's own unlock, and on a phone that is still unlocked through the fingerprint prompt once the screen has been on for 10 seconds (sooner, a tap goes straight in, like a screen that timed out a moment ago); pressing home or recents instead locks the phone (with Minimalist's accessibility service on). On the charger it stays lit all night **only over the lock screen**: while the phone is still unlocked it times out like any screen, so nobody can pull down the notifications and read or answer your messages; once Android has locked, the next wake shows it again and it stays on. Tapping then asks for your PIN or fingerprint, and unlocking any other way closes it. With *Standby on every wake* on (it needs Minimalist's accessibility service, the same one as double-tap to lock), it is put on top each time the screen turns off (except during calls), so it is the first thing you see on every wake; after unlocking you are back in the app you were using. |
| **Next alarm** | Beside the date and battery: "6:30" within a day, "Sat 6:30" further out. Tap for alarms. |
| **Notification dots** (setting) | A small dot beside pinned apps and bottom icons with notifications waiting (notification access). |
| **Smart search** | The app search also answers sums (`12*7`, `2,5 + 1`, `1.500 + 300`, `200 x 15%`, `200 + 10%` = 220; thousands and decimals the local way; phone numbers and dates are left alone; tap to copy), finds Android settings by English or Portuguese words (`wifi`, `brilho`, `hotspot`, `bateria`…), switches the flashlight (`lanterna`), and ends with a web search. With *Contacts in search* on (contacts permission), names appear from two letters: the chat glyph opens their WhatsApp chat directly; tapping the name offers WhatsApp, call, message and the contact card. A single app opens on its own only from two letters that begin its name or a word in it ("ai" for aiqfome, never "q"), never while a contact matches, and a matching setting stops it only from three letters (so "in" still opens Instagram). |
| **Weather** (setting) | Temperature and sky under the date on the bedside clock (standby), from Open-Meteo (free, no account) for the approximate location, in °C unless Android's regional preferences say °F or the SIM is from the US; refreshed at most every half hour, last place remembered when location is off. |
| **Search only** (setting) | The app list stays empty until you type: apps appear only as their name is searched, so there is nothing to scroll through idly. The keyboard always opens with it. When a name escapes you, *Show all apps* under the search lists everything until the list closes. |
| **Mindful pause** (setting) | Chosen apps open only after a five-second "Breathe in / Breathe out", with today's time in the app when usage access is on. |
| **Bottom icons** | Long-press one to swap in any app. Apps with a hand-drawn glyph keep it; others show their initial in a thin circle. |
| **Extras** (off by default, in Settings) | *Screen time*: today's time in apps, on its own line under the date (usage access). *Now playing*: the song, artist and ⏮ ⏯ ⏭ just above the bottom icons while a player holds a track, paused or playing; its space stays reserved so nothing moves (notification access). Tap screen time to open Digital Wellbeing. *Double-tap to lock*: double-tap empty space to turn the screen off (accessibility service). |
| **Language** | English or Brazilian Portuguese, following the phone; on Android 13+ *Settings → Apps → Minimalist → Language* picks one for Minimalist alone. |
| **Accessibility** | With TalkBack on, *All apps* and *Settings* appear as buttons on the home screen, since TalkBack can't make the swipes. On a short screen or with large text the pinned names shrink a little so the bottom icons always fit. |
| **Settings** | Long-press empty space on the home screen. Five groups, each its own page (back returns to the list): *Home screen*, *Search*, *Standby*, *Apps* and *Gestures*. |

## Install on your phone

1. Open the [latest build](../../releases/tag/latest) on your phone and download
   `minimalist-launcher.apk`.
2. Open it and allow your browser or Files app to install unknown apps when asked.
3. Open **Minimalist** and tap **Set as home app** at the bottom of the screen
   (or go to *Settings → Apps → Default apps → Home app*).

Every push to `main` builds a new APK, numbered by its CI run (*App info* shows
it). Installing it over the previous one keeps your favourites and settings. Each
release also carries the APK's SHA-256, to check the download. Finished versions get
their own release ([v1.0](../../releases/tag/v1.0) and later), which stays put while
*latest* moves on.

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
./gradlew testReleaseUnitTest lintRelease assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

CI runs the same: the unit tests (calculator, search, screen time, weather retries)
and Android lint must pass before an APK is published. A version release is made by
running the workflow by hand with a version number, or by pushing a `v1.1`-style tag.

The project uses only the Android framework, with no AndroidX or other libraries,
so the APK stays tiny.

## Signing

Release builds are signed with a key kept only in the repository's Actions secrets,
never in the code:

- `MINIMALIST_KEYSTORE`: the PKCS12 keystore, base64-encoded (alias `minimalist`).
- `MINIMALIST_KEYSTORE_PASSWORD`: its password (store and key).

CI decodes the key to a temporary file for the build and deletes it afterwards. Keep a
private backup of the keystore: losing it means uninstalling and reinstalling once.

Without them (a local build, a pull request from a fork) the release APK comes out
unsigned, and CI refuses to publish it. Debug builds use Android's own debug key and
install as a separate app (`.debug`). For Google Play, use a separate upload key, also
kept out of the repository.
