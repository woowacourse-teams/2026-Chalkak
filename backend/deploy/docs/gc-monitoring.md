# GC 정지와 스왑 활동 수집 (#654)

GC 로그는 dev/prod 앱 시작 옵션으로 활성화하고 조사 후에도 기본 로그를 유지한다. 운영 memwatch와 추가 상세 GC·safepoint 로그는 원인 조사와 개선 효과 검증 동안 사용한다. memwatch는 기존 임시 1분 타이머를 유지하고 스크립트만 보완한다. 새 영구 타이머·procstat·10초 수집·힙/GC 종류 변경은 포함하지 않는다. 아래 운영 명령은 사람이 실행한다.

## 적용 순서

1. CloudWatch 서울 리전에 `/chalkak/dev/gc`(7일), `/chalkak/prod/gc`와 `/chalkak/prod/memwatch`(각 30일)를 먼저 만든다. 기존 런북의 태그를 사용한다. 회사 IAM 역할을 직접 수정하지 않고 쓰기 권한 문제가 있으면 담당자에게 요청한다.
2. 운영에서 `sudo systemctl cat chalkak-memwatch.service chalkak-memwatch.timer`로 현재 실행 명령·1분 주기를 확인한다. 기존 스크립트와 조사 로그를 보관한다. 타이머가 이미 돌아가면 중복 등록하지 않는다.
3. 저장소의 `deploy/monitoring/chalkak-memwatch.sh`를 운영 서버 `/tmp/chalkak-memwatch.sh`로 전달한 뒤 적용한다. 기존 누적 필드를 유지하며, 첫 새 기록부터 boot ID 등 추가 필드가 남는다.

   ```bash
   sudo install -o root -g root -m 0700 /tmp/chalkak-memwatch.sh /usr/local/bin/chalkak-memwatch.sh
   sudo touch /var/log/chalkak-memwatch.log
   sudo chown root:chalkak /var/log/chalkak-memwatch.log
   sudo chmod 0640 /var/log/chalkak-memwatch.log
   sudo -u cwagent head -n 1 /var/log/chalkak-memwatch.log
   ```

   다음 타이머 실행 후 `sudo tail -n 2 /var/log/chalkak-memwatch.log`로 확인한다. 읽기 실패 값은 `-`다. root 소유 로그를 유지하고, 기존 타이머의 서비스 계정이 root인지 확인한다.
4. 아래 파일을 `/etc/logrotate.d/chalkak-memwatch`에 root 소유·0644로 저장한다. 스크립트가 매번 파일을 열기 때문에 copytruncate는 필요 없다. 회전 이력을 CloudWatch로 중복 수집하지 않도록 Agent는 활성 파일만 지정한다.

   ```text
   /var/log/chalkak-memwatch.log {
       su root root
       daily
       rotate 7
       maxsize 10M
       missingok
       notifempty
       compress
       delaycompress
       create 0640 root chalkak
   }
   ```

   `sudo logrotate --debug /etc/logrotate.d/chalkak-memwatch`로 문법·파일 권한을 확인한다. `su root root`는 부모 디렉터리의 그룹 쓰기 권한이 있는 환경에서도 회전 계정을 명시한다. `maxsize`는 일 단위 회전과 함께 적용되며 logrotate 실행 시 검사하므로 실시간 용량 상한은 아니다. debug 검사는 상태 파일을 갱신하거나 실제로 회전하지 않는다.
