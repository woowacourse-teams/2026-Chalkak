# 찰칵 백엔드 AWS CI/CD 구축 가이드

이 문서는 `woowacourse-teams/2026-Chalkak` 저장소의 개발·운영 백엔드를 다음 구조로 배포하는 절차다.

```text
Pull Request
  → GitHub Actions: 테스트와 bootJar 검증
  → be/develop 또는 main 병합
  → CodePipeline: 대상 브랜치 변경 감지
  → CodeBuild: 테스트, JAR 빌드, CodeDeploy revision 생성
  → techcourse-project-2026-artifacts
  → CodeDeploy: EC2에 In-place 배포
  → Spring Boot 시작
  → Flyway migration
  → /actuator/health 검증
```

개발 파이프라인은 병합 후 자동 배포한다. 운영 파이프라인은 `main` 병합 후 빌드까지 자동으로 실행하고, Manual approval을 통과한 뒤 배포하는 구성을 권장한다.

## 1. 환경별 구성

| 항목 | 개발 | 운영 |
| --- | --- | --- |
| GitHub branch | `be/develop` | `main` |
| Pipeline | `chalkak-backend-dev-pipeline` | `chalkak-prod-pipeline` |
| CodeDeploy application | `chalkak-dev-backend` | `chalkak-prod-backend` |
| Deployment group | `chalkak-dev-backend-dg` | `chalkak-prod-backend-dg` |
| EC2 tag | `Name=chalkak-dev-api` | `Name=chalkak-prod-api` |
| Spring profile | `dev` | `prod` |
| Database | 같은 EC2의 Docker PostgreSQL | RDS PostgreSQL |
| Load balancer | 사용하지 않음 | 기존 ALB target group 연결 |
| 배포 승인 | 자동 | Manual approval 권장 |

CodeBuild project는 환경 비밀값을 사용하지 않고 동일한 JAR을 만들기 때문에 다음 하나를 두 파이프라인에서 공유한다.

```text
chalkak-backend-build
```

## 2. 회사 공용 리소스

서울 Region(`ap-northeast-2`)에서 다음 회사 제공 리소스만 사용한다.

| 서비스 | 회사 제공 값 |
| --- | --- |
| Artifact bucket | `techcourse-project-2026-artifacts` |
| CodeBuild role | `arn:aws:iam::843255971531:role/codebuild-project` |
| CodeDeploy role | `arn:aws:iam::843255971531:role/codedeploy-project` |
| CodePipeline role | `arn:aws:iam::843255971531:role/codepipeline-project` |
| CodeBuild log group | `/aws/codebuild/project-2026` |
| Pipeline source | GitHub (Version 1) |

태그를 지원하는 리소스에는 다음을 입력한다.

```text
Service=techcourse
Role=techcourse-etc
ProjectTeam=chalkak
```

다른 팀의 IAM role, pipeline, build project, deployment group, S3 prefix를 열거나 수정하지 않는다. 삭제 권한이 제한된 공유 계정이므로 테스트용 리소스를 임의로 만들지 않는다. 권한이나 삭제가 필요한 문제는 `#8기-기술-검토`에 문의한다.

## 3. 저장소에 반영된 역할 분리

### GitHub Actions

`.github/workflows/backend-ci.yml`은 다음 경우에 실행한다.

- `be/develop` 또는 `main`을 대상으로 한 Pull Request
- GitHub Actions 화면에서 수동 실행

먼저 변경 파일을 확인하고 `backend/**`, 루트 `buildspec.yml`, 백엔드 workflow가 변경된 경우에만 PostgreSQL 18.4 서비스 컨테이너로 테스트하고 JAR 빌드 가능 여부까지 확인한다. Android-only PR은 백엔드 테스트를 생략하지만 필수 `Backend CI` check는 성공한다. AWS 배포 권한과 애플리케이션 비밀값은 사용하지 않는다.

### CodeBuild

저장소 루트의 `buildspec.yml`이 다음을 수행한다.

