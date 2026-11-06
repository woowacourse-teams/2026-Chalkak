#!/usr/bin/env bash
set -euo pipefail
umask 077
# 서비스·앱 명령은 대역, 권한·flock·timeout은 실제 Linux 동작. 운영 서버에서 실행하지 않는다.
[[ $EUID -eq 0 && ! -e /etc/chalkak-backup/backup.env ]] || exit 1
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
root=$(mktemp -d)
pids=()
cleanup() {
  touch "$root/release"
  for pid in "${pids[@]}"; do wait "$pid" 2>/dev/null || true; done
  rm -rf -- "$root"
}
trap cleanup EXIT
trap 'printf "FAIL line=%s\n" "$LINENO" >&2; cat "$root/output" "$root/begin-output" >&2' ERR
id chalkak-backup >/dev/null 2>&1 || useradd --system --user-group --shell /usr/sbin/nologin chalkak-backup
chmod 755 "$root"
mkdir -p "$root/scripts" "$root/config" "$root/code" "$root/state" "$root/bin"
chmod 755 "$root/scripts" "$root/bin"
chown chalkak-backup:chalkak-backup "$root/state"
chmod 750 "$root/config"
chown root:chalkak-backup "$root/config"
for script in deployment_backup_guard.sh install_backup.sh; do
  sed -e "s|/var/lib/chalkak-backup-lock|$root/locks|g" \
    -e "s|/etc/chalkak-backup|$root/config|g" \
    -e "s|/opt/chalkak-backup|$root/code|g" \
    -e "s|/var/lib/chalkak-backup|$root/state|g" \
    "$script_dir/$script" > "$root/scripts/$script"
done
cp "$script_dir/../backup/backup-lock.sh" "$root/scripts/backup-lock.sh"
chmod 644 "$root/scripts/backup-lock.sh"
cat > "$root/scripts/blue_green.sh" <<'MOCK'
bg_active_port() { echo 8081; }
bg_idle_port() { echo 8080; }
bg_health_ok() { [[ ! -e "$TEST_ROOT/unhealthy" ]]; }
bg_container_running() { [[ -e "$TEST_ROOT/old-running" ]]; }
bg_port_busy() { [[ -e "$TEST_ROOT/old-listener" ]]; }
MOCK
cat > "$root/bin/systemctl" <<'MOCK'
#!/bin/bash
printf '%s\n' "$*" >> "$TEST_ROOT/systemctl.log"
case "$*" in
  'is-active --quiet chalkak-backup.timer') [[ -e "$TEST_ROOT/timer-active" ]] ;;
  'stop chalkak-backup.timer') rm -f "$TEST_ROOT/timer-active" ;;
  'start chalkak-backup.timer') [[ ! -e "$TEST_ROOT/fail-start" ]] && touch "$TEST_ROOT/timer-active" ;;
  *) exit 99 ;;
esac
MOCK
printf '#!/bin/bash\necho unexpected-docker >> "$TEST_ROOT/forbidden"\nexit 99\n' > "$root/bin/docker"
chmod 755 "$root/bin/systemctl" "$root/bin/docker"
export PATH="$root/bin:$PATH" TEST_ROOT="$root" DEPLOYMENT_ID=d-FIRST
guard="$root/scripts/deployment_backup_guard.sh"
marker="$root/locks/deployment"
config="$root/config/backup.env"
bash "$guard" begin > "$root/output"
[[ ! -e "$root/locks" && ! -e "$root/systemctl.log" ]]
echo 'PASS: 미구성 서버는 배포 차단·서비스 변경 생략'
printf 'BACKUP_LOCK_FILE=%s/locks/backup.lock\nBACKUP_STATE_DIRECTORY=%s/state\nBACKUP_MAX_RUNTIME_SECONDS=2\n' "$root" "$root" > "$config"
chmod 600 "$config"
chown chalkak-backup:chalkak-backup "$config"
bash "$root/scripts/install_backup.sh" --prepare-locks
[[ ! -e "$root/code/current" ]]
echo 'PASS: BeforeInstall 준비는 보호 잠금만 생성, 코드·서비스 교체 없음'
status=0
bash "$guard" begin > "$root/output" 2>&1 || status=$?
[[ $status -ne 0 && ! -e "$marker" && ! -e "$root/systemctl.log" ]]
mkdir "$root/code/current"
cp "$root/scripts/backup-lock.sh" "$root/code/current/backup-lock.sh"
echo 'PASS: 배포 표시를 모르는 옛 백업 코드에서는 배포 시작 거부' 

try_backup() {
  runuser -u chalkak-backup -- env BACKUP_LOCK_FILE="$root/locks/backup.lock" \
    /bin/bash -c 'set -e; source "$1"; backup_lock; touch "$2/state/new-backup"' bash "$root/scripts/backup-lock.sh" "$root"
}
expect_blocked() {
  local status=0
  try_backup > "$root/output" 2>&1 || status=$?
  [[ $status -eq 75 && ! -e "$root/state/new-backup" ]]
  grep -q reason=app-deployment "$root/output"
}

# 실행 중인 부분 백업의 자식은 계속 일하고, 다른 새 백업만 차단한다.
runuser -u chalkak-backup -- env BACKUP_LOCK_FILE="$root/locks/backup.lock" \
  bash -c '
    source "$1/scripts/backup-lock.sh"; backup_lock
    touch "$1/state/running"
    while [[ ! -e "$1/release" ]]; do sleep 0.05; done
    bash -c '\''source "$1/scripts/backup-lock.sh"; backup_lock; touch "$1/state/child-finished"'\'' bash "$1"
  ' bash "$root" &
