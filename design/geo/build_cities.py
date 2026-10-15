#!/usr/bin/env python3
"""Build geo/cities.tsv.gz — offline city lookup data for Wend (coords → city name in en/ko/ja/zh).

Name priority per language: manual_names.tsv > Wikidata label > GeoNames alternate name.
Simplified/Traditional Chinese fill each other through OpenCC when only one exists.

Usage (steps in geo/README.md):
  python3 geo/build_cities.py --src <dir with cities1000.txt, alternateNamesV2.txt, wd_labels.jsonl, p1566*.tsv>
  --report  prints coverage only
"""
import argparse, collections, csv, glob, gzip, json, os, re, sys

from opencc import OpenCC  # pip install opencc-python-reimplemented

HERE = os.path.dirname(os.path.abspath(__file__))
LANGS = ['en', 'ko', 'ja', 'zh_hans', 'zh_hant']
_S2T, T2S = OpenCC('s2t'), OpenCC('t2s')


# OpenCC picks the "meaning" character, but transliterations keep the plain one: 里约 not 裏約, 索伦托 → 索倫托 not 索倫託,
# 采尔马特 → 采爾馬特 not 採爾馬特, 赤峰 not 赤峯, 群山 not 羣山
S2T_KEEP = str.maketrans('裏裡託佈採誇幹剋鹹峯羣', '里里托布采夸干克咸峰群')  # not 巖: 龍巖 is right


class S2T:
    @staticmethod
    def convert(s): return _S2T.convert(s).translate(S2T_KEEP)
# Traditional → Japanese shinjitai for characters common in Chinese place names (Japanese writes them in kanji)
T2JP = str.maketrans('萬樓樂廣龍灣縣區關鄉莊營澤濱齊鐵寶圓黃豐舊雙單戰國學會濟鹽邊縱櫻壽將輕來淺嶽變團擴廳屬條實廠滿彌',
                     '万楼楽広竜湾県区関郷荘営沢浜斉鉄宝円黄豊旧双単戦国学会済塩辺縦桜寿将軽来浅岳変団拡庁属条実廠満弥')
CHINESE_CC = ('CN', 'TW', 'HK', 'MO')


def load_cities(src):
    cities = {}
    for line in open(os.path.join(src, 'cities1000.txt'), encoding='utf-8'):
        c = line.rstrip('\n').split('\t')
        cities[c[0]] = dict(name=c[1], lat=float(c[4]), lon=float(c[5]), cc=c[8], a1=c[10], pop=int(c[14] or 0))
    return cities


def load_geonames_names(src, cities):
    """GeoNames alternate names → {gid: {lang: name}} plus {gid: wikidata qid}."""
    keymap = {'en': 'en', 'ko': 'ko', 'ja': 'ja', 'zh-CN': 'zh_hans', 'zh-Hans': 'zh_hans', 'zh-SG': 'zh_hans',
              'zh-TW': 'zh_hant', 'zh-Hant': 'zh_hant', 'zh-HK': 'zh_hant', 'zh-MO': 'zh_hant', 'zh': 'zh'}
    names, qid = collections.defaultdict(dict), {}
    for line in open(os.path.join(src, 'alternateNamesV2.txt'), encoding='utf-8'):
        c = line.rstrip('\n').split('\t')
        gid, lang = c[1], c[2]
        if gid not in cities: continue
        if lang == 'wkdt': qid[gid] = c[3]; continue
        k = keymap.get(lang)
        if not k or c[6] == '1' or c[7] == '1': continue  # colloquial / historic
        if k not in names[gid] or c[4] == '1': names[gid][k] = c[3]
    return names, qid


def load_p1566(src):
    """Wikidata items found by GeoNames ID (find_by_geonames_id.py), from every <src>/p1566*.tsv."""
    out = {}
    for p in sorted(glob.glob(os.path.join(src, 'p1566*.tsv'))):
        out.update(l.rstrip('\n').split('\t') for l in open(p))
    return out


def load_wikidata(src):
    wd = {}
    p = os.path.join(src, 'wd_labels.jsonl')
    if os.path.exists(p):
        for line in open(p, encoding='utf-8'):
            r = json.loads(line); wd[r['id']] = r['labels']
    return wd


def load_manual():
    manual = {}
    p = os.path.join(HERE, 'manual_names.tsv')
    if os.path.exists(p):
        for r in csv.DictReader(open(p, encoding='utf-8'), delimiter='\t'):
            # the same id may appear on several rows (one per fix) — merge them, later non-empty columns win
            manual.setdefault(r['geonameid'], {}).update({k: r[k].strip() for k in LANGS if r.get(k, '').strip()})
    return manual


