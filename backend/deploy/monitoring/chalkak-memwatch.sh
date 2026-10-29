#!/usr/bin/env bash
# 기존 임시 타이머에서 1분마다 실행. 누적값의 차이는 분석 시 계산한다.
set -u
LOG=/var/log/chalkak-memwatch.log
TS=$(date -u +%Y-%m-%dT%H:%M:%SZ)
SNIPPET=/etc/nginx/snippets/chalkak-upstream.conf
PID=0; PORT=-
# 활성 컨테이너(blue/green)의 JVM을 GC 로그 경로로 찾는다. docker 데몬은 거치지 않는다.
while IFS= read -r UP_LINE; do
  if [[ "$UP_LINE" =~ 127\.0\.0\.1:(8080|8081)\; ]]; then
    PORT=${BASH_REMATCH[1]}
    break
  fi
done < <({ cat "$SNIPPET"; } 2>/dev/null)
if [[ "$PORT" != - ]]; then
  CPID=$(pgrep -o -f -- "^java .*file=/opt/chalkak/logs/gc-${PORT}\.log" 2>/dev/null) || CPID=0
  [[ "$CPID" =~ ^[0-9]+$ ]] && PID=$CPID
fi
# 컨테이너 JVM이 없으면 전환 전 systemd 서비스로 대체한다.
if [[ "$PID" == 0 ]]; then
  PID=$(systemctl show chalkak-backend.service --property=MainPID --value 2>/dev/null) || PID=0
  [[ "$PID" =~ ^[0-9]+$ ]] || PID=0
fi

# 없는 값과 읽기 실패를 정상값 0으로 기록하지 않는다.
field() {
  local value
  value=$(awk -v key="$2" '$1 == key {print $2; exit}' "$1" 2>/dev/null) || value=-
  [[ "$value" =~ ^[0-9]+$ ]] && printf '%s' "$value" || printf '-'
}

PSWPIN=$(field /proc/vmstat pswpin)
PSWPOUT=$(field /proc/vmstat pswpout)
PGMAJ=$(field /proc/vmstat pgmajfault)
MEMAVAIL=$(field /proc/meminfo MemAvailable:)
SWAPFREE=$(field /proc/meminfo SwapFree:)
BOOT=-; UPTIME=-; STEAL=-
{ IFS= read -r BOOT < /proc/sys/kernel/random/boot_id; } 2>/dev/null || BOOT=-
{ read -r UPTIME _ < /proc/uptime; } 2>/dev/null || UPTIME=-
{ read -r -a CPU < /proc/stat; } 2>/dev/null && STEAL=${CPU[8]:--}

JMAJ=-; JSTART=-; JSWAP=-; JRSS=-
STAT=
if [[ "$PID" != 0 ]] && { IFS= read -r STAT < "/proc/$PID/stat"; } 2>/dev/null; then
  # comm 필드의 공백·괄호를 건너뛴 뒤 필드 3부터 읽는다.
  read -r -a FIELDS <<< "${STAT##*) }"
  JMAJ=${FIELDS[9]:--}
  JSTART=${FIELDS[19]:--}
  JSWAP=$(field "/proc/$PID/status" VmSwap:)
  JRSS=$(field "/proc/$PID/status" VmRSS:)
  # 수집 도중 종료·PID 재사용이 발생하면 Java 측정값을 무효화한다.
  if { IFS= read -r STAT < "/proc/$PID/stat"; } 2>/dev/null; then
    read -r -a FIELDS <<< "${STAT##*) }"
    [[ "${FIELDS[19]:--}" == "$JSTART" ]] || { JMAJ=-; JSTART=-; JSWAP=-; JRSS=-; }
  else
    JMAJ=-; JSTART=-; JSWAP=-; JRSS=-
  fi
fi

LINE=$(printf '%s pid=%s port=%s boot_id=%s uptime_s=%s java_start_ticks=%s pswpin=%s pswpout=%s pgmajfault=%s java_majflt=%s java_vmswap_kb=%s java_rss_kb=%s memavail_kb=%s swapfree_kb=%s cpu_steal_ticks=%s' \
  "$TS" "$PID" "$PORT" "$BOOT" "$UPTIME" "$JSTART" "$PSWPIN" "$PSWPOUT" "$PGMAJ" "$JMAJ" "$JSWAP" "$JRSS" "$MEMAVAIL" "$SWAPFREE" "$STEAL")
for resource in memory io cpu; do
  SOME=-; FULL=-
  if [[ -r "/proc/pressure/$resource" ]]; then
    while read -r kind rest; do
      for item in $rest; do
        [[ "$item" == total=* ]] || continue
        value=${item#total=}
        [[ "$value" =~ ^[0-9]+$ ]] || continue
        case "$kind" in some) SOME=$value ;; full) FULL=$value ;; esac
      done
    done < "/proc/pressure/$resource"
  fi
  # 시스템 전체 CPU full은 정의되지 않으므로 수집하지 않는다.
  LINE+=" psi_${resource}_some_us=$SOME"
  [[ "$resource" == cpu ]] || LINE+=" psi_${resource}_full_us=$FULL"
done
printf '%s\n' "$LINE" >> "$LOG"
