# 사용자 친화 UI/UX, 접근성, 플랫폼 호환

근거: [raw/ios-android-compat-ux.md](../../raw/ios-android-compat-ux.md), 각 플랫폼 raw의 UI 절. HIG 링크는 일부 경로가 바뀌었으므로(예: refresh-content-controls 404) 구현 전에 다시 연다.

## 1. 플랫폼 관례와 공통 동작

정보 구조와 동작은 같게, 외형은 플랫폼 관례를 따른다. 무엇이 같아야 하는지는 [00-principles.md §1](../00-principles.md#1-같아야-하는-것과-달라도-되는-것).

| 요소 | iOS (HIG) | Android (Material 3) | 찰캌 공통 기준 |
| --- | --- | --- | --- |
| 메인 내비 | 하단 Tab bar | 하단 Navigation bar | 탭 구성과 순서는 같다 |
| 일시 피드백 | 토스트·스낵바 관례 없음. 인라인 배너, 상태 변화 | Snackbar(되돌리기 액션 가능) | 공통 `Feedback` 컴포넌트: 의미, 문구, 표시 시간, 되돌리기 유무가 같고 외형만 다르다 |
| 다이얼로그 | Alert, 파괴적 버튼은 빨강 | Dialog | 확인 다이얼로그는 되돌릴 수 없는 동작에만 |
| 시트 | 카드형 sheet, 아래로 스와이프 | Modal bottom sheet, Back으로 닫힘 | 시트가 열린 상태의 뒤로 = 시트 닫기 |
| 웹 | 데스크톱 폭에서도 모바일 폭 중앙 정렬 [제안] | | 마우스·키보드로 모든 기능 사용 가능 |

## 2. 화면 상태

모든 데이터 화면은 아래 상태를 명세하고 다섯 플랫폼이 같은 문구와 전이를 쓴다. UiState 모델은 [architecture-state.md §2](architecture-state.md#2-ui-상태-모델링).

| 상태 | 기준 |
| --- | --- |
| 로딩 | 짧은 요청은 표시 지연 후 표시([상수](networking-errors-retry.md#찰캌-공통-상수)). 목록은 skeleton, 버튼 액션은 버튼 안 진행 표시. 업로드처럼 긴 작업은 진행률 [공식·미열람](https://www.nngroup.com/articles/skeleton-screens/) |
| 빈 상태 | 이유와 다음 행동. 예: 오늘 피드가 비었으면 "오늘의 첫 사진을 올려 보세요" + 작성 버튼 |
| 오류 | 무엇이 왜 실패했고 어떻게 하면 되는지. 서버 코드·HTTP 상태 숫자는 보여 주지 않는다. 사용자 입력(고른 사진, 제목)은 보존한다. 재시도 가능하면 다시 시도 버튼 |
| 오프라인 | 캐시 콘텐츠 + 오프라인 표시. 쓰기 동작은 막고 이유를 알린다([오프라인 범위](offline-optimistic-polling.md#1-오프라인-대응의-범위)) |
| 새로 고침 | 피드 상단 pull-to-refresh. 새로 고침 중에도 기존 콘텐츠는 유지(`isRefreshing` 필드) |
| 다음 페이지 | 하단 로딩 행, 실패 시 "다시 시도" 행, 끝 도달 표시. ID 기준 중복 제거, 스크롤 위치 보존 |

자주 하는 실수: 빈 상태와 로딩 실패를 구분하지 못함, 스피너만 오래 노출, 실패 후 고른 사진이 사라짐, 새로 고침 중 목록이 통째로 사라짐.

## 3. 피드백, 되돌리기, 햅틱, 모션

- **확인 대신 되돌리기**: 자주 하는 가벼운 동작은 확인창 없이 실행하고 필요하면 되돌리기를 준다. 되돌릴 수 없는 동작(게시물 삭제, 회원 탈퇴, 작성 중 이탈)만 확인한다. [공식·미열람](https://www.nngroup.com/articles/confirmation-dialog/)
- **낙관적 반영**: 좋아요는 즉시 반영하고 실패하면 되돌리며 알린다. [offline-optimistic-polling.md §3](offline-optimistic-polling.md#3-낙관적-업데이트)
- **햅틱**: 의미 있는 순간에만(좋아요, 업로드 성공). 시스템 설정을 존중한다. 웹은 없음(허용된 차이).
- **모션**: 짧게, 의미 전달용. "동작 줄이기" 설정(iOS Reduce Motion, Android 애니메이션 배율, 웹 `prefers-reduced-motion`)을 따른다.

## 4. 레이아웃: 안전 영역, edge-to-edge, 키보드

| 항목 | 기준 | 플랫폼 메모 |
| --- | --- | --- |
| 안전 영역 | 콘텐츠는 inset 안쪽, 배경은 가장자리까지. 하단 버튼·탭 바는 항상 inset 적용 | iOS safe area, Flutter `SafeArea`, RN `react-native-safe-area-context`, 웹 `env(safe-area-inset-*)` + `viewport-fit=cover` |
| edge-to-edge | Android 15(targetSdk 35+)에서 강제, targetSdk 36부터 opt-out 불가 [공식](https://developer.android.com/develop/ui/views/layout/edge-to-edge) | 3버튼 내비와 제스처 내비 둘 다 확인 |
| 키보드 | 입력 필드와 제출 버튼이 키보드 위에 보인다 | Android IME insets, Flutter `resizeToAvoidBottomInset`, RN `KeyboardAvoidingView`(플랫폼별 `behavior` 다름), 웹 `visualViewport` |
| 상태 바 | 사진 위에서는 scrim으로 가독성 확보 | |

기존 iOS 앱 규칙: 공통 컴포넌트가 Safe Area·바깥 여백·전체 폭을 결정하지 않고 화면이 정한다. ([client/iOS/CLAUDE.md](../../../client/iOS/CLAUDE.md)) [찰캌규칙]

## 5. 권한 요청

| 권한 | 언제 | 거부 시 |
| --- | --- | --- |
| 사진 보관함 | **요청하지 않는다.** 시스템 피커 사용 | — |
| 카메라 | 촬영 버튼을 누를 때 | 앨범 선택 대안 + 설정 이동 |
| 알림 | 가치를 설명할 수 있는 순간(예: 첫 업로드 후 "승인되면 알려 드릴게요") | 앱 사용 계속. 알림 설정 화면에 기기 알림 꺼짐 안내 |

- 권한 상태는 세 가지로 추상화한다: 미결정, 거부(다시 물을 수 있음), 영구 거부(설정에서만 변경). iOS는 한 번 거부하면 다시 묻지 못한다. Android는 두 번 거부하면 시스템이 다이얼로그를 생략할 수 있다. [공식](https://developer.android.com/training/permissions/requesting)
- 시스템 다이얼로그 전에 앱의 설명 화면을 먼저 보여 줄 수 있다. 다만 같은 사용자에게 반복해서 조르지 않는다.
- Flutter `permission_handler`, RN `Linking.openSettings()`, 웹은 브라우저가 권한 UI를 그린다.

## 6. 날짜·시간대

- **"오늘의 주제" 날짜는 KST 기준이다(TOPIC-002, TOPIC-005).** 기기 시간대로 "오늘"을 계산하지 않는다. 해외 시간대 기기에서도 같은 주제일이 보여야 한다. [찰캌규칙]
- 서버 시각은 ISO 8601(UTC 또는 오프셋 포함)로 받고, 표시만 로케일 포맷터로 한다. `HH:mm` 같은 패턴을 하드코딩하지 않는다.
- KST 날짜 계산 수단: Swift `TimeZone(identifier: "Asia/Seoul")`, Kotlin `ZoneId.of("Asia/Seoul")`, Dart는 KST가 UTC+9 고정(서머타임 없음)이라 `toUtc().add(Duration(hours: 9))`로 충분하다, JS `Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul' })`. [일반지식]
- 자정 직전·직후, 기기 시간대 변경, 기기 시계가 틀린 경우를 테스트한다. 가능하면 "오늘"은 서버 응답(`GET /topics` 등)의 날짜를 쓴다.
- 상대 시간("3분 전")은 다섯 플랫폼이 같은 경계값을 쓴다. 경계값은 기존 iOS·Android 구현에서 가져와 [open-questions](../open-questions.md#팀-결정-대상)에서 확정한다.

## 7. 접근성

| 항목 | 기준 | 근거 |
| --- | --- | --- |
| 터치 영역 | 최소 44pt(iOS) / 48dp(Android). 공통으로 48 단위로 설계. 웹은 WCAG 2.2 AA 최소 24px, 권장 44px | [공식](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html) |
| 대비 | 본문 4.5:1 이상 | [공식](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html) |
| 글자 크기 | iOS Dynamic Type, Android `sp`(Android 14부터 최대 200% 비선형). 최대 크기에서 잘리지 않게 줄바꿈·스크롤 허용, 고정 높이 금지 | [공식](https://developer.android.com/about/versions/14/features) |
| 스크린 리더 | 아이콘 버튼 라벨, 사진 대체 텍스트(주제 + 작성자 등), 좋아요 상태("좋아요, 선택됨"), 읽기 순서 | VoiceOver, TalkBack, Flutter `Semantics`, RN `accessibilityLabel`/`accessibilityState`, 웹 ARIA |
| 색 | 색만으로 상태를 구분하지 않는다 | |
| 웹 키보드 | 모든 기능을 키보드로, 포커스 표시, 모달 포커스 가두기 | |

접근성 라벨 문구는 다섯 플랫폼이 같은 문구 표를 쓴다.

## 8. 프로세스 종료와 상태 복원

- Android는 앱이 백그라운드에 있는 동안 프로세스를 죽일 수 있다. 카메라 앱에 다녀오는 사이에도 그렇다. 작성 중인 상태(고른 사진 경로, 편집 값, 제목)는 `SavedStateHandle`이나 로컬 저장으로 복원한다. [공식](https://developer.android.com/topic/libraries/architecture/saving-states)
- 개발자 옵션 "활동 유지 안 함"으로 테스트한다.
- iOS·Flutter(`RestorationMixin`)·RN·웹(새로 고침)도 같은 수준을 목표로 하되, 복원 범위(작성 중 초안을 앱 재시작 후에도 유지할지)는 기존 앱 동작을 기준으로 맞춘다.
- 피커가 준 임시 URI는 접근 권한이 만료될 수 있다. 앱 저장소로 복사한 뒤 쓴다.

## 체크리스트

- [ ] 화면마다 로딩·빈·오류·오프라인 상태 명세와 프리뷰가 있다
- [ ] 오류 후 사용자 입력이 보존된다
- [ ] 확인 다이얼로그는 되돌릴 수 없는 동작에만 있다
- [ ] Android 15 edge-to-edge, 3버튼·제스처 내비, 키보드 겹침을 확인했다
- [ ] 보관함 권한을 요청하지 않고, 카메라·알림 권한은 맥락 있는 시점에 요청한다
- [ ] 기기 시간대를 바꿔도 오늘의 주제가 같다
- [ ] 최대 글자 크기, VoiceOver/TalkBack(웹은 스크린 리더) 핵심 흐름(로그인 → 업로드 → 좋아요)을 확인했다
- [ ] "활동 유지 안 함"에서 작성 중 상태가 복원된다
