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

## 테스트

Node 22 이상이 필요합니다.

```
npm ci && npm test
```

`test/m1-*.test.js`는 알려진 연산을 합성 견본에 걸고 읽은 값이 복원되는지 보는 시험입니다. 견본 한 장에 RGBA·평면 수백 MB를 쓰므로 메모리가 일정하도록 파일을 하나씩 순서대로 돌립니다(`--test-concurrency=1`, 전체 약 125초). 적용 쪽 연산(`test/support/`)은 시험용입니다.
