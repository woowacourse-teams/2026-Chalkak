#!/usr/bin/env bash
# set -e는 쓰지 않는다: 실패 종료 코드를 반환하는 함수를 호출하고 그 값을 검사하는 테스트이기 때문이다.
set -uo pipefail

# 배포 서버(root, /opt/chalkak/bin 존재)에서는 실행하지 않는다. 테스트가 실제 레거시 상태를 건드리면 안 된다.
if [[ "${EUID}" -eq 0 && -e /opt/chalkak/bin/blue_green.sh ]]; then
  echo "Refusing to run: this looks like a deploy host (root and /opt/chalkak/bin/blue_green.sh exists)." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HELPER="${SCRIPT_DIR}/blue_green.sh"
T="$(mktemp -d)"; mkdir -p "$T/bin" "$T/rel"
trap 'rm -rf "$T"' EXIT
# 실제 rm을 감싸 픽스처(임시 디렉터리) 밖의 절대 경로를 지우려 하면 기록하고 거부한다.
REAL_RM="$(command -v rm)"; export REAL_RM ESCAPE_FILE="$T/escape" ALLOWED_ROOT="$T"
cat >"$T/bin/rm" <<'S'
#!/usr/bin/env bash
for a; do
  if [[ "$a" == /* && "$a" != "$ALLOWED_ROOT"/* ]]; then echo "rm $a" >>"$ESCAPE_FILE"; exit 99; fi
done
exec "$REAL_RM" "$@"
S
chmod +x "$T/bin/rm"
export PATH="$T/bin:$PATH" UPSTREAM_SNIPPET="$T/up.conf" RELEASES_DIR="$T/rel" \
  LEGACY_APP_DIR="$T/legacy-app" LEGACY_WANTS_LINK="$T/legacy.wants"
# shellcheck disable=SC1090
source "$HELPER"
pass=0; fail=0
check() { if [[ "$2" == "$3" ]]; then pass=$((pass+1)); echo "PASS $1"; else fail=$((fail+1)); echo "FAIL $1: got '$2' want '$3'"; fi; }

for p in 8080 8081; do echo "server 127.0.0.1:$p;" >"$UPSTREAM_SNIPPET"; check "active $p" "$(bg_active_port)" "$p"; done
check idle8080 "$(bg_idle_port 8080)" 8081; check idle8081 "$(bg_idle_port 8081)" 8080
for bad in "server 127.0.0.1:9000;" "garbage" "server 127.0.0.1:8080; server 127.0.0.1:8081;" ""; do
  echo "$bad" >"$UPSTREAM_SNIPPET"; bg_active_port >/dev/null 2>&1; check "garbage rejected: '$bad'" "$?" 1
done
printf 'server 127.0.0.1:8080;\nserver 127.0.0.1:8081 backup;\n' >"$UPSTREAM_SNIPPET"; check "active w/ backup 8080" "$(bg_active_port)" 8080
printf '\nserver 127.0.0.1:8081;\n\nserver 127.0.0.1:8080 backup;\n\n' >"$UPSTREAM_SNIPPET"; check "active w/ backup 8081 blank lines" "$(bg_active_port)" 8081
for bad in $'server 127.0.0.1:8080;\nserver 127.0.0.1:8081;' $'server 127.0.0.1:8080;\nserver 127.0.0.1:8080 backup;' $'server 127.0.0.1:8080;\ngarbage' $'server 127.0.0.1:8081 backup;' $'server 127.0.0.1:8080;\nserver 127.0.0.1:8081 backup;\nserver 127.0.0.1:8081 backup;' $'server 127.0.0.1:8080;\nserver 127.0.0.1:9000 backup;'; do
  printf '%s\n' "$bad" >"$UPSTREAM_SNIPPET"; bg_active_port >/dev/null 2>&1; rc=$?; check "two-line rejected: $(echo "$bad" | tr '\n' '|')" "$rc" 1
done
rm "$UPSTREAM_SNIPPET"; bg_active_port >/dev/null 2>&1; check "missing file" "$?" 1
bg_idle_port 9 >/dev/null 2>&1; check "idle invalid" "$?" 1

# switch: nginx ok
printf '#!/bin/sh\nexit 0\n' >"$T/bin/nginx"; chmod +x "$T/bin/nginx"
echo "server 127.0.0.1:8080;" >"$UPSTREAM_SNIPPET"
bg_switch_upstream 8081 2>/dev/null; check "switch rc" "$?" 0
check "switch content" "$(cat "$UPSTREAM_SNIPPET")" "server 127.0.0.1:8081;"
check "no leftovers" "$(ls "$T" | grep -c 'up.conf\.')" 0
# nginx -t fails -> restore
printf '#!/bin/sh\n[ "$1" = "-t" ] && exit 1\nexit 0\n' >"$T/bin/nginx"
bg_switch_upstream 8080 2>/dev/null; check "switch fail rc" "$?" 1
check "restored content" "$(cat "$UPSTREAM_SNIPPET")" "server 127.0.0.1:8081;"
check "no leftovers after fail" "$(ls "$T" | grep -c 'up.conf\.')" 0

# switch failure injection: backup cp fails / tmp cp fails / printf fails / mv fails
printf '#!/bin/sh\nexit 0\n' >"$T/bin/nginx"
echo "server 127.0.0.1:8080;" >"$UPSTREAM_SNIPPET"
real_cp="$(command -v cp)"; real_mv="$(command -v mv)"
printf '#!/bin/sh\nexit 1\n' >"$T/bin/cp"; chmod +x "$T/bin/cp"
hash -r; bg_switch_upstream 8081 2>/dev/null; check "cp fails rc" "$?" 1
check "cp fails snippet intact" "$(cat "$UPSTREAM_SNIPPET")" "server 127.0.0.1:8080;"
# only the second cp (tmp) fails
cat >"$T/bin/cp" <<C
#!/bin/sh
case "\$3" in *.new) exit 1;; esac
exec $real_cp "\$@"
C
hash -r; bg_switch_upstream 8081 2>/dev/null; check "tmp cp fails rc" "$?" 1
check "tmp cp fails snippet intact" "$(cat "$UPSTREAM_SNIPPET")" "server 127.0.0.1:8080;"
check "tmp cp fails no leftovers" "$(ls "$T" | grep -c 'up.conf\.')" 0
rm "$T/bin/cp"; hash -r
# printf fails: read-only snippet mode is copied to tmp (skipped when running as root)
if [[ $EUID -ne 0 ]]; then
  chmod 444 "$UPSTREAM_SNIPPET"
  bg_switch_upstream 8081 2>/dev/null; check "printf fails rc" "$?" 1
  check "printf fails snippet intact" "$(cat "$UPSTREAM_SNIPPET")" "server 127.0.0.1:8080;"
  check "printf fails no leftovers" "$(ls "$T" | grep -c 'up.conf\.')" 0
  chmod 644 "$UPSTREAM_SNIPPET"
fi
printf '#!/bin/sh\nexit 1\n' >"$T/bin/mv"; chmod +x "$T/bin/mv"
hash -r; bg_switch_upstream 8081 2>/dev/null; check "mv fails rc" "$?" 1
check "mv fails snippet intact" "$(cat "$UPSTREAM_SNIPPET")" "server 127.0.0.1:8080;"
rm "$T/bin/mv"; hash -r

# reconcile: stub curl by port, nginx records reloads
cat >"$T/bin/curl" <<'C'
#!/bin/sh
for a; do u="$a"; done
case "$u" in *:8080/*) [ -e "$HEALTH_8080" ] && exit 0 || exit 22;; *:8081/*) [ -e "$HEALTH_8081" ] && exit 0 || exit 22;; esac
C
chmod +x "$T/bin/curl"
export HEALTH_8080="$T/h8080" HEALTH_8081="$T/h8081"
echo "server 127.0.0.1:8081;" >"$UPSTREAM_SNIPPET"; touch "$HEALTH_8080"; rm -f "$HEALTH_8081"
bg_reconcile 2>/dev/null; check "reconcile switches to healthy" "$(cat "$UPSTREAM_SNIPPET")" "server 127.0.0.1:8080;"
touch "$HEALTH_8081"; rm -f "$HEALTH_8080"
bg_reconcile 2>/dev/null; check "reconcile switches back when active unhealthy" "$(cat "$UPSTREAM_SNIPPET")" "server 127.0.0.1:8081;"
touch "$HEALTH_8080"
bg_reconcile 2>/dev/null; check "reconcile no-op both healthy" "$(cat "$UPSTREAM_SNIPPET")" "server 127.0.0.1:8081;"
rm -f "$HEALTH_8080" "$HEALTH_8081"
bg_reconcile 2>/dev/null; check "reconcile no-op both down" "$(cat "$UPSTREAM_SNIPPET")" "server 127.0.0.1:8081;"
touch "$HEALTH_8080"; printf '#!/bin/sh\n[ "$1" = "-t" ] && exit 1\nexit 0\n' >"$T/bin/nginx"
bg_reconcile 2>/dev/null; check "reconcile switch failure propagates" "$?" 1
check "reconcile failed switch restored" "$(cat "$UPSTREAM_SNIPPET")" "server 127.0.0.1:8081;"

# prune: releases d-A (oldest) .. d-G (newest); labels: active=d-B, other container=d-A
printf 'server 127.0.0.1:8081;\nserver 127.0.0.1:8080 backup;\n' >"$UPSTREAM_SNIPPET"; printf '#!/bin/sh\nexit 0\n' >"$T/bin/nginx"
touch "$HEALTH_8081" "$HEALTH_8080"
bg_reconcile 2>/dev/null; check "reconcile two-line both healthy -> single primary" "$(cat "$UPSTREAM_SNIPPET")" "server 127.0.0.1:8081;"
printf 'server 127.0.0.1:8081;\nserver 127.0.0.1:8080 backup;\n' >"$UPSTREAM_SNIPPET"
rm -f "$HEALTH_8081" "$HEALTH_8080"
bg_reconcile 2>/dev/null; check "reconcile two-line both down untouched" "$(cat "$UPSTREAM_SNIPPET")" $'server 127.0.0.1:8081;\nserver 127.0.0.1:8080 backup;'
touch "$HEALTH_8080"
bg_reconcile 2>/dev/null; check "reconcile two-line -> single other" "$(cat "$UPSTREAM_SNIPPET")" "server 127.0.0.1:8080;"

for i in A B C D E F G; do mkdir "$RELEASES_DIR/d-$i"; done
mkdir "$RELEASES_DIR/other"; 
n=1; for i in A B C D E F G; do touch -t "20260101000$n" "$RELEASES_DIR/d-$i"; n=$((n+1)); done
cat >"$T/bin/docker" <<'D'
#!/bin/sh
if [ "$1" = inspect ]; then echo d-B; exit 0; fi
if [ "$1" = ps ]; then printf 'd-B\nd-A\n\n'; exit 0; fi
D
chmod +x "$T/bin/docker"
bg_prune_releases chalkak-backend-8081
left="$(cd "$RELEASES_DIR" && ls | tr '\n' ' ')"
check "prune result" "$left" "d-A d-B d-F d-G other "

# 주석 줄은 건너뛰고, 전환 시 파일은 깨끗한 줄로 다시 쓴다.
printf '# managed by hand\nserver 127.0.0.1:8080;\n   # indented comment\nserver 127.0.0.1:8081 backup;\n' >"$UPSTREAM_SNIPPET"
check "comment lines skipped" "$(bg_active_port)" 8080
printf '#server 127.0.0.1:8081;\n' >"$UPSTREAM_SNIPPET"; bg_active_port >/dev/null 2>&1; check "comment-only snippet rejected" "$?" 1
printf '# note\nserver 127.0.0.1:8080;\n' >"$UPSTREAM_SNIPPET"
printf '#!/bin/sh\nexit 0\n' >"$T/bin/nginx"
bg_switch_upstream 8081 2>/dev/null; check "switch drops comments" "$(cat "$UPSTREAM_SNIPPET")" "server 127.0.0.1:8081;"

# bg_stop_old: 포트 점유 확인과 레거시 PID 종료 경로. 시스템 명령은 모두 스텁이며 kill/sleep은 함수로 대체한다.
CALLS="$T/calls"; LISTEN="$T/listen"; mkdir -p "$LISTEN"
export CALLS LISTEN
cat >"$T/bin/ss" <<'S'
#!/usr/bin/env bash
echo "ss $*" >>"$CALLS"
port="${*: -1}"; port="${port##*:}"
[[ -s "$LISTEN/$port" ]] || exit 0
if [[ "$1" == *p* ]]; then
  printf 'LISTEN 0 511 127.0.0.1:%s 0.0.0.0:* users:(("java",pid=%s,fd=9))\n' "$port" "$(cat "$LISTEN/$port")"
else
  printf 'LISTEN 0 511 127.0.0.1:%s 0.0.0.0:*\n' "$port"
fi
S
cat >"$T/bin/systemctl" <<'S'
#!/usr/bin/env bash
echo "systemctl $*" >>"$CALLS"
[[ "$1" == is-active ]] && exit 3
[[ "$1" == is-enabled ]] && exit 1
exit 0
S
cat >"$T/bin/docker" <<'S'
#!/usr/bin/env bash
echo "docker $*" >>"$CALLS"
case "$1" in
  inspect) [[ -z "${NO_CONTAINER:-}" ]] || exit 1 ;;
  stop)
    [[ -n "${DOCKER_STOP_FAILS:-}" ]] && exit 1
    name="${*: -1}"; rm -f "$LISTEN/${name##*-}"
    ;;
  rm)
    if [[ "$2" == -f && -n "${RM_F_CLEARS:-}" ]]; then rm -f "$LISTEN/${3##*-}"; fi
    ;;
esac
exit 0
S
chmod +x "$T/bin/ss" "$T/bin/systemctl" "$T/bin/docker"
# RESPAWN=<포트>이면 안정화 대기(sleep 3) 중 같은 포트에 프로세스가 되살아난 것처럼 만든다.
sleep() {
  echo "sleep $*" >>"$CALLS"
  if [[ "$1" == 3 && -n "${RESPAWN:-}" ]]; then echo 99 >"$LISTEN/${RESPAWN}"; fi
}
real_cleanup_legacy_body="$(declare -f bg_cleanup_legacy)"
# 실제 레거시 정리는 /etc, /opt를 지우므로 테스트에서는 호출 기록만 남긴다.
bg_cleanup_legacy() { echo "cleanup_legacy" >>"$CALLS"; }
kill() {
  echo "kill $*" >>"$CALLS"
  [[ -n "${NO_KILL:-}" ]] && return 0
  if [[ "$1" == -KILL || -z "${IGNORE_TERM:-}" ]]; then
    local f
    for f in "$LISTEN"/*; do [[ -f "$f" && "$(cat "$f")" == "$2" ]] && rm -f "$f"; done
  fi
  return 0
}
stop_case() {
  : >"$CALLS"; rm -f "$LISTEN"/*
  unset NO_CONTAINER DOCKER_STOP_FAILS IGNORE_TERM NO_KILL RM_F_CLEARS RESPAWN
}

stop_case; echo 4242 >"$LISTEN/8080"; export NO_CONTAINER=1
bg_stop_old 8080; check "legacy: still listening -> rc" "$?" 0
check "legacy: systemctl stop attempted" "$(grep -c 'systemctl stop chalkak-backend.service' "$CALLS")" 1
check "legacy: SIGTERM to listening pid" "$(grep -c '^kill -TERM 4242$' "$CALLS")" 1
check "legacy: cleanup after port freed" "$(grep -c '^cleanup_legacy$' "$CALLS")" 1
check "legacy: no SIGKILL needed" "$(grep -c '^kill -KILL' "$CALLS")" 0
check "legacy: port-free check ran after kill" "$([[ "$(grep -c '^ss ' "$CALLS")" -ge 3 ]] && echo yes)" yes

stop_case; echo 4242 >"$LISTEN/8080"; export NO_CONTAINER=1; IGNORE_TERM=1
bg_stop_old 8080 2>/dev/null; check "legacy ignoring TERM -> rc" "$?" 0
check "legacy ignoring TERM: waited 40s" "$(grep -c '^sleep 1$' "$CALLS")" 41
check "legacy ignoring TERM: SIGKILL sent" "$(grep -c '^kill -KILL 4242$' "$CALLS")" 1

stop_case; echo 4242 >"$LISTEN/8080"; export NO_CONTAINER=1; NO_KILL=1
err="$(bg_stop_old 8080 2>&1 >/dev/null)"; rc=$?
check "unkillable -> rc" "$rc" 1
check "unkillable -> message" "$([[ "$err" == *"still in use"* ]] && echo yes)" yes

stop_case; echo 77 >"$LISTEN/8081"; export DOCKER_STOP_FAILS=1
bg_stop_old 8081 2>/dev/null; check "docker stop fails, port busy -> killed, rc" "$?" 0
check "docker stop fails: SIGTERM to listening pid" "$(grep -c '^kill -TERM 77$' "$CALLS")" 1
check "docker stop fails: no legacy systemctl stop for 8081" "$(grep -c 'systemctl stop' "$CALLS")" 0

stop_case; echo 88 >"$LISTEN/8081"
bg_stop_old 8081 2>/dev/null; check "container stopped cleanly -> rc" "$?" 0
check "container stopped cleanly: no kill" "$(grep -c '^kill' "$CALLS")" 0
check "container stopped cleanly: docker rm" "$(grep -c '^docker rm chalkak-backend-8081$' "$CALLS")" 1

stop_case; export NO_CONTAINER=1
bg_stop_old 8081 2>/dev/null; check "nothing running -> rc" "$?" 0
unset NO_CONTAINER

# docker stop이 실패하면 재시작 정책을 끄고 컨테이너를 먼저 제거한다(PID kill 전에). 제거로 포트가 비면 kill은 필요 없다.
stop_case; echo 55 >"$LISTEN/8081"; export DOCKER_STOP_FAILS=1 RM_F_CLEARS=1
bg_stop_old 8081 2>/dev/null; check "stop fails, rm -f clears -> rc" "$?" 0
check "stop fails: restart policy disabled" "$(grep -c '^docker update --restart=no chalkak-backend-8081$' "$CALLS")" 1
check "stop fails: update before rm -f" "$([[ "$(grep -n '^docker update' "$CALLS" | cut -d: -f1)" -lt "$(grep -n '^docker rm -f' "$CALLS" | cut -d: -f1)" ]] && echo yes)" yes
check "stop fails: no PID kill after rm -f" "$(grep -c '^kill' "$CALLS")" 0

# 정리 후 잠시 뒤 같은 포트에 프로세스가 되살아나면(재시작 정책 등) 성공으로 보지 않는다.
stop_case; echo 66 >"$LISTEN/8081"; export DOCKER_STOP_FAILS=1 RESPAWN=8081
bg_stop_old 8081 2>/dev/null; check "respawn after settle -> rc" "$?" 1
check "respawn: settle wait ran" "$(grep -c '^sleep 3$' "$CALLS")" 1

# 실제 bg_cleanup_legacy: 임시 경로 기준으로 흔적이 있을 때만 정리하고, 없으면 아무것도 하지 않는다.
eval "${real_cleanup_legacy_body/bg_cleanup_legacy/real_bg_cleanup_legacy}"
stop_case; mkdir -p "$LEGACY_APP_DIR"; touch "$LEGACY_WANTS_LINK"
real_bg_cleanup_legacy
check "cleanup removes app dir" "$([[ -e "$LEGACY_APP_DIR" ]] && echo exists || echo gone)" gone
check "cleanup removes wants link" "$([[ -e "$LEGACY_WANTS_LINK" ]] && echo exists || echo gone)" gone
check "cleanup ran disable + daemon-reload" "$(grep -cE '^systemctl (disable|daemon-reload)' "$CALLS")" 2
stop_case; real_bg_cleanup_legacy
check "cleanup no-op without legacy traces" "$(grep -c '^systemctl disable\|^systemctl daemon-reload' "$CALLS")" 0
check "no rm escaped the fixture" "$([[ -e "$ESCAPE_FILE" ]] && cat "$ESCAPE_FILE" || echo none)" none
if [[ $fail -gt 0 ]]; then
  echo "${fail} of $((pass + fail)) blue_green checks failed." >&2
  exit 1
fi
echo "All $((pass + fail)) blue_green checks passed."
