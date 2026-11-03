#!/usr/bin/env bash
set -euo pipefail
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
root=$(mktemp -d)
trap 'rm -rf -- "$root"' EXIT
trap 'printf "FAIL: line=%s\n" "$LINENO" >&2' ERR
log_file="$root/status.log"
touch "$log_file"
export INVOCATION_ID=0123456789abcdef0123456789abcdef

check_result() {
  local expected_status=$1 expected_reason=$2
  bash "$script_dir/report-backup-status.sh" "$log_file" > "$root/output"
  jq -e --arg status "$expected_status" --arg reason "$expected_reason" \
    '.event == "backup_finished" and .status == $status and .reason == $reason and
     .invocation_id == "0123456789abcdef0123456789abcdef" and
     (.timestamp | test("^[0-9TZ:-]+$"))' "$root/output" >/dev/null
  tail -n 1 "$log_file" | cmp - "$root/output"
}
export SERVICE_RESULT=success EXIT_CODE=exited EXIT_STATUS=0
check_result success completed
echo 'PASS: 정상 종료는 성공 요약만 기록'
export SERVICE_RESULT=exit-code EXIT_STATUS=1
check_result failed service-failure
echo 'PASS: 설정·DB·이미지·결과 전송 실패의 종료 코드 1 감지'
for code in 124 137; do
  export EXIT_STATUS=$code
  check_result failed runtime-limit
done
echo 'PASS: 전체 시간 상한 초과 감지'
export SERVICE_RESULT=signal EXIT_CODE=killed EXIT_STATUS=TERM
check_result failed service-failure
export SERVICE_RESULT=oom-kill EXIT_STATUS=KILL
check_result failed service-failure
echo 'PASS: 신호 종료·메모리 부족 종료 감지'
export SERVICE_RESULT=exit-code EXIT_CODE=exited EXIT_STATUS=75
check_result skipped already-running
echo 'PASS: 중복 실행 거부는 성공·실패 경보와 구분'
[[ $(wc -l < "$log_file") -eq 7 ]]
echo 'PASS: 기존 기록을 덮어쓰지 않고 회차별 추가'

# 설정과 오류 메시지를 출력하지 않고 systemd 종료 정보도 허용된 값으로 제한한다.
export SERVICE_RESULT='SECRET-SENTINEL"' EXIT_CODE='SECRET-SENTINEL' \
  EXIT_STATUS='SECRET-SENTINEL' INVOCATION_ID='SECRET-SENTINEL'
bash "$script_dir/report-backup-status.sh" "$log_file" > "$root/output"
jq -e '.status == "failed" and .service_result == "unknown" and
  .exit_code == "unknown" and .exit_status == "unknown" and .invocation_id == "unknown"' "$root/output" >/dev/null
if grep -q SECRET-SENTINEL "$log_file"; then exit 1; fi
echo 'PASS: 예상하지 못한 환경변수와 민감 문자열을 요약에서 제거'

ln -s "$log_file" "$root/link"
if bash "$script_dir/report-backup-status.sh" "$root/link" > "$root/output" 2>&1; then exit 1; fi
if bash "$script_dir/report-backup-status.sh" "$root/missing/status.log" > "$root/output" 2>&1; then exit 1; fi
echo 'PASS: 심볼릭 링크·준비되지 않은 기록 경로 거부'

# 실제 파일 쓰기가 실패하는 경우도 오류를 반환하고 기존 기록을 유지해야 한다.
cp "$log_file" "$root/before"
result=0
(ulimit -c 0; ulimit -f 0; bash "$script_dir/report-backup-status.sh" "$log_file") 2>/dev/null | cat >/dev/null || result=$?
[[ $result -ne 0 ]]
cmp "$root/before" "$log_file"
echo 'PASS: 실제 기록 실패 시 오류 반환·기존 요약 보존'

# 비동기 알림은 운영 서비스 실행 결과를 바꾸지 않아야 한다.
grep -Fxq 'ExecStopPost=-/usr/bin/timeout --kill-after=1s 5s /bin/bash report-backup-status.sh /var/log/chalkak-backup/status.log' \
  "$script_dir/../systemd/chalkak-backup.service"
grep -Fxq 'ReadWritePaths=/var/log/chalkak-backup' "$script_dir/../systemd/chalkak-backup.service"
jq -e '[.logs.logs_collected.files.collect_list[] |
  select(.file_path == "/var/log/chalkak-backup/status.log" and .log_group_name == "/chalkak/prod/backup")] | length == 1' \
  "$script_dir/../monitoring/cloudwatch-agent.prod.json" >/dev/null
echo 'PASS: 종료 결과 유지 옵션과 전용 요약 파일 수집 연결'
