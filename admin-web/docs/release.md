# 관리자 웹 1차 배포 점검표

범위: 로그인·게시물 검수·사용자 관리·주제 관리·처리 이력. Slack·FCM·대시보드는 이번 배포의 선행 조건이 아니다. 연결 이슈: #187.

## 배포 전에 확정할 것

- GitHub 대상 브랜치: `admin-web`. 코드 변경은 별도 PR에서 최신 커밋을 승인받는다.
- Vercel 팀/프로젝트: `chalkak` / `chalkak-admin-web`, Root Directory: `admin-web`.
- 고정 주소: `https://chalkak-admin-web.vercel.app` — 실제 도메인 연결 상태를 대시보드에서 확인한다.
- 이번 1차 배포는 **개발 백엔드 `https://chalkak-dev.pysun.kr/api/v1/admin`와 개발 데이터**를 유지한다. Vercel Production은 웹의 배포 환경이며 백엔드 prod 전환을 뜻하지 않는다. 운영 백엔드·API 주소·운영 데이터 사용은 별도 승인을 받는다.
- 기존 정상 Production 배포 ID·소스 SHA·연결 환경·도메인을 기록한다. 처음 공개하는 경우에는 되돌릴 Production이 없음을 기록한다.
- 현재 Root Directory 외 파일 포함 끄기, 변경 없는 루트 빌드 건너뛰기, Preview 브랜치 추적 비활성화 정책을 유지한다. 백엔드 PR에 Vercel CI를 다시 붙이지 않는다.

## 보안·환경 관문

- [ ] 실제 관리자 인증이 켜져 있고, 무인증 401·일반 사용자 403 검증이 통과했다.
- [ ] 선택한 백엔드에 개발용 인증 우회가 꺼져 있다. 백엔드 prod를 쓰는 경우 Swagger와 OpenAPI 비활성화도 실제 서버에서 확인했다.
- [ ] GitHub CI, 실제 모드 Preview/Production 빌드, 설정 오류 음성 테스트, 비밀값 검사가 통과했다.
- [ ] Preview와 Production의 공개 환경변수를 각각 확인했다. Preview 브랜치별 값이 새 브랜치나 Production에 자동 적용된다고 가정하지 않는다.
- [ ] `NEXT_PUBLIC_API_MODE=real`; `NEXT_PUBLIC_ADMIN_API_BASE_URL`은 승인받은 HTTPS `/api/v1/admin` 주소다.
- [ ] 관리자 비밀번호·해시·JWT·AWS/Slack/Firebase 자격증명을 웹 환경변수에 추가하지 않았다.
- [ ] Vercel Authentication/Standard Protection을 유지한다. Preview 보호를 전체 해제해서 로그인 문제를 우회하지 않는다.
- [ ] Production 도메인에서 Vercel 로그인 없이 관리자 로그인 화면을 볼 수 있는지 별도로 확인한다. Vercel 접근 보호와 관리자 API 인증은 다른 단계다.
- [ ] 새 백엔드 CORS 예외를 만들지 않는다. 브라우저는 같은 출처 `/api/admin/*`만 호출하고 Next.js 서버가 백엔드에 연결한다.

환경변수는 빌드에 반영되므로 변경 후에는 다시 빌드한다. 예전 Preview 산출물에 도메인만 붙이고 Production 환경변수를 적용했다고 간주하지 않는다. 잘못된 설정을 통과시키려고 `mock`, 인증 우회, HTTP 운영 API를 사용하지 않는다.

## 후속 보안 점검

사용자 결정에 따라 로그인 반복 시도 제한과 Nginx 보완은 이번 PR의 강제 선행 조건이 아닌 후속 작업으로 진행한다. 점검한 백엔드 `b9782d1`에는 로그인 반복 시도 제한이 미구현 상태이며, 외부 WAF/Nginx의 적용 여부도 미확인이다. 따라서 로그인 무차별 대입에 대한 방어가 검증되지 않은 위험은 남아 있다.

