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

## 테스트

Node 22 이상이 필요합니다.

```
npm ci && npm test
```

`test/m1-*.test.js`는 알려진 연산을 합성 견본에 걸고 읽은 값이 복원되는지 보는 시험입니다. 견본 한 장에 RGBA·평면 수백 MB를 쓰므로 메모리가 일정하도록 파일을 하나씩 순서대로 돌립니다(`--test-concurrency=1`, 전체 약 125초). 적용 쪽 연산(`test/support/`)은 시험용입니다.
