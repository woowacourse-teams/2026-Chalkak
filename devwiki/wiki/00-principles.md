# 기능 동등성 원칙과 공통 어휘

## 1. 같아야 하는 것과 달라도 되는 것

다섯 클라이언트는 "같은 입력과 서버 응답이면 같은 결과"를 내야 한다. 화면의 생김새는 플랫폼 관례를 따른다. [제안]

| 반드시 같아야 하는 것 | 플랫폼별로 달라도 되는 것 |
| --- | --- |
| 화면 흐름과 진입 조건(로그인 게이트, 버전 게이트, 약관, 사인 등록) | 내비게이션 컨트롤 외형(탭 바, 앱 바, 시트 모양) |
| 서버 호출 순서, 요청 값, 재시도·타임아웃 상수 | 컴포넌트 외형(Snackbar vs 인라인 배너, 다이얼로그 스타일) |
| 화면 상태 목록(로딩·빈·오류·오프라인·콘텐츠)과 전이 조건 | 애니메이션, 햅틱 세부, 아이콘 세트 |
| 사용자에게 보이는 문구와 오류 코드별 메시지 | 시스템 권한 다이얼로그(OS가 그림) |
| 낙관적 업데이트와 롤백 규칙 | 뒤로 가기 제스처 방식(스와이프 vs 시스템 Back), 단 결과는 같다 |
| 푸시 탭, 딥링크 진입 시 도착 화면 | 백그라운드 실행 수단(OS API 차이). 단 한계는 문서화한다 |
| 분석 이벤트 이름과 속성 | |
| 접근성 라벨, 최소 터치 영역, 큰 글자 대응 수준 | |

플랫폼 한계 때문에 동작을 맞출 수 없을 때(웹의 백그라운드 업로드, iOS 웹의 푸시 등)는 [open-questions.md](open-questions.md)에 "허용된 차이"로 기록하고 대체 UX를 정한다. 몰래 다르게 구현하지 않는다.

## 2. 근거 우선순위

1. `docs/business-rules/**`(읽기 전용)와 서버 동작이 정책의 원본이다. 규칙이 없거나 서버와 충돌하면 임의로 정하지 않고 백엔드 확인 대상으로 올린다. ([CLAUDE.md](../../CLAUDE.md))
2. 기존 iOS·Android 앱이 이미 정한 구현 결정(예: [Android UI 상태와 내비게이션](../../client/android/docs/architecture/ui-state-navigation.md), [게시물 이미지 인코딩](../../client/android/docs/architecture/post-image-encoding.md))은 새 플랫폼에서도 기준으로 삼는다.
3. 이 wiki의 공통 기준
4. 각 플랫폼 공식 가이드

## 3. 공통 어휘

다섯 플랫폼의 코드 리뷰와 문서에서 아래 이름만 쓴다. 근거: [Android 아키텍처](https://developer.android.com/topic/architecture) [공식], [Flutter 아키텍처 가이드](https://docs.flutter.dev/app-architecture/guide) [공식], [Thinking in React](https://react.dev/learn/thinking-in-react) [공식]. iOS·RN 열의 구체 타입은 [제안]이다.

| 공통 어휘 | 역할 | iOS (SwiftUI) | Android (Compose) | Flutter | React Native / Web |
| --- | --- | --- | --- | --- | --- |
| Screen | 화면 단위 컨테이너. ViewModel을 아는 유일한 UI | `XxxScreen: View` | `XxxRoute` + `XxxScreen` | `XxxScreen` 위젯 | `XxxScreen` 컴포넌트 |
| Component | 상태를 파라미터로 받고 이벤트를 콜백으로 올리는 표현 UI | `View` struct | stateless `@Composable` | `StatelessWidget` | 함수 컴포넌트 |
| ViewModel | 화면 상태 보유, 사용자 이벤트 처리 | `@Observable @MainActor final class` | `ViewModel` + `StateFlow` | `ChangeNotifier` + Command | custom hook(`useXxxViewModel`) 또는 store |
| UiState | 화면에 보이는 것의 불변 표현 | `struct` + `enum` | `data class` / `sealed interface` | `sealed class` | discriminated union |
| Repository | 데이터 타입별 단일 진실 원천(SSOT). 인터페이스로 노출 | `protocol` | `interface` | `abstract class` | TS `interface` |
| Service (DataSource) | API, DB, 플랫폼 API를 감싸는 상태 없는 계층 | `URLSession` 클라이언트 | Retrofit/Room DAO | API client | fetch client |
| UseCase | 선택. 여러 Repository 결합, 복잡한 로직, 여러 ViewModel 재사용일 때만 | struct | class | class | 순수 함수 |

UseCase를 기본으로 만들지 않는다. Android와 Flutter 공식 가이드 모두 domain layer를 선택 사항으로 두고 남용을 경고한다. [공식]

## 4. 상수는 한 곳에서

플랫폼마다 숫자가 달라지면 동작이 갈린다. 아래 값은 [common/](README.md#공통-문서) 문서에 단일 표로 두고 다섯 클라이언트가 그대로 쓴다.

- 네트워크 타임아웃, 재시도 횟수·백오프: [networking-errors-retry.md](common/networking-errors-retry.md#찰캌-공통-상수)
- 로딩 표시 지연, 최소 표시 시간: 같은 문서
- AI 리뷰 폴링 간격·창: [offline-optimistic-polling.md](common/offline-optimistic-polling.md#4-ai-리뷰-폴링)
- 이미지 업로드 규격: [media-upload.md](common/media-upload.md#1-찰캌-업로드-규격)
