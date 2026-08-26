# 배포 운영 런북

이 문서는 GitHub 병합부터 개발·운영 배포 확인, Flyway 운영, 장애 대응까지 반복적으로 수행하는 절차를 설명한다. 최초 구축은 [서버 구축](infrastructure-setup.md)과 [CI/CD 파이프라인 구축](pipeline-setup.md)을 참고한다.

## CI/CD 역할 분리

### GitHub Actions

`.github/workflows/backend-ci.yml`은 다음 경우에 실행한다.

- `be/develop` 또는 `main`을 대상으로 한 Pull Request
- GitHub Actions 화면에서 수동 실행

먼저 변경 파일을 확인한다. `backend/**`, 루트 `buildspec.yml`, 백엔드 workflow가 변경된 경우에만 PostgreSQL 18.4 service container로 Flyway 계약, 테스트, JAR 빌드 가능 여부를 확인한다. Android-only PR은 백엔드 테스트를 생략하지만 필수 `Backend CI` check는 성공한다. AWS 배포 권한과 애플리케이션 비밀값은 사용하지 않는다.

### CodeBuild

루트 `buildspec.yml`은 다음을 수행한다.

1. Corretto 25로 Gradle을 실행한다.
2. PostgreSQL 18.4 Docker container를 기동한다.
3. 테스트와 `bootJar`를 실행한다.
4. `application.jar`, `appspec.yml`, 배포 script, systemd unit을 하나의 output artifact로 만든다.

Gradle toolchain vendor는 Adoptium이므로 실제 compilation toolchain은 Foojay resolver가 Temurin 25를 준비한다.

### CodeDeploy

동일한 revision을 개발·운영에 사용한다. 서버의 `/etc/chalkak/application.env`에 지정된 `SPRING_PROFILES_ACTIVE`로 환경을 구분한다.

- `dev`: Docker PostgreSQL 기동 및 배포 직전 `pg_dump` 수행
- `prod`: Docker를 사용하지 않고 RDS에 연결

## GitHub Ruleset

CodePipeline은 merge 여부가 아니라 대상 branch의 새 commit을 감지한다. 직접 push도 배포를 실행하므로 `be/develop`과 `main` 직접 push를 차단한다.

```text
GitHub repository
→ Settings
→ Rules → Rulesets
→ New branch ruleset
```

두 branch에 다음 규칙을 설정한다.

- Require a pull request before merging
- Require status checks to pass before merging
- 필수 check: `Backend CI` (`Backend PR CI` workflow의 최종 gate job)
- Require branches to be up to date before merging
- Block force pushes
- Restrict deletions
- 가능하면 Require conversation resolution before merging

`Require branches to be up to date before merging`을 활성화해야 대상 branch에 다른 Flyway migration이 먼저 병합된 경우 남은 PR을 최신 기준으로 다시 검사할 수 있다.

Status check가 선택 목록에 없다면 `Backend PR CI`를 한 번 실행한 뒤 다시 설정한다.

## 개발 배포

1. Pull Request를 `be/develop`에 병합한다.
2. `Backend PR CI / Backend CI`가 성공했는지 확인한다. 백엔드 변경 PR에서는 `Verify backend`도 성공해야 한다.
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

## 운영 배포

운영 EC2, RDS, ALB target group, `/etc/chalkak/application.env`가 준비된 뒤 진행한다.

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

## Flyway 배포 규칙

- Migration은 `src/main/resources/db/migration/VyyyyMMddHHmm__description.sql`에 추가한다.
- PR에서 추가한 migration 버전은 대상 branch의 마지막 migration 버전보다 커야 한다.
- 공유 DB에 한 번 적용된 migration은 수정·삭제·rename하지 않고 새 migration으로 roll forward한다.
- Entity 변경과 migration을 같은 PR에 포함한다.
- Column 삭제, rename, `NOT NULL` 강제는 expand-contract 방식으로 여러 배포에 나눈다.
- Spring Boot 시작 중 Flyway가 migration을 실행한다.
- Migration 또는 Hibernate validation이 실패하면 health check와 CodeDeploy도 실패한다.
- CodeDeploy가 이전 JAR을 다시 배포해도 성공한 DB migration은 자동으로 되돌아가지 않는다.

Pull Request CI는 `scripts/check_flyway_migrations.sh`로 다음 계약을 검사한다.

- 새 migration 버전이 대상 branch의 마지막 버전보다 큰지 확인
- PR 안에서 migration 버전이 중복되지 않는지 확인
- 대상 branch에 이미 존재하는 migration을 수정·삭제·rename하지 않았는지 확인
- 파일명이 `VyyyyMMddHHmm__description.sql` 형식인지 확인

대상 branch에 더 높은 버전이 먼저 병합되어 검사가 실패하면, 아직 공유 DB에 적용되지 않은 PR의 새 migration 파일명만 현재 시각 기준으로 변경하고 다시 push한다.

개발 배포는 Flyway 실행 전에 `/opt/chalkak/backups`에 `pg_dump`를 만들고 7일이 지난 자동 백업을 삭제한다. 개발 DB와 백업이 같은 EC2 disk에 있으므로 중요한 데이터는 별도로 백업한다.

운영은 RDS automated backup과 point-in-time recovery를 활성화하고 위험한 migration 전에는 별도 snapshot을 만든다.

## 장애 확인 순서

### CodePipeline과 CodeBuild

```text
CodePipeline execution
→ 실패 stage
→ Details
→ CodeBuild build details
→ CloudWatch log stream
```

로그 그룹은 `/aws/codebuild/project-2026`이다.

### CodeDeploy Agent

```bash
sudo systemctl status codedeploy-agent --no-pager
sudo tail -n 200 /var/log/aws/codedeploy-agent/codedeploy-agent.log
```

### 개발 PostgreSQL container

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

## 이미지 처리 Lambda

이미지 처리는 EC2 백엔드와 분리한 `chalkak-image-processor` Lambda가 담당한다. ECR은 사용하지 않고 CodeBuild에서 Linux 호환 ZIP을 만든다.

- 코드·테스트: `backend/lambda/image-processor`
- Lambda buildspec: `backend/lambda/image-processor/buildspec.yml`
- 함수: `chalkak-image-processor`
- SQS: `chalkak-image-processing`
- 상세 절차: [이미지 처리 Lambda 가이드](../../lambda/image-processor/README.md)

기존 `chalkak-backend-build`와 산출물 형태가 다르므로 Lambda용 CodeBuild project는 `chalkak-image-processor-build`로 분리한다. 두 project는 회사 공용 `codebuild-project` role과 CloudWatch log group을 공유할 수 있다.

## 공식 문서

- [GitHub Ruleset의 필수 상태 검사](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets)
- [Flyway versioned migration](https://documentation.red-gate.com/fd/versioned-migrations-273973333.html)
- [Flyway schema history table](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/flyway-schema-history-table)
- [Flyway out-of-order 설정](https://documentation.red-gate.com/fd/flyway-out-of-order-setting-277579015.html)
- [Lambda Python ZIP 배포](https://docs.aws.amazon.com/lambda/latest/dg/python-package.html)
- [Lambda SQS trigger](https://docs.aws.amazon.com/lambda/latest/dg/services-sqs-configure.html)
- [S3 event notification](https://docs.aws.amazon.com/AmazonS3/latest/userguide/enable-event-notifications.html)
- [CodePipeline V2 Lambda deploy action](https://docs.aws.amazon.com/codepipeline/latest/userguide/action-reference-LambdaDeploy.html)
