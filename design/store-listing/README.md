# Wend — Play 스토어 등록정보

언어마다 txt 파일 하나(`<언어>.txt`)에 앱 이름·간단한 설명·자세한 설명을 함께 적는다. 항목 옆 괄호는 글자 수/제한(앱 이름 30 · 간단한 설명 80 · 자세한 설명 4,000). Play Console에는 직접 복사해 넣는다.

언어: `en`(기본) · `ko` · `ja` · `zh-CN` · `zh-TW` · `es` · `pt-BR`.

## 문구 원칙
- 여행을 나누는 기준은 쓰지 않음 (`TRIP_DETECTION.md` 비공개 정책).
- 봇 이름(틱/핀)은 임시라서 쓰지 않음.
- 약속은 시안과 같게: 사진은 휴대폰 밖으로 나가지 않음 · 직접 고르기 전에는 지우지 않음(휴지통 30일) · 데이터 지우기는 기기 사진을 건드리지 않음.

## 이미지
- 스크린샷: `<언어>/images/phoneScreenshots/` — 실제 앱 화면 + 상단 설명, 1080×1920, 언어당 5장(ko-KR · en-US · ja-JP · zh-CN · zh-TW · es-419 · pt-BR). 스페인어는 es-419 이미지를 Play Console 스페인어(스페인)에도 씁니다. 에뮬레이터 + 공개 라이선스 예시 사진으로 촬영.
- 전체 미리보기: `screenshots-preview.html` / `screenshots-preview.png`.
- 문구·촬영·재생성·언어 추가: `../../tools/store-screenshots/README.md`. 문구와 예시 여행 이름은 언어별 JSON으로 관리합니다.
- 그래픽 이미지: 1024×500. 글자 없는 `../play-store/feature-graphic-global*.png`를 모든 언어에 공통으로 씁니다(한국어용은 `feature-graphic-ko.png`).
- 아이콘: `<언어>/images/icon.png` — 512×512
