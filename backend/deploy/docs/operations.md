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

1. Temurin 25.0.4.1+1 공식 배포 파일을 내려받고 SHA-256을 검증해 설치한다. 설치한 Java로 Gradle을 실행한다.
2. PostgreSQL 18.4 Docker container를 기동한다.
3. 테스트와 `bootJar`를 실행한다.
4. `application.jar`, `appspec.yml`, 배포 script, systemd unit을 하나의 output artifact로 만든다.

Gradle toolchain vendor는 Adoptium이다. CodeBuild에서는 `JAVA_HOME`에 준비한 Temurin을 사용하므로 Foojay 자동 다운로드에 의존하지 않는다. 로컬의 기존 Foojay 설정은 유지한다. 다운로드 또는 체크섬 검증에 실패하면 설치 단계에서 빌드를 중단한다.

2026-10-09 개발 빌드 #129·#130은 테스트 실행 전에 Temurin 25 toolchain을 찾지 못해 실패했다. 같은 날 Foojay 플러그인과 동일한 Linux 조회 조건으로 확인한 결과는 `No package(s) found`였지만, Adoptium 공식 API에서는 설치 파일을 확인했다. 이전 성공 소스와 Java 설정은 같았다. 과거 Foojay 응답 기록이 없어 검색 결과가 바뀐 시점과 원인은 확정하지 않는다.

### CodeDeploy

동일한 revision을 개발·운영에 사용한다. 서버의 `/etc/chalkak/application.env`에 지정된 `SPRING_PROFILES_ACTIVE`로 환경을 구분한다.

- `dev`: Docker PostgreSQL 기동 및 배포 직전 `pg_dump` 수행
- `prod`: RDS에 연결 (PostgreSQL container는 사용하지 않는다)

앱은 컨테이너 `chalkak-backend-<포트>`로 실행하고, nginx가 `/etc/nginx/snippets/chalkak-upstream.conf`로 가리키는 포트(8080 또는 8081)가 활성이다. snippet의 `#` 주석 줄은 읽을 때 무시하지만, 전환할 때 파일을 새로 쓰므로 주석은 보존되지 않는다. 배포마다 nginx가 쓰지 않는 포트에 새 컨테이너를 띄우고 검증한 뒤 nginx를 전환한다. 이전 앱은 안정화가 끝날 때까지 계속 실행한다.

| Hook | timeout | 동작 |
| --- | --- | --- |
| BeforeInstall | 60초 | 사용자·디렉터리만 준비한다. 앱은 중지하지 않는다. |
| Install | - | JAR을 `/opt/chalkak/incoming`에 복사한다. |
| AfterInstall | 300초 | JAR을 `/opt/chalkak/releases/<deployment-id>/application.jar`로 옮기고 이미지 `eclipse-temurin:25.0.4_7-jre`가 없으면 미리 받는다. |
| ApplicationStart | 420초 | dev는 Postgres 기동(최대 60초)과 `pg_dump` 백업을 마친 뒤, nginx가 쓰지 않는 포트(8080↔8081)에 컨테이너 `chalkak-backend-<유휴 포트>`를 기동하고 최대 240초 동안 health를 기다린다. |
| ValidateService | 900초 | 스모크 검사 → nginx 전환(이전 앱은 backup) → 안정화 → backup 제거 → 이전 앱 종료 → release 정리 |

