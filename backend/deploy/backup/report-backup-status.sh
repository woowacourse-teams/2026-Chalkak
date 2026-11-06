#!/usr/bin/env bash
set -euo pipefail
set +x
umask 022

# systemd가 전달한 종료 정보만 기록한다. 비밀 설정과 백업 상세 로그는 읽지 않는다.
[[ $# -eq 1 ]] || exit 1
log_file=$1
[[ -f "$log_file" && ! -L "$log_file" && -O "$log_file" ]] || {
  echo '백업 종료 요약 파일의 소유권과 경로를 확인하세요.' >&2; exit 1;
}

service_result=${SERVICE_RESULT:-unknown}
case "$service_result" in
  success|exit-code|signal|core-dump|timeout|watchdog|start-limit-hit|resources|protocol|oom-kill|exec-condition) ;;
  *) service_result=unknown ;;
esac
exit_code=${EXIT_CODE:-unknown}
case "$exit_code" in exited|killed|dumped) ;; *) exit_code=unknown ;; esac
exit_status=${EXIT_STATUS:-unknown}
if [[ ! "$exit_status" =~ ^[0-9]{1,3}$ && "$exit_status" != TERM && "$exit_status" != KILL ]]; then
  exit_status=unknown
fi
invocation_id=${INVOCATION_ID:-unknown}
[[ "$invocation_id" =~ ^[a-f0-9]{32}$ ]] || invocation_id=unknown

status=failed
reason=service-failure
if [[ "$service_result" == success && "$exit_code" == exited && "$exit_status" == 0 ]]; then
  status=success
  reason=completed
elif [[ "$service_result" == exit-code && "$exit_code" == exited && "$exit_status" == 75 ]]; then
  status=skipped
  reason=already-running
elif [[ "$exit_code" == exited && ( "$exit_status" == 124 || "$exit_status" == 137 ) ]]; then
  reason=runtime-limit
fi

record=$(printf '{"event":"backup_finished","timestamp":"%s","status":"%s","reason":"%s","service_result":"%s","exit_code":"%s","exit_status":"%s","invocation_id":"%s"}' \
  "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$status" "$reason" "$service_result" "$exit_code" "$exit_status" "$invocation_id")
# 요약은 저널과 CloudWatch 수집 파일에만 남긴다. 파일 쓰기 실패는 저널에서 확인한다.
printf '%s\n' "$record"
printf '%s\n' "$record" >> "$log_file"
