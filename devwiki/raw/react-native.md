---
title: React Native 앱 개발 (New Architecture, Expo) 리서치 노트
topic: 찰캌(Chalkak)을 iOS/Android React Native 앱으로 재구현하기 위한 RN/Expo 가이드라인 정리
collected: 2026-10-27
language: ko
sources:
  - https://reactnative.dev/docs/environment-setup
  - https://reactnative.dev/architecture/landing-page
  - https://reactnative.dev/docs/hermes
  - https://reactnative.dev/docs/performance
  - https://reactnative.dev/docs/optimizing-flatlist-configuration
  - https://reactnative.dev/docs/security
  - https://reactnative.dev/docs/platform-specific-code
  - https://reactnative.dev/docs/network
  - https://reactnative.dev/docs/shadow-props
  - https://reactnative.dev/docs/keyboardavoidingview
  - https://reactnative.dev/docs/backhandler
  - https://reactnative.dev/docs/accessibility
  - https://reactnative.dev/docs/testing-overview
  - https://reactnative.dev/docs/the-new-architecture/using-codegen
  - https://docs.expo.dev/router/introduction/
  - https://docs.expo.dev/router/error-handling/
  - https://docs.expo.dev/guides/new-architecture/
  - https://docs.expo.dev/guides/using-hermes/
  - https://docs.expo.dev/guides/react-compiler/
  - https://docs.expo.dev/guides/typescript/
  - https://docs.expo.dev/guides/environment-variables/
  - https://docs.expo.dev/guides/permissions/
  - https://docs.expo.dev/linking/ios-universal-links/
  - https://docs.expo.dev/linking/android-app-links/
  - https://docs.expo.dev/build/introduction/
  - https://docs.expo.dev/eas-update/introduction/
  - https://docs.expo.dev/eas-update/rollouts/
  - https://docs.expo.dev/push-notifications/overview/
  - https://docs.expo.dev/versions/latest/sdk/notifications/
  - https://docs.expo.dev/versions/latest/sdk/securestore/
  - https://docs.expo.dev/versions/latest/sdk/imagepicker/
  - https://docs.expo.dev/versions/latest/sdk/image/
  - https://docs.expo.dev/versions/latest/sdk/filesystem/
  - https://docs.expo.dev/versions/latest/sdk/safe-area-context/
  - https://expo.dev/changelog/sdk-56.md
  - https://tanstack.com/query/latest/docs/framework/react/react-native
  - https://tanstack.com/query/latest/docs/framework/react/guides/optimistic-updates
  - https://docs.swmansion.com/react-native-reanimated/docs/fundamentals/glossary/
  - https://shopify.github.io/flash-list/docs/
  - https://developer.apple.com/app-store/review/guidelines/
---

# React Native 앱 개발 (New Architecture, Expo)

> 수집 방식: 공식 문서(WebFetch)로 직접 확인한 내용만 출처 링크와 함께 기재했다. 공식 문서에서 확인하지 못했고 일반 지식에 기반한 항목은 **[미검증]** 으로 표시했다. 문서 버전이 빠르게 바뀌므로(아래 0절) 구현 시점에 재확인할 것.

## 0. 버전 현황 (2026-10 기준, 확인된 범위)