1. Corretto 25로 Gradle을 실행한다.
2. PostgreSQL 18.4 Docker container를 기동한다.
3. 테스트와 `bootJar`를 실행한다.
4. `application.jar`, `appspec.yml`, 배포 스크립트, systemd unit을 하나의 output artifact로 만든다.

Gradle toolchain의 vendor는 Adoptium으로 유지되어 있어 실제 compilation toolchain은 Foojay resolver가 Temurin 25를 준비한다.

### CodeDeploy

동일한 revision을 개발·운영에 사용할 수 있다. 서버의 `/etc/chalkak/application.env`에 지정된 `SPRING_PROFILES_ACTIVE`로 환경을 구분한다.

- `dev`: Docker PostgreSQL 기동 및 배포 직전 `pg_dump` 수행
- `prod`: Docker를 사용하지 않고 RDS에 연결

## 4. GitHub Branch protection

CodePipeline은 merge 여부가 아니라 대상 branch의 새 commit을 감지한다. 직접 push도 배포를 실행하므로 GitHub에서 직접 push를 차단한다.

```text
GitHub repository
→ Settings
→ Branches 또는 Rules → Rulesets
→ New branch ruleset
```

`be/develop`과 `main`을 대상으로 다음 규칙을 설정한다.

- Require a pull request before merging
- Require status checks to pass before merging
- 필수 check: `Backend CI` (`Backend PR CI` workflow의 최종 gate job)
- Block force pushes
- Restrict deletions
- 가능하면 Require conversation resolution before merging

status check가 선택 목록에 없다면 `Backend PR CI`를 한 번 실행한 뒤 다시 설정한다.

## 5. EC2 공통 준비

### 5.1 운영체제 주의

AWS CodeDeploy agent의 공식 테스트 대상 Ubuntu는 현재 22.04 LTS까지다. Ubuntu 26에서도 설치를 시도할 수 있지만 공식 지원 대상이 아니다. 운영 서버는 Ubuntu 22.04 LTS 또는 Amazon Linux 2023을 권장한다.

### 5.2 Java 25와 기본 패키지

Ubuntu에서 다음을 실행한다.

```bash
sudo apt update
sudo apt install -y ca-certificates curl wget gpg unzip ruby-full gzip

sudo apt install -y wget apt-transport-https gpg
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
  | gpg --dearmor \
  | sudo tee /etc/apt/trusted.gpg.d/adoptium.gpg >/dev/null

echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" \
  | sudo tee /etc/apt/sources.list.d/adoptium.list >/dev/null

sudo apt update
sudo apt install -y temurin-25-jdk
java -version
```

### 5.3 CodeDeploy agent

개발·운영 EC2 모두 설치한다.

```bash
cd /tmp
wget -q \
  https://aws-codedeploy-ap-northeast-2.s3.ap-northeast-2.amazonaws.com/latest/install \
  -O codedeploy-install
chmod +x codedeploy-install
sudo ./codedeploy-install auto

sudo systemctl enable --now codedeploy-agent
sudo systemctl status codedeploy-agent --no-pager
```

agent는 outbound HTTPS 443으로 AWS에 연결한다. CodeDeploy를 위해 별도의 inbound port를 열지 않는다.

운영 EC2가 private subnet에 있다면 NAT 또는 회사가 제공한 CodeDeploy·S3 접근 경로가 있어야 한다. outbound 443이나 S3 다운로드가 막히면 agent가 배포를 받을 수 없다.

### 5.4 개발 EC2에 Docker 설치

운영 EC2는 RDS를 사용하므로 Docker가 필요하지 않다. 개발 EC2에서만 실행한다.

```bash
sudo apt update
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${UBUNTU_CODENAME:-$(. /etc/os-release && echo "$VERSION_CODENAME")} stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker version
sudo docker compose version
```

배포 hook은 root로 Docker를 실행하므로 `ubuntu` 사용자를 docker group에 추가할 필요가 없다.

## 6. 서버 환경변수 파일

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

개발 환경의 `DB_HOST`는 `127.0.0.1`, 운영 환경은 RDS endpoint여야 한다. 배포 script가 이 값을 검증한다.
`GOOGLE_OIDC_CLIENT_ID`에는 모바일이 ID Token 발급 시 사용하는 백엔드용 Google Web Client ID를 설정한다.

