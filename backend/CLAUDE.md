# Backend Instructions

## 작업 범위와 공통 원칙

- 작업 범위는 현재 `backend/` 디렉터리로 제한한다.
- `../client/**`는 참조를 위해 읽을 수 있지만 수정하지 않는다.
- 불분명하거나 충돌하는 규칙은 임의로 해석하지 않고 사용자에게 확인한다.
- 명시적인 요청 없이 컨벤션 자체를 변경하지 않는다.
- 하네스 문서·검사기를 수정하면 [검사기](scripts/check_harness.py)의 실행 안내에 따라 `python3 scripts/check_harness.py`를 실행하고 오류를 수정·재검사한다. 검사기 변경 시 자체 테스트도 실행한다. 미실행·실패는 통과로 보고하지 않는다.
- 하네스 지침·스킬 변경 시 검사 사례 검토와 명시적으로 요청한 동작 평가에는 `harness-evaluation` Skill을 사용한다. 실제 AI 평가는 요청 없이 실행하지 않는다.

## 기획과 Git 작업

요청에 해당하는 스킬을 사용한다.

| 요청 | 적용 스킬 |
| --- | --- |
| 새 작업의 요구사항 구체화·이슈 기획 또는 명시적인 인터뷰 | `chalkak-interview` Skill |
| 합의한 작업의 이슈·PR 단위, 의존 관계·진행 순서와 개발 중 재분할 | `work-breakdown` Skill |
| 합의한 분할안·이슈 초안으로 개발부터 PR까지 시작·재개 | `development-workflow` Skill |
| 이슈·PR 작성·등록 | `issue-pr-workflow` Skill |
| 작업 브랜치 이름·생성·PR 대상·작업 완료 후 정리 | `branch-workflow` Skill |
| 커밋 단위 설계·메시지 추천·커밋 생성·정리 | `commit-conventions` Skill |

- 새 세션이나 특정 단어만으로 인터뷰를 시작하지 않는다. 확정된 작업의 구현·재개와 단순 질문에는 인터뷰를 자동 적용하지 않는다.
- 기획과 구현 중 요청 목적이 애매하면 관련 대화·이슈·명세·변경 근거를 확인하고 어느 쪽이 필요한지 한 번만 묻는다.
- 이슈·PR 양식은 `../.github/ISSUE_TEMPLATE/`와 `../.github/pull_request_template.md`에서 읽는다.

## 코드와 테스트

변경·검토 대상에 해당하는 규칙과 스킬을 함께 적용한다.

| 작업 조건 | 적용 규칙·스킬 |
| --- | --- |
| `src/main/java/**/*.java` 작업 | `.claude/rules/main-code.md` 경로 규칙 |
| `src/test/java/**/*.java` 작업 | `.claude/rules/test-code.md` 경로 규칙 |
| `src/main/java`의 동작 추가·변경 또는 버그 수정 | `tdd-workflow` Skill |
| `src/main/java`의 클래스·패키지 생성·이동 또는 배치 위치 검토 | `package-structure` Skill |
| API JSON 필드·Parameter·URI, Java 변수·필드·매개변수·메서드, DB 식별자 또는 ErrorCode 이름 생성·변경·리뷰 | `naming-conventions` Skill |
| API Endpoint, Controller, Request·Response 계약 추가·변경·삭제 또는 API 버전 검토 | `api-versioning` Skill |
| `src/main/java`의 Request DTO 생성·수정·리뷰 | `request-dto` Skill |
| `src/main/java`의 날짜·시간 입력·조회·검증·변환·응답 처리 생성·수정·리뷰 | `date-time` Skill |
| 예외 정의·발생·변환 또는 예외 클래스·에러 코드·예외 응답·예외 처리기 변경 | `exception-handling` Skill |
| springdoc-openapi 설정, Swagger/OpenAPI 어노테이션, API 문서 인터페이스 또는 생성된 OpenAPI 계약 생성·수정·리뷰 | `swagger-docs` Skill |

- 운영 코드와 테스트 코드를 함께 변경하면 양쪽에 해당하는 규칙과 스킬을 모두 적용한다.
- 테스트 클래스·메서드와 패키지 이름에는 `naming-conventions` Skill을 사용하지 않는다.
- Swagger/OpenAPI 문서와 설정만 변경하는 작업에는 `tdd-workflow` Skill을 사용하지 않는다. 실제 API 동작도 변경하면 그 동작 변경에만 적용한다.

## 환경변수와 배포

- `.env`, `.env.example`, `src/main/resources`의 환경변수 참조, `deploy/examples/*.env.example` 또는 환경변수를 처리하는 `deploy/scripts/**`를 변경할 때는 `env-synchronization` Skill을 사용한다.
- 배포 환경변수의 계약 또는 서버 값 변경이 필요한 작업은 최종 응답의 맨 마지막에 `🚨 배포 전 필수 수동 작업` 제목으로 안내한다.

  - 사람이 대상 서버의 `/etc/chalkak/application.env`를 직접 업데이트하고 `sudo systemctl restart chalkak-backend.service`를 실행해야 한다고 명시한다.
  - 실제 값은 쓰지 않고 변경할 키 이름과 대상 환경만 적는다. 이 경고 뒤에는 다른 내용을 작성하지 않는다.