- Expo SDK 56은 React Native 0.85, React 19.2를 탑재하고, Hermes V1이 기본 엔진이다. (`useHermesV1`을 `expo-build-properties`에서 끄는 opt-out 가능) ([출처](https://expo.dev/changelog/sdk-56.md))
- SDK 56 주요 breaking: `expo/fetch`가 `globalThis.fetch` 기본값(`EXPO_PUBLIC_USE_RN_FETCH=1`로 opt-out), `expo-file-system`의 `copy()/move()`가 async로 변경, `expo-router`가 더 이상 React Navigation에 의존하지 않음(codemod 제공), iOS 최소 16.4 / Xcode 26.4. ([출처](https://expo.dev/changelog/sdk-56.md))
- SDK 56 알려진 회귀: `react-native-worklets`/`react-native-reanimated` import 시 메모리 사용량이 크게 증가하는 문제가 있고 SDK 57에서 수정 예정이라고 명시. Reanimated를 쓰는 찰캌은 SDK 선택 시 반드시 확인. ([출처](https://expo.dev/changelog/sdk-56.md))
- 참고: Expo "latest" 문서의 환경변수 페이지는 SDK 58 동작을 언급하고, RN 문서 사이트는 0.87 버전 페이지를 노출한다. 즉 "latest" 문서는 SDK 56보다 앞선 내용을 포함할 수 있으므로 **채택한 SDK 버전의 문서를 기준으로 읽어야 한다.** ([출처1](https://docs.expo.dev/guides/environment-variables/), [출처2](https://reactnative.dev/docs/the-new-architecture/using-codegen))

---

## 1. 프레임워크(Expo)와 Expo Router

### 핵심 개념
- RN 공식 문서는 "새 앱을 만든다면 Framework 사용을 권장"한다. Framework 없이 쓰면 핵심 기능을 직접 구현하거나 라이브러리를 조합해야 하며 시작·유지보수 모두 비용이 든다고 설명한다. ([출처](https://reactnative.dev/docs/environment-setup))
- Expo는 file-based routing, 고품질 universal 라이브러리, 네이티브 파일을 직접 관리하지 않고 네이티브 코드를 수정하는 config plugin을 제공한다. 시작: `npx create-expo-app@latest`. ([출처](https://reactnative.dev/docs/environment-setup))
- Expo Router: `app` 디렉터리에 파일을 추가하면 route가 된다. 모든 화면이 자동으로 deep link 가능하고, typed routes(존재하지 않는 링크는 타입 에러), React Native Screens 기반 네이티브 내비게이션을 제공한다. ([출처](https://docs.expo.dev/router/introduction/))

### 공식 가이드라인(권장 방식)
- 신규 앱은 Expo + Expo Router로 시작하고 네이티브 변경은 config plugin / Continuous Native Generation으로 처리한다. ([출처](https://reactnative.dev/docs/environment-setup))
- 네이티브 모듈이 필요하면 Expo Go가 아닌 development build를 쓴다(아래 8절 푸시 참고). EAS Build의 `development` 프로필 등 profile을 `eas.json`에 정의한다. ([출처](https://docs.expo.dev/build/introduction/))
- Router 레벨 에러 처리: route 파일에서 `ErrorBoundary`를 export하면 해당 route의 렌더 에러를 잡고 `error`, `retry` props를 받는다. 없으면 가장 가까운 부모의 boundary로 전파된다. ([출처](https://docs.expo.dev/router/error-handling/))
- 네이티브 앱에는 서버가 없어 404 개념이 없지만, `+not-found.tsx`로 잘못된 route를 처리하고 "/"로 돌아가는 링크를 두라고 권장한다. ([출처](https://docs.expo.dev/router/error-handling/))

### 자주 하는 실수·놓치기 쉬운 점
- SDK 56부터 `expo-router`가 React Navigation 의존을 제거했다. React Navigation hook(`useFocusEffect`, `useIsFocused`, `useHeaderHeight` 등)을 직접 쓰는 기존 예제/블로그 코드는 import 경로가 달라질 수 있으니 codemod와 마이그레이션 문서를 확인. ([출처](https://expo.dev/changelog/sdk-56.md))
- SDK 56의 Stack v5 / `Stack.Toolbar`는 **experimental** 표기다. 프로덕션 핵심 화면에는 신중히. ([출처](https://expo.dev/changelog/sdk-56.md))
- Framework를 쓰지 않는 선택은 "특수 제약이 있을 때"만 정당화된다. 찰캌(사진 업로드, 푸시, 딥링크)은 Expo가 제공하는 범위 안에 있다. ([출처](https://reactnative.dev/docs/environment-setup))

### 체크리스트
- [ ] `create-expo-app` + Expo Router, SDK 버전 고정 후 `npx expo-doctor@latest` 실행
- [ ] route별 또는 root layout에 `ErrorBoundary`, `+not-found.tsx` 정의
- [ ] 사용 SDK 버전의 Router 문서와 React Navigation 의존 여부 확인

---

## 2. New Architecture (Fabric, TurboModules, JSI, Bridgeless)

### 핵심 개념
- JSI: 비동기 bridge를 대체. JS가 C++ 객체를 직접 참조해 직렬화 없이 호출한다. Fabric: 새 렌더링 시스템(동기 레이아웃 접근, concurrent rendering). TurboModules: 새 네이티브 모듈 시스템. 문서는 "bridgeless"라는 용어 대신 "비동기 bridge가 JSI로 대체됨"으로 서술한다. ([출처](https://reactnative.dev/architecture/landing-page))
- RN 0.76부터 신규 프로젝트에서 기본 활성화. ([출처](https://reactnative.dev/architecture/landing-page))
- Expo: SDK 55 이상은 항상 New Architecture를 사용하며 끌 수 없다(`newArchEnabled: false`는 무시됨). RN 0.82에서 비활성화 옵션 제거. Legacy는 2025-06 동결되어 SDK 54 이하에서만 가능. Expo Go는 New Architecture만 지원. ([출처](https://docs.expo.dev/guides/new-architecture/))
- 신규 동기 레이아웃(`useLayoutEffect`)과 concurrent 기능(`startTransition` 등)을 쓰려면 앱 코드 수정이 필요할 수 있고, 켠다고 자동으로 빨라지는 것은 아니라고 문서가 명시한다. ([출처](https://reactnative.dev/architecture/landing-page))

### 공식 가이드라인(권장 방식)
- 라이브러리 선택 시 React Native Directory에서 New Architecture 호환 여부를 확인하고, `npx expo-doctor@latest`가 의존성을 Directory 기준으로 검사한다. 모든 `expo-*` 패키지는 SDK 53부터 지원. ([출처](https://docs.expo.dev/guides/new-architecture/))
- interop layer(RN 0.74부터 기본)로 구형 라이브러리 다수가 동작하지만 완벽하지 않고, 서드파티 네이티브 코드를 포함한 라이브러리가 가장 위험하다. ([출처](https://docs.expo.dev/guides/new-architecture/))
- 자체 네이티브 모듈이 필요하면 Codegen 스펙(`Native*.ts` 파일명 규칙, `codegenConfig`)을 쓴다. ([출처](https://reactnative.dev/docs/the-new-architecture/using-codegen))

### 자주 하는 실수·놓치기 쉬운 점
- 오래된 블로그의 "bridge 기반" 해결법(`NativeModules` 직접 호출 등)이나 미유지 라이브러리를 그대로 채택. 유지보수 상태/호환 여부 확인 필수. ([출처](https://docs.expo.dev/guides/new-architecture/))
- Gradle/Podfile에서 New Architecture를 끄는 옵션은 RN 문서에 있으나, Expo SDK 55+에서는 무의미하다. ([출처1](https://reactnative.dev/architecture/landing-page), [출처2](https://docs.expo.dev/guides/new-architecture/))
- FlashList v2, Reanimated 4 등 최신 라이브러리는 New Architecture 전용이다(3절 참고).

### 체크리스트
- [ ] 모든 서드파티 네이티브 라이브러리를 React Native Directory로 검증
- [ ] `expo-doctor` CI 단계 추가
- [ ] 커스텀 네이티브 코드가 필요하면 Expo Modules API 또는 Codegen 사용

---

## 3. Hermes와 성능

### 핵심 개념
- Hermes: RN 기본 엔진. 시작 시간·메모리·앱 크기 개선. 릴리스 빌드에서 JS를 Hermes bytecode(`.hbc`)로 사전 컴파일. ([출처](https://reactnative.dev/docs/hermes))
- Expo 환경에서 `eas update`, `npx expo export`는 bytecode 번들과 source map을 생성한다. bytecode는 Hermes 버전마다 달라질 수 있어, `react-native` 버전을 바꾸면 `runtimeVersion`도 갱신해야 앱이 실행 시 크래시하지 않는다. ([출처](https://docs.expo.dev/guides/using-hermes/))
- SDK 56은 Hermes V1이 기본. ([출처](https://expo.dev/changelog/sdk-56.md))
- 스레드 모델: JS thread는 비즈니스 로직·React 렌더링·API 호출·터치 이벤트를 처리하고, UI(main) thread는 스크롤·네이티브 전환을 처리. JS가 막혀도 `ScrollView` 스크롤과 native-stack 전환은 부드럽게 유지된다. 목표는 60fps(프레임당 16.67ms). ([출처](https://reactnative.dev/docs/performance))

### 공식 가이드라인(권장 방식)
- 성능은 **반드시 release 빌드**에서 측정(개발 모드는 크게 느림). ([출처](https://reactnative.dev/docs/performance))
- 프로덕션에서 `console.log`가 JS thread 병목이 될 수 있으므로 `babel-plugin-transform-remove-console`로 제거(서드파티 로그 포함). ([출처](https://reactnative.dev/docs/performance))
- `Animated`는 `useNativeDriver: true`가 아니면 JS thread에서 돈다. 애니메이션 중 무거운 JS 작업은 `requestIdleCallback` 등으로 미룬다. ([출처](https://reactnative.dev/docs/performance))
- iOS에서 `Image` width/height 애니메이션은 매번 재크롭·재스케일이므로 `transform: scale`을 쓴다. ([출처](https://reactnative.dev/docs/performance))
- Reanimated: worklet은 UI thread에서 실행 가능한 짧은 함수(`"worklet"` 지시어, Babel 플러그인이 자동 처리), `useSharedValue`는 JS/UI thread 간 값을 동기화한다. 제스처·스크롤 연동 애니메이션은 이쪽을 사용. ([출처](https://docs.swmansion.com/react-native-reanimated/docs/fundamentals/glossary/))
- React Compiler: 자동 메모이제이션. `"reactCompiler": true`(app config `experiments`) 설정, SDK 54+ Babel 자동 구성, 켜면 수동 `useCallback/useMemo/React.memo` 제거 가능. `node_modules`는 대상 아님, `"use no memo"`로 개별 제외, `npx react-compiler-healthcheck`로 사전 점검. ([출처](https://docs.expo.dev/guides/react-compiler/))

### 자주 하는 실수·놓치기 쉬운 점
- 개발 모드/에뮬레이터 체감으로 성능을 판단. ([출처](https://reactnative.dev/docs/performance))
- `onPress`에서 무거운 re-render를 같은 프레임에 실행해 터치 피드백이 늦어짐 → `requestAnimationFrame`으로 감싼다. ([출처](https://reactnative.dev/docs/performance))
- Android에서 이동 중인 뷰(스크롤/회전)에 투명 배경 텍스트+이미지가 겹치면 매 프레임 합성 비용이 든다. `renderToHardwareTextureAndroid`로 완화하되 남용하면 메모리 증가. ([출처](https://reactnative.dev/docs/performance))
- SDK 56에서 Reanimated/worklets 메모리 급증 회귀(0절). ([출처](https://expo.dev/changelog/sdk-56.md))
- Compiler를 켰는데 규칙 위반(렌더 중 mutation 등) 코드가 있으면 healthcheck에서 걸린다. 클래스 컴포넌트는 최적화되지 않는다. ([출처](https://docs.expo.dev/guides/react-compiler/))

### 체크리스트
- [ ] 성능 프로파일링은 release 빌드 + Perf Monitor(JS/UI fps)
- [ ] 프로덕션 console 제거 설정
- [ ] 애니메이션은 native driver 또는 Reanimated
- [ ] React Compiler 도입 여부 결정(healthcheck 통과 시)

---

## 4. 리스트 성능 (FlatList / FlashList) — 찰캌 피드의 핵심

### 핵심 개념
- FlatList 튜닝 prop과 기본값: `initialNumToRender` 10, `maxToRenderPerBatch` 10, `windowSize` 21(뷰포트 높이 단위), `removeClippedSubviews`는 Android에서 기본 true(iOS에서는 transform/absolute 위치 시 콘텐츠 누락 가능). ([출처](https://reactnative.dev/docs/optimizing-flatlist-configuration))
- 높이가 고정된 아이템이면 `getItemLayout`을 제공해 비동기 레이아웃 계산을 제거("매우 바람직한 최적화"). ([출처](https://reactnative.dev/docs/optimizing-flatlist-configuration))
- FlashList v2는 New Architecture 대상이며, 구버전 앱은 v1.x 문서를 따라야 한다. ([출처](https://shopify.github.io/flash-list/docs/))
- RN 성능 문서도 느린 FlatList의 대안으로 FlashList, Legend List를 언급한다. ([출처](https://reactnative.dev/docs/performance))

### 공식 가이드라인(권장 방식)
- `keyExtractor`는 안정적이고 고유한 id를 반환(index 금지). `renderItem`은 `useCallback`으로 고정하고 아이템 컴포넌트는 `React.memo`로 감싼다(React Compiler 사용 시 자동화 가능). ([출처](https://reactnative.dev/docs/optimizing-flatlist-configuration))
- 아이템은 가볍게, 이미지는 썸네일/크롭된 것을 사용. ([출처](https://reactnative.dev/docs/optimizing-flatlist-configuration))
- recycling 리스트(FlashList)에서는 expo-image의 `recyclingKey`를 지정해 이전 이미지가 잠깐 보이는 현상을 방지. ([출처](https://docs.expo.dev/versions/latest/sdk/image/))

### 자주 하는 실수·놓치기 쉬운 점
- `windowSize`를 줄이면 메모리는 절약되나 빈 영역(blank) 증가, 키우면 반대. 트레이드오프를 측정 없이 조정. ([출처](https://reactnative.dev/docs/optimizing-flatlist-configuration))
- `renderItem`에 인라인 화살표 함수 + 비메모 아이템으로 좋아요 1회에 전체 리스트 re-render.
- FlashList의 `getItemType`, `estimatedItemSize` 등 세부 API는 이번 조사에서 해당 페이지를 직접 확인하지 못했다. **[미검증]** FlashList 사용 페이지/Known issues/Migrating to v2 문서를 구현 시 확인. ([출처](https://shopify.github.io/flash-list/docs/))

### 체크리스트
- [ ] 피드 리스트: 안정 key, memo 아이템, 썸네일 URL 사용
- [ ] 고정 높이면 `getItemLayout`; 가변이면 FlashList 검토
- [ ] 좋아요 토글이 해당 아이템만 re-render하는지 React DevTools Profiler로 확인

---

## 5. 서버 상태·클라이언트 상태 (TanStack Query, Zustand/Redux Toolkit)

> 일반 retry/offline 개념은 다른 리서치에서 다룸. 여기서는 RN 고유 연결부만 정리.

### 핵심 개념 / RN 고유 설정
- 네이티브에는 브라우저의 online/focus 이벤트가 없으므로 `onlineManager.setEventListener`에 NetInfo 리스너를 연결(`isConnected` 기반 boolean). 시작 시 이벤트가 안 올 수 있어 초기 상태를 읽는 Expo Network 변형도 문서에 있다. ([출처](https://tanstack.com/query/latest/docs/framework/react/react-native))
- 앱 포커스: window focus 대신 `AppState` 변화를 구독해 `focusManager.setFocused(status === 'active')` 호출(web에서는 호출 금지 가드). ([출처](https://tanstack.com/query/latest/docs/framework/react/react-native))
- 화면 포커스 refetch: `useFocusEffect`로 stale·active 쿼리를 `refetchQueries`(첫 mount 호출은 ref로 skip). 또는 `useIsFocused`를 `useQuery`의 `subscribed` 옵션에 넣어 비활성 화면의 구독/리렌더를 막는다. ([출처](https://tanstack.com/query/latest/docs/framework/react/react-native))
- DevTools는 서드파티: Rozenite 플러그인, macOS 독립 앱, Flipper 플러그인, Reactotron 플러그인. ([출처](https://tanstack.com/query/latest/docs/framework/react/react-native))

### 공식 가이드라인(권장 방식) — 낙관적 업데이트 (좋아요에 적용)
- 방식 1 UI 변수: `useMutation`의 `variables`/`isPending`으로 임시 항목 렌더, 롤백 로직 불필요, 한 곳에서만 보일 때 적합. 방식 2 캐시: `setQueryData`로 캐시를 직접 수정, 여러 컴포넌트가 공유할 때 적합. ([출처](https://tanstack.com/query/latest/docs/framework/react/guides/optimistic-updates))
- 캐시 방식 절차: `onMutate`에서 `cancelQueries` → 스냅샷 저장 → 낙관 적용 → 스냅샷 반환; `onError`에서 복원; `onSettled`에서 `invalidateQueries`. `cancelQueries`가 없으면 진행 중 refetch가 낙관 값을 덮어쓸 수 있다. ([출처](https://tanstack.com/query/latest/docs/framework/react/guides/optimistic-updates))

### 클라이언트 상태 (Zustand / Redux Toolkit)
- 이번 조사에서 Zustand/RTK 공식 페이지는 직접 확인하지 않았다. **[미검증]** 일반 원칙(서버 상태는 TanStack Query, 인증/UI 같은 소규모 클라이언트 상태만 Zustand/RTK)으로 설계하되 도입 시 공식 문서 확인.
- RN 보안 문서는 민감 정보를 전체 Redux state에 persist하는 것을 "의도치 않은 노출" 예로 경고한다. ([출처](https://reactnative.dev/docs/security))

### 자주 하는 실수·놓치기 쉬운 점
- NetInfo/`onlineManager` 미연결 → 오프라인 복귀 시 자동 refetch/재개되지 않음. ([출처](https://tanstack.com/query/latest/docs/framework/react/react-native))
- 탭 내비게이션에서 비활성 화면의 쿼리가 계속 구독·리렌더. `subscribed` 옵션 활용. ([출처](https://tanstack.com/query/latest/docs/framework/react/react-native))
- 위 `useFocusEffect`/`useIsFocused` 예제는 React Navigation 기반. SDK 56 Expo Router의 해당 hook 제공 위치는 마이그레이션 문서로 확인. ([출처](https://expo.dev/changelog/sdk-56.md))

### 체크리스트
- [ ] `onlineManager` + NetInfo, `focusManager` + AppState를 앱 루트에서 1회 설정
- [ ] 좋아요/사인은 캐시 기반 낙관적 업데이트 + 롤백 + `onSettled` 무효화
- [ ] 서버 데이터를 Zustand/Redux에 중복 보관하지 않기

---

## 6. TypeScript 설정

### 핵심 개념 / 가이드라인
- `tsconfig.json`은 `"extends": "expo/tsconfig.base"`; `npx expo customize tsconfig.json`으로 생성. 기본값은 느슨하므로 `compilerOptions.strict: true` 권장. ([출처](https://docs.expo.dev/guides/typescript/))
- path alias(`baseUrl`, `paths`, 예: `@/*` → `src/*`)는 기본 활성이며 Metro 전용, 변경 후 Expo CLI 재시작. ([출처](https://docs.expo.dev/guides/typescript/))
- Typed routes로 존재하지 않는 링크를 컴파일 타임에 차단. ([출처](https://docs.expo.dev/router/introduction/))
- SDK 56은 TypeScript 6.0.3으로 상향. ([출처](https://expo.dev/changelog/sdk-56.md))

### 실수 / 체크리스트
- alias 변경 후 캐시/재시작 누락. ([출처](https://docs.expo.dev/guides/typescript/))
- [ ] strict 활성, typed routes 활성, API 응답 타입(Zod 등 런타임 검증은 **[미검증]** 팀 선택 사항)

---

## 7. 내비게이션과 딥링크 (Universal Links / App Links)

### 핵심 개념
- Expo Router는 모든 화면이 자동 deep link 가능. ([출처](https://docs.expo.dev/router/introduction/))
- iOS Universal Links: `ios.associatedDomains`에 `applinks:<domain>`(프로토콜 `https://` 포함 금지 — 흔한 실수). AASA 파일은 `public/.well-known/apple-app-site-association`, HTTPS 서빙, 압축 해제 기준 128KB 이하, 앱 ID는 `<TEAM_ID>.<BUNDLE_ID>`. iOS 13+ `components` 형식과 구형 형식을 함께 넣을 때 새 형식을 앞에. ([출처](https://docs.expo.dev/linking/ios-universal-links/))
- AASA는 앱 설치/업데이트 시 가져오며 자주 갱신하지 않는다 → 프로덕션 경로 변경은 새 App Store 릴리스가 필요. ([출처](https://docs.expo.dev/linking/ios-universal-links/))
- Android App Links: `android.intentFilters`에 `autoVerify: true` 필수, `public/.well-known/assetlinks.json`(relation `delegate_permission/common.handle_all_urls`, `package_name`, `sha256_cert_fingerprints`), HTTPS + `application/json`. EAS는 `eas credentials -p android`로 지문 확인, Play App Signing을 쓰면 Play Console의 지문도 포함 필요. ([출처](https://docs.expo.dev/linking/android-app-links/))

### 보안 가이드라인
- 커스텀 scheme(`app://`)은 다른 앱이 같은 scheme을 등록해 가로챌 수 있어 안전하지 않다. 토큰 등 민감 정보를 deep link에 싣지 말 것. 보안 링크는 universal link. ([출처](https://reactnative.dev/docs/security))
- OAuth는 PKCE 사용(`react-native-app-auth` 등). ([출처](https://reactnative.dev/docs/security))

### 자주 하는 실수·놓치기 쉬운 점
- 푸시 알림 탭 → 특정 게시물로 이동하는 라우팅을 경로 설계 없이 구현. 알림 payload의 id → route 매핑을 deep link 규칙과 통일(9절).
- `associatedDomains`에 `https://` 붙임, assetlinks 지문을 Play 서명 키와 불일치, 웹 파일 수정 후 네이티브 재빌드 누락. ([출처1](https://docs.expo.dev/linking/ios-universal-links/), [출처2](https://docs.expo.dev/linking/android-app-links/))
- Android 검증은 20초 이상 걸릴 수 있다. ([출처](https://docs.expo.dev/linking/android-app-links/))

### 체크리스트
- [ ] 도메인 확정 후 AASA/assetlinks 배포, 브라우저에서 접근 확인
- [ ] 링크 경로 변경 가능성 최소화(iOS는 스토어 릴리스 필요)
- [ ] deep link에 토큰/민감 정보 금지

---

## 8. 플랫폼별 코드 및 iOS/Android 차이

### 핵심 개념 / 가이드라인
- `Platform.OS`, `Platform.select({ios, android, native, default})`(조회 순서: 플랫폼 → native → default). 큰 차이는 `.ios.tsx` / `.android.tsx` 파일 분리, 웹과 공유 시 `.native.tsx`. ([출처](https://reactnative.dev/docs/platform-specific-code))
- `Platform.Version`: Android는 API level 숫자, iOS는 문자열(`"10.3"`)이라 파싱 필요. ([출처](https://reactnative.dev/docs/platform-specific-code))
- **그림자**: `shadowOffset/Opacity/Radius`는 iOS 전용; `shadowColor`는 양쪽(Android는 API 28+). 구버전 Android는 `elevation`. `boxShadow`는 양 플랫폼 지원하는 CSS 호환 스타일 prop. ([출처](https://reactnative.dev/docs/shadow-props))
- **KeyboardAvoidingView**: `behavior`(`height`/`position`/`padding`)에 따라 iOS·Android 동작이 다르므로 양쪽 모두 설정 권장; `keyboardVerticalOffset` 기본 0. 흔히 iOS `padding` / Android `height` 패턴을 쓰나 이는 문서가 아니라 관행이다. **[미검증]** (문서는 플랫폼별 값을 명시하지 않음) ([출처](https://reactnative.dev/docs/keyboardavoidingview))
- **BackHandler(Android 전용)**: 나중에 등록한 리스너가 먼저 호출, `true` 반환 시 전파·기본 동작 중단, 구독은 cleanup에서 `.remove()`. `Modal`이 열려 있으면 이벤트가 오지 않으므로 `onRequestClose` 사용. ([출처](https://reactnative.dev/docs/backhandler))
- **Safe area**: `react-native-safe-area-context`의 `SafeAreaProvider`를 루트에(Expo Router는 자동 구성 **[미검증]**), `useSafeAreaInsets()`로 inset 적용. 라이브러리의 `SafeAreaView`는 네이티브 구현으로 회전 시 비동기 지연이 없어 가능하면 권장되고, hook은 회전 시 성능이 더 불리할 수 있다. 웹에서는 inset이 0. ([출처](https://docs.expo.dev/versions/latest/sdk/safe-area-context/))
- **HTTP**: iOS ATS는 기본 HTTPS 요구(도메인 단위 예외 권장, 전체 `NSAllowsArbitraryLoads`는 심사 시 정당화 필요), Android API 28+는 cleartext 차단. ([출처](https://reactnative.dev/docs/network))

### 자주 하는 실수·놓치기 쉬운 점
- 그림자를 iOS 스타일만 쓰고 Android에서 안 보임 → `elevation`/`boxShadow` 병행. ([출처](https://reactnative.dev/docs/shadow-props))
- `removeClippedSubviews`가 iOS에서 콘텐츠 누락. ([출처](https://reactnative.dev/docs/optimizing-flatlist-configuration))
- 안전 영역을 RN 기본 `SafeAreaView` 대신 `react-native-safe-area-context`로 통일했는지 확인(기본 컴포넌트의 deprecated 여부는 이번 조사에서 확인하지 못함 **[미검증]**).
- 모달 안에서 BackHandler가 안 먹힘. ([출처](https://reactnative.dev/docs/backhandler))
- `Platform.select`에 `default` 누락 시 웹/기타에서 undefined.

### 체크리스트
- [ ] 입력 화면은 iOS/Android 실기기에서 키보드 동작 확인(`keyboardVerticalOffset`은 헤더 높이 기준)
- [ ] Android 뒤로가기: 글 작성 중 이탈 확인 등 커스텀 처리
- [ ] 노치/제스처 바 inset 적용, 그림자 양 플랫폼 확인

---

## 9. 이미지 처리 (expo-image, expo-image-picker, 업로드)

### 핵심 개념
- **expo-image**: `cachePolicy`(`disk` 기본, `none`, `memory`, `memory-disk`), `placeholder`(BlurHash/ThumbHash, 서버에서 `blurhash` 패키지로 생성), `contentFit`(기본 `cover`), `recyclingKey`, `transition`, 정적 `prefetch`. 기본 RN `Image`의 `defaultSource/fadeDuration/loadingIndicatorSource/resizeMode`는 deprecated이고 AVIF·애니메이션 이미지를 지원. `placeholderContentFit` 기본값 `scale-down`이라 `contentFit`과 맞추지 않으면 깜빡임. ([출처](https://docs.expo.dev/versions/latest/sdk/image/))
- **expo-image-picker**: 이미지 라이브러리 실행에는 권한 요청이 필요 없다(예제는 요청하지만). 카메라는 항상 권한 필요(Android `CAMERA`, iOS `NSCameraUsageDescription`). `mediaTypes` 기본 `'images'`, `allowsEditing`(다중 선택과 배타), `selectionLimit`, `quality` 0~1(iOS에서 png/bmp는 무시, 이미 압축된 이미지는 오히려 커질 수 있음), `exif`(iOS 카메라 촬영 사진엔 GPS 태그 없음). ([출처](https://docs.expo.dev/versions/latest/sdk/imagepicker/))
- iOS HEIC: SDK 54부터 기본 `Passthrough`는 원본(HEIC/AVIF)을 압축 없이 반환 → 서버/AI 리뷰 파이프라인이 HEIC를 처리할 수 있는지 확인 필요. 크롭을 거치면 JPG로 나오는 예시가 있다. ([출처](https://docs.expo.dev/versions/latest/sdk/imagepicker/))
- Android 액티비티 파괴: 피커 종료 후 시스템이 `MainActivity`를 죽여 선택 결과를 잃을 수 있으며 `getPendingResultAsync()`로 복구. 개발자 옵션 "Don't keep activities"로 테스트. ([출처](https://docs.expo.dev/versions/latest/sdk/imagepicker/))

### 업로드
- RN `fetch`는 에러를 catch하지 않으면 조용히 버려진다고 경고한다. ([출처](https://reactnative.dev/docs/network))
- `expo-file-system` 새 API: `file.upload(url, options)`(non-2xx 응답도 resolve, 읽기 실패/네트워크 실패/취소 시에만 reject), `createUploadTask`(상태·취소·진행률), 옵션 `uploadType`(기본 `BINARY_CONTENT`, `MULTIPART`), `fieldName`(기본 `'file'`), `signal`(AbortSignal), `onProgress`(multipart는 framing 오버헤드 포함). 기존 `FileSystem.uploadAsync`는 deprecated이며 `expo-file-system/legacy`에서만 import 가능. ([출처](https://docs.expo.dev/versions/latest/sdk/filesystem/))
- iOS `sessionType` 기본 `'background'`(앱이 중단돼도 전송 지속 가능)이나, 앱이 종료/재시작되면 JS `UploadTask`의 promise/progress/cancel은 복구되지 않는다. ([출처](https://docs.expo.dev/versions/latest/sdk/filesystem/))
- `expo/fetch`에서 `File`을 body로 직접 넘기거나 `FormData`에 append 가능. SDK 56에서는 `expo/fetch`가 전역 `fetch` 기본. ([출처1](https://docs.expo.dev/versions/latest/sdk/filesystem/), [출처2](https://expo.dev/changelog/sdk-56.md))
- 전통적인 RN FormData 파일 객체 `{uri, name, type}` 및 "`Content-Type`을 수동 지정하지 말 것(boundary 자동 설정)" 관행은 공식 RN 문서 페이지에서 확인되지 않았다. **[미검증]** 일반 관행으로 취급하고 실제 사용 HTTP 클라이언트(`expo/fetch` 등) 동작을 테스트로 확인.

### 자주 하는 실수·놓치기 쉬운 점
- 피커의 `quality`를 PNG/HEIC에 기대하고 용량이 안 줄어듦 — 업로드 전 리사이즈/압축 단계(예: expo-image-manipulator **[미검증]**) 설계.
- `file.upload`가 4xx/5xx에서도 resolve하므로 status를 직접 검사해야 한다. ([출처](https://docs.expo.dev/versions/latest/sdk/filesystem/))
- 리스트 이미지에 `recyclingKey` 누락 시 스크롤 중 이전 이미지 잔상. ([출처](https://docs.expo.dev/versions/latest/sdk/image/))
- EXIF의 위치정보 유출: `exif: true` 사용 시 원본 메타데이터가 서버로 갈 수 있음(서버/클라 정책 결정 필요). ([출처](https://docs.expo.dev/versions/latest/sdk/imagepicker/))
- `prefetch`는 하나라도 실패하면 즉시 `false`. ([출처](https://docs.expo.dev/versions/latest/sdk/image/))

### 체크리스트
- [ ] 피커는 권한 없는 시스템 피커 사용, 카메라는 사용 목적 문구 작성
- [ ] HEIC/대용량 대응(포맷·리사이즈·압축), EXIF 정책
- [ ] 업로드: 응답 status 검사, 취소(AbortSignal), 진행률 UI, 앱 종료 시 재개 불가 전제
- [ ] `getPendingResultAsync` 처리, 목록은 expo-image + 썸네일 + `recyclingKey`

---

## 10. 푸시 알림 (expo-notifications / FCM / APNs)

### 핵심 개념
- Expo Push Service가 FCM(Android)·APNs(iOS) 전송을 대행해 양 플랫폼을 동일하게 다룬다. ([출처](https://docs.expo.dev/push-notifications/overview/))
- `getExpoPushTokenAsync`는 Expo 서버를 호출하므로 오프라인에서 실패 가능(try/catch + 재시도); `getDevicePushTokenAsync`는 네이티브 FCM/APNs 토큰(직접 연동용). 토큰 교체는 `addPushTokenListener`로 재등록. `projectId`는 `extra.eas.projectId`에서 읽으며 명시 권장. ([출처](https://docs.expo.dev/versions/latest/sdk/notifications/))
- Expo 푸시 토큰에는 푸시 자격증명(credentials) 설정이 필요. ([출처](https://docs.expo.dev/versions/latest/sdk/notifications/))

### 공식 가이드라인(권장 방식)
- Android 8+는 모든 알림이 channel에 속해야 하며(없으면 "Miscellaneous" fallback), channel 설정은 생성 후 name/description만 변경 가능. Android 13+ 권한 프롬프트는 channel이 최소 하나 생성된 뒤에야 뜨므로 **토큰 요청 전에 `setNotificationChannelAsync`** 호출. ([출처](https://docs.expo.dev/versions/latest/sdk/notifications/))
- iOS 권한 상태는 루트 `status`가 아닌 `ios.status`(NOT_DETERMINED/DENIED/AUTHORIZED/PROVISIONAL/EPHEMERAL)를 확인. ([출처](https://docs.expo.dev/versions/latest/sdk/notifications/))
- 포그라운드 표시는 `setNotificationHandler`가 결정(3초 내 응답 필요, 없으면 배너·소리·배지 표시). 탭 처리는 `addNotificationResponseReceivedListener` 또는 `useLastNotificationResponse`, 수신은 `addNotificationReceivedListener`. ([출처](https://docs.expo.dev/versions/latest/sdk/notifications/))
- 헤드리스(앱 종료 상태) JS 실행은 `registerTaskAsync`, iOS는 `contentAvailable` data-only push + `remote-notification` background mode 필요. ([출처](https://docs.expo.dev/versions/latest/sdk/notifications/))
- 실기기, Google Play 서비스가 있는 Android 에뮬레이터, Xcode 14+(macOS 13+, iOS 16+) 시뮬레이터에서 테스트 가능. SDK 53부터 Android Expo Go에서는 원격 푸시 불가 → development build 필수(로컬 알림은 가능). ([출처](https://docs.expo.dev/versions/latest/sdk/notifications/))

### 자주 하는 실수·놓치기 쉬운 점
- 앱 시작마다 토큰을 서버에 등록하지 않거나 로그아웃/계정 전환 시 토큰을 해제하지 않음 → 다른 사용자 알림 수신(서버 설계는 다른 문서 참조).
- 알림 탭 시 deep link 처리를 콜드 스타트에서 누락 → `useLastNotificationResponse` 사용. ([출처](https://docs.expo.dev/versions/latest/sdk/notifications/))
- Android 13+에서 channel 생성 전 권한 요청. ([출처](https://docs.expo.dev/versions/latest/sdk/notifications/))
- `addNotificationsDroppedListener`: Android에서 FCM이 메시지를 드롭하는 경우 감지. ([출처](https://docs.expo.dev/versions/latest/sdk/notifications/))
- Expo Push Service 대신 FCM/APNs 직접 연동 선택은 자격증명 관리 책임과 토큰 종류(`getDevicePushTokenAsync`)가 달라진다. ([출처](https://docs.expo.dev/versions/latest/sdk/notifications/))

### 체크리스트
- [ ] Android channel 생성 → 권한 요청 → 토큰 발급 순서
- [ ] iOS/Android 자격증명(APNs 키, FCM) 설정, dev build로 검증
- [ ] 알림 탭 → 화면 이동(콜드/웜/포그라운드 3경로) 테스트
- [ ] 토큰 갱신·로그아웃 시 해제 흐름

---

## 11. 보안 저장소, 권한, 환경변수·비밀, 난독화

### 저장소
- AsyncStorage는 **암호화되지 않으며** 토큰/비밀 저장에 쓰지 말 것. 비민감 데이터(persist된 Redux/GraphQL 상태, 앱 설정 등)용. ([출처](https://reactnative.dev/docs/security))
- `expo-secure-store`: iOS는 Keychain(`kSecClassGenericPassword`, `keychainAccessible` 기본 `WHEN_UNLOCKED`), Android는 Keystore로 암호화한 SharedPreferences. 값 크기 제한은 강제하지 않으나 일부 iOS에서 약 2048바이트 초과를 거부한 사례가 있어 에러 처리 필요. ([출처](https://docs.expo.dev/versions/latest/sdk/securestore/))
- iOS는 같은 bundle ID로 재설치 시 데이터가 남을 수 있으나 "구현 세부사항, 보장 아님". Android는 삭제 시 사라진다. ([출처](https://docs.expo.dev/versions/latest/sdk/securestore/))
- `requireAuthentication`: 생체 등록이 바뀌면 키가 무효화되어 값을 읽을 수 없음, Expo Go 미지원(실기기 필요), 실패는 throw → try/catch. Android Auto Backup으로 복원된 항목은 복호화 불가이므로 백업 제외 필요(기본 설정은 자동 처리, 커스텀 백업 규칙 시 `SecureStore` 경로 제외 + `configureAndroidBackup: false`). ([출처](https://docs.expo.dev/versions/latest/sdk/securestore/))
- 로깅/모니터링(Sentry, Crashlytics)에 토큰·개인정보를 보내는 것도 노출 경로. ([출처](https://reactnative.dev/docs/security))

### 환경변수와 비밀
- `EXPO_PUBLIC_` 접두사 변수는 JS 번들에 인라인되어 평문으로 노출된다. 개인 키 등 민감 정보를 넣지 말 것. 반드시 dot notation(`process.env.EXPO_PUBLIC_X`)이어야 인라인되고 bracket/destructuring은 안 된다. ([출처](https://docs.expo.dev/guides/environment-variables/))
- `.env` 우선순위(개발: `.env.development.local` > `.env.local` > `.env.development` > `.env`; 프로덕션 유사), `.env*.local`은 gitignore. 최신 문서 기준 SDK 58+에서는 `.env.staging` 등은 자동 로드되지 않는다(사용 SDK 버전별 확인). ([출처](https://docs.expo.dev/guides/environment-variables/))
- 비밀이 필요한 외부 리소스 호출은 서버리스 함수 등 중계 계층에서 키를 붙여 전달. ([출처](https://reactnative.dev/docs/security))
- EAS Build는 `eas.json` build profile과 EAS 환경변수/Secrets로 변수를 주입할 수 있으나, 클라이언트로 인라인되면 결국 번들에 포함되어 노출된다. EAS 변수의 가시성(visibility) 규칙은 별도 문서 확인 필요. ([출처](https://docs.expo.dev/guides/environment-variables/))

### 난독화의 현실
- Hermes bytecode와 minification은 번들 가독성을 낮출 뿐이다. RN 보안 문서는 "번들을 검사하면 API 키를 평문으로 볼 수 있다"고 경고하며, 보안 경계를 클라이언트 코드에 두지 말고 요청하지 않은 정보는 애초에 받지 않는 것이 더 안전하다고 한다. ([출처](https://reactnative.dev/docs/security))
- bytecode가 사실상 역분석 불가라는 주장이나 별도 난독화 도구(예: 상용 보호 솔루션)의 효과는 이번 조사에서 공식 근거를 찾지 못했다. **[미검증]**
- SSL pinning은 MITM 방어에 유효하지만 인증서 만료(1~2년) 시 구버전 앱이 중단되는 운영 비용이 있다. ([출처](https://reactnative.dev/docs/security))

### 권한(Permissions)
- 민감 권한은 런타임 요청 + 빌드 시 네이티브 설정(standalone/dev build). Android는 `android.permissions`, 라이브러리가 추가하는 권한을 `android.blockedPermissions`로 제거. iOS는 `ios.infoPlist`의 `NS*UsageDescription` 문구를 앱에 맞게 작성(모호/누락 시 심사 반려·스토어 퇴출 가능). Info.plist 변경은 OTA 불가, 새 네이티브 빌드 필요. 거부 후엔 재프롬프트가 안 뜨므로 재설치로 재테스트. ([출처](https://docs.expo.dev/guides/permissions/))

### 체크리스트
- [ ] 액세스/리프레시 토큰은 SecureStore, AsyncStorage에는 비민감 캐시만
- [ ] 클라이언트 번들에 비밀키 없음(CI에서 `EXPO_PUBLIC_*` 목록 리뷰), AI 리뷰 API 키 등은 서버에서만
- [ ] 불필요한 Android 권한 blockedPermissions로 제거, 사용 목적 문구 정비
- [ ] 오류 리포트에 토큰/PII 마스킹

---

## 12. EAS Build / EAS Update와 OTA 정책

### 핵심 개념
- EAS Build: `eas.json` 프로필, development build, `--auto-submit`(EAS Submit), 인증서/keystore 관리, `"distribution": "internal"`(Android APK / iOS ad hoc). ([출처](https://docs.expo.dev/build/introduction/))
- EAS Update로 OTA 가능한 범위: JS, 스타일, 이미지 등 비네이티브 부분(버그 수정, 문구, UI 변경). 네이티브 코드/의존성, 권한, Expo SDK 업그레이드 등 새 바이너리가 필요한 변경은 새 빌드. 업데이트는 `runtimeVersion` 정책으로 호환 빌드에만 전달. ([출처](https://docs.expo.dev/eas-update/introduction/))
- 퍼센트 롤아웃(`--rollout-percentage`), 브랜치 롤아웃(`eas channel:rollout`), 롤백(`eas update:rollback`, `eas update:roll-back-to-embedded`). 브랜치당/플랫폼/runtime version당 롤아웃은 하나씩. ([출처](https://docs.expo.dev/eas-update/rollouts/))

### 스토어 정책 제약
- Expo 문서: 업데이트는 App Store·Play 정책(콘텐츠와 사용 방식 포함)을 따라야 하며, 앱 동작 변경은 보통 심사가 필요하고 정책은 수시로 바뀌므로 정기 확인. ([출처](https://docs.expo.dev/eas-update/introduction/))
- Apple 가이드라인 2.5.2(확인된 원문): 앱은 번들 내에서 자기완결적이어야 하며 "앱의 기능이나 동작을 도입하거나 변경하는 코드를 다운로드·설치·실행해서는 안 된다". (이번에 가져온 텍스트 범위에서 해석 예외 조항이나 3.3.2는 확인되지 않음) 따라서 OTA를 심사 우회용 신기능 배포에 쓰면 정책 위반 위험. ([출처](https://developer.apple.com/app-store/review/guidelines/))
- Google Play 정책의 OTA 관련 조항은 이번 조사에서 확인하지 못했다. **[미검증]**
- Info.plist/권한/네이티브 설정 변경은 OTA로 불가. ([출처](https://docs.expo.dev/guides/permissions/))

### 자주 하는 실수·놓치기 쉬운 점
- `react-native`/네이티브 의존성 버전을 바꾸고 `runtimeVersion`을 안 올려 호환되지 않는 OTA가 배포되어 크래시. bytecode 호환성 경고 참조. ([출처](https://docs.expo.dev/guides/using-hermes/))
- OTA로 네이티브 의존 코드를 포함한 JS를 올림(해당 네이티브 모듈이 설치된 바이너리에만 안전).
- 롤아웃 중 `eas update --channel` 사용 불가(브랜치 지정 필요). ([출처](https://docs.expo.dev/eas-update/rollouts/))

### 체크리스트
- [ ] runtimeVersion 정책 선택(fingerprint 등은 **[미검증]**) 및 네이티브 변경 시 새 빌드 규칙 문서화
- [ ] 프로덕션 OTA는 퍼센트 롤아웃 + 롤백 절차 준비
- [ ] 기능 추가/동작 변경은 스토어 심사 대상으로 취급, OTA는 버그 수정·문구 위주

---

## 13. 에러 경계와 전역 에러 처리

### 핵심 개념 / 가이드라인
- Expo Router `ErrorBoundary` export로 route 단위 렌더 에러 처리(`retry` 제공), layout 레벨 boundary는 헤더/탭바를 유지하며 화면만 대체. 화면의 boundary가 layout 설정보다 우선. ([출처](https://docs.expo.dev/router/error-handling/))
- 비동기 에러(`fetch` reject)는 ErrorBoundary가 잡지 않으므로 직접 catch. RN 문서는 `fetch` 오류를 잡지 않으면 조용히 버려진다고 경고. ([출처](https://reactnative.dev/docs/network))
- 전역 JS 에러 핸들러(`ErrorUtils.setGlobalHandler`)나 크래시 리포터(Sentry 등) 연동은 이번 조사에서 공식 문서를 확인하지 못했다. **[미검증]** 도입 시 Sentry for Expo 공식 문서 확인. 리포터에 민감 정보 전송 주의. ([출처](https://reactnative.dev/docs/security))

### 체크리스트
- [ ] 루트 및 주요 화면 ErrorBoundary + 재시도 UI
- [ ] mutation/query `onError` 공통 처리(토스트, 로그)
- [ ] 크래시 리포팅 설정, source map 업로드(Hermes bytecode 번들은 source map 필요: [출처](https://docs.expo.dev/guides/using-hermes/))

---

## 14. 테스트

### 핵심 개념 / 가이드라인
- RN 공식 권장: Jest(기본 템플릿 preset 포함) + React Native Testing Library(사용자 관점: 보이는 텍스트/접근성 쿼리 우선, props/state/`testID` 단언 지양) + E2E(Detox, Appium, Maestro). ([출처](https://reactnative.dev/docs/testing-overview))
- 컴포넌트 테스트는 Node에서만 돌고 네이티브 코드는 검증하지 못한다. E2E는 release 빌드로 실기기/시뮬레이터에서, 인증·핵심 기능·결제 같은 핵심 플로우만 커버. snapshot은 작게, `--updateSnapshot` 일괄 갱신 금지. React Test Renderer는 deprecated. ([출처](https://reactnative.dev/docs/testing-overview))
- 정적 분석(ESLint, TypeScript) → 단위 → 컴포넌트/통합 → E2E 순으로 속도·신뢰도 트레이드오프. ([출처](https://reactnative.dev/docs/testing-overview))

### 찰캌에서의 적용
- 푸시/딥링크/이미지 선택/권한은 JS 테스트로 검증 불가 → Maestro/Detox E2E 또는 실기기 수동 체크리스트.
- 접근성 쿼리를 쓰면 접근성 prop 정비와 테스트가 함께 개선된다(15절).

### 체크리스트
- [ ] CI: tsc + ESLint + Jest/RNTL
- [ ] E2E 시나리오: 로그인 → 사진 선택·업로드 → 좋아요 → 푸시 탭 이동
- [ ] 외부 의존성(네트워크)은 mock, 도메인 로직은 실제 객체

---

## 15. 접근성

### 핵심 개념 / 가이드라인
- `accessible`(보조기술이 인식), `accessibilityLabel`(없으면 touchable의 Text 자식을 이어 붙임), `accessibilityRole`(`button`은 앱 내 동작/이동, `link`는 외부 URL), `accessibilityState`(`disabled/selected/busy/expanded/checked`), `accessibilityHint`(iOS는 VoiceOver 설정에 따라 읽힘, Android는 항상 읽힘). `role` prop이 `accessibilityRole`보다 우선. ([출처](https://reactnative.dev/docs/accessibility))
- Android `importantForAccessibility="no-hide-descendants"`로 겹친 뷰를 숨김. `AccessibilityInfo`로 스크린리더 활성 여부 확인. VoiceOver는 시뮬레이터에서 사용 불가라 실기기/Accessibility Inspector로 확인. ([출처](https://reactnative.dev/docs/accessibility))

### 찰캌 적용 / 실수
- 아이콘 전용 버튼(좋아요, 사인)에 `accessibilityLabel` 누락, 토글 상태(`accessibilityState={{selected}}`/`checked`) 미표기, 이미지에 대체 설명 없음.
- 체크: [ ] 모든 아이콘 버튼에 label+role, 좋아요 수/상태 변경이 스크린리더에 전달되는지 TalkBack/VoiceOver 확인.

---

## 16. RN 개발자가 흔히 하는 실수 모음 (위 근거 종합)

1. 개발 모드·에뮬레이터로 성능 판단 → release 빌드 측정. ([출처](https://reactnative.dev/docs/performance))
2. 프로덕션에 `console.log` 방치. ([출처](https://reactnative.dev/docs/performance))
3. `EXPO_PUBLIC_*`에 비밀키. ([출처](https://docs.expo.dev/guides/environment-variables/))
4. AsyncStorage에 토큰 저장. ([출처](https://reactnative.dev/docs/security))
5. Expo Go로 푸시/네이티브 모듈 테스트(Android Expo Go 원격 푸시 불가, SecureStore `requireAuthentication` 불가). ([출처1](https://docs.expo.dev/versions/latest/sdk/notifications/), [출처2](https://docs.expo.dev/versions/latest/sdk/securestore/))
6. New Architecture 비호환 라이브러리 채택. ([출처](https://docs.expo.dev/guides/new-architecture/))
7. runtimeVersion 미관리 OTA. ([출처](https://docs.expo.dev/guides/using-hermes/))
8. iOS 그림자 prop만 사용. ([출처](https://reactnative.dev/docs/shadow-props))
9. 리스트 index key, 비메모 renderItem. ([출처](https://reactnative.dev/docs/optimizing-flatlist-configuration))
10. 딥링크에 토큰, 커스텀 scheme만으로 보안 링크 대체. ([출처](https://reactnative.dev/docs/security))
11. 권한 사용 목적 문구 누락/모호. ([출처](https://docs.expo.dev/guides/permissions/))
12. `fetch` 에러 미처리, 업로드 응답 status 미검사. ([출처1](https://reactnative.dev/docs/network), [출처2](https://docs.expo.dev/versions/latest/sdk/filesystem/))

---

## 찰캌 적용 포인트

1. **프로젝트 골격**: Expo(권장 프레임워크) + Expo Router + TypeScript strict + typed routes. SDK 버전은 Reanimated 메모리 회귀(SDK 56)와 expo-router의 React Navigation 분리를 감안해 확정하고, 이 노트의 "latest" 문서 내용을 선택 SDK 문서와 대조. ([출처1](https://reactnative.dev/docs/environment-setup), [출처2](https://expo.dev/changelog/sdk-56.md))
2. **피드(일일 주제 사진 목록)**: FlatList 최적화 prop 또는 FlashList v2 + memo 아이템 + expo-image(썸네일, BlurHash placeholder, `recyclingKey`, `prefetch`). ([출처1](https://reactnative.dev/docs/optimizing-flatlist-configuration), [출처2](https://docs.expo.dev/versions/latest/sdk/image/))
3. **좋아요/사인**: TanStack Query 캐시 기반 낙관적 업데이트(onMutate 스냅샷 → onError 롤백 → onSettled 무효화) + NetInfo `onlineManager` 연결. 좋아요 버튼 애니메이션은 Reanimated 또는 native driver. ([출처1](https://tanstack.com/query/latest/docs/framework/react/guides/optimistic-updates), [출처2](https://tanstack.com/query/latest/docs/framework/react/react-native))
4. **사진 업로드**: `expo-image-picker`(권한 불필요한 시스템 피커, 카메라는 권한+문구), HEIC/EXIF/압축 정책 결정, `expo-file-system`의 `file.upload`(AbortSignal·진행률) 또는 `expo/fetch`+FormData, 응답 status 직접 검사, Android `getPendingResultAsync` 처리. 앱 종료 시 업로드 재개 불가 전제로 서버/큐 설계. AI 사진 리뷰 파이프라인은 HEIC 입력 가능성 고려. ([출처1](https://docs.expo.dev/versions/latest/sdk/imagepicker/), [출처2](https://docs.expo.dev/versions/latest/sdk/filesystem/))
5. **푸시 알림**: expo-notifications, development build 필수, Android channel 선생성 → 권한 → 토큰, 토큰 갱신 리스너, 알림 탭 → deep link 라우팅(콜드 스타트는 `useLastNotificationResponse`). Expo Push Service vs 직접 FCM/APNs는 서버팀과 결정. ([출처](https://docs.expo.dev/versions/latest/sdk/notifications/))
6. **인증 토큰/비밀**: SecureStore에 토큰, AsyncStorage는 비민감 캐시만. AI 리뷰·외부 API 키는 번들에 넣지 않고 서버 경유. 난독화는 보안 경계가 아님. ([출처1](https://docs.expo.dev/versions/latest/sdk/securestore/), [출처2](https://reactnative.dev/docs/security))
7. **딥링크**: 공유 링크용 universal link/App Link 도메인·경로를 초기에 확정(iOS는 경로 변경 시 스토어 릴리스 필요), 링크에 토큰 금지. ([출처1](https://docs.expo.dev/linking/ios-universal-links/), [출처2](https://docs.expo.dev/linking/android-app-links/))
8. **배포/OTA**: EAS Build 프로필(dev/preview/production), OTA는 버그 수정·문구 위주 + 퍼센트 롤아웃 + 롤백, 네이티브 변경은 새 바이너리, runtimeVersion 관리. Apple 2.5.2를 근거로 기능 변경 OTA는 피한다. ([출처1](https://docs.expo.dev/eas-update/introduction/), [출처2](https://developer.apple.com/app-store/review/guidelines/))
9. **플랫폼 차이 QA 목록**: 키보드(글 작성 화면), Android 뒤로가기, 안전 영역, 그림자/elevation, 권한 거부 후 재요청, 접근성(TalkBack/VoiceOver). ([출처](https://reactnative.dev/docs/platform-specific-code))
10. **테스트 전략**: Jest+RNTL(접근성 쿼리) + Maestro/Detox로 업로드·좋아요·푸시 핵심 플로우. ([출처](https://reactnative.dev/docs/testing-overview))
11. **백엔드 확인 필요 쟁점(client 규칙 관점)**: 이미지 포맷(HEIC)·크기 제한·EXIF 처리, 토큰 등록/해제 API 의미, 푸시 payload의 deep link 필드 규격. 정책 문서가 불명확하면 임의로 정하지 말고 백엔드에 확인(CLAUDE.md 지침).

---

## 출처 목록

공식 React Native
- https://reactnative.dev/docs/environment-setup
- https://reactnative.dev/architecture/landing-page
- https://reactnative.dev/docs/the-new-architecture/using-codegen
- https://reactnative.dev/docs/hermes
- https://reactnative.dev/docs/performance
- https://reactnative.dev/docs/optimizing-flatlist-configuration
- https://reactnative.dev/docs/security
- https://reactnative.dev/docs/platform-specific-code
- https://reactnative.dev/docs/network
- https://reactnative.dev/docs/shadow-props
- https://reactnative.dev/docs/keyboardavoidingview
- https://reactnative.dev/docs/backhandler
- https://reactnative.dev/docs/accessibility
- https://reactnative.dev/docs/testing-overview

Expo
- https://docs.expo.dev/router/introduction/
- https://docs.expo.dev/router/error-handling/
- https://docs.expo.dev/guides/new-architecture/
- https://docs.expo.dev/guides/using-hermes/
- https://docs.expo.dev/guides/react-compiler/
- https://docs.expo.dev/guides/typescript/
- https://docs.expo.dev/guides/environment-variables/
- https://docs.expo.dev/guides/permissions/
- https://docs.expo.dev/linking/ios-universal-links/
- https://docs.expo.dev/linking/android-app-links/
- https://docs.expo.dev/build/introduction/
- https://docs.expo.dev/eas-update/introduction/
- https://docs.expo.dev/eas-update/rollouts/
- https://docs.expo.dev/push-notifications/overview/
- https://docs.expo.dev/versions/latest/sdk/notifications/
- https://docs.expo.dev/versions/latest/sdk/securestore/
- https://docs.expo.dev/versions/latest/sdk/imagepicker/
- https://docs.expo.dev/versions/latest/sdk/image/
- https://docs.expo.dev/versions/latest/sdk/filesystem/
- https://docs.expo.dev/versions/latest/sdk/safe-area-context/
- https://expo.dev/changelog/sdk-56.md

기타 공식/준공식
- https://tanstack.com/query/latest/docs/framework/react/react-native
- https://tanstack.com/query/latest/docs/framework/react/guides/optimistic-updates
- https://docs.swmansion.com/react-native-reanimated/docs/fundamentals/glossary/
- https://shopify.github.io/flash-list/docs/
- https://developer.apple.com/app-store/review/guidelines/

미확인(조사 못 함) 항목 요약: Zustand/Redux Toolkit 공식 문서, FlashList 세부 API(`getItemType` 등), RN FormData `{uri,name,type}` 공식 근거, `ErrorUtils` 전역 핸들러, Google Play OTA 정책, Hermes bytecode 역분석 난이도 근거, KeyboardAvoidingView 플랫폼별 권장값(문서에 없음).