`DB_PASSWORD`는 Docker Compose와 systemd가 같은 파일을 안전하게 읽을 수 있도록 공백, 따옴표, `#`, `$`가 없는 URL-safe 문자로 20자 이상 생성한다.
`IMAGE_PROCESSOR_CALLBACK_SECRET`는 같은 문자 규칙으로 32자 이상 생성하고,
dev·prod 백엔드와 Lambda 환경 변수에 동일한 값을 설정한다.
Lambda에는 추가로 `DEV_BACKEND_CALLBACK_URL`과 `PROD_BACKEND_CALLBACK_URL`을
`/internal/v1/signature-processing`까지 포함해 설정한다. 실제 값을 배포 artifact에
포함하지 않는다.

환경변수를 변경한 뒤에는 다음 명령으로 적용한다.

```bash
sudo systemctl restart chalkak-backend.service
```

개발 PostgreSQL volume을 만든 뒤 `DB_PASSWORD`만 수정해도 DB 계정 비밀번호는 변경되지 않는다. 먼저 PostgreSQL의 role password를 변경하고 파일 값을 동일하게 수정한다.

## 7. EC2 instance role과 S3 확인

두 EC2에 회사 제공 `ec2-project` instance profile이 연결되어 있어야 한다. CodeDeploy agent가 다음 bucket의 pipeline artifact를 내려받을 수 있어야 한다.

```text
s3://techcourse-project-2026-artifacts
```

EC2에서 identity를 확인한다.

```bash
aws sts get-caller-identity
```

첫 pipeline artifact가 만들어진 뒤 다운로드가 `AccessDenied`로 실패하면 `ec2-project`의 해당 bucket `s3:GetObject` 권한 문제다. 직접 IAM을 변경하지 않고 `#8기-기술-검토`에 문의한다.

## 8. CodeBuild project 설정값 준비

CodeBuild project는 개발 CodePipeline을 만들면서 Build stage의 `Create project` 버튼으로 생성하는 것이 가장 확실하다. CodeBuild console에서 단독으로 생성하면 Source provider에 CodePipeline이 표시되지 않을 수 있다. 이 절에서는 입력값만 확인하고 실제 생성은 10절에서 진행한다.

```text
AWS Console → CodePipeline → chalkak-backend-dev-pipeline 편집
→ Build stage
→ Build provider: AWS CodeBuild
→ Create project
```

### Project configuration

| 항목 | 값 |
| --- | --- |
| Project name | `chalkak-backend-build` |
| Tags | 회사 필수 태그 3개 |

### Source

| 항목 | 값 |
| --- | --- |
| Source provider | `CodePipeline` |

Source는 pipeline wizard가 `CodePipeline`으로 연결한다. GitHub를 CodeBuild source로 다시 연결하지 않는다.

### Environment

| 항목 | 값 |
| --- | --- |
| Provisioning model | On-demand |
| Environment image | Managed image |
| Operating system | Ubuntu |
| Runtime | Standard |
| Image | `aws/codebuild/standard:7.0` |
| Compute | 가장 작은 허용 사양부터 시작 |
| Privileged | 활성화 |
| Service role | Existing service role |
| Role ARN | `arn:aws:iam::843255971531:role/codebuild-project` |

Privileged mode는 테스트용 PostgreSQL Docker container를 실행하기 위해 필요하다.

별도 지침이 없다면 CodeBuild를 VPC에 연결하지 않는다. VPC를 선택하면서 NAT가 없으면 Gradle dependency, Temurin toolchain, PostgreSQL image를 내려받지 못한다.

### Buildspec

| 항목 | 값 |
| --- | --- |
| Build specifications | Use a buildspec file |
| Buildspec name | `buildspec.yml` |

### Artifacts

Pipeline에서 호출되는 project이므로 artifact type은 `CodePipeline`을 사용한다. CodeBuild에서 별도로 S3 upload 경로를 만들지 않는다.

### Logs

