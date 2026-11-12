# chalkak-filter-worker

작가가 Lightroom 프리셋을 적용해 내보낸 견본 JPG(3072x4096)를 필터 레시피로 바꾸는 Node 워커입니다.

입력 단계(바이트 검사, 디코딩, XMP 읽기), M1 레시피 추출, 격자 보정(A2·G1), 분기 선택, 레시피 JSON이 있습니다. 추출 결과를 저장하고 앱에 내려주는 단계는 이어지는 작업에서 추가합니다.

입력 검사 규칙:

- 디코드 전에 `metadata()`로 방향(EXIF 5~8은 가로·세로 교환)을 반영한 크기를 확인하고, 3072x4096이 아니면 픽셀을 읽지 않고 `INVALID_PROBE`로 끝냅니다. 디코드는 `limitInputPixels`를 3072x4096 픽셀로 걸어 큰 사진이 메모리를 채우지 못하게 합니다.
- XMP에 마스크·부분 보정이 있으면 디코드 전에 `INVALID_PROBE`('마스크와 부분 보정은 필터로 옮길 수 없어요…')로 거절합니다. 마스크 보정이 전역 LUT에 섞여 들어가기 때문입니다. 디헤이즈·명료도만 경고로 남깁니다.
- 마커 정합에서 작은 어긋남(허용 범위 안)이나 낮은 상관이 있으면 `meta.warnings`에 '견본 위치가 조금 어긋나 있어요…' 한 문장을 넣습니다(읽은 값은 바뀌지 않습니다).

## M1 레시피 추출

`src/m1/extract.js`의 `extractM1({ rgba, width, height }, plan)`이 디코드한 견본과 `planFromXmp`의 계획으로 `{ lut, V, C, K, grain, warnings }`를 읽습니다. 꺼진 연산자는 `null`이고 `toRecipeJson`이 JSON으로 쓸 수 있는 객체로 바꿉니다.

| 모듈 | 하는 일 |
| --- | --- |
| `layout.js` | 견본 배치, 합성 입력 견본(`renderProbe`), 마커 정합(어긋나면 `INVALID_PROBE`) |
| `core-lut.js` | 색 코어에서 33³ LUT 읽기 |
| `probe-vignette.js` | 첫 LUT 추정을 위한 바탕·회색칸 비네팅 표 |
| `vignette.js` | 비네팅 V(형태·반경 곡선·밝기 응답) 읽기와 되돌리기, 연산자 식 |
| `local-tone.js` | 지역 톤 C 표 맞춤 |
| `detail.js` | 디테일 대역 이득 k_b 표 맞춤 |
| `grain.js` | 그레인 파라미터 읽기와 그레인 연산자 |
| `extract.js` | 순서와 켜기 정리(V ↔ LUT 번갈아 읽기, C·k_b 번갈아 맞춤) |
| `color.js`, `numeric.js` | 색 변환, 흐림, 33³ 조회, 선형 풀이 |

12.6M px 견본은 RGBA 한 장이 50MB라서, 큰 중간 평면은 한 번에 몇 장만 두도록 흐림을 제자리로 계산하고 단계가 끝나는 대로 놓습니다.

## M1 적용과 격자 분기

`src/m1/apply.js`의 `applyM1(rgba, width, height, m1, { scaleK, grain })`이 앱이 맞춰야 하는 기준 렌더러입니다(LUT → C → k_b → V → 그레인). `scaleK`는 프레임 긴 변 / 원본 긴 변입니다.

`src/grid/`는 M1이 못 읽는 나머지를 견본 쌍(무편집 견본 `assets/identity-probe-v2.jpg` ↔ 작가 견본)에서 맞추는 격자 보정 두 가지입니다. 어느 쪽을 쓸지는 `select.js`가 고릅니다(아래 분기 선택).

| 분기 | 격자 입력 | 꼴 `dims` | 맞춤 / 적용 |
| --- | --- | --- | --- |
| A2 | M1 LUT 출력 | [5, 9, 7, 4, 3] | `fitA2` / `applyA2` |
| G1 | M1 전체 스택 출력(잔차 격자) | [3, 5, 5, 4, 3] | `fitG1` / `applyG1` |

축은 반경, 감마 휘도, 무편집 입력의 흐린 log 휘도(1%)입니다. 흐림 축은 항상 지금 렌더하는 사진의 무편집 입력에서 만듭니다. 무편집 견본은 `loadIdentityProbe()`가 한 번만 디코드합니다.