holder=$!; pids+=("$holder")
while [[ ! -e "$root/state/running" ]]; do sleep 0.05; done
touch "$root/timer-active"
bash "$guard" begin > "$root/begin-output" 2>&1 &
begin_pid=$!; pids+=("$begin_pid")
for ((i=0; i<100; i++)); do
  [[ -e "$marker/timer-active" && ! -e "$root/timer-active" ]] && break
  sleep 0.02
done
[[ -e "$marker" && ! -e "$marker/ready" && ! -e "$root/timer-active" ]]
kill -0 "$holder"
touch "$root/release"
wait "$holder"; wait "$begin_pid"
[[ -e "$root/state/child-finished" && -e "$marker/ready" ]]
expect_blocked
echo 'PASS: 기존 백업·자식 정상 완료 대기, 배포 동안 새 백업 차단'

bash "$guard" check
status=0
DEPLOYMENT_ID=d-OTHER bash "$guard" check > "$root/output" 2>&1 || status=$?
[[ $status -ne 0 ]]
if runuser -u chalkak-backup -- rmdir "$marker" 2>/dev/null; then exit 1; fi
echo 'PASS: 다른 배포의 표시 재사용·백업 사용자의 표시 삭제 거부'

for failure in unhealthy old-running old-listener fail-start; do
  touch "$root/$failure"
  status=0
  bash "$guard" finish > "$root/output" 2>&1 || status=$?
  [[ $status -ne 0 && -e "$marker/ready" ]]
  expect_blocked
  rm "$root/$failure"
done
echo 'PASS: 앱 장애·이전 앱/포트 잔존·예약 재개 실패에는 차단 유지'
bash "$guard" finish
[[ ! -e "$marker" && -e "$root/timer-active" ]]
try_backup
[[ -e "$root/state/new-backup" ]]
rm "$root/state/new-backup"
echo 'PASS: 앱 정상·이전 앱 종료 후에만 차단 해제와 기존 예약 재개'

rm "$root/timer-active"
bash "$guard" begin > "$root/output"
bash "$guard" finish > "$root/output"
[[ ! -e "$marker" && ! -e "$root/timer-active" ]]
echo 'PASS: 배포 전 꺼진 타이머는 자동 활성화하지 않음'

# 기존 백업은 timeout의 자식이 아니다. 대기 프로세스만 종료되고 기존 작업·앱은 그대로다.
exec 9<> "$root/locks/backup.lock"
flock --exclusive 9
touch "$root/timer-active"
status=0
bash "$guard" begin 9>&- > "$root/output" 2>&1 || status=$?
[[ $status -eq 124 && ! -e "$marker" && -e "$root/timer-active" ]]
if flock --exclusive --nonblock "$root/locks/backup.lock" true; then exit 1; fi
flock --unlock 9
exec 9>&-
echo 'PASS: 대기 상한 초과는 배포만 실패, 기존 백업 유지·예약 원복'

# 전체 예약 대기 표시도 존중한다. 배포 대기를 취소해도 기존 전체 작업의 잠금은 유지한다.
exec 8<> "$root/locks/full-backup.lock"
flock --exclusive 8
bash "$guard" begin 8>&- > "$root/begin-output" 2>&1 &
begin_pid=$!; pids+=("$begin_pid")
for ((i=0; i<100; i++)); do
  [[ -e "$marker/timer-active" && ! -e "$root/timer-active" ]] && break
  sleep 0.02
done
[[ -e "$marker" && ! -e "$marker/ready" ]]
expect_blocked
kill -TERM "$begin_pid"
status=0
wait "$begin_pid" || status=$?
[[ $status -eq 143 && ! -e "$marker" && -e "$root/timer-active" ]]
if flock --exclusive --nonblock "$root/locks/full-backup.lock" true; then exit 1; fi
flock --unlock 8
exec 8>&-
echo 'PASS: 전체 예약 대기도 존중, 배포 대기 취소는 기존 작업을 종료하지 않음'

# 비정상 종료한 배포는 다음 배포가 표시를 덮어쓰거나 시간 경과로 해제하지 않는다.
bash "$guard" begin > "$root/output"
status=0
DEPLOYMENT_ID=d-OTHER bash "$guard" begin > "$root/output" 2>&1 || status=$?
[[ $status -ne 0 && $(cat "$marker/id") == d-FIRST ]]
expect_blocked
bash "$guard" finish > "$root/output"
echo 'PASS: 배포 중단 뒤 새 배포도 차단, 명시적 확인·해제 후 재개'

# 준비 대기 중 강제 중단으로 ready가 없어도, 사람이 배포 종료·앱 상태를 확인한 후 복구할 수 있다.
bash "$guard" begin > "$root/output"
rm "$marker/ready"
status=0
bash "$guard" check > "$root/output" 2>&1 || status=$?
[[ $status -ne 0 ]]
expect_blocked
bash "$guard" finish > "$root/output"
[[ ! -e "$marker" && -e "$root/timer-active" ]]
echo 'PASS: 준비 미완료 표시는 앱 시작 차단, 배포 종료 확인 후 수동 해제 가능'

# 훅 연결을 실제 파일로 대조한다. 새 앱 시작 전에 check가 실패하면 Docker/설정 명령에 도달하지 않는다.
cp "$script_dir/start_service.sh" "$root/scripts/start_service.sh"
status=0
bash "$root/scripts/start_service.sh" > "$root/output" 2>&1 || status=$?
[[ $status -ne 0 ]]
grep -q '백업 차단 표시' "$root/output"
grep -q 'deployment_backup_guard.sh" begin' "$script_dir/before_install.sh"
grep -q 'deployment_backup_guard.sh" check' "$script_dir/after_install.sh"
echo 'PASS: 차단 준비 없는 새 앱 시작 거부, BeforeInstall·AfterInstall 연결'
