#!/usr/bin/env python3
"""Extract GeoNames → Wikidata links for cities1000 into <src>/wkdt.tsv."""
import sys, os
d = sys.argv[1]
ids = set(l.split('\t', 1)[0] for l in open(os.path.join(d, 'cities1000.txt'), encoding='utf-8'))
out = {}
for line in open(os.path.join(d, 'alternateNamesV2.txt'), encoding='utf-8'):
    c = line.split('\t')
    if c[2] == 'wkdt' and c[1] in ids: out[c[1]] = c[3]
with open(os.path.join(d, 'wkdt.tsv'), 'w') as f:
    for g, q in out.items(): f.write(f'{g}\t{q}\n')
print(len(ids), 'cities,', len(out), 'with wikidata')
