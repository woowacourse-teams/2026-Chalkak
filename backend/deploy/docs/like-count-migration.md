# 좋아요 즉시 집계 전환

`post_likes`는 사용자별 관계의 원본이고, `post_like_counts`는 게시물당 한 행의 현재 개수다. 캐시나 비동기 작업은 사용하지 않는다.

선택의 근거는 [실제 잠금 흐름·테이블 분리 이유·기존 EC2 성능 비교](../../docs/interviews/좋아요-집계를-분리한-이유.md)에 정리했다. 게시물 수정과 좋아요의 잠금 충돌이 사라졌다는 의미는 아니다.

**첫 전환 한 번만 전체 점검 후 수동 배포한다. 기존 Blue/Green 스크립트는 수정하지 않는다.** 아래는 합의한 절차이며 서버에서 실행하거나 리허설한 상태는 아니다.

진행 순서: **자동 배포 통제 → 구버전 종료 → 새 버전 실행·초기 집계 → 검증·공개 → 기존 배포 재개**.

## 구현한 보장

- 게시물 생성과 집계 행 생성(`0`)을 같은 트랜잭션에서 처리한다.
- 관계 INSERT·DELETE가 실제로 한 행을 바꾼 경우에만 `like_count = like_count + 1 / - 1`을 실행한다. 중복 요청은 개수를 바꾸지 않는다.
- 원자적 UPDATE도 행 잠금을 사용한다. 같은 게시물에 쓰기가 집중되면 대기가 생길 수 있다.
- 관계와 집계를 같은 트랜잭션에서 갱신한다. 집계 행 누락·음수 등으로 실패하면 관계 변경도 롤백한다. 기존 게시물 삭제와 좋아요 사이의 잠금 순서는 유지한다.
- 작성자·관리자 삭제 시 관계는 제거하고, 소프트 삭제된 게시물의 집계 행은 `0`으로 남긴다.
- 목록·상세·작성자별 사진·인기순·관리자 조회는 집계 값을 읽는다. 내 좋아요 여부는 기존 관계에서 확인한다.

## 이번만 전체 점검으로 전환하는 이유

새 마이그레이션은 **기존 모든 게시물(좋아요 0개·소프트 삭제 포함)**의 실제 관계 수를 한 번 계산한다. 구버전은 집계를 갱신하지 않으므로, 구버전과 신버전이 함께 쓰기를 처리하면 개수가 어긋날 수 있다.

평소 배포는 구버전이 살아 있는 동안 신버전이 시작된다. 이번에는 그 순서 대신 **구버전 완전 종료 → 신버전 실행·초기 집계 → 검증 → 서비스 재개**로 진행한다. 점검 중에는 조회·등록·취소를 포함한 앱 API를 사용할 수 없다. 별도 점검 화면을 구성하지 않으면 클라이언트에는 연결 실패나 Nginx의 502 등이 보일 수 있다.

## 1. 중단 전에 준비한다

- **개발 환경에서 먼저 리허설한 뒤 운영에 적용한다.** 각각의 EC2·DB 식별자, 기존 활성 포트·컨테이너·release 경로, Nginx upstream 내용을 기록한다. 이 문서 작성만으로 서버의 현재 상태가 확인된 것은 아니다.
- 대상 환경의 새 CodePipeline 배포 진입을 잠시 막고, 실행·대기 중인 pipeline/CodeDeploy가 없는지 확인한다. 운영 승인만 보류해도 이미 시작된 배포가 멈추지는 않는다. 진행 중인 배포는 **자동 롤백 없이 중지**하고 종료를 확인한다. 다른 환경의 pipeline은 건드리지 않는다.
- 검증된 커밋의 빌드 산출물을 별도 release 디렉터리에 준비하고 SHA-256을 확인한다. 실행 중인 JAR은 덮어쓰지 않는다. DB 백업·복구 방법을 확인하고, 함께 포함된 다른 Flyway 변경도 검토한다.
- `flyway_schema_history`에서 `202611021230` 적용 여부를 확인한다. 이미 적용된 뒤 구버전이 쓰기를 처리했다면 이 초기 전환 절차를 그대로 반복하지 않고, 쓰기를 멈춘 상태에서 집계 보정부터 준비한다.

준비할 값은 **새 release 경로·커밋 SHA·기존 활성 포트·새 포트**다. 아래 명령은 서버에서 그 값을 확인한 다음 사용할 예시이며 지금 실행하지 않는다.

