# 예외 처리, 재시도, 타임아웃, 토큰 갱신

근거: [raw/network-resilience-offline.md §1~4](../../raw/network-resilience-offline.md), 서버 코드([ErrorCode](../../../backend/src/main/java/com/chalkak/backend/exception/ErrorCode.java), [DomainErrorHttpMapper](../../../backend/src/main/java/com/chalkak/backend/exception/DomainErrorHttpMapper.java), [RefreshTokenService](../../../backend/src/main/java/com/chalkak/backend/auth/service/token/RefreshTokenService.java)). 기존 구현 참고: Android [TokenAuthenticator.kt](../../../client/android/app/src/main/java/com/stonefive/chalkak/data/remote/TokenAuthenticator.kt), iOS [AuthenticatedHTTPClient.swift](../../../client/iOS/chalkak/chalkak/Core/Auth/AuthenticatedHTTPClient.swift).

## 1. 에러 분류

### 1.1 공통 에러 모델

다섯 클라이언트는 같은 이름의 분류를 쓴다. Service 계층이 플랫폼 예외를 이 타입으로 바꾸고, ViewModel은 이 타입만 본다. [제안]

```text
AppError =
  | Network(kind: offline | timeout | connectionLost | tls)   // 연결 자체 실패
  | Server(status, errorCode?, message?, retryAfter?)         // 서버가 응답함
  | Decoding(cause)                                           // 응답 형식 불일치 = 버그
  | Cancelled                                                 // 화면 이탈 등. UI에 표시하지 않는다
  | Unknown(cause)
```

| 계층 | 재시도 | 사용자 메시지 | 로깅 |
| --- | --- | --- | --- |
| Network | 멱등 요청만 자동 재시도 | "네트워크 연결을 확인해 주세요" + 다시 시도 | breadcrumb |
| Server 5xx | 멱등 요청만 자동 재시도 | 일반 오류 문구 + 다시 시도 | 이벤트 |
| Server 4xx | 재시도 안 함(401 제외, §4) | `errorCode`별 문구(§1.2) | 이벤트 |
| Decoding | 안 함 | 일반 오류 문구 | 크래시 리포트(엔드포인트 포함) |
| Cancelled | 안 함 | 없음 | 없음 |

플랫폼별 함정 [공식·미열람]/[일반지식]

| 플랫폼 | HTTP 4xx/5xx가 예외인가 | 취소 판별 |
| --- | --- | --- |
| iOS `URLSession` | 아니다. `HTTPURLResponse.statusCode`를 직접 검사 | `URLError.cancelled`, `CancellationError` |
| Android OkHttp/Retrofit | `Response`면 아니다, `suspend fun`이 바디 타입을 반환하면 `HttpException` | `CancellationException`(반드시 재던짐) |
| Flutter Dio | 기본으로 던진다(`http` 패키지는 안 던짐) | `DioExceptionType.cancel` |
| RN·Web `fetch` | 아니다. `res.ok` 검사 | `AbortError` |
| axios | 던진다 | `axios.isCancel` |

### 1.2 찰캌 오류 응답과 `errorCode`

서버 오류 바디는 `{ "errorCode": string, "message": string }`이다. HTTP 상태는 예외 종류로 정해진다: NotFound→404, Unauthorized→401, Forbidden→403, Business→400, 그 밖→500. [찰캌규칙]

| errorCode | HTTP | 의미 | 클라이언트 동작 |
| --- | --- | --- | --- |
| `UNAUTHORIZED` | 401 | 액세스 토큰만 만료 | 토큰 갱신 후 원 요청 1회 재전송(§4) |
| `REAUTHENTICATION_REQUIRED` | 401 | 갱신까지 실패. 저장 토큰을 버려야 함 | 갱신하지 않고 세션 정리 → 로그인 화면 |
| `FORBIDDEN` | 403 | 권한 없음(정지 회원 제한 USER-002 포함 여부는 기능별 API 문서 확인) | 재시도 안 함. 서버 `message` 표시 |
| `SIGNATURE_REGISTRATION_REQUIRED` | 400 | 사인 재등록 필요 | 사인 등록 화면으로 유도 |
| `SIGNATURE_REUPLOAD_REQUIRED` | 400 | 사인 이미지 다시 올려야 함 | 사인 업로드부터 다시 |
| `SIGNATURE_PROCESSING_PENDING` | 400 | 서버가 사인 이미지를 처리 중 | 짧게 기다린 뒤 다시 시도 가능 상태로 표시 |
| `RESOURCE_STATE_CHANGED` | 400 | 그 사이 대상 상태가 바뀜 | 대상 다시 조회 후 화면 갱신 |
| `BUSINESS_ERROR` | 400/404 | 입력·정책 위반 일반 | 서버 `message` 표시, 재시도 안 함 |
| `INTERNAL_ERROR` | 500 | 서버 오류 | 일반 오류 문구, 멱등 요청만 재시도 |

