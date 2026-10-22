# ADMIN-011 AI 프롬프트 버전 도입

- 결정일: 2026-10-19
- 상태: 확정
- 변경 전: 프롬프트 초안을 미리보기로 실행해 볼 수만 있었고, 확인한 프롬프트를 서비스에 저장하거나 사용자 기능에 쓸 방법이 없었다.
- 변경 후: 관리자가 프롬프트를 용도별 불변 버전으로 저장하고 목록(본문 제외)·상세(전체)로 조회한다. 버전은 수정·삭제할 수 없고 새 버전을 만들어 바꾼다. 사용자 기능은 용도별 가장 최신 버전을 사용하며(사용자 기능 적용은 [#689](https://github.com/woowacourse-teams/2026-Chalkak/issues/689)에서 하고, 그 전까지 만든 버전은 사용자에게 쓰이지 않는다), 버전이 없으면 해당 기능은 꺼진 상태이다. 생성마다 용도와 버전 번호만 담은 감사 로그를 남긴다.
- 변경 이유: 확인한 프롬프트를 빠르게 사용자에게 내보내고 반복해서 개선하기 위해서다. 불변 버전과 번호 증가만으로 변경 이력과 되돌리기(이전 내용으로 새 버전 생성)를 단순하게 유지한다. 명시적 활성화·비활성화는 범위를 줄이려고 뒤로 미룬다.
- 영향 범위: 관리자 API 세 개(`POST`·`GET /api/v1/admin/ai-prompt-versions`, `GET /api/v1/admin/ai-prompt-versions/{promptVersionId}`)와 프롬프트 버전 테이블. #689 적용 이후에는 버전을 만드는 것이 곧 운영 변경이다. 활성화 기능([#701](https://github.com/woowacourse-teams/2026-Chalkak/issues/701))이 생기기 전에는 되돌릴 스위치가 없으므로, 그때 문제가 생기면 OpenAI 예산·키로 호출을 멈춘다. 회원 API와 Android는 바뀌지 않는다.
- 관련 규칙: ADMIN-011, ADMIN-010
- 관련 이슈·PR: [#689](https://github.com/woowacourse-teams/2026-Chalkak/issues/689), [#686](https://github.com/woowacourse-teams/2026-Chalkak/issues/686), [#688](https://github.com/woowacourse-teams/2026-Chalkak/issues/688), [#701](https://github.com/woowacourse-teams/2026-Chalkak/issues/701). 이 구현의 PR은 아직 등록하지 않았다.
