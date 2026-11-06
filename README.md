# 살아볼길 Android 0.1.0

이사 후보지에서 평소의 출근·병원·장보기를 직접 해보고, 사전 예상과 현장 기록을 비교하는 Android 앱이다. 영상이나 웹뷰를 감싼 앱이 아니라 Java와 Android 네이티브 화면으로 구현했다.

## 이번 버전에서 할 수 있는 일

- 주당 출근·장보기 횟수, 병원까지 감당할 수 있는 이동시간을 설정한다.
- 후보 A/B의 이름·주거지 메모와 출근·병원·마트 목적지 좌표를 입력한다.
- 사전 예상 이동시간에 출처·확인 시점·가정을 함께 남긴다. 근거가 없으면 예상은 미확인으로 표시한다.
- 과제를 선택하고 확인한 뒤 내비 안내를 요청한다. 도착 알림과 실제 시설 이용 여부를 별도로 처리한다.
- 주차 후 소요시간, 이용 가능·불가·미확인, 주차와 접근 여건, 메모를 저장한다.
- 예상과 기록의 차이, 개인 기준 충족 여부, A/B의 같은 생활 항목을 비교한다. 미확인을 0점으로 바꾸거나 자동 순위를 매기지 않는다.
- 다음 과제 선택, 중단·재개, 건너뛰기, 앱 재시작 후 복구와 전체 삭제를 지원한다.

## 바로 확인하는 방법

`apk/살아볼길_살아볼길_release.apk`가 서명된 release APK다. Android 9(API 28) 이상에서 설치할 수 있다. 일반 폰에서는 시연 모드를, Pleos Connect에서는 시연 모드와 실제 내비 연결을 확인할 수 있다.

시연 확인 순서:

1. 시작 화면에서 서비스 목적과 후보 A/B를 확인한다.
2. `오늘 체험` 탭(폰에서는 `체험`)에서 A의 병원 체험을 시작한다.
3. `도착 알림 재현`을 눌러도 시설 이용은 미확인으로 남는다.
4. `주차 후 기록 상태 재현`을 누른 뒤 현장 기록에 18분을 입력하고 이용 여부를 선택해 저장한다.
5. 예상 12분 대비 +6분과 개인 기준 15분 초과를 확인한다.
6. `후보 비교` 탭(폰에서는 `비교`)에서 A 병원과 B 병원을 나란히 본다. B의 현장 결과는 미확인이다.
7. 다음 과제를 선택하거나 앱을 종료·재실행해 저장한 기록을 확인한다.

시연 후보지·좌표·예상시간은 가상이다. 시연 모드에서 입력한 내용은 실제 측정 결과로 표시하지 않는다. 시연 시작은 실제 내비를 호출하지 않는다.

## 실제 Pleos 연결

설정에서 모드를 전환하면 기존 데이터를 삭제한다는 확인을 받는다. 실제 모드는 빈 후보지와 목적지로 시작하므로 직접 이름·좌표·출발지 메모를 입력한다. 경로는 차량의 현재 위치에서 요청한다. 앱이 후보 주거지의 위치를 자동 검증하거나 주거지 메모를 좌표로 변환하지 않으므로 출발 장소를 직접 확인해야 한다.

NaviHelper 3.0.5를 사용해 실제 경로 요청과 이벤트를 처리한다. 현재 위치 조회·지도 검색·예상시간 자동 조회 기능은 구현하지 않았다. 예상시간은 사용자가 근거와 함께 입력한다. 소요시간도 사용자가 확인해 기록하며 자동 측정값으로 주장하지 않는다.

실제 차량 UX 상태가 미확인이거나 제한 중이면 상세 입력과 비교를 차단한다. 제한 해제만으로 주차했다고 판단하지 않고 사용자의 별도 확인을 받는다. AAOS에서는 시연 모드도 실제 플랫폼 UX 제한을 우회하지 못한다. 경로가 자동으로 다음 목적지로 전환되지는 않는다.

## 빌드

JDK 17, Android SDK 35와 build-tools 35.0.0이 필요하다. AGP 8.9.2 / Gradle 8.11.1 / compileSdk 35 / targetSdk 34 / minSdk 28을 사용한다. `scripts/env.example.sh`를 `scripts/env.sh`로 복사해 이 PC의 JDK·SDK 경로를 맞추거나 `JAVA_HOME`, `ANDROID_HOME`을 직접 설정한다. `env.sh`는 기기별 설정이므로 저장소에서 제외했다.

```sh
cp scripts/env.example.sh scripts/env.sh
# scripts/env.sh의 JDK·SDK 경로를 자신의 환경에 맞춘다.
source scripts/env.sh
./gradlew :app:assembleDebug :app:lintDebug
./scripts/build-release.sh
```

릴리즈 스크립트는 서명 키를 프로젝트 밖의 `~/.local/share/salabolgil/signing/`에 만들고 재사용한다. 키와 비밀번호 파일은 소스·APK 패키지에 포함하지 않는다. 기존 설치본을 업데이트하려면 같은 키를 보관해야 한다. 다른 개발 PC에서 새 키로 빌드하면 기존 APK와 서명이 달라진다.

현재 Pleos 에뮬레이터 설치·실행 예시:

```sh
adb -s emulator-5554 shell am get-current-user
# 위 명령에서 확인한 사용자 ID를 사용한다. 이번 검증은 10이었다.
adb -s emulator-5554 install -r --user 10 apk/살아볼길_살아볼길_release.apk
adb -s emulator-5554 shell am start --user 10 -n kr.salabolgil.app/.MainActivity
```

## 검증과 현재 범위

순수 Java 상태엔진 테스트 실행법은 [DOMAIN-TESTS.md](docs/DOMAIN-TESTS.md), 실제 환경·SDK의 확인 범위는 [ENVIRONMENT.md](docs/ENVIRONMENT.md), [PLEOS.md](docs/PLEOS.md), 최종 결과는 [QA.md](QA.md)에 있다. 생활 조건과 기록은 앱 내부 저장소에만 보관하고 자동 백업·기기 전송에서 제외했다. 자세한 내용은 [개인정보 안내](docs/PRIVACY.md)를 따른다.

실차 검증, 실제 이주 준비자의 사용자 평가, 검증된 지역 시설 데이터 확보, 장시간 주행·배터리·제조사별 검증은 아직 하지 않았다. 모든 앱마켓 심사 요건을 충족한 정식 배포판으로 주장하지 않는다. 영상의 AI 차량 재현과 실제 Android 앱 실행 결과는 별도 산출물이다.

소스와 APK는 [제출용 공개 저장소](https://github.com/jonnaalive/salabolgil-android)에 함께 보관했다. 공모전 최종 제출은 참가자가 직접 진행한다.

## 공식 참고 자료

- [공모전 제출 안내](https://appdevchallenge.auto-hmg.io/guide/submission)
- [Pleos 개발 환경](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [NaviHelper SDK 가져오기](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk)
- [차량 UX 제한 연동](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