5. dev/prod Agent의 기존 설정을 백업하고 해당 환경의 `cloudwatch-agent.*.json`을 서버 `/opt/aws/amazon-cloudwatch-agent/etc/chalkak.json`에 적용한다. 기존 수집 항목과 서버별 추가 설정이 있다면 보존한다. [기존 Agent 적용 절차](monitoring.md#5-cloudwatch-agent-설치-dev-prod-ec2-각각)의 fetch-config·상태 확인을 따른다. 앱 배포는 Agent 설정을 자동 적용하지 않는다.
6. GC 옵션은 다음 일반 앱 배포·서비스 재시작부터 적용된다. EC2 재부팅은 필요 없다. 기존 `jcmd` 로그 파일이 있다면 chalkak이 쓸 수 있는지 확인한다. JVM은 `/opt/chalkak/logs/gc.log` 및 회전 이력 최대 5개를 10MB 기준으로 관리한다. GC와 safepoint는 서로 별도 행으로 수집하며 줄을 합치지 않는다.
7. 배포 후 아래와 CloudWatch 실제 유입·UTC 시각을 확인한다. 일반·알림 로그도 계속 들어와야 한다. GC를 강제로 실행하거나 테스트 문구를 운영 파일에 쓰지 않는다.

   ```bash
   sudo systemctl status chalkak-backend.service --no-pager
   sudo -u cwagent head -n 2 /opt/chalkak/logs/gc.log
   sudo systemctl list-timers chalkak-memwatch.timer --all
   sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -m ec2 -a status
   ```

GC 비동기 로깅은 쓰기 대기를 줄이는 목적이며 무손실을 보장하지 않는다. 버퍼 포화 등의 로그 경고가 있는지도 확인한다. 재시작할 때 GC 번호·uptime은 초기화되므로 실행 경계를 넘어 같은 GC 번호를 묶지 않는다.

## 2초 이상 정지 알람

`/chalkak/prod/gc`의 지표 필터 `GcPauseOver2s`에 아래 패턴을 사용한다. 숫자+ms를 숫자 지표로 변환하지 않고 종료 요약 행을 건수로 센다. 괄호 그룹 없이 AWS 필터가 지원하는 정규식만 사용한다.

```text
%Pause [FY].* [2-9][0-9]{3}\.[0-9]+ms$|Pause [FY].* [1-9][0-9]{4,}\.[0-9]+ms$%
```

- 네임스페이스 `Chalkak/prod`, 지표 `GcPauseOver2s`, 값 `1`, 기본값 `0`, 단위 `Count`, 디멘션 없음.
- 경보 `chalkak-prod-gc-pause`: Sum, 60초, `>= 1`, 1 of 1, 누락 `notBreaching`. ALARM/OK는 기존 `chalkak-prod-alarms` 토픽으로 전달한다.
- 설명: `2초 이상 GC 정지가 관측됐습니다. CloudWatch의 prod GC 로그에서 원인·단계·CPU 시간을 확인하고 같은 구간 memwatch와 API·Lambda 로그를 대조하세요. 스왑 원인으로 자동 확정하지 마세요.`
- 발생 종료 후 전송·평가하는 사후 경보다. ALARM 유지 중 매 GC마다 새 Slack 메시지가 오는 것은 아니다. 무로그와 정상 상태는 구분하지 못하므로 수집 유입도 확인한다.
- 콘솔의 필터 테스트에 실제 형식 샘플을 붙여 검증한다: `Pause Full ... 1999.999ms` 불일치, `2000.000ms`·`7605.158ms`·`10000.000ms` 일치. `Pause Young ...`도 동일하다. 시작 행·`Phase 1: Mark live objects 6932.118ms`·CPU 행·safepoint 행은 불일치여야 한다.
- 콘솔 필터 테스트는 지표를 발행하지 않는다. 필터 생성 후 일반 GC 로그로 기본값 지표가 생긴 뒤 경보를 만든다. 운영에서 긴 GC를 일부러 유발하지 않는다. AWS 측 패턴 승인·실제 알람 전달은 수동 검증 대상이다.

## 조회와 해석

GC 그룹에서 긴 종료 행을 찾는다.

```sql
fields @timestamp, @logStream, @message
| filter @message like /Pause (Full|Young)/
| parse @message / (?<pauseMs>[0-9.]+)ms$/
| filter pauseMs * 1 >= 2000
| sort @timestamp desc
```

대상 행의 GC 번호(예: `GC(228)`)를 같은 로그 스트림·같은 JVM 실행 시간대에서 검색해 단계별 시간과 User/Sys/Real을 본다. safepoint 로그도 함께 확인한다. application·Lambda 그룹은 동일 시간대로 조회한다. 실제 uploadId가 없는 접근 로그는 특정 사진의 콜백과 직접 연결할 수 없다.

memwatch 그룹에서는 우선 원본을 시간순으로 조회·필요 시 CSV로 내보낸다.

```sql
fields @timestamp, @message
| sort @timestamp asc
| limit 1000
```

같은 boot_id의 연속 두 행에서 `uptime_s` 차이가 실제 간격이며, `pswpin/pswpout/pgmajfault/cpu_steal_ticks/psi_*_us` 차이가 그 구간 증가량이다. Java major fault 차이는 PID와 java_start_ticks도 같을 때만 계산한다. 첫 측정·누락·식별자 변경·감소한 카운터는 비교하지 않는다. `-`는 수집 불가이며 0이 아니다. 이전 스크립트 형식의 행은 새 식별 필드가 없어 같은 방식으로 비교하지 않는다.

PSI total 단위는 마이크로초, steal·Java 시작 시점은 커널 clock tick이다. 메모리 필드의 `_kb`는 Linux의 KiB 단위이고, swap-in/out은 페이지 수다. 페이지 크기를 확인하지 않고 바이트로 단정하지 않는다. PSI는 서버 전체의 압박이며 시스템 전체 CPU full은 수집하지 않는다. swap-in/major fault만으로 스왑 원인을 확정하지 않고 GC와 함께 대조한다. 1분 기록으로 초 단위 발생 순서까지 확정할 수 없다.

## 재부팅·복구

- 앱 배포는 임시 memwatch 타이머를 없애지 않지만, EC2 재부팅은 없앤다. 조사 기간 중 EC2를 재부팅·중지 후 시작했다면 memwatch 재등록과 새 기록·CloudWatch 유입 확인이 필수다. 재등록 전까지 관측 공백이 생긴다. 기존 실행 명령을 확인하고 아래처럼 다시 등록한다. 먼저 같은 이름의 타이머 존재 여부를 확인한다.

  ```bash
  sudo systemd-run --unit=chalkak-memwatch --on-calendar='*:*:00' --timer-property=AccuracySec=1s /usr/local/bin/chalkak-memwatch.sh
  ```

- 수집 이상이면 Agent 로그와 읽기 권한을 확인하고 필요 시 백업 설정을 fetch-config로 되돌린다. memwatch는 백업 스크립트로 복구한다. 로그·타이머를 이번 작업에서 삭제하지 않는다.
- JVM 옵션으로 기동이 실패하면 이전 서비스 파일로 복구하고 daemon-reload 후 서비스 재시작한다. 이 복구는 앱 중단을 동반할 수 있으므로 운영자가 수행한다.
- 환경변수 변경은 없다. 힙·GC 종류·swappiness·Lambda/SQS 설정도 변경하지 않는다.

## 조사·개선 검증 후 정리

원인 파악만으로 수집을 바로 중단하지 않는다. 개선 전후 GC 정지와 API 지연·memwatch 기록을 비교하고, 조사 근거를 보관한 뒤 운영자가 다음 순서로 정리한다. 현재 조사 중에는 실행하지 않는다.

1. 필요한 CloudWatch 로그와 로컬 조사 자료를 내보내 보관한다.
2. memwatch 타이머와 실행 중 서비스를 중지하고, Agent의 memwatch 수집 항목을 제거해 재적용한다. 기존 application·GC 수집 항목은 유지한다.
3. 더 이상 실행·수집하지 않는 것을 확인한 뒤 서버의 memwatch 스크립트·logrotate 설정·로컬 로그와 백업을 제거한다. CloudWatch의 기존 memwatch 기록은 보관을 확인한 뒤 보존 기간 만료 또는 수동 삭제로 정리한다.
4. 기본 GC 로그·회전·CloudWatch 수집·2초 알람은 유지한다. JVM 시작 옵션의 상세 GC·safepoint 선택자는 기본 `gc=info` 수준으로 줄이고, 알람 설명과 대시보드 안내에서 memwatch·상세 단계 대조 안내를 제거한다. 다음 앱 재시작 후 기본 GC 종료 행의 유입을 확인한다.

memwatch를 영구 운영할 필요가 생기면 부팅 자동 복구를 다시 검토한다. 현재 선택의 근거는 [GC 로그와 조사용 관측의 유지 범위](../../docs/interviews/GC-로그와-조사용-관측의-유지-범위.md)에 남긴다.