- 컨테이너는 `--network host`, `SERVER_ADDRESS=127.0.0.1`, `chalkak` 사용자, `--memory 768m`로 실행한다. JVM 옵션은 `start_service.sh`에 있다. 새 컨테이너의 `oom_score_adj`는 500으로 올려 메모리 압박 시 이전 앱보다 먼저 종료되게 하고, 안정화 후 0으로 되돌린다.
- ValidateService는 `/api/v1/banners`가 200인지 확인하고 snippet을 새 포트로 바꿔 nginx를 reload한다. 이때 이전 앱을 nginx `backup` 서버로 남겨, 안정화 중 새 앱이 연결을 거부하거나 끊으면(예: 프로세스가 죽은 경우) 요청이 즉시 이전 앱으로 넘어간다(`proxy_next_upstream`). 응답 없이 멈춘 앱은 proxy read timeout까지 기다리고, 이미 새 앱에 전달된 POST 같은 비멱등 요청은 nginx 기본 동작대로 재시도되지 않는다. 이후 600초 동안 10초마다 health와 `/api/v1/banners`(200)를 함께 확인하며(health가 UP이어도 실제 요청이 500이면 실패이고, nginx `backup`은 HTTP 500을 넘기지 않는다), 연속 6회 실패하면 이전 포트로 되돌려 전환을 확정한다. 600초가 지났을 때 해결되지 않은 실패가 남아 있으면 회복(카운터 0)하거나 연속 6회 실패에 이를 때까지 관찰을 이어간다.
- 안정화가 끝나면 이전 앱을 종료하기 직전에 새 앱 health와 스모크를 한 번 더 확인한다. 실패하면 안정화 중 롤백과 같은 경로로 되돌린다.
- 안정화가 끝나면 이전 앱을 종료하기 전에 backup 줄을 제거한다. 제거에 실패하면 경고만 남기고 배포는 유지한다(backup 줄은 무해하다).
- 이전 앱 종료 뒤 포트(`ss`)가 비었는지 확인한다. 아직 점유 중이면(예: 첫 배포에서 CodeDeploy가 unit 파일을 지워 `systemctl`이 레거시 서비스를 못 찾는 경우) `systemctl stop` 후 점유 PID에 SIGTERM, 최대 40초 대기, SIGKILL 순으로 종료한다. 그래도 점유 중이면 `ERROR: old app still listening on port <포트>; stop it manually`를 남기지만, 새 앱이 정상 서비스 중이므로 배포는 성공으로 둔다(실패시키면 CodeDeploy 자동 롤백이 정상 릴리스를 되돌린다). 운영자가 ERROR 줄을 보고 직접 종료한다.
- 컨테이너 종료는 Spring Boot의 graceful shutdown(기본 켜짐, 단계별 대기 30초)이 끝나도록 `docker stop --time 40`으로 기다린다.
- 안정화가 끝나면 이전 앱을 종료하고, 컨테이너가 참조하는 release와 최신 2개를 제외한 `/opt/chalkak/releases/` 디렉터리를 삭제한다.
- 진행 로그의 각 줄에는 UTC 시각이 붙는다. `/opt/codedeploy-agent/deployment-root/deployment-logs/codedeploy-agent-deployments.log`에서 확인한다.

### 첫 컨테이너 배포 전환

기존 systemd 서비스 `chalkak-backend.service`가 8080에서 계속 요청을 처리하는 동안 첫 컨테이너가 8081에서 기동한다.

- Install 단계에서 CodeDeploy의 정리가 이전 revision의 파일인 systemd unit 파일과 `/opt/chalkak/app/application.jar`를 삭제한다. 실행 중인 프로세스는 계속 동작한다.
- 안정화가 끝나면 ValidateService가 레거시 서비스를 중지·비활성화하고 `/opt/chalkak/app`을 삭제한다.
- 알려진 위험: nginx 전환 전에 서버가 재부팅되면 unit 파일이 없어 8080을 제공하는 프로세스가 없다. 재배포하기 전까지 서비스가 중단된다.

### 롤백

- 자동: 전환 전에 실패하면 이전 앱이 계속 요청을 처리한다. 안정화 중 새 앱이 연결을 받지 못하면 backup인 이전 앱이 즉시 요청을 처리하고, health 연속 실패(6회×10초)가 확인되면(안정화 마감 시점에 실패가 남아 있으면 관찰을 연장하고, 이전 앱 종료 직전 최종 health도 실패하면) nginx를 이전 포트 한 줄로 되돌리고 새 컨테이너를 삭제한다.
- 예외: 이전 앱이 비정상이거나 upstream을 되돌리지 못하면 새 컨테이너를 그대로 두고 `Rollback not possible`을 기록한 뒤 배포를 실패 처리한다. 운영자가 두 컨테이너와 snippet 상태를 직접 확인해야 한다.
- 예외: 첫 컨테이너 배포가 실패해 CodeDeploy 자동 롤백이 이전 revision을 재배포하면 옛 appspec(systemd)이 실행되며 nginx snippet은 건드리지 않는다. snippet이 8081에 남아 있으면 운영자가 `server 127.0.0.1:8080;`로 고치고 nginx를 reload한다.
- CodeDeploy 자동 롤백은 이전 revision을 같은 blue/green hook으로 다시 배포한다. ApplicationStart가 먼저 snippet을 health가 정상인 포트로 맞춘다.
- 수동 롤백(배포 중): CodeDeploy 콘솔에서 진행 중인 배포를 중지한다(Stop deployment). 이 배포 그룹은 자동 롤백이 켜져 있어, 중지만 선택해도 CodeDeploy가 직전 성공 revision으로 롤백 배포를 바로 시작한다. 롤백 배포는 같은 blue/green hook으로 진행되며, ApplicationStart가 남은 backup 줄을 먼저 정리한 뒤 직전 revision 컨테이너를 반대 포트에 띄우고 전환한다(2026-10-30 운영 확인: 중지 후 약 40초에 트래픽 전환, 약 11분에 완료, 외부 요청 실패 0건).
  - 배포를 중지해도 서버에서 실행 중인 ValidateService 스크립트는 멈추지 않는다. 롤백 배포와 동시에 돌며 nginx와 컨테이너를 서로 덮어쓸 수 있으므로, 중지한 배포 ID 경로의 스크립트를 PID로 바로 종료한다. `pkill -f`는 자기 셸 명령까지 맞을 수 있어 쓰지 않는다.

    ```bash
    pgrep -af 'deploy/scripts/validate_service.sh'   # 중지한 배포 ID(d-...) 경로의 PID를 고른다
    sudo kill <PID>
    ```

  - 배포를 두고 snippet만 고치는 방식은 쓰지 않는다. 실행 중인 ValidateService가 안정화를 마친 뒤 새 앱을 다시 primary로 쓰고 이전 앱을 종료해 수동 변경을 덮어쓴다.

