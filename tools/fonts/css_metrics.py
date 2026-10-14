"""Make a font's vertical extent 1em while keeping the glyph center where CSS puts it,
so Android (which never makes a line shorter than the font extent) lays text out like CSS line-height.
Renames the family (OFL: modified fonts must not use Reserved Font Names)."""
import sys
from fontTools.ttLib import TTFont

src, dst, old_family, new_family, old_ps, new_ps = sys.argv[1:7]
f = TTFont(src)
upm = f['head'].unitsPerEm
hhea, os2 = f['hhea'], f['OS/2']
center = (hhea.ascent + hhea.descent) / 2  # content-area center above baseline (descent is negative)
asc = round(center + upm / 2)
desc = upm - asc
hhea.ascent, hhea.descent, hhea.lineGap = asc, -desc, 0
os2.sTypoAscender, os2.sTypoDescender, os2.sTypoLineGap = asc, -desc, 0
os2.usWinAscent, os2.usWinDescent = asc, desc
for rec in f['name'].names:
    s = rec.toUnicode()
    t = s.replace(old_family, new_family).replace(old_ps, new_ps)
    if t != s:
        rec.string = t
f.save(dst)
print(dst.split('/')[-1], 'upm', upm, 'center', center, 'asc', asc, 'desc', desc)
