# Pleos 연동 범위와 검증 상태

확인일: 2026-11-06. 기획안의 이사 후보지 생활 체험을 실제 Android 앱으로 구현하는 초기 버전이다. 앱 빌드 성공, SDK 컴파일, 내비 서비스 연결, 경로 탐색 성공, 차량 UX 상태 수신, 실차 검증은 서로 다른 검증 단계다.

## 실제 사용하는 SDK

- Maven 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/`
- 직접 의존성: `ai.pleos.playground:NaviHelper:3.0.5`
- POM에 명시된 연계 버전: `navi-data:3.0.4`, `navi-aidl:3.0.1`, Kotlin stdlib 1.9.25.
- 2026-11-06 POM 및 NaviHelper/navi-data AAR을 인증 없이 다운로드할 수 있었고 실제 클래스 시그니처를 확인했다.
- Android API 34의 `optional/android.car.jar`에 포함된 Car 및 CarUxRestrictionsManager API도 `javap`로 확인했다. 이 라이브러리는 컴파일에 사용하고 기기의 Android Automotive가 런타임 구현을 제공한다.

필요한 앱 설정:

```groovy
// settings.gradle repositories
maven { url 'https://nexus-playground.pleos.ai/repository/maven-releases/' }
// app/build.gradle
android { useLibrary 'android.car' }
dependencies { implementation 'ai.pleos.playground:NaviHelper:3.0.5' }
```

```xml
<uses-permission android:name="pleos.car.permission.NAVI_ROUTE" />
<uses-permission android:name="pleos.car.permission.NAVI_ROUTE_SEARCH" />
<!-- application 안에 배치. 일반 Android에서는 이 라이브러리를 요구하지 않는다. -->
<uses-library android:name="android.car" android:required="false" />
```

위 권한 선언만으로 실제 서비스의 권한 허용이나 공모전 심사 통과를 보장하지 않는다. 앱에서 지도 화면을 요청하지 않으므로 `NAVI_CUSTOM_MAP`은 요구하지 않는다. 별도 차량 속도·기어·제어 권한도 요구하지 않는다.

## NavigationBridge의 동작

`connect()`는 Automotive 환경을 확인하고 NaviHelper 서비스 연결과 Car UX 제한 감지를 시작한다. 일반 Android에서는 내비 연동을 사용할 수 없다고 반환한다. 서비스 연결 성공 콜백에만 `onReady()`를 전달한다.

`navigate(name, latitude, longitude)`는 확인된 차량 UX 상태, 좌표 범위, 연결 여부를 확인한 뒤 실제 `requestRoute()`를 호출한다. 앱의 사용자 입력 이름은 유지하되 내비에 보내는 이름에는 요청마다 새 UUID를 붙인다. SDK 이벤트에는 앱 요청 ID가 없으므로 동일 이름의 A/B 시설과 이전 회차 이벤트를 구분하기 위한 값이다. 요청 함수가 반환됐다는 이유로 경로 성공을 처리하지 않는다. UUID를 포함한 이름 전체와 SDK `onRouteStarted` 이름이 정확히 일치할 때 `onRouteStarted()`를 앱에 전달한다. 내비가 이름을 바꾸거나 잘라 돌려주면 매칭하지 않는다. 30초 안에 해당 응답이 없으면 연결을 해제하고 다시 연결한 뒤 요청하도록 한다.

`onDestinationArrived`도 시작된 경로 및 UUID를 포함한 목적지 이름이 일치하는 경우에만 한 번 전달한다. 내비 도착은 시설 이용 확인이나 기록 저장이 아니다. 이벤트가 없거나 일치 여부를 확정할 수 없으면 주차 후 사용자가 수동 확인해야 한다. 단계별 실제 기록의 중복 적용 방지는 앱 상태에서도 수행해야 한다. SDK 연결 세대와 요청 세대 토큰을 main-thread 이벤트 처리 시 다시 확인해 취소·재연결 전에 큐에 들어온 이벤트도 무시한다.

`cancel()`은 사용자가 체험 중단·건너뛰기·수동 완료를 명시적으로 선택했을 때만 호출한다. 기존 요청 상태를 비우고 진행 중인 SDK 경로에 취소를 요청한다. 취소 확인 이벤트를 받기 전에는 다음 경로를 거절한다. 10초 안에 취소 확인을 못 받으면 SDK 연결을 해제하며 재연결 후 재시도해야 한다. 이전 취소 응답이 새 요청을 취소하는 경합을 방지하기 위한 처리다. 초기 연결 응답도 20초가 지나면 정리하여 영구 대기하지 않는다. 다음 목적지는 기록 저장과 사용자의 확인을 거친 뒤 새 경로로 요청해야 한다. Activity 종료 시 `close()`가 리스너와 연결을 해제한다.

SDK의 취소 이벤트에는 요청 ID나 목적지 이름이 없다. 앱이 취소 확인을 기다리는 구간 밖에서 받은 취소 이벤트만으로 현재 과제를 실패 처리하지 않는다. 이전 중복 이벤트와 다른 내비 클라이언트의 취소를 구별할 수 없기 때문이다. 사용자가 내비 앱에서 별도로 취소한 경우에는 체험 앱에서도 주차 후 중단하거나 수동 확인해야 한다.

## SDK 문서와 실제 바이너리 차이

현재 웹 문서의 Java 예시는 `RouteInfo`를 인자 9개로 생성하고 첫 좌표를 경도로 적지만, 실제 배포된 `navi-data:3.0.4`에는 인자 11개짜리 생성자가 있다. 실제 바이너리의 필드·getter·생성자 시그니처를 기준으로 **위도, 경도 순서**로 구현했다.

```java
new RouteInfo(latitude, longitude, null, null,
    name + " · 생활체험 " + UUID.randomUUID(), "", "", "", RouteOption.RECOMMENDED, "", false);
