# Changelog

## 0.3.0

### Added

- **Stations:** a screen for every MRT and LRT station with its lines and status, platform crowding now and forecast by the half-hour, lifts under maintenance, and the bus stops at its exits with live arrivals. "Save as place" saves those stops.
- **Stations everywhere:** bus stops next to a station show it with line colours; Nearby lists stations around you; Search finds stations by name or code.
- **Disruptions:** the card names affected stations and links each one, so you can find a bus there. Optional notifications when a line near your saved stops is disrupted, and when it's back to normal.
- **Last bus:** a chip in the 45 minutes before tonight's last bus, on stops and saved buses.
- **Stops away:** how many stops away the next bus is, on stops, Next up and the tracking notification.
- **Leave-now alerts:** bus alerts count your walk to the stop.
- **Frequency:** how often a service runs right now, plus category and loop point on its route.
- **Widget setup:** show all favourites, one saved stop or one place; rows fill the widget's height.
- **Shortcuts:** your first saved stops and places on the launcher icon, and any stop pinned to the home screen.
- **Nearby map:** an optional map with the closest stops numbered as in the list.
- **Wide screens:** two panes on tablets and unfolded foldables.

### Changed

- Public holidays use the Sunday timetable.
- Search understands spelled-out words ("station", "opposite", "primary school"), any word order and small typos.
- Tap a bus number on a stop to open its route, with your stop highlighted and the next buses marked where they are. Route stops show first and last buses.
- Screens stack, so stops and stations can open each other; back goes through them in order.

- **Nearby is much faster:** it shows stops at once from the phone's last known position (if under 10 minutes old), then refines with a fresh fix, reshuffling only if you've moved more than 25 m. Location also warms up at launch, and a refresh button in the title spins while locating.
- **Test disruption alert:** a "Send a test alert" button under Train disruptions in Settings sends LTA's sample disruption through the real notification, so you can check alerts get through.
- **Leave-now alerts** allow 30% extra for winding streets, and only use a position from the last 3 minutes.

### Fixed

- "Stops away" was wrong on loop and out-and-back routes: it could snap a bus to the stop on the other side of the road. It now walks back from your stop to the bus.
- The widget setup screen could crash when opened with a widget id that isn't Tsugi's.
- No more "Trains back to normal" alert just because you unsaved the stops near a disrupted line.
- The disruption headline counts lines, not segments: one line in both directions no longer reads "2 lines disrupted".
- Night-service last buses are matched to the right day (the service day now starts at 5 am).
- Bus frequencies are marked "on weekdays" on other days, since LTA only publishes weekday figures.
- Pinned shortcuts to a deleted place are disabled.
- Only 5-digit stop codes are sent to LTA, and the widget's clock uses Singapore time.
- "1 stop within 200 m", not "1 stops".
- Away from your saved stops, Next up showed "No buses running" in the middle of the day: the walking-time check applied to stops kilometres away, so every bus looked uncatchable. Only stops within 400 m count the walk now, and the overnight card only shows when no saved bus is running at all.

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
