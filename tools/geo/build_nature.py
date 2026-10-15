"""Natural Earth physical names → app/src/main/assets/geo/nature.json (deserts, mountain ranges, plateaus, islands,
seas, bays, peaks). The trip map labels them in the user's language and lightly tints deserts / ranges / plateaus.

    python3 build_nature.py <dir with Natural Earth 10m geojson> ../../app/src/main/assets/geo/nature.json

Plain JSON on purpose: the Android build un-gzips *.gz assets and drops the extension (the APK compresses it anyway).

Needs ne_10m_geography_regions_polys, ne_10m_geography_marine_polys, ne_10m_geography_regions_elevation_points.
Public domain data, no attribution required.
"""
import argparse
import json
import math
import os

from nature_names import NatureNames

# region classes we label; the first three are also tinted on the map
TINT = {'Desert': 'desert', 'Range/mtn': 'range', 'Plateau': 'plateau'}
LABEL_ONLY = {'Plain': 'plain', 'Basin': 'basin', 'Valley': 'valley', 'Lowland': 'plain', 'Tundra': 'plain',
              'Wetlands': 'plain', 'Depression': 'basin', 'Foothills': 'range', 'Delta': 'plain',
              'Peninsula': 'land', 'Pen/cape': 'land', 'Island': 'island', 'Island group': 'island'}
TOLERANCE = 0.03  # degrees, ~3 km


def rdp(pts, eps):
    if len(pts) < 3: return pts
    a, b = pts[0], pts[-1]
    dx, dy = b[0] - a[0], b[1] - a[1]
    norm = math.hypot(dx, dy) or 1e-12
    best, idx = 0.0, 0
    for i in range(1, len(pts) - 1):
        d = abs(dy * pts[i][0] - dx * pts[i][1] + b[0] * a[1] - b[1] * a[0]) / norm
        if d > best: best, idx = d, i
    if best <= eps: return [a, b]
    return rdp(pts[:idx + 1], eps)[:-1] + rdp(pts[idx:], eps)


def simplify_ring(ring, eps):
    """Closed ring: split at the point farthest from the start, simplify both halves (plain RDP collapses a closed ring)."""
    pts = ring[:-1] if ring[0] == ring[-1] else ring
    if len(pts) < 4: return ring
    far = max(range(len(pts)), key=lambda i: math.hypot(pts[i][0] - pts[0][0], pts[i][1] - pts[0][1]))
    a = rdp(pts[:far + 1], eps)
    b = rdp(pts[far:] + [pts[0]], eps)
    return a[:-1] + b


def rings_of(geom):
    polys = [geom['coordinates']] if geom['type'] == 'Polygon' else geom['coordinates']
    return [poly[0] for poly in polys]  # outer rings only


def area_centroid(ring):
    a = cx = cy = 0.0
    for (x0, y0), (x1, y1) in zip(ring, ring[1:] + ring[:1]):
        c = x0 * y1 - x1 * y0
        a += c; cx += (x0 + x1) * c; cy += (y0 + y1) * c
    if abs(a) < 1e-12:
        return abs(a), (sum(p[0] for p in ring) / len(ring), sum(p[1] for p in ring) / len(ring))
    return abs(a) / 2, (cx / (3 * a), cy / (3 * a))


def flat(ring):
    return [round(v, 3) for pt in ring for v in (pt[1], pt[0])]  # lat, lon pairs


def main(src, out, audit=None):
    reviewed = NatureNames()
    names = reviewed.names
    feats = []
    for f in json.load(open(os.path.join(src, 'ne_10m_geography_regions_polys.geojson')))['features']:
        p = f['properties']
        cls = p.get('FEATURECLA')
        kind = TINT.get(cls) or LABEL_ONLY.get(cls)
        if not kind or not f.get('geometry'): continue
        rings = rings_of(f['geometry'])
        biggest = max(rings, key=lambda r: area_centroid(r)[0])
        _, (lon, lat) = area_centroid(biggest)
        item = {'k': kind, 'r': round(float(p.get('MIN_LABEL') or p.get('SCALERANK') or 9), 1), 'n': names(p), 'p': [round(lat, 3), round(lon, 3)]}
        if cls in TINT:
            simple = [simplify_ring(r, TOLERANCE) for r in rings]
            item['g'] = [flat(r) for r in simple if len(r) >= 4]
        feats.append(item)
    for f in json.load(open(os.path.join(src, 'ne_10m_geography_marine_polys.geojson')))['features']:
        p = f['properties']
        if not f.get('geometry'): continue
        biggest = max(rings_of(f['geometry']), key=lambda r: area_centroid(r)[0])
        _, (lon, lat) = area_centroid(biggest)
        feats.append({'k': 'sea', 'r': round(float(p.get('min_label') or p.get('scalerank') or 9), 1), 'n': names(p), 'p': [round(lat, 3), round(lon, 3)]})
    for f in json.load(open(os.path.join(src, 'ne_10m_geography_regions_elevation_points.geojson')))['features']:
        p = f['properties']
        lon, lat = f['geometry']['coordinates'][:2]
        feats.append({'k': 'peak', 'r': float(p.get('scalerank') or 9), 'n': names(p), 'p': [round(lat, 3), round(lon, 3)], 'e': int(p.get('elevation') or 0)})
    reviewed.validate()
    data = json.dumps(feats, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    with open(out, 'wb') as fp: fp.write(data)
    print(f'{len(feats)} features, {len(data) / 1e6:.2f} MB → {out}')
    if audit:
        reviewed.write_audit(audit)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('src')
    parser.add_argument('out')
    parser.add_argument('--audit', help='Write per-feature Chinese name changes as TSV')
    args = parser.parse_args()
    main(args.src, args.out, args.audit)
