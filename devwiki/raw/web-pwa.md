---
title: Mobile-first Web App / PWA 개발 가이드
topic: Chalkak 웹앱(설치형 PWA) 재구현을 위한 PWA·모바일 웹 개발 지식
collected: 2026-10-27
verification: |
  [검증됨] 표시 = 이번 수집에서 WebFetch로 본문 확인 (webkit.org Web Push, web.dev storage-for-the-web, MDN Background Sync, web.dev INP).
  그 외 링크는 공식 문서의 정식(canonical) URL이며 본문 재확인은 하지 않음 -> 구현 전 해당 페이지에서 최신 내용 재확인 필요.
sources: 문서 말미 "출처 목록" 참조
---

# 1. PWA 기본 (Manifest / 설치 가능성)

## 핵심 개념
- PWA = HTTPS + Web App Manifest + Service Worker(선택이지만 오프라인/푸시에 필요). Manifest의 `display: standalone|fullscreen`이면 브라우저 UI 없는 앱처럼 실행된다. ([MDN Web app manifest](https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Manifest))
- 설치 가능성 기준(Chromium): manifest에 `name`/`short_name`, `icons`(192, 512px), `start_url`, `display`, `prefers_related_applications` 아님 등. ([web.dev Installable](https://web.dev/articles/install-criteria))
- Chrome/Android는 `beforeinstallprompt`로 설치 UX를 커스터마이즈할 수 있으나, iOS Safari는 이 이벤트가 없다(홈 화면에 추가 = 공유 메뉴 수동). ([MDN Trigger install prompt](https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/How_to/Trigger_install_prompt))

## 공식 가이드라인
- `maskable` 아이콘 별도 제공, `theme_color`/`background_color`, `id` 지정(앱 식별 안정화), `scope` 명시. ([MDN Manifest](https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Manifest))
- iOS용으로 `<link rel="apple-touch-icon">`, `<meta name="apple-mobile-web-app-capable">` 보조 태그를 둔다(iOS는 manifest 지원이 늘었으나 아이콘은 apple-touch-icon 권장). ([WebKit: Web Push for Web Apps](https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/))
- 설치 안내는 사용자 맥락(예: 첫 사진 업로드 후)에서 비침투적으로. iOS는 "공유 > 홈 화면에 추가" 안내 UI를 직접 제공해야 한다.

## 자주 하는 실수
- iOS에서 `beforeinstallprompt`를 기다리며 설치 버튼이 영영 안 뜸 -> UA/standalone(`matchMedia('(display-mode: standalone)')`, `navigator.standalone`) 감지 후 별도 안내.
- manifest `start_url`이 인증 리다이렉트를 타서 설치 앱이 깨짐, `scope` 밖 링크가 브라우저 탭으로 열림.
- 아이콘에 투명 배경만 제공해 Android 적응형 아이콘이 어색함(maskable 누락).

## 체크리스트
- [ ] HTTPS, manifest 링크, 192/512 + maskable 아이콘
- [ ] `display: standalone`, `start_url`, `scope`, `id`
- [ ] iOS 전용 meta/apple-touch-icon
- [ ] iOS 수동 설치 안내 UI, Android는 `beforeinstallprompt` 활용
- [ ] Lighthouse/DevTools Application 탭에서 installability 확인

# 2. Service Worker 수명주기와 업데이트 함정

## 핵심 개념
- 수명주기: register -> install -> (waiting) -> activate -> controlling. 새 SW는 기존 탭이 모두 닫히기 전까지 `waiting`에 머문다(기본). ([web.dev SW lifecycle](https://web.dev/articles/service-worker-lifecycle))
- 브라우저는 SW 스크립트를 바이트 단위로 비교해 업데이트하며, SW 파일 자체가 HTTP 캐시되면 업데이트가 지연된다(`Cache-Control: no-cache` 권장). ([web.dev SW lifecycle](https://web.dev/articles/service-worker-lifecycle))

## 공식 가이드라인
- 새 버전 배포 시 UI에 "새 버전 사용 가능 - 새로고침" 토스트를 띄우고 사용자가 동의하면 `skipWaiting()` + `clients.claim()` + 페이지 reload. 무조건 `skipWaiting`은 열려 있는 페이지와 새 SW의 캐시/스키마가 불일치할 수 있다. ([web.dev Handling SW updates](https://web.dev/articles/service-worker-lifecycle#skip_the_waiting_phase), [Workbox: Advanced recipes](https://developer.chrome.com/docs/workbox/handling-service-worker-updates/))
- 예전 버전 캐시는 `activate`에서 정리(Workbox는 `cleanupOutdatedCaches()`). ([Workbox precaching](https://developer.chrome.com/docs/workbox/modules/workbox-precaching))

## 자주 하는 실수
- `index.html`을 cache-first로 두어 사용자가 영원히 옛 번들을 봄(HTML은 network-first 또는 precache+업데이트 UX).
- SW가 API(인증 필요) 응답까지 캐시해 로그아웃 후 타인 데이터 노출.
- SW 에러로 앱 전체 먹통 -> kill switch(빈 SW 배포) 계획 없음.
- 개발 중 SW 캐시로 변경이 안 보임(DevTools "Update on reload"/"Bypass for network").

## 체크리스트
- [ ] SW 파일은 no-cache, 해시된 정적 자산은 immutable
- [ ] 업데이트 감지 UX(`registration.waiting`/`workbox-window` `waiting` 이벤트)
- [ ] 인증 API 응답/개인 데이터는 SW 캐시 제외, 로그아웃 시 캐시 삭제
- [ ] SW 롤백/kill switch 절차 문서화

# 3. Workbox 캐싱 전략

## 핵심 개념
- 전략: CacheFirst(해시된 정적 자산, 이미지), NetworkFirst(HTML, 자주 바뀌는 API), StaleWhileRevalidate(아바타 등 약간 오래돼도 되는 리소스), NetworkOnly, CacheOnly. ([Workbox Strategies](https://developer.chrome.com/docs/workbox/modules/workbox-strategies))
- `workbox-expiration`으로 항목 수·기간 제한, `workbox-cacheable-response`로 opaque(status 0) 응답 캐싱 제어. ([Workbox expiration](https://developer.chrome.com/docs/workbox/modules/workbox-expiration))

## 공식 가이드라인
- 앱 셸/빌드 산출물은 precache, 런타임은 `registerRoute`로 리소스 유형별 전략 지정. ([Workbox routing](https://developer.chrome.com/docs/workbox/modules/workbox-routing))
- 이미지 캐시는 반드시 `maxEntries`/`maxAgeSeconds` 설정(사진 앱은 용량이 빠르게 증가, 스토리지 쿼터/eviction 대상). ([web.dev Storage](https://web.dev/articles/storage-for-the-web) [검증됨])
- Chalkak 예시: 사진 CDN 이미지 = CacheFirst(+expiration), 피드 API = NetworkFirst(타임아웃 3s) 또는 TanStack Query 캐시에 위임, 알림/좋아요 쓰기 = NetworkOnly.

## 자주 하는 실수
- cross-origin 이미지를 `no-cors`로 캐시해 opaque 응답이 쿼터를 크게 소모(패딩).
- Range 요청(동영상) 미처리, POST 캐시 시도.
- SW 캐시와 HTTP 캐시와 React Query 캐시가 중복·충돌 -> 데이터 계층은 한 곳(보통 Query)으로.

## 체크리스트
- [ ] 리소스별 전략 표 작성, API는 기본 NetworkOnly
- [ ] expiration 설정, 캐시 이름 버전화
- [ ] 인증 헤더/쿠키 요청의 캐시 키 주의

# 4. 오프라인 지원

## 핵심 개념
- `navigator.onLine`은 "네트워크 인터페이스 존재 여부"일 뿐이라 `true`여도 실제 인터넷 연결을 보장하지 않는다(MDN도 false만 신뢰 가능하다고 설명). 실제 요청 실패(fetch reject/timeout)를 기준으로 판단. ([MDN navigator.onLine](https://developer.mozilla.org/en-US/docs/Web/API/Navigator/onLine))
- IndexedDB: 구조화 데이터·Blob 저장(오프라인 큐, 임시 업로드 사진). 래퍼로 `idb` 권장. ([MDN IndexedDB](https://developer.mozilla.org/en-US/docs/Web/API/IndexedDB_API))
- Background Sync API는 Baseline이 아니다(제한적 지원, 특히 Safari/Firefox 미지원). 반드시 feature detect 후 폴백. [검증됨] ([MDN Background Synchronization API](https://developer.mozilla.org/en-US/docs/Web/API/Background_Synchronization_API))

## 공식 가이드라인
- 쓰기 작업은 IndexedDB outbox에 저장 -> 앱이 열려 있을 때 `online` 이벤트/주기 재시도 + 앱 포그라운드 복귀(`visibilitychange`) 시 flush. Background Sync는 지원 브라우저에서만 보너스.
- Workbox `BackgroundSyncPlugin`은 Chromium 한정; 미지원 브라우저는 큐를 메모리/페이지에서 재생. ([Workbox background sync](https://developer.chrome.com/docs/workbox/modules/workbox-background-sync))
- 오프라인일 땐 읽기 전용 모드 + "오프라인" 배너, 쓰기는 "대기 중" 상태 표시(낙관적 UI + 실패 시 롤백).

## 자주 하는 실수
- `navigator.onLine === true`를 믿고 에러 처리 생략(captive portal, 약한 신호).
- 요청 재시도 시 멱등성 없음 -> 좋아요/업로드 중복. Idempotency-Key 필요(서버 협의).
- IndexedDB에 대용량 사진을 쌓고 정리하지 않음 -> 쿼터/eviction.

## 체크리스트
- [ ] 연결 판단은 실제 요청 결과 기준
- [ ] outbox + 멱등 키 + 재시도 백오프
- [ ] Background Sync 미지원 폴백

# 5. Web Push

## 핵심 개념
- 표준: Push API + Notifications API + Service Worker. 서버는 VAPID 키쌍(Voluntary Application Server Identification)으로 푸시 서비스에 서버를 식별, 클라이언트는 `pushManager.subscribe({userVisibleOnly:true, applicationServerKey})`. ([MDN Push API](https://developer.mozilla.org/en-US/docs/Web/API/Push_API), [RFC 8292 VAPID](https://datatracker.ietf.org/doc/html/rfc8292))
- iOS/iPadOS 16.4+에서 지원, 단 홈 화면에 추가된 웹앱(manifest `display`가 `standalone` 또는 `fullscreen`)만 가능. 일반 Safari 탭에서는 불가. 권한 요청은 반드시 사용자 직접 상호작용(버튼 탭) 응답으로. 서버가 URL을 제한한다면 `*.push.apple.com` 허용. Apple Developer Program 불필요. [검증됨] ([WebKit blog](https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/))
- 구독 객체(endpoint, keys)를 서버에 저장하고 사용자·기기별로 관리. 만료/410 Gone 응답 시 삭제. ([web.dev Push notifications overview](https://web.dev/articles/push-notifications-overview))

## 공식 가이드라인(권한 UX)
- 페이지 로드 직후 권한 팝업 금지. 가치 설명 후 사용자가 "알림 켜기"를 누를 때 `Notification.requestPermission()` 호출. 거부(denied)되면 재요청 불가 -> 브라우저/OS 설정 안내. ([web.dev Notification permission UX](https://web.dev/articles/push-notifications-permissions-ux))
- 모든 푸시는 사용자에게 보이는 알림을 표시해야 한다(`userVisibleOnly`). 특히 Safari는 silent push 시 구독이 취소될 수 있다고 알려짐(Apple 문서 재확인 필요).
- 알림 클릭: SW의 `notificationclick`에서 `clients.openWindow`/`focus`로 딥링크 처리. ([MDN notificationclick](https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerGlobalScope/notificationclick_event))

## 자주 하는 실수
- iOS에서 설치 전(탭 상태)에 푸시 가능하다고 가정.
- 권한 요청을 `useEffect` 마운트 시 자동 호출(제스처 아님 -> iOS 거부/무시).
- 구독 갱신(`pushsubscriptionchange`) 미처리, 만료 토큰 누적.
- 기기당 FCM 토큰 모델(네이티브)과 Web Push 구독 모델을 같은 테이블로 섞음 -> 플랫폼 구분 컬럼 필요.

## 체크리스트
- [ ] VAPID 키 서버 보관(비공개키 노출 금지)
- [ ] 권한 요청은 클릭 핸들러 안에서
- [ ] 구독 저장/삭제 API, 410/404 정리
- [ ] 알림 클릭 딥링크, 앱 내 알림함 폴백(푸시 거부 사용자)

# 6. iOS Safari PWA 제약과 특이점

## 핵심 개념
- 스토리지 eviction: Safari(iOS 13.4+/macOS 13.1+)는 script-writable storage(IndexedDB, SW 등록, Cache API)에 7일 비사용 시 삭제 정책이 있으나 홈 화면 설치 PWA는 제외. Safari 쿼터 약 1GB 수준, 설치 PWA는 별도 컨테이너. [검증됨] ([web.dev Storage for the web](https://web.dev/articles/storage-for-the-web))
- 기본 저장소는 best-effort; `navigator.storage.persist()`로 persistent 요청 가능(브라우저별 정책). ([MDN StorageManager.persist](https://developer.mozilla.org/en-US/docs/Web/API/StorageManager/persist))
- `100vh`는 모바일 주소창 때문에 실제 보이는 영역보다 큼 -> `dvh`/`svh`/`lvh` 사용. ([web.dev Large, small, dynamic viewport units](https://web.dev/blog/viewport-units))
- 노치/홈 인디케이터: `<meta name="viewport" content="..., viewport-fit=cover">` + `env(safe-area-inset-top|bottom|left|right)`. ([MDN env()](https://developer.mozilla.org/en-US/docs/Web/CSS/env), [WebKit: Designing Websites for iPhone X](https://webkit.org/blog/7929/designing-websites-for-iphone-x/))
- `beforeinstallprompt` 없음(위 1장). 홈 화면 앱과 Safari의 저장소·쿠키는 분리되어, 탭에서 로그인한 세션이 설치 앱에 공유되지 않는다(설치 후 재로그인 UX 필요).
- standalone 모드에는 뒤로가기 UI가 없으므로 앱 내 네비게이션 제공.

## 자주 하는 실수
- 하단 고정 탭바가 홈 인디케이터에 가림(`padding-bottom: env(safe-area-inset-bottom)` 누락).
- 입력창 포커스 시 iOS 자동 줌: input `font-size` 16px 미만일 때 발생(흔히 알려진 동작, 공식 문서 재확인 필요).
- OAuth 로그인 리다이렉트가 standalone에서 외부 Safari로 빠져 세션 단절(인앱 로그인 흐름 테스트 필수).
- 키보드 노출 시 visualViewport 변화 미처리로 고정 요소가 가려짐(`visualViewport` API 활용). ([MDN VisualViewport](https://developer.mozilla.org/en-US/docs/Web/API/VisualViewport))

## 체크리스트
- [ ] `viewport-fit=cover` + safe-area 패딩
- [ ] `dvh` 사용, 폴백 `vh`
- [ ] 설치 앱에서 로그인/딥링크/푸시 구독 전 흐름 실기기 테스트
- [ ] 중요 데이터는 서버가 원본, 로컬은 캐시로만 간주

# 7. 모바일 이미지 업로드

## 핵심 개념
- `<input type="file" accept="image/*" capture="environment">`: 카메라 직접 실행 힌트. `capture` 생략 시 OS가 카메라/앨범 선택 시트를 제공(사진 앱 대부분 이쪽이 안전). ([MDN capture attribute](https://developer.mozilla.org/en-US/docs/Web/HTML/Attributes/capture), [MDN input type=file](https://developer.mozilla.org/en-US/docs/Web/HTML/Element/input/file))
- HEIC: iOS는 카메라 사진을 HEIC로 저장하지만 Safari는 `<input accept="image/*">` 선택 시 보통 JPEG로 변환해 전달하는 것으로 알려져 있음(공식 확인 필요). 다른 브라우저/데스크톱 환경에서는 HEIC가 직접 올 수 있어 서버·클라이언트에서 처리 정책 필요. HEIC는 Chrome 등에서 `<img>` 디코딩 불가.
- 클라이언트 리사이즈: `createImageBitmap(file, {imageOrientation: 'from-image'})` -> `OffscreenCanvas`/`canvas.drawImage` -> `canvas.toBlob('image/jpeg', 0.85)`. ([MDN createImageBitmap](https://developer.mozilla.org/en-US/docs/Web/API/Window/createImageBitmap), [MDN HTMLCanvasElement.toBlob](https://developer.mozilla.org/en-US/docs/Web/API/HTMLCanvasElement/toBlob))
- EXIF Orientation: 현대 브라우저는 `image-orientation: from-image`가 기본으로 `<img>`에는 자동 적용되나, canvas로 그릴 때·서버 측 처리(리사이즈 파이프라인)에서 회전이 유실되는 경우가 있다. ([MDN image-orientation](https://developer.mozilla.org/en-US/docs/Web/CSS/image-orientation))
- 캔버스 재인코딩은 EXIF(GPS 포함)를 제거하는 부수효과가 있어 개인정보 측면에서 유리하나 촬영 일시 등 필요한 메타는 별도 추출.

## 공식 가이드라인
- 대용량 업로드는 presigned URL로 스토리지 직접 업로드(서버 부하 감소), 진행률은 `XMLHttpRequest.upload.onprogress`(fetch는 업로드 진행률 미지원).
- 파일 크기·MIME은 클라이언트에서 선검증하되 서버에서 반드시 재검증(매직 바이트).
- 메모리: 12MP+ 사진을 canvas에 그리면 모바일에서 메모리 부족 가능 -> 큰 이미지는 `createImageBitmap` 의 `resizeWidth`로 디코딩 시 축소.

## 자주 하는 실수
- 리사이즈 후 회전이 틀어짐(EXIF 이중 적용 또는 누락).
- `URL.createObjectURL` 해제(`revokeObjectURL`) 누락으로 메모리 누수.
- 업로드 중 앱이 백그라운드로 가면 iOS가 요청 중단 -> 재시도/이어올리기 필요.
- canvas `toBlob` 결과가 PNG 기본이라 용량 폭증(mime 명시).

## 체크리스트
- [ ] accept 지정, capture 사용 여부 UX 결정
- [ ] 방향 보정 + 최대 변 크기(예: 2048px) + JPEG/WebP 품질 지정
- [ ] 진행률/취소/재시도 UI
- [ ] 서버 재검증 및 AI 리뷰 연동은 업로드 완료 이벤트 기준

# 8. fetch + AbortController 타임아웃

## 핵심 개념
- `fetch`에는 기본 타임아웃이 없다. `AbortSignal.timeout(ms)`(최신 브라우저) 또는 `AbortController` + `setTimeout`으로 구현. 타임아웃 시 `TimeoutError`, 수동 abort는 `AbortError`. ([MDN AbortSignal.timeout](https://developer.mozilla.org/en-US/docs/Web/API/AbortSignal/timeout_static), [MDN AbortController](https://developer.mozilla.org/en-US/docs/Web/API/AbortController))
- 여러 신호 결합: `AbortSignal.any([userSignal, AbortSignal.timeout(10000)])`. ([MDN AbortSignal.any](https://developer.mozilla.org/en-US/docs/Web/API/AbortSignal/any_static))

## 자주 하는 실수
- HTTP 4xx/5xx에서 `fetch`가 reject하지 않음(`response.ok` 확인 필수). ([MDN Using fetch](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API/Using_Fetch))
- 컴포넌트 언마운트 시 abort 안 함 -> 경쟁 상태. TanStack Query는 `signal`을 queryFn에 넘겨준다. ([TanStack Query: Query Cancellation](https://tanstack.com/query/latest/docs/framework/react/guides/query-cancellation))
- 타임아웃 후 비멱등 POST를 무작정 재시도 -> 중복 생성.

## 체크리스트
- [ ] 공통 fetch 래퍼에 타임아웃·ok 검사·에러 정규화
- [ ] 업로드는 별도(긴) 타임아웃 정책

# 9. Core Web Vitals와 이미지 최적화

## 핵심 개념
- 3대 지표: LCP(로딩), INP(응답성, FID 대체), CLS(시각 안정성). 75퍼센타일 기준. INP 좋음 기준 200ms 이하. [검증됨] ([web.dev INP](https://web.dev/articles/inp)) LCP 2.5s 이하, CLS 0.1 이하(권장 임계값, 각 문서 참조). ([web.dev LCP](https://web.dev/articles/lcp), [web.dev CLS](https://web.dev/articles/cls))
- 이미지: `srcset`/`sizes`로 반응형, `loading="lazy"`(뷰포트 밖), LCP 이미지는 lazy 금지 + `fetchpriority="high"`, `width`/`height`(또는 `aspect-ratio`) 지정으로 CLS 방지. ([web.dev Responsive images](https://web.dev/learn/design/responsive-images), [web.dev Browser-level image lazy-loading](https://web.dev/articles/browser-level-image-lazy-loading), [web.dev Optimize LCP](https://web.dev/articles/optimize-lcp))
- 포맷: AVIF > WebP > JPEG, `<picture>`로 폴백. ([web.dev Serve images in modern formats](https://web.dev/articles/choose-the-right-image-format))

## 공식 가이드라인
- 무한 스크롤 피드: 이미지 placeholder(blurhash/dominant color), 고정 비율 카드, 가상화(virtualization)는 INP/메모리에 유리.
- INP 개선: 긴 작업 분할(`scheduler.yield()` 등), 좋아요 버튼은 낙관적 UI로 즉시 반응. ([web.dev Optimize INP](https://web.dev/articles/optimize-inp))
- `web-vitals` 라이브러리로 실사용(RUM) 측정. ([GitHub web-vitals](https://github.com/GoogleChrome/web-vitals))

## 자주 하는 실수
- 첫 화면 이미지에 `loading="lazy"` 적용 -> LCP 악화.
- 폰트/배너/광고의 늦은 삽입으로 CLS.
- 원본 12MP 이미지를 썸네일에 그대로 사용(서버/CDN 리사이즈 필요).

## 체크리스트
- [ ] 이미지 CDN 변환(w, format=auto), srcset
- [ ] 모든 img에 크기/비율 예약
- [ ] LCP 요소 식별 및 우선 로드
- [ ] RUM 수집, 실기기 중저가 Android로 프로파일링

# 10. 모바일 UX

## 핵심 개념
- 터치 타겟: WCAG 2.2 SC 2.5.8(AA) 최소 24x24 CSS px(예외 있음), SC 2.5.5(AAA) 44x44. 모바일 플랫폼 권장은 약 44-48px. ([W3C: Target Size (Minimum)](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html), [W3C: Target Size (Enhanced)](https://www.w3.org/WAI/WCAG22/Understanding/target-size-enhanced.html))
- viewport: `width=device-width, initial-scale=1`; `user-scalable=no`/`maximum-scale=1`로 확대 막지 말 것(접근성 위반). ([MDN Viewport meta](https://developer.mozilla.org/en-US/docs/Web/HTML/Viewport_meta_tag))
- `prefers-reduced-motion` 존중: 큰 전환·패럴랙스 비활성. ([MDN prefers-reduced-motion](https://developer.mozilla.org/en-US/docs/Web/CSS/@media/prefers-reduced-motion))
- `touch-action: manipulation`으로 더블탭 지연 제거, `overscroll-behavior`로 pull-to-refresh/스크롤 체이닝 제어. ([MDN touch-action](https://developer.mozilla.org/en-US/docs/Web/CSS/touch-action), [MDN overscroll-behavior](https://developer.mozilla.org/en-US/docs/Web/CSS/overscroll-behavior))

## 자주 하는 실수
- hover 의존 UI(터치 기기 불가) -> `@media (hover: hover)`로 한정.
- 레이아웃 시프트를 일으키는 늦은 배너/키보드 대응 미비.
- 스와이프 제스처만 제공하고 버튼 대안 없음(WCAG 2.5.1 Pointer Gestures). ([W3C: Pointer Gestures](https://www.w3.org/WAI/WCAG22/Understanding/pointer-gestures.html))

## 체크리스트
- [ ] 터치 타겟 >= 44px(최소 24px), 간격 확보
- [ ] reduced-motion 분기
- [ ] 제스처에 단일 포인터 대안

# 11. 접근성 (WCAG 2.2, ARIA)

## 핵심 개념
- WCAG 2.2 신규 기준: Target Size(Minimum), Focus Not Obscured, Dragging Movements(드래그 대안), Consistent Help, Accessible Authentication 등. ([W3C: What's New in WCAG 2.2](https://www.w3.org/WAI/standards-guidelines/wcag/new-in-22/))
- ARIA 제1원칙: 네이티브 요소(`<button>`, `<a>`, `<input>`)로 가능하면 ARIA를 쓰지 않는다. 잘못된 ARIA는 없는 것보다 나쁘다. ([W3C: Using ARIA](https://www.w3.org/TR/using-aria/), [W3C APG: No ARIA is better than Bad ARIA](https://www.w3.org/WAI/ARIA/apg/practices/read-me-first/))

## 자주 하는 실수
- `div onClick`을 버튼으로 사용(키보드·스크린리더 불가).
- 이미지 `alt` 누락/무의미("image"), 장식 이미지는 `alt=""`. 사용자 업로드 사진의 alt 정책(캡션/토픽 기반) 필요.
- 모달 포커스 트랩/복귀 미구현, 토스트에 `role="status"`/`aria-live` 누락.
- 색 대비 4.5:1 미달, 좋아요 상태를 색으로만 표현(`aria-pressed` 사용). ([MDN aria-pressed](https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Attributes/aria-pressed))

## 체크리스트
- [ ] 키보드/스크린리더(VoiceOver, TalkBack) 수동 테스트
- [ ] axe 자동 검사를 CI(Playwright)에 포함
- [ ] 폼 레이블, 에러 메시지 연결(`aria-describedby`)

# 12. 프레임워크 선택과 서버 상태

## 핵심 개념
- SPA(React + Vite) vs SSR(Next.js): 로그인 후 앱 성격이 강한 서비스는 SPA/PWA 궁합이 좋고 SW 캐싱 단순. 공개 공유 페이지(OG 메타, SEO)가 필요하면 SSR/SSG 혼합 고려. ([Next.js: PWA guide](https://nextjs.org/docs/app/guides/progressive-web-apps), [React docs: Start a new React project](https://react.dev/learn/start-a-new-react-project))
- TanStack Query: 서버 상태 캐시(staleTime, gcTime, 낙관적 업데이트, 무한 쿼리, 재시도, 포커스 복귀 refetch). 오프라인 영속화는 `persistQueryClient`. ([TanStack Query overview](https://tanstack.com/query/latest/docs/framework/react/overview), [Optimistic Updates](https://tanstack.com/query/latest/docs/framework/react/guides/optimistic-updates), [Network Mode](https://tanstack.com/query/latest/docs/framework/react/guides/network-mode))

## 자주 하는 실수
- 서버 데이터를 전역 클라이언트 스토어(Redux 등)에 복제.
- 낙관적 업데이트 롤백(`onError`)·`invalidateQueries` 누락.
- Next.js 앱 라우터에서 SW 스코프/캐시와 RSC 응답 캐싱 충돌.

## 체크리스트
- [ ] 라우팅/렌더링 방식 결정 근거 기록(공유 링크 미리보기 필요 여부)
- [ ] Query 키 규약, staleTime 기본값, 에러 경계

# 13. 보안

## 핵심 개념
- XSS: 사용자 입력(캡션, 닉네임, 토픽)은 프레임워크 기본 이스케이프에 의존, `dangerouslySetInnerHTML`/`innerHTML` 금지 또는 sanitize(DOMPurify). ([OWASP XSS Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html))
- CSP: 인라인 스크립트 금지, nonce/hash 기반 `script-src`, `object-src 'none'`, `base-uri 'self'`. 이미지 CDN은 `img-src`에 명시. ([MDN CSP](https://developer.mozilla.org/en-US/docs/Web/HTTP/CSP), [web.dev Strict CSP](https://web.dev/articles/strict-csp))
- 토큰 저장: localStorage는 XSS 시 탈취 가능. 가능하면 `HttpOnly; Secure; SameSite` 쿠키에 세션/리프레시 토큰, 액세스 토큰은 메모. 쿠키 사용 시 CSRF 방어(SameSite, CSRF 토큰, Origin 검사) 필요. ([OWASP HTML5 Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html), [OWASP CSRF Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html), [OWASP Session Management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html))
- 소스맵·난독화: 번들은 minify하되 난독화는 보안 수단이 아니다(클라이언트 코드는 본질적으로 공개). 비밀키/로직 신뢰를 클라이언트에 두지 말 것. 소스맵은 공개 배포를 피하고 에러 추적 서비스(Sentry 등)에만 업로드하는 방식을 고려. ([OWASP Web Security Testing Guide - client-side](https://owasp.org/www-project-web-security-testing-guide/), [Chrome DevTools: Source maps](https://developer.chrome.com/docs/devtools/javascript/source-maps))
- SW 보안: HTTPS 필수, SW 범위 제어, 캐시에 토큰/개인정보 저장 금지.

## 자주 하는 실수
- JWT를 localStorage에 저장하고 CSP 없음.
- `target="_blank"`에 `rel="noopener"` 누락(현대 브라우저는 기본 적용이지만 관례 유지).
- 외부 URL(`javascript:`) 렌더링, 사용자 제공 이미지 URL 무검증.
- 푸시 payload에 민감정보 포함.

## 체크리스트
- [ ] CSP report-only로 시작해 강화
- [ ] 쿠키 속성/CSRF 방어 합의(백엔드와)
- [ ] 의존성 감사(npm audit), SRI 적용 가능한 외부 스크립트 최소화

# 14. 테스트

## 핵심 개념
- 단위/컴포넌트: Vitest + Testing Library(사용자 관점 쿼리 `getByRole`). ([Vitest guide](https://vitest.dev/guide/), [Testing Library guiding principles](https://testing-library.com/docs/guiding-principles/))
- E2E: Playwright 모바일 에뮬레이션(`devices['iPhone 13']`, Pixel 등) - viewport, UA, touch, 위치/권한 모킹. 단, WebKit 엔진이어도 실제 iOS Safari/PWA 설치/푸시는 재현 불가 -> 실기기 테스트 병행. ([Playwright Emulation](https://playwright.dev/docs/emulation), [Playwright Service Workers](https://playwright.dev/docs/service-workers))
- 네트워크 모킹: MSW(단위) / Playwright `route`(E2E), 오프라인은 `context.setOffline(true)`. ([MSW](https://mswjs.io/docs/), [Playwright Network](https://playwright.dev/docs/network))

## 자주 하는 실수
- 구현 세부(state)를 테스트해 리팩터링마다 깨짐.
- SW가 켜진 상태의 E2E가 캐시 때문에 flaky -> 테스트에서 SW 비활성/`serviceWorkers: 'block'` 옵션 구분.
- 에뮬레이터만으로 iOS 이슈(safe-area, 키보드, 푸시) 통과로 오판.

## 체크리스트
- [ ] 핵심 플로우 E2E: 로그인, 업로드, 좋아요, 오프라인 복귀
- [ ] axe 접근성 스캔, Lighthouse CI
- [ ] 실기기 매트릭스(iOS 최신/-1, 중저가 Android)

# 15. 흔한 실수 요약
1. iOS 푸시/설치를 Android와 동일하게 가정(탭 상태 푸시 불가, 설치 이벤트 없음).
2. 오래된 SW/캐시로 사용자가 구버전에 갇힘 - 업데이트 UX 부재.
3. `navigator.onLine` 신뢰, 멱등성 없는 재시도.
4. 100vh/safe-area 미처리로 하단 UI 가림.
5. 업로드 이미지 방향·용량 미처리.
6. 토큰을 localStorage에 두고 CSP 미적용.
7. 첫 화면 이미지 lazy, 크기 미예약.
8. 에뮬레이터만 테스트.

# 찰캌 적용 포인트

## 네이티브 대비 웹이 맞추기 어려운 기능과 graceful degradation
| 기능 | 웹 한계 | 폴백/설계 |
|---|---|---|
| 푸시 알림 | iOS는 16.4+ 홈 화면 설치 앱만 가능, 사용자 제스처로 권한 요청 [검증됨] ([WebKit](https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/)) | 설치 유도 온보딩, 거부/미설치 사용자용 앱 내 알림함 + 앱 복귀 시 폴링/refetch |
| 백그라운드 업로드/동기화 | Background Sync 제한적 지원 [검증됨] ([MDN](https://developer.mozilla.org/en-US/docs/Web/API/Background_Synchronization_API)) | 포그라운드 outbox 재시도, "업로드 중 앱을 닫지 마세요" 안내, 실패 시 임시 보관 후 재시도 버튼 |
| 로컬 데이터 보존 | 비설치 Safari는 7일 미사용 시 삭제, 설치 PWA는 제외 [검증됨] ([web.dev](https://web.dev/articles/storage-for-the-web)) | 서버를 원본으로, 로컬은 캐시/임시 드래프트로만 사용; `persist()` 요청 |
| 카메라/갤러리 | 네이티브 카메라 API 대비 제어 제한(`capture`는 힌트), HEIC/EXIF 처리 필요 | 파일 선택 + 클라이언트 리사이즈/방향 보정 + 서버 HEIC 대응 |
| 설치 UX | iOS `beforeinstallprompt` 없음 | 수동 안내 모달(공유 > 홈 화면에 추가) |
| 앱 아이콘 배지, 풍부한 알림 액션 | 플랫폼별 편차 | 있으면 사용(feature detect), 없으면 생략 |
| 제스처/햅틱 | iOS Safari `navigator.vibrate` 미지원(알려짐; 확인 필요) | 시각 피드백으로 대체 |

## 서비스별 설계 메모
- **일일 토픽 피드/좋아요/사인**: TanStack Query 무한 쿼리 + 낙관적 업데이트(좋아요), 실패 시 롤백 토스트. 쓰기는 멱등 키 협의(백엔드).
- **AI 사진 리뷰**: 업로드 후 비동기 결과이면 폴링/푸시 + 앱 내 상태 표시; 타임아웃은 AbortSignal.timeout으로 분리 설정.
- **이미지**: 서버/CDN에서 썸네일·AVIF/WebP 변환, 피드 카드는 고정 비율 + placeholder.
- **인증**: 설치 앱과 Safari 탭의 저장소 분리를 고려해 설치 후 재로그인 흐름 설계; 쿠키 기반이면 CSRF 방어 필요, 토큰 방식이면 메모리+refresh 쿠키 검토. 백엔드 정책 필요 사항은 `docs/business-rules/`를 읽기 전용으로 확인하고 규칙 부재 시 백엔드 확인 대상으로만 제기(CLAUDE.md 지침).
- **배포**: SW 업데이트 토스트, kill switch, 소스맵은 비공개 업로드.

# 출처 목록
(WebFetch로 본문 확인: 1~4번. 나머지는 공식 정식 URL이며 구현 전 최신성 재확인 필요)
1. https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/
2. https://web.dev/articles/storage-for-the-web
3. https://developer.mozilla.org/en-US/docs/Web/API/Background_Synchronization_API
4. https://web.dev/articles/inp
5. https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Manifest
6. https://web.dev/articles/install-criteria
7. https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/How_to/Trigger_install_prompt
8. https://web.dev/articles/service-worker-lifecycle
9. https://developer.chrome.com/docs/workbox/handling-service-worker-updates/
10. https://developer.chrome.com/docs/workbox/modules/workbox-precaching
11. https://developer.chrome.com/docs/workbox/modules/workbox-strategies
12. https://developer.chrome.com/docs/workbox/modules/workbox-expiration
13. https://developer.chrome.com/docs/workbox/modules/workbox-routing
14. https://developer.chrome.com/docs/workbox/modules/workbox-background-sync
15. https://developer.mozilla.org/en-US/docs/Web/API/Navigator/onLine
16. https://developer.mozilla.org/en-US/docs/Web/API/IndexedDB_API
17. https://developer.mozilla.org/en-US/docs/Web/API/Push_API
18. https://datatracker.ietf.org/doc/html/rfc8292
19. https://web.dev/articles/push-notifications-overview
20. https://web.dev/articles/push-notifications-permissions-ux
21. https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerGlobalScope/notificationclick_event
22. https://developer.mozilla.org/en-US/docs/Web/API/StorageManager/persist
23. https://web.dev/blog/viewport-units
24. https://developer.mozilla.org/en-US/docs/Web/CSS/env
25. https://webkit.org/blog/7929/designing-websites-for-iphone-x/
26. https://developer.mozilla.org/en-US/docs/Web/API/VisualViewport
27. https://developer.mozilla.org/en-US/docs/Web/HTML/Attributes/capture
28. https://developer.mozilla.org/en-US/docs/Web/HTML/Element/input/file
29. https://developer.mozilla.org/en-US/docs/Web/API/Window/createImageBitmap
30. https://developer.mozilla.org/en-US/docs/Web/API/HTMLCanvasElement/toBlob
31. https://developer.mozilla.org/en-US/docs/Web/CSS/image-orientation
32. https://developer.mozilla.org/en-US/docs/Web/API/AbortSignal/timeout_static
33. https://developer.mozilla.org/en-US/docs/Web/API/AbortController
34. https://developer.mozilla.org/en-US/docs/Web/API/AbortSignal/any_static
35. https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API/Using_Fetch
36. https://tanstack.com/query/latest/docs/framework/react/guides/query-cancellation
37. https://web.dev/articles/lcp , https://web.dev/articles/cls , https://web.dev/articles/optimize-lcp , https://web.dev/articles/optimize-inp
38. https://web.dev/learn/design/responsive-images
39. https://web.dev/articles/browser-level-image-lazy-loading
40. https://web.dev/articles/choose-the-right-image-format
41. https://github.com/GoogleChrome/web-vitals
42. https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html
43. https://www.w3.org/WAI/WCAG22/Understanding/target-size-enhanced.html
44. https://www.w3.org/WAI/WCAG22/Understanding/pointer-gestures.html
45. https://www.w3.org/WAI/standards-guidelines/wcag/new-in-22/
46. https://www.w3.org/TR/using-aria/
47. https://www.w3.org/WAI/ARIA/apg/practices/read-me-first/
48. https://developer.mozilla.org/en-US/docs/Web/HTML/Viewport_meta_tag
49. https://developer.mozilla.org/en-US/docs/Web/CSS/@media/prefers-reduced-motion
50. https://developer.mozilla.org/en-US/docs/Web/CSS/touch-action
51. https://developer.mozilla.org/en-US/docs/Web/CSS/overscroll-behavior
52. https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Attributes/aria-pressed
53. https://nextjs.org/docs/app/guides/progressive-web-apps
54. https://react.dev/learn/start-a-new-react-project
55. https://tanstack.com/query/latest/docs/framework/react/overview
56. https://tanstack.com/query/latest/docs/framework/react/guides/optimistic-updates
57. https://tanstack.com/query/latest/docs/framework/react/guides/network-mode
58. https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html
59. https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html
60. https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html
61. https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html
62. https://developer.mozilla.org/en-US/docs/Web/HTTP/CSP
63. https://web.dev/articles/strict-csp
64. https://owasp.org/www-project-web-security-testing-guide/
65. https://developer.chrome.com/docs/devtools/javascript/source-maps
66. https://vitest.dev/guide/
67. https://testing-library.com/docs/guiding-principles/
68. https://playwright.dev/docs/emulation
69. https://playwright.dev/docs/service-workers
70. https://playwright.dev/docs/network
71. https://mswjs.io/docs/