규칙

- 분기는 `errorCode`로 한다. 서버 `message`는 문구가 이미 사용자용 한국어이므로 `BUSINESS_ERROR`·`FORBIDDEN`에서는 그대로 보여 줄 수 있다. 나머지 코드는 위 표의 클라이언트 문구 키를 쓴다. 문구 키 표는 다섯 클라이언트가 공유한다. [제안]
- 오류 바디를 해석하지 못하면(HTML 게이트웨이 오류 등) HTTP 상태만으로 분류한다.
- 서버에 새 `errorCode`가 추가되면 모르는 코드는 `BUSINESS_ERROR`처럼 처리해 앱이 깨지지 않게 한다.

### 1.3 자주 하는 실수

| 실수 | 대신 |
| --- | --- |
| `catch {}`로 삼키고 스피너만 멈춤 | 모든 catch는 상태 전이 또는 로그 중 하나를 한다 |
| 4xx/5xx를 "네트워크 오류"로 통일 | 계층별 문구 |
| 취소를 오류 토스트로 표시 | `Cancelled`는 UI에서 제외 |
| 상태 코드 검사 없이 디코딩 | 상태 검사 → 오류 바디 해석 → 성공 바디 디코딩 순서 |
| Kotlin `catch (e: Exception)`이 취소까지 삼킴 | `CancellationException`은 재던짐 |

## 2. 재시도

### 2.1 무엇을 재시도하나

