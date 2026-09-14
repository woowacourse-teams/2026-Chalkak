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

| 규칙 | `be/develop` | `main` |
| --- | --- | --- |
| Require a pull request before merging | 승인 2명, 새 commit push 시 승인 무효화, Squash만 허용 | 승인 0명, Squash만 허용 |
| Require status checks to pass | `Backend CI` | `Backend CI`, `Admin Web CI` |
| Require branches to be up to date before merging | 사용 | 사용하지 않음 |
| Block force pushes | 사용 | 사용 |
| Restrict deletions | 사용 | 사용 |
| Bypass list | 비움 | 비움 |

필수 check는 출처를 GitHub Actions로 지정한다. `Backend CI`는 `Backend PR CI`, `Admin Web CI`는 `Admin Web PR CI` workflow의 최종 gate job이다. Status check가 선택 목록에 없다면 해당 workflow를 한 번 실행한 뒤 다시 설정한다.

`be/develop`은 `Require branches to be up to date before merging`을 사용해야 다른 Flyway migration이 먼저 병합된 경우 남은 PR을 최신 기준으로 다시 검사할 수 있다. 다른 PR이 먼저 병합되면 PR 화면의 Update branch로 최신 상태를 반영하고 CI를 다시 통과해야 병합할 수 있다. Update branch는 PR branch에 병합 commit을 추가하므로 기존 승인이 초기화된다. 승인과 병합 사이에 다른 PR이 병합될수록 재승인이 필요해지므로, 승인 조건을 채운 PR은 바로 병합한다.

`main`은 이 규칙을 사용하지 않는다. `main`에는 백엔드와 client가 각자 개발 branch에서 검증을 마친 릴리스 PR만 들어오고, 백엔드 릴리스는 `backend`와 `docs`, client 릴리스는 `client`만 변경하므로 릴리스끼리 같은 코드를 건드리지 않는다. 같은 영역 안의 병합 순서 문제는 각 개발 branch에서 먼저 검증된다.

`main`의 필수 check에 `Client CI gate`를 추가하지 않는다. client CI는 `client/develop` 대상 PR과 push에서만 실행되어 `main` 대상 릴리스 PR에는 보고되지 않는다.

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

### 백엔드 릴리스 branch 준비

`be/develop`을 `main`에 직접 병합하지 않는다. 릴리스를 Squash로 병합해 두 branch 이력이 이어져 있지 않으므로, 이미 반영된 변경도 충돌로 표시된다. `main`에서 릴리스 branch를 만들고 백엔드 경로만 `be/develop` 기준으로 교체한다.

```bash
git fetch origin main be/develop
git switch -c release/backend-YYYY-MM-DD origin/main
git restore --source=origin/be/develop --staged --worktree -- backend docs
```

`git checkout origin/be/develop -- backend docs`는 `be/develop`에서 삭제한 파일을 지우지 않으므로 사용하지 않는다.

루트 `CLAUDE.md`, `AGENTS.md`, `.claude`, `.agents` 하네스 문서는 client 팀과 공유하지 않고 운영 배포에도 포함되지 않으므로 `main`에 반영하지 않는다. `backend` 안의 하네스는 경로 교체에 함께 포함된다.

PR을 만들기 전에 다음을 확인한다.

1. 교체 경로 밖에서 `be/develop`에 추가되거나 변경된 파일을 확인한다. `buildspec.yml`, `.github/workflows/backend-ci.yml`처럼 운영 빌드나 CI에 영향을 주는 파일이 출력되면 릴리스 branch에 반영한다.

   ```bash
   git diff --name-status --diff-filter=AM origin/main origin/be/develop -- . \
     ':!backend' ':!docs' ':!client' \
     ':!CLAUDE.md' ':!AGENTS.md' ':!.claude' ':!.agents'
   ```

   `be/develop`에 없는 `admin-web` 등 `main`에만 있는 파일은 `--diff-filter=AM`으로 제외한다. 따라서 `be/develop`에서 교체 경로 밖의 파일을 삭제한 경우는 출력되지 않으므로 별도로 확인한다.