- [ ] 백엔드 직접 접근에도 로그인 반복 시도 제한이 적용되는지 확인한다. 웹 UI의 중복 클릭 방지나 Vercel 경로만의 제한을 같은 방어로 간주하지 않는다.
- [ ] 브라우저 → Vercel 중계 → Nginx → 백엔드 경로에서 신뢰할 프록시와 클라이언트 IP 전달·검증 경계를 확인한다. 원본 `X-Forwarded-For`를 맹신하지 않고, 위조 헤더로 제한을 우회하거나 중계 IP 하나로 모든 사용자가 함께 제한되지 않는지 점검한다.
- [ ] Nginx를 거치지 않는 백엔드 포트의 직접 접근을 보안 그룹·방화벽·바인딩 설정으로 제한해, Nginx의 방어를 우회할 수 없는지 확인한다.

## 배포

1. 승인된 설정과 코드의 SHA를 배포 기록에 남긴다. 실제 비밀값은 기록하지 않는다.
2. `admin-web`을 추적하는 Production 자동 배포 설정이라면 준비가 끝난 후 PR을 병합한다. 자동 배포가 꺼져 있으면 Vercel에서 정확한 브랜치/커밋의 새 Production 배포를 만든다.
3. 배포의 Environment가 Production이고 Source SHA가 승인된 코드인지 확인한다. 예전 배포의 Redeploy는 예전 소스를 다시 빌드한다.
4. Ready 상태와 고정 도메인 연결을 확인한다. 실패하면 Build Logs의 원인을 확인하며, 비밀번호·토큰이 담긴 요청 본문이나 전체 환경변수를 로그에 붙이지 않는다.
5. 아래 Smoke test를 완료하기 전까지 #187을 닫지 않는다.

## 공개 주소 Smoke test

- [ ] 비로그인으로 `/posts`·상세 URL 접근 시 관리자 데이터 없이 로그인으로 이동한다.
- [ ] 비로그인 `/api/admin/auth/me`·`/api/admin/posts`가 401이며, 내부 오류·비밀값을 응답하지 않는다.
- [ ] 사용자가 직접 관리자 로그인 → 새로고침 → 목록/상세 조회 → 로그아웃 → 보호 경로 차단 → 재로그인을 확인한다. 채팅이나 PR에 비밀번호를 보내지 않는다.
- [ ] 사용자가 지정한 테스트 게시물/사용자/주제만 변경하고 처리 이력·목록/집계 갱신을 확인한다. 임의 운영 데이터를 변경하지 않는다.
- [ ] 휴대폰에서 사진 확대, 검수 버튼, 거절 입력과 소프트 키보드가 정상이며 가로 스크롤이 없다.
- [ ] 잘못된 계정·만료 상태에서 무한 재시도하지 않고 입력/오류가 적절히 표시된다. 계정을 반복 공격하는 테스트는 하지 않는다.
- [ ] 로그아웃 후 다음 요청이 인증되지 않는다. 이미 발급된 JWT의 즉시 폐기는 현재 백엔드 기능이 아니므로 쿠키 삭제와 서버 토큰 폐기를 혼동하지 않는다.

## 중단·롤백

무인증 데이터 조회, 잘못된 데이터 환경 연결, 비밀값 노출, 중복 처리/감사 불일치, 지속적인 로그인 실패나 5xx가 보이면 추가 변경을 멈추고 담당자에게 알린다.

이전 정상 Production이 있다면 Vercel의 **Instant Rollback**에서 기록한 배포를 선택하고 도메인·소스·백엔드 환경을 다시 확인한 뒤 복구한다. Hobby에서는 바로 이전 Production만 선택할 수 있다. Preview URL이 있다는 이유만으로 Production 롤백 대상으로 사용할 수 있는 것은 아니다.

롤백은 이전 산출물로 도메인을 돌리는 작업이다. 새 환경변수로 다시 빌드하거나 이미 일어난 DB 변경을 되돌리지 않는다. 복구 후에도 위 비로그인/로그인/조회 검증을 반복한다. 롤백 후에는 새 Production의 도메인 자동 할당이 중지될 수 있으므로 정상 릴리스를 다시 승격할 때 설정을 확인한다.