| 항목 | 값 |
| --- | --- |
| CloudWatch logs | 활성화 |
| Group name | `/aws/codebuild/project-2026` |
| Stream name | `chalkak-backend` |

Cache 권한이 허용된다면 S3 cache를 다음 prefix로 설정할 수 있다. 권한 오류가 나면 cache 없이 시작한다.

```text
techcourse-project-2026-artifacts/chalkak/cache/codebuild
```

## 9. CodeDeploy application과 group

### 9.1 개발

```text
AWS Console
→ CodeDeploy
→ Applications
→ Create application
```

| 항목 | 값 |
| --- | --- |
| Application name | `chalkak-dev-backend` |
| Compute platform | EC2/On-premises |

application 안에서 `Create deployment group`을 선택한다.

| 항목 | 값 |
| --- | --- |
| Deployment group name | `chalkak-dev-backend-dg` |
| Service role | `arn:aws:iam::843255971531:role/codedeploy-project` |
| Deployment type | In-place |
| Environment | Amazon EC2 instances |
| EC2 tag | `Name=chalkak-dev-api` |
| Agent installation | Never 또는 자동 설치 안 함 |
| Deployment configuration | `CodeDeployDefault.OneAtATime` |
| Load balancer | 비활성화 |
| Automatic rollback | 배포 실패 시 활성화 |

target instance가 반드시 개발 EC2 한 대만 표시되는지 확인한다.

### 9.2 운영

별도 application을 만든다.

| 항목 | 값 |
| --- | --- |
| Application name | `chalkak-prod-backend` |
| Deployment group name | `chalkak-prod-backend-dg` |
| Service role | `arn:aws:iam::843255971531:role/codedeploy-project` |
| Deployment type | In-place |
| EC2 tag | `Name=chalkak-prod-api` |
| Deployment configuration | `CodeDeployDefault.OneAtATime` |
| Load balancer | 기존 운영 ALB target group 연결 |
| Automatic rollback | 배포 실패 시 활성화 |

다른 팀 instance가 deployment target에 포함되지 않았는지 생성 직전에 다시 확인한다.

## 10. 개발 CodePipeline 생성

```text
AWS Console
→ CodePipeline
→ Pipelines
→ Create pipeline
```

### Pipeline settings

| 항목 | 값 |
| --- | --- |
| Pipeline name | `chalkak-backend-dev-pipeline` |
| Pipeline type | V2 |
| Service role | Existing service role |
| Role ARN | `arn:aws:iam::843255971531:role/codepipeline-project` |
| Artifact store | Custom location |
| Bucket | `techcourse-project-2026-artifacts` |

태그를 입력할 수 있으면 회사 필수 태그를 설정한다.

### Source stage

| 항목 | 값 |
| --- | --- |
| Source provider | GitHub (Version 1) |
| GitHub owner | `woowacourse-teams` |
| Repository | `2026-Chalkak` |
| Branch | `be/develop` |
| Change detection | 활성화 |
| Output artifact | `DevSource` |

GitHub 인증 화면이 나오면 팀 repository에 접근 가능한 계정으로 승인한다. 다른 repository 권한을 추가하지 않는다.

GitHub v1에서 polling과 webhook을 동시에 활성화하면 pipeline이 두 번 실행될 수 있다. console이 webhook을 만들었다면 polling은 끈다. webhook 생성 권한이 없다면 polling을 사용한다.

이 저장소는 `backend/`와 `client/`를 함께 쓰는 monorepo지만 GitHub (Version 1) source는 branch의 새 commit을 기준으로 동작한다. 따라서 `client/**`만 변경한 commit이 `be/develop` 또는 `main`에 병합되어도 해당 pipeline은 시작될 수 있다. 다만 루트 `buildspec.yml`이 Build 단계에서 먼저 `cd backend`를 실행하므로 Android project를 테스트하거나 빌드하지 않고 백엔드 artifact만 만든다.

