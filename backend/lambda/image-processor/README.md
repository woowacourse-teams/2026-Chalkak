# Chalkak image processor Lambda

이 디렉터리는 이슈 #101의 사인 이미지 검증·변환 Lambda와 배포 패키지용
CodeBuild 설정을 관리한다. 컨테이너와 ECR은 사용하지 않으며, CodeBuild의 Linux
환경에서 네이티브 의존성이 포함된 Lambda ZIP을 만든다.

## 처리 흐름

```text
S3 ObjectCreated
  → SQS chalkak-image-processing
  → Lambda chalkak-image-processor
  → staging 하위 디렉터리로 processor 라우팅
  → PNG 실제 디코딩 및 검증
  → 메타데이터를 제거한 PNG 재인코딩
  → 원본과 썸네일 저장
  → 백엔드 성공·실패 콜백
  → staging 객체 삭제
```

현재 Spring 애플리케이션과 공유하는 S3 키 계약은 다음과 같다.

```text
입력(dev)   chalkak/staging/dev/signatures/{uploadId}.png
입력(prod)  chalkak/staging/prod/signatures/{uploadId}.png
원본         chalkak/signatures/{environment}/original/{uploadId}.png
썸네일       chalkak/signatures/{environment}/thumbnail/{uploadId}.png
```

S3 이벤트 알림의 prefix는 기존 `chalkak/staging/`를 그대로 유지한다.
Lambda는 입력 키의 `environment`를 읽어 같은 환경의 결과 경로와 백엔드
콜백 URL을 선택하며, `dev`와 `prod` 외의 환경은 반려한다.

### 백엔드 DB 기록 계약

사인 등록 API는 staging 객체의 형식과 크기를 검증한 뒤, Lambda 완료를
기다리지 않고 `pending_signature_upload_id`와 처리 상태를 저장한다.
기존 active 원본·썸네일 키는 Lambda가 완료될 때까지 유지한다.

```text
사인 등록 API 트랜잭션
  pending_signature_upload_id = uploadId
  signature_processing_status = PROCESSING
  signature_processing_started_at = now
  active 키는 유지
  → 즉시 응답

Lambda
  → 원본·썸네일 객체 생성
  → HMAC 성공 콜백
  → 백엔드가 pending 일치 시 active 키로 승격
  → staging 삭제
```

성공 콜백은 pending `uploadId`가 현재 작업과 같고 상태가
`PROCESSING`일 때만 승격한다. 중복 콜백과 이전 작업의 느린 콜백은
`204 No Content`로 멱등하게 무시한다. 영구 실패 콜백은 active를 유지하고
상태만 `FAILED`로 바꾼다.

이 구조를 택한 이유는 **DB에 키가 있다는 사실이 S3 객체의 존재를 보장하지
않기 때문**이다. 등록 시점에 예측한 최종 키를 곧바로 active로 저장하면,
Lambda가 영구 실패했을 때 존재하지 않는 URL이 active로 남고 다른 사용자의
게시물 목록에서 깨진 이미지로 노출된다. 그래서 출력 객체 저장이 끝난
뒤에만 콜백으로 승격한다.

이슈 #101은 사인 처리만 포함한다. 포스트 이미지 처리를 추가할 때는
`image_processor` 아래에 별도 프로세서를 추가하고 handler에서 staging 경로에 따라
라우팅한다. 현재도 `staging/{environment}/signatures/`와
`staging/{environment}/posts/`를 라우터가
구분하며, posts는 processor가 아직 없어 반려한다. 같은 SQS 큐에 소비
Lambda를 하나 더 연결해서 메시지를 나누면 안 된다.

## 실패 정책

- 크기 초과, 손상 파일, PNG가 아닌 파일은 실패 콜백 2xx 확인 후 staging 객체를
  삭제하고 메시지를 정상 종료한다.
- 잘못된 버킷·키처럼 신뢰할 업로드 ID를 추출할 수 없는 이벤트는 콜백 없이 반려한다.
- S3 timeout과 5xx처럼 복구 가능한 실패는 예외를 전파해 SQS가 다시 전달하게 한다.
- 원본과 썸네일 저장과 성공 콜백이 모두 성공한 뒤에만 staging 객체를 삭제한다.
- 백엔드 콜백 timeout·5xx와 네트워크 오류는 예외를 전파해 SQS가 다시 전달하게 한다.
- 백엔드 콜백이 `400`, `404`, `405`, `413`, `414`, `415`를 주면 같은 요청을 다시 보내도 결과가
  같으므로 재시도하지 않고 오류를 남긴 뒤 메시지를 종료한다. 우리 코드나 설정이 잘못 만든
  요청이라는 뜻이므로 CloudWatch 오류 로그가 유일한 단서다.
