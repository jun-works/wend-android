# Bundled fonts

`app/src/main/res/font/wend_*.ttf` are modified versions of:

- Gowun Batang (`wend_batang_*`) — https://github.com/google/fonts/tree/main/ofl/gowunbatang
- IBM Plex Sans KR (`wend_sans_kr_*`) — https://github.com/google/fonts/tree/main/ofl/ibmplexsanskr

Both are licensed under the SIL Open Font License 1.1 (see `OFL-*.txt`).

## Why modified

Android never makes a text line shorter than the font's vertical extent (Compose hardcodes
`fallbackLineSpacing = true`). These fonts are 1.448em / 1.5em tall, so any `lineHeight`
below that (the design uses 1.0–1.32 for headings and labels) was silently enlarged and the
layout drifted from the prototype.

`css_metrics.py` sets the vertical extent to exactly 1em while keeping the glyph center where
CSS puts it, so `lineHeight` + `LineHeightStyle(Center, Trim.None)` lays text out like CSS
`line-height`. Glyph outlines are untouched. The family is renamed (`Wend Batang`,
`Wend Sans KR`) because IBM Plex has the Reserved Font Name "Plex".

## Regenerate

```sh
pip install fonttools
python css_metrics.py GowunBatang-Regular.ttf wend_batang_regular.ttf "Gowun Batang" "Wend Batang" GowunBatang WendBatang
python css_metrics.py IBMPlexSansKR-Medium.ttf wend_sans_kr_medium.ttf "IBM Plex Sans KR" "Wend Sans KR" IBMPlexSansKR WendSansKR
```