def first(*vals):
    for v in vals:
        if v: return v
    return ''


def clean(lang, name, cc, ja_raw, zh_raw):
    """Drop administrative suffixes travellers don't say: 교토 시 → 교토, 箱根町 → 箱根, Jeju City → Jeju."""
    if not name: return name
    n = re.sub(r'\s*[(（][^)）]*[)）]$', '', name).strip()
    jp, east = cc == 'JP', cc in ('JP', 'KR', 'CN', 'TW')
    city_kanji = ja_raw.endswith('市') or zh_raw.endswith('市')
    if lang == 'en':
        n = re.sub(r'^Immediate Geographic Region of ', '', n)
        n = re.sub(r' (Municipality|Municipal Okrug|District|district|city|governorate|Governorate|Canton|Sadar Upazila'
                   r'|Upazila|Tehsil|Thana|Vidhan Sabha constituency|constituency|Department)$', '', n)
        if cc in ('CN', 'TW'): n = re.sub(r' (Town|Township)$', '', n)
    if lang == 'en' and east:
        if cc == 'CN': n = re.sub(r', .*$', '', n)  # "Yicheng Subdistrict, Yixing"
        n = re.sub(r'(?: City| County| Prefecture| District| Subdistrict| New Area|-shi|-si|-gun|-ku|-machi|-cho|-mura|-son)$', '', n)
    if lang == 'en' and cc == 'KP':
        n = re.sub(r'(?:-si|-ŭp|-up|-ni|-gun|-ku)$', '', n)  # Chŏngju-si → Chŏngju
    elif lang == 'ja':
        n = re.sub(r'^テーサバーン(ナコーン|ムアン|タムボン)・', '', n)  # Thai municipality prefix
        n = re.sub(r'^(.{2,}?)地区$', r'\1', n)
        # ソウル特別市 → ソウル, 釜山広域市 → 釜山 (꼬리표 전체를 뗀다)
        m = re.match(r'^(.{2,}?)(特別自治市|特別市|広域市|廣域市|特級市|市)$', n) or (jp and re.match(r'^(.{2,})[町村]$', n)) \
            or (east and not re.search(r'(第?[0-9一二三四五六七八九十百]+|社)区$', n) and re.match(r'^(.{2,}?)(鎮街道|街道|新区|区)$', n)) \
            or (east and not jp and re.match(r'^(.{2,}?)[県郡]$', n)) \
            or re.match(r'^([ァ-ヴー・＝]{2,})[郡県区]$', n)  # ブルックリン区 → ブルックリン
        if m: n = m.group(1)
    elif lang in ('zh_hans', 'zh_hant'):
        # 巴黎十五区 · 布达佩斯第十四区 keep 区 (the number alone is not a name); 社区 is a community, not a suffix
        keep_district = re.search(r'(第?[0-9一二三四五六七八九十百]+|社)[区區]$', n)
        m = (not keep_district and re.match(r'^(.{2,}?)(特别自治市|特別自治市|特别市|特別市|广域市|廣域市|特级市|特級市|镇街道|鎮街道|街道|新区|新區|市|县|縣|区|區)$', n)) \
            or (jp and re.match(r'^(.{2,})[町村]$', n))
        if m: n = m.group(1)
    elif lang == 'ko':
        # 서울특별시 → 서울, 부산광역시 → 부산 (꼬리표 전체를 뗀다 — '시'만 떼면 '서울특별'이 남음)
        if cc == 'KR': m = re.match(r'^(.{2,}?)\s?(특별자치시|특별자치도|특별시|광역시|[시군구읍면])$', n)
        elif jp and ja_raw.endswith('町'): m = re.match(r'^(.{2,}?)\s?(정|마치|초)$', n)
        elif jp and ja_raw.endswith('村'): m = re.match(r'^(.{2,}?)\s?(촌|무라|손)$', n)
        elif city_kanji or east: m = re.match(r'^(.{2,}?)\s?시$', n)
        else: m = re.match(r'^(.{2,}?) 시$', n)
        if m: n = m.group(1)
    return n


