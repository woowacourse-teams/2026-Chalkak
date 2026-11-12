# chalkak-filter-worker

작가가 Lightroom 프리셋을 적용해 내보낸 견본 JPG(3072x4096)를 필터 레시피로 바꾸는 Node 워커입니다.

현재는 입력 단계(바이트 검사, 디코딩, XMP 읽기)와 M1 레시피 추출이 있습니다. 추출 결과를 저장하고 앱에 내려주는 단계는 이어지는 작업에서 추가합니다.

## M1 레시피 추출

`src/m1/extract.js`의 `extractM1({ rgba, width, height }, plan)`이 디코드한 견본과 `planFromXmp`의 계획으로 `{ lut, V, C, K, grain }`을 읽습니다. 꺼진 연산자는 `null`이고 `toRecipeJson`이 JSON으로 쓸 수 있는 객체로 바꿉니다.

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

`src/grid/`는 M1이 못 읽는 나머지를 견본 쌍(무편집 견본 `assets/identity-probe-v2.jpg` ↔ 작가 견본)에서 맞추는 격자 보정 두 가지입니다. 어느 쪽을 쓸지 고르는 일은 이 폴더에 없습니다.

| 분기 | 격자 입력 | 꼴 `dims` | 맞춤 / 적용 |
| --- | --- | --- | --- |
| A2 | M1 LUT 출력 | [5, 9, 7, 4, 3] | `fitA2` / `applyA2` |
| G1 | M1 전체 스택 출력(잔차 격자) | [3, 5, 5, 4, 3] | `fitG1` / `applyG1` |

축은 반경, 감마 휘도, 무편집 입력의 흐린 log 휘도(1%)입니다. 흐림 축은 항상 지금 렌더하는 사진의 무편집 입력에서 만듭니다. 무편집 견본은 `loadIdentityProbe()`가 한 번만 디코드합니다.

연구 코드와의 일치: 같은 디코드 픽셀을 주면 수식은 정확히 같습니다(θ는 `toPrecision(8)` 기준 전부 일치, 적용 결과는 픽셀 단위로 동일). 다만 sharp와 macOS sips의 JPEG 디코드가 조금 달라서 실제 입력에서는 θ가 수 e-3(최대 약 8e-3) 차이 나고, 견본 표본 평균 ΔE는 −0.02~+0.06 변합니다.

## 테스트

Node 22 이상이 필요합니다.

```
npm ci && npm test
```

`test/m1-*.test.js`는 알려진 연산을 합성 견본에 걸고 읽은 값이 복원되는지 보는 시험입니다. 견본 한 장에 RGBA·평면 수백 MB를 쓰므로 메모리가 일정하도록 파일을 하나씩 순서대로 돌립니다(`--test-concurrency=1`, 전체 약 125초). 적용 쪽 연산(`test/support/`)은 시험용입니다.
