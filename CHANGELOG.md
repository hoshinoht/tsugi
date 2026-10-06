# Changelog

## 0.2.0

### Added

- **Bus alerts:** track a bus from its stop or the Next up card. You get a live countdown notification (a Live Update on Android 16 QPR2+) and heads-up alerts at a set lead time and on arrival.
- **Every service at a stop:** the route data (cached weekly) lists services LTA's live feed omits, with today's first-bus time.
- **Stop map:** a OneMap static map with pins for the stop and for you, walking directions, and an "Across the road" link.
- **Search:** bus numbers (opening a route screen), buildings, addresses and postal codes, plus recent stops, saved stops and example chips before you type.
- **Favourites:**
  - "Next up" prefers the nearest saved stop and a bus you can still catch, and shows the first bus overnight.
  - Places group stops into one board.
  - Cards collapse and can be dragged to reorder.
- **Stop screen:** sort by Soonest, Number or Starred.
- **MRT and LRT status** from `TrainServiceAlerts`.
- **Settings:** theme, Nearby radius, stop order and alert lead time.
- **Quick access:** a home-screen widget, a Quick Settings tile and launcher shortcuts.

### Changed

- **Material 3 Expressive:**
  - The countdown morphs as the bus arrives, and times roll as they change.
  - `SegmentedListItem` rows and item animations.
  - Predictive back on stop, route, place and settings screens.
  - The toolbar is fixed-width with a sliding highlight, hides on scroll, and the list fades behind it.
- **Crowding bars** are green, amber and red, and keep the 1–3 bar count.
- **TalkBack** reads each arrival tile as one sentence. Decorative motion stops when system animations are off.
- **Text at large sizes** shrinks to fit in tabs and tiles instead of clipping.
- **Nearby radius** defaults to 200 m.
- **minSdk** is now 31 (Android 12).

### Removed

- The locate button; Nearby locates you when opened and on pull-to-refresh.

### Fixed

- A startup crash on Android 17, caused by an old WorkManager pulled in by Glance.
- Nearby no longer polls an empty stop code after collapsing a stop.
- A restored place screen no longer closes before favourites load.

## 0.1.0

- First version: favourites, nearby stops, stop detail with live arrivals, and stop search.