Android-only 병합에서 백엔드 pipeline 자체가 시작되지 않게 하려면 file-path trigger filter를 지원하는 connection 기반 GitHub source로 전환하거나 repository를 분리해야 한다. 회사 지침이 GitHub (Version 1)을 요구하므로 임의로 전환하지 말고, 필요하면 `#8기-기술-검토`에 먼저 문의한다.

### Build stage

| 항목 | 값 |
| --- | --- |
| Build provider | AWS CodeBuild |
| Project | `chalkak-backend-build` 또는 `Create project`로 8절 설정 적용 |
| Input artifact | `DevSource` |
| Output artifact | `DevBuild` |

### Deploy stage

| 항목 | 값 |
| --- | --- |
| Deploy provider | AWS CodeDeploy |
| Application | `chalkak-dev-backend` |
| Deployment group | `chalkak-dev-backend-dg` |
| Input artifact | `DevBuild` |

Pipeline을 생성하면 첫 실행이 자동으로 시작될 수 있다. 서버 환경파일과 CodeDeploy agent 준비가 끝나기 전이라면 즉시 실행을 중지한다.

## 11. 운영 CodePipeline 생성

개발 pipeline과 동일하게 만들되 다음 값을 바꾼다.

| 항목 | 값 |
| --- | --- |
| Pipeline name | `chalkak-prod-pipeline` |
| Source branch | `main` |
| Source artifact | `ProdSource` |
| Build artifact | `ProdBuild` |
| CodeDeploy application | `chalkak-prod-backend` |
| Deployment group | `chalkak-prod-backend-dg` |

Build와 Deploy 사이에 stage를 추가한다.

```text
Add stage
→ Stage name: ApproveProduction
→ Add action group
→ Action provider: Manual approval
→ Action name: ApproveProductionDeploy
```

SNS topic은 회사 정책에 따라 사용할 수 있을 때만 연결한다. 없어도 console에서 승인할 수 있다.

완전 자동 운영 배포가 필요하면 approval stage를 생략할 수 있지만, 초기 운영에서는 유지한다.

## 12. 첫 개발 배포

다음 순서로 실행한다.

1. 이 CI/CD 변경사항을 Pull Request로 `be/develop`에 병합한다.
2. GitHub Actions의 `Backend PR CI / Backend CI`가 성공하는지 확인한다. 백엔드 변경 PR에서는 `Verify backend`도 성공해야 한다.
3. `chalkak-backend-dev-pipeline` 실행이 시작되는지 확인한다.
4. Source와 Build stage가 성공하는지 확인한다.
5. CodeDeploy deployment가 `Succeeded`인지 확인한다.
6. 개발 EC2에서 다음을 확인한다.

```bash
sudo systemctl status codedeploy-agent --no-pager
sudo systemctl status chalkak-backend.service --no-pager
sudo docker ps --filter name=chalkak-dev-postgres
curl --fail http://127.0.0.1:8080/actuator/health
```

외부에서는 EC2 public address와 API port 또는 설정한 reverse proxy를 통해 확인한다.

## 13. 첫 운영 배포

운영 EC2, RDS, ALB와 target group, `/etc/chalkak/application.env`가 준비된 뒤 진행한다.

1. `be/develop`에서 충분히 검증한 PR을 `main`에 병합한다.
2. GitHub Actions가 성공했는지 확인한다.
3. `chalkak-prod-pipeline`의 Source와 Build가 성공했는지 확인한다.
4. Manual approval에서 commit과 변경사항을 확인한다.
5. 승인 후 CodeDeploy와 ALB target health를 확인한다.
6. 운영 EC2 내부와 외부 endpoint를 모두 확인한다.

```bash
sudo systemctl status chalkak-backend.service --no-pager
curl --fail http://127.0.0.1:8080/actuator/health
```

## 14. Flyway 배포 규칙

- migration은 `src/main/resources/db/migration/VyyyyMMddHHmm__description.sql`에 추가한다.
- 공유 DB에 한 번 적용된 migration을 수정하거나 rename하지 않는다.
- Entity 변경과 migration을 같은 PR에 포함한다.
- column 삭제, rename, `NOT NULL` 강제는 expand-contract 방식으로 여러 배포에 나눈다.
- Spring Boot 시작 중 Flyway가 migration을 실행한다.
- migration이나 Hibernate validation 실패 시 health check가 실패하고 CodeDeploy도 실패한다.
- CodeDeploy가 이전 JAR을 다시 배포해도 이미 성공한 DB migration은 자동으로 되돌아가지 않는다.

