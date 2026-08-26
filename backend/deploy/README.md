# 찰칵 백엔드 배포 가이드

찰칵 백엔드는 GitHub Actions로 Pull Request를 검증하고, AWS CodePipeline·CodeBuild·CodeDeploy로 개발·운영 EC2에 배포한다.

```text
Pull Request
  → GitHub Actions: Flyway 계약, 테스트, bootJar 검증
  → be/develop 또는 main 병합
  → CodePipeline: 대상 브랜치 변경 감지
  → CodeBuild: 테스트, JAR 빌드, CodeDeploy revision 생성
  → techcourse-project-2026-artifacts
  → CodeDeploy: EC2 In-place 배포
  → Spring Boot 시작
  → Flyway migration
  → /actuator/health 검증
```

개발 파이프라인은 병합 후 자동 배포한다. 운영 파이프라인은 `main` 병합 후 빌드까지 자동으로 실행하고, Manual approval을 통과한 뒤 배포한다.

## 문서 안내

| 문서 | 언제 사용하는가 | 주요 내용 |
| --- | --- | --- |
| [서버 구축](docs/infrastructure-setup.md) | EC2를 처음 준비하거나 서버 설정을 변경할 때 | 공용 AWS 리소스, Java, CodeDeploy Agent, Docker, 환경변수, IAM·S3 |
| [CI/CD 파이프라인 구축](docs/pipeline-setup.md) | AWS 파이프라인을 처음 만들거나 재구성할 때 | CodeBuild, CodeDeploy application·group, 개발·운영 CodePipeline |
| [배포 운영 런북](docs/operations.md) | PR 병합, 배포 확인, 장애 대응 시 | GitHub Ruleset, 개발·운영 배포, Flyway 규칙, 장애 확인 |
| [이미지 처리 Lambda](../lambda/image-processor/README.md) | 이미지 처리 Lambda를 구축·배포할 때 | Lambda, SQS, 빌드와 배포 절차 |

## 환경별 구성

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
| 배포 승인 | 자동 | Manual approval |

CodeBuild project는 환경 비밀값을 사용하지 않고 동일한 JAR을 만들기 때문에 개발·운영 파이프라인에서 `chalkak-backend-build` 하나를 공유한다. 동일한 revision을 서버의 `SPRING_PROFILES_ACTIVE`에 따라 `dev` 또는 `prod`로 실행한다.

## 저장소 구성

| 경로 | 역할 |
| --- | --- |
| `.github/workflows/backend-ci.yml` | Pull Request 검증과 최종 `Backend CI` check |
| `buildspec.yml` | CodeBuild 테스트·JAR·배포 artifact 생성 |
| `backend/deploy/appspec.yml` | CodeDeploy lifecycle hook 정의 |
| `backend/deploy/scripts/` | 설정 검증, 서비스 시작·중지·검증 |
| `backend/deploy/systemd/` | 백엔드 systemd unit |
| `backend/deploy/examples/` | 개발·운영 서버 환경변수 계약 예시 |
| `backend/scripts/check_flyway_migrations.sh` | PR의 Flyway migration 계약 검사 |

## 반드시 지킬 원칙

- 비밀값을 GitHub, CodeBuild, CodePipeline, S3 artifact 또는 CodeDeploy revision에 넣지 않는다.
- 서버 비밀값은 각 EC2의 `/etc/chalkak/application.env`에 `root:root`, mode `600`으로 저장한다.
- `be/develop`과 `main` 직접 push를 막고 필수 `Backend CI` check를 통과한 PR만 병합한다.
- 다른 팀의 IAM role, pipeline, build project, deployment group 또는 S3 prefix를 수정하지 않는다.
- 공유 DB에 적용된 Flyway migration은 수정·삭제·rename하지 않는다.
- 운영 배포 전 Manual approval에서 대상 commit과 변경사항을 확인한다.

처음 구축할 때는 [서버 구축](docs/infrastructure-setup.md) → [CI/CD 파이프라인 구축](docs/pipeline-setup.md) → [배포 운영 런북](docs/operations.md) 순서로 진행한다.