- `401`, `403`은 secret 롤링 교체 중 잠깐 날 수 있고 `408`, `429`는 명시적 재시도 대상이라
  재시도한다.
- 출력 키가 결정적이므로 같은 메시지가 다시 전달돼도 동일한 객체를 덮어쓴다.
- 현재 DLQ를 사용하지 않으므로 CloudWatch에서 Lambda 오류와 SQS 적체를 확인해야 한다.

검증에서 반려된 staging 객체는 실패 콜백이 2xx로 완료된 뒤 즉시 삭제한다.
콜백 timeout·5xx·네트워크 오류와 영구 거부에서는 상태 전달을 보장할 수 없으므로
재시도·조사를 위해 staging을 유지한다. 공유 버킷의 `chalkak/staging/` prefix
lifecycle 만료 규칙은 사용자가 업로드 후 이탈하거나 오류가 장기간 해결되지 않은
객체를 1~2일 뒤 정리하는 안전장치로 유지한다.

## 환경 변수

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `S3_BUCKET` | `techcourse-project-2026` | 이벤트를 허용할 S3 버킷. 백엔드와 같은 이름 |
| `S3_PREFIX` | `chalkak` | 공유 버킷 내 팀 prefix. 백엔드와 같은 이름 |
| `SIGNATURE_MAX_BYTES` | `1048576` | 사인 입력 최대 크기 1 MiB |
| `SIGNATURE_MAX_PIXELS` | `25000000` | 압축 폭탄 방지용 최대 픽셀 수 |
| `SIGNATURE_THUMBNAIL_MAX_SIZE` | `512` | 썸네일 가로·세로 최대 길이 |
| `SIGNATURE_CACHE_CONTROL` | `public, max-age=86400` | 결과 객체 Cache-Control |
| `DEV_BACKEND_CALLBACK_URL` | 없음(필수) | `/internal/v1/signature-processing`까지 포함한 dev 백엔드 HTTPS URL |
| `PROD_BACKEND_CALLBACK_URL` | 없음(필수) | `/internal/v1/signature-processing`까지 포함한 prod 백엔드 HTTPS URL |
| `IMAGE_PROCESSOR_CALLBACK_SECRET` | 없음(필수) | dev·prod 백엔드와 공통으로 사용하는 HMAC 비밀키 |
| `BACKEND_CALLBACK_TIMEOUT_SECONDS` | `3` | 백엔드 콜백 HTTP timeout |

### 배포 순서

1. dev·prod EC2의 `/etc/chalkak/application.env`에 동일한 32자 이상의
   `IMAGE_PROCESSOR_CALLBACK_SECRET`를 설정한다.
2. DB 마이그레이션과 내부 콜백 API가 포함된 dev·prod 백엔드를 먼저 배포한다.
3. Lambda에 `DEV_BACKEND_CALLBACK_URL`, `PROD_BACKEND_CALLBACK_URL`, 같은
   `IMAGE_PROCESSOR_CALLBACK_SECRET`, `BACKEND_CALLBACK_TIMEOUT_SECONDS`를 설정한다.
4. Lambda 코드를 배포하고 성공·실패 콜백이 204를 받는지 확인한다.

Lambda를 먼저 배포하면 필수 환경 변수 누락 또는 콜백 API 미배포로
SQS 재시도가 반복될 수 있다. HTTP 콜백은 추가 IAM 권한을 필요로
하지 않지만, ALB가 `/internal/v1/signature-processing/*` 경로를 백엔드
타겟 그룹으로 전달해야 한다.
입력 키가 `chalkak/staging/dev/` 또는 `chalkak/staging/prod/`로 시작하므로
공유 Lambda도 사인 등록을 소유한 백엔드 DB로만 콜백한다.

## 로컬 검증

Python 3.14와 동일한 계열의 환경에서 실행한다.

```bash
cd backend/lambda/image-processor
python -m venv .venv
.venv/bin/python -m pip install --requirement requirements.txt
.venv/bin/python -m unittest discover --start-directory tests --verbose
```

## CodeBuild 프로젝트

