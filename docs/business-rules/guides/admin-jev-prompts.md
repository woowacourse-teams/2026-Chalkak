# 관리자 Jev 프롬프트 관리 연동 안내

관련 규칙: [ADMIN-011](../rules/admin.md#admin-011-ai-프롬프트-버전). #1056의 프롬프트 관리 API이며 저장·조회·적용 API 구현과 관련 회귀 검증을 완료했으며 PR 미등록·서버 미배포다. 이 문서는 관리자 화면의 연계 계약이며 화면 구현 완료를 뜻하지 않는다.

## 직접 입력한 내용을 비활성 버전으로 저장한다

관리자 인증으로 `POST /api/v1/admin/jev-prompt-versions`를 호출한다. role은 `JEV_TOPIC_CLASSIFICATION` 또는 `JEV_PHOTO_MODERATION`, model은 `jev-1.14.0`이다. configuration에 직접 입력한 questions·categoryRules·categoryInstructions를 보낸다. memo는 선택이다. 저장 성공은 201이며 active=false다. 기존 GPT의 systemPrompt·userPromptTemplate·parameters를 받는 API와 다르다.

configuration의 각 질문은 type="choice", instructions 문자열, criteria의 선택지 이름→설명 문자열로 구성한다. 선택지는 2~255개이고 이름은 영문 소문자로 시작하는 소문자·숫자·밑줄(최대64자)이다. 질문·선택지 순서는 입력 Map에서 보존하며 JSONB 저장 후에는 DB가 정한 안정적인 객체 순서를 사용한다. 내용은 자동으로 생성하거나 채워 넣지 않는다.

| 역할 | 필수 질문 | 추가 규칙 |
| --- | --- | --- |
| 분류 | topic_category 한 개 | general 선택지 필수. categoryRules·categoryInstructions는 빈 객체 |
| 사진 검수 | topic_fit·image_safety·image_expression 세 개 | 각 선택지는 approve·human_review·reject. categoryRules에 general 필수 |

사진 검수의 categoryRules는 분류 카테고리 이름→주제 해석 지침이다. categoryInstructions는 해당 카테고리의 공통 주제 지침을 대체할 때만 입력하며 categoryRules에 있는 이름만 쓸 수 있다. 두 역할을 운영 적용할 때는 분류 선택지마다 사진 규칙이 있어야 한다. 사진 속 반려 추천은 최종 게시물 반려와 다르다. 최신 합의에서는 승인 추천만 자동 승인하고 반려 추천·검토는 관리자가 결정한다.

운영 프롬프트는 실험 원문과 합의한 기준을 관리자가 직접 검토해 입력한다. Flyway에는 초기 프롬프트 INSERT가 없으며 기동 시 파일·기본값을 읽어 자동 저장하지 않는다.

## 저장한 버전을 조회하고 명시적으로 적용한다

- `GET /api/v1/admin/jev-prompt-versions?role=JEV_PHOTO_MODERATION&page=1&pageSize=20`: 최신 버전순 목록. page는 1부터, pageSize는 1~100이다. 목록에는 본문이 없다.
- `GET /api/v1/admin/jev-prompt-versions/{promptVersionId}`: 원문·규칙·모델·활성 상태·적용 시각·생성자와 생성 시각을 조회한다. 다른 제공자의 ID는 404다.
- `PUT /api/v1/admin/jev-prompt-versions/{promptVersionId}/activation`에 `{"active":true}`: 해당 역할의 기존 활성 버전을 교체한다.
- 같은 경로에 `{"active":false}`: 지정한 버전이 현재 활성일 때만 비활성화한다. 과거 버전 ID로 현재 다른 활성 버전을 끄지 않는다. active를 생략하거나 null로 보내면 400이다.

동일한 활성 상태 재요청은 적용 시각과 감사 로그를 늘리지 않는다. 내용 수정은 새 버전으로 저장한다. 양쪽 역할의 필수 프롬프트가 모두 적용돼야 후속 검수 실행에서 사용할 수 있으며, GPT의 최신 버전 선택에는 영향을 주지 않는다.

시간은 UTC Instant로 전달한다. activatedAt은 적용 전 null이며 비활성화해도 과거 마지막 적용 시각을 보존한다. 현재 활성 여부는 active로 판단한다. 잘못된 역할·모델·질문·호환되지 않는 규칙 조합은 400, 인증 없음은 401, 관리자 권한 없음은 403, 없는 Jev 버전은 404다.

실제 시험 실행·사용량 기록과 게시물 검수 연결은 #1056의 다음 구현 단위에서 제공한다. 현재 저장·조회·적용 API 자체를 실제 Jev 호출·성능 검증의 완료로 표시하지 않는다.
