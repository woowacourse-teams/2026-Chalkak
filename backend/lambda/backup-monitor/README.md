# 전체 백업 완료 기록 감시

회사 운영 EC2가 꺼지거나 예약이 실행되지 않아도 감지하기 위해 **팀 계정·서울 리전**에 설치한다.
운영 EC2의 실행 실패 알림과 함께 사용한다. EC2나 복구용 Docker를 새로 띄우지 않는다.

```text
팀 계정 EventBridge Scheduler (마감 이후 1시간, 15분 간격 총 4회)
→ 감시 Lambda → 팀 S3의 runs/.../COMPLETE와 RESULT 읽기
→ CloudWatch: 백업 누락 여부 + 감시 정상 기록
→ 누락 또는 감시 중단 경보 → 확인한 SNS → 슬랙
```

## 무엇을 성공으로 판단하는가

- `selection=all`이며 DB·이미지가 모두 0으로 끝난 `RESULT`와 같은 회차의 `COMPLETE`가 있어야 한다.
- 시각 순서가 `시작 ≤ 작업 종료 ≤ 검증 완료 ≤ S3 저장 ≤ 현재`인지 확인한다.
- DB만 또는 이미지만 실행한 회차, 중단·불완전·잘못된 형식·미래 시각 기록은 성공으로 인정하지 않는다.
- 마감은 **02:00 + 대기 상한 + 실행 상한 + 종료 여유 10초 + 추가 여유**다.
  **마감 이상·마감 + 1시간 미만**에만 해당 예약의 완료 기록을 확인한다. 다른 시간에는 S3를 조회하거나
  지표를 기록하지 않는다. 자정이나 다음 예약의 마감 전에는 전날 누락을 다시 검사하지 않는다.
- 02시 직전 실행한 전체 백업 때문에 예약이 생략됐다면, 대기 상한 이내에 시작하고 02시 이후 완료한
  전체 회차도 인정한다. 확인 구간 안에서 수동 재실행한 전체 백업이 성공하면 다음 검사에 반영된다.

예를 들어 운영 상한이 3600초이고 추가 여유가 1800초이면 마감은 **04:30:10 KST**다.
AWS 예약은 분 단위이므로 마감 직후 첫 분부터 **04:31 → 04:46 → 05:01 → 05:16**, 총 4회 확인한다.
05:30:10 이후에는 확인하지 않는다. 각 예약의 분 내 전달·CloudWatch 평가·알림 전송 지연이 있어
정확히 표시한 시각에 알림이 도착하는 것은 아니다.
상한은 운영 `BACKUP_MAX_RUNTIME_SECONDS`와 반드시 같게 설정한다. 이 문서의 3600은 입력 예시다.

| 상황 | 처리 |
| --- | --- |
| 마감 이후 전체 완료 기록 없음 | `BackupOverdue=1`, 누락 경보 |
| 정상 완료 기록 있음 | `BackupOverdue=0`, 누락 상태 해제 |
| S3 접근 거부·네트워크·설정·지표 기록 오류 | 감시 실행 실패. 정상 기록을 남기지 않음 |
| Scheduler/Lambda 미실행·오류·권한 문제 | `MonitorHealthy` 누락으로 별도 감시 중단 경보 |

감시 중단 경보는 **예약된 4개 15분 구간 안에서 최근 3개 중 2개 이상 정상 기록이 없으면** 발생한다.
그 외 구간은 정상 값으로 계산하므로 23시간 동안 실행이 없다는 이유로 경보가 울리지 않는다.
지표가 조금 늦게 도착한 한 구간만으로 경보를 내지 않도록 2개 이상을 기준으로 한다.
CloudWatch 평가·지표 도착 지연이 있어 정확한 경보 도착 시간은 보장하지 않는다.
경보는 ALARM 전환 시 알리고 같은 상태에서는 15분마다 반복 발송하지 않는다. OK 알림은 보내지 않는다.
확인 구간이 끝나면 경보가 정상으로 돌아갈 수 있지만, 이것이 백업 문제가 해결됐다는 뜻은 아니다.
알림을 받으면 운영 로그와 완료 기록을 직접 확인한다. 시간 밖 수동 호출도 검사 없이 건너뛴다.
AWS 리전 전체나 SNS·슬랙 경로 자체의 장애는 이 구성만으로 감지·전달을 보장하지 않는다. 완료 기록 확인은 실제 데이터 복구 검증을 대신하지 않는다.

## 설치 준비

이 디렉터리의 `handler.py`, `configure.py`, `template.yaml`을 개발자 PC 또는 팀 CloudShell에 준비한다.
템플릿은 Lambda·전용 읽기 역할·일일 예약 4개·CloudWatch 경보 2개·로그 보존 30일을 생성한다.
`configure.py`가 실행 상한에서 예약 시각과 UTC 경보 구간을 함께 계산한다. AWS 호출은 하지 않는다.
서로 따로 입력하면 시간대가 어긋날 수 있으므로 상한·여유 변경 시에도 이 도구로 전부 다시 계산한다.
감시 역할은 회차 기록만 읽고 지표를 쓸 수 있다. 사진·덤프 다운로드, S3 쓰기·삭제·설정 변경 권한은 없다.
코드 ZIP은 백업 쓰기 권한의 `chalkak/*` 밖인 `monitor-code/*`에 관리자 권한으로 업로드한다.
백업용 사용자가 이 코드까지 바꿀 수 없도록 분리한 경로다. 앱 배포 묶음에는 넣지 않는다. 백업 코드와 함께 저장소에서 관리하되 팀 계정에 별도로 설치한다.

