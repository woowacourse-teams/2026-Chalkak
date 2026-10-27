# React Native (Expo)

공통 기준은 [common/](../README.md#공통-문서)에 있다. 이 문서는 React Native에만 있는 API와 함정만 담는다. 근거: [raw/react-native.md](../../raw/react-native.md). 새로 만드는 클라이언트다.

## 버전 기준 (2026-10)

- Expo SDK 56 = React Native 0.85, React 19.2, TypeScript 6.0, **Hermes V1 기본**. [공식](https://expo.dev/changelog/sdk-56)
- SDK 55+는 New Architecture(Fabric, TurboModules, JSI)를 끌 수 없다. 서드파티 네이티브 라이브러리는 React Native Directory에서 호환을 확인하고 `npx expo-doctor`를 CI에 둔다.
- SDK 56 알려진 회귀: `react-native-reanimated`/`worklets`를 import하면 메모리가 크게 는다(SDK 57에서 수정 예정). 피드 애니메이션에 Reanimated를 쓸지 SDK 버전과 함께 결정한다. [open-questions](../open-questions.md#팀-결정-대상)
- SDK 56의 `expo-router`는 React Navigation 의존을 제거했다. 블로그의 `useFocusEffect` 예제는 import 위치가 다를 수 있다.
- Expo "latest" 문서는 SDK 56보다 앞선 내용을 섞어 보여 준다. 고정한 SDK 버전의 문서를 본다.

## 권장 스택

| 영역 | 선택 | 메모 |
| --- | --- | --- |
| 프레임워크 | Expo + Expo Router(파일 기반, typed routes) | RN 공식 문서가 프레임워크 사용을 권장 [공식](https://reactnative.dev/docs/environment-setup). 네이티브 변경은 config plugin |
| 언어 | TypeScript `strict: true` | `expo/tsconfig.base` 기본값은 느슨함 |
| 서버 상태 | TanStack Query | 클라이언트 상태(세션, UI)만 Zustand 등 작은 store. 서버 데이터를 store에 복제하지 않음 |
| 응답 검증 | zod 등 런타임 스키마 검증 [일반지식] | `fetch`는 4xx/5xx에서 reject하지 않음 |
| 리스트 | FlatList(고정 높이면 `getItemLayout`) 또는 FlashList v2 | |
| 이미지 | `expo-image`(`recyclingKey`, `cachePolicy`) | |
| 보안 저장 | `expo-secure-store` | AsyncStorage는 암호화되지 않음 |
| 푸시 | `expo-notifications` + **FCM 기기 토큰** | §푸시 |
| 빌드·배포 | EAS Build, EAS Update | |

## TanStack Query를 RN에 연결

- 브라우저 이벤트가 없으므로 앱 루트에서 한 번: `onlineManager`에 NetInfo, `focusManager`에 `AppState`를 연결한다. 빠뜨리면 오프라인 복귀·앱 복귀 시 다시 불러오지 않는다. [공식](https://tanstack.com/query/latest/docs/framework/react/react-native)
- 탭 화면에서 비활성 화면 쿼리가 계속 구독되지 않게 `subscribed` 옵션을 화면 포커스와 연결한다.
- 기본 재시도 값을 [공통 상수](../common/networking-errors-retry.md#찰캌-공통-상수)로 덮어쓴다(jitter 포함).
- 좋아요 낙관적 업데이트는 [offline-optimistic-polling.md §3](../common/offline-optimistic-polling.md#3-낙관적-업데이트)의 캐시 방식(`cancelQueries` → 스냅샷 → `setQueryData` → `onError` 복원 → `onSettled` 무효화).

## 성능

- 성능은 **release 빌드**로 측정한다. 프로덕션에서 `console.log` 제거(`babel-plugin-transform-remove-console`). [공식](https://reactnative.dev/docs/performance)
- 피드: `keyExtractor`는 postId(index 금지), 아이템은 `React.memo`, `renderItem`은 고정 참조. 좋아요 하나가 목록 전체를 다시 그리지 않는지 Profiler로 확인. [공식](https://reactnative.dev/docs/optimizing-flatlist-configuration)
- 재활용 리스트에서 `expo-image`의 `recyclingKey`가 없으면 이전 이미지 잔상이 보인다.
- 애니메이션은 `useNativeDriver: true` 또는 Reanimated. `onPress`에서 무거운 렌더는 `requestAnimationFrame`으로 미룬다.
- React Compiler는 healthcheck 통과 시 도입 검토.

## 플랫폼 차이 처리

- `Platform.select`에 `default`를 넣는다. `Platform.Version`은 Android 숫자, iOS 문자열.
- `KeyboardAvoidingView`는 iOS·Android `behavior`를 각각 지정하고 실기기에서 확인.
- Android 뒤로: `BackHandler`(나중 등록이 먼저, `true` 반환 시 중단, cleanup에서 `.remove()`). Modal 안에서는 동작이 다르다. [공식](https://reactnative.dev/docs/backhandler)
- 안전 영역은 `react-native-safe-area-context`로 통일.
- 그림자: iOS 전용 `shadow*`만 쓰면 Android에서 안 보인다. `boxShadow` 또는 `elevation` 병행.

## 사진과 업로드

- `expo-image-picker`: 앨범 선택은 권한 불필요, 카메라는 필요. **iOS에서 기본 `Passthrough`는 HEIC/AVIF 원본을 그대로 준다.** 업로드 입력은 WebP뿐이므로 반드시 변환한다. [공식](https://docs.expo.dev/versions/latest/sdk/imagepicker/)
- Android에서 피커가 끝난 뒤 `MainActivity`가 죽어 결과를 잃을 수 있다. `getPendingResultAsync()`로 복구.
- `exif: true`를 쓰면 위치 정보가 따라온다. 쓰지 않는다.
- `expo-file-system`의 `file.upload`는 4xx/5xx에서도 resolve한다. 상태 코드를 직접 검사한다. iOS 기본 `sessionType`은 background지만 앱이 재시작되면 JS 쪽 진행률·취소는 복구되지 않는다. [공식](https://docs.expo.dev/versions/latest/sdk/filesystem/)
- WebP 인코딩 수단은 [media-upload.md §3.1](../common/media-upload.md#31-인코딩).

## 푸시

- 찰캌 서버는 FCM으로 보낸다. **`getDevicePushTokenAsync()`(FCM/APNs 기기 토큰)** 를 쓰고 Expo Push Service 토큰(`getExpoPushTokenAsync`)을 쓰지 않는다. iOS에서는 APNs 토큰이 나오므로 FCM 토큰을 얻으려면 `@react-native-firebase/messaging` 등이 필요한지 도입 전에 확인한다. [공식](https://docs.expo.dev/push-notifications/sending-notifications-custom/) [일반지식]
- 순서: Android 채널 생성 → 권한 요청 → 토큰. iOS 권한은 `ios.status`로 판단.
- 포그라운드 표시는 `setNotificationHandler`. 콜드 스타트 탭은 `useLastNotificationResponse`/`getLastNotificationResponseAsync`.
- Android Expo Go는 SDK 53부터 원격 푸시 불가 → development build로 검증.

## 보안·환경

- `EXPO_PUBLIC_*`는 번들에 평문으로 들어간다. EAS Secrets도 클라이언트에 인라인하면 노출된다.
- `expo-secure-store`는 일부 iOS에서 약 2KB 넘는 값을 거부할 수 있다. Android Auto Backup에서 제외한다.
- Hermes 바이트코드는 보호가 아니다. Sentry RN SDK로 source map 자동 업로드. [공식](https://docs.sentry.io/platforms/react-native/sourcemaps/)

## EAS Update(OTA)

- JS·문구·에셋 수정만 OTA. 네이티브 코드·권한·SDK 변경은 새 바이너리. `runtimeVersion`은 RN 버전을 바꾸면 함께 바꾼다(Hermes 바이트코드 호환). [공식](https://docs.expo.dev/eas-update/introduction/)
- Apple 2.5.2 때문에 OTA로 기능을 추가하지 않는다.

## 오류 처리

- Expo Router route에서 `ErrorBoundary`를 export하고 `+not-found.tsx`를 둔다. [공식](https://docs.expo.dev/router/error-handling/)
- 처리되지 않은 Promise rejection을 전역에서 잡아 크래시 리포터로 보낸다.

## 테스트

- Jest + React Native Testing Library(사용자 관점: 텍스트·역할로 찾기). 큰 스냅샷 테스트는 쓰지 않는다. [공식](https://reactnative.dev/docs/testing-overview)
- E2E는 Maestro(공용 flow, `testID`). 네이티브 버그는 컴포넌트 테스트로 잡히지 않는다.

## 체크리스트

- [ ] SDK 버전 고정, `expo-doctor` 통과, 사용 라이브러리 New Architecture 호환
- [ ] TS strict, typed routes, 응답 런타임 검증
- [ ] `onlineManager`/`focusManager` 연결
- [ ] iOS HEIC 사진이 WebP로 업로드된다(실기기)
- [ ] FCM 기기 토큰 등록, 콜드 스타트 탭 이동
- [ ] release 빌드로 피드 스크롤·좋아요 리렌더 확인
