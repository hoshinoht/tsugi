#!/usr/bin/env python3
"""Builds app/src/main/assets/stations.json from SG Rail Data's sg-rail.geojson.

Usage:
    curl -LO https://raw.githubusercontent.com/cheeaun/sgraildata/master/data/v1/sg-rail.geojson
    python3 scripts/build_stations.py sg-rail.geojson

SG Rail Data (https://github.com/cheeaun/sgraildata) compiles station points and exits from LTA's
geospatial datasets. Only names, station codes and coordinates are kept.
"""
import json
import sys
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/stations.json"


def main(path):
    features = json.load(open(path, encoding="utf-8"))["features"]

    def point(f):
        lng, lat = f["geometry"]["coordinates"]
        return round(lat, 6), round(lng, 6)

    stations = []
    for f in features:
        p = f["properties"]
        if p.get("stop_type") != "station":
            continue
        lat, lng = point(f)
        stations.append({"name": p["name"], "codes": p["station_codes"].split("-"), "lat": lat, "lng": lng, "exits": []})

    by_code = {code: s for s in stations for code in s["codes"]}
    for f in features:
        p = f["properties"]
        if p.get("stop_type") != "entrance":
            continue
        station = next((by_code[c] for c in p["station_codes"].split("-") if c in by_code), None)
        if station is None:
            continue
        lat, lng = point(f)
        station["exits"].append({"name": p.get("name", ""), "lat": lat, "lng": lng})

    for s in stations:
        s["exits"].sort(key=lambda e: (len(e["name"]), e["name"]))
    stations.sort(key=lambda s: s["name"])
    OUT.write_text(json.dumps(stations, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"{len(stations)} stations, {sum(len(s['exits']) for s in stations)} exits -> {OUT}")


if __name__ == "__main__":
    main(sys.argv[1])