연구 코드와의 일치: 같은 디코드 픽셀을 주면 수식은 정확히 같습니다(θ는 `toPrecision(8)` 기준 전부 일치, 적용 결과는 픽셀 단위로 동일). 다만 sharp와 macOS sips의 JPEG 디코드가 조금 달라서 실제 입력에서는 θ가 수 e-3(최대 약 8e-3) 차이 나고, 견본 표본 평균 ΔE는 −0.02~+0.06 변합니다.

## 분기 선택

`src/grid/select.js`가 M1 · A2 · G1 가운데 하나를 고릅니다(연구 e15 G3와 같은 규칙).

- 점수(낮을수록 좋음): A2와 G1은 견본을 1024px 블록 체커 두 무리((⌊x/1024⌋ + ⌊y/1024⌋) mod 2)로 가르고, 한 무리로 맞춰 다른 무리를 예측하는 것을 두 방향으로 한 뒤 전 표본의 평균 ΔE2000입니다. M1은 견본 전체에서 읽은 값이라 홀드아웃이 아니고, 무편집 견본에 M1 전체(`scaleK` 1, 그레인 없음)를 건 출력과 작가 견본의 같은 위치 평균 ΔE입니다.
- 선택: 최저값과 0.05 이내면 M1, 아니면 A2, G1 순으로 최저값과 0.05 이내인 첫 분기입니다.
- 최종 격자 θ는 고른 분기만 맞춥니다. A2는 연구 `grids.json`과 비트가 같도록 256px 체커 무리로, G1은 점수 때 쌓은 1024px 무리 그대로 풉니다. 전체 해상도 `applyM1`은 추출 한 번에 한 번만 돕니다(G1 표본이 그 출력이고 M1 점수도 같은 표본입니다).

## 레시피와 추출

`extractRecipe(buffer)`(`src/extract-recipe.js`)는 바이트 검사 → XMP 계획(디코드 전에 실패) → 디코드 → M1 읽기 → 분기 선택 → 레시피 순으로 돌고 `{ recipe, meta }`를 돌려줍니다. `meta`는 `branch`, `holdout: { M1, A2, G1 }`, `warnings`(XMP와 마커 경고, 중복 제거), `xmpSummary`, `extractorVersion`, `probeVersion`입니다. `ExtractionFailure`가 아닌 오류는 원인을 `cause`에 둔 `PROCESSING_FAILED`(고정 안내 문구)로 바꾸고, CLI도 같은 문구를 쓰며 원인은 stderr 둘째 줄 `cause:`에만 나옵니다.

```
node src/cli.js <probe.jpg> [--out recipe.json] [--meta meta.json]   # npm run extract -- <probe.jpg>
```

실패하면 stderr에 `{"type","message"}` JSON을 쓰고 `INVALID_PROBE`는 종료 코드 2, `PROCESSING_FAILED`는 1입니다.

레시피(`schemaVersion` 1, `src/recipe.js`):

```
{ schemaVersion: 1, extractorVersion, probeVersion: 2, branch: 'M1'|'A2'|'G1', colorSpace: 'sRGB',
  ops: { lut: { size: 33, encoding: 'rgb8', data },            // base64, 33³·3바이트, r이 가장 빨리 변함
         vignette, localTone, detail,                          // 켜졌고 분기가 쓸 때만. 숫자는 JSON 배열(float64가 그대로 왕복)
         grid: { kind: 'A2'|'G1', dims, encoding: 'f32le', data },   // A2·G1만. θ를 float32 little-endian으로
         grain: { amp, size, edge, tilt, seed } } }            // 꺼졌으면 없음
```

**앱이 따라야 하는 적용 순서** (그레인은 분기와 무관하게 항상 마지막):

| 분기 | 순서 |
| --- | --- |
| M1 | LUT → 지역 톤 → 디테일 → 비네팅 → 그레인 |
| A2 | LUT → A2 격자(흐림 평면은 원본 입력에서) → 그레인 |
| G1 | M1 전체(그레인 없이) → G1 격자(흐림 평면은 원본 입력에서) → 그레인 |

A2 레시피에는 비네팅·지역 톤·디테일이 없고(격자가 흡수), G1 레시피에는 M1 연산자가 모두 들어갑니다. `decodeRecipe(json)`이 `applyM1`·`applyA2`·`applyG1`이 받는 구조로 되돌리고, `applyRecipe(rgba, width, height, recipe, { scaleK })`가 서버 기준 렌더러입니다(앱 골든 시험용). 격자 θ가 float32를 거치므로 메모리 안 θ로 적용한 결과와 최대 1단계(8비트) 차이까지 날 수 있고, M1 분기는 픽셀까지 같습니다.

