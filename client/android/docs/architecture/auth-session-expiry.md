# 로그인이 만료되면 보던 화면 위에 로그인을 쌓고, 같은 계정이면 돌아온다

관련 경로: data/remote/TokenAuthenticator.kt, data/remote/SessionRefreshCoordinator.kt, data/local/auth/**, domain/model/UserSessionState.kt, feature/authgate/**, navigation/ReauthenticationNavigator.kt

## 맥락
Refresh 토큰이 만료·폐기되면 세션을 지워 `SignedOut`이 됐고, AuthGate가 `ChalkakNavHost`를 통째로 새로 만들어 백스택·ViewModel·작성 중 글(`SavedStateHandle`)이 사라진 채 로그인 뒤 홈으로 갔다(#1016).
또 갱신이 네트워크 오류로 실패해도 원요청은 401로 끝나서, 401을 "다시 로그인"으로 바꾸는 저장소(사진 올리기·고객센터)가 살아 있는 로그인을 로그인 화면으로 보냈다.

## 서버 응답과 앱 동작
서버 쪽은 be/develop f0fdeac 코드 기준(`RefreshTokenService`, `UserRefreshTokenService`, `LoginSessionService`, `ErrorCode`)이다.

| 상황 | 서버 응답 | 앱 동작 |
|---|---|---|
| Access 토큰 만료·무효 | 401 `UNAUTHORIZED` | 갱신 후 원요청 1회 재시도. 동시에 여러 요청이 받아도 갱신은 한 번(`SessionRefreshCoordinator`) |
| Refresh 토큰 만료·폐기·재사용·모르는 토큰 | 갱신 요청에 401 `REAUTHENTICATION_REQUIRED`(원인 구분 없음) | 세션 `ReauthenticationRequired(userId)`, 보던 화면 위에 로그인 |
| 원요청이 바로 `REAUTHENTICATION_REQUIRED` | 401 `REAUTHENTICATION_REQUIRED` | 갱신 없이 위와 같음 |
| 갱신 중 네트워크 오류·5xx·응답 형식 오류 | - | 로그인 유지, 원요청은 `IOException`(화면은 네트워크 오류 안내) |
| 권한 없음(정지 등) | 403 `FORBIDDEN` | 갱신하지 않음(OkHttp `Authenticator`는 401만 받음) |
| 직접 로그아웃·탈퇴 | - | 지금처럼 `SignedOut`, 앱 화면을 새로 만듦 |

- 일반 API는 로그인 세션이 살아 있는지 검사하지 않는다. 세션 검사는 `PUT /api/v1/push-devices/current`와 푸시 발송뿐이다. 그래서 Refresh 토큰을 폐기해도 Access 토큰이 살아 있는 동안은 그대로 쓰이고, 다음 갱신에서 재인증으로 간다.
- 푸시 기기 등록도 재인증 경로다. Access 토큰에 `session_id`가 없으면 등록 전에 갱신하는데(`SessionRefreshCoordinator.ensurePushRegistrationSession`), 여기서 재인증이 나와도 `clear()`가 아니라 재인증 상태로 간다. `MainActivity.onResume`마다 등록을 확인하므로, 이 경로가 `clear()`를 부르면 화면에 돌아오는 순간 백스택이 사라진다(#1016 녹화에서 발견).
- Refresh 토큰은 갱신마다 바뀐다. 이미 바뀐 토큰을 다시 내면 그 로그인 전체가 폐기된다. 그래서 갱신은 한 번만 보내야 한다.
- 수명: 서버 env 예시(`deploy/examples/application.{dev,prod}.env.example`)는 Access 15분, Refresh 비활동 30일(갱신마다 연장)·절대 90일(최초 로그인 고정)이다. 실제 값은 서버 env 파일에 있고 예시와 다를 수 있다. 2026-11-11 개발 서버에서 debug 개발자 도구로 본 값은 Access 7일, Refresh 30일이었다. 그래서 개발 서버에서는 Access 토큰이 저절로 만료되기를 기다리지 말고 개발자 도구로 만료시킨다.

## 선택지
- A: 재인증도 `SignedOut`으로 두고, 로그인 뒤 돌아갈 화면을 따로 저장해 다시 연다 — 상태 하나로 끝남 / 백스택·ViewModel·작성 중 글을 되살리려면 화면마다 저장·복원이 필요함
- B: 재인증 전용 상태를 두고 AuthGate가 NavHost를 바꾸지 않은 채 로그인을 위에 쌓는다 — 백스택·ViewModel이 그대로 남아 쓰던 글도 남음 / 세션 상태가 하나 늘어 `UserSessionState`를 보는 `when`을 모두 챙겨야 함

## 결정
B를 골랐다.
- `LocalSession`·`UserSessionState.ReauthenticationRequired(userId)`는 **메모리에만** 둔다. 토큰은 지우고 userId만 기억한다. 재인증 로그인 화면에서 프로세스가 죽으면 다시 시작할 때 `SignedOut` → 일반 로그인 → 홈으로 간다(알려진 한계).
- `UserSessionStore.requireReauthentication`은 상태를 먼저 알린 뒤 저장소를 비운다. 순서를 바꾸면 DataStore 구독이 `SignedOut`을 먼저 내 AuthGate가 앱 화면을 새로 만든다.
- AuthGate는 `Authenticated`와 `ReauthenticationRequired`를 같은 key로 그린다. 게스트로 바꾸면 key가 달라 앱 화면을 새로 만든다(게스트 홈).
- 백스택 정책은 `ReauthenticationNavigator` 한 곳에서 정한다. 같은 계정으로 로그인하면 로그인만 빼고, 다른 계정이면 홈부터 다시 시작하고, 뒤로가기는 앱을 백그라운드로 보낸다. 화면별 `onReauthenticationRequired`도 세션이 재인증 상태면 이 정책으로 보낸다(로그인 화면이 두 번 쌓이지 않게).
- ui-state-navigation의 원칙대로 LoginViewModel·화면 ViewModel은 목적지를 모른다. 복귀는 세션 상태 변화(`ReauthenticationRequired(u1)` → `Authenticated(u1)`)를 보고 NavHost가 한다.
- 돌아온 화면 중 재인증으로 실패한 요청이 있으면 세션 상태를 보고 다시 불러온다(지금은 사진 올리기 주제). 재인증 상태에서도 홈 내용은 비우지 않는다.
- 갱신 일시 실패는 `IOException`으로 끝낸다. 401을 그대로 넘기면 저장소가 "다시 로그인"으로 바꿔 버린다.

앞으로 지킬 것:
- `UserSessionState`를 보는 새 코드는 `ReauthenticationRequired`를 "로그인되지 않음"으로 다루되 보던 내용을 지우지 않는다.
- 재인증 경로에서 `sessionStore.clear()`를 부르지 않는다. `clear()`는 사용자가 고른 로그아웃·탈퇴용이다.

## 근거
이슈 #1016(스프린트 4 Android 요구사항 6번), 계획 댓글의 TC 1–18, 공통 규칙 `guides/push-device-registration.md` 5항(Refresh 토큰 인증 오류일 때만 다시 로그인, 네트워크·서버 오류로 로그인 해제 안 함).
재현: debug 개발자 도구의 "인증 토큰" 카드(Access 토큰 강제 만료, Refresh 토큰 폐기 = 현재 RT로 `POST /api/v1/auth/logout` + Access 토큰 강제 만료, Refresh 토큰 폐기(30초 뒤 만료) = 폐기는 바로 하고 Access 토큰은 30초 뒤 무효, 동시 요청 5개와 갱신 횟수, 갱신 네트워크 실패 스위치).
- 개발자 도구는 설정에서만 열려서, 다른 화면(예: 글 쓰던 사진 올리기)에서 재인증을 보려면 30초 뒤 만료 버튼을 누르고 그 화면으로 가서 30초 뒤 요청을 보낸다.
