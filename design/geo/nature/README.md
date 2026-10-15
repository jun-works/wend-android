# 자연 지명 중국어 검수 (2026-10-15)

대상은 앱에 포함된 Natural Earth 자연 지명 1,966개다. 전체 이름 목록을 점검한 뒤,
의심 항목을 원본 피처 이름·분류·좌표, 정부·사전·현지 관광청 자료와 대조했다.
간체/번체가 가리키는 장소가 다르면 맞는 쪽의 원본 이름도 수정 근거로 사용했다.
출처의 권위가 모두 같지는 않으며, 이 기록은 모든 지명의 공식 표기를 인증한 결과가 아니다.

- `name_overrides.tsv`: 26개 피처의 수동 수정. Natural Earth `NE_ID`로 식별하고 원본 `NAME`을 확인한다.
  영문명이나 Wikidata ID만으로 교체하지 않는다. 과들루프와 세인트빈센트의 La Soufrière는
  원본 영문명과 Wikidata ID가 같지만 실제로 다른 화산이다.
- `review.tsv`: 전체 1,966개의 원본 간체/번체, 최종 표시명, 변경 종류, 수동 수정 근거.
  `manual-correction` 26개, `script-normalized` 890개, `upstream-unchanged` 970개,
  `missing-chinese` 80개. 총 916개 피처의 939개 언어 필드가 변경되었다.
- `script-normalized`는 OpenCC로 자형을 정리했다는 뜻이다. 의미나 표준 지명이 확인되었다는 뜻은 아니다.
  간체/번체 각각의 기존 지역별 이름을 유지하고, 한쪽이 비어 있을 때만 다른 쪽에서 보충한다.
  음역의 里·托·干·峰 등은 의미에 따른 이체자로 바뀌지 않도록 한다.
- `upstream-unchanged`도 검증 완료라는 뜻이 아니다. 이번 검토에서 수동 수정을 확정하지 않은 원본 이름이다.

대표 수정은 고비 `大漠 → 戈壁`, 리아우 군도 `廖内拱门 → 廖内群岛`,
메르귀 군도 `迈耶克拱门 → 丹老群岛`, 북섬 화산고원 `北爱尔兰火山高地 → 北岛火山高原`,
울루루의 국립공원 이름을 바위 이름 `乌鲁鲁 / 烏魯魯`로 바꾼 것이다.
`大漠`은 사전에도 나오는 이명이지만, 지도 고유 지명에는 사용자 요청에 따라 `戈壁`를 쓴다.

## 남은 원본 한계

중국어가 없는 80개는 이름을 새로 만들지 않았다. 이 중 대부분은 원본 영문 이름도 비어 있다
(전체 영문 공백 78개). 현재 앱은 사용자 언어가 없으면 영어를 선택한다.

원본의 영문명·분류·Wikidata 링크·좌표가 모순되는 사례도 있다. 예를 들어:

- `1730070769`: Jabal as Sawdä’지만 위치는 리비아이고 분류는 Desert다. 산 이름과 사막 이름이 섞여 있다.
- `1730072703`: 원본 NAME은 Southern Ghats, 영문은 Western Ghats다. 중국어 南高止를 영어만 보고 西高止로 바꾸지 않았다.
- `1159104223`: 원본 NAME은 GANGES PLAIN, 영문은 Indo-Gangetic Plain이다. 중국어 中央平原은 더 구체적인 지명 확인이 필요하다.
- `1730072595` / `1159104047`: Guiana Highlands / GUIANA SHIELD의 영문 현지화가 같아 고지와 지질학적 지괴가 혼동된다.
- `1730072149`: North-Estonian Coastal Plain이라는 영문과 원본 Baltic Plains의 범위가 다르다.
- `1730072927`: Laurentides의 간체는 산맥, 번체는 평원이다. 원본 분류와 지리적 범위의 추가 확인이 필요하다.
- `1730071967`: Jabal Abyad Plateau의 간체 阿比让高原은 의심스럽지만, 확정할 근거가 부족하다.

이 항목들은 이번에 표기를 확정하지 않았다. 앱의 지형·좌표·한국어·일본어·영문 데이터는 변경하지 않았다.

## 재생성 및 확인

빌드 도구에만 `opencc-python-reimplemented==0.1.7`이 필요하다. Android 앱에는 Python/OpenCC나
조회용 네트워크를 추가하지 않는다. `nature.json`을 APK에 포함하며, 앱 실행 시 로컬 asset을 읽는다.

```sh
python3 -m pip install opencc-python-reimplemented==0.1.7
python3 tools/geo/build_nature.py <Natural-Earth-geojson-dir> app/src/main/assets/geo/nature.json --audit design/geo/nature/review.tsv
python3 -m unittest discover -s tools/geo -p 'test_*.py'
./gradlew :app:assembleDebug
```

원본 파일은 `ne_10m_geography_regions_polys.geojson`, `ne_10m_geography_marine_polys.geojson`,
`ne_10m_geography_regions_elevation_points.geojson`이다. 수동 수정의 ID나 원본 이름이 바뀌면
재생성이 오류로 종료되어 검수를 요구한다. 원본 라이선스는 Natural Earth 퍼블릭 도메인이다.
