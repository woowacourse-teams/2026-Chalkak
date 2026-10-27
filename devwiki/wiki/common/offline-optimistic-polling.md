# 오프라인 대응, 낙관적 업데이트, 장기 작업 대기

근거: [raw/network-resilience-offline.md §5, 6, 9](../../raw/network-resilience-offline.md), [AI 리뷰 연동 안내](../../../docs/business-rules/guides/ai-review.md), [좋아요 규칙](../../../docs/business-rules/rules/like.md).

## 1. 오프라인 대응의 범위

찰캌은 "오늘의 주제에 사진 한 장"을 올리고 다른 사람 사진을 보는 서비스다. 완전한 offline-first(로컬 DB가 진실 원천, 모든 쓰기를 큐에 쌓음)는 과하다. 찰캌 기준은 아래와 같다. [제안]

| 데이터·동작 | 오프라인 읽기 | 오프라인 쓰기 | 쓰기 전략 |
| --- | --- | --- | --- |
| 오늘의 주제, 피드 첫 페이지, 내 기록 | 마지막 캐시 표시 + "오프라인" 배너 | — | — |
| 게시물 상세, 이미지 | 이미지 디스크 캐시 | — | — |
| 좋아요 | 캐시 값 | 즉시 반영, 연결되면 최종 상태 1회 전송 | 낙관적(§3) |
| 게시물 작성 | — | 촬영·편집은 가능, 전송은 연결 필요 | 업로드 작업으로 보존([media-upload.md](media-upload.md)) |
| 제목 수정, 삭제, 탈퇴, 사인 교체, AI 리뷰 요청, 피드백 | — | 막고 안내 | online-only |
| 알림함 | 캐시 표시 | 읽음 처리는 연결 시 | online-only |

