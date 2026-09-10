# 실행 가능한 백엔드 컨벤션

AI의 판단 규칙은 Skill에, 기계적으로 확인할 규칙은 아래 설정과 Java 테스트에 둔다.
이 파일은 검사 설정을 변경하거나 실패 원인을 찾을 때 읽는다.

| 담당 | 규칙 원본 | 이번에 자동화한 범위 |
| --- | --- | --- |
| Spotless | [Gradle 설정](../build.gradle.kts), [포맷 설정](formatter/eclipse-java.xml) | 4칸 들여쓰기, 이어지는 줄 8칸, 같은 줄의 여는 중괄호, LF·끝 개행·후행 공백·불필요한 import |
| Checkstyle | [문법 검사](checkstyle/checkstyle.xml) | 운영 코드의 if·for·while 중괄호, else·switch 금지, 제어문 빈 블록, 필드·매개변수·지역변수·record 컴포넌트 이름 |
| ArchUnit | [아키텍처 테스트](../src/test/java/com/chalkak/backend/architecture/ArchitectureConventionTest.java) | 요청 패키지의 `*Request` record, Service의 Spring Data·AWS SDK 직접 의존 금지, 저장소 포트의 JpaRepository 상속 금지, JPA 저장소·Controller 위치 |

## 실행

backend 디렉터리에서 실행한다. 도구는 Gradle이 내려받으며 별도 AI 호출은 없다.

```bash
# DB·Spring 실행 없이 컨벤션 검사
./gradlew checkConventions

# 형식 자동 수정 후 재검사 (대상 파일 diff를 확인한다)
./gradlew spotlessApply checkConventions

# 기존 기능 테스트와 정적 검사 전체
./gradlew check
```

`checkConventions`가 통과해도 기능·정책 테스트를 대신하지 않는다. `check`는 기존 DB 테스트 환경이 필요하다.
기존 CI는 `test bootJar`를 호출하므로 ArchUnit은 실행되지만 Spotless·Checkstyle은 아직 CI 필수 단계가 아니다.
CI에 전체 검사를 연결할 때는 기존 명령의 `test`를 `check`로 바꾼다.

## 검사 설정 자체의 동작 확인

설정·버전·예외를 바꿀 때 아래 검사를 실행한다. 정상 예제의 통과와 잘못된 예제의 실패를 함께 확인한다.

```bash
python3 scripts/test_convention_tools.py
./gradlew architectureTest
```

첫 명령은 Spotless의 자동 수정·재실행 안정성·기존 줄바꿈 보존과 Checkstyle의 금지 문법·이름·예외 범위를 확인한다.
가상 코드는 `build/convention-probe`에만 생성한다. 두 번째 명령은 실제 운영 코드와 ArchUnit 오류 주입 사례를 검사한다.
이 검사는 AI가 도구를 자발적으로 호출하거나 실패를 올바르게 수정하는지 평가하는 AI 행동 검사와는 별개다.

## 기존 코드와 예외

- 포맷은 통합 브랜치에 존재하는 고정 기준 커밋 `50e3764ec85d26714710bb86086edcf493768f26` 이후 변경한 Java 파일 전체에 적용한다. 기준을 HEAD나 매번 움직이는 브랜치로 바꾸지 않는다. shallow clone이면 해당 커밋 이력이 필요하며 기준을 찾지 못하면 검사 실패로 처리한다.
- Checkstyle은 운영 Java 전체, ArchUnit은 테스트·생성 코드가 아닌 운영 클래스를 검사한다. 테스트 코드에 운영 코드의 금지 문법이나 이름 규칙을 적용하지 않는다.
- 기존 `AdminTopicQueryRepositoryImpl.appendOrder`의 switch 한 곳만 [기존 위반 목록](checkstyle/suppressions.xml)에 남긴다. 그 메서드를 정리할 때 제외도 제거한다. 패키지 전체 제외나 자동 baseline 갱신은 하지 않는다.
- Request의 일반 클래스가 필요한 경우 기존 Skill대로 개발자에게 확인한다. 승인된 구체적인 대상·이유를 남겨 검사 예외를 변경하며 AI가 통과만을 위해 검사를 끄지 않는다.

## Skill에 유지하는 규칙

상수의 파일 내부 용도 판단, 정확한 메서드 배치 순서, 매개변수 3개 이상 줄 나누기,
get/find 등 이름의 의미, 검증 재사용, 예외 분리, API 호환성, 날짜 정책, 테스트 전략과 TDD는 유지한다.
외부에 노출된 JSON·URI·DB 이름도 단순 Java 이름 검사로 대체하지 않는다.
패키지 배치표는 생성 전에 읽는 안내로 유지하며, ArchUnit이 모든 도메인 경계를 증명하지는 않는다.

## 공식 문서

- [Spotless Gradle 설정](https://github.com/diffplug/spotless/blob/main/plugin-gradle/README.md)
- [Checkstyle 검사 목록](https://checkstyle.org/checks.html)
- [ArchUnit 사용법](https://www.archunit.org/userguide/html/000_Index.html)
