# 서버 구축

이 문서는 찰칵 백엔드 배포에 필요한 개발·운영 EC2의 최초 준비 절차를 설명한다. 전체 배포 구조는 [배포 가이드](../README.md)를 먼저 확인한다.

## 회사 공용 리소스

서울 Region(`ap-northeast-2`)에서 다음 회사 제공 리소스만 사용한다.

| 서비스 | 회사 제공 값 |
| --- | --- |
| Artifact bucket | `techcourse-project-2026-artifacts` |
| CodeBuild role | `arn:aws:iam::843255971531:role/codebuild-project` |
| CodeDeploy role | `arn:aws:iam::843255971531:role/codedeploy-project` |
| CodePipeline role | `arn:aws:iam::843255971531:role/codepipeline-project` |
| CodeBuild log group | `/aws/codebuild/project-2026` |
| Pipeline source | GitHub (Version 1) |

태그를 지원하는 리소스에는 다음 값을 입력한다.

```text
Service=techcourse
Role=techcourse-etc
ProjectTeam=chalkak
```

다른 팀의 IAM role, pipeline, build project, deployment group, S3 prefix를 열거나 수정하지 않는다. 삭제 권한이 제한된 공유 계정이므로 테스트용 리소스를 임의로 만들지 않는다. 권한이나 삭제가 필요한 문제는 `#8기-기술-검토`에 문의한다.

## EC2 공통 준비

### 운영체제와 CodeDeploy Agent 버전

AWS가 현재 테스트한 EC2 운영체제에는 Amazon Linux 2023과 Ubuntu Server 16.04, 18.04, 20.04, 22.04, 24.04, 25.04, 26.04가 포함된다. 다만 Ubuntu Server 24.04, 25.04, 26.04는 CodeDeploy Agent 2.0.0에서만 테스트되었다.

이 문서의 Ubuntu 설치 절차는 Ruby 의존성이 없는 Agent 2.0.x와 `latestv2` installer를 기준으로 한다. Agent 2.0.0은 Region별로 순차 배포되므로 설치 전에 `ap-northeast-2` 제공 여부를 확인한다.

### Java 25와 기본 패키지

Ubuntu에서 다음을 실행한다.

```bash
sudo apt update
sudo apt install -y ca-certificates curl wget gpg unzip gzip apt-transport-https

wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
  | gpg --dearmor \
  | sudo tee /etc/apt/trusted.gpg.d/adoptium.gpg >/dev/null

echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" \
  | sudo tee /etc/apt/sources.list.d/adoptium.list >/dev/null

sudo apt update
sudo apt install -y temurin-25-jdk
java -version
```

### CodeDeploy Agent

개발·운영 EC2 모두 설치한다.

```bash
cd /tmp
wget -q \
  https://aws-codedeploy-ap-northeast-2.s3.ap-northeast-2.amazonaws.com/latestv2/install \
  -O codedeploy-install
chmod +x codedeploy-install
sudo ./codedeploy-install auto

sudo systemctl enable --now codedeploy-agent
sudo systemctl status codedeploy-agent --no-pager
sudo /opt/codedeploy-agent/bin/codedeploy-agent --version
```

Agent는 outbound HTTPS 443으로 AWS에 연결한다. CodeDeploy를 위해 별도의 inbound port를 열지 않는다.

운영 EC2가 private subnet에 있다면 NAT 또는 회사가 제공한 CodeDeploy·S3 접근 경로가 있어야 한다. outbound 443이나 S3 다운로드가 막히면 Agent가 배포를 받을 수 없다.

### 개발 EC2의 Docker

운영 EC2는 RDS를 사용하므로 Docker가 필요하지 않다. 개발 EC2에서만 설치한다.

```bash
sudo apt update
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker version
sudo docker compose version
```

기존 Docker 관련 패키지가 설치된 서버라면 충돌 패키지를 먼저 제거해야 한다. 제거 대상은 Docker 공식 설치 문서에서 확인한다. 배포 hook은 root로 Docker를 실행하므로 `ubuntu` 사용자를 docker group에 추가할 필요가 없다.

## 서버 환경변수 파일

비밀값은 GitHub, CodeBuild, CodePipeline, S3 artifact, CodeDeploy revision에 넣지 않는다. 각 EC2에 다음 파일로 한 번만 저장한다.

```text
/etc/chalkak/application.env
owner: root:root
mode: 600
```

파일을 만든다.

```bash
sudo install -d -o root -g root -m 0700 /etc/chalkak
sudo install -o root -g root -m 0600 /dev/null /etc/chalkak/application.env
sudoedit /etc/chalkak/application.env
sudo chown root:root /etc/chalkak/application.env
sudo chmod 600 /etc/chalkak/application.env
sudo stat /etc/chalkak/application.env
```

SSM console 권한이 없다면 개발 EC2는 허용된 SSH 경로로 접속해 위 작업을 수행한다. 운영 EC2를 private subnet에 둘 경우에는 public 접근을 제거하기 전에 회사가 허용한 bastion, SSM 또는 다른 관리 접속 경로를 먼저 확보해야 한다. 이를 해결하기 위해 `.env`를 CodeDeploy artifact에 넣어서는 안 된다.

내용을 출력하는 `cat` 명령은 사용하지 않는다. 예시는 다음 파일을 참고하되 실제 비밀번호를 저장소에 입력하지 않는다.

- 개발: `backend/deploy/examples/application.dev.env.example`
- 운영: `backend/deploy/examples/application.prod.env.example`

다음 값을 특히 주의한다.

