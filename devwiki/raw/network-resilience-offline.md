# 크로스플랫폼 네트워킹 복원력 (Network Resilience, Offline, Optimistic UI)

> 조사일: 2026-10-27 / 대상: iOS(Swift), Android(Kotlin), Flutter(Dart), React Native(JS/TS), Web
> 목적: 5개 플랫폼에서 **동일한 기능 동작**을 보장하기 위한 개념 + 플랫폼별 구현 매핑.
> 표기: `[출처](URL)`은 해당 문장의 근거. `(미검증)`은 본 조사 중 페이지 본문을 직접 확인하지 못했고 공식 문서 위치만 안내한다는 뜻이다.
> 이 문서는 서버 정책을 정의하지 않는다. 서버 에러 코드/멱등성 정책은 `docs/business-rules/README.md`(읽기 전용)와 backend 합의를 따른다.

---

## 0. 한눈에 보는 전체 원칙

| # | 원칙 | 한 줄 요약 |
|---|------|-----------|
| 1 | 에러는 분류하고 삼키지 않는다 | transport / HTTP / decoding / domain 4계층으로 나눠 UI 메시지에 매핑 |
| 2 | 재시도는 멱등한 요청만 | GET/PUT/DELETE는 기본 안전, POST는 Idempotency-Key 필요 |
| 3 | 백오프엔 반드시 jitter | capped exponential + full jitter, Retry-After 우선 |
| 4 | 취소 가능해야 한다 | 화면 이탈 시 요청 취소 (Task / coroutine / CancelToken / AbortController) |
| 5 | 토큰 갱신은 single-flight | 동시 401이 와도 refresh는 1회, 나머지는 대기 후 재시도 |
| 6 | 로컬 DB가 source of truth | UI는 로컬 데이터를 관찰, 네트워크는 동기화 수단 |
| 7 | Optimistic UI는 "목표 상태"를 보낸다 | like 토글이 아니라 `liked=true/false` 를 전송 |
| 8 | 큰 업로드는 OS 백그라운드 API | WorkManager / background URLSession |
| 9 | 장기 작업(AI 리뷰)은 폴링+백오프+상한 | 가능하면 push로 보완 |

---

## 1. 에러 처리 분류 (Error Handling Taxonomy)

### 1.1 핵심 개념
에러를 4계층으로 분리해 각각 다른 정책(재시도 여부, 사용자 메시지, 로깅)을 적용한다.

| 계층 | 예시 | 재시도 | 사용자 메시지 방향 |
|------|------|--------|--------------------|
| Transport | DNS 실패, 연결 끊김, offline, timeout, TLS | 보통 가능(멱등 시) | "네트워크를 확인해 주세요" |
| HTTP status | 401, 403, 404, 409, 422, 429, 5xx | 상태별 상이 | 상태별 안내 |
| Decoding/Parsing | JSON 스키마 불일치, 필수 필드 누락 | 불가(버그) | 일반 오류 + 로그/크래시 리포트 |
| Domain | 서버 body의 `code` (예: 이미 삭제된 게시물, 일일 업로드 한도) | 코드별 | 코드→문구 매핑 테이블 |

