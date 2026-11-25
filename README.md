# Dusk

A calm home screen for Amazon Fire TV. The background is a live sky that follows your real day:
the sun rises on the left and sets on the right at your city's actual times, the sky turns golden
at sunset, and at night the real moon (in its true phase) and stars come out. Clouds, mist and
rain follow the weather outside.

Built from scratch with no third-party code or libraries, and no Gradle: just the JDK and
Google's Android SDK tools.

> Screenshots and a short video are coming with the blog post.

## Features

- **Live sky**: sun and moon on one arc, real sunrise and sunset, a red sun at golden hour, the moon
  in its real phase (crescent, half, full), shooting stars, birds, rain and lightning, all from your
  city's weather.
- **App dock**: your pinned apps on a frosted glass bar with a macOS-dock style magnification.
- **Continue watching**: cards for what you were watching in JioHotstar, Stremio and YouTube, with
  progress bars, season and episode; a click opens that episode or video directly where the app
  gives a link (JioHotstar, YouTube).
- **Control panel** (the sliders button, or the remote's ☰ button): brightness, now playing,
  Wi-Fi, Bluetooth, apps, ADB on/off, free memory, sleep timer, screensaver, a sky preview that
  plays a whole day and night in 30 seconds, and the greeting name.
- **Home button**: pressing Home on the remote opens Dusk instead of Amazon's home screen.

## Install

You need a Mac or Linux computer with [adb](https://developer.android.com/tools/adb), JDK 17 and the
Android SDK (build-tools 35.0.1, platform android-30). On macOS:

```bash
brew install openjdk@17 android-commandlinetools
sdkmanager "build-tools;35.0.1" "platforms;android-30"
```

On the TV: Settings → My Fire TV → Developer options → turn on **ADB debugging**, and note the
TV's IP address (Settings → My Fire TV → About → Network). Then:

```bash
./build.sh                          # runs the tests, then builds build/tvhome.apk
export TV=192.168.1.20:5555         # your TV's address
./tv.sh install                     # installs Dusk and grants what it needs (see below)
```

The first build creates a signing key in `signing/`. Keep it: updates must be signed with the same
key. On some Fire TVs ADB debugging switches itself off after a restart; turn it back on before
running `tv.sh`.

## Settings

| Command | What it does |
|---|---|
| `./tv.sh name Sam` | The greeting says "Hey Sam!". You can also type it in the control panel. |
| `./tv.sh name auto` | Use the owner's name from the TV's own name ("Sam's Fire TV"). This is the default. |
| `./tv.sh city 51.51 -0.13` | Your city's latitude and longitude, for weather and sun times. |
| `./tv.sh city auto` | Find the city from the postcode Fire OS keeps for the TV. This is the default. |
| `./tv.sh clear-continue` | Empty Continue watching (shows you watch afterwards appear again). |
| `./tv.sh off` / `./tv.sh on` | Give the Home button back to Amazon's home, or take it again. |
| `./tv.sh uninstall` | Remove Dusk and everything it set up. |

## What Dusk can see and what leaves the TV

Fire OS doesn't let apps replace the home screen, so Dusk needs a few permissions an ordinary app
wouldn't. `tv.sh install` grants them over adb, and `tv.sh uninstall` removes them all.

| Permission | Why |
|---|---|
| Read system log (`READ_LOGS`) | To see the moment Home is pressed, so Dusk opens before Amazon's home shows. It reads only the system's "app started" lines (one log tag) and reacts only to the Home one. |
| Accessibility service | Shows a short cover while Dusk opens, and powers the sleep timer. It only listens for window changes; it can't read screen content or what you type. |
| Notification access | The only way Android lets an app read "now playing" (title and progress) for YouTube's Continue watching card. Notifications themselves are never read. |
| TV listings (`READ_TV_LISTINGS`) | Reads the TV's shared "Watch Next" list, where apps like JioHotstar record what you're watching. |
| Modify system settings (`WRITE_SETTINGS`) | The brightness slider. |
| Secure settings (`WRITE_SECURE_SETTINGS`) | The ADB on/off tile. |
| Location (`ACCESS_FINE_LOCATION`) | Android only shares the Wi-Fi network's name with this. It's used for nothing else; Dusk never asks for your position. |
| Close background apps | The Free memory tile. |

Everything Dusk learns stays on the TV. It only connects to:

- **Open-Meteo**, every 10 minutes, with your city's coordinates, for weather and sun times;
- **OpenStreetMap**, once, with the TV's postcode and country, if you didn't set a city;
- **YouTube**, once per video, with its title and channel, to find its picture and link;
- the picture links the streaming apps provide for their own Continue watching cards.

## Limits

- **Netflix** shows no Continue watching card: it shares its list only with Amazon's own home screen.
- **FanCode** (and any app that doesn't share what you're watching) never gets a card: it writes no
  Watch Next entry and reports no "now playing", even while a match plays.
- The **Picture**, **Sound** and **AirPlay** settings pages are closed to other apps, so the control
  panel can't open them, and the **backlight** can't be changed by an app.
- Tested on a Fire TV Edition TV (Fire OS 8, Android 11). Other Fire TV devices should work but
  are untested.

## Development

- `src/local/tvhome/`: the app. Classes ending in `Rules` (and a few others listed in `build.sh`) are
  plain Java with no Android types; `test/` runs over 480 checks on them before every build.
- `tools/DrawIcons.java` draws the app icon and TV banner (`java tools/DrawIcons.java res`).
- `./tv.sh logs` shows Dusk's log; `./tv.sh shot` saves a screenshot from the TV.

## License

[MIT](LICENSE)
