# Wend · Play 스토어 스크린샷

## 결과물
- `../../design/store-listing/{ko-KR,en-US}/images/phoneScreenshots/*.png`: 언어당 5장, 1080×1920, 상단 설명 + 하단 실제 앱 화면.
- `../../design/store-listing/screenshots-preview.html`: 전체 비교용 미리보기.
- `raw/<locale>/*.png`: 에뮬레이터 원본. UI를 다시 그리거나 번역 문구를 덮어씌우지 않습니다.

## 구성
- `locales/*.json`: 스토어 문구·강조색·화면 이동에 필요한 현지화 라벨.
- `render.cjs`: 공통 레이아웃. 첫 장은 휴대폰 사진에서 여행을 찾는 기능을 설명하고, 두 번째 장은 실제 목록 화면 속 지구본을 확대합니다. `source`는 원본 캡처 이름, `layout: "globe"`는 확대 레이아웃입니다. 앱에 포함된 Wend 글꼴을 사용하며 외부 폰트 요청은 없습니다.
- `capture.py`: 실제 앱의 목록 → 여행 상세 → 베스트샷 → 정리하기를 촬영합니다. 삭제 버튼은 누르지 않습니다.
- `androidTest/StoreFixtureTest.kt`: 테스트 사진과 여행 데이터를 넣습니다. 테스트용 날짜·위치·이름이며, 연사 예시를 위해 같은 사진을 복제합니다. 화질·유사도는 실제 앱 코드로 계산하고, 베스트샷은 예시 사용자 선택으로 저장합니다. 여행 자동 탐지 정확도를 검증하는 테스트는 아닙니다.
- `assets/sources.json`: 사진별 원문 페이지·작가·다운로드 주소·라이선스·SHA-256.

## 재생성
JDK 17 이상(프로젝트 Gradle에 맞는 JDK 권장), Android SDK/adb, API 33+ 에뮬레이터, Python 3, Node와 Playwright/Chromium이 필요합니다.

```sh
# 프로젝트 루트에서 실행. 물리 기기는 허용하지 않습니다.
./gradlew -PstoreScreenshots=true :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell pm grant app.wend.sea.screenshots android.permission.READ_MEDIA_IMAGES
adb -s emulator-5554 shell pm grant app.wend.sea.screenshots android.permission.READ_MEDIA_VIDEO
adb -s emulator-5554 shell pm grant app.wend.sea.screenshots android.permission.ACCESS_MEDIA_LOCATION
python3 tools/store-screenshots/capture.py --device emulator-5554
node tools/store-screenshots/render.cjs
```

Playwright가 다른 경로에 설치되어 있으면 `PLAYWRIGHT_MODULE=/absolute/path/to/playwright`를 지정합니다. 문구만 바꿀 때는 마지막 렌더 명령만 실행하면 됩니다. `node render.cjs ko-KR`처럼 특정 언어만 렌더할 수도 있습니다.

촬영 시 해상도는 2160×3840, 밀도는 720으로 잠시 바꿉니다(논리 화면 크기는 동일, 지구본 확대용 고해상도 원본). `--scale 1`이면 1080×1920/360으로 촬영합니다. 최종 출력은 1080×1920입니다. 촬영 후 `finally`에서 원래 설정으로 복원합니다. 중간 강제 종료 시 `adb -s emulator-5554 shell wm size reset`, `wm density reset`으로 복원할 수 있습니다.

## 다른 언어 추가
1. `locales/en-US.json`을 `<Play Console 언어 코드>.json`으로 복사합니다.
2. `locale`, 문구, 실제 앱 버튼 라벨을 번역합니다. 제목은 2줄, 설명은 최대 2줄입니다.
3. 같은 JSON의 `fixture` 안에 있는 예시 여행/장소 이름도 번역하고, 앱이 해당 언어를 지원하는지 확인합니다. Kotlin 수정은 필요 없습니다.
4. `python3 tools/store-screenshots/capture.py --device emulator-5554 --locales <코드>` 후 `node tools/store-screenshots/render.cjs`를 실행합니다.
5. 미리보기에서 줄바꿈·앱 언어·잘림·사진 로딩을 확인합니다. 렌더러는 설명과 화면의 겹침을 검사합니다.

## 안전한 분리
`-PstoreScreenshots=true`일 때만 debug 패키지가 `app.wend.sea.screenshots`로 분리됩니다. 실제 앱의 데이터나 사진은 건드리지 않습니다. Firebase 초기화도 촬영 빌드에서 제외됩니다. 예시 사진은 테스트 APK에만 포함되며 release APK에는 포함되지 않습니다. 일반 debug 빌드는 `app.wend.sea.dev`, release 빌드는 `app.wend.sea`입니다.

## 사진 사용과 규격
Unsplash 무료 라이선스 사진 4장만 사용합니다. 작가·원문 링크는 `assets/sources.json`에 보관했습니다. 인물이나 브랜드 중심 사진은 사용하지 않았습니다. 원본 사진을 그대로 상품으로 판매하거나 사진 서비스로 재배포하기 위한 자료가 아닙니다.
- 라이선스: https://unsplash.com/license
- Play 스토어 이미지 규격: https://support.google.com/googleplay/android-developer/answer/9866151?hl=en

사진을 다시 받을 때만 `python3 tools/store-screenshots/fetch_photos.py`를 실행하세요. 이미 내려받은 파일로는 네트워크 없이 재생성할 수 있습니다.
