#!/usr/bin/env python3
"""Fetch Wikidata labels (en/ko/ja/zh) for GeoNames cities. Needs wkdt.tsv from extract step in <src>; resumable."""
import sys, os, json, time, urllib.request, urllib.parse
d = sys.argv[1]
qids = sorted(set(l.split('\t')[1].strip() for n in ('wkdt.tsv', 'p1566.tsv') if os.path.exists(os.path.join(d, n)) for l in open(os.path.join(d, n))))
outp = os.path.join(d, 'wd_labels.jsonl')
done = set()
if os.path.exists(outp):
    for l in open(outp): done.add(json.loads(l)['id'])
todo = [q for q in qids if q not in done]
LANGS = 'en|ko|ja|zh|zh-hans|zh-hant|zh-cn|zh-tw|zh-hk'
UA = 'WendGeoBuild/0.1 (offline city names build; contact jh22dev@gmail.com)'
with open(outp, 'a') as f:
    for i in range(0, len(todo), 50):
        batch = todo[i:i + 50]
        url = 'https://www.wikidata.org/w/api.php?' + urllib.parse.urlencode(dict(action='wbgetentities', ids='|'.join(batch), props='labels', languages=LANGS, format='json'))
        for attempt in range(6):
            try:
                r = json.load(urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': UA}), timeout=60))
                if 'error' in r: raise RuntimeError(r['error'].get('code'))
                break
            except Exception as e:
                print('retry', i, e, flush=True); time.sleep(5 * (attempt + 1))
        else:
            sys.exit('failed at %d' % i)
        for q, ent in r.get('entities', {}).items():
            f.write(json.dumps({'id': q, 'labels': {k: v['value'] for k, v in ent.get('labels', {}).items()}}, ensure_ascii=False) + '\n')
        f.flush()
        if i % 5000 == 0: print(i, '/', len(todo), flush=True)
        time.sleep(0.3)
print('done', len(qids))
