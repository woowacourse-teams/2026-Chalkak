# 백업 실패 알림

## 전달 흐름과 범위

```text
새벽 2시 예약 또는 systemctl 수동 실행
→ 백업 서비스 종료
→ 비밀값 없는 종료 요약 → /var/log/chalkak-backup/status.log
→ 기존 CloudWatch Agent → /chalkak/prod/backup
→ BackupFailures 지표 → chalkak-prod-backup-failed 경보
→ 기존 SNS chalkak-prod-alarms → 알림 Lambda → 운영 슬랙 채널
```

| 결과 | 처리 |
| --- | --- |
| 설정 검사·DB·이미지·결과 전송 실패, 시간 초과, 신호·메모리 부족 종료 | `failed` 기록, 실패 경보 대상 |
| 전체 백업 정상 종료 | `success` 기록, 슬랙 발송 없음 |
| 다른 백업과 잠금 충돌(종료 코드 75) | `skipped` 기록, 성공으로 처리하지 않으며 실패 경보에서는 제외 |
| 요약 기록 실패 | 저널에 오류. 기록 작업은 5초로 제한하며 원래 백업 종료 결과는 유지 |

대상은 `chalkak-backup.service` 실행이다. `bash backup.sh`·개별 스크립트를 직접 실행하면
이 알림이 적용되지 않는다. 호스트 중단·타이머 미실행과 완료 기록 누락은
[팀 계정의 외부 감시](../../lambda/backup-monitor/README.md)로 확인한다. 외부 감시도 별도 설치·수신
검증이 필요하다. Agent·SNS·슬랙 경로 자체의 장애까지 이 알림만으로 보장하지 않는다.

## 최초 적용

1. [설치 가이드](README.md#운영-서버의-새벽-2시-예약-설치)에 따라 백업 실행 파일과 서비스를
   갱신한다. 설치기가 전용 요약 파일을 만든다. 보호된 `/var/lib/chalkak-backup`의 권한은 넓히지 않는다.
2. 기존 Agent 설정을 보관하고 `deploy/monitoring/cloudwatch-agent.prod.json`의 **백업 수집 항목을
   현재 설정에 합친 뒤** Agent에 다시 적용한다. 서버에만 있는 다른 수집 설정을 지우지 않는다.
   기존 Agent 설치·적용 명령은 [모니터링 가이드](../docs/monitoring.md)를 따른다.
3. 회사 계정·서울 리전의 CloudWatch에서 로그 그룹 `/chalkak/prod/backup`을 만들고 로그 보존을
   30일로 설정한다. 기존 EC2 역할·SNS·Lambda·슬랙 webhook은 변경하지 않는다.
4. 아래 필터와 경보를 만든다. 이후에 들어오는 로그부터 집계한다.

| 항목 | 값 |
| --- | --- |
| 메트릭 필터 이름 | `chalkak-prod-backup-failed` |
| 필터 패턴 | `{ $.event = "backup_finished" && $.status = "failed" }` |
| 네임스페이스 / 지표 | `Chalkak/prod` / `BackupFailures` |
| 지표 값 / 기본값 / 차원 | `1` / `0` / 없음 |
| 경보 이름 | `chalkak-prod-backup-failed` |
| 통계 / 기간 / 평가 | 합계 / 1분 / 1개 중 1개가 1 이상 |
| 누락 데이터 | 정상으로 처리(`notBreaching`) |
| 경보 작업 | ALARM일 때 기존 SNS `chalkak-prod-alarms`로 전송 |
| OK·데이터 부족 작업 | 없음 |
| 설명 | `찰칵 백업 실패. /chalkak/prod/backup 및 journalctl -u chalkak-backup.service에서 회차와 종료 코드를 확인하세요. 상세 원인은 보호된 회차의 OUTCOME·RESULT·db.log·image.log를 확인하세요.` |

경보는 상태가 ALARM으로 바뀔 때 알린다. 연속된 실패가 같은 ALARM 상태에 묶이면 회차마다
별도 메시지를 보내지는 않는다. 지표·로그·알림 사용량에 따른 기존 CloudWatch/SNS/Lambda 비용이 추가된다.

## 실패 시 확인·재실행

```bash
sudo systemctl status chalkak-backup.service --no-pager
sudo journalctl -u chalkak-backup.service --since today --no-pager
sudo tail -n 5 /var/log/chalkak-backup/status.log
# 저널에서 run 값을 찾은 뒤, 해당 회차의 보호된 결과 파일을 확인한다.
sudo ls -lt /var/lib/chalkak-backup
```

`runtime-limit`는 전체 시간 상한, `exit_status=1`은 설정 또는 백업 단계 실패다.
해당 회차의 `OUTCOME`·`RESULT`에서 단계와 DB/이미지 종료 코드를 확인하고, 실패한 쪽의
`db.log` 또는 `image.log`에서 원인을 찾는다. 사전 설정 검사에서 실패하면 회차 디렉터리가
없을 수 있으므로 저널부터 확인한다. 상세 로그는 비밀값이 포함될 수 있어 슬랙·PR에 그대로 붙이지 않는다.

원인을 해결한 뒤 운영 부하·배포 중 여부를 확인하고 새 회차를 실행한다.

```bash
sudo systemctl start chalkak-backup.service
```

## 적용 완료 확인

- 정상 서비스 실행 후 `success` 요약이 CloudWatch에 도착하며 슬랙 성공 메시지가 없는지 확인한다.
- 실패 로그 예제로 필터를 시험하고, 별도 합성 서비스에서 실패·시간 초과·요약 기록 실패를 확인한다.
  실제 백업 파일을 삭제하거나 운영 DB 설정을 고장 내서 시험하지 않는다.
- **테스트 슬랙 발송에 대한 팀 동의 후** 합성 실패 한 건으로 실제 슬랙 수신까지 확인한다.
  필터 시험만으로 알림 전달 검증이 끝났다고 기록하지 않는다.
- 적용 시각·합성 실패 시각·수신 시각·정상/실패 결과를 기록한다. 로컬 종료 요약 파일은
  하루 수백 바이트 수준이며 초기에는 자동 삭제하지 않는다. CloudWatch 로그 보존과 백업본 보존은 별개다.
