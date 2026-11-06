# Android 개발·검증 환경

기준일: 2026-11-06. 설치는 이 Mac의 로컬 SSD에만 수행했다. 셸 프로필이나 전역 JAVA_HOME은 바꾸지 않았다.

## 빌드 도구

- macOS arm64, Hypervisor.Framework 사용 가능(`emulator -accel-check` exit 0)
- Eclipse Temurin OpenJDK 17.0.21+9: `/Users/user/.cache/salabolgil-tools/jdk-17.0.21+9/Contents/Home`
- Gradle 8.11.1: `/Users/user/.cache/salabolgil-tools/gradle-8.11.1`
- Android SDK: `/Users/user/Library/Android/sdk`
- Android command-line tools 19.0
- SDK platforms: Android 34 revision 3, Android 35 revision 2
- SDK Build Tools: 35.0.0
- Platform Tools: 37.0.1 (ADB protocol 1.0.41)
- Android Emulator: 37.2.13.0 build 16740729

프로젝트 루트에서 다음을 실행한다.

```sh
source scripts/env.sh
java -version
gradle --version
sdkmanager --list_installed
```

## 설치 경로와 라이선스

공식 Adoptium 배포 API, Gradle 배포 서버, Google Android SDK 다운로드 서버에서 도구를 받았다. 다운로드 캐시는 `/Users/user/.cache/salabolgil-tools/`에 있다. `sdkmanager --licenses`로 설치에 필요한 Android SDK 라이선스를 수락했으며 로그는 캐시의 `android-licenses.log`에 있다. SDK 설치 로그는 `android-install.log`다. 다운로드 파일과 SDK 자체는 앱 소스나 배포 ZIP에 포함하지 않는다.

## Pleos 에뮬레이터

공식 문서에 명시된 Mac arm64 시스템 이미지 저장소에 로그인 없이 접근할 수 있었다.

- 공식 설치 안내: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- 저장소 메타데이터: https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml
- 이미지: https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp_car_arm64_emulator.zip
- 패키지: `system-images;android-34;pleos_car_emulator;arm64-v8a`, revision 2, Pleos Connect v2.1.0
- 이미지 크기 4,669,762,816 bytes, 공식 SHA-1 `24d28eb2f18eba5e87c861a7eb2bdccebb049419`

다운로드 SHA-1이 공식 값과 일치함을 확인했다. ZIP을 Android SDK 시스템 이미지 폴더에 설치했다. 원격 패키지 이름은 `pleos_car_emulator`지만 ZIP의 `source.properties`에는 `android-automotive` 태그가 있어서 avdmanager가 인식하는 실제 로컬 패키지 ID는 `system-images;android-34;android-automotive;arm64-v8a`다. 로컬 설치 경로는 이에 맞춰 `system-images/android-34/android-automotive/arm64-v8a/`로 정리했다. SDK 바이너리는 수정하지 않았다. 이미지 안의 공식 `devices.xml`을 Android 기기 프로필에 등록했다. Pleos SDK 라이선스는 메타데이터에 포함돼 있으며 앱 개발을 위한 설치·사용 범위로 수락했다.

AVD 이름은 `Salabolgil_Pleos_34`, 공식 Pleos 기기 프로필 `Pleos_Connect_v2_0`, 화면 2560x1440, RAM 4096MB, 4코어, host GPU다. 시작 명령은 다음과 같다.

```sh
source scripts/env.sh
emulator -avd Salabolgil_Pleos_34 -no-snapshot -no-audio -no-window -gpu host -memory 4096 -cores 4
```

로그는 `/Users/user/.cache/salabolgil-tools/pleos-emulator.log`에 있다. 에뮬레이터에서 동작하는 것은 실차 검증을 뜻하지 않는다.

### 실제 부팅 확인

- ADB serial: `emulator-5554`, 연결 상태 `device`
- `getprop sys.boot_completed`: `1`
- `ro.build.fingerprint`: `Pleos/crp_avd_arm64/crp_avd_arm64:14/RELEASE.release_connect_v2.1.0/release_connect_v2.1.0:user/test-keys`
- 실제 화면: 2560x1440, density 160
- 내비 서비스 패키지 `ai.pleos.playground.service.navi`와 지도 앱 `ai.umos.maps.android.navigation.app` 설치 확인
- `dumpsys car_service --services CarUxRestrictionsManagerService`: Display 0에 `DO: false UxR: 0` 확인. UX 제한 해제는 주차 확인과 별개다.

에뮬레이터는 앱 설치·실행 검증을 위해 실행 상태로 유지했다. 이 환경 확인만으로 내비 SDK 호출 성공, 앱 기능 성공, 실차 안전성이 검증된 것은 아니다. 해당 결과는 앱 QA 문서에 별도로 기록한다.
