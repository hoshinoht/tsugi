# Data sources

## LTA DataMall

Requires an AccountKey from LTA. Endpoints and fields follow the DataMall API User Guide (v6.10).

| Endpoint | Used for | LTA update frequency |
| --- | --- | --- |
| `v3/BusArrival?BusStopCode=` | Next three buses per service: ETA, load, deck type, wheelchair access, whether the time is live or scheduled | 20 s |
| `BusStops?$skip=` | All bus stops (code, road, description, coordinates), 500 per page | Ad hoc |
| `BusRoutes?$skip=` | Every stop on every service, in order, with first and last bus times (weekday, Saturday, Sunday), 500 per page | Ad hoc |
| `TrainServiceAlerts` | Line status, affected stations and direction, free bus or shuttle, LTA's message | Ad hoc |

Notes:
- `BusArrival` returns nothing for a stop with no buses on the road, or during maintenance; the app treats that as "no buses running".
- A bus's destination name is looked up from the cached stop list using its `DestinationCode`, so no extra calls are needed.
- `BusArrival` also omits services that have no bus on the road (late at night, peak-only expresses). The app fills them in from `BusRoutes` and shows today's first-bus time.
- First-bus times use the weekday, Saturday or Sunday timetable by day of the week. Public holidays run the Sunday timetable, which the app doesn't account for yet.
- LTA does not publish live train arrival times, so the app doesn't show them.

## OneMap

`https://www.onemap.gov.sg/api/common/elastic/search` finds buildings, addresses and postal codes. It works without an access token. The app sends only the search text, after a 350 ms pause and once at least 3 characters are typed, and uses the returned coordinates to show nearby stops.

`https://www.onemap.gov.sg/api/staticmap/getStaticImage` draws the stop map, also without a token. Requests are 512 × 256 px (the maximum width), at zoom 17 or 18 (18 is the most detailed level the API returns), in the default or night style, with a pin for the stop and, if you're within 300 m, one for you. OneMap's attribution is part of the image and is kept.

Both services have their own terms of use; this repository's license covers only its code.
