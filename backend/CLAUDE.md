# Backend Instructions

## 작업 범위와 공통 원칙

- 작업 범위는 현재 `backend/` 디렉터리로 제한한다. 저장소 공통 비즈니스 규칙이 적용되는 작업은 `../docs/business-rules/**`도 함께 읽고 필요한 문서만 수정할 수 있다.
- `../client/**`는 참조를 위해 읽을 수 있지만 수정하지 않는다.
- 불분명하거나 충돌하는 규칙은 임의로 해석하지 않고 사용자에게 확인한다.
- 명시적인 요청 없이 컨벤션 자체를 변경하지 않는다.
- 하네스 문서·검사기를 수정하면 [검사기](scripts/check_harness.py)의 실행 안내에 따라 `python3 scripts/check_harness.py`를 실행하고 오류를 수정·재검사한다. 검사기 변경 시 자체 테스트도 실행한다. 미실행·실패는 통과로 보고하지 않는다.
- 하네스 지침·스킬 변경 시 검사 사례 검토와 명시적으로 요청한 동작 평가에는 `harness-evaluation` Skill을 사용한다. 실제 AI 평가는 요청 없이 실행하지 않는다.

## 기획과 Git 작업

요청에 해당하는 스킬을 사용하고 레퍼런스는 링크의 적용 조건에 맞을 때 읽는다. 현재 문맥에 있는 변경되지 않은 지침은 재사용하되, 작업 기록·Git·PR 상태는 필요한 시점에 다시 조회한다.

| 요청 | 적용 스킬 |
| --- | --- |
| 사용자가 직접 선택·호출한 심화 인터뷰 | `chalkak-interview` Skill (수동 전용) |
| 합의한 작업의 이슈·PR 단위, 의존 관계·진행 순서와 개발 중 재분할 | `work-breakdown` Skill |
| 합의한 분할안·이슈 초안으로 개발부터 PR까지 시작·재개 | `development-workflow` Skill |
| 이슈·PR 작성·등록 | `issue-pr-workflow` Skill |
| 중요한 기획·개발 선택의 합의 후 고민 기록 작성·갱신 | `decision-notes` Skill |
| 작업 브랜치 이름·생성·PR 대상·운영 릴리스 브랜치·작업 완료 후 정리 | `branch-workflow` Skill |
| 커밋 단위 설계·메시지 추천·커밋 생성·정리 | `commit-conventions` Skill |
| 백엔드 PR의 코드 리뷰·리뷰 댓글 작성 | `backend-pr-review` Skill |

- 심화 인터뷰는 사용자가 `/chalkak-interview 작업 내용`으로 직접 호출하거나 스킬 선택 UI에서 선택해야 시작한다. 신규 기능·리팩터링·미정인 요구사항을 이유로 자동 호출하거나 다른 스킬에서 대신 시작하지 않는다.
- 일반 요청에서는 관련 근거를 확인하고 필요한 확인 질문만 한다. 심화 인터뷰를 요청하는 일반 문장에는 수동 실행 방법을 안내한다. 수동으로 시작한 같은 인터뷰는 답변으로 이어가고, 종료 후 재개는 다시 수동 호출한다.
- 이슈에 연결된 변경 작업의 착수·진행 갱신·재개에는 [작업 기록](.claude/skills/development-workflow/references/work-state.md)을 사용한다. 새 세션·단순 질문만으로 기록을 읽거나 만들지 않는다.
- 이슈·PR 양식은 `../.github/ISSUE_TEMPLATE/`와 `../.github/pull_request_template.md`에서 읽는다.
- 중요한 선택이 합의되면 `decision-notes` Skill으로 고민의 흐름을 기록하고 구현·검증 결과에 맞춰 갱신한다. 단순 대화에는 만들지 않으며 채팅·초안만 요청한 범위와 인터뷰 종료 요청을 우선한다.

## 순차 개발과 승인 경계

- 사람이 제공한 메인 이슈를 기준으로 인터뷰 결과 승인 → 실제 서브 이슈 분할안·본문·실행 승인 순서로 진행한다. 인터뷰는 수동 전용이며 단순 수정 요청에 이 흐름을 강제하지 않는다.
- 전체 실행 승인 뒤 서브 이슈 등록·첫 작업은 바로 시작하지만, 커밋은 매번 변경 설명·메시지를 보여주고 사용자 승인을 받은 뒤 생성한다. 다음 커밋 단위를 미리 구현하지 않는다.
- 독립 이슈도 하나씩 개발·리뷰·병합한다. 다음 이슈는 이전 PR 병합·완료 조건·이슈 종료 확인 후에만 시작한다. 조회 실패·미병합이면 다음 작업 요청이 있어도 착수하지 않는다.
- 이슈·PR은 확인된 본인 GitHub 계정을 담당자로 지정하고 생성 후 실제 반영을 확인한다. 새 커밋에 AI 공동 작성자·생성 문구를 넣지 않는다.

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
