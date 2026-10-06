<div align="center">

# Tsugi (次, "next")

**A lightweight Singapore bus arrivals app for Android.**

*Native Kotlin and Material 3 Expressive, built on LTA DataMall for a phone you sideload it to.*

[![Version](https://img.shields.io/badge/version-0.3.0-orange)](CHANGELOG.md)
[![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)](app/build.gradle.kts)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white)](gradle/libs.versions.toml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

[Features](#features) · [Architecture](#architecture) · [Quick start](#quick-start) · [Security](#security) · [Development](#development)

</div>

> **Tsugi shows the next bus, fast, and gets out of the way.**
>
> It reads LTA's live data directly. Journey planning, turn-by-turn navigation and its own arrival predictions are left to other apps.

> [!NOTE]
> **Pre-1.0 (`0.3.0`).** Built for personal sideloading, not the Play Store. It uses Material 3 Expressive alpha APIs (`1.5.0-alpha29`), so the UI layer may change with library updates. See the [changelog](CHANGELOG.md).

## At a glance

| | |
| --- | --- |
| **Screens** | Favourites · Nearby · Search · Stop · Station · Route · Place · Settings · two panes on wide screens |
| **Data** | LTA DataMall (bus arrivals, stops, routes and services; train alerts, station crowding, lift maintenance) · OneMap (address search, static maps) · bundled MRT/LRT stations |
| **Platforms** | Android 12+ (minSdk 31, target 37) · Live Updates on Android 16 QPR2+ |
| **Extras** | Configurable home-screen widget · Quick Settings tile · launcher shortcuts for saved stops · disruption notifications |
| **Size** | About 4.5 MB release APK (R8 shrinking) |
| **Stack** | Kotlin 2.4 · Jetpack Compose · Material 3 Expressive · Glance · OkHttp · DataStore |

## Features

### Arrivals

- **Live times:** the next three buses for each service, refreshed every 20 s (LTA's update rate) only while the app is visible.
- **Every service:** services that aren't running right now still appear, greyed out, with today's first-bus time. Public holidays use the Sunday timetable.
- **Last bus:** a "Last bus 11:42 pm" chip in the 45 minutes before tonight's last bus.
- **Bus details:** crowding as green, amber or red bars (1–3), deck type, wheelchair access, how many stops away the next bus is, how often the service runs now, and a dashed outline on timetable-based times.
- **Sorting:** order a stop's buses by Soonest, Number or Starred.

### Trains

- **Stations:** each MRT and LRT station's lines, platform crowding now and forecast by the half-hour, lifts under maintenance, and the bus stops at its exits with live arrivals. Save those stops as a place in one tap.
- **Line status:** disruptions with affected stations, free bus or shuttle info, and a link to the bus stops at each affected station. LTA doesn't publish live train times.
- **Disruption alerts:** optional notifications when a line near your saved stops is disrupted, and when it's back to normal.
- **Stations at stops:** bus stops next to a station show it with its line colours.

### Saving

- **Next up:** the nearest saved stop and a bus you can still walk to in time, or the first bus of the morning overnight.
- **Favourites:** whole-stop cards, pinned-bus groups and places, all collapsible and reorderable by drag.
- **Places:** group stops, such as an interchange and the stops at the MRT exits, into one board sorted by soonest bus.
- **Save sheet:** save a whole stop, pin individual buses, or add the stop to places.

### Finding stops

- **Nearby:** stops and stations within 200, 400 or 800 m, using the platform location service rather than Play Services, with an optional map of the closest stops.
- **Search:** stop names (spelled out or abbreviated, in any order, with small typos forgiven), roads, codes, bus numbers and stations, plus buildings, addresses and postal codes through OneMap.
- **Stop map:** where the stop is and which side of the road you're on, with walking directions and a link to the stop across the road.
- **Routes:** each bus service's stops in order, by direction, with first and last buses, category and frequency. Tap a bus number on a stop to open its route with the next buses marked on it.

### Alerts and quick access

- **Bus alerts:** a live countdown notification for a bus you choose, with a heads-up when it's time to leave (counting your walk to the stop) and when it arrives. See [bus alerts](docs/bus-alerts.md).
- **Widget:** next buses for all favourites, or for one saved stop or place; as many as fit.
- **Tile and shortcuts:** a "Next bus" Quick Settings tile; Nearby, Search and your first saved stops from the launcher icon; any stop pinned to the home screen.

### Design

- **Wallpaper colour:** dynamic colour throughout in light and dark, with fixed MRT line and crowding colours.
- **Wide screens:** on tablets and unfolded foldables the tabs stay on the left and what you open shows on the right.
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
    C --> SI[ServiceInfoRepository<br/>weekly service cache]
    C --> ST[StationRepository<br/>bundled stations]
    D[DisruptionWorker] --> C
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
- **Privacy:** no accounts or analytics. Your location stays on the device. LTA receives only stop codes and line codes. OneMap receives search text, plus map coordinates when a stop map is shown (including your position if you're within 300 m of the stop) and, only if you tap "Show map" on Nearby, your position rounded to about 10 m.

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
| `app/src/main/java/dev/cantabile/tsugi/data/` | LTA and OneMap clients, models, stop, route and service caches, stations, timetables, search, favourites, settings, Next up, location |
| `app/src/main/java/dev/cantabile/tsugi/ui/` | Compose screens, shared components, theme |
| `app/src/main/java/dev/cantabile/tsugi/tracking/` | Bus alert service, notifications, disruption worker, Quick Settings tile |
| `app/src/main/java/dev/cantabile/tsugi/widget/` | Glance home-screen widget and its setup screen, launcher shortcuts |
| `app/src/main/assets/stations.json` | MRT and LRT stations and exits, built by `scripts/build_stations.py` |
| `app/src/test/` | Unit tests for parsing, timetables, stations, search, alerts and Next up |
| `docs/` | Architecture, data sources and bus alerts |
| `gradle/libs.versions.toml` | Dependency versions |

</details>

## Non-goals

Tsugi is not a journey planner, a navigation app, or an arrival predictor; it shows LTA's own times as they are. It doesn't target iOS or the Play Store, and it has no accounts or sync.

## License

[MIT](LICENSE). LTA DataMall and OneMap data are subject to their own terms of use. Station locations come from [SG Rail Data](https://github.com/cheeaun/sgraildata), compiled from LTA's geospatial datasets.