## 콜백 계약

워커가 백엔드(#1010)에 보내는 내부 콜백입니다. 이 절이 #1010이 구현할 계약이고, 클라이언트는 `src/worker/callback-client.js`, 모의 서버는 `test/support/mock-backend.js`입니다. 워커 프로세스 하나가 공용 큐를 한 건씩 처리하고, S3 키의 `dev`/`prod`로 콜백할 백엔드를 고릅니다.

### 공통

| 항목 | 값 |
| --- | --- |
| 기준 URL | 환경별 `DEV_BACKEND_INTERNAL_API_BASE_URL`, `PROD_BACKEND_INTERNAL_API_BASE_URL`. 값은 `/internal/v1`까지 포함(`https://<dev API 도메인>/internal/v1`) |
| 경로 | `{base}/filter-probes/{probeId}/{start\|complete\|failed}`. `probeId`는 소문자 정규 UUID(백엔드 `CanonicalUuidParser`와 같은 모양). 백엔드는 파싱한 UUID의 소문자 `toString()`으로 서명 경로를 다시 만들므로 워커는 소문자만 보내야 한다(대문자는 서명이 맞지 않는다) |
| 메서드 | 모두 `POST` |
| 인증 헤더 | `X-Chalkak-Callback-Timestamp: <유닉스 초>`, `X-Chalkak-Callback-Signature: v1=<HMAC-SHA256 hex 소문자>` |
| 서명 대상 | `"{ts}\nPOST\n{path}\n{sha256hex(본문 바이트)}\n{env}"`. `path`는 `/internal/v1/filter-probes/{probeId}/{action}`(쿼리 없음), `env`는 `dev`/`prod`(백엔드 `chalkak.image.environment`). 본문이 없으면 빈 바이트의 해시(`e3b0c442…b855`). 검증은 `ProcessingCallbackAuthenticator`를 그대로 씁니다 |
| 시크릿 | 백엔드 `IMAGE_PROCESSOR_CALLBACK_SECRET`과 같은 값. 워커 쪽 이름은 Lambda와 같은 `IMAGE_PROCESSING_API_SECRET`(32자 이상) |
| 시각 | 백엔드는 과거 300초, 미래 30초까지 받습니다. 워커는 **시도마다 새로 서명**합니다(재시도 대기 중 시각이 밀려도 401이 나지 않게) |
| 본문 인코딩 | `Content-Type: application/json; charset=utf-8`, **ASCII만**(비ASCII는 `\uXXXX` 소문자 16진수). 문자열만 있는 본문(failed)은 Lambda의 `json.dumps(..., separators=(",", ":"))`와 바이트가 같습니다. 숫자 표기(`1.0`과 `1`)는 다를 수 있으므로 complete는 파이썬과 바이트 비교를 하지 않습니다. 실패·경고 문구가 한국어라서, 백엔드가 문자열을 다시 인코딩해 해시하는 경로가 생겨도 서명이 어긋나지 않게 합니다 |
| 본문 크기 | complete 약 1KB, failed 300B 이하. `CallbackBodySizeLimitFilter`(`/internal/v1/*`) 상한 안 |
| 요청 제한 시간 | 워커 HTTP 요청 하나당 5초(`CALLBACK_TIMEOUT_SECONDS`). 리다이렉트는 따라가지 않습니다 |
| 백엔드 인증 실패 | 401(기존 `UnauthorizedException`). 헤더 누락도 401 |

기준 URL은 워커가 기동할 때 검증합니다. 끝의 `/`를 지운 뒤 경로가 정확히 `/internal/v1`이어야 하고, 사용자 정보·쿼리·조각이 없어야 하며, `https`만 받습니다(호스트가 `127.0.0.1`·`localhost`·`[::1]`이면 `http` 허용). 경로가 틀리면 모든 콜백이 401이 되고 401은 재시도 대상이라 오타 하나가 끝없는 재시도처럼 보이기 때문입니다.

고정 서명 벡터(파이썬 `hmac`으로 만든 값, `test/worker-callback-client.test.js`): 시크릿 `chalkak-filter-worker-test-secret-0123456789`, 시각 `1762905600`.

| 경우 | 경로 / 본문 | 서명 |
| --- | --- | --- |
| start, `dev` | `/internal/v1/filter-probes/0190f5c2-7b4a-7c3e-9d2f-1a2b3c4d5e6f/start`, 빈 본문, `Content-Type` 없음 | `v1=330e7bf515c005d8ff18a3db601b0341a055edae06422233dbc3c96ed877fdba` |
| failed, `prod` | 같은 `probeId`의 `/failed`, 본문 `{"type":"INVALID_PROBE","message":"JPG 파일이 아니에요. Lightroom에서 JPG로 다시 내보내 주세요."}`를 ASCII로 이스케이프한 바이트(sha256 `f14d968eda6b68efbe2d41a96441a7c6540f712b3b4e9fb76cdd3fecbfa8a77f`) | `v1=b668b759b37325a902b123a0cc970ec11d151076937e7a2c3f0dc25a9ae57ce3` |

### start

```
POST /internal/v1/filter-probes/{probeId}/start
본문 없음 (Content-Length: 0, Content-Type 없음)

200 {"proceed": true,  "recipeId": "0190f5c2-…"}   // ISSUED → PROCESSING, 또는 이미 PROCESSING(같은 recipeId)
200 {"proceed": false, "recipeId": null}            // 현재 견본 아님, 없는 probeId, READY/FAILED 확정, 제한 시간 지남
```

- 인증을 통과하면 **항상 200**입니다. 없는 `probeId`(삭제된 초안 등)도 404가 아니라 `proceed: false`입니다.
- 이미 PROCESSING이면 같은 `recipeId`로 `proceed: true`를 돌려줍니다(재전달·재시도 대비, 멱등).

### complete

```
POST /internal/v1/filter-probes/{probeId}/complete
{"extractorVersion":"1.0.0","probeVersion":2,"branch":"A2","holdoutDeltaE":1.234,
 "warningMessages":["디헤이즈·명료도는 사진에 따라 다르게 보일 수 있어요"],
 "xmpSummary":{"PostCropVignetteAmount":-15,"Highlights2012":0,"Clarity2012":10,"Texture":0,"Sharpness":40,
               "LuminanceSmoothing":0,"GrainAmount":0,"Dehaze":0,"masks":false}}

204
```

| 필드 | 형식 | 출처 | 백엔드 저장 |
| --- | --- | --- | --- |
| `extractorVersion` | 문자열, 1~30자 | `meta.extractorVersion`(package.json 버전) | `extractor_version` |
| `probeVersion` | 정수 ≥ 1 | `meta.probeVersion`(지금 2) | 저장하지 않습니다. 발급 때 저장한 `probe_version`과 다르면 경고 로그만 |
| `branch` | `M1`·`A2`·`G1` | `meta.branch` | `branch` |
| `holdoutDeltaE` | 숫자 ≥ 0 또는 `null` | `meta.holdout[branch]`. 유한수가 아니면 `null` | `holdout_delta_e`(NUMERIC(6,3), 반올림) |
| `warningMessages` | 문자열 배열(빈 배열 가능) | `meta.warnings` | `warning_messages` |
| `xmpSummary` | 객체 | `meta.xmpSummary` | `xmp_summary` |

- **레시피 위치는 보내지 않습니다.** 워커는 start가 돌려준 `recipeId`로만 키 `{S3_PREFIX}/filters/{env}/recipes/{recipeId}.json`을 만듭니다. 백엔드는 자기가 발급한 `recipeId`로 같은 키·CDN URL을 계산합니다(키는 DB에 저장하지 않고 ID로 계산). 키를 본문에 실으면 같은 사실의 출처가 둘이 되고 백엔드가 받은 키를 검증해야 합니다.
- **홀드아웃은 고른 분기 값 하나만** 보냅니다. DB 컬럼이 하나이고 백엔드가 분기별 값을 고를 이유가 없습니다. 세 값은 워커 로그(`extraction_finished`)에 남습니다.
- 모르는 필드는 무시합니다(나중에 필드를 더해도 깨지지 않게).
- 현재 견본이고 PROCESSING이며 제한 시간 안일 때만 READY. 아니면 **무시하고 204**(늦은 완료, 이미 READY인 재전송 포함).
- 본문 검증 실패는 400(`BUSINESS_ERROR`).

### failed

```
POST /internal/v1/filter-probes/{probeId}/failed
{"type":"INVALID_PROBE","message":"JPG 파일이 아니에요. Lightroom에서 JPG로 다시 내보내 주세요."}

204
```

- `type`: `INVALID_PROBE`(작가 파일 문제) 또는 `PROCESSING_FAILED`(내부 오류, 재시도 소진, 추출 시간 초과).
- `message`: 작가에게 보일 문구, 1~200자(`failure_message` VARCHAR(200)). 워커는 고정 문구만 보냅니다.
- 현재 견본이고 **미확정(ISSUED 또는 PROCESSING)**일 때 FAILED. ISSUED에서도 받아야 합니다(수신 횟수 초과로 start 없이 포기하는 경로). 확정됐거나 현재 견본이 아니면 무시하고 204.

### 응답별 워커 동작

"남김"은 메시지를 지우지 않아 숨김 시간이 끝난 뒤 SQS가 다시 주는 것입니다. 모든 "남김"은 수신 횟수 상한(5)으로 끝이 있습니다. 구현은 `callback-client.js`의 `outcomeForCallbackError`입니다.

| 결과 | start | complete | failed |
| --- | --- | --- | --- |
| 2xx | 본문 검사: `proceed`가 불리언, `true`면 `recipeId`가 소문자 UUID. `false`면 **삭제**. 본문이 잘못됐으면 **남김** | **삭제** | **삭제** |
| 400·404·405·413·414·415 | **남김**(start는 영구 거부로 보지 않습니다. 배포 전 404, 일시 오류를 같은 메시지로 다시 해 봅니다) | `failed(PROCESSING_FAILED)`를 보내 상태를 닫고 **삭제**(`callback_permanently_refused` 오류 로그) | 오류 로그 후 **삭제** |
| 401·403 | **남김**(시크릿 교체 중일 수 있음). 오류 로그 | 프로세스 안 재시도 후 **남김** | 프로세스 안 재시도 후 **남김** |
| 408·429·5xx·3xx·그 밖 | **남김** | 프로세스 안 재시도 후 **남김** | 프로세스 안 재시도 후 **남김** |
| 네트워크 오류·5초 초과 | **남김** | 프로세스 안 재시도 후 **남김** | 프로세스 안 재시도 후 **남김** |

- **프로세스 안 재시도**: complete·failed는 최대 3번(대기 5초, 15초, 매번 새 서명). complete를 남기면 1~3.5분짜리 추출을 처음부터 다시 하고, 그동안 dev·prod 모두 막히기 때문입니다. start는 싸므로 바로 남깁니다.
- 수신 횟수 상한에 걸린 포기 경로의 failed는 결과와 상관없이 **삭제**합니다(Lambda `_abandon`과 같음).
- 종료 신호를 받으면 재시도 대기를 끊고 메시지를 숨김 0초로 반납합니다.

### 시간 제한과 #1010 구현 확인 목록

- 워커는 추출에 최대 480초를 쓰고(초과하면 재시도 없이 `failed(PROCESSING_FAILED)`), 견본이 몰리면 한 건씩 처리하므로 대기도 생깁니다. 그래서 백엔드 `FILTER_EXTRACTION_TIMEOUT`(`probe_issued_at` 기준)은 기존 15분 제안에서 **30분**으로 정했습니다. 15분이면 견본이 4~5개 몰릴 때 대기만으로 넘겨 늦은 것이 실패로 보입니다.
- 컨트롤러 `CALLBACK_PATH = "/internal/v1/filter-probes"`, 본문은 `byte[] rawBody`로 받아 `ProcessingCallbackAuthenticator.authenticate(path, rawBody, ts, sig)` 뒤에 파싱합니다(`PostImageProcessingCallbackController`와 같은 방식). start는 `rawBody`가 `null`입니다.
- start는 인증 뒤 항상 200. complete·failed는 무시해도 204.
- 위 고정 서명 벡터 두 개를 백엔드 시험에도 넣으면 양쪽이 같은 형식인지 확인됩니다.
- 모의 백엔드로 워커를 먼저 확인할 수 있습니다: `IMAGE_PROCESSING_API_SECRET=<시험용 시크릿> node test/support/mock-backend.js --environment dev --port 8787 [--start 503|skip] [--complete 400] [--failed 500]`. 서명·시각을 백엔드와 같은 규칙으로 검증하고, 요청마다 `{ action, probeId, signatureValid, body }` JSON 한 줄을 표준 출력에 씁니다.

## 테스트

Node 22 이상이 필요합니다.

```
npm ci && npm test
```

`test/m1-*.test.js`는 알려진 연산을 합성 견본에 걸고 읽은 값이 복원되는지 보는 시험입니다. 견본 한 장에 RGBA·평면 수백 MB를 쓰므로 메모리가 일정하도록 파일을 하나씩 순서대로 돌립니다(`--test-concurrency=1`, 전체 약 125초). 적용 쪽 연산(`test/support/`)은 시험용입니다.
