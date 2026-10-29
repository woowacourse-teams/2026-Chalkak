# 관리자 앱 버전 관리 연동 안내

현재 정책은 [앱 버전 규칙](../rules/app-version.md)의 APPVERSION-004~006과 [관리자 규칙](../rules/admin.md)의 ADMIN-001·006·007을 따른다. 관리자 웹과 앱 UI는 별도 작업이다.

## 조회 후 저장

관리자 액세스 토큰으로 `GET /api/v1/admin/app-versions`를 호출한다. `appVersions` 배열은 ANDROID·IOS 두 항목을 반환한다. 미설정 플랫폼은 platform 외 모든 필드가 null이다. 설정된 항목은 다음 필드를 갖는다.

```json
{
  "platform": "ANDROID",
  "minimumSupportedVersion": "1.10.0",
  "storeUrl": "https://play.google.com/store/apps/details?id=com.example.chalkak",
  "revision": 0,
  "lastChangedBy": "019a0000-0000-7000-8000-000000000848",
  "changeReason": "출시 확인",
  "createdAt": "2026-10-29T01:00:00Z",
  "updatedAt": "2026-10-29T01:00:00Z"
}
```

위 값은 계약 설명용 예시이며 운영값이 아니다. 시각은 UTC Instant이다. 기존에 직접 입력한 설정의 변경자·사유는 null일 수 있다.

`PUT /api/v1/admin/app-versions/ANDROID` 또는 `/IOS`에 다음 본문을 보낸다. 최초 저장은 revision=null, 수정은 방금 조회한 revision을 그대로 전달한다. 성공하면 현재 설정을 200으로 반환한다.

```json
{
  "revision": null,
  "minimumSupportedVersion": "1.10.0",
  "storeUrl": "https://play.google.com/store/apps/details?id=com.example.chalkak",
  "reason": "스토어 설치 가능 버전 확인"
}
```

버전·주소·사유는 필수이다. 버전은 세 자리 각 숫자 0~2,147,483,647이며 `01.2.0`을 거절한다. 사유는 앞뒤 공백 제거 후 1~500 Unicode code point이다. 주소는 앞뒤 공백 제거 후 2,000자 이하의 공식 HTTPS 앱 상세 링크다. Android는 id와 선택적 hl·gl, iOS는 선택적 l·mt=8 쿼리만 허용한다. 다른 추적 쿼리가 있으면 거절하므로 관리자가 해당 쿼리를 제거한 주소를 입력한다.

## 재시도와 복구

- 같은 버전·주소 재저장은 기존 값과 이력을 유지한다. 오래된 revision 또는 최초 저장의 null revision으로 다시 보내도 같은 값이면 성공한다. 사유 등 입력 검증은 재시도에도 적용한다.
- 값이 다르고 revision이 현재 상태와 다르면 400 `RESOURCE_STATE_CHANGED`다. 최신 값을 다시 조회하고 관리자가 변경 내용을 확인한 후 저장한다.
- 잘못 높인 최소 버전은 최신 revision으로 더 낮은 버전을 저장해 복구한다. 복구도 사유와 변경 이력을 남긴다.
- 미인증은 401, 일반 회원은 상태와 무관하게 403, 잘못된 입력·플랫폼은 400이다. 관리자 업무 변경과 감사 저장은 함께 성공하거나 함께 취소된다.
- 이력은 기존 `GET /api/v1/admin/audit-logs?targetType=APP_VERSION`으로 조회한다. action은 APP_VERSION_CREATED 또는 APP_VERSION_UPDATED다.

## 상향 전 운영 확인

관리자는 실제 Chalkak 앱 상세 링크인지 확인하고 대상 국가·기기·단계적 출시 범위에서 해당 버전 이상을 설치할 수 있는지 확인한 뒤 최소 버전을 높인다. 서버는 주소 형식을 검증하며 스토어에 접속해 출시 여부를 자동 확인하지 않는다. 저장 화면에 확인 체크 필드를 요구하지 않는다.

저장 완료 후 공개 `GET /api/v1/app-version?platform=...`으로 반영된 값을 확인한다. 앱의 비교·차단 화면·스토어 이동은 앱 연동이 있어야 동작한다. 공개 조회의 통신 실패 처리와 호출 주기는 아직 합의하지 않았다.

## 배포 전 기존 설정 확인

서버는 Flyway 마이그레이션을 적용하기 전에 기존 `app_versions` 행을 최소 버전·스토어 주소 VO와 감사 스냅샷 검증으로 확인한다. 이 검증은 읽기만 하며 예시 값으로 교체하거나 기존 주소의 공백·추적 쿼리를 조용히 지우지 않는다. 테이블이 없는 첫 설치와 빈 테이블은 통과한다.

기존 행이 새 형식과 맞지 않으면 해당 행 ID만 포함한 사전 검증 오류로 시작을 중단한다. 원문 URL·민감정보는 오류 메시지에 출력하지 않는다. 배포 담당자는 대상 DB에서 다음 조회로 실제 값을 확인하고, 해당 앱의 올바른 버전·주소를 확인한 뒤 필요한 행을 보정하고 재배포한다.

```sql
SELECT id, platform, minimum_supported_version, store_url
FROM app_versions;
```

확인 대상에는 `01.2.0`·정수 상한 초과 버전, 예전 호스트·단축 주소, 허용되지 않은 쿼리, 고정 경로의 퍼센트 인코딩, 앞뒤 공백·탭·개행이 포함된다. 포트는 생략하거나 정확히 `:443`만 사용한다. 기본 `btrim`은 탭·개행·모든 Unicode 공백을 제거하지 않으므로 Java의 `strip()`과 같다고 가정하지 않는다. `NOT VALID`만 추가해 부적합한 기존 행을 남기는 방법도 VO 조회 실패를 해결하지 못한다.

관리자 요청은 저장 전에 주소 공백을 제거한다. DB에서 읽는 주소 생성자는 값을 바꾸지 않으며, 저장 원문이 정규 형식인지 검증한다. 정상 운영에서 설정을 바꿀 때는 관리자 API를 사용한다. DB CHECK는 기본 저장 방어이며, 임의의 SQL 입력에 대해 Java의 URI·쿼리·감사 값 검증 전체를 대신하지 않는다. 운영 DB의 실제 행 확인과 보정은 배포 담당자의 작업이며 테스트 DB 검증으로 대신하지 않는다.
