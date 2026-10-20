# 사진 편집 구조

사진 편집 코드(`feature/photoedit`, `domain/model/photoedit`)를 처음 읽는 사람을 위한 문서다.
업로드 파일의 용량·품질 정책은 [게시물 이미지 인코딩 정책](post-image-encoding.md)을 본다.

## 1. 한 장의 사진이 지나가는 길

편집 화면은 **이미지를 만들지 않고 값(`PhotoEdit`)만 만든다.** 실제 픽셀은 업로드할 때 원본에서 다시 만든다.
그래야 업로드 URL이 만료돼 다시 인코딩할 때도, 미리보기와 업로드가 서로 다른 해상도를 쓸 때도 같은 결과가 나온다.

```text
사진 선택
  ↓
PhotoEditScreen ── PhotoEditViewModel ──(완료)──→ PhotoEdit
  │                   │                              ↓
  │                   │                     PhotoUploadViewModel → PostCreationRepository
  │                   │                              ↓
  │                   │                     AndroidPostImageEncoder: 원본을 PhotoEdit대로 다시 그려 WEBP로 저장
  │                   │
  │                   └─ 자동 보정 분석: PhotoEditImageRepository(픽셀 읽기) → PhotoPixels.sampled(자른 영역만)
  │                                      → PhotoImageStatistics(통계) → PhotoAutoAdjuster(보정값)
  │
  └─ 미리보기: CropCanvas·EditedPhotoImage → rememberAdjustedImage → PhotoColorTransform
```

같은 계산을 두 곳 이상에서 쓰는 부분은 클래스 하나로 모아 두었다. 고칠 때 한 곳만 고치면 된다.

| 계산 | 클래스 | 같이 쓰는 곳 |
|---|---|---|
| 회전·자르기 좌표 변환 | `PhotoEditTransform` | 업로드 인코더, 자동 보정 분석 |
| 색 조정 적용 | `PhotoColorTransform` | 업로드 인코더, 미리보기 (필터는 아래 3절 참고) |
| 조정 값 → 실제 변화량 | `PhotoAdjustmentScale` | 색 조정, 자동 보정 |
| 사진 읽기 + EXIF 회전 | `OrientedBitmapDecoder` | 업로드 인코더, 자동 보정 분석 |

이 계산들이 `domain`에 있는 이유는 비즈니스 규칙이어서가 아니다. 미리보기(feature)와 인코더(data)가 함께 쓰려면
둘 다 의존할 수 있는 층이 domain뿐이기 때문이다. 그래서 Android API 없이 순수 Kotlin으로 작성한다.

## 2. 좌표계

사진 편집 코드에는 좌표계가 여러 개 있다. 코드를 읽다가 막히면 대부분 "지금 어느 좌표계인가"의 문제다.
아래 예시 하나로 모든 좌표계의 값을 보여 준다.

> **예시**: 4000×3000 사진을 시계 방향으로 한 번 돌리고(`quarterTurns = 1`), 돌린 사진의 **왼쪽 절반**만 남긴다.
> 돌린 사진은 3000×4000이고, 남길 영역은 돌린 사진에서 x 0..1500, 중심 (750, 2000)이다.

| 이름 | 쓰는 곳 | (0, 0) 위치 | 단위 | 예시 값 |
|---|---|---|---|---|
| 원본(source) | `PhotoPixels`, `PhotoEditTransform.sourceX/Y` | 원본 왼쪽 위 | 픽셀 | 4000×3000, 남길 영역의 중심 (2000, 2250) |
| 비율 좌표 | `CropWindow` | 원본 왼쪽 위 | 0..1 | `CropWindow(0.5, 0.75, 0.5, 1)` |
| 결과(output) | `PhotoEditTransform.outputWidth/Height` | 결과 왼쪽 위 | 픽셀 | 1500×4000 |
| 중심 단위 | `CropGeometry`, `CenteredCropRect` | 원본 가운데 | 원본 긴 변 = 1 | `CenteredCropRect(-0.1875, 0, 0.375, 1)` |

**돌린 원본(turned)** 은 좌표계가 아니라 크기를 가리키는 말이다. 90°씩 돌린 원본의 가로·세로로, 예시에서는 3000×4000이다.
코드에서는 `turnedWidth`·`turnedHeight`로 나오고 `CropWindow`의 크기 비율이 이 값을 기준으로 한다.

**돌린 사진의 점 (xr, yr) → 원본의 점 (x, y)** (원본 W×H, 기울기 없음):

| quarterTurns | x | y |
|---|---|---|
| 0 | xr | yr |
| 1 | yr | H − xr |
| 2 | W − xr | H − yr |
| 3 | W − yr | xr |

예시는 quarterTurns = 1이므로 (750, 2000) → (2000, 3000 − 750) = (2000, 2250), 비율로 (0.5, 0.75)다.
기울기까지 있는 일반적인 경우는 `PhotoEditTransform.sourceX/Y`가 계산한다.

