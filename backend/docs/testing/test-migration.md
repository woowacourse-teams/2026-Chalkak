# 백엔드 테스트 이관 계획

2026-11-02 / [이슈 #893](https://github.com/woowacourse-teams/2026-Chalkak/issues/893)

규칙 정비를 커밋한 뒤 앱 버전 조회 테스트를 첫 이관 대상으로 정리했다. 전체 기존 테스트의 전환은 아직 완료하지 않았다. 현재 기준은 [테스트 규칙](../../.agents/skills/test-code/SKILL.md)이며, 기존 테스트 코드는 기능별 작업에서 검증을 대조하며 옮긴다.

## 기준이 되는 코드

`be/develop`의 `5e732cfef69be262e97fd43427105c575876f6e5`에서 조사했다. 테스트 파일은 338개이며, 미병합 [좋아요 변경 PR #892](https://github.com/woowacourse-teams/2026-Chalkak/pull/892)는 포함하지 않았다.

`@Test`, `@ParameterizedTest`, `@RepeatedTest`, `@TestFactory`, `@TestTemplate` 선언이 있는 Java 파일을 집계했다. 선언 수는 파라미터별 실제 실행 건수와 다르다. 전체 Spring 여부는 `IntegrationTestSupport` 상속 또는 `@SpringBootTest`로 확인한 조사 지표이며 테스트의 최종 분류는 본문의 검증 목적을 보고 결정한다.

| 분야 | 테스트 파일 | 테스트 선언 | 서비스 파일 | 전체 Spring 서비스 파일 |
| --- | ---: | ---: | ---: | ---: |
| admin | 98 | 574 | 36 | 34 |
| aireview | 21 | 241 | 7 | 3 |
| analytics | 7 | 58 | 4 | 1 |
| appversion | 10 | 35 | 1 | 1 |
| architecture | 2 | 4 | 0 | 0 |
| auth | 50 | 357 | 10 | 8 |
| banner | 8 | 36 | 1 | 1 |
| common | 8 | 32 | 0 | 0 |
| config | 6 | 61 | 0 | 0 |
| exception | 2 | 20 | 0 | 0 |
| feedback | 5 | 17 | 0 | 0 |
| like | 5 | 25 | 2 | 2 |
| notification | 65 | 491 | 24 | 20 |
| photo | 2 | 11 | 0 | 0 |
| post | 29 | 301 | 16 | 14 |
| topic | 7 | 26 | 1 | 1 |
| user | 12 | 133 | 4 | 3 |
| 애플리케이션 시작 확인 | 1 | 1 | 0 | 0 |
| 합계 | 338 | 2423 | 106 | 88 |

## 전환과 유지 기준

| 현재 검증 | 처리 | 전환 때 남겨야 할 확인 |
| --- | --- | --- |
| 도메인 규칙과 순수 계산 | 기존 단위 테스트 유지 | 상태와 경계 규칙의 중복만 확인 |
| 서비스의 조건 판단과 객체 협력 | Repository Mock 단위 테스트로 전환 검토 | 실제 도메인 객체, 반환값, 저장할 상태와 필요한 부수효과 |
| 쿼리와 매핑 | PostgreSQL 슬라이스 테스트로 정리 | 공개와 삭제 조건, 정렬, 페이지, DB 제약 |
| HTTP 계약 | 웹 슬라이스 유지 | 실제 입력 검증, 상태 코드, JSON과 오류 변환 |
| 인증과 회원 상태별 접근 | 실제 보안 구성 유지 | 미인증, 정지, 탈퇴와 자원 권한 |
| 실제 저장 결과와 여러 계층의 연결 | 핵심 인수 또는 필요한 통합 테스트로 유지 | Mock 전환으로 사라지는 저장과 매핑 검증 |
| 경쟁 요청과 트랜잭션 | 실제 Service, Repository와 DB 유지 | 락, 커밋, 롤백, 별도 트랜잭션과 커밋 후 이벤트 |
| 외부 Adapter, 설정과 아키텍처 | 각 책임의 기존 테스트 유지 | 자체 요청 계약, 오류 변환과 구성 연결 |

전체 Spring을 사용한다는 이유만으로 테스트를 삭제하거나 Mock으로 일괄 교체하지 않는다. 이름에 동시성이나 트랜잭션이 없어도 실제 경계 검증이 들어 있으면 유지 대상이다.

## 먼저 읽을 기존 테스트

| 사례 | 판단 |
| --- | --- |
| [PostCreationServiceTest](../../src/test/java/com/chalkak/backend/post/service/PostCreationServiceTest.java) | 일반 생성 판단은 단위 전환 후보이다. 게시물과 사진의 실제 저장, 이벤트 발행 검증은 남겨 둘 테스트와 대응시킨 뒤 옮긴다. |
| [AdminFeedbackQueryServiceTest](../../src/test/java/com/chalkak/backend/admin/service/feedback/AdminFeedbackQueryServiceTest.java) | 이미 Repository Mock 단위 테스트다. 변경할 이유가 없으면 유지한다. |
| [PostRepositoryTest](../../src/test/java/com/chalkak/backend/post/infrastructure/persistence/PostRepositoryTest.java) | 실제 DB 슬라이스를 유지한다. |
| [PostControllerTest](../../src/test/java/com/chalkak/backend/post/api/v1/controller/PostControllerTest.java) | 웹 슬라이스를 유지한다. 도메인 경계와 HTTP 오류 변환을 다른 목적으로 구분한다. |
| [FeedbackSubmissionIntegrationTest](../../src/test/java/com/chalkak/backend/feedback/api/v1/controller/FeedbackSubmissionIntegrationTest.java) | 요청부터 실제 저장과 응답까지 이어지는 인수 시나리오로 유지한다. |
| [PostModerationNotificationTransactionTest](../../src/test/java/com/chalkak/backend/post/service/PostModerationNotificationTransactionTest.java) | 커밋 후 알림과 롤백 시 미발송을 실제 DB로 유지한다. |
| [UserRefreshTokenConcurrencyTest](../../src/test/java/com/chalkak/backend/auth/service/token/UserRefreshTokenConcurrencyTest.java) | 토큰 경쟁 요청을 실제 DB로 유지한다. |
| [AiReviewWorkerServiceTest](../../src/test/java/com/chalkak/backend/aireview/service/AiReviewWorkerServiceTest.java) | 일반 처리와 경쟁 상황이 섞여 있다. 클래스 전체를 Mock으로 전환하지 않는다. |
| [PushWorkerServiceTest](../../src/test/java/com/chalkak/backend/notification/service/PushWorkerServiceTest.java) | 발송 판단과 실제 트랜잭션 검증을 나눠 읽고, 필요한 경계 검증을 남긴다. |

## 앱 버전 조회의 첫 이관

규칙 정비는 `3987d2b7`로 커밋했다. 사용자의 추가 요청에 따라 아래 기존 클래스 4개에서 첫 전환을 진행한다. 새 테스트 클래스는 만들지 않는다.

| 이전 시나리오 | 남긴 검증 위치 |
| --- | --- |
| 두 플랫폼의 설정과 조회 결과 매핑 | [서비스 단위](../../src/test/java/com/chalkak/backend/appversion/service/AppVersionServiceTest.java)의 플랫폼별 반환값과 [Repository 통합](../../src/test/java/com/chalkak/backend/appversion/infrastructure/persistence/AppVersionRepositoryTest.java)의 실제 DB 조회로 역할을 나눈다. |
| 빈 DB에서 미설정 조회와 기본 행 미생성 | 서비스 단위에서 null 응답과 저장 미호출을 확인하고, [기존 관리자 통합](../../src/test/java/com/chalkak/backend/admin/service/appversion/AdminAppVersionServiceTest.java)에서 두 플랫폼의 공개 조회 후 DB 행이 0개인지 확인한다. |
| 다른 플랫폼만 설정된 경우 | 서비스의 동일한 미설정 분기는 파라미터화하고, 관리자 생성과 수정 통합 시나리오에서 ANDROID가 저장돼도 IOS는 미설정인지를 유지한다. |
| 변경된 설정의 다음 조회 반영 | 서비스 단위의 연속 조회와 기존 관리자 저장 후 공개 조회 통합 시나리오에 남긴다. |
| 생성과 수정 시각, 기존 데이터 마이그레이션 | [시각과 매핑 테스트](../../src/test/java/com/chalkak/backend/appversion/infrastructure/persistence/AppVersionTimestampTest.java)를 PostgreSQL `@DataJpaTest`로 좁히고 기존 assertion을 유지한다. |
| 플랫폼 중복 저장 경쟁과 관리자 트랜잭션 | Repository의 기존 경쟁 요청과 [관리자 트랜잭션](../../src/test/java/com/chalkak/backend/admin/service/appversion/AdminAppVersionTransactionTest.java)은 실제 DB를 유지한다. |

이 전환은 DB 없는 일반 서비스 판단과 실제 DB 검증을 분리하는 첫 단위다. 관리자 서비스 전체나 다른 기능까지 단위 테스트로 옮겼다는 뜻은 아니다. 실행 결과와 환경 제약은 해당 작업 기록과 PR에 남긴다.

## 기능별 후속 작업 범위

다음 표는 전체 이관 범위를 빠뜨리지 않기 위한 목록이다. 후속 이슈와 PR의 본문, 최종 크기와 실행 승인은 각 작업 전에 확인한다. 이 문서만으로 후속 이슈 등록이나 개발을 승인한 것으로 보지 않는다. 앞 작업의 병합과 완료를 확인한 뒤 다음 작업에 착수한다.

| 순서 | 검토 범위 | 보호할 흐름 |
| --- | --- | --- |
| 1 | 공통, 도메인, 설정, 예외와 문서 계약 | 기존 단위와 슬라이스를 유지하고 공통 오류와 보안 검증의 중복을 점검 |
| 2 | 인증, 사용자와 관리자 인증 | 로그인, 가입, 갱신, 탈퇴, 기기 폐기 연결과 토큰 경쟁 |
| 3 | 게시물, 사진, 좋아요와 관리자 게시물 | 제출, 콜백, 검수, 공개, 수정과 삭제의 저장 및 경쟁 요청 |
| 4 | 주제, 배너, 앱 버전, 피드백, 지표와 대응 관리자 기능 | 조회 조건, 관리 변경, 감사 기록과 저장 원자성 |
| 5 | AI 리뷰와 관리자 AI 기능 | 요청 자격, 버전 선택, 재시도, Worker 처리와 중복 실행 방지 |
| 6 | 알림과 관리자 알림 기능 | 알림함, 수신 설정, 기기, Relay, Worker, 공지, 리마인더와 주제 발송 |

큰 범위는 유스케이스별로 다시 나눈다. 위 행을 곧바로 PR 하나로 묶지 않으며, Controller와 Service처럼 계층만으로 분리하지 않는다. 기기 폐기처럼 여러 분야에 걸친 흐름은 한 작업에서 관련 연결 검증을 함께 확인한다.

## 각 작업의 완료 확인

- 변경 전 테스트가 보호하는 정상, 거부, 경계, 저장과 부수효과를 확인한다.
- 옮기거나 삭제한 시나리오마다 남은 검증 위치를 PR에 적는다. 삭제하지 않은 테스트까지 별도 표로 복제하지 않는다.
- 같은 목적의 테스트는 묶고, 입력만 다른 경우 파라미터화한다. 실행 환경이나 책임이 다를 때만 새 클래스를 만든다.
- 단위 전환 대상은 DB 없이 실행되는지 확인한다. 남겨 둔 DB 테스트는 테스트 PostgreSQL로 실제 실행한다.
- 관련 테스트와 형식 검사를 실행하고, 해당 변경으로 영향받는 전체 테스트의 회귀를 확인한다. 실패와 미실행은 그대로 기록한다.
- 클래스 수나 속도 개선은 실제 전후를 측정한 경우에만 성과로 적는다. 현재는 삭제 목표 수나 속도 개선 수치를 정하지 않는다.
