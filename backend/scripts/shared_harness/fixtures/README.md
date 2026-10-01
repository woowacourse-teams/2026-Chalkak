# SessionStart 입력 계약 예시

2026-09-24 확인한 공식 문서를 바탕으로 만든 최소 입력이다. 실제 사용자 대화·경로·인증 정보를 수집한 기록이 아니며, 실제 앱 이벤트 캡처라고 보고하지 않는다.

- [Codex 공통 입력·SessionStart](https://learn.chatgpt.com/docs/hooks): `session_id`, `cwd`, `source`, `hook_event_name`; source는 startup/resume/clear/compact.
- [Claude SessionStart](https://code.claude.com/docs/en/hooks#sessionstart): 같은 필드를 사용한다. 부가 정보는 도구·버전에 따라 달라진다.
- [Codex 예시](codex-session-start.json), [Claude 예시](claude-session-start.json)의 cwd만 임시 저장소로 치환해 실제 설치된 `manage.py session` 프로세스의 입력으로 사용한다.

검사 범위는 JSON 입력 → 등록 저장소 확인 → 정책 버전 고정 → 문서 경로 출력이다. 실제 Orca·Codex 데스크톱의 신뢰 승인·hook 실행·모델의 문서 읽기는 팀원 환경에서 별도로 확인한다. source가 누락됐다고 startup으로 처리하면 기존 대화에 새 버전을 섞을 수 있으므로 추정하지 않는다.
