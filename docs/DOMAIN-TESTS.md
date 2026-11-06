# 도메인 검증

Android framework에 의존하지 않는 `AppData`와 `TrialEngine`을 직접 컴파일한다.

```sh
source scripts/env.sh
mkdir -p build/domain-test
javac -d build/domain-test app/src/main/java/kr/salabolgil/app/core/AppData.java app/src/main/java/kr/salabolgil/app/core/TrialEngine.java app/src/testDomain/java/kr/salabolgil/app/core/TrialEngineTest.java
java -cp build/domain-test kr.salabolgil.app.core.TrialEngineTest
```

결과: `PASS: 47 domain assertions`.

검증 범위: 내비 호출과 경로 시작의 구분, 중복 도착 무시, 도착과 시설 이용의 구분, 주행 입력 잠금, 실제 UX 상태 unknown fail-closed, UX 제한 해제와 사용자 주차 확인의 구분, 실제 AAOS UX 제한을 시연 모드로 우회할 수 없음, 도중 경로 실패의 재시도, 사용자 확인 없는 다음 과제 자동 시작 방지, 미확인과 0의 구분, 같은 과제 A/B 격리, 직렬화 후 진행 상태 복구와 안전 상태 재확인, 건너뛰기, 중단·재개, 예상 근거 필수, 잘못된 좌표 차단.

이 테스트는 실제 Pleos SDK 동작이나 실차 안전성을 입증하지 않는다. 실제 에뮬레이터 실행 결과는 별도로 확인해야 한다.
