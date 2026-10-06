<div align="center">

# Tsugi (次, "next")

**A lightweight Singapore bus arrivals app for Android.**

*Native Kotlin and Material 3 Expressive, built on LTA DataMall for a phone you sideload it to.*

[![Version](https://img.shields.io/badge/version-0.2.0-orange)](CHANGELOG.md)
[![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)](app/build.gradle.kts)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white)](gradle/libs.versions.toml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

[Features](#features) · [Architecture](#architecture) · [Quick start](#quick-start) · [Security](#security) · [Development](#development)

</div>

> **Tsugi shows the next bus, fast, and gets out of the way.**
>
> It reads LTA's live data directly. Journey planning, turn-by-turn navigation and its own arrival predictions are left to other apps.

> [!NOTE]
> **Pre-1.0 (`0.2.0`).** Built for personal sideloading, not the Play Store. It uses Material 3 Expressive alpha APIs (`1.5.0-alpha29`), so the UI layer may change with library updates. See the [changelog](CHANGELOG.md).

## At a glance

| | |
| --- | --- |
| **Screens** | Favourites · Nearby · Search · Stop · Route · Place · Settings |
| **Data** | LTA DataMall (bus arrivals, bus stops, bus routes, train alerts) · OneMap (address search, static maps) |
| **Platforms** | Android 12+ (minSdk 31, target 37) · Live Updates on Android 16 QPR2+ |
| **Extras** | Home-screen widget · Quick Settings tile · launcher shortcuts |
| **Size** | About 4.5 MB release APK (R8 shrinking) |
| **Stack** | Kotlin 2.4 · Jetpack Compose · Material 3 Expressive · Glance · OkHttp · DataStore |

## Features

### Arrivals

- **Live times:** the next three buses for each service, refreshed every 20 s (LTA's update rate) only while the app is visible.
- **Every service:** services that aren't running right now still appear, greyed out, with today's first-bus time.
- **Bus details:** crowding as green, amber or red bars (1–3), deck type, wheelchair access, and a dashed outline on timetable-based times.
- **Sorting:** order a stop's buses by Soonest, Number or Starred.
- **MRT and LRT status:** line disruptions with affected stations and free bus or shuttle info. LTA doesn't publish live train times.

### Saving

- **Next up:** the nearest saved stop and a bus you can still walk to in time, or the first bus of the morning overnight.
- **Favourites:** whole-stop cards, pinned-bus groups and places, all collapsible and reorderable by drag.
- **Places:** group stops, such as an interchange and the stops at the MRT exits, into one board sorted by soonest bus.
- **Save sheet:** save a whole stop, pin individual buses, or add the stop to places.

### Finding stops

- **Nearby:** stops within 200, 400 or 800 m, using the platform location service rather than Play Services.
- **Search:** stop names, roads, codes and bus numbers, plus buildings, addresses and postal codes through OneMap.
- **Stop map:** where the stop is and which side of the road you're on, with walking directions and a link to the stop across the road.
- **Routes:** each bus service's stops in order, by direction.

### Alerts and quick access

- **Bus alerts:** a live countdown notification for a bus you choose, with heads-up alerts before it arrives. See [bus alerts](docs/bus-alerts.md).
- **Widget, tile and shortcuts:** next buses on the home screen, a "Next bus" Quick Settings tile, and Nearby or Search from the launcher icon.

### Design

- **Wallpaper colour:** dynamic colour throughout in light and dark, with fixed MRT line and crowding colours.
- **Expressive motion:**
  - The countdown morphs as the bus arrives, and times roll as they change.
  - Lists animate as they reorder.
  - Screens shrink with the predictive back gesture.
  - The toolbar's highlight slides between tabs.
  - Decorative motion stops when system animations are off.

## Architecture

```mermaid
flowchart LR
    UI[Compose screens] --> VM[AppViewModel]
    VM --> C[AppContainer]
    W[Glance widget] --> C
    Q[Quick Settings tile] --> C
    T[BusTrackingService] --> C
    C --> S[StopRepository<br/>weekly stop cache]
    C --> R[RouteRepository<br/>weekly route cache]
    C --> F[FavouritesRepository<br/>and settings, DataStore]
    C --> L[LocationProvider]
    C --> LTA[LtaApi] --> DM[(LTA DataMall)]
    C --> OM[OneMapApi] --> OMS[(OneMap)]
    S --> LTA
    R --> LTA
    T --> N[Notifications and Live Update]
```

A single module with hand-rolled dependency injection: `AppContainer` holds a few singletons, and one view model serves every screen. See [architecture](docs/architecture.md) for the refresh policy and navigation, and [data sources](docs/data-sources.md) for the endpoints used.

## Quick start

**Requirements:** JDK 17+, the Android SDK with platform 37.1, an LTA DataMall AccountKey, and a phone on Android 12+ with USB debugging on.

```sh
# 1. Clone the repository
git clone https://github.com/hoshinoht/tsugi.git && cd tsugi

# 2. Add your LTA key (or export LTA_ACCOUNT_KEY instead)
cp .env.example .env    # then set LTA_ACCOUNT_KEY=...

# 3. Point Gradle at the Android SDK (or set ANDROID_HOME)
echo "sdk.dir=<path-to-android-sdk>" >> local.properties

# 4. Build and install on the connected phone
./gradlew installRelease
```

On certified phones in Singapore, install over `adb` as above. Since 30 September 2026, apps from unverified developers can be blocked when installed from an APK file.

**Next steps:**

| Step | Guide |
| --- | --- |
| Understand what's fetched and when | [Architecture](docs/architecture.md) |
| See which LTA and OneMap endpoints are used | [Data sources](docs/data-sources.md) |
| Set up bus alerts and Live Updates | [Bus alerts](docs/bus-alerts.md) |
| See what changed | [Changelog](CHANGELOG.md) |

## Security

> [!IMPORTANT]
> The LTA key is compiled into the APK through `BuildConfig`. That's fine for your own phone, but anyone with the APK can extract the key, so don't publish it.

- **Key sources:** an environment variable, `.env` or `local.properties`, checked in that order. Both files are git-ignored.
- **CI:** builds without a key, so CI artifacts never contain one.
- **Privacy:** no accounts or analytics. Your location stays on the device. LTA receives only stop codes. OneMap receives search text, plus map coordinates when a stop map is shown, including your position if you're within 300 m of the stop.

## Development

```sh
./gradlew testDebugUnitTest               # unit tests
./gradlew lintDebug                       # Android lint
./gradlew assembleDebug assembleRelease   # debug and R8-shrunk release builds
./gradlew installRelease                  # install on a connected device
```

CI ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) runs the tests, lint and both builds on pushes to `master` and `dev` and on pull requests.

<details>
<summary>Repository layout</summary>

| Path | Contents |
| --- | --- |
| `app/src/main/java/dev/cantabile/tsugi/data/` | LTA and OneMap clients, models, stop and route caches, favourites, settings, Next up, location |
| `app/src/main/java/dev/cantabile/tsugi/ui/` | Compose screens, shared components, theme |
| `app/src/main/java/dev/cantabile/tsugi/tracking/` | Bus alert service, notifications, Quick Settings tile |
| `app/src/main/java/dev/cantabile/tsugi/widget/` | Glance home-screen widget |
| `app/src/test/` | Unit tests for parsing, timetables and Next up |
| `docs/` | Architecture, data sources and bus alerts |
| `gradle/libs.versions.toml` | Dependency versions |

</details>

## Non-goals

Tsugi is not a journey planner, a navigation app, or an arrival predictor; it shows LTA's own times as they are. It doesn't target iOS or the Play Store, and it has no accounts or sync.

## License

[MIT](LICENSE). LTA DataMall and OneMap data are subject to their own terms of use.
