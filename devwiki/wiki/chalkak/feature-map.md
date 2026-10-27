# 찰캌 기능 지도

새 플랫폼에서 구현할 기능 목록이다. 정책은 규칙 ID의 문서를 읽고, 이 표는 "무엇을 어느 API로 만들고 어떤 공통 문서를 봐야 하는지" 찾는 용도로만 쓴다. 2026-10-27 `main` 기준. [찰캌규칙]

## 1. 현재 레포 상태

| 위치 | 상태 |
| --- | --- |
| `backend/` | Spring. 사용자 API는 `/api/v1/**`, springdoc OpenAPI 제공 |
| `client/iOS` | SwiftUI 앱 운영 중. 배포 타깃 iOS 18.0. 기능 폴더 15개 |
| `client/android` | Compose 앱 운영 중. minSdk 26, targetSdk 37. 기능 패키지 17개, ktlint·Konsist 검사 |
| `client/web/beta` | 베타 감사 정적 페이지뿐. 서비스 웹앱은 없다 |
| `admin-web/` | 관리자 웹. 사용자 클라이언트 범위가 아니다 |

새로 만드는 것은 Flutter, React Native, Web app 세 개다. iOS·Android는 기존 앱이 있으므로 "다섯 개를 같은 기준으로 맞추는 것"에는 기존 앱의 격차 점검도 포함된다. 이 범위는 [open-questions.md](../open-questions.md#팀-결정-대상)에서 결정한다.

## 2. 기능과 규칙, API, 화면

| 기능 | 규칙·연동 문서 | 사용자 API | 기존 화면(iOS / Android) | 관련 공통 문서 |
| --- | --- | --- | --- | --- |
| 소셜 로그인(Apple, Google, Kakao)·가입 | USER-001, SIGN-001 | `POST /auth/social-login`, `POST /auth/social-signup`, `POST /auth/social-signup/signature/uploads` | Login, Onboarding / login, terms, authgate | [보안](../common/security-privacy-release.md), [네트워킹](../common/networking-errors-retry.md#4-토큰-갱신) |
| 토큰 갱신·로그아웃 | 푸시 기기 등록 안내 | `POST /auth/refresh`, `POST /auth/logout` | 공통 네트워크 계층 | [네트워킹](../common/networking-errors-retry.md#4-토큰-갱신) |
| 회원 탈퇴 | USER-003~006 | `DELETE /users/me` | Settings / settings | [보안 §스토어](../common/security-privacy-release.md#5-스토어-심사-필수-항목) |
| 정지 회원 제한 | USER-002 | 각 API의 오류 코드 | 공통 | [네트워킹 §에러](../common/networking-errors-retry.md#1-에러-분류) |
| 사인(손글씨 서명 이미지) 등록·교체 | SIGN-001~006 | `GET /users/me/signature`, `POST /users/me/signature/uploads`, `PUT /users/me/signature` | Signature / signature | [업로드](../common/media-upload.md) |
| 오늘의 주제 | TOPIC-001~005 | `GET /topics`, `GET /posts/today` | Today / today | [UX §시간대](../common/ux-accessibility.md#6-날짜시간대) |
| 피드(최신·인기·랜덤) | POST-007, POST-008 | `GET /posts` | Feed, Home / feed, home | [오프라인](../common/offline-optimistic-polling.md), [UX](../common/ux-accessibility.md) |
| 게시물 상세 | POST-007 | `GET /posts/{postId}` | Feed / feed | |
| 작성자 공개 사진 이어보기 | POST-012, [작성자 공개 사진 안내](../../../docs/business-rules/guides/author-public-posts.md) | `GET /users/{userId}/posts` | Display / display | [UX §목록](../common/ux-accessibility.md#2-화면-상태) |
| 좋아요 | LIKE-001~005 | `PUT /posts/{postId}/likes`, `DELETE /posts/{postId}/likes` | Feed, Display / feed, display | [낙관적 업데이트](../common/offline-optimistic-polling.md#3-낙관적-업데이트) |
| 사진 촬영·선택·편집 | POST-004 | 없음(로컬) | PhotoEdit / photoedit | [업로드](../common/media-upload.md), [Android 사진 편집 구조](../../../client/android/docs/architecture/photo-edit.md) |
| 게시물 작성(업로드) | POST-001~006, POST-011 | `POST /posts/uploads` → 스토리지 PUT → `POST /posts` | PhotoUpload / upload | [업로드](../common/media-upload.md#32-업로드-흐름) |
| 제목 수정·삭제 | POST-005, POST-010 | `PUT /posts/{postId}`, `DELETE /posts/{postId}` | Record / record | |
| 내 기록 캘린더 | POST-009 | `GET /posts/calendar`, `GET /posts/calendar/months` | Record / record | |
| AI 리뷰 요청·결과 | AIREVIEW-001~006, [AI 리뷰 안내](../../../docs/business-rules/guides/ai-review.md) | `POST /posts/{postId}/ai-review`, `GET /posts/{postId}/ai-review` | AIReview / aireview | [폴링](../common/offline-optimistic-polling.md#4-ai-리뷰-폴링) |
| 알림함·읽음 | NOTIFICATION-002~004, [알림함 안내](../../../docs/business-rules/guides/notification-inbox.md) | `GET /notifications`, `GET /notifications/unread-status`, `GET /notifications/{id}`, `PATCH .../{id}/read`, `PATCH /notifications/read-all` | Notification / notification | [푸시](../common/push-deeplink-navigation.md) |
| 푸시 기기 등록·수신 설정 | NOTIFICATION-005, 006, [푸시 기기 등록](../../../docs/business-rules/guides/push-device-registration.md), [푸시 메시지 계약](../../../docs/business-rules/guides/notification-push-contract.md) | `PUT /push-devices/current`, `GET/PATCH /notification-settings` | Settings / settings | [푸시](../common/push-deeplink-navigation.md) |
| 배너 | BANNER-006 | `GET /banners` | Home(Banner, IssueWebScreen) / home, web | |
| 피드백 제출 | FEEDBACK-001~003 | `POST /feedbacks` | Feedback / feedback | |
| 앱 버전 안내 | 규칙 없음 | 스토어 조회(iOS `AppStore`) | VersionGate / versiongate | [보안 §릴리스](../common/security-privacy-release.md#6-릴리스와-업데이트) |

API 경로 앞의 `/api/v1`은 생략했다. 요청·응답 스키마는 서버의 OpenAPI 문서가 기준이다.

## 3. 다섯 플랫폼 구현에 직접 영향을 주는 찰캌 사실

- **오류 응답 형식은 `{ errorCode, message }`다.** ([ErrorResponse](../../../backend/src/main/java/com/chalkak/backend/exception/ErrorResponse.java)) 클라이언트는 `errorCode`로 분기하고 문구는 공통 표에서 가져온다.
- **좋아요는 PUT·DELETE로 이미 멱등이다.** 중복 등록은 개수를 늘리지 않고, 없는 좋아요 취소도 성공이다(LIKE-003). 응답에 현재 좋아요 수가 온다(LIKE-004). 낙관적 UI에서 재시도해도 안전하다.
- **게시물 업로드 입력은 WebP, 최대 5 MiB다.** 업로드 URL은 5분, 업로드 ID는 1시간 유효하다(POST-004). 기존 Android 앱의 품질 단계 결정은 [post-image-encoding.md](../../../client/android/docs/architecture/post-image-encoding.md)에 있다.
- **게시물 생성은 POST지만 서버가 중복을 막는다.** 업로드 한 건은 한 번만 연결되고(POST-004), 한 회원은 한 주제에 활성 게시물 하나만 가진다(POST-003). 따라서 응답을 못 받은 생성 요청을 재시도하면 두 번째는 실패 코드로 온다. 클라이언트는 이 코드를 "이미 올라갔을 수 있음"으로 해석하고 오늘 작성 여부를 다시 조회한다(POST-011). [제안]
- **게시물은 검수를 거친다.** `VALIDATING → PENDING → APPROVED | REJECTED`(POST-006). 업로드 직후 피드에 바로 보이지 않는 것이 정상이며 승인·반려는 푸시와 알림함으로 온다.
- **주제 날짜는 KST 기준이다**(TOPIC-002, TOPIC-005). 기기 시간대로 "오늘"을 계산하지 않는다.
- **사인은 PNG, 최대 1 MiB다**(SIGN-002). 게시물에는 작성자의 현재 사인이 표시된다(SIGN-005).
- **AI 리뷰는 3초 간격, 약 5분 창으로 폴링한다.** HTTP 상태가 아니라 응답의 `status`로 분기한다. ([AI 리뷰 안내](../../../docs/business-rules/guides/ai-review.md))
- **푸시는 `notification` + `data`를 함께 보낸다.** `data`에는 `eventId`, `notificationId`, `type`, `sourceType`, `sourceId`가 있다. ([푸시 메시지 계약](../../../docs/business-rules/guides/notification-push-contract.md))
- **소셜 로그인은 Apple, Google, Kakao다.** 서버 코드 기준. 서드파티 로그인을 제공하므로 iOS는 Sign in with Apple을 함께 제공해야 한다(App Review 4.8). [공식]