- 4xx는 "클라이언트가 요청을 고쳐야 함", 5xx는 "서버 측 문제"로 의미가 갈린다. 상태 코드 의미는 HTTP 표준을 따른다. [출처](https://www.rfc-editor.org/rfc/rfc9110.html)
- 서버 에러 body 형식은 Problem Details(RFC 9457)를 쓰는 것이 표준적이다. Idempotency-Key 드래프트도 에러 body에 Problem Details를 권고한다. [출처](https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/)
- Android 가이드는 로컬 소스 에러는 `catch`로 크래시를 막고, UI 상태는 Loading/Content/Error(LCE) sealed 클래스로 표현하라고 한다. [출처](https://developer.android.com/topic/architecture/data-layer/offline-first)
- Flutter 오프라인 레시피 예제는 `catch (e) { // Handle the error }`를 보여 주는데, 실제로는 에러를 삼키지 말고 상태로 올려야 한다(아래 "자주 하는 실수"). [출처](https://docs.flutter.dev/app-architecture/design-patterns/offline-first)

### 1.2 공통 에러 모델 (플랫폼 무관 의사 코드)
```
AppError =
  | Network(kind: offline | timeout | connectionLost | tls)
  | Http(status, retryAfter?)            // 401은 별도 처리(토큰 갱신 파이프라인)
  | Decoding(cause)
  | Domain(code, message?)               // 서버 error.code 매핑
  | Cancelled                            // 에러 UI 표시 금지
  | Unknown(cause)
```
- `Cancelled`는 사용자 에러가 아니다. 화면 이탈로 인한 취소를 에러 토스트로 보여 주면 안 된다.
- 서버 `code → 사용자 메시지`는 **클라이언트 5종이 같은 표**를 공유해야 한다(i18n 키 기반). 문구를 플랫폼별로 따로 만들지 않는다.

### 1.3 플랫폼별 구현 매핑

| 항목 | iOS | Android | Flutter | RN | Web |
|------|-----|---------|---------|----|-----|
| 결과 타입 | `Result<T, E>` 또는 `async throws` | `Result`/sealed class, `suspend` + try/catch | `Result`(sealed) 또는 `Future` throw | Promise reject + discriminated union | Promise reject + discriminated union |
| transport 에러 | `URLError` (`.notConnectedToInternet`, `.timedOut`, `.cancelled`) | `IOException`, `SocketTimeoutException` | `DioException.type` (`connectionTimeout`, `receiveTimeout`, `connectionError`, `cancel`) | `fetch` TypeError / axios `error.code` | `fetch` TypeError, `AbortError` |
| HTTP status 처리 | `HTTPURLResponse.statusCode` 직접 검사 (URLSession은 4xx/5xx를 throw 하지 않음) | Retrofit `HttpException` / OkHttp `Response.code` | Dio는 기본 4xx/5xx throw, `http`는 안 던짐 | axios는 throw, `fetch`는 안 던짐(`res.ok` 검사) | `fetch`는 4xx/5xx에서 reject 안 함 |
| decoding | `JSONDecoder` → `DecodingError` | kotlinx.serialization / Moshi 예외 | `fromJson` 예외 | zod 등 런타임 검증 권장 | zod 등 런타임 검증 권장 |
| 취소 구분 | `URLError.cancelled` / `CancellationError` | `CancellationException`(재throw 필수) | `DioExceptionType.cancel` | axios `isCancel` | `AbortError` |

- iOS: `URLSession`의 async API 취소는 Swift 구조화된 동시성의 `Task.cancel()`과 연동된다. [출처](https://developer.apple.com/documentation/swift/task/cancel())
- Kotlin: `CancellationException`을 삼키는 `catch (e: Exception)`은 취소를 깨뜨린다(자주 하는 실수 참조). 코루틴 기본 개념은 공식 문서. [출처](https://kotlinlang.org/docs/coroutines-basics.html)
- Web: `fetch`가 HTTP 에러에서 reject하지 않는다는 점, `AbortController` 사용법은 MDN. [출처](https://developer.mozilla.org/en-US/docs/Web/API/AbortController) (미검증: 본문 직접 확인 못함)

### 1.4 자주 하는 실수
1. `catch {}` 로 에러를 삼키고 로딩 스피너만 멈춤 (사용자는 실패를 모름).
2. 4xx/5xx를 transport 에러와 같은 "네트워크 오류" 문구로 처리.
3. 취소(`Cancelled`)를 에러 토스트로 표시.
4. 서버 `code`를 문자열 그대로 사용자에게 노출.
5. `fetch`/URLSession에서 status 검사 없이 decoding 시도 → 의미 없는 DecodingError.
6. Kotlin에서 `catch (e: Exception)`이 `CancellationException`까지 삼킴.

### 1.5 체크리스트
- [ ] 5개 플랫폼이 동일한 `AppError` 분류와 `code→문구` 표를 공유하는가
- [ ] 모든 `catch`가 로그 또는 상태 전이 중 하나는 수행하는가
- [ ] 취소는 별도 분기로 UI 에러에서 제외했는가
- [ ] 401/429/5xx별 정책이 문서화되어 있는가
- [ ] decoding 실패 시 크래시 리포트(요청 ID 포함)를 남기는가

---

## 2. 재시도 (Retry)

### 2.1 핵심 개념
**어떤 요청을 재시도할 수 있나**
- HTTP 표준은 메서드를 safe / idempotent로 구분한다. GET/HEAD/OPTIONS는 safe, PUT/DELETE도 idempotent이며 POST/PATCH는 기본적으로 idempotent가 아니다. 연결 실패 시 클라이언트가 자동 재시도할 수 있는 요청은 idempotent 메서드 기준이다(RFC 9110 §9.2.2, §2.4). [출처](https://www.rfc-editor.org/rfc/rfc9110.html) (§2.4 문장은 확인, 9.2.2 본문은 fetch 요약에 포함되지 않아 목차 수준 확인)
- 비멱등 요청(POST 업로드, 게시물 생성)은 **Idempotency-Key** 헤더로 서버가 중복을 식별하게 한다. IETF 드래프트 `draft-ietf-httpapi-idempotency-key-header-08`(2026-10-22 기준 최신, 아직 RFC 아님):
  - 값은 클라이언트가 생성한 고유 문자열(UUID 권장), 다른 payload에 재사용 금지.
  - 첫 요청은 정상 처리, 완료 후 재시도는 완료된 응답과 일관된 응답, 처리 중 재시도는 **409**, 키 재사용+다른 payload는 **422**, 필수인데 누락은 **400**.
  - 서버는 키 만료 정책을 문서화해야 한다. [출처](https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/)

**어떻게 재시도하나**
- Exponential backoff 단독은 호출 군집(cluster)을 해결하지 못하고, **jitter**가 호출을 시간상 고르게 분산시켜 총 호출 수를 크게 줄인다. AWS Architecture Blog는 jittered backoff의 "구현 복잡도 대비 효과가 크다"고 평가한다. [출처](https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/)
  - Full Jitter: `sleep = random(0, min(cap, base * 2^attempt))` (블로그의 공식 수식은 이미지로 제공되어 본문에서 직접 확인하지 못했으나, 알고리즘 이름과 비교 결과는 위 링크 기준).
  - Full vs Equal Jitter는 호출 수가 비슷, Decorrelated는 더 많은 호출.
- **Retry-After**: 값은 초(정수) 또는 HTTP-date. 503(다운 예상 시간), 429(재요청까지 대기), 3xx에 사용. 있으면 계산된 백오프보다 이를 우선한다. [출처](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Retry-After)
- 429는 RFC 6585에서 정의. [출처](https://www.rfc-editor.org/rfc/rfc6585.html) (미검증)
- 일반적인 재시도 대상: 408, 429, 5xx(500/502/503/504) + 연결 오류. 4xx(400/401/403/404/422)는 재시도하지 않는다. Google Cloud Storage retry 문서가 "idempotent 조건 + truncated exponential backoff + 전체 deadline"을 설명한다. [출처](https://docs.cloud.google.com/storage/docs/retry-strategy) (리다이렉트 URL 확인만 했고 본문은 미확인)
- Android 가이드도 "연결 오류는 exponential backoff로 재시도, 인증 오류는 자격 증명이 유효해지기 전엔 재시도 금지, 재시도 횟수 제한"을 명시한다. [출처](https://developer.android.com/topic/architecture/data-layer/offline-first)
- TanStack Query 기본값: query 실패 시 3회 재시도, `retryDelay`는 1000ms에서 시작해 2배씩 증가하며 최대 30초. (mutation 기본값은 해당 페이지에 없음) [출처](https://tanstack.com/query/latest/docs/framework/react/guides/query-retries)

**재시도 폭풍(retry storm) 방지**: ① jitter ② 최대 시도 횟수(예: 3~5) ③ 전체 deadline ④ Retry-After 존중 ⑤ 계층별 중복 재시도 금지(OkHttp 재시도 + Repository 재시도 + WorkManager 재시도가 곱해지지 않게) ⑥ 서버 과부하(503/429) 시 클라이언트 전역 circuit breaker 또는 쿨다운.

### 2.2 재시도 정책 표 (찰캌 제안)
| 요청 | 메서드 | 자동 재시도 | 조건 |
|------|--------|-------------|------|
| 피드/토픽 조회 | GET | O | backoff+jitter, 최대 3회 |
| 좋아요 설정 | PUT `liked=true/false` | O | 목표 상태 전송 → 멱등 |
| 게시물 삭제 | DELETE | O | 404를 성공으로 간주할지 서버와 합의 |
| 게시물 생성 | POST | 조건부 | Idempotency-Key 필수 |
| 이미지 업로드 | PUT presigned | O (이어올리기) | 3절/7절 |
| 토큰 갱신 | POST | X(1회) | 실패 시 로그아웃 |

### 2.3 플랫폼별 구현 매핑

| 항목 | iOS | Android | Flutter | RN | Web |
|------|-----|---------|---------|----|-----|
| 내장 재시도 | `waitsForConnectivity`로 연결 대기 | OkHttp `retryOnConnectionFailure`(기본 on, 연결 실패만) | 없음(`dio_smart_retry` 등 패키지) | 없음 | 없음 |
| 커스텀 백오프 | `Task.sleep` + 루프 | `delay()` + `retry` 연산자(Flow) | `Future.delayed` | `setTimeout` Promise | `setTimeout` Promise |
| 라이브러리 | 직접 구현 | 직접/ Retrofit CallAdapter | Dio Interceptor | axios-retry, TanStack Query | TanStack Query, SWR |
| 헤더 처리 | `HTTPURLResponse.value(forHTTPHeaderField:)` | `response.header("Retry-After")` | `response.headers` | `res.headers.get` | `res.headers.get` (CORS 시 `Access-Control-Expose-Headers` 필요) |
| 백그라운드 재시도 | background URLSession 자동 | WorkManager `BackoffPolicy.EXPONENTIAL` | `workmanager` 패키지 | 네이티브 모듈/ headless task | Service Worker Background Sync(지원 제한적) |

- iOS `waitsForConnectivity`: 연결이 없을 때 즉시 실패하는 대신 연결 대기. [출처](https://developer.apple.com/documentation/foundation/urlsessionconfiguration/waitsforconnectivity)
- WorkManager: `Result.retry()`와 `setBackoffCriteria(BackoffPolicy.EXPONENTIAL, ...)`로 재시도 정책 설정. [출처](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started)

### 2.4 자주 하는 실수
1. 비멱등 POST를 키 없이 자동 재시도 → 게시물/서명 중복 생성.
2. jitter 없는 고정 간격 재시도 → 서버 복구 직후 동시 폭주.
3. 429/503의 `Retry-After`를 무시.
4. 401/403/422까지 재시도.
5. 계층별 재시도 중첩(총 시도 = 3×3×3).
6. 최대 시도/최대 시간 없이 무한 재시도(배터리·데이터 소모).
7. 사용자 취소·화면 이탈 후에도 재시도 계속.

### 2.5 체크리스트
- [ ] 메서드별 재시도 가능 여부 표가 있고 5개 플랫폼이 동일한가
- [ ] 모든 비멱등 POST에 Idempotency-Key(UUID, 요청당 1회 생성·재시도 시 재사용)를 붙이는가
- [ ] base/cap/maxAttempts/deadline 상수가 공통 문서에 있는가
- [ ] Retry-After 파싱(초/날짜 둘 다)을 구현했는가
- [ ] 재시도 계층이 한 곳으로 정해져 있는가

---

## 3. 타임아웃과 지연 (Timeouts & Latency)

### 3.1 핵심 개념
- **Connect timeout / Read(response) timeout / Total(deadline)** 을 구분한다. 연결은 짧게(예: 10s), 응답 대기는 API 성격별, 전체 deadline은 별도 상한을 둔다. 업로드 등 큰 body는 total timeout을 요청 단위가 아니라 "진행 없음(idle)" 기준으로 두는 것이 적절하다.
- **취소**: 화면을 떠나면 진행 중 요청을 취소한다. 구조화된 동시성(scope 취소)과 연결하면 누수 방지.
- **Debounce / Throttle**: 검색 입력은 debounce(예: 300ms), 스크롤 로딩/버튼 연타는 throttle 또는 in-flight 가드.
- **Loading indicator 임계값**: 사용자는 약 0.1초 이내를 "즉각", 1초 이내를 "생각의 흐름이 유지됨", 10초를 주의력 한계로 인식한다(Nielsen). 짧은 로딩에서 스피너를 깜빡이지 않도록 **표시 지연(~300ms)과 최소 표시 시간(~500ms)** 을 둔다. [출처](https://www.nngroup.com/articles/response-times-3-important-limits/) (미검증: 페이지 직접 확인 못함. 300ms/500ms는 업계 관례이지 표준이 아니므로 팀 상수로 확정할 것)
- **Skeleton UI**: 레이아웃을 유지한 채 placeholder를 보여 체감 속도를 높인다(콘텐츠 형태를 아는 목록/카드에 적합).
- **느린 네트워크 테스트**:
  - iOS: Network Link Conditioner (Xcode Additional Tools에 포함, 설정 > Developer에서 프로필 선택). 공식 URL을 확인하지 못해 출처 미기재(미검증).
  - Android emulator: 네트워크 속도/지연 설정(`-netspeed`, `-netdelay`, Extended controls). [출처](https://developer.android.com/studio/run/emulator-networking)
  - Chrome DevTools: Network 패널 throttling + 커스텀 프로필(다운/업 속도, 지연, 패킷 손실). [출처](https://developer.chrome.com/docs/devtools/settings/throttling)

### 3.2 플랫폼별 구현 매핑

| 항목 | iOS | Android | Flutter | RN | Web |
|------|-----|---------|---------|----|-----|
| 요청 timeout | `URLSessionConfiguration.timeoutIntervalForRequest` (패킷 간 idle 기준), `timeoutIntervalForResource` (전체) | OkHttp `connectTimeout/readTimeout/writeTimeout/callTimeout` | Dio `connectTimeout/receiveTimeout/sendTimeout` | axios `timeout`; `fetch`엔 없음 → `AbortSignal.timeout()` | `AbortSignal.timeout(ms)` |
| 취소 | `Task.cancel()` | coroutine scope 취소 / `Call.cancel()` | Dio `CancelToken` | `AbortController` | `AbortController` |
| 화면 이탈 연동 | SwiftUI `.task {}` 자동 취소 | `viewModelScope`/`repeatOnLifecycle` | `dispose()`에서 `cancel` | `useEffect` cleanup | `useEffect` cleanup / TanStack `signal` |
| debounce | Combine `debounce` / `AsyncAlgorithms` | Flow `debounce` | `Timer` / rxdart | lodash/`useDeferredValue` | 동일 |
| 느린망 테스트 | Network Link Conditioner | Emulator network speed/delay | 에뮬레이터/시뮬레이터 설정 따름 | 동일(플랫폼별) | DevTools throttling |

- iOS timeout 속성 정의: `timeoutIntervalForRequest`는 데이터가 오지 않고 기다리는 최대 시간. [출처](https://developer.apple.com/documentation/foundation/urlsessionconfiguration/timeoutintervalforrequest)
- iOS async 요청 API: `URLSession.data(for:delegate:)`. [출처](https://developer.apple.com/documentation/foundation/urlsession/data(for:delegate:))
- TanStack Query는 query function에 `signal`(AbortSignal)을 전달해 컴포넌트 unmount/키 변경 시 취소를 지원한다. [출처](https://tanstack.com/query/latest/docs/framework/react/guides/query-cancellation) (미검증)

### 3.3 자주 하는 실수
1. 기본 timeout(플랫폼별로 다름: URLSession 60s 등)에 의존 → 플랫폼별 UX 불일치.
2. 화면을 떠나도 요청이 살아 있어 setState/emit on disposed.
3. 0.1초 만에 끝나는 요청에 스피너를 깜빡임.
4. 개발은 항상 빠른 Wi-Fi에서만 테스트.
5. 취소된 요청 결과가 새 화면의 상태를 덮어씀(race).
6. 업로드에 짧은 total timeout 적용 → 큰 사진 실패.

### 3.4 체크리스트
- [ ] 5개 플랫폼 timeout 상수를 문서 한 곳에서 관리
- [ ] 로딩 표시 지연/최소 표시 시간 공통 상수화
- [ ] 화면 이탈 시 취소 테스트 존재
- [ ] 3G/high-latency/packet loss 프로필로 수동 QA 시나리오 수행
- [ ] 목록 화면은 skeleton, 버튼 액션은 inline progress

---

## 4. 토큰 갱신 (Token Refresh, single-flight)

### 4.1 핵심 개념
- Access token 만료 → 401 → refresh token으로 갱신 → 원 요청 재실행. 문제는 **동시 요청 N개가 모두 401**을 받을 때 refresh가 N번 호출되고, refresh token rotation 환경에서는 두 번째 호출이 실패해 **강제 로그아웃**이 일어나는 것이다.
- 해법 = **single-flight**: 갱신이 진행 중이면 나머지는 같은 결과를 기다린다. 갱신 성공 시 대기 요청은 새 토큰으로 재시도, **갱신 실패(401/403, refresh 만료)** 시 모든 대기 요청을 실패시키고 로그아웃(토큰/캐시 삭제, 로그인 화면 이동).
- 추가 규칙: ① refresh 요청 자체는 인터셉터를 타지 않게(무한 루프 방지) ② 재시도는 요청당 1회 ③ 네트워크 오류로 refresh가 실패한 경우(서버가 거부한 것이 아님)는 로그아웃하지 말고 에러로 전파 ④ 이미 새 토큰으로 갱신된 상태라면(다른 요청이 먼저 갱신) 재갱신 없이 새 토큰으로 재시도.
- Android 가이드는 "인증 오류는 자격 증명이 유효해지기 전에 재시도하지 말라"고 명시한다. [출처](https://developer.android.com/topic/architecture/data-layer/offline-first)

### 4.2 플랫폼별 구현 매핑

| 항목 | iOS | Android | Flutter | RN | Web |
|------|-----|---------|---------|----|-----|
| 후크 | 직접 래퍼(`URLSession` 위 `APIClient`) | OkHttp `Authenticator` (401 응답 시 호출) | Dio `QueuedInterceptor` | axios response interceptor | axios/fetch 래퍼 |
| single-flight | `actor TokenStore` + 진행 중 `Task<Token,Error>` 공유 | `synchronized`/`Mutex` + 토큰 비교(double-check) | `QueuedInterceptor`가 요청별 큐 처리, 또는 `Completer` 공유 | 전역 `refreshPromise` 공유 | 전역 `refreshPromise` 공유 |
| 토큰 저장 | Keychain | EncryptedSharedPreferences/Keystore | `flutter_secure_storage` | Keychain/Keystore 래퍼 | httpOnly cookie 권장(서버 협의) / 메모리 |
| 실패 시 | 로그아웃 이벤트 방송 | 로그아웃 이벤트(SharedFlow) | 이벤트 스트림 | 이벤트 emitter | 이벤트/라우터 이동 |

- OkHttp `Authenticator`: 서버가 401로 인증을 요구할 때 OkHttp가 호출하는 훅. (공식: https://square.github.io/okhttp/ — 개별 Authenticator 페이지는 fetch 실패로 미검증)
- Dio `QueuedInterceptor`: `onRequest/onResponse/onError`가 **각각 별도 큐**에서 순차 처리되며(콜백 종류 간에는 병렬), 동시 요청 시 직렬화에 쓴다. 단 큐 직렬화 만으로 "한 번만 갱신"이 보장되지는 않으므로 갱신 전 현재 토큰과 요청 시 토큰을 비교하는 가드가 필요하다. [출처](https://pub.dev/documentation/dio/latest/dio/QueuedInterceptor-class.html)
- axios interceptors 사용법. [출처](https://axios-http.com/docs/interceptors) (미검증)

### 4.3 의사 코드 (공통)
```
func authorizedRequest(req):
  token = tokenStore.current()
  res = send(req.with(token))
  if res.status != 401: return res
  newToken = singleFlightRefresh(failedToken = token)   // 진행 중이면 await
  res2 = send(req.with(newToken))                        // 1회만
  return res2

singleFlightRefresh(failedToken):
  if tokenStore.current() != failedToken: return tokenStore.current()  // 이미 갱신됨
  if inFlight == nil: inFlight = refreshCall()           // refresh는 인터셉터 우회
  try: return await inFlight
  catch RefreshRejected: logout(); throw
  finally: inFlight = nil
```

### 4.4 자주 하는 실수
1. 동시 401마다 refresh 호출 → rotation 충돌로 로그아웃.
2. refresh 요청에도 인터셉터가 붙어 무한 재귀.
3. 오프라인 때문에 refresh가 실패했는데 로그아웃.
4. 재시도 후에도 401인데 계속 재시도.
5. 갱신 완료 전에 시작한 요청이 오래된 토큰으로 계속 전송.
6. 로그아웃 시 진행 중 요청/캐시/푸시 토큰 정리 누락.

### 4.5 체크리스트
- [ ] "동시 401 5개 → refresh 1회" 자동 테스트 (플랫폼별)
- [ ] refresh 실패(서버 거부) vs refresh 네트워크 오류 구분
- [ ] 로그아웃 시 사용자 데이터·로컬 DB·푸시 토큰 정리
- [ ] 토큰은 안전 저장소에만 저장

---

## 5. Offline-first

### 5.1 핵심 개념
- Android 공식: offline-first 앱은 핵심 기능을 네트워크 없이 수행. **로컬 데이터 소스가 canonical source of truth**이며 상위 계층은 로컬만 읽고, 네트워크 호출은 Repository가 하고 결과로 로컬을 갱신. 읽기는 `Flow` 같은 관찰 가능 타입으로 노출. [출처](https://developer.android.com/topic/architecture/data-layer/offline-first)
- 쓰기 전략 3가지(Android): **Online-only**(네트워크 먼저, 성공 시 로컬), **Queued**(큐에 넣고 온라인 시 비움; 분석/로그), **Lazy**(로컬 먼저 쓰고 네트워크는 가능한 빨리; 중요 데이터, 충돌 처리 필요). [출처](https://developer.android.com/topic/architecture/data-layer/offline-first)
- 동기화: Pull-based(화면 진입 시), Push-based(서버 신호로 stale 시 갱신), Hybrid. 충돌: 모바일에선 **last write wins**(타임스탬프)가 흔하고, 서버가 최종 권위. WorkManager + `NetworkType.CONNECTED` 제약으로 동기화. [출처](https://developer.android.com/topic/architecture/data-layer/offline-first)
- Flutter 공식: Repository가 SSOT. 읽기 = ① 네트워크→실패 시 DB fallback ② **Stream으로 캐시 먼저 emit 후 서버 값 emit(권장)** ③ 로컬 전용+`sync()`. 쓰기 = online-only vs offline-first(DB 먼저, API 실패 시 sync가 재시도). 동기화는 `Timer.periodic`, `synchronized` 플래그, `workmanager`, FCM push. [출처](https://docs.flutter.dev/app-architecture/design-patterns/offline-first)
- **Outbox(sync queue) 패턴**: 쓰기 의도를 로컬 DB `outbox` 테이블에 (id, type, payload, idempotencyKey, createdAt, attempts, state)로 저장 → 백그라운드 워커가 순서대로 전송 → 성공 시 삭제/마킹. 크래시·재시작에도 보존된다. 같은 대상 연산은 병합(좋아요 on→off는 최종 상태만).
- **연결 상태 ≠ 인터넷 도달성**: 
  - Web `navigator.onLine`: `true`는 인터넷 도달을 뜻하지 않으며(LAN만 연결돼도 true, 가상 어댑터 등), 값이 오래될 수 있어 MDN은 이것으로 기능을 비활성화하지 말고 힌트로만 쓰라고 한다. [출처](https://developer.mozilla.org/en-US/docs/Web/API/Navigator/onLine)
  - Android `ConnectivityManager`/`NetworkCapabilities`: `NET_CAPABILITY_INTERNET`(선언) vs `NET_CAPABILITY_VALIDATED`(검증됨)를 구분. [출처](https://developer.android.com/develop/connectivity/network-ops/reading-network-state)
  - iOS `NWPathMonitor`: path 상태(`satisfied` 등)와 인터페이스 종류를 제공. 이것도 "서버에 닿는다"는 보장은 아님. [출처](https://developer.apple.com/documentation/network/nwpathmonitor)
  - 결론: 연결 상태는 **UX 힌트와 동기화 트리거**로만 쓰고, 실제 성공 여부는 요청 결과로 판단.

### 5.2 플랫폼별 구현 매핑

| 항목 | iOS | Android | Flutter | RN | Web |
|------|-----|---------|---------|----|-----|
| 로컬 DB | SwiftData / Core Data / GRDB | Room | drift / sqflite / Isar 대안 | SQLite(op-sqlite 등) / WatermelonDB / MMKV(작은 데이터) | IndexedDB (Dexie 등) |
| 관찰 | `@Query`, Combine, `AsyncStream` | `Flow` / `StateFlow` | `Stream` / `ChangeNotifier` | 구독 훅 / TanStack persist | live query / TanStack persist |
| 연결 감지 | `NWPathMonitor` | `ConnectivityManager.NetworkCallback` | `connectivity_plus` | `@react-native-community/netinfo` | `navigator.onLine` + online/offline 이벤트 |
| outbox 실행 | BGTaskScheduler / background URLSession | WorkManager (제약+백오프) | `workmanager` | 네이티브 WorkManager/BGTask 브리지 | Background Sync API(제한적)/앱 열린 동안 재생 |
| push 트리거 | silent push(APNs) | FCM data message | FCM | FCM/APNs | Web Push |

- `connectivity_plus`는 연결 유형(Wi-Fi/모바일 등)을 알려 주는 패키지이며 인터넷 도달을 보장하지 않는다. Flutter 레시피도 sync 시 이를 활용하라고만 안내. [출처](https://pub.dev/packages/connectivity_plus), [출처](https://docs.flutter.dev/app-architecture/design-patterns/offline-first)
- RN NetInfo: https://github.com/react-native-netinfo/react-native-netinfo (미검증; `isInternetReachable` 필드가 reachability 검사 결과).
- Web Background Sync: https://developer.mozilla.org/en-US/docs/Web/API/Background_Synchronization_API (미검증; 브라우저 지원이 제한적이므로 의존 금지).

### 5.3 자주 하는 실수
1. `onLine`/`isConnected`가 true이면 요청이 성공한다고 가정.
2. offline이면 모든 UI를 막음(읽기는 가능해야 함).
3. UI가 네트워크 응답을 직접 렌더(SSOT 이중화) → 화면별 상태 불일치.
4. outbox 없이 메모리 큐 사용 → 앱 종료 시 유실.
5. 서버 ID 없는 낙관적 생성물의 임시 ID 처리 누락(서버 ID로 치환 필요).
6. 충돌 정책 미정의(동시에 다른 기기에서 수정).

### 5.4 체크리스트
- [ ] 어떤 데이터가 offline 읽기 필수인지(피드 캐시, 내 게시물) 정의
- [ ] 쓰기별 전략(online-only / queued / lazy) 표 확정
- [ ] outbox 스키마와 최대 보존 기간 정의
- [ ] 연결 복귀 시 재생 순서·병합 규칙·실패 시 사용자 알림
- [ ] 서버 권위 + 충돌 정책(LWW 또는 서버 거부 시 롤백) 합의

---

## 6. Optimistic UI

### 6.1 핵심 개념
- 패턴: **UI 먼저 갱신 → 백그라운드 동기화 → 실패 시 롤백 + 에러 안내**. Flutter 레시피는 "이미 상태면 무시(탭 중복 가드) → 낙관적 상태 설정+notify → try 호출 → catch에서 롤백+error 플래그 → finally notify", 에러는 1회성 플래그로 SnackBar 후 리셋을 권장. [출처](https://docs.flutter.dev/app-architecture/design-patterns/optimistic-state)
- **React `useOptimistic`**: Action/Transition 안에서 setter 호출, pending 동안만 낙관적 값을 보여 주고 Action이 끝나면 부모 `value`로 수렴. Action이 throw 하면 `value`가 바뀌지 않아 **자동 롤백**. `value`가 도중에 바뀌면 reducer가 최신 값 위에서 재실행. Transition 밖 호출은 경고 후 값이 되돌아감. [출처](https://react.dev/reference/react/useOptimistic)
- **TanStack Query**: 방법 ① UI에서 `variables` 사용(캐시 미변경, 롤백 불필요, 한 곳만 표시할 때) ② `onMutate`에서 `cancelQueries` → 스냅샷 → `setQueryData` → 스냅샷 반환, `onError`에서 복구, `onSettled`에서 `invalidateQueries`. 여러 컴포넌트가 같은 데이터를 보면 ②. [출처](https://tanstack.com/query/latest/docs/framework/react/guides/optimistic-updates)
- **Android/iOS 등가물**: Android는 Room에 먼저 쓰고(`Lazy` write) Flow로 UI 자동 반영, 서버 실패 시 outbox 재시도 또는 롤백. iOS는 `@Observable` 상태/SwiftData를 먼저 갱신하고 `Task`로 전송, 실패 시 이전 값 복원(공식 별도 가이드 없음 → 위 패턴을 적용).
- **좋아요/취소 멱등성**: "토글(POST /like)" 대신 **목표 상태 전송**(`PUT /posts/{id}/like {liked: true}` 등). 재시도·중복 탭·요청 순서 역전에도 최종 상태가 결정적이다. 
- **빠른 연타 race 처리**: 
  1. 로컬 상태는 즉시 반영, 네트워크는 **post별 직렬화/coalescing**(in-flight 중이면 최종 목표 상태만 기억했다가 완료 후 1회 더 전송).
  2. 응답이 로컬 최신 의도를 덮어쓰지 않도록, 응답 반영 시 "그 사이 더 새로운 의도가 있는가"를 검사.
  3. 롤백 시 "이전 스냅샷"이 아니라 "서버가 확인한 마지막 상태"로 복원.
  4. like count는 서버 값을 최종으로 받아 보정(낙관적 +1/-1은 임시).

### 6.2 플랫폼별 구현 매핑

| 항목 | iOS | Android | Flutter | RN | Web |
|------|-----|---------|---------|----|-----|
| 낙관적 상태 | `@Observable` ViewModel / SwiftData | Room 선쓰기 + Flow / `StateFlow` | ViewModel `ChangeNotifier` (공식 레시피) | TanStack Query `onMutate` / zustand | `useOptimistic` / TanStack Query |
| 롤백 | catch에서 이전 값 복원 | catch에서 복원 또는 outbox 상태 | catch에서 복원 + error 플래그 | `onError` + 스냅샷 | `useOptimistic` 자동 / `onError` |
| 연타 가드 | in-flight `Task` 보관, coalescing | `Mutex`/`conflate` Channel | in-flight flag, 마지막 의도 저장 | mutation key 직렬화 | 동일 |
| 재조정 | 응답으로 count 보정 | 서버 값으로 Room 갱신 | repository 값으로 교체 | `invalidateQueries` | `invalidateQueries` |

### 6.3 자주 하는 실수
1. 토글 API를 낙관적으로 호출 → 재시도/중복 시 반대 상태가 됨.
2. 연타마다 요청 N개 병렬 전송 → 순서 역전으로 서버 최종 상태가 UI와 다름.
3. 롤백 없이 실패를 무시(UI와 서버 불일치 영구화).
4. 롤백하면서 에러 안내 생략(Flutter 레시피도 이를 나쁜 UX로 지적).
5. 낙관적으로 만든 임시 ID를 서버 ID로 치환하지 않음.
6. `useOptimistic`을 Transition 밖에서 호출.
7. TanStack에서 `cancelQueries` 누락 → 진행 중 refetch가 낙관적 값을 덮어씀.

### 6.4 체크리스트
- [ ] 좋아요/서명은 목표 상태 전송 API(PUT)인지 backend와 확인
- [ ] 연타 시나리오(10회 연속 탭, 네트워크 느림) 테스트
- [ ] 실패 시 롤백 + 사용자 피드백 + 재시도 경로
- [ ] 서버 응답으로 count/상태 재조정

---

## 7. 안정적인 이미지 업로드 (Reliable Background Upload)

### 7.1 핵심 개념
- **압축/리사이즈 후 업로드**: 업로드 전 해상도·품질 제한으로 전송량과 실패 확률을 줄인다(EXIF 방향 보정, 위치정보 제거 정책은 서비스 정책으로 결정).
- **Presigned URL**: 서버가 짧은 수명의 서명 URL을 발급 → 클라이언트가 스토리지로 직접 PUT. 앱 서버 대역폭 절감. 서명 만료 시 새 URL 요청 필요. (AWS 문서: https://docs.aws.amazon.com/AmazonS3/latest/userguide/PresignedUrlUploadObject.html — 미검증)
- **Resumable/multipart**: 큰 파일은 청크 업로드로 중단 지점부터 재개. (Google Cloud resumable upload: https://cloud.google.com/storage/docs/resumable-uploads — 미검증). 사진 한 장 수 MB 수준이면 단일 PUT + 재시도로 충분한 경우가 많다.
- **OS 백그라운드 API**:
  - iOS **background URLSession**: `URLSessionConfiguration.background(withIdentifier:)`; 앱이 suspend/종료돼도 시스템이 전송을 계속하고 완료 시 앱을 relaunch 할 수 있음. 업로드는 **파일 기반**(`uploadTask(with:fromFile:)`), delegate 필수, data task/completion handler 불가. [출처](https://developer.apple.com/documentation/foundation/urlsessionconfiguration/background(withidentifier:)) (설명 요약 일부는 일반 지식 기반이므로 라이브 문서 재확인 필요), 가이드: [출처](https://developer.apple.com/documentation/foundation/downloading-files-in-the-background)
  - Android **WorkManager**: 앱이 종료되거나 재부팅돼도 실행되는 지연 가능(deferrable) 작업. 공식 예시가 이미지 업로드. 제약(`NetworkType`)·백오프·`Result.retry()` 지원. [출처](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started), 제약: [출처](https://developer.android.com/reference/androidx/work/Constraints)
  - 장시간 사용자 시작 작업은 foreground service(long-running worker). [출처](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)
- **파이프라인(권장)**: ① 선택 → ② 로컬 임시 파일로 압축 저장 → ③ outbox에 UPLOAD 등록(idempotencyKey) → ④ presigned URL 발급 → ⑤ PUT → ⑥ 게시물 생성 API(Idempotency-Key) → ⑦ 완료/실패를 UI(로컬 DB 상태)에 반영. 각 단계가 독립적으로 재시도 가능해야 한다.

### 7.2 플랫폼별 구현 매핑

| 항목 | iOS | Android | Flutter | RN | Web |
|------|-----|---------|---------|----|-----|
| 백그라운드 업로드 | background `URLSession` (+ 파일 업로드) | WorkManager (+ OkHttp) | `workmanager` / `background_downloader` 류 패키지 | 네이티브 모듈(`react-native-background-upload` 류), 앱 상태 주의 | 탭이 열려 있는 동안만(Service Worker Background Sync는 제한적) |
| 압축 | `UIImage.jpegData`/ImageIO 다운샘플 | `Bitmap` 다운샘플 + `Bitmap.compress` | `flutter_image_compress` | `react-native-image-resizer` 류 | Canvas / `createImageBitmap` |
| 진행률 | delegate `didSendBodyData` | `setProgress` + `WorkInfo` | Dio `onSendProgress` | axios `onUploadProgress` | XHR `upload.onprogress` (fetch 업로드 진행률 제한) |
| 재시도 | 시스템이 자동/ 앱이 재등록 | `Result.retry()` + backoff | 패키지 정책 | 래퍼 정책 | 수동 |
| 중복 방지 | 업로드 task description에 키 | unique work name (`KEEP`) | 로컬 DB 상태 | 로컬 DB 상태 | IndexedDB 상태 |

- 위 서드파티 패키지 이름은 예시이며 선택 시 유지보수 상태를 별도 검증해야 한다(본 조사에서 미검증).
- Android Now in Android 샘플은 unique work + `ExistingWorkPolicy.KEEP`으로 동기화 중복 실행을 막는다. [출처](https://developer.android.com/topic/architecture/data-layer/offline-first)

### 7.3 자주 하는 실수
1. 앱이 백그라운드로 가면 업로드가 죽음(포그라운드 `URLSession`/코루틴 스코프만 사용).
2. iOS background session에 `Data` body 업로드 시도(파일이어야 함).
3. 압축 없이 원본 12MP 업로드.
4. presigned URL이 만료된 뒤 재시도(새 URL 발급 로직 없음).
5. 업로드 성공 후 게시물 생성 실패 시 고아 파일/중복 게시물.
6. 재시도 시 새 Idempotency-Key 생성.
7. 임시 파일을 앱 재시작 전에 삭제.

### 7.4 체크리스트
- [ ] 업로드는 OS 백그라운드 API 사용, 포그라운드 전용 아님
- [ ] 압축 규격(최대 변 길이, JPEG 품질, EXIF 정책) 공통 상수
- [ ] presigned URL 만료 처리
- [ ] 진행률/실패/재시도 상태를 로컬 DB에 저장해 UI가 관찰
- [ ] 업로드 취소·삭제 시 서버 정리 경로

---

## 8. Callbacks vs async 패러다임

### 8.1 핵심 개념
- Callback 기반은 중첩(callback hell), 에러 전파 분산, 취소 어려움 문제가 있다. 현대 언어는 **순차 코드처럼 쓰는 비동기**(async/await, coroutines)와 **구조화된 동시성(structured concurrency)** 으로 이를 대체한다. 부모 scope가 취소되면 자식 작업도 취소된다.
- Callback → async 변환: 1회성 callback은 continuation(`withCheckedThrowingContinuation` / `suspendCancellableCoroutine` / `Completer` / `new Promise`)으로 감싼다. 반드시 **정확히 한 번 resume** 하고 **취소 시 정리**를 연결한다.
- 공식 개념: Swift concurrency [출처](https://developer.apple.com/documentation/swift/concurrency), Kotlin coroutines [출처](https://kotlinlang.org/docs/coroutines-basics.html), Dart async/await [출처](https://dart.dev/libraries/async/async-await) (미검증), JS Promise/async는 MDN.

### 8.2 개념 매핑

| 개념 | Swift | Kotlin | Dart | JS/TS |
|------|-------|--------|------|-------|
| 비동기 함수 | `async throws` | `suspend fun` | `Future<T>` + `async` | `Promise<T>` + `async` |
| 대기 | `await` | 호출 자체 | `await` | `await` |
| 병렬 | `async let`, `TaskGroup` | `async {}` + `awaitAll`, `coroutineScope` | `Future.wait` | `Promise.all` / `allSettled` |
| 스트림 | `AsyncSequence`/`AsyncStream` | `Flow` | `Stream` | AsyncIterator / RxJS |
| 취소 | 협력적 `Task.cancel()` + `Task.checkCancellation()` | 협력적 `Job.cancel()`, `ensureActive()` | 내장 취소 없음 → `CancelToken`/`StreamSubscription.cancel` | `AbortController` (협력적) |
| scope | `Task`, `TaskGroup`, SwiftUI `.task` | `viewModelScope`, `lifecycleScope`, `coroutineScope` | State `dispose` 수동 | `useEffect` cleanup |
| 메인 스레드 | `@MainActor` | `Dispatchers.Main` | 단일 isolate(UI) | 단일 스레드 이벤트 루프 |
| callback 브리지 | `withCheckedThrowingContinuation` | `suspendCancellableCoroutine` | `Completer` | `new Promise` |

- Swift: `Task.cancel()`는 **협력적** 취소(작업이 확인해야 함). [출처](https://developer.apple.com/documentation/swift/task/cancel())
- Dart/JS는 Future/Promise 자체에 취소가 없다 → 취소 토큰/AbortSignal을 함수 인자로 전달하는 설계가 필요. 이 차이가 5개 플랫폼 중 **취소 동작이 가장 달라지기 쉬운 지점**이다.

### 8.3 자주 하는 실수
1. continuation을 0회 또는 2회 resume.
2. 취소를 무시하고 작업 계속(협력적 취소 확인 누락).
3. `GlobalScope`/`Task.detached`/무관 Future 남발 → 수명 누수.
4. 병렬 작업에서 하나의 실패가 나머지를 취소하는지(`allSettled` vs `all`) 불명확.
5. 메인 스레드/UI 업데이트 스레드 혼동(`@MainActor`, `Dispatchers.Main`).
6. Promise를 await 없이 두어 unhandled rejection.

### 8.4 체크리스트
- [ ] 새 코드는 callback 대신 async 계열 API 사용, 레거시는 브리지 한 곳에 격리
- [ ] 모든 장시간 작업이 취소 신호를 받고 반응
- [ ] ViewModel/화면 scope에 작업이 묶여 있음
- [ ] unhandled rejection/uncaught coroutine 예외 핸들러 존재

---

## 9. 장기 작업(AI 리뷰): Polling vs Push

### 9.1 핵심 개념
- **Polling**: 구현 단순, 서버 변경 없이 가능, 대신 지연·배터리·트래픽 비용. **Push**: 즉시성·효율은 좋으나 OS 알림 권한/전달 보장 안 됨(전달 지연·유실 가능), 앱이 꺼져 있을 때만 유용. 권장 = **push를 힌트로, polling을 보증(fallback)으로** 하는 하이브리드. Flutter 레시피도 push와 background task를 조합 가능하다고 본다. [출처](https://docs.flutter.dev/app-architecture/design-patterns/offline-first)
- Polling 설계:
  1. 작업 생성 시 `jobId` 반환(서버가 202 Accepted + 상태 URL 제공하는 것이 이상적).
  2. 간격: 1s → 2s → 4s → … 상한 10~15s (backoff + jitter, 응답의 `Retry-After`가 있으면 우선). [출처](https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/), [출처](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Retry-After)
  3. **최대 지속 시간**(예: 2~5분) 후 폴링 중단 → "나중에 알려드릴게요" + push/다음 진입 시 재조회.
  4. **백그라운드 전환 시 중단**, 포그라운드 복귀 시 즉시 1회 조회 후 재개. 화면 이탈 시 취소.
  5. 종료 상태(`COMPLETED`/`FAILED`)는 폴링 중단 + 결과를 로컬 DB에 저장(SSOT).
  6. 폴링 요청의 개별 실패는 재시도 한도 내에서 흡수하되 연속 실패 시 사용자 안내.
- TanStack Query `refetchInterval`은 함수형으로 상태 기반 중단/간격 변경이 가능하다(미검증: https://tanstack.com/query/latest/docs/framework/react/guides/query-retries 와 별개 페이지).

### 9.2 플랫폼별 구현 매핑

| 항목 | iOS | Android | Flutter | RN | Web |
|------|-----|---------|---------|----|-----|
| 폴링 루프 | `Task` 루프 + `Task.sleep` | coroutine `while(isActive){delay}` / Flow | `Stream.periodic`/재귀 `Future.delayed` | `setTimeout` 재귀 / TanStack `refetchInterval` | 동일 |
| 백그라운드 감지 | `scenePhase` | `repeatOnLifecycle(STARTED)` | `WidgetsBindingObserver.didChangeAppLifecycleState` | `AppState` | `document.visibilitychange` |
| 종료 후 알림 | APNs (alert/silent) | FCM | FCM | FCM/APNs | Web Push |
| 앱 종료 중 확인 | push로 유도, 진입 시 재조회 | WorkManager periodic(최소 15분 주기 제약 — 공식 WorkManager 문서 참조) | `workmanager` | 네이티브 | Service Worker push |

### 9.3 자주 하는 실수
1. 고정 1초 폴링을 무기한 유지.
2. 백그라운드/화면 이탈 후에도 폴링 지속.
3. 종료 상태를 못 알아보고 계속 폴링(알 수 없는 enum 값 처리 누락).
4. push만 믿고 fallback 없음(권한 거부·유실 시 결과 확인 불가).
5. 폴링 응답마다 UI 전체 재렌더·스피너 깜빡임.
6. 5개 플랫폼의 최대 시간/간격이 달라 동작이 서로 다름.

### 9.4 체크리스트
- [ ] 간격/상한/최대 지속 시간 공통 상수
- [ ] lifecycle 연동(백그라운드 중단, 복귀 즉시 조회)
- [ ] push 수신 시 로컬 DB 갱신 + 화면 반영
- [ ] 최대 시간 초과 UX 문구 합의

---

## 10. 찰캌 적용 포인트

> 아래는 본 조사에서 도출한 **제안**이다. 서비스 정책(멱등 API, 에러 코드, 제한 시간 등)은 `docs/business-rules/README.md` 및 backend 합의가 우선이며, client 작업에서는 규칙이 없거나 서버 동작과 충돌할 때 backend 확인 대상으로 보고한다.

1. **공통 에러 계약**: 서버 error body `{code, message?}` → 5개 플랫폼 공통 `code→i18n 키` 표. `Cancelled`는 UI 비노출. (1절)
2. **좋아요/싸인은 목표 상태 API**: toggle이 아닌 `liked: true|false`(PUT) 확인 필요. 아니라면 backend에 멱등 전환 요청. (2·6절)
3. **게시물 생성 POST에 Idempotency-Key**: 업로드→생성 재시도 시 중복 게시물 방지. backend 지원 여부 확인(드래프트 기준 409/422 동작과 키 만료 정책 합의). (2·7절)
4. **재시도 상수 공통화**: base 1s, cap 30s(TanStack 기본과 유사), maxAttempts 3, full jitter, Retry-After 우선. 계층 중복 재시도 금지. (2절)
5. **Single-flight refresh**: 동시 401 → refresh 1회 → 실패 시 전체 로그아웃. 플랫폼별 "동시 401 5개" 테스트 필수. (4절)
6. **로컬 DB = SSOT**: 피드·내 게시물·알림은 로컬 캐시 우선 읽기, 쓰기는 Lazy(좋아요/싸인) vs Online-only(삭제/신고 등)로 구분. 연결 상태는 힌트일 뿐. (5절)
7. **Optimistic UI**: 좋아요 즉시 반영 + post별 coalescing + 서버 count로 재조정 + 실패 시 롤백/토스트. (6절)
8. **이미지 업로드**: 압축 → outbox → presigned PUT → 게시물 생성. iOS background URLSession(파일 기반), Android WorkManager. RN/Flutter/Web은 한계를 문서화(Web은 탭 유지 필요). (7절)
9. **AI 리뷰 폴링**: 1s→10s 백오프, 최대 지속 시간, 백그라운드 중단, push 보완, 종료 상태는 로컬 DB 저장. (9절)
10. **로딩 UX 상수**: 스피너 표시 지연 ~300ms/최소 표시 시간 ~500ms(팀 합의 필요), 목록은 skeleton. (3절)
11. **QA 시나리오**: 비행기 모드 전환, 느린 3G(Network Link Conditioner / emulator / DevTools), 업로드 중 앱 종료, 토큰 만료 직후 동시 요청, 연타 좋아요.
12. **플랫폼 차이 고지**: Dart/JS는 Future/Promise 취소가 없어 `CancelToken`/`AbortSignal` 전달 규약이 필요하고, Web은 background 처리에 한계가 있어 "동일 동작"의 범위를 사전에 정의해야 한다.

### 정책 확인이 필요한 쟁점 (backend 확인 대상)
- 좋아요/싸인 API가 멱등 목표 상태 방식인가, 토글인가
- 게시물 생성의 Idempotency-Key 지원과 키 TTL
- 429 시 `Retry-After` 제공 여부
- refresh token rotation 여부 및 재사용 감지 정책
- AI 리뷰 결과 상태 enum, 예상 소요 시간, push 발송 여부
- 이미지 업로드 방식(presigned vs 서버 경유), 최대 크기/형식

---

## 11. 출처 목록

**표준 / 일반**
- RFC 9110 HTTP Semantics — https://www.rfc-editor.org/rfc/rfc9110.html (§2.4 확인, §9.2.2/10.2.3 본문은 목차 수준)
- IETF Idempotency-Key draft — https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/ (확인)
- RFC 6585 (429) — https://www.rfc-editor.org/rfc/rfc6585.html (미검증)
- MDN Retry-After — https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Retry-After (확인)
- MDN navigator.onLine — https://developer.mozilla.org/en-US/docs/Web/API/Navigator/onLine (확인)
- MDN AbortController — https://developer.mozilla.org/en-US/docs/Web/API/AbortController (미검증)
- AWS Architecture Blog, Exponential Backoff And Jitter — https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/ (확인)
- Google Cloud Storage retry strategy — https://docs.cloud.google.com/storage/docs/retry-strategy (미검증)
- Google Cloud resumable uploads — https://cloud.google.com/storage/docs/resumable-uploads (미검증)
- AWS S3 presigned upload — https://docs.aws.amazon.com/AmazonS3/latest/userguide/PresignedUrlUploadObject.html (미검증)
- Nielsen, Response Times — https://www.nngroup.com/articles/response-times-3-important-limits/ (미검증)

**Android**
- Build an offline-first app — https://developer.android.com/topic/architecture/data-layer/offline-first (확인)
- WorkManager 시작하기 — https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started (확인)
- WorkManager Constraints — https://developer.android.com/reference/androidx/work/Constraints
- Long-running workers — https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running
- Reading network state — https://developer.android.com/develop/connectivity/network-ops/reading-network-state
- Emulator networking — https://developer.android.com/studio/run/emulator-networking
- OkHttp — https://square.github.io/okhttp/ (미검증)

**Apple**
- Background URLSession — https://developer.apple.com/documentation/foundation/urlsessionconfiguration/background(withidentifier:)
- Downloading files in the background — https://developer.apple.com/documentation/foundation/downloading-files-in-the-background
- waitsForConnectivity — https://developer.apple.com/documentation/foundation/urlsessionconfiguration/waitsforconnectivity
- timeoutIntervalForRequest — https://developer.apple.com/documentation/foundation/urlsessionconfiguration/timeoutintervalforrequest
- URLSession data(for:delegate:) — https://developer.apple.com/documentation/foundation/urlsession/data(for:delegate:)
- NWPathMonitor — https://developer.apple.com/documentation/network/nwpathmonitor
- Swift concurrency — https://developer.apple.com/documentation/swift/concurrency
- Task.cancel() — https://developer.apple.com/documentation/swift/task/cancel()
- (참고: Network Link Conditioner 관련 URL 하나가 404였음. 도구는 Xcode Additional Tools에 포함)

**Flutter / Dart**
- Offline-first — https://docs.flutter.dev/app-architecture/design-patterns/offline-first (확인)
- Optimistic state — https://docs.flutter.dev/app-architecture/design-patterns/optimistic-state (확인)
- Dio QueuedInterceptor — https://pub.dev/documentation/dio/latest/dio/QueuedInterceptor-class.html (확인)
- connectivity_plus — https://pub.dev/packages/connectivity_plus
- Dart async/await — https://dart.dev/libraries/async/async-await (미검증)

**React / RN / Web**
- React useOptimistic — https://react.dev/reference/react/useOptimistic (확인)
- TanStack Optimistic Updates — https://tanstack.com/query/latest/docs/framework/react/guides/optimistic-updates (확인)
- TanStack Query Retries — https://tanstack.com/query/latest/docs/framework/react/guides/query-retries (확인)
- TanStack Query Cancellation — https://tanstack.com/query/latest/docs/framework/react/guides/query-cancellation (미검증)
- axios interceptors — https://axios-http.com/docs/interceptors (미검증)
- React Native NetInfo — https://github.com/react-native-netinfo/react-native-netinfo (미검증)
- Chrome DevTools throttling — https://developer.chrome.com/docs/devtools/settings/throttling (확인; 네트워크 프리셋 설명은 해당 페이지에 없고 커스텀 프로필만 기술)
- MDN Background Synchronization API — https://developer.mozilla.org/en-US/docs/Web/API/Background_Synchronization_API (미검증)

**Kotlin**
- Coroutines basics — https://kotlinlang.org/docs/coroutines-basics.html
