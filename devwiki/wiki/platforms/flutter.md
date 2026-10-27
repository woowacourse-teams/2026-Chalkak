# Flutter

공통 기준은 [common/](../README.md#공통-문서)에 있다. 이 문서는 Flutter에만 있는 API와 함정만 담는다. 근거: [raw/flutter.md](../../raw/flutter.md). 새로 만드는 클라이언트다.

## 권장 스택

| 영역 | 선택 | 근거·메모 |
| --- | --- | --- |
| 구조 | 공식 아키텍처 가이드: View / ViewModel / Repository / Service, domain은 선택 | [공식](https://docs.flutter.dev/app-architecture/guide) |
| 상태 | `ChangeNotifier` ViewModel + Command 패턴 + `ListenableBuilder` (공식 레시피) | Riverpod·Bloc은 팀 결정. [open-questions](../open-questions.md#팀-결정-대상) |
| 결과 타입 | Service·Repository는 `Result<T>`(sealed) 반환, ViewModel에서 exhaustive `switch` | [공식](https://docs.flutter.dev/app-architecture/design-patterns/result) |
| DI | `provider`로 생성자 주입 | 공식 사례 |
| 라우팅 | `go_router`(redirect로 로그인·게이트) | 현재 maintenance 모드임을 인지 [공식](https://pub.dev/packages/go_router) |
| HTTP | Dio(인터셉터, `CancelToken`) | 토큰 갱신은 `QueuedInterceptor` + 토큰 비교 가드 |
| 푸시 | `firebase_messaging` | |
| 이미지 | `image_picker`, `cached_network_image` | WebP 인코딩 수단은 [media-upload.md §3.1](../common/media-upload.md#31-인코딩) |
| 보안 저장 | `flutter_secure_storage` | |
| 린트 | `flutter_lints` + `use_build_context_synchronously`를 error로 | |

## 아키텍처 함정

- Repository끼리 의존하지 않는다. Service는 상태를 갖지 않는다. 위젯에서 API를 부르지 않는다.
- Command 결과를 처리한 뒤 `clearResult()`를 부르지 않으면 notify마다 스낵바·이동이 반복된다. [공식](https://docs.flutter.dev/app-architecture/design-patterns/command)
- 낙관적 롤백 뒤 오류 플래그를 리셋하지 않으면 메시지가 중복된다.
- `dispose()`에서 리스너, `Timer`, `StreamSubscription`, `CancelToken`을 정리한다. Dart Future에는 취소가 없다.
- 플랫폼 분기(`Platform.isIOS`, `kIsWeb`)를 코드 곳곳에 두지 않고 `Policy`/`Capability` 클래스 한 곳에 모은다. [공식](https://docs.flutter.dev/ui/adaptive-responsive/capabilities)

## BuildContext와 async gap

- `await` 뒤에 `context`를 쓰기 전에 `mounted`(State) 또는 `context.mounted`를 확인한다. 사진 선택·권한 요청·업로드 뒤 `ScaffoldMessenger`, `Navigator` 호출에서 자주 터진다. [공식](https://dart.dev/tools/linter-rules/use_build_context_synchronously)
- 더 나은 방법은 비동기 결과를 ViewModel 상태로 내고 View가 listener로 처리하는 것이다.

## 성능

- `build()`에서 Future를 만들지 않는다(`FutureBuilder(future: api())`는 rebuild마다 호출).
- 피드는 `ListView.builder`/`SliverList`, 아이템에 `ValueKey(postId)`. 변하지 않는 서브트리는 `const`.
- `Opacity`, `ShaderMask`, clip 남용은 `saveLayer`를 유발한다. 둥근 모서리는 `borderRadius`. [공식](https://docs.flutter.dev/perf/best-practices)
- 목록 이미지는 표시 크기로 디코딩(`cacheWidth`, `memCacheWidth`).
- 16ms를 넘는 작업(큰 JSON, 이미지 인코딩)은 `compute`/`Isolate.run`. 백그라운드 isolate에서 플러그인을 쓰려면 `RootIsolateToken`. [공식](https://docs.flutter.dev/cookbook/networking/background-parsing)
- 성능은 profile 모드, 실기기에서 DevTools로 본다. iOS는 Impeller만, Android는 API 29+에서 Impeller 기본이고 그 아래는 OpenGL로 떨어지므로 구형 기기 렌더링을 확인한다. [공식](https://docs.flutter.dev/perf/impeller)

## 푸시 (`firebase_messaging`)

- Android 13+·iOS·웹은 `requestPermission` 필요.
- iOS: Firebase 콘솔에 APNs 인증 키(.p8) 업로드, Push Notifications capability + Background Modes(Remote notifications). 다른 FCM 호출 전에 `getAPNSToken()`이 준비됐는지 확인. 이미지 알림은 Notification Service Extension, 시뮬레이터에서 안 보임.
- 포그라운드 `notification` 메시지는 기본으로 표시되지 않는다. Android는 high priority 채널(또는 `flutter_local_notifications`), iOS는 `setForegroundNotificationPresentationOptions`.
- 백그라운드 핸들러는 **top-level 함수 + `@pragma('vm:entry-point')`**. 빠뜨리면 release에서만 동작하지 않는다.
- 탭 처리는 `getInitialMessage()`(종료 상태)와 `onMessageOpenedApp`(백그라운드) 둘 다. [공식](https://firebase.google.com/docs/cloud-messaging/flutter/receive)
- 토큰 API는 SDK 버전에 따라 바뀌는 중이라는 보고가 있다(raw 참고, 미검증). 도입 시 현재 문서로 확인한다.

## 사진

- iOS `Info.plist`에 `NSPhotoLibraryUsageDescription`, `NSCameraUsageDescription`. Android는 13+에서 Photo Picker를 자동 사용.
- Android에서 카메라 다녀오는 사이 앱이 죽을 수 있다. 앱 시작 시 `retrieveLostData()`. [공식](https://pub.dev/packages/image_picker)
- iOS 시뮬레이터는 HEIC 선택이 안 된다. 실기기로 HEIC → WebP 변환을 확인한다.
- 카메라 임시 파일 경로는 사라질 수 있다. 앱 저장소로 복사한다.

## 권한

`permission_handler`로 상태(미결정·거부·영구 거부)를 읽고 `openAppSettings()`. iOS는 Podfile 매크로로 쓰는 권한만 켠다(안 쓰는 권한 코드가 들어가면 심사에서 문제). [공식·미열람](https://pub.dev/packages/permission_handler)

## 빌드·환경

- flavor(dev/prod)로 base URL, Firebase 설정, 앱 ID를 분리한다. 비밀은 `--dart-define`에 넣지 않는다(번들에 포함).
- release: `flutter build <target> --obfuscate --split-debug-info=<dir>`, 심볼 파일 보관. `runtimeType.toString()` 의존 코드 금지. [공식](https://docs.flutter.dev/deployment/obfuscate)
- iOS 3.41+ 기본 `UISceneDelegate` 수명주기에서는 플랫폼 채널을 `didInitializeImplicitFlutterEngine`에서 만든다. [공식](https://docs.flutter.dev/platform-integration/platform-channels)
- 플러그인 선택: pub points, verified publisher(flutter.dev, firebase.google.com 우선), 최근 릴리스, Swift Package Manager 지원. 커스텀 네이티브 연동은 Pigeon.

## 테스트

- unit(ViewModel·Repository + Fake) > widget > `integration_test` 소수. 권한 다이얼로그·알림 같은 네이티브 UI는 `patrol` 또는 Maestro. [공식](https://docs.flutter.dev/testing/overview)
- 시간은 `fakeAsync`로 제어(폴링, 재시도).
- Maestro 공용 식별자는 `Semantics(identifier:)`.
- 접근성: 아이콘 버튼 `Semantics(label:)`/`tooltip`, `MediaQuery.textScaler` 최대값에서 레이아웃 확인.

## 체크리스트

- [ ] View / ViewModel / Repository / Service 분리, Repository 간 의존 없음
- [ ] `use_build_context_synchronously` 경고 0
- [ ] `dispose()`에서 타이머·구독·취소 토큰 정리
- [ ] 백그라운드 메시지 핸들러가 top-level + `vm:entry-point`
- [ ] obfuscate + split-debug-info 빌드로 스모크 테스트
