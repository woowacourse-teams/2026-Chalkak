# CI/CD 파이프라인 구축

이 문서는 서버 준비 후 AWS CodeBuild·CodeDeploy·CodePipeline을 구성하는 절차를 설명한다. 먼저 [서버 구축](infrastructure-setup.md)을 완료한다.

## CodeBuild project

CodeBuild project는 개발 CodePipeline을 만들면서 Build stage의 `Create project` 버튼으로 생성하는 것이 가장 확실하다. CodeBuild console에서 단독으로 생성하면 Source provider에 CodePipeline이 표시되지 않을 수 있다.

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

저장소 루트의 `buildspec.yml`은 Corretto 25로 Gradle을 실행하고, PostgreSQL 18.4 container에서 테스트한 뒤 JAR과 CodeDeploy revision을 생성한다.

### Artifacts와 logs

Pipeline에서 호출되는 project이므로 artifact type은 `CodePipeline`을 사용한다. CodeBuild에서 별도의 S3 upload 경로를 만들지 않는다.

| 항목 | 값 |
| --- | --- |
| CloudWatch logs | 활성화 |
| Group name | `/aws/codebuild/project-2026` |
| Stream name | `chalkak-backend` |

Cache 권한이 허용된다면 S3 cache를 다음 prefix로 설정할 수 있다. 권한 오류가 나면 cache 없이 시작한다.

```text
techcourse-project-2026-artifacts/chalkak/cache/codebuild
```

## CodeDeploy application과 deployment group

### 개발

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

Application 안에서 `Create deployment group`을 선택한다.

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

Target instance가 반드시 개발 EC2 한 대만 표시되는지 확인한다.

### 운영

별도 application과 deployment group을 만든다.

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

In-place deployment에 load balancer를 연결하면 CodeDeploy가 배포 중 instance를 target group에서 제외하고 완료 후 다시 등록한다. 다른 팀 instance가 deployment target에 포함되지 않았는지 생성 직전에 다시 확인한다.

CodeDeploy rollback은 DB를 되돌리는 기능이 아니라 이전 application revision을 새 deployment로 다시 배포하는 기능이다. 이미 성공한 Flyway migration은 자동으로 되돌아가지 않는다.

## 개발 CodePipeline

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

AWS는 현재 GitHub App 기반 connection source를 권장하고 OAuth 기반 GitHub Version 1은 권장하지 않는다. 이 프로젝트는 회사 제공 정책이 Version 1을 요구하므로 임의로 바꾸지 않는다. 전환하려면 `#8기-기술-검토`와 먼저 협의한다.

GitHub 인증 화면이 나오면 팀 repository에 접근 가능한 계정으로 승인한다. 다른 repository 권한을 추가하지 않는다.

GitHub Version 1에서 polling과 webhook을 동시에 활성화하면 pipeline이 두 번 실행될 수 있다. Console이 webhook을 만들었다면 polling은 끈다. Webhook 생성 권한이 없다면 polling을 사용한다.

이 저장소는 `backend/`와 `client/`를 함께 쓰는 monorepo지만 GitHub Version 1 source는 branch의 새 commit을 기준으로 동작한다. 따라서 `client/**`만 변경한 commit이 병합되어도 pipeline은 시작될 수 있다. 루트 `buildspec.yml`은 Build 단계에서 `backend`로 이동해 백엔드만 테스트하고 artifact를 만든다.

Android-only 병합에서 pipeline 자체가 시작되지 않게 하려면 file-path trigger filter를 지원하는 connection source로 전환하거나 repository를 분리해야 한다. 회사 지침 변경 없이 임의로 전환하지 않는다.

### Build stage

| 항목 | 값 |
| --- | --- |
| Build provider | AWS CodeBuild |
| Project | `chalkak-backend-build` 또는 `Create project`로 앞의 설정 적용 |
| Input artifact | `DevSource` |
| Output artifact | `DevBuild` |

### Deploy stage

| 항목 | 값 |
| --- | --- |
| Deploy provider | AWS CodeDeploy |
| Application | `chalkak-dev-backend` |
| Deployment group | `chalkak-dev-backend-dg` |
| Input artifact | `DevBuild` |

Pipeline을 생성하면 첫 실행이 자동으로 시작될 수 있다. 서버 환경파일과 CodeDeploy Agent 준비가 끝나기 전이라면 즉시 실행을 중지한다.

## 운영 CodePipeline

개발 pipeline과 동일하게 만들되 다음 값을 바꾼다.

| 항목 | 값 |
| --- | --- |
| Pipeline name | `chalkak-prod-pipeline` |
| Source branch | `main` |
| Source artifact | `ProdSource` |
| Build artifact | `ProdBuild` |
| CodeDeploy application | `chalkak-prod-backend` |
| Deployment group | `chalkak-prod-backend-dg` |

Build와 Deploy 사이에 approval stage를 추가한다.

```text
Add stage
→ Stage name: ApproveProduction
→ Add action group
→ Action provider: Manual approval
→ Action name: ApproveProductionDeploy
```

SNS topic은 회사 정책에 따라 사용할 수 있을 때만 연결한다. 없어도 Console에서 승인할 수 있다. AWS CodePipeline의 manual approval은 7일 안에 승인 또는 거절되지 않으면 실패 처리된다.

완전 자동 운영 배포가 필요하면 approval stage를 생략할 수 있지만, 초기 운영에서는 유지한다.

구축이 끝나면 [배포 운영 런북](operations.md)에 따라 첫 배포를 확인한다.

## 공식 문서

- [CodeBuild EC2 compute image](https://docs.aws.amazon.com/codebuild/latest/userguide/ec2-compute-images.html)
- [CodeBuild 지원 runtime](https://docs.aws.amazon.com/codebuild/latest/userguide/available-runtimes.html)
- [CodeBuild buildspec](https://docs.aws.amazon.com/codebuild/latest/userguide/build-spec-ref.html)
- [CodePipeline artifact](https://docs.aws.amazon.com/codepipeline/latest/userguide/welcome-introducing-artifacts.html)
- [CodePipeline GitHub connection](https://docs.aws.amazon.com/codepipeline/latest/userguide/connections-github.html)
- [CodePipeline GitHub Version 1 source](https://docs.aws.amazon.com/codepipeline/latest/userguide/appendix-github-oauth.html)
- [CodePipeline manual approval](https://docs.aws.amazon.com/codepipeline/latest/userguide/approvals.html)
- [CodeDeploy In-place deployment group](https://docs.aws.amazon.com/codedeploy/latest/userguide/deployment-groups-create-in-place.html)
- [CodeDeploy load balancer](https://docs.aws.amazon.com/codedeploy/latest/userguide/deployment-groups-create-load-balancer.html)
- [CodeDeploy rollback](https://docs.aws.amazon.com/codedeploy/latest/userguide/deployments-rollback-and-redeploy.html)
- [CodeDeploy AppSpec](https://docs.aws.amazon.com/codedeploy/latest/userguide/application-specification-files.html)
