# Data sources

## LTA DataMall

Requires an AccountKey from LTA. Endpoints and fields follow the DataMall API User Guide (v6.10).

| Endpoint | Used for | LTA update frequency |
| --- | --- | --- |
| `v3/BusArrival?BusStopCode=` | Next three buses per service: ETA, load, deck type, wheelchair access, whether the time is live or scheduled | 20 s |
| `BusStops?$skip=` | All bus stops (code, road, description, coordinates), 500 per page | Ad hoc |
| `BusRoutes?$skip=` | Every stop on every service, in order, with first and last bus times (weekday, Saturday, Sunday), 500 per page | Ad hoc |
| `BusServices?$skip=` | Each service direction's category, loop point and frequency in four time bands, 500 per page | Ad hoc |
| `TrainServiceAlerts` | Line status, affected stations and direction, free bus or shuttle, LTA's message | Ad hoc |
| `PCDRealTime?TrainLine=` | Platform crowding (low, moderate, high) at every station on a line | 10 min |
| `PCDForecast?TrainLine=` | Forecast platform crowding in half-hours for today | Daily |
| `v2/FacilitiesMaintenance` | Lifts under maintenance: station, lift ID and where it goes | Ad hoc |

Notes:
- `BusArrival` returns nothing for a stop with no buses on the road, or during maintenance; the app treats that as "no buses running".
- A bus's destination name is looked up from the cached stop list using its `DestinationCode`, so no extra calls are needed.
- `BusArrival` also omits services that have no bus on the road (late at night, peak-only expresses). The app fills them in from `BusRoutes` and shows today's first-bus time.
- First- and last-bus times use the weekday, Saturday or Sunday timetable by day of the week. Public holidays (and Mondays in lieu) use the Sunday timetable; the list in `data/Timetable.kt` covers 2026 and 2027 and needs the next year added when MOM publishes it, usually in June.
- A bus day runs from 4 am to 4 am, so a last bus at 12:15 am counts towards the day before. Night services, whose first bus is after midnight, keep their calendar day.
- `BusArrival` gives each bus's last reported position (blank or 0 for scheduled times). Snapping it to the nearest stop the bus hasn't passed on its route gives "N stops away"; positions more than 600 m from the route are ignored.
- Crowd endpoints take their own line codes: the Changi Airport branch is `CGL` and the LRTs are `SLRT` and `PLRT`. Bayfront and Marina Bay, renamed CE1/CE2 to CC34/CC33, are matched under both codes.
- LTA does not publish live train arrival times, so the app doesn't show them.
- Not used yet: the GTFS train APIs launched on 3 August 2026 (timetable, service alerts, and trip updates during disruptions). Their endpoint paths weren't available to check when 0.3.0 was written; the timetable would add first and last trains, and the trip updates live train times during disruptions.

## MRT and LRT stations

`app/src/main/assets/stations.json` lists every station's name, codes and coordinates, and its exits. It's built by `scripts/build_stations.py` from [SG Rail Data](https://github.com/cheeaun/sgraildata)'s `sg-rail.geojson` (version 5.1.0, July 2026, including Circle Line Stage 6), which compiles LTA's Train Station and Train Station Exit datasets. Re-run the script when stations open. Lines are worked out from station codes (EW and CG are East West, SE/SW/STC Sengkang LRT, and so on).

## OneMap

`https://www.onemap.gov.sg/api/common/elastic/search` finds buildings, addresses and postal codes. It works without an access token. The app sends only the search text, after a 350 ms pause and once at least 3 characters are typed, and uses the returned coordinates to show nearby stops.

`https://www.onemap.gov.sg/api/staticmap/getStaticImage` draws the stop map, and the Nearby map when you ask for it, also without a token. Requests are 512 × 256 px (the maximum width), at zoom 17 or 18 (18 is the most detailed level the API returns), in the default or night style, with a pin for the stop and, if you're within 300 m, one for you. OneMap's attribution is part of the image and is kept.

Both services have their own terms of use; this repository's license covers only its code.
