"""Build app/src/main/assets/basemap.bin — Wend's offline paper map (Natural Earth, public domain).

Layers are simplified (~300m) and clipped into 2° cells so the app decodes only the cells it shows.

Usage:
  pip install shapely
  python build_basemap.py <dir with Natural Earth 10m geojson> <out file>

Needs: ne_10m_land, ne_10m_lakes, ne_10m_rivers_lake_centerlines, ne_10m_admin_0_boundary_lines_land,
ne_10m_admin_1_states_provinces_lines, ne_10m_roads, ne_10m_populated_places (.geojson).

Format (little endian):
  "WBM1" · u8 cellDeg · u32 cellCount · cellCount × (i16 latIdx, i16 lonIdx, u32 offset, u32 length)
  · u32 placeCount · places · cell blobs
  cell blob: for each layer in LAYERS: varint geomCount · geom: varint ringCount · ring: varint n · n × (zigzag Δlat, zigzag Δlon)
    coordinates are round(deg × 1e4); the first point of a ring is relative to the cell's south-west corner.
  place: i32 lat×1e4 · i32 lon×1e4 · u8 rank · u8 nameLen · utf8 name
"""
import json
import math
import os
import re
import struct
import sys
from collections import defaultdict

from shapely.geometry import box, shape, LineString, MultiLineString, Polygon, MultiPolygon, GeometryCollection

CELL = 2
SIMPLIFY = 0.003
SCALE = 1e4
LAYERS = ['land', 'lake', 'river', 'border', 'province', 'road']
SOURCES = {
    'land': 'ne_10m_land',
    'lake': 'ne_10m_lakes',
    'river': 'ne_10m_rivers_lake_centerlines',
    'border': 'ne_10m_admin_0_boundary_lines_land',
    'province': 'ne_10m_admin_1_states_provinces_lines',
    'road': 'ne_10m_roads',
}
POLYGON_LAYERS = {'land', 'lake'}


def keep(layer, props):
    if layer == 'road':
        return props.get('type') != 'Ferry Route'
    return True


def varint(n, out):
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return


def zigzag(n):
    return (n << 1) ^ (n >> 63)


def parts(geom, polygon):
    """Yield list-of-rings (polygon) or single-ring lists (line) from any geometry."""
    if geom.is_empty:
        return
    if isinstance(geom, (MultiPolygon, MultiLineString, GeometryCollection)):
        for g in geom.geoms:
            yield from parts(g, polygon)
    elif isinstance(geom, Polygon) and polygon:
        yield [list(geom.exterior.coords)] + [list(r.coords) for r in geom.interiors]
    elif isinstance(geom, LineString) and not polygon:
        yield [list(geom.coords)]


def main(src, out_path):
    cells = defaultdict(lambda: {l: [] for l in LAYERS})
    for layer in LAYERS:
        polygon = layer in POLYGON_LAYERS
        features = json.load(open(os.path.join(src, SOURCES[layer] + '.geojson')))['features']
        count = 0
        for f in features:
            if not f.get('geometry') or not keep(layer, f['properties']):
                continue
            geom = shape(f['geometry']).simplify(SIMPLIFY, preserve_topology=polygon)
            if geom.is_empty:
                continue
            minx, miny, maxx, maxy = geom.bounds
            for li in range(math.floor(miny / CELL), math.floor(maxy / CELL) + 1):
                for lo in range(math.floor(minx / CELL), math.floor(maxx / CELL) + 1):
                    piece = geom.intersection(box(lo * CELL, li * CELL, (lo + 1) * CELL, (li + 1) * CELL))
                    for rings in parts(piece, polygon):
                        rings = [r for r in rings if len(r) >= (3 if polygon else 2)]
                        if rings:
                            cells[(li, lo)][layer].append(rings)
                            count += 1
        print(f'{layer}: {count} pieces', file=sys.stderr)

    blobs = []
    for (li, lo), layers in sorted(cells.items()):
        out = bytearray()
        ox, oy = round(lo * CELL * SCALE), round(li * CELL * SCALE)
        for layer in LAYERS:
            geoms = layers[layer]
            varint(len(geoms), out)
            for rings in geoms:
                varint(len(rings), out)
                for ring in rings:
                    pts = [(round(y * SCALE), round(x * SCALE)) for x, y in ring]
                    # drop consecutive duplicates after quantising
                    pts = [p for i, p in enumerate(pts) if i == 0 or p != pts[i - 1]]
                    varint(len(pts), out)
                    py, px = oy, ox
                    for y, x in pts:
                        varint(zigzag(y - py), out)
                        varint(zigzag(x - px), out)
                        py, px = y, x
        blobs.append(((li, lo), bytes(out)))

    places = bytearray()
    feats = json.load(open(os.path.join(src, 'ne_10m_populated_places.geojson')))['features']
    for f in feats:
        p = f['properties']
        name = re.sub(r'(특별자치시|특별자치도|특별시|광역시)$', '', (p.get('NAME_KO') or p.get('NAME') or '').strip())
        if not name:
            continue
        raw = name.encode('utf-8')[:255]
        lon, lat = f['geometry']['coordinates'][:2]
        places += struct.pack('<iiBB', round(lat * SCALE), round(lon * SCALE), min(255, int(p.get('SCALERANK') or 10)), len(raw)) + raw
    place_count = len(feats)

    header = bytearray(b'WBM1') + struct.pack('<BI', CELL, len(blobs))
    index_size = len(blobs) * 12
    base = len(header) + index_size + 4 + len(places)
    offset = base
    index = bytearray()
    for (li, lo), blob in blobs:
        index += struct.pack('<hhII', li, lo, offset, len(blob))
        offset += len(blob)
    with open(out_path, 'wb') as fp:
        fp.write(header + index + struct.pack('<I', place_count) + places)
        for _, blob in blobs:
            fp.write(blob)
    print(f'{len(blobs)} cells, {place_count} places, {os.path.getsize(out_path) / 1e6:.1f} MB', file=sys.stderr)


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
