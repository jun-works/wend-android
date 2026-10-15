#!/usr/bin/env python3
"""Find Wikidata items for gap cities by GeoNames ID (P1566) → appends to <out>. Resumable: <out>.done records finished batches.
Usage: find_by_geonames_id.py gaps.tsv <src>/p1566_b.tsv"""
import sys, os, json, time, csv, urllib.request, urllib.parse
gaps, out = sys.argv[1], sys.argv[2]
ids = [r['geonameid'] for r in csv.DictReader(open(gaps), delimiter='\t')]
UA = 'WendGeoBuild/0.1'
done_p = out + '.done'
done = set(open(done_p).read().split()) if os.path.exists(done_p) else set()
found = 0
with open(out, 'a') as f, open(done_p, 'a') as d:
    for i in range(0, len(ids), 200):
        if str(i) in done: continue
        vals = ' '.join(f'"{g}"' for g in ids[i:i + 200])
        q = f'SELECT ?g ?item WHERE {{ VALUES ?g {{ {vals} }} ?item wdt:P1566 ?g . }}'
        url = 'https://query.wikidata.org/sparql?' + urllib.parse.urlencode({'query': q, 'format': 'json'})
        for a in range(5):
            try:
                r = json.load(urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': UA}), timeout=90)); break
            except Exception as e:
                print('retry', i, e, flush=True); time.sleep(10 * (a + 1))
        else:
            sys.exit(f'failed at batch {i}; rerun to resume')
        for b in r['results']['bindings']:
            f.write(f"{b['g']['value']}\t{b['item']['value'].rsplit('/', 1)[1]}\n"); found += 1
        f.flush(); d.write(f'{i}\n'); d.flush()
        print(f'{min(i + 200, len(ids))} / {len(ids)}', flush=True)
        time.sleep(1)
print(len(ids), 'gaps,', found, 'found via P1566 this run')