주의할 점:

- **원본은 EXIF 회전을 반영한 뒤의 사진이다.** 휴대폰을 세워 찍은 사진은 파일에 가로로 저장되고 "90° 돌려 보라"는
  EXIF 정보가 붙는다. 코드의 원본은 이 정보대로 이미 돌려서 화면에 보이는 방향이다(`OrientedBitmapDecoder`).
- **`CropWindow`는 중심과 크기의 기준이 다르다.** 중심은 돌리기 전 원본 기준, 크기는 돌린 원본 기준 비율이다.
  가운데(0.5, 0.5)를 자르면 회전해도 중심 값이 같아서 이 차이가 안 보이므로, 가운데가 아닌 영역에서 특히 주의한다.
- **미리보기와 업로드는 원본 방향을 서로 다른 곳에서 맞춘다.** 미리보기와 화면의 사진 크기(`imageSize`)는 Coil이
  EXIF 방향을 적용해 불러온 이미지에서 오고, 업로드·자동 보정 분석은 `OrientedBitmapDecoder`가 맞춘다.
  둘이 같은 방향을 낸다는 전제 위에 있으므로, 특정 사진만 방향이 어긋나면 둘을 비교한다.
- **중심 단위 좌표는 화면 조작용이다.** 화면의 자르기 프레임은 사진이 돌아가도 똑바로 서 있다. 원본 가운데를 원점으로
  두고 사진과 같이 돌린 좌표에서 보면 프레임이 기울지 않은 사각형이 되어, 끌기·늘리기 계산이 단순해진다.
  화면 픽셀과는 `CropGeometry.fitScale`로 오간다.
- **회전은 모두 시계 방향이다.** `PhotoEdit.rotationDegrees = quarterTurns × 90 + straightenDegrees`.
  `straightenDegrees`는 지금 화면에서 바꿀 수 없어 항상 0이다.

## 3. 색 조정

### 저장 값과 선형 값

사진 파일의 색 값(0..255)은 사람 눈에 맞춰 어두운 쪽을 촘촘하게 둔 값이다(**저장 값**, 감마 값).
빛의 양과 비례하지 않아서, 저장 값 128(0.5)은 빛의 양으로는 약 0.21이다. 노출처럼 "빛을 2배로" 바꾸는 계산은
빛의 양에 비례하는 값(**선형 값**)에서 해야 자연스럽다. 두 값 사이 변환은 `SrgbTransfer`에 있다.

### 처리 순서

`PhotoColorTransform`이 픽셀마다 이 순서로 바꾼다.

```text
저장 값 R·G·B
  → 선형 값으로 바꾼다                         SrgbTransfer.linear
  → 노출·따뜻함: 채널마다 곱한다                PhotoAdjustmentScale.exposureGain, WARMTH_GAIN
  → 채도: 밝기(회색)와 원래 색 사이를 벌리거나 좁힌다
  → 저장 값으로 되돌린다                        SrgbTransfer.gamma
  → 톤 곡선 표로 바꾼다                         PhotoToneCurve (블랙 포인트 → 그림자·하이라이트 → 대비)
  → 필터 행렬을 곱한다                          PhotoFilter
```

| 단계 | 식 (v = 조정 값 -1..1, w = 따뜻함) |
|---|---|
| 노출·따뜻함 | 선형 값에서 R·2^(1.5v)·(1 + 0.25w), G·2^(1.5v), B·2^(1.5v)·(1 − 0.25w) |
| 채도 | 선형 값의 밝기 L = 0.2125·R + 0.7154·G + 0.0721·B를 기준으로 L + (1 + v)·(색 − L) |
| 블랙 포인트 | v > 0이면 밝기 0.3·v 아래를 검게 하고 나머지를 다시 편다. v < 0이면 검은색을 0.3·(−v)까지 띄운다 |
| 그림자·하이라이트 | t += 0.25·v·4t(1−t)² (그림자, t = 1/3 부근) / t += 0.25·v·4t²(1−t) (하이라이트, t = 2/3 부근) |
| 대비 | t −= 0.6·v·sin(2πt)/2π (0.5보다 어두우면 더 어둡게, 밝으면 더 밝게) |

필터는 경우에 따라 입히는 곳이 다르지만, 모두 같은 `PhotoFilter` 행렬을 쓰므로 색은 같다.

| | 조정 없음 | 조정 있음 |
|---|---|---|
| 미리보기 | Compose `ColorFilter`로 그리면서 입힘 | 조정은 `PhotoColorTransform`(필터 없이), 필터는 Compose `ColorFilter` |
| 업로드 인코더 | `ColorMatrixColorFilter`로 그리면서 입힘(픽셀 루프 없음) | `PhotoColorTransform.of(edit)`가 조정 뒤 필터까지 적용 |

## 4. 조정 값과 자동 보정

### 값이 어디서 오는가

