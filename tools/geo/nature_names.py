"""Build-time Chinese name corrections; no network or runtime dependencies in the app."""
import csv
from pathlib import Path

from opencc import OpenCC


OVERRIDES = Path(__file__).resolve().parents[2] / 'design/geo/nature/name_overrides.tsv'
# Keep ordinary transliteration characters when OpenCC picks a semantic variant.
KEEP = str.maketrans('裏裡託佈採誇幹剋峯羣', '里里托布采夸干克峰群')


class NatureNames:
    def __init__(self, overrides=OVERRIDES):
        self.t2s, self.s2t = OpenCC('t2s'), OpenCC('s2t')
        with open(overrides, encoding='utf-8', newline='') as fp:
            rows = list(csv.DictReader(fp, delimiter='\t'))
        self.overrides = {r['ne_id']: r for r in rows}
        if len(self.overrides) != len(rows):
            raise ValueError('Duplicate Natural Earth ID in name overrides')
        self.seen = set()
        self.audit = []

    def names(self, properties):
        p = {k.lower(): v for k, v in properties.items()}
        get = lambda key: str(p.get(key) or '').strip()
        source_name, ne_id = get('name'), get('ne_id')
        out = {'en': get('name_en') or source_name.title()}
        for lang in ('ko', 'ja', 'zh', 'zht'):
            if value := get('name_' + lang):
                out[lang] = value
        original = {lang: out.get(lang, '') for lang in ('zh', 'zht')}
        # Each script keeps its own regional wording; only fill the other if absent.
        if value := out.get('zh') or out.get('zht'):
            out['zh'] = self.t2s.convert(value)
        if value := out.get('zht') or original['zh']:
            out['zht'] = self.s2t.convert(value).translate(KEEP)
        correction = self.overrides.get(ne_id)
        if correction:
            # IDs distinguish same-name features, even when the upstream Wikidata link is wrong.
            if source_name != correction['source_name']:
                raise ValueError(f'Upstream name changed for {ne_id}: {source_name!r}')
            out.update({lang: correction[lang] for lang in ('zh', 'zht')})
            self.seen.add(ne_id)
        changed = any(out.get(lang, '') != original[lang] for lang in original)
        status = ('manual-correction' if correction else 'script-normalized' if changed
                  else 'upstream-unchanged' if original['zh'] or original['zht'] else 'missing-chinese')
        self.audit.append(dict(ne_id=ne_id, source_name=source_name, en=out['en'],
                               old_zh=original['zh'], old_zht=original['zht'],
                               zh=out.get('zh', ''), zht=out.get('zht', ''), status=status,
                               sources=correction['sources'] if correction else '',
                               reason=correction['reason'] if correction else ''))
        return out

    def validate(self):
        if unused := self.overrides.keys() - self.seen:
            raise ValueError(f'Unused Natural Earth overrides: {sorted(unused)}')

    def write_audit(self, path):
        with open(path, 'w', encoding='utf-8', newline='') as fp:
            writer = csv.DictWriter(fp, fieldnames=list(self.audit[0]), delimiter='\t',
                                    quoting=csv.QUOTE_ALL, lineterminator='\n')
            writer.writeheader()
            writer.writerows(self.audit)
