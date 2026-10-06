# Architecture

Tsugi is a single-module app with no navigation, dependency-injection or image libraries. A handful of singletons live in `AppContainer` (in `TsugiApplication.kt`), and one `AppViewModel` serves all screens.

| Component | File | Role |
| --- | --- | --- |
| `LtaApi` | `data/LtaApi.kt` | OkHttp + kotlinx.serialization client for DataMall; sends the `AccountKey` header |
| `OneMapApi` | `data/OneMapApi.kt` | OneMap address search (no token) |
| `StopRepository` | `data/StopRepository.kt` | All bus stops, cached as `bus_stops.json` in app storage; nearby and text search |
| `FavouritesRepository` | `data/FavouritesRepository.kt` | Saved stops, buses and places as one JSON list in DataStore |
| `LocationProvider` | `data/LocationProvider.kt` | Platform `LocationManager` (fused provider where available) |
| `AppViewModel` | `ui/AppViewModel.kt` | Arrivals cache, nearby state, train status, place editing |
| `BusTrackingService` | `tracking/BusTrackingService.kt` | Foreground service for bus alerts; see [bus alerts](bus-alerts.md) |
| `FavouritesWidget` | `widget/FavouritesWidget.kt` | Glance home-screen widget |

## Refresh policy

| Data | When it's fetched |
| --- | --- |
| Bus arrivals | Every 20 s for the stops on screen, only while the app is visible. Stops fetched in the last 10 s are skipped unless you pull to refresh. |
| Train alerts | Every 2 min while Favourites is visible, and on pull-to-refresh. |
| Bus stops | Once, then when the cached file is older than 7 days. A stale cache is kept if the download fails. |
| Widget | When favourites change, every 30 min (Android's minimum), and on its refresh button. |

## Navigation

`TsugiRoot` holds the current tab (Saved, Nearby, Search) plus an optional open stop or place, all in `rememberSaveable` state. Back closes the stop, then the place, then returns to Saved. Tapping a tracking notification opens its stop through `MainActivity.EXTRA_OPEN_STOP`.

## Theme and motion

`TsugiTheme` uses `MaterialExpressiveTheme` with the dynamic (wallpaper) colour scheme and `MotionScheme.expressive()`. The countdown morphs between `MaterialShapes.Cookie9Sided` and `SoftBurst`; arrival times use `RollingText`. Decorative spinning stops when system animations are turned off. MRT line colours are fixed rather than themed.
