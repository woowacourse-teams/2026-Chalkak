# 사인 이미지 presigned URL API 설계

- 상태: Implemented
- 관련 이슈: [#99 presigned url 발급 api 적용](https://github.com/woowacourse-teams/2026-Chalkak/issues/99)
- 구현 브랜치: `be/feature/#99-signature-presigned-url`

## 1. 목적

클라이언트가 백엔드를 통해 이미지 바이트를 전송하지 않고 S3에
사인 PNG를 직접 업로드할 수 있도록 1회성 presigned PUT URL을 발급한다.
업로드가 완료되면 기존 S3 이벤트 → SQS → Lambda 흐름이 이미지를
비동기로 처리한다.

## 2. 전체 흐름

```text
1. 클라이언트 → POST /api/v1/users/me/signature/uploads
2. 백엔드가 uploadId와 환경별 staging key 생성
3. 백엔드 → uploadId, uploadUrl, expiresInSeconds
4. 클라이언트 → presigned URL로 PNG PUT
5. S3 ObjectCreated → SQS → Lambda
6. 클라이언트 → PUT /api/v1/users/me/signature (uploadId)
7. 백엔드가 pending 상태를 저장하고 즉시 응답
8. Lambda 완료 콜백 → active original·thumbnail 키 승격
```

Lambda가 6번보다 먼저 완료되어도 사인 저장 API가 최종 thumbnail
객체를 한 번 확인하여 즉시 승격한다. 따라서 presigned URL API는
Lambda 완료 순서를 보장하지 않아도 된다.

## 3. API 계약

### 3.1 URL 발급

```http
POST /api/v1/users/me/signature/uploads
X-User-Id: {userId}
```

- Request body: 없음
- 인증: 기존 `@LoginUser AuthenticatedUser` 계약 사용
- `signature` 싱글턴 리소스 아래의 `uploads` 컬렉션에 새 업로드
  작업을 발급하므로 `GET`이 아닌 `POST`를 사용한다.
- 현재는 발급 이력을 DB에 저장하거나 별도 URI로 조회할 수 있는
  업로드 리소스를 만들지 않으므로 `201 Created`가 아닌 `200 OK`를
  반환한다.
- 현재 임시 인증 정책에 따라 `prod` 프로필에서는 외부 User API를
  등록하지 않는다. Spring Security 도입 후 프로필 제한을 제거한다.

#### 성공 응답

```http
200 OK
```

```json
{
  "uploadId": "0198f6c1-62ba-7d30-8b12-0f733b6570d4",
  "uploadUrl": "https://techcourse-project-2026.s3.ap-northeast-2.amazonaws.com/...",
  "expiresInSeconds": 300
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `uploadId` | UUID | 백엔드가 생성한 업로드 식별자. 사인 저장 API에 전달 |
| `uploadUrl` | String | S3 presigned PUT URL |
| `expiresInSeconds` | Number | 발급 시점부터의 URL 유효 시간. 기본 300초 |

#### 실패 응답

```json
// X-User-Id가 없거나 UUID 형식이 아닐 때
// 401 Unauthorized
{
  "errorCode": "UNAUTHORIZED",
  "message": "유효하지 않은 인증 정보입니다."
}
```

```json
// 회원이 없거나 탈퇴했을 때
// 404 Not Found
{
  "errorCode": "BUSINESS_ERROR",
  "message": "사인을 업로드할 회원을 찾을 수 없습니다."
}
```

S3 presigning 자격 증명 또는 AWS 호출에 실패하면 임의의 성공 URL을
반환하지 않고 서버 오류로 전파한다. 외부 응답은 기존 공통 예외
계약을 따른다.

### 3.2 S3 업로드 요청

클라이언트는 `uploadUrl`에 다음과 같이 요청한다.

```http
PUT {uploadUrl}
Content-Type: image/png

<PNG binary>
```

- `Content-Type`은 presigned URL에 서명된 `image/png`과 정확히 같아야 한다.
- 파일 크기와 실제 PNG 여부는 기존 백엔드 metadata 검증과 Lambda
  디코딩 검증이 최종 판단한다.
- URL 만료 후에는 새 URL을 발급받아야 한다.

## 4. S3 키 계약

```text
dev  : chalkak/staging/dev/signatures/{uploadId}.png
prod : chalkak/staging/prod/signatures/{uploadId}.png
```

- bucket: `techcourse-project-2026`
- root prefix: `S3_PREFIX`, 기본 `chalkak`
- environment: #101에서 추가된 백엔드 이미지 환경 설정
- 파일 형식: `.png`
- 클라이언트가 bucket, key, environment를 요청으로 지정하지 않는다.

기존 S3 event notification prefix `chalkak/staging/`는 변경하지 않는다.

## 5. 서버 설계

### 5.1 컨트롤러

- 기존 `UserController` 아래에 `POST /me/signature/uploads` 추가
- 응답 DTO: `UserSignatureUploadResponse`
- 컨트롤러는 service 결과를 JSON DTO로 변환하는 역할만 담당

### 5.2 서비스

- `UserService.createSignatureUpload(UUID userId)`
- active 회원 존재 여부를 확인한다.
- `UUID.randomUUID()`로 uploadId를 생성한다.
- 인프라 port를 호출하고 service result를 반환한다.
- 발급 자체는 DB 변경이 없으므로 read-only 트랜잭션을 유지한다.

### 5.3 포트와 인프라

- service result: `SignatureImageUpload`
- port: `SignatureImageUploadIssuer`
- adapter: `S3SignatureImageUploadIssuer`
- AWS SDK: `S3Presigner`, `PutObjectPresignRequest`, `PutObjectRequest`
- `PutObjectRequest` 서명 조건에 bucket, key, `Content-Type: image/png`를 포함
- 만료 기본값: `Duration.ofMinutes(5)`

`S3Presigner`는 `S3Client`와 같은 region과 배포 환경 credential chain을
사용한다. 빈으로 등록하고 애플리케이션 종료 시 `close()`한다.

## 6. 보안·제한

- URL 유효 시간을 5분으로 제한한다.
- 서버가 생성한 bucket·key·Content-Type을 서명해 경로 조작을 막는다.
- 발급 API는 active 회원에게만 허용한다.
- presigned URL은 bearer credential처럼 취급하고 로그에 전체 query
  string을 남기지 않는다.
- 현재 단계에서는 발급 이력을 DB에 저장하지 않는다. 따라서
  uploadId의 사용자 소유권, 발급 횟수 제한, 동시 청구 직렬화는 후속
  이슈로 남긴다. uploadId의 충분한 무작위성과 HTTPS는 이 제한을
  대체하는 권한 검증이 아니다.

## 7. AWS·클라이언트 설정

### 7.1 IAM

presigned URL을 서명하는 EC2 instance role에 다음 권한이 필요하다.

```text
s3:PutObject
arn:aws:s3:::techcourse-project-2026/chalkak/staging/dev/signatures/*
arn:aws:s3:::techcourse-project-2026/chalkak/staging/prod/signatures/*
```

권한을 직접 수정할 수 없으면 위 resource 범위를 기술 검토 채널에
전달한다. 각 백엔드 role은 자신의 환경 prefix만 PutObject하도록
제한하는 구성이 최선이다.

### 7.2 CORS

브라우저 기반 클라이언트가 직접 PUT하면 S3 bucket CORS에 허용
origin, `PUT`, `Content-Type` header를 추가해야 한다. 네이티브 모바일
클라이언트는 브라우저 CORS 제약을 받지 않는다.

## 8. 제외 범위

- 포스트 이미지 presigned URL
- 업로드 이력·소유권 DB 테이블
- 이전 staging 객체를 즉시 삭제하는 API
- 이미지 변환·Lambda·SQS 재구현
- 사인 처리 상태 조회 API
- rate limit

## 9. 인수 조건

- active 회원이 URL 발급 API를 호출하면 200과 300초 유효한 URL을 받는다.
- uploadId는 요청마다 새로 생성된다.
- dev·prod 프로필이 각각 자신의 staging 경로를 서명한다.
- 서명된 URL은 `Content-Type: image/png`로 PUT할 때만 성공한다.
- bucket·key·environment는 클라이언트 입력으로 받지 않는다.
- 업로드 완료 후 기존 Lambda 비동기 처리와 사인 저장 API가
  같은 uploadId로 연결된다.
- 존재하지 않거나 탈퇴한 회원은 URL을 발급받지 못한다.
- 발급 실패 시 서명되지 않은 임의 URL을 반환하지 않는다.