개발 배포는 Flyway 실행 전에 `/opt/chalkak/backups`에 `pg_dump`를 만들고 7일 지난 자동 백업을 삭제한다. 개발 DB와 백업이 같은 EC2 disk에 있으므로 중요한 데이터는 별도 백업해야 한다.

운영은 RDS automated backup과 point-in-time recovery를 활성화하고, 위험한 migration 전에는 별도 snapshot을 만든다.

## 15. 장애 확인 순서

### CodePipeline과 CodeBuild

```text
CodePipeline execution
→ 실패 stage
→ Details
→ CodeBuild build details
→ CloudWatch log stream
```

로그 그룹:

```text
/aws/codebuild/project-2026
```

### CodeDeploy agent

```bash
sudo systemctl status codedeploy-agent --no-pager
sudo tail -n 200 /var/log/aws/codedeploy-agent/codedeploy-agent.log
```

### PostgreSQL 개발 container

```bash
sudo docker ps -a --filter name=chalkak-dev-postgres
sudo docker logs --tail 200 chalkak-dev-postgres
```

### Spring Boot와 Flyway

```bash
sudo systemctl status chalkak-backend.service --no-pager
sudo journalctl -u chalkak-backend.service -n 200 --no-pager
```

환경변수 문제를 확인할 때도 `/etc/chalkak/application.env` 전체 내용을 로그나 채팅에 붙이지 않는다.

## 16. 사인 이미지 Lambda

사인 이미지는 EC2 백엔드 배포와 분리한 `chalkak-image-processor` Lambda가
처리한다. ECR은 사용하지 않고 CodeBuild에서 Linux 호환 ZIP을 만든다.

- 코드·테스트: `backend/lambda/image-processor`
- Lambda buildspec: `backend/lambda/image-processor/buildspec.yml`
- 함수: `chalkak-image-processor`
- SQS: `chalkak-image-processing`
- 상세 생성·권한·테스트 절차: `backend/lambda/image-processor/README.md`

기존 `chalkak-backend-build`와 산출물 형태가 다르므로 Lambda용 CodeBuild project를
`chalkak-image-processor-build`로 분리한다. 두 project는 회사 공용
`codebuild-project` role과 CloudWatch log group을 공유할 수 있다.

## 17. 공식 문서

- [GitHub protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches)
- [CodePipeline GitHub v1 source](https://docs.aws.amazon.com/codepipeline/latest/userguide/appendix-github-oauth.html)
- [CodePipeline artifacts](https://docs.aws.amazon.com/codepipeline/latest/userguide/welcome-introducing-artifacts.html)
- [CodeBuild runtime versions](https://docs.aws.amazon.com/codebuild/latest/userguide/available-runtimes.html)
- [CodeBuild buildspec](https://docs.aws.amazon.com/codebuild/latest/userguide/build-spec-ref.html)
- [CodeDeploy AppSpec](https://docs.aws.amazon.com/codedeploy/latest/userguide/application-specification-files.html)
- [Lambda Python ZIP 배포](https://docs.aws.amazon.com/lambda/latest/dg/python-package.html)
- [Lambda SQS 트리거 설정](https://docs.aws.amazon.com/lambda/latest/dg/services-sqs-configure.html)
- [S3 event notification](https://docs.aws.amazon.com/AmazonS3/latest/userguide/enable-event-notifications.html)
- [CodePipeline V2 Lambda deploy action](https://docs.aws.amazon.com/codepipeline/latest/userguide/action-reference-LambdaDeploy.html)
- [Ubuntu CodeDeploy agent 설치](https://docs.aws.amazon.com/codedeploy/latest/userguide/codedeploy-agent-operations-install-ubuntu.html)
- [Eclipse Temurin Ubuntu 설치](https://adoptium.net/installation/linux/)