```

POI ID·주소·제공처를 알 수 없을 때 임의 값을 만들어 넣지 않는다. 이번 Pleos Connect 2.1.0의 UMOS 내비에서는 좌표와 이름만으로 요청해 경로가 생성되는 것을 확인했다. 다른 내비 구현에서 같은 동작을 보장하지는 않는다.

## 차량 UX 상태

Car service lifecycle 콜백으로 서비스 상태를 받고 `CarUxRestrictionsManager`에 리스너를 등록한다. 현재 상태를 다시 조회하며, 서비스 단절·예외·null 결과는 미확인으로 취급한다. `isRequiresDistractionOptimization()`이 true이거나 활성 제한 비트가 있으면 상세 입력을 제한한다. 따라서 SDK가 정상 연결돼도 UX 상태가 미확인이면 앱의 실제 차량 모드에서 상세 입력을 허용해서는 안 된다.

**UX 제한이 없다는 사실은 안전한 주차의 증거가 아니다.** 입력 전 사용자의 주차 확인이 별도로 필요하고, 제한이 다시 걸리거나 상태가 끊기면 그 확인을 해제해야 한다. 시연 모드의 수동 주행·주차 전환은 실제 차량 신호 검증과 구분한다. 이 adapter는 자동차의 속도나 P단을 추정하지 않는다. Android 9(API28)에서는 이 빌드의 lifecycle 방식 UX 감지를 실행하지 않아 실제 연동이 차단되며 Android 10 이상을 요구한다.

## 실행 검증에서 남겨야 할 근거

1. 설치·실행한 에뮬레이터 이미지와 버전, API level, 기기 serial.
2. NaviHelper 연결 성공 또는 정확한 오류. 일반 AAOS 실행은 Pleos 연결 검증과 다르다.
3. 요청 좌표의 순서·유효성 및 실제 `onRouteStarted` 수신 여부.
4. 실제 도착 이벤트 중복·누락·불일치 때 기록 상태가 바뀌지 않는지.
5. UX 상태의 초기 미확인·제한·해제·연결 해제 전환 및 주차 확인 초기화.
6. 실패 후 재시도, 사용자가 중단한 후 다음 목적지 요청.

SDK 다운로드·시그니처 확인에 이어 Java 17 컴파일러로 adapter를 실제 SDK 및 Android API34/Car jar에 대해 단독 컴파일했고 exit 0을 확인했다.

공식 Pleos 에뮬레이터 `emulator-5554`를 조사한 결과 `ai.pleos.playground.service.navi/.NaviService`(서비스 버전 3.0.4), 내비 앱 `ai.umos.maps.android.navigation.app`이 설치돼 있고 현재 사용자 10에서 실행 중이었다. 서비스 action은 `ai.pleos.playground.intent.action.NAVI_START`이다. 해당 이미지에서 `NAVI_ROUTE`·`NAVI_ROUTE_SEARCH`의 보호 수준은 `normal`이었다. 앱 설치 뒤 실제 grant 여부는 다시 확인해야 한다. 사용자 0과 현재 사용자 10을 혼동하지 않도록 `am get-current-user`를 확인한다.

앱 실행 후 Pleos 내비 서비스 연결 및 UX 제한 상태 수신을 확인했다. 테스트 좌표(서울시청)를 요청해 UMOS 내비에서 경로가 생성됐고, 앱에서 일치하는 경로 탐색 시작 이벤트를 수신했다. 이 목적지는 내비 통신 검증용이며 병원 시설을 확인한 사례가 아니다. 공식 Virtual Console로 D단·5km/h 상태를 재현해 실제 Car service의 DO=true/UxR=80, 편집창 닫힘과 플랫폼 차단도 확인했고 속도 0·P단으로 복원했다. 최종 실행 결과와 빌드 구분은 배포본의 QA 보고서를 기준으로 한다. 실차 검증은 하지 않았다.

## 공식 근거

- [공모전 개발 환경](https://appdevchallenge.auto-hmg.io/guide/development)
- [SDK 가져오기](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk)
- [필요한 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)
- [SDK 초기화와 해제](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release)
- [목적지 요청과 결과 확인](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations)
- [이벤트와 구현별 차이](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners)
- [Car API](https://developer.android.com/reference/android/car/Car)
- [CarUxRestrictionsManager](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictionsManager)
- [CarUxRestrictions](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictions)
