# 사진 선택, 인코딩, 업로드

근거: [POST-004](../../../docs/business-rules/rules/post.md), [SIGN-002](../../../docs/business-rules/rules/sign.md), 기존 Android 결정 [게시물 이미지 인코딩 정책](../../../client/android/docs/architecture/post-image-encoding.md)·[사진 편집 구조](../../../client/android/docs/architecture/photo-edit.md), [raw/network-resilience-offline.md §7](../../raw/network-resilience-offline.md), 각 플랫폼 raw의 미디어 절.

## 1. 찰캌 업로드 규격

| 항목 | 게시물 사진 | 사인 이미지 |
| --- | --- | --- |
| 형식 | **WebP** | **PNG** |
| 최대 크기 | **5 MiB**. 실제 기준은 발급 응답의 `maxBytes` | **1 MiB** |
| 업로드 URL 유효 | 5분 | 5분 |
| 업로드 ID 유효 | 발급 후 1시간 | — |
| 연결 | 업로드 한 건은 게시물 하나에 한 번만 | 처리(원본·썸네일) 완료 후 활성 |
| 규칙 | POST-004 | SIGN-002, SIGN-003 |

- **크기 기준은 상수가 아니라 서버 응답의 `maxBytes`다.** 서버가 값을 바꿔도 다섯 클라이언트가 같이 따라간다. [찰캌규칙]
- **5분은 URL 수명, 1시간은 업로드 ID 수명이다.** URL이 만료되면 기존 ID를 재사용하지 않고 새로 발급받는다(기존 Android 결정). [찰캌규칙]
- 사인은 처리 중일 때 `SIGNATURE_PROCESSING_PENDING`이 올 수 있다. [errorCode 표](networking-errors-retry.md#12-찰캌-오류-응답과-errorcode)

## 2. 사진 고르기와 촬영

### 권한 없이 고르기

모든 플랫폼에 "앱이 사진 보관함 권한을 받지 않고 사용자가 고른 사진만 받는" 피커가 있다. 찰캌은 한 장만 고르므로 보관함 권한을 요청하지 않는다. [공식]

| 플랫폼 | 피커 | 비고 |
| --- | --- | --- |
| iOS | `PhotosPicker`(SwiftUI) / `PHPickerViewController` | 권한 불필요. `loadTransferable`로 파일 표현을 받는다 [공식·미열람] |
| Android | Photo Picker `PickVisualMedia` | API 19+ 백포트(Google Play 서비스 경유). `READ_MEDIA_IMAGES` 불필요 [공식](https://developer.android.com/training/data-storage/shared/photopicker) |
| Flutter | `image_picker` | iOS는 PHPicker, Android는 Photo Picker를 쓴다 [공식](https://pub.dev/packages/image_picker) |
| RN | `expo-image-picker` | iOS에서 HEIC를 그대로 줄 수 있다 → §3 변환 필수 [공식](https://docs.expo.dev/versions/latest/sdk/imagepicker/) |
| Web | `<input type="file" accept="image/*">` (+ `capture`) | 권한 없음. iOS Safari는 HEIC를 JPEG로 바꿔 줄 수 있으나 보장하지 않는다 [일반지식] |

- 카메라 촬영은 카메라 권한이 필요하다. 권한은 **촬영 버튼을 누른 순간** 요청하고, 거부되면 설정으로 가는 안내와 "앨범에서 고르기" 대안을 보여 준다. 권한 UX는 [ux-accessibility.md §5](ux-accessibility.md#5-권한-요청).
- iOS는 `Info.plist`의 사용 목적 문자열(`NSCameraUsageDescription` 등)이 없으면 크래시하고 심사에서도 반려된다. [공식·미열람]

## 3. 인코딩과 업로드 흐름

### 3.1 인코딩

기존 Android 앱의 파이프라인이 기준이다. 다섯 플랫폼은 같은 단계와 같은 상수를 쓴다. [찰캌규칙]

```text
크기만 읽기 → 긴 변 ≤ 4096으로 다운샘플 디코딩 → EXIF 방향 보정 → 편집(자르기·회전·조정·필터) 적용
→ WebP 인코딩: 품질 90 → 80 → 60 → 40 순으로 maxBytes 이하면 성공
→ 안 되면 해상도 ×0.85 후 다시(최대 3회) → 끝내 실패하면 SizeLimitExceeded
```

- 원본을 통째로 메모리에 펼치지 않는다. 4000×3072 사진은 메모리에서 약 48MB다.
- 인코딩은 메인 스레드 밖에서 한다(Flutter는 `compute`/isolate, Web은 Worker + `OffscreenCanvas`).
- **HEIC**: iOS 카메라 기본 형식이다. 업로드 입력은 WebP뿐이므로 모든 플랫폼이 클라이언트에서 디코딩 후 WebP로 인코딩한다.
- **EXIF·위치 정보**: 다시 인코딩하면 보통 메타데이터가 떨어지지만 플랫폼·라이브러리마다 다르다. 위치 정보를 남기지 않는 것을 기준으로 하되, 정책 확정은 [open-questions](../open-questions.md#백엔드-확인-대상)에 올린다. 방향 태그는 버리기 전에 픽셀에 반영한다.

| 플랫폼 | WebP 인코딩 수단 | 주의 |
| --- | --- | --- |
| iOS | ImageIO `CGImageDestination` + WebP UTType | WebP **쓰기** 지원 OS 버전 확인 필요 [공식·미열람]. 기존 iOS 앱 구현을 먼저 본다 |
| Android | `Bitmap.compress(WEBP_LOSSY, q)` | API 30 미만은 `WEBP` 상수. 하드웨어 가속 없는 CPU 연산 |
| Flutter | `flutter_image_compress`(WebP 지원) 또는 네이티브 채널 | 패키지 유지 상태 검증 필요 [일반지식] |
| RN | `expo-image-manipulator`의 `SaveFormat.WEBP` [공식·미열람] | Android만 되는 옵션이 있는지 확인 |
| Web | `canvas.toBlob(cb, 'image/webp', q)` | Safari의 WebP **인코딩** 지원이 제한적이다. 지원하지 않으면 PNG/JPEG로 떨어지므로 결과 `type`을 반드시 검사하고 WASM 인코더를 대안으로 둔다 [일반지식] |

### 3.2 업로드 흐름

```text
(편집 완료 즉시, 백그라운드)
  POST /posts/uploads → { uploadId, uploadUrl(5분), maxBytes }
  maxBytes에 맞춰 로컬 WebP 준비 (스토리지 전송은 아직 안 함)
(사용자가 "전시하기")
  준비 완료 → 스토리지 PUT → POST /posts { topicId, photoUploadId, title }
  준비 중   → 전시 중 상태로 기다렸다가 자동 진행
  URL 만료  → 새로 발급 → 파일이 새 maxBytes에 맞으면 재사용, 아니면 다시 인코딩
```

- 사진을 바꾸거나 편집 값을 바꿔 다시 완료하면 이전 준비 작업을 취소하고 결과 파일을 지운다. 늦게 끝난 이전 작업은 세대(generation) 비교로 새 상태를 덮지 못하게 한다.
- 스토리지 PUT은 멱등이라 재시도해도 된다. `POST /posts`는 자동 재시도하지 않는다. 응답이 유실되면 `GET /posts/today`로 작성 여부를 확인한다. [networking §2.1](networking-errors-retry.md#21-무엇을-재시도하나)
- 생성에 성공해도 게시물은 `VALIDATING`이다. 업로드 완료 화면은 "검수 후 공개"를 알리고 피드에 미리 끼워 넣지 않는다(POST-006).
- 한 주제에 활성 게시물은 하나다(POST-003). 오늘 이미 올렸다면 작성 진입 자체를 막는다(POST-011).
- 정지 회원은 업로드 발급부터 막힌다(USER-002).

## 4. 백그라운드 업로드

**현재 기준은 "앱이 포그라운드에 있는 동안 업로드를 끝낸다"이다.** 기존 Android 앱은 화면을 나가면 준비 작업을 취소하고 WorkManager 복구를 지원하지 않는다. 다섯 클라이언트를 같게 만들려면 이 기준에서 출발한다. 앱이 백그라운드로 가도 업로드를 끝내는 기능은 웹에서 같은 수준으로 만들 수 없으므로, 도입하려면 팀 결정이 필요하다. [open-questions](../open-questions.md#팀-결정-대상) [찰캌규칙]/[제안]

도입한다면 플랫폼별 수단은 아래다.

| 플랫폼 | 수단 | 핵심 제약 |
| --- | --- | --- |
| iOS | background `URLSession` | **파일 기반** `uploadTask(with:fromFile:)`만 가능, delegate 필수, 완료 시 앱 재실행 처리(`handleEventsForBackgroundURLSession`) [공식](https://developer.apple.com/documentation/foundation/urlsessionconfiguration/background(withidentifier:)) |
| Android | WorkManager(+ 사용자 시작 장시간 작업이면 foreground 알림) | unique work + `ExistingWorkPolicy.KEEP`으로 중복 방지, `NetworkType.CONNECTED` 제약 [공식](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started) |
| Flutter | `workmanager`, `background_downloader` 등 | 결국 위 네이티브 수단을 감싼다. 패키지 검증 필요 |
| RN | 네이티브 모듈 필요 | Expo 기본 모듈만으로는 어렵다 [일반지식] |
| Web | 불가에 가깝다 | Background Sync/Fetch는 Chromium 한정. 탭을 닫으면 중단 [공식·미열람] |

## 5. 이미지 표시

- 목록 썸네일과 상세 원본을 구분해 받는다(서버가 주는 URL 기준).
- 디스크 캐시를 쓰고, 목록에서는 표시 크기로 다운샘플한다.

| 플랫폼 | 권장 |
| --- | --- |
| iOS | `AsyncImage`는 캐시 제어가 약하다. 기존 앱 방식 또는 Nuke·Kingfisher 등 [일반지식] |
| Android | Coil(기존 앱) |
| Flutter | `cached_network_image` |
| RN | `expo-image` |
| Web | `<img loading="lazy" decoding="async">` + `width`/`height` 명시(CLS 방지), `srcset` |

## 체크리스트

- [ ] 보관함 권한 없이 사진을 고른다
- [ ] 카메라 권한 거부 시 앨범 대안과 설정 이동이 있다
- [ ] HEIC 입력이 WebP로 변환된다(iOS 기기 실사진으로 확인)
- [ ] 결과 파일이 WebP이고 `maxBytes` 이하다(웹은 `blob.type` 확인)
- [ ] 방향이 맞고 위치 정보가 남지 않는다
- [ ] URL 5분 만료 후 전시하기가 새 발급으로 성공한다
- [ ] `POST /posts` 응답 유실 시 중복 생성 없이 오늘 작성 여부를 다시 확인한다
- [ ] 사진 교체·화면 이탈 시 이전 작업이 취소되고 임시 파일이 지워진다