HTTP 표준상 GET·PUT·DELETE는 멱등이고 POST·PATCH는 아니다. 연결 실패 시 자동 재시도는 멱등 요청에만 한다. [공식](https://www.rfc-editor.org/rfc/rfc9110.html)

| 찰캌 요청 | 메서드 | 자동 재시도 | 근거·주의 |
| --- | --- | --- | --- |
| 조회 전부(피드, 주제, 상세, 알림, 캘린더) | GET | O | |
| 좋아요 등록·취소 | PUT / DELETE | O | LIKE-003으로 멱등. [§3 낙관적 업데이트](offline-optimistic-polling.md#3-낙관적-업데이트) |
| 스토리지 이미지 PUT | PUT(presigned) | O | URL 5분 만료 시 업로드 발급부터 다시. [업로드](media-upload.md#32-업로드-흐름) |
| 게시물 제목 수정 | PUT | O | |
| 게시물 삭제 | DELETE | O | 재시도에서 404가 오면 삭제 완료로 볼지는 [open-questions](../open-questions.md#백엔드-확인-대상) |
| 업로드 발급 `POST /posts/uploads` | POST | O | 새 업로드 ID가 생길 뿐 부작용 없음 |
| 게시물 생성 `POST /posts` | POST | **자동 재시도 안 함** | 서버가 POST-003·POST-004로 중복을 막는다. 응답 유실 시 `GET /posts/today`로 확인(POST-011) |
| AI 리뷰 요청 | POST | 안 함 | 상태 조회로 확인 |
| 알림 읽음 | PATCH | O | 읽음 처리는 결과가 같다 [일반지식] |
| 토큰 갱신 | POST | **안 함** | rotation 때문에 두 번 보내면 세션 폐기(§4) |
| 피드백 제출 | POST | 안 함 | 사용자가 다시 누른다 |

Idempotency-Key 헤더(IETF 초안)는 서버가 지원하지 않으므로 지금은 쓰지 않는다. 필요하면 백엔드 확인 대상으로 올린다. [공식](https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/)

### 2.2 어떻게 재시도하나

- **지수 백오프 + full jitter**: `delay = random(0, min(cap, base × 2^attempt))`. jitter가 없으면 서버 복구 직후 모든 클라이언트가 동시에 몰린다. [공식](https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/)
- **재시도 대상 상태**: 연결 오류, 408, 429, 500, 502, 503, 504. 그 밖의 4xx는 재시도하지 않는다.
- **Retry-After**: 초 또는 HTTP 날짜. 있으면 계산한 백오프보다 우선한다. 서버가 보내는지는 미확인. [공식](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Retry-After)
- **재시도는 한 계층에서만**: OkHttp `retryOnConnectionFailure`(연결 실패만) + Repository 재시도 + WorkManager 재시도가 곱해지지 않게 한다. 찰캌 기준은 "Repository가 위 표대로 재시도, 백그라운드 작업은 OS 스케줄러에 맡김"이다. [제안]
- 화면을 떠나면 재시도도 멈춘다(취소 전파).

### 2.3 플랫폼별 도구

| 플랫폼 | 포그라운드 재시도 | 백그라운드 재시도 |
| --- | --- | --- |
| iOS | `Task.sleep` 루프. `waitsForConnectivity`로 연결 대기 | background `URLSession`이 자동 |
| Android | `delay` + 루프, Flow `retryWhen` | WorkManager `BackoffPolicy.EXPONENTIAL` [공식](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started) |
| Flutter | Dio 인터셉터(직접 구현) | `workmanager` 패키지 |
| RN | TanStack Query `retry`/`retryDelay` | 제한적. [react-native.md](../platforms/react-native.md) |
| Web | TanStack Query | Background Sync는 Chromium 한정. [web.md](../platforms/web.md) |

TanStack Query 기본값은 쿼리 3회 재시도, 1초부터 2배씩 최대 30초다. 찰캌 상수와 맞추려면 `retryDelay`에 jitter를 넣어 덮어쓴다. [공식](https://tanstack.com/query/latest/docs/framework/react/guides/query-retries)

## 3. 타임아웃, 취소, 로딩 표시

- 연결 타임아웃, 응답 대기(idle) 타임아웃, 전체 상한을 구분한다. 플랫폼 기본값이 다르므로(iOS `URLSession` 요청 idle 60초 등) 반드시 명시한다.
- 업로드처럼 큰 바디는 전체 시간 대신 "진행 없음(idle)" 기준으로 끊는다.
- 화면을 떠나면 요청을 취소한다. 수명 범위 대응은 [architecture-state.md §5](architecture-state.md#5-콜백과-비동기-패러다임).
- 취소된 요청의 결과가 새 화면 상태를 덮어쓰지 않게, 결과 반영 전에 요청 세대(generation)나 키를 비교한다.
- 버튼 연타는 in-flight 가드로 막는다. 같은 액션이 진행 중이면 두 번째는 무시한다.
- 로딩 표시: 짧은 요청에서 스피너가 깜빡이지 않도록 표시 지연과 최소 표시 시간을 둔다. 0.1초·1초·10초 응답 한계는 Nielsen 연구다. 구체 값은 업계 관례이지 표준이 아니다. [공식·미열람](https://www.nngroup.com/articles/response-times-3-important-limits/)
- 목록은 skeleton, 버튼 액션은 버튼 안 진행 표시를 쓴다. 상태 종류는 [ux-accessibility.md §2](ux-accessibility.md#2-화면-상태).

| 플랫폼 | 타임아웃 설정 | 느린 망 테스트 |
| --- | --- | --- |
| iOS | `timeoutIntervalForRequest`(idle), `timeoutIntervalForResource`(전체) [공식](https://developer.apple.com/documentation/foundation/urlsessionconfiguration/timeoutintervalforrequest) | Network Link Conditioner |
| Android | OkHttp `connectTimeout`, `readTimeout`, `writeTimeout`, `callTimeout` | 에뮬레이터 `-netdelay`, `-netspeed` [공식](https://developer.android.com/studio/run/emulator-networking) |
| Flutter | Dio `connectTimeout`, `receiveTimeout`, `sendTimeout` | 기기·에뮬레이터 설정 |
| RN·Web | `fetch`에는 없음 → `AbortSignal.timeout(ms)` | Chrome DevTools throttling [공식](https://developer.chrome.com/docs/devtools/settings/throttling) |

## 4. 토큰 갱신

### 4.1 찰캌 서버 동작 [찰캌규칙]

- `POST /auth/refresh`는 **refresh token을 회전(rotate)한다.** 이미 회전된 토큰을 다시 내면 재사용으로 판단해 **그 세션 전체를 폐기**하고 `REAUTHENTICATION_REQUIRED`(401)를 돌려준다. 만료·폐기된 토큰도 같은 코드다.
- 세션이 폐기되면 그 세션의 푸시 기기 등록도 서버에서 지워진다.
- 로그아웃은 모르는 토큰이나 이미 폐기된 토큰에도 성공한다. 재시도해도 안전하다.

즉 **갱신 요청을 두 번 보내면 로그아웃된다.** 아래 single-flight는 선택이 아니라 필수다.

### 4.2 single-flight 갱신

```text
send(req):
  token = tokenStore.access
  res = http(req + token)
  if res.status != 401 or res.errorCode == REAUTHENTICATION_REQUIRED:
      if REAUTHENTICATION_REQUIRED: sessionExpired(); 
      return res
  newToken = refreshOnce(failedWith = token)     // 진행 중이면 그 결과를 기다림
  return http(req + newToken)                     // 원 요청은 1회만 재전송

refreshOnce(failedWith):
  if tokenStore.access != failedWith: return tokenStore.access   // 다른 요청이 이미 갱신
  inFlight ?= http(POST /auth/refresh, 인터셉터 우회)
  try:   pair = await inFlight; tokenStore.save(pair) (access와 refresh를 원자적으로); return pair.access
  catch REAUTHENTICATION_REQUIRED: sessionExpired(); throw
  catch Network: throw            // 로그아웃하지 않는다
  finally: inFlight = nil
```

| 플랫폼 | 훅 | 단일화 수단 |
| --- | --- | --- |
| iOS | `URLSession` 래퍼 | `actor` 안에서 진행 중 `Task` 공유 |
| Android | OkHttp `Authenticator` | `Mutex` + 토큰 비교(double-check). 기존 앱 구현 참고 |
| Flutter | Dio `QueuedInterceptor` | 큐만으로는 "한 번"이 보장되지 않는다. 토큰 비교 가드 필수 [공식](https://pub.dev/documentation/dio/latest/dio/QueuedInterceptor-class.html) |
| RN·Web | fetch 래퍼 / axios 인터셉터 | 모듈 전역 `refreshPromise` 공유 |

### 4.3 자주 하는 실수

| 실수 | 결과 |
| --- | --- |
| 동시 401마다 갱신 호출 | 재사용 감지로 세션 폐기 → 강제 로그아웃 |
| 갱신 요청에도 인터셉터 적용 | 무한 재귀 |
| 갱신 응답을 받고 저장 전에 앱 종료 | 다음 실행 때 옛 refresh 토큰 재사용 → 로그아웃. 저장을 응답 직후 원자적으로 |
| 오프라인으로 갱신 실패했는데 로그아웃 | 네트워크 오류는 오류로 전파만 |
| 여러 프로세스·확장(iOS 공유 확장, Android 다중 프로세스, 웹 여러 탭)이 각자 갱신 | 같은 세션을 두 번 회전. 갱신은 한 곳에서만. 웹은 탭 간 잠금(Web Locks API 등) [일반지식] |
| 로그아웃 때 정리 누락 | 토큰, 사용자 캐시, 로컬 DB, outbox, 진행 중 업로드, 푸시 토큰을 함께 정리 |

## 찰캌 공통 상수

다섯 클라이언트가 같은 값을 쓴다. 근거가 [제안]인 값은 첫 구현 전에 팀에서 확정하고 이 표를 고친다.

| 이름 | 값 | 근거 |
| --- | --- | --- |
| `CONNECT_TIMEOUT` | 10초 | [제안] |
| `READ_TIMEOUT`(idle) | 20초 | [제안] |
| `UPLOAD_IDLE_TIMEOUT` | 60초 | [제안] |
| `RETRY_MAX_ATTEMPTS` | 3(최초 포함 총 3회) | [제안], TanStack 기본과 근사 |
| `RETRY_BASE_DELAY` | 1초 | [제안] |
| `RETRY_MAX_DELAY` | 30초 | [제안], TanStack 기본 상한 |
| `RETRY_JITTER` | full jitter | [공식] AWS |
| `LOADING_SHOW_DELAY` | 300ms | [제안] 업계 관례 |
| `LOADING_MIN_VISIBLE` | 500ms | [제안] 업계 관례 |
| `TOKEN_REFRESH_RETRY` | 0회 | [찰캌규칙] rotation |
| `AI_REVIEW_POLL_INTERVAL` / `WINDOW` | 3초 / 약 5분 | [찰캌규칙] [폴링](offline-optimistic-polling.md#4-ai-리뷰-폴링) |

## 체크리스트

- [ ] 모든 HTTP 응답이 `AppError` 또는 성공 값으로 변환된다
- [ ] `errorCode` 표의 모든 코드에 대한 분기와 단위 테스트가 있다
- [ ] 재시도 대상 표와 실제 코드가 일치하고 재시도 계층이 하나다
- [ ] "동시 401 다섯 개 → 갱신 1회" 테스트가 있다
- [ ] `REAUTHENTICATION_REQUIRED` → 세션 정리 → 로그인 화면 테스트가 있다
- [ ] 갱신 네트워크 오류에서는 로그아웃하지 않는다
- [ ] 화면 이탈 시 요청·재시도가 취소된다
- [ ] 상수는 이 표의 값과 같다
