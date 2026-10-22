# 공개 웹페이지 작업 지침

- 이 디렉터리는 `web/pages` 브랜치와 전용 Vercel 프로젝트로 배포한다. 앱·백엔드·관리자·기존 랜딩 코드와 독립적으로 유지한다.
- 공개 웹 CI는 `web/pages` push 전용으로 유지한다. Vercel Production은 `web/pages`를 추적하고 Preview의 Branch Tracking은 끈다. 다른 브랜치에는 이 폴더가 없을 수 있으므로 `vercel.json`만으로 배포를 제한했다고 판단하지 않는다.
- 새 페이지는 `public/<slug>/`에 넣고 `config/pages.json`에 등록한다. 페이지 전용 스타일·에셋은 해당 폴더에, 여러 페이지가 공유하는 코드는 `public/shared/`에 둔다.
- 외부 설문 주소는 `config/surveys.json`의 공개 Google Forms 응답 링크만 사용한다. 설문이 없으면 `null`을 유지하며 임의 URL이나 수집 완료 표시를 만들지 않는다.
- `{{survey:id}}`, `{{survey-status:id}}`, `{{site:origin}}`는 빌드 시 치환된다. 직접 `dist/`를 수정하지 않는다.
- 변경 후 `npm test`, `npm run build`를 실행하고 해당 페이지와 공통 바를 PC·모바일에서 확인한다.
- 비밀값, Vercel 개인 인증, 관리자/백엔드 토큰을 공개 파일이나 Git에 넣지 않는다.
- 서비스 정책을 새로 만들거나 AI 점수·가중치·모델·처리 보장을 임의로 설명하지 않는다. 출처와 확인된 범위를 유지한다.