첫 정식 배포라 이전 정상 Production이 없다면 롤백 성공을 약속하지 않는다. 책임자 승인 후 **Settings → General → Pause Project**로 Production 제공을 일시 중지하고 고정 주소가 더 이상 관리자 웹을 제공하지 않는지 확인한다. 이 프로젝트에서 해당 버튼이 활성화된 것을 사전 확인했지만 실제 중지는 수행하지 않았다. Preview·설정·데이터는 유지되며, **Delete Project는 사용하지 않는다**. 수정본 검증 후 책임자 승인으로 재개하고 Smoke test를 반복한다.

권한이 없어 일시 중지할 수 없으면 프로젝트 책임자에게 즉시 요청한다. 관리자 웹의 일시 중지만으로 직접 접근 가능한 백엔드나 노출된 자격증명이 차단·회수되는 것은 아니다. 비밀값 노출 시 서버 자격증명 교체는 담당자가 별도로 수행한다.

실제 운영 데이터를 되돌리거나 성공한 Production을 의도적으로 내리는 롤백 훈련은 별도 승인 없이 수행하지 않는다. 문서 검토와 실제 롤백 실행 여부를 구분해 기록한다.

## 2026-08-31 사전 점검 기록

- #207 병합 소스 `3dc678c`, 검증 소스 `6de7394`: 프런트엔드 276개 테스트·lint·typecheck·Preview build/CI 통과, 사용자 실사용 검증 완료 확인.
- 백엔드 `b9782d1`: 일회용 로컬 PostgreSQL에서 46클래스/314개 테스트와 환경계약 20개 통과. 보호 API 15개의 무인증/일반 회원 인가 조합, 동시성, 감사 로그 원자성 포함. 기존 DB·서버는 변경하지 않았으며 임시 DB는 정리했다.
- prod Swagger 비활성화는 코드 설정을 확인했다. 운영 서버 HTTP 검증은 아직 수행하지 않았다.
- 로그인 반복 시도 제한 미구현·외부 WAF/Nginx 적용 미확인 상태와 잔여 위험을 유지해 기록한다. 사용자 결정에 따라 관련 보완·검증은 위 후속 보안 점검으로 이관하며, 이번 PR의 강제 선행 조건으로 두지 않는다.
- #207의 첫 Production 시도는 검증 전 전체 차단 코드 때문에 실패했다. 정식 배포 완료가 아니다.
- 이번 연결 대상은 개발 백엔드 유지로 확정했다. 사용자 요청 범위는 PR #244의 Ready 전환과 Vercel Production 환경변수 설정까지이며, 팀 승인·병합·배포는 사용자가 별도로 진행한다. 요청 승인과 실제 작업 완료를 구분한다.
- Vercel Production에 공개 Config 3개(`NEXT_PUBLIC_API_MODE`, `NEXT_PUBLIC_ADMIN_API_BASE_URL`, `NEXT_PUBLIC_APP_ENV`)를 저장하고 새로고침 후 확인했다. 기존 `admin-web-preview` 전용 Preview 설정은 유지했으며, 환경변수 저장만으로 재배포를 실행하지 않았다. EC2 환경변수·도메인·접근 보호 설정은 변경하지 않았다.
- 새 배포 SHA/ID, 고정 주소 Smoke test, 복구 대상은 실제 배포 시 #187에 기록한다.

## 공식 참고

- [Vercel 환경변수](https://vercel.com/docs/environment-variables)
- [Production 승격](https://vercel.com/docs/deployments/promoting-a-deployment)
- [Instant Rollback](https://vercel.com/docs/instant-rollback)
- [프로젝트 일시 중지](https://vercel.com/docs/projects/managing-projects#pausing-a-project)
- [Gitleaks 사용법](https://github.com/gitleaks/gitleaks)