def build(src):
    cities = load_cities(src)
    gn, qid = load_geonames_names(src, cities)
    wd, manual, p1566 = load_wikidata(src), load_manual(), load_p1566(src)
    rows, srcs = [], collections.Counter()
    for gid, c in cities.items():
        g, m = gn.get(gid, {}), manual.get(gid, {})
        w = {**wd.get(p1566.get(gid), {}), **wd.get(qid.get(gid), {})}  # GeoNames' own link wins, P1566 fills the rest
        raw = {
            'en': first(w.get('en'), g.get('en'), c['name']),
            'ko': first(w.get('ko'), g.get('ko')),
            'ja': first(w.get('ja'), g.get('ja')),
            'zh_hans': first(w.get('zh-hans'), w.get('zh-cn'), g.get('zh_hans')),
            # foreign places: the Taiwan name first (雪梨, not 悉尼 in traditional); Chinese places keep the plain traditional form
            'zh_hant': first(w.get('zh-hant'), w.get('zh-tw'), w.get('zh-hk'), g.get('zh_hant')) if c['cc'] in CHINESE_CC
            else first(w.get('zh-tw'), w.get('zh-hant'), w.get('zh-hk'), g.get('zh_hant')),
        }
        for k, v in m.items(): raw[k] = v  # manual names are final and already clean; zh_hant may still be derived
        # a manual name in one script replaces the (often wrong-place) automatic name in the other script too
        if 'zh_hans' in m and 'zh_hant' not in m: raw['zh_hant'] = S2T.convert(m['zh_hans']) if m['zh_hans'] != '-' else '-'
        if 'zh_hant' in m and 'zh_hans' not in m: raw['zh_hans'] = T2S.convert(m['zh_hant']) if m['zh_hant'] != '-' else '-'
        zh_any = first(w.get('zh'), g.get('zh'))
        if zh_any and not raw['zh_hans']: raw['zh_hans'] = T2S.convert(zh_any)
        if zh_any and not raw['zh_hant']: raw['zh_hant'] = S2T.convert(zh_any)
        if raw['zh_hans'] == '-': raw['zh_hant'] = '-'
        if raw['zh_hans'] and not raw['zh_hant']: raw['zh_hant'] = S2T.convert(raw['zh_hans'])
        if raw['zh_hant'] and not raw['zh_hans']: raw['zh_hans'] = T2S.convert(raw['zh_hant'])
        raw['zh_hant'] = raw['zh_hant'].replace('臺', '台').replace('裏', '里').replace('裡', '里')  # everyday Taiwan spelling; 里 in transliterations
        if not raw['ja'] and c['cc'] in CHINESE_CC and raw['zh_hant'] not in ('', '-'):
            raw['ja'] = raw['zh_hant'].translate(T2JP)
        out = {k: (raw[k] if k in m else clean(k, raw[k], c['cc'], raw['ja'], raw['zh_hans'])) for k in LANGS}
        out = {k: ('' if v == '-' else v) for k, v in out.items()}  # "-" in manual_names.tsv clears a wrong automatic name
        for k in LANGS:
            if out[k]: srcs[(k, 'manual' if k in m else 'auto')] += 1
        rows.append((gid, c, out, ''.join(k[:2] if k != 'zh_hant' else 'zt' for k in m)))
    return rows


def report(rows):
    tiers = [('인구 10만+', 100000), ('인구 1.5만+', 15000), ('인구 1천+ 전체', 0)]
    print('| 대상 | 곳 | ' + ' | '.join(LANGS) + ' |\n|---|---|' + '---|' * len(LANGS))
    for label, lo in tiers:
        sel = [r for r in rows if r[1]['pop'] >= lo]
        cov = [sum(1 for r in sel if r[2][k]) * 100 / len(sel) for k in LANGS]
        print(f'| {label} | {len(sel):,} | ' + ' | '.join(f'{v:.1f}%' for v in cov) + ' |')


def write(rows, path):
    with gzip.open(path, 'wt', encoding='utf-8', compresslevel=9) as f:
        f.write('\t'.join(['geonameid', 'lat', 'lon', 'cc', 'admin1', 'pop'] + LANGS + ['manual']) + '\n')
        for gid, c, out, man in sorted(rows, key=lambda r: int(r[0])):
            f.write('\t'.join([gid, f"{c['lat']:.4f}", f"{c['lon']:.4f}", c['cc'], c['a1'], str(c['pop'])]
                              + [out[k].replace('\t', ' ') for k in LANGS] + [man]) + '\n')


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--src', required=True)
    ap.add_argument('--report', action='store_true')
    a = ap.parse_args()
    rows = build(a.src)
    report(rows)
    if not a.report:
        out = os.path.join(HERE, 'cities.tsv.gz')
        write(rows, out)
        print(f'\nwrote {out} ({os.path.getsize(out) / 1e6:.1f}MB, {len(rows):,} cities)')