2. 지난 백엔드 릴리스 이후 `main`에만 반영된 백엔드 수정이 없는지 확인한다. 출력이 있으면 해당 수정이 `be/develop`에도 반영됐는지 확인한다. 반영되지 않았다면 이번 교체로 사라진다.

   ```bash
   last=$(git log -1 --format=%H --grep='^release: 백엔드' origin/main)
   if [ -z "$last" ]; then
     echo "기준 백엔드 릴리스 commit을 찾지 못했습니다. 지난 릴리스 commit SHA를 직접 확인하세요."
   else
     git log --oneline "$last"..origin/main -- backend docs
   fi
   ```

   기준 commit을 찾지 못했다는 메시지가 나오면 검사를 통과한 것으로 보지 않는다. 지난 백엔드 릴리스 PR의 병합 commit SHA를 확인해 `last`에 직접 지정한 뒤 `git log` 명령을 다시 실행한다. `last`가 비어 있으면 `git log`가 `HEAD..origin/main`으로 해석되어, 수정이 있어도 항상 출력이 비기 때문이다.

### 릴리스 PR과 배포

1. 릴리스 branch를 commit하고 `main` 대상 PR을 만든다. commit 메시지와 PR 제목은 모두 `release: 백엔드 `로 시작한다. PR 본문에 지난 릴리스 이후 포함된 PR 목록을 적는다.
2. `Backend CI`와 `Admin Web CI`가 통과한 뒤 Squash and merge로 병합한다. 병합 화면의 commit 제목이 `release: 백엔드 `로 시작하는지 확인한다. PR의 commit이 하나면 GitHub가 PR 제목 대신 해당 commit 메시지를 제목으로 제안하며, 이 제목이 다음 릴리스의 기준 commit 검색에 사용된다.
3. `chalkak-prod-pipeline`의 Source와 Build가 성공했는지 확인한다.
4. Manual approval에서 commit과 변경사항을 확인한다.
5. 승인 후 CodeDeploy와 ALB target health를 확인한다. 운영 EC2가 한 대라 배포 중에는 요청이 실패하므로 승인 시점을 조절한다.
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

대상 branch에 더 높은 버전이 먼저 병합되면, `be/develop`의 up to date 규칙에 따라 Update branch 후 재검사에서 실패한다. 아직 공유 DB에 적용되지 않은 PR의 새 migration 파일명만 현재 시각 기준으로 변경하고 다시 push한다.

### 병합됐지만 공유 DB에 적용되지 않은 migration 복구

대상 branch에 이미 병합된 migration의 이름 변경은 CI가 차단한다. 다만 배포가 Flyway 실행 전에 실패하여 해당 migration이 개발·운영을 포함한 모든 공유 DB에 적용되지 않았음이 확인된 경우에만 예외적으로 이름을 바로잡을 수 있다.

1. 개발·운영 DB의 `flyway_schema_history`에서 대상 version과 script가 모두 없는지 확인한다.
2. 하나의 공유 DB라도 적용 이력이 있으면 기존 파일을 변경하지 않고 새 migration으로 roll forward한다.
3. 적용 이력이 없다면 복구 PR에 확인 결과와 이름 변경 사유를 남기고 백엔드 팀원의 확인을 받는다.
4. 저장소 관리자가 대상 branch ruleset의 Bypass list에 본인을 `For pull requests only`로 추가해 복구 PR을 병합하고, 병합 직후 즉시 제거한다. 우회 기록이 남지 않는 `exempt` 모드는 사용하지 않으며, 검사를 끄거나 다른 규칙을 변경하지 않는다. bypass 병합은 `Backend CI` 전체를 건너뛰므로 복구 PR에는 migration 파일명 변경만 포함하고, 병합 전에 로컬에서 `./gradlew test`를 통과시킨다.
5. 병합 후 개발·운영 배포에서 `flyway_schema_history`와 애플리케이션 health를 다시 확인한다.

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