Android 공식 가이드의 쓰기 전략 분류: online-only(네트워크 먼저), queued(큐에 넣고 연결 시 전송), lazy(로컬 먼저, 충돌 처리 필요). [공식](https://developer.android.com/topic/architecture/data-layer/offline-first)

### 캐시 원칙

- 읽기는 "캐시를 먼저 내보내고 서버 값으로 갱신"(stale-while-revalidate)이다. Flutter 공식 레시피도 이 방식(Stream으로 캐시 → 서버 순서)을 권한다. [공식](https://docs.flutter.dev/app-architecture/design-patterns/offline-first)
- 캐시는 Repository가 소유한다. 화면이 따로 캐시하지 않는다(SSOT).
- 캐시는 로그인 사용자별이다. 로그아웃·탈퇴 시 전부 지운다.
- 오프라인에서 캐시로 보여 줄 때는 "마지막 갱신 시각" 또는 오프라인 배너로 오래된 데이터임을 알린다.
- 주제 날짜(KST)가 바뀌었는데 캐시의 "오늘의 주제"가 어제 것이면 보여 주지 않고 갱신한다.

### 연결 상태는 힌트일 뿐이다

연결 API는 "인터넷에 닿는다"를 보장하지 않는다. UX 힌트(배너)와 재전송 트리거로만 쓰고, 성공 여부는 실제 요청 결과로 판단한다. 연결 상태가 false라고 요청을 막지 않는다.

| 플랫폼 | API | 주의 |
| --- | --- | --- |
| iOS | `NWPathMonitor` | path `satisfied`도 서버 도달 보장 아님 [공식](https://developer.apple.com/documentation/network/nwpathmonitor) |
| Android | `ConnectivityManager` + `NetworkCapabilities` | `NET_CAPABILITY_VALIDATED`까지 확인 [공식](https://developer.android.com/develop/connectivity/network-ops/reading-network-state) |
| Flutter | `connectivity_plus` | 연결 유형만 알려 줌 [공식](https://pub.dev/packages/connectivity_plus) |
| RN | `@react-native-community/netinfo` | `isInternetReachable`이 도달성 [공식·미열람] |
| Web | `navigator.onLine`, `online`/`offline` 이벤트 | MDN이 기능 비활성화 근거로 쓰지 말라고 함 [공식](https://developer.mozilla.org/en-US/docs/Web/API/Navigator/onLine) |

## 2. 오래 걸리는 쓰기의 보존(outbox)

앱이 꺼져도 사라지면 안 되는 쓰기는 메모리가 아니라 디스크에 의도를 저장하고 백그라운드 작업이 비운다. 찰캌에서 이 대상은 **게시물 업로드 하나**다. 좋아요는 실패하면 롤백하면 되므로 디스크 큐까지 두지 않는다. [제안]

- 저장 항목: 작업 ID, 단계(인코딩 완료 / 업로드 발급 / 스토리지 전송 / 생성 요청), 로컬 파일 경로, `uploadId`와 발급 시각, 시도 횟수, 마지막 오류.
- 단계마다 독립 재시도. 앱 재시작 시 미완료 작업을 복구한다.
- 로그아웃 시 남은 작업과 임시 파일을 지운다.

플랫폼별 실행 수단은 [media-upload.md §4](media-upload.md#4-백그라운드-업로드)를 따른다.

## 3. 낙관적 업데이트

"UI를 먼저 바꾸고 백그라운드로 서버와 통신, 실패하면 되돌리고 알린다." 찰캌에서 낙관적으로 처리하는 것은 **좋아요**뿐이다. 게시물 작성은 검수(`VALIDATING → PENDING`)를 거치므로 피드에 미리 끼워 넣지 않는다. 삭제·제목 수정은 서버 확인 후 반영한다. [제안]

### 3.1 좋아요 규칙

좋아요 API는 목표 상태를 보내는 방식이라 멱등이다. `PUT /posts/{postId}/likes`(등록), `DELETE ...`(취소). 중복 등록과 없는 좋아요 취소 모두 성공이고, 응답에 현재 좋아요 수가 온다(LIKE-003, LIKE-004). [찰캌규칙]

```text
onLikeTapped(postId):
  ui.liked = !ui.liked; ui.count ±= 1; ui.pending = true        // 즉시 반영
  desired[postId] = ui.liked
  if inFlight[postId]: return                                    // 연타는 마지막 의도만 기억
  loop:
    target = desired[postId]
    res = target ? PUT : DELETE                                  // 멱등이므로 재시도 안전
    if res ok:
       confirmed[postId] = (target, res.likeCount)
       if desired[postId] == target: ui = confirmed; pending=false; break
       else continue                                             // 그 사이 의도가 바뀜 → 한 번 더
    else:
       ui = confirmed[postId]                                    // 스냅샷이 아니라 서버가 확인한 마지막 상태로
       userMessage = "좋아요를 반영하지 못했어요"; break
```

규칙

- **게시물별 직렬화와 병합(coalescing)**: 연타 10번이 요청 10개가 되지 않는다. 진행 중 요청이 있으면 마지막 의도만 기억했다가 끝난 뒤 다르면 한 번 더 보낸다.
- **개수는 서버 값으로 보정**: ±1은 임시 값이다. 응답의 `likeCount`로 덮는다.
- **같은 게시물이 여러 화면에 있으면 같은 값을 본다**: 피드, 상세, 작성자 이어보기의 좋아요 상태는 Repository 한 곳에서 나온다.
- **실패 시 되돌리고 알린다**: 조용히 되돌리면 사용자는 좋아요가 된 줄 안다. Flutter 공식 레시피도 이를 나쁜 UX로 지적한다. [공식](https://docs.flutter.dev/app-architecture/design-patterns/optimistic-state)
- **대상이 사라진 경우**: 게시물이 삭제·비공개되어 4xx가 오면 롤백 후 목록에서 그 게시물을 갱신한다(LIKE-002, LIKE-005).
- **정지 회원**: 좋아요 등록이 막히고 취소는 허용된다(USER-002). 등록 실패 응답은 [errorCode 표](networking-errors-retry.md#12-찰캌-오류-응답과-errorcode)대로 처리한다.

### 3.2 플랫폼별 도구

| 플랫폼 | 낙관적 상태 | 직렬화 | 참고 |
| --- | --- | --- | --- |
| iOS | `@Observable` Repository/ViewModel 상태 | 게시물별 진행 중 `Task` 보관 | 공식 가이드 없음, 위 패턴 적용 |
| Android | `StateFlow`(또는 Room) 선반영 | 게시물별 `Mutex`·`Job` | |
| Flutter | ViewModel `ChangeNotifier` | in-flight 플래그 + 마지막 의도 | [공식 레시피](https://docs.flutter.dev/app-architecture/design-patterns/optimistic-state) |
| RN·Web | TanStack Query `onMutate` → `setQueryData`, `onError` 복원, `onSettled` 무효화 | `mutationKey`별 `scope`로 직렬화 [일반지식] | `cancelQueries` 누락 시 진행 중 refetch가 낙관 값을 덮음 [공식](https://tanstack.com/query/latest/docs/framework/react/guides/optimistic-updates) |

React `useOptimistic`은 Action이 끝나면 부모 값으로 수렴하고 throw 시 자동 롤백된다. 단 여러 화면이 같은 값을 공유하는 좋아요에는 Query 캐시 방식이 맞다. [공식](https://react.dev/reference/react/useOptimistic)

## 4. AI 리뷰 폴링

[AI 리뷰 연동 안내](../../../docs/business-rules/guides/ai-review.md)가 원본이다. 아래는 구현 요약이다. [찰캌규칙]

| 항목 | 값·동작 |
| --- | --- |
| 진입 | 화면에 들어오면 항상 `GET`부터. 응답 `status`로 분기 |
| 분기 | `NONE` → 요청 버튼, 폴링 안 함 / `PROCESSING` → 분석 중 + 폴링 / `COMPLETED` → 결과, 폴링 중단 / `FAILED` → 실패 안내, `canRequestAgain`이 true일 때만 다시 요청 버튼 |
| 간격 | **3초 고정**(백오프 아님) |
| 시간 창 | **약 5분**. `POST`를 보냈거나 폴링을 시작한 시각부터 잰다. `requestedAt`은 기준이 아니다 |
| 창 초과 | 폴링 중단, "잠시 후 다시 확인해 주세요" 안내. 실패로 표시하지 않는다. `POST`를 다시 보내지 않는다 |
| 화면 이탈 | 폴링 중단 |
| `POST` | 사용자가 누를 때만, 응답 전까지 한 번만. 실패 응답 후 자동 재전송 금지(재요청 횟수를 소모함) |
| 정지 회원 | `POST`가 403. 버튼 숨김 또는 안내. `GET`은 가능 |
| 푸시 | 결과 확정 시 푸시가 올 수 있지만 **폴링 중단 조건으로 쓰지 않는다**. 보고 있는 게시물의 AI 리뷰 푸시는 포그라운드 표시를 생략하기를 권장 |
| 알 수 없는 `status` | 폴링 중단, 일반 오류 표시 [제안] |

추가 기준 [제안]

- 앱이 백그라운드로 가면 폴링을 멈추고, 돌아오면 `GET` 1회 후 이어간다. 시간 창은 처음 시작 시각 기준을 유지한다.
- 폴링 요청 하나가 네트워크 오류로 실패하면 다음 주기에 다시 묻는다. 연속 3회 실패하면 폴링을 멈추고 오류 상태와 다시 시도 버튼을 보여 준다.
- 폴링 응답마다 화면 전체를 다시 그리거나 스피너를 깜빡이지 않는다. 상태가 바뀔 때만 갱신한다.

| 플랫폼 | 루프 | 백그라운드 감지 | 화면 이탈 시 중단 |
| --- | --- | --- | --- |
| iOS | `Task` + `Task.sleep(for: .seconds(3))` | `scenePhase` | `.task {}` 자동 취소 |
| Android | `while (isActive) { delay(3_000) }` | `repeatOnLifecycle(STARTED)` | `viewModelScope` 또는 수명 연동 |
| Flutter | `Timer` 또는 `Future.delayed` 루프 | `AppLifecycleListener` / `WidgetsBindingObserver` | `dispose()`에서 취소 |
| RN | `setTimeout` 루프 또는 TanStack `refetchInterval`(함수형으로 상태별 중단) | `AppState` | effect cleanup |
| Web | 동일 | `visibilitychange` | effect cleanup |

## 체크리스트

- [ ] 오프라인에서 캐시 화면 + 오프라인 배너, 쓰기 막힘 안내가 위 표대로 나온다
- [ ] 연결 상태 API로 요청을 막지 않는다
- [ ] 로그아웃 시 캐시·업로드 작업·임시 파일이 지워진다
- [ ] 좋아요 연타 10회 + 느린 망에서 최종 상태와 개수가 서버와 같다
- [ ] 좋아요 실패 시 서버 확인 상태로 되돌리고 메시지를 보여 준다
- [ ] 같은 게시물의 좋아요가 피드·상세·이어보기에서 같다
- [ ] AI 리뷰: 3초 간격, 약 5분 창, 화면 이탈 시 중단, `POST` 자동 재전송 없음, 창 초과 안내
