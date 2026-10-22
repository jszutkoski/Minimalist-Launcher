# Minimalist

A quiet, text-only Android home screen. Black background, a clock, the few apps
you actually use, and everything else one swipe away.

No app icons, no widgets, no ads, no tracking. The only thing that ever leaves the phone is the weather request, when that option is on: an approximate position (rounded to about a kilometre) sent to Open-Meteo. Contacts, notifications and usage never do.

## What it does

| | |
|---|---|
| **Home** | Clock, a battery line as wide as the time with the percentage set into it right where the fill ends, the line parting around the number, then the date (and the next alarm). Up to 8 pinned apps. Everything is centred by default, or left aligned. A row of line icons along the bottom: WhatsApp, Waze, Google Photos, Spotify and the camera to start with, up to six of your choice (an app that is uninstalled leaves its place empty). Tap the clock for alarms, the date for your calendar, the battery for battery usage. While charging the line and its number breathe; full on the charger it all turns green and pulses slowly; at 15% or below it turns red. |
| **App list** | Swipe up. The keyboard opens and search starts as you type; when only one app matches, it opens on its own. Press *Go* to open the top match (with nothing typed, *Go* just closes the keyboard). Accents are ignored, so `camera` finds *Câmera*. Apps in a work profile or Android 15's private space are marked as such; a locked private space shows nothing. |
| **Long-press an app** | The app's own shortcuts first (Waze "Drive home", WhatsApp chats…), then add to home or to the bottom bar, remove, reorder, rename, pause before opening (for a paused app: open without the pause just this once, or remove the pause), hide, app info, uninstall. A shortcut into a paused app goes through the pause too. Pressing home closes the menu. |
| **Gestures** | Swipe up for the app list, down for notifications. Drag the list down (or press back) to close it. Swiping left or right can open an app of your choice (*Settings → Gestures*, off by default; start away from the screen's edge, which Android keeps for back). |
| **Standby** | A bedside clock in one of two set screens, picked from their pictures in *Settings → Standby*: *Digital*, large thin digits a little above the middle, the battery line under them (as wide as the time, without the number, breathing while charging) halfway down to the weather, then the weather with a glyph for the sky (sun or moon, clouds, fog, drizzle, rain, snow, storm), then the notification icons, one even gap between them; or *Analog*, the face with its battery arc (breathing while charging, green when full), then the weather, with its glyph too, and the icons. The clock keeps its place whatever shows under it. The weather is there when *Weather* is on in Standby settings. The notification icons are a line glyph for each app with notifications waiting: calls, messages, WhatsApp, Instagram, email and many more, one glyph per kind, with apps that have no glyph of their own sharing three dots; *Notification icons* in Standby settings chooses which apps may show one; over the lock screen only if *Notification icons on the lock screen* is on. At the system brightness or one of its own (*Brightness* in Standby settings, from 10% to 100% or by the time of day: bright by day, softer at dawn and in the evening, faint from 11 pm to 6 am; for the bedside clock only), that drifts a little every minute. Opens with a two-finger pinch on the home screen; on a phone with a screen lock it needs Minimalist's accessibility service, which is what locks the phone when someone leaves an unlocked bedside clock, and without it standby stays closed; tap to leave (the touch that wakes the screen, such as the end of a double-tap to wake, doesn't count). Leaving always asks for the fingerprint or PIN: over the lock screen through Android's own unlock, and on a phone that is still unlocked through the fingerprint prompt once the screen has been on for 10 seconds (sooner, a tap goes straight in, like a screen that timed out a moment ago); pressing home or recents instead locks the phone (with Minimalist's accessibility service on), also with the fingerprint prompt open. Over the lock screen, tapping asks for your PIN or fingerprint, and unlocking any other way closes it; swiping home over it goes to Android's lock screen, as Android does with anything shown over it. Over the lock screen off the charger, it goes dark after 10 untouched seconds, and 10 seconds after the tap when Android's unlock is left unanswered (with Minimalist's accessibility service on). Pulling down the notifications over it locks a still-unlocked phone. Over the lock screen the shade is Android's own, exactly as without Minimalist: what it shows is set in Android (*Notifications on the lock screen* in Standby settings goes there). With *Standby on every wake* on (it needs Minimalist's accessibility service, the same one as double-tap to lock), it is put on top each time the screen turns off (except during calls), so it is the first thing you see on every wake; after unlocking you are back in the app you were using. |
| **Clock on the charger** | Each time the phone locks on the charger (charging, or holding at a charge limit), by the power button, the screen timeout or double-tap to lock, or when it is plugged in already locked, the screen comes back on about 3 seconds later with the bedside clock in the screen chosen for standby, the weather, the next alarm under it with a bell (its weekday too when more than a day away) and, when any notification is waiting, three dots that never say from which app. As bright as the light around it calls for, from the phone's light sensor: near the screen's lowest in the dark, rising with the light on a log scale to 80% in daylight, as in a car by day (with no light sensor, the phone's own brightness); lit for as long as it is plugged in, and somewhere new on the screen every minute, fading out and in, so nothing marks it. Shown **only over the lock screen** (or on a phone with no screen lock): if Android would wait a few seconds after the screen turns off before locking, it is locked at once. Tap to unlock with Android's own unlock (left unanswered for 10 seconds, it goes with the screen, which turns off and comes back on with the clock about 3 seconds later); unlocking with the fingerprint or face, or unplugging, closes it; the power button turns the screen off until the next wake, which shows it again. Never during a call. *Clock on the charger* in Standby settings; needs Minimalist's accessibility service, which is what hears the screen turn off. |
| **Clock** (setting) | The home screen's clock, in *Settings → Clock* (the bedside clock has its own two screens), in one of two layouts. *Panel*: the date, the digits (in the weight and size chosen), and one quiet grey row of glyphs and values: the weather, today's screen time and the battery, parted by hairlines (weather and screen time when they are on; tap screen time for Digital Wellbeing, the battery for battery usage). *Classic*: one block in two designs: *Digital*, the time with the battery line under it as wide as the time; or *Analog*, a dark dial with the 12, 3, 6 and 9, a mark for every minute, three hands, and the battery as a segmented arc along the right-hand edge, from the 1 to the 5, filling up from the 5 so a full battery reaches the 1 (it breathes while charging, turns green when full and red when low, as the line does). Five thicknesses from ultra thin to bold and three sizes, for the clock and its battery together. The date above the clock, below it, or hidden; the weather and today's screen time each on a line under it, if wanted; one even gap between them all, measured from the glyphs. A live preview, centred, at the top of *Settings → Clock*, in a space that keeps its height so the options never move. The digits use a cut-down [Roboto Flex](https://github.com/googlefonts/roboto-flex) (SIL Open Font License, in `app/src/main/assets/fonts`) so every thickness looks the same on any phone. A clock too wide for the screen shrinks until it fits. |
| **Next alarm** | Beside the date and battery: "6:30" within a day, "Sat 6:30" further out. Tap for alarms. |
| **Notification dots** (setting) | A small dot beside pinned apps and bottom icons with notifications waiting (notification access). |
| **Smart search** | The app search also answers sums (`12*7`, `2,5 + 1`, `1.500 + 300`, `200 x 15%`, `200 + 10%` = 220; thousands and decimals the way of the country the phone is in, from its network or SIM, so a phone in English in Brazil reads `1.500` as fifteen hundred; phone numbers and dates are left alone; tap to copy), finds Android settings by English or Portuguese words (`wifi`, `brilho`, `hotspot`, `bateria`…), switches the flashlight (`lanterna`), starts timers and sets alarms in the clock app (`timer 10 min`, `timer de 1h30`, `alarme 7:30`, `alarm 7am`, `alarme amanhã às 6h`; `10 min` or `7:30` alone work too, and `timer` or `alarmes` alone open the clock app's page), searches inside an app when its name comes first (`yt receita de bolo` for YouTube, `maps padaria`, `waze`, `sp` for Spotify, `play`, `nf` for Netflix, `ml` for Mercado Livre, `wiki`, `traduzir`; in the app when installed, otherwise in the browser), and ends with a web search. With *Contacts in search* on (contacts permission), names appear from two letters: the chat glyph opens their WhatsApp chat directly; tapping the name offers WhatsApp, call, message and the contact card. A single app opens on its own only from two letters that begin its name or a word in it ("ai" for aiqfome, never "q"), never while a contact matches, and a matching setting stops it only from three letters (so "in" still opens Instagram). |
| **Weather** (setting) | Temperature and sky under the clock, on the home screen, the bedside clock or both, from Open-Meteo (free, no account) for the approximate location, in °C unless Android's regional preferences say °F or the phone is in a °F country (by its network or SIM); refreshed at most every half hour (every 10 minutes while requests fail), the request naming itself only "Minimalist", last place remembered when location is off. |
| **Search only** (setting) | The app list stays empty until you type: apps appear only as their name is searched, so there is nothing to scroll through idly. The keyboard always opens with it. When a name escapes you, *Show all apps* under the search lists everything until the list closes. |
| **Mindful pause** (setting) | Chosen apps open only after a five-second "Breathe in / Breathe out", with today's time in the app and how many times it was opened when usage access is on (moving between its screens, or back within 3 seconds, is the same visit). Every way in pauses: home, the list, the bottom icons, shortcuts, swipes, and searches inside the app (`yt …`, a WhatsApp chat, the web search for a paused browser). The five seconds are counted on the clock, so they hold with Android's animations turned off. |
| **Daily limits** (setting) | A time a day for an app (15, 30 or 45 min, 1 h or 2 h), from its long-press menu (*Daily limit*) or *Settings → Apps → Daily limits*. Once the app is past it, opening it brings the mindful pause, whether or not the app pauses otherwise, lasting 15 seconds instead of 5: it says the limit is past, and *Not now* comes first, larger, with the way in quieter under it. Opening is still there after the pause. Today's time is read from the usage events as the app is tapped, so it needs usage access. |
| **Bottom icons** | *Settings → Bottom bar*: none to six icons, each changed, moved or removed there, or by long-pressing it; long-press any app in the list to put it there (in place of one you pick when all six are taken). Any app, or the camera. Some forty well-known apps have a hand-drawn line glyph (phone, messages, WhatsApp, Instagram, Facebook, Telegram, YouTube, Chrome, Maps, banks, shopping, food…), as do the phone's own dialer, messages, camera, browser and the like; others show their initial in a thin circle. |
| **Extras** (off by default, in Settings) | *Screen time*: today's time in apps, on its own line under the clock, on the home screen, the bedside clock or both (usage access; in *Settings → Clock*). *Now playing*: the song, artist and ⏮ ⏯ ⏭ just above the bottom icons while a player is playing, or paused within the last half hour (a player that keeps or brings back a paused track on its own, with nothing played, stays hidden); its space stays reserved so nothing moves (notification access). Tap screen time to open Digital Wellbeing. *Double-tap to lock*: double-tap empty space to turn the screen off (accessibility service). |
| **Language** | English or Brazilian Portuguese, as the phone speaks, or chosen for Minimalist alone in *Settings → Language* (Android 13 and later). |
| **Accessibility** | With TalkBack on, *All apps* and *Settings* appear as buttons on the home screen, since TalkBack can't make the swipes. On a short screen or with large text the pinned names shrink a little so the bottom icons always fit. |
| **Settings** | Long-press empty space on the home screen. Seven groups, each its own page (back returns to the list): *Home screen*, *Bottom bar*, *Clock*, *Search*, *Standby*, *Apps* and *Gestures*, plus the privacy policy. Before sending you to Android's accessibility page, it explains what the service is used for and asks you to agree. |

## Install on your phone

1. Open the [latest build](../../releases/tag/latest) on your phone and download
   `minimalist-launcher.apk`.
2. Open it and allow your browser or Files app to install unknown apps when asked.
3. Open **Minimalist** and tap **Set as home app** at the bottom of the screen
   (or go to *Settings → Apps → Default apps → Home app*).

Every push to `main` builds a new APK, numbered by its CI run (*App info* shows
it, as in "1.2 (152)": the last version, then the run). Installing it over the previous one keeps your favourites and settings. Each
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

Requires JDK 21 (the screen tests run Android 15's framework, which needs it; the app itself is built for Java 17) and the Android SDK (Android Studio installs both).

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

CI runs the tests and lint first without them, then hands the key and its password only
to the two steps that sign: decoding the key to a temporary file, deleted afterwards, and
building the APK. Keep a private backup of the keystore: losing it means uninstalling and
reinstalling once.

Without them (a local build, any pull request) the release APK comes out unsigned, and CI
refuses to publish it. Version releases are made from `main` only, with a plain number
(`1.3`) or a `v1.3` tag. Debug builds use Android's own debug key and
install as a separate app (`.debug`). For Google Play, use a separate upload key, also
kept out of the repository.

## Privacy

Nothing is collected and nothing is sent to the developer. The one thing that leaves the
phone is the approximate place (about 1 km) sent to Open-Meteo, and only with *Weather*
on. The full list of what the app reads is in [PRIVACY.md](PRIVACY.md) (Portuguese and
English), also linked from the app's settings.

## License

[Apache License 2.0](LICENSE). Provided "as is", without warranty of any kind. The clock's font, Roboto Flex, is under the [SIL Open Font License 1.1](app/src/main/assets/fonts/OFL.txt).
