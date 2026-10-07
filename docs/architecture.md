# Architecture

Tsugi is a single-module app with no navigation, dependency-injection or image libraries. A handful of singletons live in `AppContainer` (in `TsugiApplication.kt`), and one `AppViewModel` serves all screens.

| Component | File | Role |
| --- | --- | --- |
| `LtaApi` | `data/LtaApi.kt` | OkHttp + kotlinx.serialization client for DataMall; sends the `AccountKey` header |
| `OneMapApi` | `data/OneMapApi.kt` | OneMap address search (no token) |
| `StopRepository` | `data/StopRepository.kt` | All bus stops, cached as `bus_stops.json` in app storage; nearby and text search |
| `RouteRepository` | `data/RouteRepository.kt` | All route stops, cached as `bus_routes.json`; services per stop, first- and last-bus times, routes by service |
| `ServiceInfoRepository` | `data/BusServices.kt` | Every service direction's category, loop point and frequency, cached as `bus_services.json` |
| `StationRepository` | `data/Stations.kt` | MRT and LRT stations and exits from `assets/stations.json`; nearby, search, station codes to lines |
| `withTimetable` | `data/Timetable.kt` | Fills a stop's live services from the timetable: missing services, first and last bus (public holidays run Sunday's), frequency |
| `stopsAway` | `data/StopsAway.kt` | Snaps a bus's reported position to its route to count stops to yours |
| `searchStops` | `data/StopSearch.kt` | Stop search with LTA abbreviations, any word order and one-typo tolerance |
| `SettingsRepository` | `data/SettingsRepository.kt` | Theme, colourway, Nearby radius, stop order, alert lead time, card order, recent stops |
| `pickNextUp` | `data/NextUp.kt` | Chooses the Next up bus: nearest saved stop, then a catchable bus (pure function, unit-tested) |
| `FavouritesRepository` | `data/FavouritesRepository.kt` | Saved stops, buses and places as one JSON list in DataStore |
| `LocationProvider` | `data/LocationProvider.kt` | Platform `LocationManager` (fused provider where available) |
| `AppViewModel` | `ui/AppViewModel.kt` | Arrivals cache, nearby state, train status, place editing |
| `BusTrackingService` | `tracking/BusTrackingService.kt` | Foreground service for bus alerts; see [bus alerts](bus-alerts.md) |
| `DisruptionWorker` | `tracking/DisruptionWorker.kt` | Optional 15-minute check for disruptions on lines near your saved stops |
| `NextBusTileService` | `tracking/NextBusTileService.kt` | Quick Settings tile showing the Next up bus |
| `FavouritesWidget` | `widget/FavouritesWidget.kt` | Glance home-screen widget: all favourites, or one stop or place chosen in `WidgetConfigActivity` |
| `Shortcuts` | `widget/Shortcuts.kt` | Launcher shortcuts for the first saved stops and places, and pinned stop shortcuts |

## Refresh policy

| Data | When it's fetched |
| --- | --- |
| Bus arrivals | Every 20 s for the stops on screen, only while the app is visible. Stops fetched in the last 10 s are skipped unless you pull to refresh. |
| Train alerts | Every 2 min while Favourites is visible, and on pull-to-refresh. |
| Bus stops | Once, then when the cached file is older than 7 days. A stale cache is kept if the download fails. |
| Bus routes | Same as bus stops; about 54 pages fetched 4 at a time with retries, since requesting them all at once gets throttled. |
| Bus services | Same as bus stops (2 pages). |
| Stations | Bundled in the APK; read once when first needed. |
| Station crowding | When a station screen opens, then every 10 min while it's visible, per line; lines fetched in the last 10 min are skipped. |
| Lift maintenance | With station crowding, network-wide. |
| Disruption alerts | Every 15 min in the background (WorkManager), only when turned on in Settings and online. |
| Stop maps | When a stop screen opens; kept in a small in-memory cache. Your position is rounded to about 10 m. |
| Quick Settings tile | Each time the shade opens. |
| Widget | When favourites change, when set up, every 30 min (Android's minimum), and on its refresh button. |

## Navigation

`TsugiRoot` holds the current tab (Saved, Nearby, Search) plus a stack of full-screen layers saved as strings (`settings`, `stop:<code>`, `service:<no>`, `place:<id>`, `station:<code>`) in `rememberSaveable` state, so a stop can open its station and the station another stop. A `SaveableStateHolder` keeps each tab's state (scroll position, collapsed cards) while it's off screen.

Back pops the top layer with a predictive-back animation, then returns to Saved. Tapping a tracking notification opens its stop through `MainActivity.EXTRA_OPEN_STOP`; launcher shortcuts open a tab through `EXTRA_OPEN_TAB`, a stop through `EXTRA_OPEN_STOP` or a place through `EXTRA_OPEN_PLACE`.

From 840 dp wide, the tabs sit in a 400 dp pane on the left and the top layer shows on the right. Opening something from the tabs replaces the right pane instead of stacking.

## Theme and motion

`TsugiTheme` takes the stored `Colourway`. The traditional colourways (`ui/theme/Colourways.kt`) map an `InkPalette` onto M3 colour roles and use `InkTypography`, which sets display, headline and large title styles in a bundled subset of Zen Old Mincho. Wallpaper uses the dynamic colour scheme and the default typography. Either way it is `MaterialExpressiveTheme` with `MotionScheme.expressive()`.

`TsugiTheme` also provides a `TsugiStyle` (`Ink` or `Expressive`) through `LocalTsugiStyle`. Shared components branch on it (`TsugiTheme.isInk`) for Ink & Paper's bordered cards, badges, "Now" and the brush route in `InkRoute.kt`; screens keep their logic. `ColourwayContrastTest` checks every Ink scheme against WCAG AA.

In Expressive, the countdown morphs between `MaterialShapes.Cookie9Sided` and `SoftBurst`; arrival times use `RollingText`. Decorative spinning stops when system animations are turned off. MRT line and crowding colours are fixed rather than themed. The toolbar has three fixed-width slots with a sliding highlight, so it never resizes when switching tabs.