## 2. 구버전 앱을 모두 종료한다 — 점검 시작

사용자에게 점검을 안내하고, 기존 앱 컨테이너(primary·backup 모두)의 재시작 정책을 `no`로 바꾼 뒤 `docker stop --time 40`으로 종료한다. 레거시 `chalkak-backend.service`가 남아 있다면 함께 중지하고 자동 재기동도 막는다. **DB 컨테이너·Nginx·CodeDeploy agent는 종료하지 않는다.**

8080·8081 리스너, 남은 앱 프로세스, DB의 구버전 진행 중 트랜잭션을 확인한다. 같은 DB를 쓰는 별도 앱·관리 도구·스케줄러의 게시물/좋아요 변경도 멈췄는지 확인한다. **하나라도 쓰기 주체가 남아 있으면 초기 집계를 시작하지 않는다.**

## 3. 새 버전만 수동 실행한다

기존 upstream은 **종료된 구버전 포트 한 줄만** 남긴다. backup 줄이 남아 있으면 기존 `bg_switch_upstream "<기존 포트>"`로 제거하고 Nginx reload·기존 worker 정리를 확인한다. 새 버전은 **다른 포트의 localhost**에서 먼저 실행한다. 이렇게 해야 공개 요청이 검증 전 신버전으로 넘어가지 않는다. `start_service.sh`·`validate_service.sh` 전체를 호출하면 자동 전환 흐름이 섞이므로 이번에는 호출하지 않는다.

컨테이너 기동 옵션은 [현재 start_service.sh](../scripts/start_service.sh)의 `docker run`을 기준으로 맞춘다. 아래는 저장소 기준 예시다. **실행 전 서버의 설치본과 대조하고, 프로파일·JVM·메모리·FCM 마운트를 임의로 바꾸지 않는다.**

<details>
<summary>수동 기동 명령 예시 — 배포 당일 실제 값 확인 후 사용</summary>

```bash
# 대상 EC2의 root Bash 세션. <...>는 실행 전 확인한 실제 값으로 교체한다.
source /opt/chalkak/bin/blue_green.sh
release_dir='/opt/chalkak/releases/manual-like-count-<확정한-커밋-SHA>'
new_port='<기존 활성 포트와 다른 8080 또는 8081>'
container="$(bg_container_name "${new_port}")"
fcm_credentials_path="$(awk -F= '$1 == "NOTIFICATION_FCM_CREDENTIALS_PATH" {print substr($0, index($0, "=") + 1); exit}' /etc/chalkak/application.env)"
fcm_mount=()
if [[ "${fcm_credentials_path}" == /* && -f "${fcm_credentials_path}" ]]; then
  fcm_mount=(-v "${fcm_credentials_path}:${fcm_credentials_path}:ro")
fi

# 같은 이름의 구버전 컨테이너가 있으면 중지·백업 여부를 확인한 뒤 별도로 정리한다.
docker run -d --name "${container}" --network host \
  --user "$(id -u chalkak):$(id -g chalkak)" \
  --env-file /etc/chalkak/application.env \
  -e "SERVER_PORT=${new_port}" -e SERVER_ADDRESS=127.0.0.1 -e MALLOC_ARENA_MAX=2 \
  --memory 768m --cap-drop ALL --security-opt no-new-privileges \
  --restart unless-stopped \
  --log-driver json-file --log-opt max-size=10m --log-opt max-file=3 \
  --label "chalkak.release=${release_dir##*/}" \
  -v "${release_dir}:/opt/chalkak/app:ro" -v /opt/chalkak/logs:/opt/chalkak/logs \
  ${fcm_mount[@]+"${fcm_mount[@]}"} "${IMAGE}" \
  java -Xmx320m -XX:MarkStackSize=524288 -XX:ReservedCodeCacheSize=64m \
  -Xlog:async \
  "-Xlog:gc*=info,safepoint*=info:file=/opt/chalkak/logs/gc-${new_port}.log:utctime,uptime,level,tags:filecount=5,filesize=10M" \
  -jar /opt/chalkak/app/application.jar
```

release 디렉터리는 `root:chalkak`·`0750`, JAR은 `root:chalkak`·`0640`으로 준비하고 이미지는 미리 받아 둔다. Spring Boot가 시작되면 Flyway가 집계 테이블 생성과 초기 COUNT를 실행한다. 실패·시간 초과 시 공개하지 않고 아래 복구 절차로 넘어간다.

