#!/usr/bin/env python3
"""List cities that should have every language but still miss some after the automatic build.

Targets: population 100k+ plus geo/travel_towns.tsv. Output TSV goes to stdout; fill the blanks into manual_names.tsv.
Usage: python3 geo/list_gaps.py --src <dir> > gaps.tsv
"""
import argparse, csv, os, sys, unicodedata

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_cities import HERE, LANGS, build


def fold(s):
    return ''.join(ch for ch in unicodedata.normalize('NFKD', s) if not unicodedata.combining(ch)).lower()


def travel_ids(rows):
    want = {(fold(r['name']), r['cc']) for r in csv.DictReader(open(os.path.join(HERE, 'travel_towns.tsv'), encoding='utf-8'), delimiter='\t')}
    best = {}
    for gid, c, out, _ in rows:
        key = (fold(c['name']), c['cc'])
        if key in want and (key not in best or c['pop'] > best[key][1]): best[key] = (gid, c['pop'])
    missing = sorted(want - set(best))
    if missing: print('# travel_towns not found:', missing, file=sys.stderr)
    return {v[0] for v in best.values()}


if __name__ == '__main__':
    ap = argparse.ArgumentParser(); ap.add_argument('--src', required=True); a = ap.parse_args()
    rows = build(a.src)
    tt = travel_ids(rows)
    print('\t'.join(['geonameid', 'cc', 'pop'] + LANGS))
    for gid, c, out, man in sorted(rows, key=lambda r: -r[1]['pop']):
        if (c['pop'] >= 100000 or gid in tt) and not all(out[k] for k in LANGS):
            print('\t'.join([gid, c['cc'], str(c['pop'])] + [out[k] for k in LANGS]))
