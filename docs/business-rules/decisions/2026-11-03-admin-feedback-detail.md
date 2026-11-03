# FEEDBACK-004·ADMIN-009 관리자 문의 상세 조회

- 결정일: 2026-11-03
- 상태: 확정
- 변경 전: 관리자는 전체·사용자별 목록으로 문의를 조회했으며, 문의 ID로 한 건을 조회하는 관리자 API는 없었다.
- 변경 후: `GET /api/v1/admin/feedbacks/{feedbackId}`에서 문의 ID·원문·접수 시각·삭제 시각과 작성자 ID·이메일·상태·앱 버전을 제공한다. 삭제된 문의와 정상·정지·탈퇴 회원의 문의를 모두 포함한다. 문의 삭제 시각은 삭제 전에는 null이다. 없는 문의는 `404 / BUSINESS_ERROR / 문의를 찾을 수 없습니다.`이며, 잘못되거나 축약된 UUID는 400으로 거절한다.
- 변경 이유: 관리자 화면에서 목록의 문의를 선택해 최신 상세를 확인할 수 있도록 한다. 본인 문의 API와 분리해 관리자에게 허용한 전체 기록 조회 범위를 유지한다.
- 영향 범위: ADMIN 전용 상세 조회를 기존 v1에 추가한다. 기존 목록 계약과 사용자 앱의 본인 조회 범위는 유지하며, 사용자에게 숨겨진 삭제 문의도 관리자는 조회할 수 있다. 조회는 변경 작업이 아니므로 감사 로그를 남기지 않는다. 목록·상세 조회는 #904의 [PR #922](https://github.com/woowacourse-teams/2026-Chalkak/pull/922)에 포함한다. 관리자 문의 삭제·감사 로그는 [#921](https://github.com/woowacourse-teams/2026-Chalkak/issues/921), 답변·알림은 #905·#906의 후속 범위다.
- 관련 규칙: [FEEDBACK-004](../rules/feedback.md#feedback-004-관리자-조회), [ADMIN-001](../rules/admin.md#admin-001-접근-권한), [ADMIN-009](../rules/admin.md#admin-009-피드백-조회)
- 관련 이슈·PR: [#904](https://github.com/woowacourse-teams/2026-Chalkak/issues/904), 선행 [PR #911](https://github.com/woowacourse-teams/2026-Chalkak/pull/911)