</details>

## 4. 초기 집계와 API를 확인한 뒤 공개한다

Flyway 성공·`/actuator/health`의 `UP`을 확인하고, 다음 SQL이 **0행**을 반환하는지 확인한다. health만으로 집계의 정확성까지 확인한 것은 아니다.

```sql
SELECT post.id, counts.like_count, COUNT(post_like.id) AS actual_count
FROM posts post
LEFT JOIN post_like_counts counts ON counts.post_id = post.id
LEFT JOIN post_likes post_like ON post_like.post_id = post.id
GROUP BY post.id, counts.like_count
HAVING counts.like_count IS NULL OR counts.like_count <> COUNT(post_like.id);
```

이 검사는 다른 쓰기 작업과 겹치지 않게 실행한다. localhost의 새 포트에서 테스트용 계정·게시물로 다음을 확인하고, 마지막에 불일치 0건을 재확인한다. 실제 사용자 게시물을 검증용으로 삭제하지 않는다.

- 게시물 생성 → 집계 `0`, 좋아요 → `+1`, 중복 PUT → 변화 없음.
- 취소 → `-1`, 중복 DELETE → 변화 없음, 게시물 삭제 → 관계 삭제·집계 `0`.
- 최신순·인기순·상세 조회 정상 응답.

검증 후 기존 `bg_switch_upstream "${new_port}"`를 수동 호출해 **새 포트 한 줄만** 연결한다. 기존 함수를 사용하는 것이며 스크립트를 수정하는 것은 아니다. 구버전을 backup으로 추가하지 않는다. 외부 API·오류·DB를 확인하고, 구버전이 계속 종료된 상태인지 확인한 뒤 점검을 끝낸다.

## 5. 다음부터 기존 무중단 배포로 돌아간다

- Nginx primary와 실행 중인 컨테이너 이름 `chalkak-backend-<포트>`, release 라벨을 대조한다. 두 포트에 구버전·레거시 자동 실행이 남아 있지 않아야 한다.
- **수동 실행은 CodeDeploy의 성공 이력을 갱신하지 않는다.** 다음 자동 롤백이 COUNT 버전을 켜지 않도록 대상 환경의 자동 롤백 설정을 임시 조정·기록하고, 즉시 집계 버전의 첫 CodeDeploy 배포 성공을 확인한 뒤 설정을 원복한다. 그 첫 배포가 실패하면 구버전으로 자동 복귀시키지 않고 추가 배포를 멈춘 뒤 신버전을 수정한다.
- 대기 중인 오래된 pipeline 실행은 재개하지 않는다. 다음 배포 커밋·Flyway 호환성을 확인해 자동 배포 진입을 복구한다. 이후에는 기존 스크립트로 Blue/Green 배포한다. 수동 release 디렉터리는 자동 정리 대상인 `d-*`가 아니므로, 후속 배포 성공과 미사용 여부를 확인한 뒤 별도로 정리한다.

## 되돌릴 때

- **공개 전 실패:** 점검을 유지하고 신버전을 중지한다. Flyway 이력·DB 상태·로그부터 확인한다. 마이그레이션이 적용되지 않았다면 DB 변경 여부와 구버전 호환성을 확인한 뒤 기존 앱 재개를 판단한다.
- **마이그레이션 적용 후:** JAR을 되돌려도 집계 테이블과 Flyway 변경은 되돌아가지 않는다. 우선 신버전을 수정해 재시도한다. 부득이하게 구버전 서비스를 재개하면 집계가 다시 어긋나므로, 다음 신버전 전환 전에 **모든 쓰기를 멈추고 누락 행 생성·관계 수 재계산**을 별도로 수행해야 한다.
- **DB 복구가 필요할 때:** 백업은 앱 롤백과 별도다. RDS 복구 대상·접속 전환·백업 이후 데이터 유실 범위를 확인한 뒤 결정한다. 이미 적용한 Flyway 파일을 수정하거나 이력만 삭제해 재실행하지 않는다.

동시 쓰기 중 자동 보정·정기 재계산 기능은 이번 변경에 포함하지 않았다. 이번 작업은 문서화만 했으며 개발·운영 서버 설정, 배포 스크립트, DB는 변경하지 않았다.
