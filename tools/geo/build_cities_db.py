"""cities.tsv.gz → SQLite for the app (assets/geo/cities.db).

The table must match CityEntity in app/.../data/geo/CityDatabase.kt exactly (Room checks it on first open).
The cell formula must match CityGrid.cell in CityFinder.kt.

    python3 build_cities_db.py ../../design/geo/cities.tsv.gz ../../app/src/main/assets/geo/cities.db
"""
import csv
import gzip
import os
import sqlite3
import sys

CELL_E4 = 5000  # 0.5° in 1e-4° units
LON_CELLS = 360 * 10_000 // CELL_E4


def e4(v):
    return round(float(v) * 10_000)


def cell(lat, lon):
    li = min((lat + 900_000) // CELL_E4, 180 * 10_000 // CELL_E4 - 1)
    lo = ((lon + 1_800_000) // CELL_E4) % LON_CELLS
    return li * LON_CELLS + lo


def main(src, out):
    if os.path.exists(out):
        os.remove(out)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    db = sqlite3.connect(out)
    db.execute("PRAGMA page_size = 4096")
    db.execute(
        "CREATE TABLE `city` (`id` INTEGER NOT NULL, `lat` INTEGER NOT NULL, `lon` INTEGER NOT NULL, "
        "`cell` INTEGER NOT NULL, `cc` TEXT NOT NULL, `pop` INTEGER NOT NULL, `en` TEXT NOT NULL, `ko` TEXT, "
        "`ja` TEXT, `zh_hans` TEXT, `zh_hant` TEXT, PRIMARY KEY(`id`))"
    )
    rows = []
    with gzip.open(src, "rt", encoding="utf-8", newline="") as f:
        for r in csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE):
            lat, lon = e4(r["lat"]), e4(r["lon"])
            rows.append((
                int(r["geonameid"]), lat, lon, cell(lat, lon), r["cc"], int(r["pop"] or 0), r["en"],
                r["ko"] or None, r["ja"] or None, r["zh_hans"] or None, r["zh_hant"] or None,
            ))
    db.executemany("INSERT INTO city VALUES (?,?,?,?,?,?,?,?,?,?,?)", rows)
    db.execute("CREATE INDEX `index_city_cell` ON `city` (`cell`)")
    db.execute("PRAGMA user_version = 4")  # CityDatabase version — raise both together when the data changes
    db.commit()
    db.execute("VACUUM")
    db.close()
    print(f"{len(rows):,} cities → {out} ({os.path.getsize(out) / 1e6:.1f} MB)")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
