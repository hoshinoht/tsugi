# Data sources

## LTA DataMall

Requires an AccountKey from LTA. Endpoints and fields follow the DataMall API User Guide (v6.10).

| Endpoint | Used for | LTA update frequency |
| --- | --- | --- |
| `v3/BusArrival?BusStopCode=` | Next three buses per service: ETA, load, deck type, wheelchair access, whether the time is live or scheduled | 20 s |
| `BusStops?$skip=` | All bus stops (code, road, description, coordinates), 500 per page | Ad hoc |
| `TrainServiceAlerts` | Line status, affected stations and direction, free bus or shuttle, LTA's message | Ad hoc |

Notes:
- `BusArrival` returns nothing for a stop with no buses on the road, or during maintenance; the app treats that as "no buses running".
- A bus's destination name is looked up from the cached stop list using its `DestinationCode`, so no extra calls are needed.
- LTA does not publish live train arrival times, so the app doesn't show them.

## OneMap

`https://www.onemap.gov.sg/api/common/elastic/search` finds buildings, addresses and postal codes. It works without an access token. The app sends only the search text, after a 350 ms pause and once at least 3 characters are typed, and uses the returned coordinates to show nearby stops.

Both services have their own terms of use; this repository's license covers only its code.