- 개발 `DB_HOST`는 `127.0.0.1`, 운영 `DB_HOST`는 RDS endpoint를 사용한다.
- `GOOGLE_OIDC_CLIENT_ID`에는 모바일이 ID Token 발급 시 사용하는 백엔드용 Google Web Client ID를 설정한다.
- `KAKAO_OIDC_APP_KEY`에는 Android와 iOS Kakao SDK가 공통으로 사용하는 네이티브 앱 키를 설정한다.
- `SOCIAL_SIGNUP_TOKEN_SECRET`는 `openssl rand -hex 32`로 생성하고 dev·prod에서 서로 다른 값을 사용한다. 값을 바꾸면 기존 회원가입 토큰이 무효화된다.
- `ACCESS_TOKEN_SECRET`는 `openssl rand -hex 32`로 생성하고 dev·prod에서 서로 다른 값을 사용한다. `SOCIAL_SIGNUP_TOKEN_SECRET`와도 반드시 다른 값을 쓴다. 값을 바꾸면 발급된 액세스 토큰이 모두 무효화되어 사용자가 다시 로그인해야 한다.
- `ACCESS_TOKEN_EXPIRATION`은 필수 값이며 생략하면 애플리케이션이 기동되지 않는다. `30m`, `PT1H` 형식을 모두 받는다.
- `DB_PASSWORD`는 공백, 따옴표, `#`, `$`가 없는 URL-safe 문자로 20자 이상 생성한다.
- `IMAGE_PROCESSOR_CALLBACK_SECRET`는 같은 문자 규칙으로 32자 이상 생성한다. Lambda의
  `IMAGE_PROCESSING_API_SECRET`에는 이 값과 동일한 값을 설정한다.
- Lambda의 `DEV_BACKEND_IMAGE_PROCESSING_API_BASE_URL`과
  `PROD_BACKEND_IMAGE_PROCESSING_API_BASE_URL`에는 `/internal/v1`까지 포함한다.

환경변수를 변경한 뒤에는 다음 명령으로 적용한다.

```bash
sudo systemctl restart chalkak-backend.service
```

개발 PostgreSQL volume을 만든 뒤 `DB_PASSWORD`만 수정해도 DB 계정 비밀번호는 변경되지 않는다. 먼저 PostgreSQL role password를 변경하고 파일 값을 동일하게 수정한다.

## EC2 instance role과 S3 확인

두 EC2에 회사 제공 `ec2-project` instance profile이 연결되어 있어야 한다. CodeDeploy Agent가 다음 bucket의 pipeline artifact를 내려받을 수 있어야 한다.

```text
s3://techcourse-project-2026-artifacts
```

백엔드는 이미지 처리 Lambda에 최종 결과용 presigned PUT URL을 발급한다. 따라서 각 환경의
instance role에는 다음 경로에 대한 `s3:PutObject` 권한이 필요하다. dev 역할은 dev 경로만,
prod 역할은 prod 경로만 허용하는 것이 원칙이다.

```text
arn:aws:s3:::techcourse-project-2026/chalkak/signatures/{environment}/original/*
arn:aws:s3:::techcourse-project-2026/chalkak/signatures/{environment}/thumbnail/*
arn:aws:s3:::techcourse-project-2026/chalkak/posts/{environment}/original/*
arn:aws:s3:::techcourse-project-2026/chalkak/posts/{environment}/thumbnail/*
```

presigned URL 생성은 로컬 서명 연산이라 위 권한이 없어도 백엔드는 URL을 200으로 반환한다.
권한 누락은 Lambda가 URL로 PUT할 때 S3의 403으로 처음 드러나므로, 백엔드 로그가 아니라
Lambda의 `image_processing_failed` 로그와 SQS 재시도를 확인한다. 재시도 상한에 도달하면 Lambda가
실패 콜백으로 처리 상태를 닫는다.

배포 순서는 다음과 같이 고정한다.

1. dev·prod EC2 instance role에 각 환경 최종 경로의 `s3:PutObject`를 부여한다.
2. `upload-urls` 엔드포인트가 포함된 백엔드를 배포한다.
3. 새 이미지 처리 API 환경 변수와 코드가 포함된 Lambda를 배포한다.

Lambda를 먼저 배포하면 아직 없는 `upload-urls` 엔드포인트가 404를 반환해 모든 이미지 처리가
SQS 재시도로 들어간다. 백엔드 배포 후 URL 발급 200, S3 PUT 200 또는 검증된 412, 완료 콜백
204를 순서대로 확인한다.

Lambda role에는 staging 객체의 `s3:GetObject`, `s3:DeleteObject`와 사인·포스트 최종 경로의
`s3:GetObject`가 필요하다. 최종 경로 읽기는 조건부 PUT이 412를 반환했을 때 기존 객체가 현재
변환 결과와 같은지 검증하는 용도다. 최종 경로의 `s3:PutObject`는 제거한다.

EC2에서 identity를 확인한다.

```bash
aws sts get-caller-identity
```

첫 pipeline artifact가 만들어진 뒤 다운로드가 `AccessDenied`로 실패하면 `ec2-project`의 해당 bucket `s3:GetObject` 권한 문제다. 직접 IAM을 변경하지 않고 `#8기-기술-검토`에 문의한다.

서버 준비가 끝나면 [CI/CD 파이프라인 구축](pipeline-setup.md)을 진행한다.

## 공식 문서

- [CodeDeploy Agent 지원 운영체제와 통신 방식](https://docs.aws.amazon.com/codedeploy/latest/userguide/codedeploy-agent.html)
- [Ubuntu에 CodeDeploy Agent 설치](https://docs.aws.amazon.com/codedeploy/latest/userguide/codedeploy-agent-operations-install-ubuntu.html)
- [Docker Engine Ubuntu 설치](https://docs.docker.com/engine/install/ubuntu/)
- [Eclipse Temurin Linux 설치](https://adoptium.net/installation/linux/)
