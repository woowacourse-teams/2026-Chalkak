# Web 앱 (모바일 우선, PWA)

공통 기준은 [common/](../README.md#공통-문서)에 있다. 이 문서는 웹에만 있는 API와 함정만 담는다. 근거: [raw/web-pwa.md](../../raw/web-pwa.md). 현재 `client/web/beta`는 베타 감사 정적 페이지뿐이고 서비스 웹앱은 새로 만든다.

## 먼저 풀어야 할 서버 쪽 전제

웹은 다른 네 클라이언트와 달리 서버 설정 없이는 동작하지 않는다. [open-questions](../open-questions.md#백엔드-확인-대상)에 올린다.

- **CORS**: 현재 서버의 CORS 설정은 관리자 경로에만 있다. 웹앱 출처에서 `/api/v1/**`를 부르려면 허용 출처, `Authorization` 헤더 허용이 필요하다(같은 도메인 리버스 프록시로 피할 수도 있다).
- **토큰 보관**: 서버는 토큰을 응답 바디로 준다. 웹에서 refresh 토큰을 localStorage에 두면 XSS 한 번에 탈취된다. httpOnly 쿠키 방식(서버 변경)과 메모리 보관 + 매 방문 재로그인 사이에서 결정해야 한다.
- **소셜 로그인**: 서버는 OIDC **ID 토큰 + rawNonce**(Apple은 authorizationCode 추가)를 받는다. 웹용 Google·Apple(Services ID)·Kakao 클라이언트 ID가 서버의 허용 audience에 들어가 있는지 확인한다.
- **웹 푸시**: 서버는 FCM으로 보낸다. 웹은 Firebase JS SDK + VAPID 키로 FCM 웹 토큰을 받아 같은 `PUT /push-devices/current`에 등록할 수 있는지 확인한다.

## 권장 스택 [제안]

| 영역 | 선택 | 메모 |
| --- | --- | --- |
| 프레임워크 | React + Vite SPA (또는 Next.js) | SEO가 필요 없고 로그인 뒤 화면이 대부분이면 SPA가 단순하다. 팀 결정 |
| 언어 | TypeScript strict + typescript-eslint `strict` | [공식](https://typescript-eslint.io/getting-started/) |
| 라우팅 | React Router 등. [내부 라우트 표](../common/push-deeplink-navigation.md#4-딥링크)와 같은 경로 | 새로 고침·직접 진입 시 SPA fallback |
| 서버 상태 | TanStack Query(`signal` 전달) | RN과 공유 가능 |
| PWA | Web App Manifest + Workbox | |
| 테스트 | Vitest + Testing Library, **Playwright**(E2E), MSW(공용 fixture) | |

RN과 코드를 나눌 범위(API 클라이언트, 타입, 쿼리 키, 문구)는 모노레포 구성 결정과 함께 정한다.

## PWA 기본

- manifest: `name`, `short_name`, 192·512px + `maskable` 아이콘, `start_url`, `scope`, `id`, `display: standalone`. iOS용 `apple-touch-icon`. [공식](https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Manifest)
- 설치 안내: Android Chrome은 `beforeinstallprompt`, **iOS Safari에는 이 이벤트가 없다.** `display-mode: standalone`을 감지해 "공유 → 홈 화면에 추가" 안내를 직접 보여 준다.
- 설치 앱과 Safari 탭은 저장소·쿠키가 분리된다. 설치 후 재로그인 흐름이 필요하다.
- standalone 모드에는 브라우저 뒤로 버튼이 없다. 앱 안에 뒤로 내비게이션을 둔다.

## Service Worker와 캐시

- 새 SW는 기존 탭이 모두 닫힐 때까지 `waiting`이다. "새 버전 있음 → 새로 고침" 안내 후 `skipWaiting()` + reload. 무조건 `skipWaiting`은 열린 탭을 깨뜨릴 수 있다. [공식](https://web.dev/articles/service-worker-lifecycle)
- SW 파일은 `Cache-Control: no-cache`, 해시된 정적 자산은 immutable. `index.html`을 cache-first로 두지 않는다.
- **인증이 필요한 API 응답은 SW에 캐시하지 않는다.** 데이터 캐시는 TanStack Query 한 곳에서. 로그아웃 시 Cache Storage·IndexedDB를 지운다.
- 사진 CDN 이미지는 CacheFirst + `maxEntries`/`maxAgeSeconds`. cross-origin `no-cors` 캐시는 opaque 응답이라 쿼터를 크게 쓴다. [공식](https://developer.chrome.com/docs/workbox/modules/workbox-strategies)
- SW 장애 대비 kill switch(빈 SW 배포) 절차를 문서화한다.

## 저장소

- 설치하지 않은 Safari는 7일간 사용하지 않으면 스크립트 저장소(IndexedDB, Cache, SW 등록)를 지운다. 설치 PWA는 제외. 서버가 원본이고 로컬은 캐시일 뿐이다. [공식](https://web.dev/articles/storage-for-the-web)
- `navigator.storage.persist()`는 요청일 뿐 보장되지 않는다.

## 사진

- `<input type="file" accept="image/*">`. `capture`를 붙이면 카메라를 바로 연다(힌트). 붙이지 않으면 OS가 카메라·앨범 선택을 준다.
- `createImageBitmap(file, { imageOrientation: 'from-image', resizeWidth })`로 디코딩 단계에서 축소, Worker + `OffscreenCanvas`에서 인코딩.
- `canvas.toBlob(cb, 'image/webp', q)` 결과의 `type`이 `image/webp`인지 반드시 확인한다. Safari는 WebP 인코딩을 지원하지 않을 수 있고 그러면 PNG로 떨어진다. WASM 인코더를 대안으로 둔다. [media-upload.md §3.1](../common/media-upload.md#31-인코딩)
- HEIC는 브라우저가 디코딩하지 못할 수 있다. iOS Safari는 선택 시 JPEG로 바꿔 주는 경우가 많지만 보장하지 않는다. 디코딩 실패를 오류 상태로 처리한다.
- 업로드 진행률은 `XMLHttpRequest.upload.onprogress`(fetch는 업로드 진행률 미지원). `URL.revokeObjectURL` 해제.
- 탭을 닫거나 iOS가 백그라운드로 보내면 업로드가 끊긴다. 업로드 중 이탈 경고(`beforeunload`)를 둔다. 이것은 [허용된 차이](../common/testing-parity.md#5-허용된-차이-기록)다.

## 네트워크

- `fetch`에는 타임아웃이 없다. `AbortSignal.any([signal, AbortSignal.timeout(ms)])`. 4xx/5xx에서 reject하지 않으므로 `res.ok`를 검사한다. [공식](https://developer.mozilla.org/en-US/docs/Web/API/AbortSignal/timeout_static)
- `Retry-After` 등 응답 헤더를 읽으려면 서버가 `Access-Control-Expose-Headers`로 노출해야 한다.
- 여러 탭이 동시에 토큰을 갱신하면 rotation 때문에 세션이 폐기된다. 갱신은 Web Locks API(`navigator.locks.request`)나 `BroadcastChannel`로 탭 간 하나만. [networking §4](../common/networking-errors-retry.md#4-토큰-갱신)
- 오프라인 판단은 요청 결과로. `navigator.onLine`은 힌트.

## 웹 푸시

- iOS·iPadOS 16.4+에서 **홈 화면에 설치한 웹앱만** 받는다. 권한 요청은 사용자 클릭 핸들러 안에서. [공식](https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/)
- 모든 푸시는 보이는 알림을 띄운다(`userVisibleOnly`). 탭 처리는 SW `notificationclick`에서 `clients.openWindow`/`focus`.
- 미설치·거부 사용자는 알림함과 화면 복귀 시 다시 불러오기로 같은 정보를 본다.

## 레이아웃

- `viewport-fit=cover` + `env(safe-area-inset-*)`. 하단 탭 바에 `padding-bottom: env(safe-area-inset-bottom)`.
- `100vh` 대신 `dvh`/`svh`(주소창 때문에). 키보드는 `visualViewport`.
- iOS에서 input 글자가 16px 미만이면 포커스 시 자동 확대된다. [일반지식]
- 데스크톱에서는 모바일 폭 레이아웃을 가운데 두고 키보드·마우스로 모든 기능을 쓸 수 있게 한다.

## 성능 (Core Web Vitals)

- 기준: LCP 2.5초, INP 200ms, CLS 0.1 이하(75퍼센타일). [공식](https://web.dev/articles/inp)
- 첫 화면 이미지는 lazy 금지 + `fetchpriority="high"`. 나머지는 `loading="lazy"`, `width`/`height` 또는 `aspect-ratio`로 자리 예약.
- 긴 피드는 가상화. 좋아요는 낙관적 반영으로 INP를 지킨다.
- `web-vitals`로 실사용 측정.

## 보안

- CSP, HSTS, `X-Content-Type-Options`. 서드파티 스크립트 최소화. 소스맵은 공개 호스팅에 올리지 않는다.
- `VITE_*`/`NEXT_PUBLIC_*`는 공개 값이다.

## 체크리스트

- [ ] CORS·토큰 보관·소셜 로그인 audience·웹 푸시를 백엔드와 합의
- [ ] manifest·아이콘·iOS 설치 안내
- [ ] SW 업데이트 안내, 인증 응답 비캐시, 로그아웃 시 캐시 삭제
- [ ] 업로드 결과가 실제 WebP인지(Safari 포함) 확인
- [ ] 탭 간 토큰 갱신 단일화
- [ ] iOS 설치 PWA 실기기에서 로그인·업로드·푸시 흐름 확인
- [ ] Core Web Vitals 기준 충족
