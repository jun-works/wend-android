# geo — 오프라인 도시 이름 데이터

사진 좌표 → 가장 가까운 도시 이름을 **기기 안에서** 찾기 위한 데이터. 좌표를 밖으로 보내지 않는다
("사진은 이 기기 안에서만 쓰여요" 약속).

## 결과물: `cities.tsv.gz`

GeoNames `cities1000` 기준 171,228곳(인구 1천 이상). gzip TSV, 첫 줄이 헤더.

| 열 | 내용 |
|---|---|
| `geonameid` | GeoNames ID |
| `lat`, `lon` | 좌표 (소수 4자리 ≈ 11m) |
| `cc`, `admin1` | 국가 코드, 1단계 행정구역 코드 |
| `pop` | 인구 |
| `en` | 영어 이름 (항상 있음) |
| `ko`, `ja`, `zh_hans`, `zh_hant` | 한국어 · 일본어 · 중국어 간체 · 번체 이름. 없으면 빈칸 |
| `manual` | 손으로 채운 언어 표시 (`ko`, `ja`, `zh`=간체, `zt`=번체, `en`) — 검수용 |

앱에서 이름 고르는 순서: **사용자 언어 → `en`**. 나라 이름은 이 파일이 아니라 Android `Locale.getDisplayCountry()`로.

### 언어별 채움 비율 (2026-10-15)

| 대상 | 곳 | ko | ja | zh |
|---|---|---|---|---|
| 인구 10만+ | 6,282 | 100% | 100% | 84% |
| 인구 1.5만+ | 34,181 | 41% | 58% | 61% |
| 인구 1천+ 전체 | 171,228 | 11% | 21% | 29% |

- 중국어는 정해진 한자 이름이 없는 비한자권 소도시를 일부러 비워 두었다. 지어낸 음역보다 영어가 낫다.
- 작은 도시는 영어로 나오는 경우가 많다. 여행 이름에는 큰 도시 이름이 주로 쓰이고, 필요하면 근처 큰 도시로 올려 보여 주는 방식을 앱에서 정한다.

## 이름의 출처와 우선순위

1. `manual_names.tsv` — 인구 10만+ 도시와 `travel_towns.tsv`(여행지 소도시) 중 빈칸을 손으로 채우고, 틀린 자동 이름을 고친 것. `-`는 "자동 이름이 틀려서 지움".
2. Wikidata 라벨 (CC0) — GeoNames의 Wikidata 링크, 없으면 Wikidata 쪽 GeoNames ID(P1566) 역조회.
3. GeoNames alternate names (CC BY 4.0).

자동 정리 (`build_cities.py`의 `clean()`):
- 행정 단위 꼬리표를 뗀다: 교토 시 → 교토, 箱根町 → 箱根, 嘉定镇街道 → 嘉定, Jeju City → Jeju, … District → ….
  특별시·광역시·특별자치시는 꼬리표 전체를 뗀다: 서울특별시 → 서울, 釜山広域市 → 釜山, 開城特級市 → 開城.
- 간체/번체 중 하나만 있으면 OpenCC로 다른 쪽을 만든다. 번체의 臺 → 台, 음역의 裏/裡 → 里.
- 중국·대만·홍콩·마카오 지명에 일본어가 없으면 번체를 일본 신자체로 바꿔 쓴다(萬→万, 廣→広 …).

## 라이선스

- GeoNames: **CC BY 4.0** → 앱 정보 화면에 "GeoNames (geonames.org), CC BY 4.0" 출처 표기 필요.
- Wikidata: CC0 (표기 의무 없음).

## 다시 만들기

`<src>`는 아무 작업 폴더(이 맥에서는 `~/workspace/geonames-src` — 원본·Wikidata 캐시·`pylib/`의 OpenCC가 들어 있음). GeoNames 원본(~1GB)은 저장소에 넣지 않는다.

```bash
pip install opencc-python-reimplemented
cd <src>
curl -O https://download.geonames.org/export/dump/cities1000.zip && unzip cities1000.zip
curl -O https://download.geonames.org/export/dump/alternateNamesV2.zip && unzip alternateNamesV2.zip alternateNamesV2.txt

python3 geo/extract_wkdt.py <src>               # → wkdt.tsv (GeoNames → Wikidata 링크)
python3 geo/fetch_wikidata.py <src>             # → wd_labels.jsonl (약 30분, 이어받기 가능)
python3 geo/list_gaps.py --src <src> > gaps.tsv
python3 geo/find_by_geonames_id.py gaps.tsv <src>/p1566_a.tsv   # 빈칸 도시의 Wikidata 역조회
python3 geo/fetch_wikidata.py <src>             # 새로 찾은 항목의 라벨
python3 geo/build_cities.py --src <src>         # → geo/cities.tsv.gz, 비율 표 출력
```

- 2026-10-15 빌드는 역조회를 인구 1.5만+ 빈칸 도시 전체로 넓혀서 돌렸다(`p1566_b.tsv`). `p1566*.tsv`는 모두 읽힌다.
- 받은 Wikidata 데이터는 `geo/cache/`에 두면 다시 받지 않아도 된다(git 제외).
- `build_cities.py --report`는 파일을 쓰지 않고 비율만 보여 준다.
- 앱에 넣을 때: `python3 tools/geo/build_cities_db.py design/geo/cities.tsv.gz app/src/main/assets/geo/cities.db`. 데이터가 바뀌면 그 스크립트의 `user_version`과 `CityDatabase`의 `version`을 같이 올린다(기기에 복사된 옛 DB를 새것으로 바꾸기 위해).
- 한국어 표기는 국립국어원 외래어 표기 용례(korean.go.kr/kornorms, 용례 찾기)를 따른다. 자동 이름이 용례와 다르면 `manual_names.tsv`에 용례 표기를 넣는다(예: 샌디에고 → 샌디에이고, 푸켓 → 푸껫). 용례에 없는 곳은 고치지 않는다. 용례도 바뀐다 — 울란바토르는 2026년 심의에서 울란바타르로 바뀌었다.
- 2026-10-15 검수: 여행지가 될 만한 약 1만 곳(Natural Earth 도시 · 인구 10만+ · travel_towns)을 en/ja/zh 위키백과 제목, Natural Earth, GeoNames와 대조해 고친 이름을 `manual_names.tsv`에 넣었다. 규칙으로 고친 것: 번체는 해외 지명이면 대만 이름(zh-tw) 우선, OpenCC가 음역 글자를 바꾸는 것(託·佈·採 …) 되돌림, 수동 간체/번체가 다른 쪽도 정함, 일본어 県·郡·가타카나+区 꼬리표, 영어 -shi/-si/-ku 꼬리표와 북한 -ŭp/-ni, 순번 구(巴黎十五区)와 社区는 区를 떼지 않음.
- 알려진 한계: GeoNames의 Wikidata 링크가 다른 장소(동네·역·고대 도시)를 가리키는 경우가 있다. 캐시에 좌표가 없어 자동으로 거르지 못하고, 검수 범위 밖의 작은 도시에는 남아 있을 수 있다.
