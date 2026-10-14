#!/usr/bin/env python3
"""Find Wikidata items for gap cities by GeoNames ID (P1566) → <src>/p1566.tsv. Usage: find_by_geonames_id.py gaps.tsv <src>/p1566.tsv"""
import sys, json, time, csv, urllib.request, urllib.parse
gaps, out = sys.argv[1], sys.argv[2]
ids = [r['geonameid'] for r in csv.DictReader(open(gaps), delimiter='\t')]
UA = 'WendGeoBuild/0.1 (offline city names build; contact jh22dev@gmail.com)'
res = {}
for i in range(0, len(ids), 200):
    vals = ' '.join(f'"{g}"' for g in ids[i:i+200])
    q = f'SELECT ?g ?item WHERE {{ VALUES ?g {{ {vals} }} ?item wdt:P1566 ?g . }}'
    url = 'https://query.wikidata.org/sparql?' + urllib.parse.urlencode({'query': q, 'format': 'json'})
    for a in range(5):
        try:
            r = json.load(urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': UA}), timeout=90)); break
        except Exception as e:
            print('retry', i, e, flush=True); time.sleep(10)
    for b in r['results']['bindings']:
        res[b['g']['value']] = b['item']['value'].rsplit('/', 1)[1]
    time.sleep(1)
with open(out, 'w') as f:
    for g, qq in res.items(): f.write(f'{g}\t{qq}\n')
print(len(ids), 'gaps,', len(res), 'found via P1566')