- 수동 롤백(배포 완료 후): 이전 컨테이너는 이미 종료됐으므로 CodeDeploy에서 직전 성공 revision을 다시 배포한다(이전 배포 화면의 Redeploy, 또는 deployment group에서 그 revision으로 Create deployment). 같은 blue/green hook으로 진행되어 무중단으로 되돌린다. 첫 컨테이너 배포 이전의 revision은 옛 appspec(systemd)이므로 이 방법의 대상이 아니다.

- DB migration은 롤백되지 않는다. 자세한 규칙은 [Flyway 배포 규칙](#flyway-배포-규칙)을 따른다.

### 두 버전 중복 실행 주의

- 안정화 약 10분(관찰이 연장되면 최대 약 12분) 동안 두 버전이 함께 실행되어 스케줄러와 worker도 둘 다 동작한다. 중복 실행 여부 검토는 후속 작업이다.
- 알려진 위험: 푸시 Worker(SQS consumer)가 두 컨테이너에서 모두 동작한다. `PushDeliveryLocks`는 JVM 메모리 락이고 토픽 스케줄러의 라운드 기록도 JVM 메모리에 있어, 약 10~12분의 중복 구간에 같은 기기가 FCM 푸시를 중복 수신하거나 같은 이벤트가 큐에 두 번 발행될 수 있다. 배포 후 확인하고 후속 작업(웹과 Worker 분리 또는 JVM 간 발송 소유권 도입)으로 처리한다.
- 중복 구간에 서버가 재부팅되면 `--restart unless-stopped`인 두 컨테이너가 모두 되살아나 2GB 메모리에 JVM 두 개가 뜬다. 다음 배포의 reconcile이 snippet을 정상화하며, 운영자는 primary가 아닌 컨테이너를 중지할 수 있다.
- `application.log`와 `notification.log`는 두 JVM이 함께 쓴다.
- memwatch는 활성 컨테이너만 기록한다. 이전 앱의 메모리는 서버 전체 `MemAvailable`과 swap에서만 보인다.

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
| Require a pull request before merging | 승인 2명, PR 변경 내용이 달라지는 push 시 승인 무효화, Squash만 허용 | 승인 0명, Squash만 허용 |
| Require status checks to pass | `Backend CI` | `Backend CI`, `Admin Web CI` |
| Require branches to be up to date before merging | 사용 | 사용하지 않음 |
| Block force pushes | 사용 | 사용 |
| Restrict deletions | 사용 | 사용 |
| Bypass list | 비움 | 비움 |

필수 check는 출처를 GitHub Actions로 지정한다. `Backend CI`는 `Backend PR CI`, `Admin Web CI`는 `Admin Web PR CI` workflow의 최종 gate job이다. Status check가 선택 목록에 없다면 해당 workflow를 한 번 실행한 뒤 다시 설정한다.

`be/develop`은 `Require branches to be up to date before merging`을 사용해야 다른 Flyway migration이 먼저 병합된 경우 남은 PR을 최신 기준으로 다시 검사할 수 있다. 다른 PR이 먼저 병합되면 PR 화면의 Update branch로 최신 상태를 반영하고 CI를 다시 통과해야 병합할 수 있다. Update branch는 PR의 변경 내용을 바꾸지 않으므로 기존 승인은 유지된다. 승인 후 코드 수정이나 충돌 해결로 PR의 변경 내용이 달라지면 승인이 초기화될 수 있다.

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
sudo docker ps --filter name=chalkak-backend
cat /etc/nginx/snippets/chalkak-upstream.conf
sudo docker ps --filter name=chalkak-dev-postgres
curl --fail http://127.0.0.1:<활성 포트>/actuator/health
```

외부에서는 EC2 public address와 API port 또는 설정한 reverse proxy를 통해 확인한다.

## 운영 배포

운영 EC2, RDS, `/etc/chalkak/application.env`가 준비된 뒤 진행한다.

### 백엔드 릴리스 branch 준비

`be/develop`을 `main`에 직접 병합하지 않는다. 릴리스를 Squash로 병합해 두 branch 이력이 이어져 있지 않으므로, 이미 반영된 변경도 충돌로 표시된다. `main`에서 릴리스 branch를 만들고 백엔드 경로만 `be/develop` 기준으로 교체한다.

`hotfix/*` branch는 만들지 않는다. 운영 긴급 수정도 코드와 migration 모두 `be/develop`에 PR로 병합한 뒤 같은 릴리스 절차로 `main`에 반영한다. `main`에만 들어간 수정은 다음 릴리스의 백엔드 경로 교체로 사라질 수 있다.

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
5. 승인 후 CodeDeploy 배포 상태와 `validate_service.sh` 결과를 확인한다. 배포 중에도 이전 앱이 요청을 처리하지만, 전환 후 약 10분 동안 두 버전이 함께 실행되므로 승인 시점을 조절한다.
6. 운영 EC2 내부와 외부 endpoint를 모두 확인한다.

```bash
sudo docker ps --filter name=chalkak-backend
cat /etc/nginx/snippets/chalkak-upstream.conf
curl --fail http://127.0.0.1:<활성 포트>/actuator/health
```

## Flyway 배포 규칙

- Migration은 `src/main/resources/db/migration/VyyyyMMddHHmm__description.sql`에 추가한다.
- PR에서 추가한 migration 버전은 대상 branch의 마지막 migration 버전보다 커야 한다.
- Migration은 `main`에 직접 추가하지 않고 `be/develop`을 거친다. 운영 긴급 수정도 같다. `be/develop`의 순서 검사는 `main`에만 추가된 migration을 알 수 없어, 이미 개발 DB에 적용된 migration이 다음 릴리스에서 `main`의 검사에 막히고 이름도 바꿀 수 없게 된다.
- 공유 DB에 한 번 적용된 migration은 수정·삭제·rename하지 않고 새 migration으로 roll forward한다.
- Entity 변경과 migration을 같은 PR에 포함한다.
- Column 삭제, rename, `NOT NULL` 강제는 expand-contract 방식으로 여러 배포에 나눈다.
- Spring Boot 시작 중 Flyway가 migration을 실행한다.
- Migration 또는 Hibernate validation이 실패하면 health check와 CodeDeploy도 실패한다.
- CodeDeploy가 이전 JAR을 다시 배포해도 성공한 DB migration은 자동으로 되돌아가지 않는다.
- 전환 후 약 10분 동안 이전·새 버전이 같은 DB를 함께 사용한다. 새 migration은 이전 버전 앱과도 호환되어야 한다.

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

`be/develop`의 migration 검사와 up to date 규칙이 순서가 뒤바뀐 병합을 막으므로, 이 복구는 bypass나 ruleset 변경으로 검사를 우회한 경우에만 필요하다. 복구 대상 migration이 이미 `main`에도 반영됐다면, 다음 백엔드 릴리스 PR은 기존 migration의 이름 변경으로 검사에 막힌다. 이 경우 일반 릴리스와 따로 `main`에서 만든 branch에 같은 파일명 변경만 담아 위 절차대로 먼저 병합하고, 일반 릴리스 PR은 bypass로 병합하지 않는다.

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
sudo docker ps -a --filter name=chalkak-backend
cat /etc/nginx/snippets/chalkak-upstream.conf
sudo docker logs --tail 200 chalkak-backend-<포트>
```

첫 컨테이너 배포 전에는 기존 `sudo journalctl -u chalkak-backend.service -n 200 --no-pager`를 사용한다.

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
