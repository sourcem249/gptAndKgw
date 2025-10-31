# WeatherApp

간단한 Jetpack Compose 기반의 안드로이드 날씨 애플리케이션입니다. 사용자가 도시 이름이나 위도/경도를 입력하면 [Open-Meteo](https://open-meteo.com/) API를 이용해 현재 기상 정보를 가져옵니다.

## 주요 기능

- 도시 이름 또는 "37.57,126.98"과 같은 위도/경도 좌표로 검색
- 현재 기온, 습도, 풍속, 하늘 상태 표시
- 마지막 업데이트 시각 표시
- Material 3 디자인과 Jetpack Compose 기반 UI

## 필요 조건

- Android Studio Iguana 이상
- Android SDK 34
- 인터넷 연결 (API 호출용)

## 실행 방법

1. 저장소를 클론하고 Android Studio로 엽니다.
2. 필요 시 `gradle-wrapper.jar`를 추가하려면 로컬 환경에서 `./gradlew wrapper`를 실행해 생성한 파일을 `gradle/wrapper` 폴더에 복사하세요.
3. 앱을 실행하면 기본 도시(Seoul)의 날씨가 로드됩니다.
4. 검색창에 새로운 도시 이름 또는 위도/경도 좌표를 입력하고 **검색** 버튼을 눌러 최신 정보를 확인하세요.

## 주의 사항

- Open-Meteo API는 인증 키가 필요 없지만, 과도한 호출은 제한될 수 있습니다.
- 에뮬레이터/실기기에서 인터넷 권한이 허용되어야 합니다.
- 저장소에는 Gradle Wrapper JAR가 포함되어 있지 않습니다. 필요 시 위 실행 방법을 참고해 추가하세요.