기존 공용 역할을 수정하지 않고 다음 값으로 별도 프로젝트를 만든다.

| 항목 | 값 |
| --- | --- |
| 프로젝트 이름 | `chalkak-image-processor-build` |
| 운영체제 | Amazon Linux 2023 |
| 이미지 | `aws/codebuild/amazonlinux-x86_64-standard:6.0` |
| 아키텍처 | x86_64 |
| Privileged mode | 비활성화 |
| Service role | `arn:aws:iam::843255971531:role/codebuild-project` |
| Buildspec | `backend/lambda/image-processor/buildspec.yml` |
| CloudWatch log group | `/aws/codebuild/project-2026` |
| Log stream | `chalkak-image-processor` |

CodePipeline에서 프로젝트를 호출하면 source와 artifact type을 모두 `CodePipeline`으로
설정한다. 단독 CodeBuild 프로젝트라면 artifact를 다음과 같이 설정한다.

```text
Type: Amazon S3
Bucket: techcourse-project-2026-artifacts
Path: chalkak/lambda/image-processor
Name: chalkak-image-processor.zip
Packaging: Zip
```

`buildspec.yml`의 artifact base directory가 `build/package`이므로 ZIP 최상위에
`handler.py`, `image_processor/`, `PIL/`, `boto3/`가 들어간다. S3 artifact 저장에서
AccessDenied가 발생하면 공용 IAM 역할을 직접 수정하지 않고 `#8기-기술-검토`에
문의한다.

### 기존 CodePipeline에 연결

현재 AWS Console에서 확인한 `chalkak-backend-dev-pipeline`은 V2이다. 기존
Source → Build → Deploy 구조를 유지하고, Lambda용 build와 deploy action을 각
stage에 병렬로 추가한다.

Build stage에 다음 action을 기존 백엔드 build action과 같은 run order로 추가한다.
같은 stage의 같은 run order action은 병렬 실행된다.

| 항목 | 값 |
| --- | --- |
| Action name | `BuildImageProcessor` |
| Action provider | AWS CodeBuild |
| Input artifact | 기존 Source action의 output artifact(`DevSource`) |
| Project | `chalkak-image-processor-build` |
| Output artifact | `LambdaBuild` |
| Run order | 기존 백엔드 build action과 같은 `1` |

공유 Lambda 하나만 사용하므로 같은 action을 `chalkak-prod-pipeline`에도 중복으로
연결하지 않는다. 두 pipeline이 같은 함수를 배포하면 마지막 실행이 앞선 배포를
덮어쓴다. 초기에는 `chalkak-backend-dev-pipeline` 하나를 Lambda 배포
소유자로 둔다.

Deploy stage에는 기존 EC2 CodeDeploy action과 같은 run order로 다음 V2 action을
추가한다. 두 build가 모두 성공한 뒤 EC2와 Lambda 배포가 병렬로 실행된다.

| 항목 | 값 |
| --- | --- |
| Action name | `DeployImageProcessor` |
| Action provider | AWS Lambda |
| Input artifact | `LambdaBuild` |
| Function name | `chalkak-image-processor` |
| Function alias | 비워둠 |
| Deploy strategy | 선택하지 않음(alias 미사용 시 적용 대상 아님) |
| Run order | 기존 EC2 CodeDeploy action과 같은 `1` |

alias를 비우면 source artifact를 `$LATEST`에 배포하고 새 version을 발행한다.
이 경우 deploy strategy 필드는 적용되지 않는다. 초기 소규모 환경에서는 이
구성을 사용하고, alias 기반 점진 배포가 필요할 때 alias와 canary 전략을
추가한다.

기존 EC2 action은 AWS CodeDeploy를 계속 사용하지만 Lambda action은 CodePipeline의
`AWS Lambda` provider를 사용한다. Lambda용 CodeDeploy application과 deployment group은
만들지 않는다.

Lambda deploy action을 실행하려면 공용 `codepipeline-project` role에 다음 권한이
필요하다. 직접 역할을 수정하지 않는다.

```text
lambda:GetAlias
lambda:UpdateFunctionCode
lambda:GetFunctionConfiguration
lambda:GetProvisionedConcurrencyConfig
lambda:PublishVersion
lambda:UpdateAlias

arn:aws:lambda:ap-northeast-2:843255971531:function:chalkak-image-processor
arn:aws:lambda:ap-northeast-2:843255971531:function:chalkak-image-processor:*
```

