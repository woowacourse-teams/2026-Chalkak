# 완료 기준 (Definition of Done)

다섯 플랫폼 공통이다. 기능 하나를 "끝났다"고 하려면 아래를 모두 만족한다. 각 항목의 근거는 링크한 문서에 있다. 플랫폼 고유 항목은 각 [platforms/](../README.md#플랫폼별-문서) 문서 끝 체크리스트를 함께 본다.

## 1. 동작

- [ ] 관련 비즈니스 규칙 ID를 읽었고, 규칙이 없거나 서버와 다른 점은 [open-questions](../open-questions.md)에 올렸다 ([00-principles §2](../00-principles.md#2-근거-우선순위))
- [ ] 패리티 시나리오 ID가 있고, 그 시나리오를 테스트가 덮는다 ([testing-parity §3](../common/testing-parity.md#3-기능-동등성-검증-체계))
- [ ] 패리티 매트릭스의 이 플랫폼 칸을 갱신했다
- [ ] 다른 플랫폼과 다르게 동작하는 부분은 "허용된 차이"로 기록했다

## 2. 상태와 오류

- [ ] 화면 상태(로딩·빈·오류·오프라인·콘텐츠)가 명세대로 나오고 프리뷰가 있다 ([ux §2](../common/ux-accessibility.md#2-화면-상태))
- [ ] 모든 `errorCode`가 공통 표대로 처리되고, 취소는 오류로 보이지 않는다 ([networking §1](../common/networking-errors-retry.md#1-에러-분류))
- [ ] 재시도 대상과 상수가 공통 표와 같다 ([공통 상수](../common/networking-errors-retry.md#찰캌-공통-상수))
- [ ] 화면을 떠나면 요청·폴링·재시도가 취소된다
- [ ] 실패 후 사용자 입력(사진, 제목)이 보존된다

## 3. 구조와 테스트

- [ ] Screen / Component / ViewModel / UiState / Repository 경계를 지켰다 ([architecture-state](../common/architecture-state.md))
- [ ] ViewModel UiState 전이 단위 테스트(Fake Repository, 가짜 시계)가 있다
- [ ] 문구·접근성 라벨·분석 이벤트가 공통 표·사전과 같다
- [ ] Maestro/Playwright용 요소 식별자가 식별자 표와 같다
- [ ] 린트·포맷·아키텍처 검사가 통과한다

## 4. 플랫폼 품질

- [ ] 느린 망(3G 프로필)과 비행기 모드 전환에서 확인했다
- [ ] 프로세스 종료·새로 고침 후 작성 중 상태가 기준대로 복원된다 ([ux §8](../common/ux-accessibility.md#8-프로세스-종료와-상태-복원))
- [ ] 최대 글자 크기, 스크린 리더, 다크 모드, 안전 영역·키보드 겹침을 확인했다 ([ux §4, §7](../common/ux-accessibility.md#7-접근성))
- [ ] 권한은 맥락 있는 시점에만 요청하고 거부 경로가 있다 ([ux §5](../common/ux-accessibility.md#5-권한-요청))

## 5. 보안과 릴리스

- [ ] 토큰·개인정보가 로그·분석·크래시 리포트에 남지 않는다 ([security §7](../common/security-privacy-release.md#7-관측성-크래시-로그-분석))
- [ ] 새 비밀 값을 클라이언트에 넣지 않았다
- [ ] release(난독화·minify) 빌드로 이 기능을 한 번 실행했다
- [ ] 새 권한·SDK가 생겼다면 권한 문자열, Privacy manifest, Data safety를 고쳤다
