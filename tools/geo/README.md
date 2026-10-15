# Offline base map

Natural-feature labels are bundled separately in `assets/geo/nature.json`. Chinese name corrections,
sources, remaining source ambiguities and regeneration instructions are in
[`design/geo/nature/README.md`](../../design/geo/nature/README.md).

`app/src/main/assets/basemap.bin` is built from [Natural Earth](https://www.naturalearthdata.com/) 1:10m data
(public domain — no attribution required). It lets the trip map show land, sea, lakes, rivers, borders,
major roads and city points. Each city point carries its GeoNames ID; the app labels it with the same city's name from `assets/geo/cities.db` in the user's language, so map labels match place names (the stored `NAME_KO`/`NAME_EN` are only a fallback) without any network access.

Data simplified to ~300 m and clipped into 2° cells; the app decodes only the cells around the day it shows.

## Regenerate

```sh
B=https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson
for l in ne_10m_land ne_10m_lakes ne_10m_rivers_lake_centerlines ne_10m_admin_0_boundary_lines_land \
         ne_10m_admin_1_states_provinces_lines ne_10m_roads ne_10m_populated_places; do
  curl -sfLO $B/$l.geojson
done
pip install shapely
python build_basemap.py . ../../app/src/main/assets/basemap.bin
```
