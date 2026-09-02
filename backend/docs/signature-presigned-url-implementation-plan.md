# 사인 presigned URL 구현 계획

- 상태: Implemented
- 관련 이슈: [#99 presigned url 발급 api 적용](https://github.com/woowacourse-teams/2026-Chalkak/issues/99)
- API 계약: `signature-presigned-url-api-design.md`
- 구현 브랜치: `be/feature/#99-signature-presigned-url`

## 1. 선행 확인

1. `be/develop`에 다음 #101 산출물이 있는지 확인한다.
   - `SignatureStorageKeys`
   - pending 사인 처리 상태
   - 환경별 `chalkak/staging/{environment}/signatures/` 계약
   - Lambda 완료·실패 콜백 API
2. `git pull --ff-only origin be/develop`로 최신화한다.
3. GitHub 이슈 번호로 `be/feature/#{issue}-signature-presigned-url`
   브랜치를 생성한다.
4. 병합 전 전체 백엔드 테스트를 한 번 실행해 baseline을
   확정한다.

## 2. TDD 순서

### 2.1 API 계약 테스트

`UserControllerTest`에 먼저 실패하는 테스트를 추가한다.

- `POST /api/v1/users/me/signature/uploads`
- active 사용자 요청 → 200
- `uploadId`, `uploadUrl`, `expiresInSeconds` JSON 계약
- `X-User-Id` 누락·오류 → 401
- request body가 없어도 정상 동작

그런 다음 response DTO와 controller endpoint를 최소 구현한다.

### 2.2 서비스 테스트

`UserServiceTest`에 다음 사례를 추가한다.

- active 회원이면 uploadId를 생성하고 issuer를 호출
- issuer 결과를 service result로 반환
- 존재하지 않거나 탈퇴한 회원은 404
- URL 발급은 사인 active·pending DB 상태를 변경하지 않음

그런 다음 service result, issuer port, service method를 구현한다.

### 2.3 S3 adapter 테스트

mock `S3Presigner`로 다음 요청 계약을 검증한다.

- bucket이 `ImageProperties.bucket`과 같음
- dev 경로가 `chalkak/staging/dev/signatures/{uploadId}.png`
- prod 경로가 `chalkak/staging/prod/signatures/{uploadId}.png`
- content type이 `image/png`
- signature duration이 5분
- SDK가 반환한 URL을 수정하지 않고 전달

그런 다음 `S3SignatureImageUploadIssuer`와 `S3Presigner` bean을
구현한다.

### 2.4 설정 테스트

- 만료 시간이 `Duration.ofMinutes(5)`로 서명 요청에 반영됨
- 환경별 이미지 prefix는 #101 `ImageProperties.environment`를 재사용
- test 프로필에서는 실제 AWS credential을 요구하지 않음

## 3. 예상 파일

```text
src/main/java/com/chalkak/backend/user/
├── api/v1/controller/UserController.java
├── api/v1/dto/response/UserSignatureUploadResponse.java
├── domain/SignatureImageUpload.java
├── service/UserService.java
├── repository/SignatureImageUploadIssuer.java
├── repository/SignatureImageStorage.java
└── infrastructure/infra/
    ├── S3ClientConfig.java
    ├── S3SignatureImageStorage.java
    └── S3SignatureImageUploadIssuer.java

src/test/java/com/chalkak/backend/user/
├── api/v1/controller/UserControllerTest.java
├── service/UserServiceTest.java
└── infrastructure/infra/S3SignatureImageUploadIssuerTest.java
```

파일 명과 패키지는 #101 병합 후 최신 `be/develop`을 다시
확인하고, 이미 동일 책임의 포트·결과 객체가 있으면 재사용한다.

## 4. 검증

1. 변경 테스트를 먼저 실행한다.
2. `./gradlew test`로 전체 회귀를 확인한다.
3. `git diff --check`로 whitespace 오류를 확인한다.
4. AWS 권한이 준비된 후 dev에서 수동 smoke test를 수행한다.

```text
URL 발급
→ curl -X PUT -H 'Content-Type: image/png' --data-binary @signature.png
→ S3 staging/dev/signatures 객체 생성
→ SQS 메시지 생성
→ Lambda original·thumbnail 생성
→ 사인 저장 API 및 콜백 승격 확인
```

## 5. AWS 적용 체크리스트

- EC2 instance role에 환경별 staging `s3:PutObject` 권한 확인
- S3 event notification prefix `chalkak/staging/` 변경 없음
- SQS·Lambda trigger 변경 없음
- 브라우저 클라이언트가 필요하면 S3 CORS 추가
- URL·AWS credential·signature query를 CloudWatch 로그에 남기지 않음

## 6. 커밋 계획

커밋은 아래 두 단위로 나누는 것을 기본으로 한다.

1. `feat: 사인 이미지 presigned URL 발급 기능 구현 (#99)`
   - service result·port
   - `S3Presigner` 설정과 adapter
   - adapter·service 테스트
2. `feat: 사인 이미지 업로드 URL 발급 API 추가 (#99)`
   - controller·response DTO
   - controller 테스트
   - README·API 문서·배포 설정 갱신

첫 번째 단위가 테스트를 포함해 독립적으로 빌드되면 그 시점에
사용자에게 커밋 경계를 알리고 첫 커밋을 만든 후 API 단위를 계속한다.
병합 후 파일 구조가 이 분리를 지지하지 않으면 테스트가 통과하는
최소 독립 단위로 조정하되, 하나의 커밋에 설정·API·문서를 무조건
몰아넣지 않는다.

## 7. 완료 조건

- API·service·adapter 테스트 통과
- 전체 백엔드 테스트 통과
- API 문서와 구현 JSON·S3 키·만료 시간 일치
- AWS에서 최소 권한으로 dev 수동 업로드 성공
- #101의 비동기 완료·실패 콜백 흐름에 회귀 없음