새 CodeBuild project를 pipeline에서 실행하려면 같은 role에 다음 권한도
필요하다.

```text
codebuild:StartBuild
codebuild:BatchGetBuilds

arn:aws:codebuild:ap-northeast-2:843255971531:project/chalkak-image-processor-build
```

pipeline 편집 저장 또는 실행 시 `AccessDenied`가 나면 위 권한과 resource ARN을
`#8기-기술-검토`에 전달한다. IAM을 직접 수정할 필요는 없다.

CodeBuild role에 Lambda 배포 권한을 주어 build 단계에서 함수를 수정하는 방식은
사용하지 않는다. 다른 build가 실패해도 Lambda만 먼저 배포되는 불일치가 생길 수 있기
때문이다.

## Lambda 설정

| 항목 | 값 |
| --- | --- |
| 함수 이름 | `chalkak-image-processor` |
| Runtime | Python 3.14 |
| Architecture | x86_64 |
| Handler | `handler.lambda_handler` |
| Memory | 1024 MB |
| Timeout | 60초 |
| Ephemeral storage | 1024 MB |
| VPC | 연결하지 않음 |

실행 역할은 회사가 제공한 기존 Lambda 역할을 선택한다. 다음 권한이 없는 경우 직접
IAM을 수정하지 않고 역할 이름과 아래 resource 범위를 기술 검토 채널에 전달한다.

```text
SQS
  sqs:ReceiveMessage
  sqs:DeleteMessage
  sqs:GetQueueAttributes
  sqs:ChangeMessageVisibility
  arn:aws:sqs:ap-northeast-2:843255971531:chalkak-image-processing

S3 read
  s3:GetObject
  arn:aws:s3:::techcourse-project-2026/chalkak/staging/*

S3 write/delete
  s3:PutObject
  arn:aws:s3:::techcourse-project-2026/chalkak/signatures/*
  s3:DeleteObject
  arn:aws:s3:::techcourse-project-2026/chalkak/staging/*

CloudWatch Logs
  logs:CreateLogGroup
  logs:CreateLogStream
  logs:PutLogEvents
```

Lambda를 생성하는 사용자에게는 기존 역할을 지정할 수 있는 `iam:PassRole`도 필요하다.

## SQS와 S3 연결

`chalkak-image-processing`은 Standard queue 하나만 사용한다.

| SQS 설정 | 값 |
| --- | --- |
| Visibility timeout | 360초 |
| Message retention | 4일 |
| Receive message wait time | 20초 |
| DLQ | 없음 |

S3가 이 queue에 event를 보낼 수 있도록 SQS → `chalkak-image-processing` →
Access policy에 다음 statement를 설정한다. 이미 다른 statement가 있으면 전체
policy를 덮어쓰지 말고 `Statement` 배열에 추가한다.

```json
{
  "Sid": "AllowS3ToSendImageEvents",
  "Effect": "Allow",
  "Principal": {
    "Service": "s3.amazonaws.com"
  },
  "Action": "sqs:SendMessage",
  "Resource": "arn:aws:sqs:ap-northeast-2:843255971531:chalkak-image-processing",
  "Condition": {
    "ArnEquals": {
      "aws:SourceArn": "arn:aws:s3:::techcourse-project-2026"
    },
    "StringEquals": {
      "aws:SourceAccount": "843255971531"
    }
  }
}
```

SQS access policy 저장이 `AccessDenied`로 막히면 위 statement를
`#8기-기술-검토`에 전달한다.

Lambda SQS trigger는 batch size `1`, batch window `0초`로 시작한다. S3 bucket event
notification은 `ObjectCreated` 이벤트, prefix `chalkak/staging/`, suffix는 빈 값으로
설정하고 위 SQS 큐로 보낸다. Lambda 내부 라우터가 `signatures/`와
`posts/`를 구분하므로 포스트 processor를 추가할 때 S3 notification을 다시
바꾸지 않아도 된다. 다만 포스트 processor가 배포되기 전에는
`staging/{environment}/posts/`로 실제 파일을 업로드하지 않는다.

배포 후 먼저 트리거를 비활성화한 상태에서 테스트 이벤트를 실행하고, 성공한 다음
트리거를 활성화한다. 실제 검증은 고유 UUID PNG를
`staging/dev/signatures/` 또는 `staging/prod/signatures/`에
올려 원본과 썸네일이 생성되고 staging 객체가 삭제되는지 확인한다.
