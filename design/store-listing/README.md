# Wend — Play 스토어 등록정보

언어별 폴더에 항목마다 파일 하나. 폴더·파일 이름은 fastlane `supply` 형식을 따름 → 개발 후 `fastlane/metadata/android/`로 옮기면 그대로 업로드 가능.

| 파일 | 항목 | 글자 수 제한 |
|---|---|---|
| `title.txt` | 앱 이름 | 30 |
| `short_description.txt` | 간단한 설명 | 80 |
| `full_description.txt` | 자세한 설명 | 4,000 |

언어: `en-US`(기본) · `ko-KR`. 언어를 추가할 때는 Play Console 언어 코드로 폴더를 만든다.

## 문구 원칙
- 여행을 나누는 기준은 쓰지 않음 (`TRIP_DETECTION.md` 비공개 정책).
- 봇 이름(틱/핀)은 임시라서 쓰지 않음.
- 약속은 시안과 같게: 사진은 휴대폰 밖으로 나가지 않음 · 직접 고르기 전에는 지우지 않음(휴지통 30일) · 데이터 지우기는 기기 사진을 건드리지 않음.

## 이미지
- 스크린샷: `<언어>/images/phoneScreenshots/` — 실제 앱 화면 + 상단 설명, 1080×1920, 한국어·영어 각 5장. 에뮬레이터 + 공개 라이선스 예시 사진으로 촬영.
- 전체 미리보기: `screenshots-preview.html` / `screenshots-preview.png`.
- 문구·촬영·재생성·언어 추가: `../../tools/store-screenshots/README.md`. 문구와 예시 여행 이름은 언어별 JSON으로 관리합니다.
- 그래픽 이미지: `<언어>/images/featureGraphic.png` — 1024×500
- 아이콘: `<언어>/images/icon.png` — 512×512
