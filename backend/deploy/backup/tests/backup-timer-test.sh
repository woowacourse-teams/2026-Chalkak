#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C SYSTEMD_COLORS=0
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TIMER_FILE="$SCRIPT_DIR/../systemd/chalkak-backup.timer"

command -v systemd-analyze >/dev/null || {
  echo '필수 도구 없음: systemd-analyze (Linux에서 실행)' >&2
  exit 1
}
calendar=$(sed -n 's/^OnCalendar=//p' "$TIMER_FILE")

assert_next() {
  local timezone=$1 base_time=$2 expected=$3 result next
  result=$(TZ="$timezone" systemd-analyze calendar --base-time="$base_time" "$calendar")
  next=$(printf '%s\n' "$result" | sed -n 's/^[[:space:]]*Next elapse: //p')
  if [[ "$next" != "$expected" ]]; then
    printf 'FAIL: timezone=%s base=%s expected=%s actual=%s\n' \
      "$timezone" "$base_time" "$expected" "$next" >&2
    exit 1
  fi
  printf 'PASS: timezone=%s base=%s next=%s\n' "$timezone" "$base_time" "$next"
}

# 한국 시간 11월 3일 01:59:59: UTC 서버에서도 다음 날 02:00으로 계산해야 한다.
assert_next UTC '2026-11-02 16:59:59 UTC' 'Mon 2026-11-02 17:00:00 UTC'
assert_next Asia/Seoul '2026-11-02 16:59:59 UTC' 'Tue 2026-11-03 02:00:00 KST'
# 한국 시간 02:00을 지났으면 다음 날 예약을 선택한다.
assert_next UTC '2026-11-02 17:00:01 UTC' 'Tue 2026-11-03 17:00:00 UTC'
