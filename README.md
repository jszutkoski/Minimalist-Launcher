# Minimalist

A quiet, text-only Android home screen. Black background, a clock, the few apps
you actually use, and everything else one swipe away.

No app icons, no widgets, no ads, no tracking. The only thing that ever leaves the phone is the weather request, when that option is on: an approximate position (rounded to about a kilometre) sent to Open-Meteo. Contacts, notifications and usage never do.

## What it does

| | |
|---|---|
| **Home** | Clock, a battery line as wide as the time with the percentage set into it right where the fill ends, the line parting around the number, then the date (and the next alarm). Up to 8 pinned apps. Everything is centred by default, or left aligned. A row of line icons along the bottom: WhatsApp, Waze, Google Photos, Spotify and the camera to start with, up to six of your choice (an app that is uninstalled leaves its place empty). Tap the clock for alarms, the date for your calendar, the battery for battery usage. While charging the line and its number breathe; full on the charger it all turns green and pulses slowly; below 15% it turns red. |
| **App list** | Swipe up. The keyboard opens and search starts as you type; when only one app matches, it opens on its own. Press *Go* to open the top match (with nothing typed, *Go* just closes the keyboard). Accents are ignored, so `camera` finds *Câmera*. Apps in a work profile or Android 15's private space are marked as such; a locked private space shows nothing. |
| **Long-press an app** | The app's own shortcuts first (Waze "Drive home", WhatsApp chats…), then add to home or to the bottom bar, remove, reorder, rename, pause before opening, hide, app info, uninstall. A shortcut into a paused app goes through the pause too. Pressing home closes the menu. |
| **Gestures** | Swipe up for the app list, down for notifications. Drag the list down (or press back) to close it. Swiping left or right can open an app of your choice (*Settings → Gestures*, off by default; start away from the screen's edge, which Android keeps for back). |
| **Standby** | A bedside clock (the clock set up for it, the date, the weather and screen time when those are on, plus a line glyph for each app with notifications waiting: calls, messages, WhatsApp, Instagram, email and many more, one glyph per kind, with apps that have no glyph of their own sharing three dots; *Notification icons* in Standby settings chooses which apps may show one; over the lock screen only if *Notification icons on the lock screen* is on) slightly dimmed, at the system brightness, that drifts a little every minute. Opens after 20 seconds on the home screen while charging, or with a two-finger pinch; tap to leave; a charging bolt (here, where there is no number) and the green "full" show too. Leaving always asks for the fingerprint or PIN: over the lock screen through Android's own unlock, and on a phone that is still unlocked through the fingerprint prompt once the screen has been on for 10 seconds (sooner, a tap goes straight in, like a screen that timed out a moment ago); pressing home or recents instead locks the phone (with Minimalist's accessibility service on). On the charger it stays lit all night **only over the lock screen**, so nobody can pull down the notifications and read or answer your messages. With Minimalist's accessibility service on, a standby that opens on a still-unlocked phone locks it after 10 untouched seconds and lights straight up again over the lock screen, and every screen off on the charger puts it back on top for the next wake; without the service, a still-unlocked phone times out like any screen and the next wake after Android locks shows it, lit. Tapping then asks for your PIN or fingerprint, and unlocking any other way closes it. Pulling down the notifications over it locks a still-unlocked phone. Over the lock screen the shade is Android's own, exactly as without Minimalist: what it shows is set in Android (*Notifications on the lock screen* in Standby settings goes there). With *Standby on every wake* on (it needs Minimalist's accessibility service, the same one as double-tap to lock), it is put on top each time the screen turns off (except during calls), so it is the first thing you see on every wake; after unlocking you are back in the app you were using. |
| **Clock** (setting) | The home screen and the bedside clock each set up their own, picked at the top of *Settings → Clock*. One block in two designs: *Digital*, the time with the battery line under it as wide as the time; or *Analog*, a dark dial with the 12, 3, 6 and 9, a mark for every minute, three hands, and the battery as a segmented arc along the right-hand edge, from the 1 to the 5 (it breathes while charging, turns green when full and red when low, as the line does). Five thicknesses from ultra thin to bold and three sizes, for the clock and its battery together. The date above the clock, below it, or hidden; the weather and today's screen time each on a line under it, if wanted; one even gap between them all, measured from the glyphs. A live preview, centred, at the top of *Settings → Clock*, in a space that keeps its height so the options never move. The digits use a cut-down [Roboto Flex](https://github.com/googlefonts/roboto-flex) (SIL Open Font License, in `app/src/main/assets/fonts`) so every thickness looks the same on any phone. A clock too wide for the screen shrinks until it fits. |
| **Next alarm** | Beside the date and battery: "6:30" within a day, "Sat 6:30" further out. Tap for alarms. |
| **Notification dots** (setting) | A small dot beside pinned apps and bottom icons with notifications waiting (notification access). |
| **Smart search** | The app search also answers sums (`12*7`, `2,5 + 1`, `1.500 + 300`, `200 x 15%`, `200 + 10%` = 220; thousands and decimals the way of the country the phone is in, from its SIM, so a phone in English in Brazil reads `1.500` as fifteen hundred; phone numbers and dates are left alone; tap to copy), finds Android settings by English or Portuguese words (`wifi`, `brilho`, `hotspot`, `bateria`…), switches the flashlight (`lanterna`), starts timers and sets alarms in the clock app (`timer 10 min`, `timer de 1h30`, `alarme 7:30`, `alarm 7am`, `alarme amanhã às 6h`; `10 min` or `7:30` alone work too, and `timer` or `alarmes` alone open the clock app's page), and ends with a web search. With *Contacts in search* on (contacts permission), names appear from two letters: the chat glyph opens their WhatsApp chat directly; tapping the name offers WhatsApp, call, message and the contact card. A single app opens on its own only from two letters that begin its name or a word in it ("ai" for aiqfome, never "q"), never while a contact matches, and a matching setting stops it only from three letters (so "in" still opens Instagram). |
| **Weather** (setting) | Temperature and sky under the clock, on the home screen, the bedside clock or both, from Open-Meteo (free, no account) for the approximate location, in °C unless Android's regional preferences say °F or the SIM is from the US; refreshed at most every half hour, last place remembered when location is off. |
| **Search only** (setting) | The app list stays empty until you type: apps appear only as their name is searched, so there is nothing to scroll through idly. The keyboard always opens with it. When a name escapes you, *Show all apps* under the search lists everything until the list closes. |
| **Mindful pause** (setting) | Chosen apps open only after a five-second "Breathe in / Breathe out", with today's time in the app when usage access is on. |
| **Bottom icons** | *Settings → Bottom bar*: none to six icons, each changed, moved or removed there, or by long-pressing it; long-press any app in the list to put it there (in place of one you pick when all six are taken). Any app, or the camera. Some forty well-known apps have a hand-drawn line glyph (phone, messages, WhatsApp, Instagram, Facebook, Telegram, YouTube, Chrome, Maps, banks, shopping, food…), as do the phone's own dialer, messages, camera, browser and the like; others show their initial in a thin circle. |
| **Extras** (off by default, in Settings) | *Screen time*: today's time in apps, on its own line under the clock, on the home screen, the bedside clock or both (usage access; in *Settings → Clock*). *Now playing*: the song, artist and ⏮ ⏯ ⏭ just above the bottom icons while a player holds a track, paused or playing; its space stays reserved so nothing moves (notification access). Tap screen time to open Digital Wellbeing. *Double-tap to lock*: double-tap empty space to turn the screen off (accessibility service). |
| **Language** | English or Brazilian Portuguese, following the phone; on Android 13+ *Settings → Apps → Minimalist → Language* picks one for Minimalist alone. |
| **Accessibility** | With TalkBack on, *All apps* and *Settings* appear as buttons on the home screen, since TalkBack can't make the swipes. On a short screen or with large text the pinned names shrink a little so the bottom icons always fit. |
| **Settings** | Long-press empty space on the home screen. Six groups, each its own page (back returns to the list): *Home screen*, *Clock*, *Search*, *Standby*, *Apps* and *Gestures*, plus the privacy policy. Before sending you to Android's accessibility page, it explains what the service is used for and asks you to agree. |

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

CI runs the same, on Java 21: the unit tests (calculator, search, screen time, weather
retries), the screen tests and Android lint must pass before an APK is published. The
screen tests use [Robolectric](https://robolectric.org) to run the app's real views on
Android 15 without a phone: the search opening apps on its own (and not), the mindful
pause, menus closing, settings pages and the Portuguese texts, standby kept lit only over
the lock screen, TalkBack's buttons and the pinned names shrinking to fit. A version release is made by
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

## Privacy

Nothing is collected and nothing is sent to the developer. The one thing that leaves the
phone is the approximate place (about 1 km) sent to Open-Meteo, and only with *Weather*
on. The full list of what the app reads is in [PRIVACY.md](PRIVACY.md) (Portuguese and
English), also linked from the app's settings.

## License

[Apache License 2.0](LICENSE). Provided "as is", without warranty of any kind. The clock's font, Roboto Flex, is under the [SIL Open Font License 1.1](app/src/main/assets/fonts/OFL.txt).