1. 팀 계정 `186101267768`의 서울 리전에 로그인했는지 확인한다. 회사 EC2의 백업용 쓰기 키를 사용하지 않는다.
2. 운영 설정에서 실행 상한 숫자를 확인한다. 비밀번호·설정 파일 전체를 복사하지 않는다.
3. SNS → 슬랙 전달 경로를 준비한다. 기존 [알림 Lambda](../alarm-notifier/README.md)를 재사용할 수 있다.
   팀 계정에 `chalkak-prod-alarms` 토픽과 해당 알림 Lambda를 설치하거나, 기존에 검증한 경로를 사용한다.
   **회사 계정 SNS를 ARN만 입력해서 사용할 수 있다고 가정하지 않는다.** 다른 계정이면 토픽 게시 정책도
   필요하다. 이 템플릿은 SNS 구독·webhook·토픽 정책을 만들거나 바꾸지 않는다.
4. 아래 명령은 **실제 AWS 리소스를 생성한다.** 코드 검토 후 실행한다. 기본은 알림 발송 비활성이다.

아래는 팀 계정으로 설정된 개발자 PC 또는 CloudShell의 **Bash 터미널**에서 이 디렉터리를 기준으로 실행하는 예시다.
`backup_runtime`에는 확인한 운영 값을 입력한다. `CodeKey`는 코드 해시를 포함해 변경 시 다른 파일로 남긴다.

```bash
set -euo pipefail
aws sts get-caller-identity --query Account --output text --no-cli-pager
# 출력이 186101267768인지 먼저 확인한다.
read -r -p '운영 백업 실행 상한(초): ' backup_runtime
monitor_hash=$(shasum -a 256 handler.py | cut -d ' ' -f 1)
monitor_zip="/tmp/chalkak-backup-monitor-${monitor_hash}.zip"
zip -j "$monitor_zip" handler.py
monitor_key="monitor-code/${monitor_hash}.zip"
python3 configure.py --max-runtime-seconds "$backup_runtime" --code-key "$monitor_key" \
  > /tmp/chalkak-monitor-parameters.json
# 생성된 예약 4개와 실행 상한을 확인한다. 이 파일에는 비밀값이 없다.
cat /tmp/chalkak-monitor-parameters.json
aws s3 cp "$monitor_zip" "s3://chalkak-backup-186101267768/$monitor_key" --region ap-northeast-2
aws cloudformation deploy --region ap-northeast-2 \
  --stack-name chalkak-backup-monitor --template-file template.yaml \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides file:///tmp/chalkak-monitor-parameters.json
```

템플릿은 상한 1~42000초와 여유 0~1800초를 허용한다. 대기·실행 합계가 하루 이상인 설정은 거부한다.
다른 버킷을 사용하면 `BackupBucket`도 변경하며, 코드 ZIP 버킷은 Lambda와 같은 서울 리전이어야 한다.

## 설치 후 검증·알림 연결

1. 1시간의 확인 구간 안에 팀 Lambda `chalkak-backup-monitor`를 빈 이벤트 `{}`로 실행한다.
   로그의 `expected_schedule`은 UTC다. S3 회차와 `run`·`overdue`, CloudWatch 두 지표를 대조한다.
   구간 밖 호출은 `backup_monitor_skipped`만 남기고 조회·지표 쓰기를 건너뛰는 것이 정상이다.
2. 정상 회차·부분 회차·완료 기록 없음·권한 실패는 아래 로컬 테스트로 먼저 확인한다.
   실제 백업 파일 삭제·운영 설정 파손으로 시험하지 않는다.
3. 생성된 Scheduler 4개의 시각·서울 시간대를 확인한다. 해당 1시간에만 15분 간격으로 호출하고
   각 호출의 `MonitorHealthy=1`을 확인한다. 시간 밖에 누락 경보가 생기지 않는지 확인한다.
   감시 미실행 시험은 백업 파일을 건드리지 않고 알림을 끈 별도 시험 스택의 예약을 중지해 확인한다.
4. 팀 동의 후 합성 경보로 **SNS → 슬랙 수신**을 확인한다. 실제 토픽 ARN을 `AlarmTopicArn`에 넣고
   `configure.py`에 `--alarm-topic-arn <검증한 ARN> --enable-notifications`를 추가해 매개변수 JSON을
   다시 만들고 같은 스택을 갱신한다. ZIP·상한·여유·예약 4개·경보 시간대를 함께 전달한다.
   활성화 시 이미 ALARM인 경보는 자동 재통지되지 않을 수 있으므로 현재 상태도 확인한다.
5. 설치·검사·수신 시각을 기록한다. Lambda·Scheduler·지표·경보·로그에 사용료가 발생할 수 있다.

실패 시 팀 계정의 `/aws/lambda/chalkak-backup-monitor` 로그 → 두 경보의 상태 사유 → Scheduler의
호출·권한을 확인한다. 백업 누락이면 회사 EC2의 백업 서비스 저널·회차 로그에서 원인을 찾는다.
감시 설정 변경이나 전체 백업 재실행 없이 경보 상태만 수동으로 정상으로 바꾸지 않는다.

```bash
python3 -m unittest discover -s tests -v
cfn-lint template.yaml
python3 configure.py --max-runtime-seconds 3600 --code-key monitor-code/test.zip
```

근거: [Scheduler의 Lambda 비동기 호출](https://docs.aws.amazon.com/lambda/latest/dg/with-eventbridge-scheduler.html),
[CloudWatch 누락 데이터 판정](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/alarms-and-missing-data.html),
[UTC 시간대 필터와 누락 값 채우기](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/using-metric-math.html).