`PhotoEdit.adjustmentLayers`(`PhotoAdjustmentLayers`)는 조정 값을 세 개로 나눠 보관한다.

```text
항목마다:  사용자가 직접 만졌으면  → 사용자 값(manual)
          아니면                  → 자동 보정값(automatic) × 강도(intensity)
```

예: 자동 보정이 노출 0.4·대비 0.2를 정했고 강도가 1이다. 사용자가 눈금자로 노출을 0.3으로 내리면

| | 노출 | 대비 |
|---|---|---|
| 그대로 | 0.3 (직접 만짐) | 0.2 |
| 강도를 0.5로 낮춤 | 0.3 | 0.1 |
| 자동 보정을 끔 | 0.3 | 0 |

자동 보정을 끄거나 강도를 바꿔도 사용자가 직접 만진 항목은 화면에서 본 값 그대로 남고, 원본보다 어두워지는 식으로
엉뚱하게 바뀌지 않는다. 자동 보정을 켜기 전에 만진 항목도 같다. 켜도 그 항목은 사용자 값을 유지한다.

눈금자에는 자동 보정 몫(자동 보정값 × 강도)이 점으로 표시되고, 그 자리에서 멈칫한다. 그 자리로 되돌리면 직접 만진
표시가 지워져 그 항목은 다시 자동 보정을 따른다. 자동 보정이 꺼져 있으면 그 자리는 0이다.

### 자동 보정값을 정하는 방법

1. 자른 영역을 긴 변 256px로 줄여(`PhotoPixels.sampled`) 밝기 분포와 채도를 센다(`PhotoImageStatistics`).
2. `PhotoAutoAdjuster`가 노출 → 블랙 포인트 → 그림자·하이라이트·대비 → 채도 순서로 정한다.
   각 단계는 앞 단계를 적용했다고 가정한 밝기를 본다. 예: 노출을 올리기로 했으면, 블랙 포인트는 "노출을 올린 뒤
   가장 어두운 쪽"을 기준으로 정한다.
3. 과하게 바뀌지 않도록 목표까지 다 옮기지 않고 일부만 옮긴다.
   이때 보정한 사진을 다시 세지 않고 원래 히스토그램의 밝기 값만 옮겨 보므로 근사다.
4. 채도는 덜 선명한 사진일수록 더 올리되, 피부색 픽셀 비율만큼 덜 올린다. 피부는 조금만 진해져도 어색하기 때문이다.
   사진 전체가 피부색이면 0, 피부가 1/4이면 원래 올릴 양의 3/4이다. 피부색 기준은 `PhotoImageStatistics.isSkinTone`.

   | 단색 사진 | 자동 채도(화면 값) |
   |---|---|
   | 밝은 피부 (0.94, 0.78, 0.69) | 0 |
   | 어두운 피부 (0.45, 0.30, 0.22) | 0 |
   | 하늘 (0.53, 0.81, 0.92) | +19 |
   | 풀 (0.30, 0.60, 0.20) | +18 |

분석은 편집 화면을 처음 열 때, 자르기를 바꾼 뒤 탭을 옮길 때, 자동 보정을 켠 채 완료할 때 한다.
자동 보정을 켠 채 완료한 사진을 같은 자르기로 다시 열면, 저장된 자동 보정값을 그대로 쓰고 다시 분석하지 않는다.
자동 보정을 끄고 완료하면 자동 보정값은 저장하지 않는다(결과에 영향이 없으므로).

## 5. 무엇을 고치려면 어디를 보나

| 하고 싶은 일 | 파일 |
|---|---|
| 필터 추가·색 바꾸기 | `PhotoFilter` (`PhotoEdit.kt`), 이름은 `PhotoEditLabels.kt` |
| 조정 항목의 세기 바꾸기 | `PhotoAdjustmentScale`, 톤 곡선 모양은 `PhotoToneCurve` |
| 자동 보정 결과 조정 | `PhotoAutoAdjuster`의 상수. 바꾸면 `PhotoColorGoldenTest` 기대값도 바뀐다 |
| 자르기 프레임 동작 | `CropGeometry`, 화면 제스처는 `component/CropCanvas.kt` |
| 회전 버튼·비율 버튼 동작 | `PhotoEditViewModel.onRotateClick`, `onAspectRatioSelect` |
| 조정 눈금자 느낌(멈춤·관성·햅틱) | `component/AdjustmentRuler.kt`, `component/RulerScale.kt` |
| 업로드 결과가 미리보기와 다를 때 | 영역: `PhotoEditTransform`·`CropGeometry`, 색: `PhotoColorTransform`·필터 표(3절), 방향: Coil과 `OrientedBitmapDecoder`(2절) |
| 업로드 영역이 자동 보정이 분석한 영역과 다를 때 | `PhotoEditTransform`(인코더는 `sourceToOutputMatrix`, 분석은 `sourceX/Y`를 쓴다), 그다음 `PhotoPixels.sampled`와 `AndroidPostImageEncoder.applyEdit` |
