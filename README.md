# Tsugi (次)

A lightweight Singapore bus arrivals app for Android, built natively with Kotlin, Jetpack Compose and Material 3 Expressive. Data comes from [LTA DataMall](https://datamall.lta.gov.sg/).

*Tsugi* means "next", as in the next bus.

## Features

- **Favourites:** a "Next up" card for your soonest saved bus, whole-stop cards, single-bus rows and places.
- **Places:** group stops, such as an interchange plus the stops at the MRT exits, into one departures board.
- **Nearby:** stops within 200, 400 or 800 m, using the platform location service (no Play Services).
- **Stop detail:** the next three buses for each service, with crowding, deck type, wheelchair access and a marker on timetable-based times.
- **Search:** by stop name, road or 5-digit code.
- **MRT/LRT status:** disruption alerts from `TrainServiceAlerts`. LTA doesn't publish live train arrivals.
- Dynamic (wallpaper) colour, M3E motion and shapes, pull-to-refresh and haptics.

Arrivals refresh every 20 s (LTA's update rate), and only while a screen is visible. The ~5,000 bus stops are cached on the device and refreshed weekly.

## Setup

Requirements: JDK 17+ and the Android SDK with platform 37.1.

1. Get a DataMall AccountKey from LTA.
2. Provide it in any one of these places (checked in this order):
   - an environment variable: `export LTA_ACCOUNT_KEY=your-key-here`
   - `.env` at the repo root (git-ignored); copy `.env.example`:
     ```sh
     cp .env.example .env   # then fill in LTA_ACCOUNT_KEY
     ```
   - `local.properties` (git-ignored, also holds `sdk.dir`)
3. Build and install on a connected phone:
   ```sh
   ./gradlew installRelease
   ```

The key is compiled into the APK through `BuildConfig`. That's fine for personal use, but don't distribute the APK.

## Project layout

```
app/src/main/java/dev/cantabile/tsugi/
  data/   LTA API client, models, stop cache, favourites (DataStore), location
  ui/     Compose screens and shared components
```

Tests: `./gradlew testDebugUnitTest`.

## Stack

AGP 9.4 with built-in Kotlin, Kotlin 2.4, Compose BOM 2026.09, Material 3 `1.5.0-alpha29` (for the Expressive APIs), OkHttp, kotlinx.serialization and DataStore. minSdk 36 (Android 16).
