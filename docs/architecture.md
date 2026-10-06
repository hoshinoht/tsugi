# Architecture

Tsugi is a single-module app with no navigation, dependency-injection or image libraries. A handful of singletons live in `AppContainer` (in `TsugiApplication.kt`), and one `AppViewModel` serves all screens.

| Component | File | Role |
| --- | --- | --- |
| `LtaApi` | `data/LtaApi.kt` | OkHttp + kotlinx.serialization client for DataMall; sends the `AccountKey` header |
| `OneMapApi` | `data/OneMapApi.kt` | OneMap address search (no token) |
| `StopRepository` | `data/StopRepository.kt` | All bus stops, cached as `bus_stops.json` in app storage; nearby and text search |
| `RouteRepository` | `data/RouteRepository.kt` | All route stops, cached as `bus_routes.json`; services per stop, first-bus times, routes by service |
| `SettingsRepository` | `data/SettingsRepository.kt` | Theme, Nearby radius, stop order, alert lead time, card order, recent stops |
| `pickNextUp` | `data/NextUp.kt` | Chooses the Next up bus: nearest saved stop, then a catchable bus (pure function, unit-tested) |
| `FavouritesRepository` | `data/FavouritesRepository.kt` | Saved stops, buses and places as one JSON list in DataStore |
| `LocationProvider` | `data/LocationProvider.kt` | Platform `LocationManager` (fused provider where available) |
| `AppViewModel` | `ui/AppViewModel.kt` | Arrivals cache, nearby state, train status, place editing |
| `BusTrackingService` | `tracking/BusTrackingService.kt` | Foreground service for bus alerts; see [bus alerts](bus-alerts.md) |
| `NextBusTileService` | `tracking/NextBusTileService.kt` | Quick Settings tile showing the Next up bus |
| `FavouritesWidget` | `widget/FavouritesWidget.kt` | Glance home-screen widget |

## Refresh policy

| Data | When it's fetched |
| --- | --- |
| Bus arrivals | Every 20 s for the stops on screen, only while the app is visible. Stops fetched in the last 10 s are skipped unless you pull to refresh. |
| Train alerts | Every 2 min while Favourites is visible, and on pull-to-refresh. |
| Bus stops | Once, then when the cached file is older than 7 days. A stale cache is kept if the download fails. |
| Bus routes | Same as bus stops; about 54 pages fetched 4 at a time with retries, since requesting them all at once gets throttled. |
| Stop maps | When a stop screen opens; kept in a small in-memory cache. Your position is rounded to about 10 m. |
| Quick Settings tile | Each time the shade opens. |
| Widget | When favourites change, every 30 min (Android's minimum), and on its refresh button. |

## Navigation

`TsugiRoot` holds the current tab (Saved, Nearby, Search) plus optional full-screen layers: settings, a stop, a route or a place, in that priority, all in `rememberSaveable` state. A `SaveableStateHolder` keeps each tab's state (scroll position, collapsed cards) while it's off screen.

Back closes the top layer with a predictive-back animation, then returns to Saved. Tapping a tracking notification opens its stop through `MainActivity.EXTRA_OPEN_STOP`; launcher shortcuts open a tab through `EXTRA_OPEN_TAB`.

## Theme and motion

`TsugiTheme` uses `MaterialExpressiveTheme` with the dynamic (wallpaper) colour scheme and `MotionScheme.expressive()`. The countdown morphs between `MaterialShapes.Cookie9Sided` and `SoftBurst`; arrival times use `RollingText`. Decorative spinning stops when system animations are turned off. MRT line and crowding colours are fixed rather than themed. The toolbar has three fixed-width slots with a sliding highlight, so it never resizes when switching tabs.
