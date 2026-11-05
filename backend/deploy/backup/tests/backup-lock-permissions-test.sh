#!/usr/bin/env bash
set -euo pipefail
umask 077

# 격리된 Linux 컨테이너의 root로만 실행한다. 운영 서버에서는 실행하지 않는다.
[[ $EUID -eq 0 ]] || { echo '격리된 Linux 컨테이너의 root로 실행하세요.' >&2; exit 1; }
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)
root=$(mktemp -d)
background_pid=''
cleanup() {
  [[ -z "$background_pid" ]] || { touch "$root/release" "$root/finish-create"; wait "$background_pid" 2>/dev/null || true; }
  rm -rf -- "$root"
}
trap cleanup EXIT
trap 'printf "FAIL: line=%s\n" "$LINENO" >&2; cat "$root/output" >&2' ERR
id chalkak-backup >/dev/null 2>&1 || useradd --system --user-group --shell /usr/sbin/nologin chalkak-backup
chmod 755 "$root"
mkdir -p "$root/bin" "$root/deploy/scripts" "$root/deploy/backup" "$root/deploy/systemd" \
  "$root/config" "$root/code" "$root/state" "$root/units"
chmod 755 "$root/bin" "$root/deploy" "$root/deploy/backup" "$root/code"
chmod 750 "$root/config"
chown root:chalkak-backup "$root/config"
chown chalkak-backup:chalkak-backup "$root/state"
cp "$script_dir/backup/"*.sh "$root/deploy/backup/"
cp "$script_dir/systemd/chalkak-backup.service" "$root/deploy/systemd/"
sed -e "s|/var/lib/chalkak-backup-lock|$root/locks|g" \
  -e "s|/etc/chalkak-backup|$root/config|g" \
  -e "s|/opt/chalkak-backup|$root/code|g" \
  -e "s|/var/lib/chalkak-backup|$root/state|g" \
  -e "s|/etc/systemd/system|$root/units|g" \
  -e "s|/var/log/chalkak-backup|$root/logs|g" \
  "$script_dir/scripts/install_backup.sh" > "$root/deploy/scripts/install_backup.sh"
printf '#!/bin/bash\n[[ "$*" == daemon-reload ]]\n' > "$root/bin/systemctl"
chmod 755 "$root/bin/systemctl"
export BACKUP_PERMISSION_TEST_ROOT="$root"
cat > "$root/bin/chown" <<'MOCK'
#!/bin/bash
if [[ "${2:-}" == "$BACKUP_PERMISSION_TEST_ROOT/locks/backup.lock" && -f "$BACKUP_PERMISSION_TEST_ROOT/pause-create" ]]; then
  touch "$BACKUP_PERMISSION_TEST_ROOT/creating-lock"
  while [[ ! -f "$BACKUP_PERMISSION_TEST_ROOT/finish-create" ]]; do sleep 0.05; done
fi
exec /usr/bin/chown "$@"
MOCK
chmod 755 "$root/bin/chown"
export PATH="$root/bin:$PATH"
config="$root/config/backup.env"
printf 'BACKUP_LOCK_FILE=%s/locks/backup.lock\nBACKUP_STATE_DIRECTORY=%s/state\n' "$root" "$root" > "$config"
chmod 600 "$config"
chown chalkak-backup:chalkak-backup "$config"
runner="$root/deploy/scripts/install_backup.sh"
lock_file="$root/locks/backup.lock"

# 파일 생성과 권한 설정 사이에 다른 설치기가 들어와도 같은 inode를 교체하지 않아야 한다.
touch "$root/pause-create"
bash "$runner" > "$root/output" 2>&1 &
background_pid=$!
for ((attempt=0; attempt<100; attempt++)); do
  [[ ! -f "$root/creating-lock" ]] || break
  sleep 0.05
done
[[ -f "$root/creating-lock" ]]
creating_inode=$(stat -c %i "$lock_file")
status=0
bash "$runner" > "$root/concurrent-output" 2>&1 || status=$?
[[ $status -ne 0 && $(stat -c %i "$lock_file") == "$creating_inode" ]]
touch "$root/finish-create"
wait "$background_pid"
background_pid=''
rm "$root/pause-create"
echo 'PASS: 최초 설치 중복 시 준비 중인 잠금 파일 교체 차단'

# 최초 설치기는 root로 실행하고, 설치된 공통 잠금은 실제 백업 사용자로 연다.
[[ $(stat -c '%U:%G:%a' "$root/locks") == root:chalkak-backup:750 ]]
[[ $(stat -c '%U:%G:%a' "$lock_file") == root:chalkak-backup:660 ]]
[[ $(stat -c '%U:%G:%a' "$root/locks/full-backup.lock") == root:chalkak-backup:660 ]]
runuser -u chalkak-backup -- env BACKUP_LOCK_FILE="$lock_file" bash -c '
  source "$1"
  backup_lock
  backup_full_lock
' bash "$root/code/current/backup-lock.sh"
echo 'PASS: 최초 root 설치 후 실제 백업 사용자가 잠금 획득'

inode=$(stat -c %i "$lock_file")
full_inode=$(stat -c %i "$root/locks/full-backup.lock")
printf 'preserved\n' > "$lock_file"
bash "$runner" > "$root/output" 2>&1
[[ $(stat -c %i "$lock_file") == "$inode" && $(cat "$lock_file") == preserved ]]
[[ $(stat -c %i "$root/locks/full-backup.lock") == "$full_inode" ]]
echo 'PASS: 재설치가 잠금 inode와 내용을 교체하지 않음'

if runuser -u chalkak-backup -- rm "$lock_file" 2>/dev/null; then exit 1; fi
if runuser -u chalkak-backup -- mv "$root/locks" "$root/moved" 2>/dev/null; then exit 1; fi
if runuser -u chalkak-backup -- chmod 666 "$lock_file" 2>/dev/null; then exit 1; fi
echo 'PASS: 백업 사용자의 잠금 삭제·디렉터리 이동·권한 변경 차단'

# 백업 사용자가 잡은 잠금은 root 설치기도 같은 inode에서 존중해야 한다.
runuser -u chalkak-backup -- env BACKUP_LOCK_FILE="$lock_file" bash -c '
  source "$1"
  backup_lock
  touch "$2/state/ready"
  while [[ ! -f "$2/release" ]]; do sleep 0.05; done
' bash "$root/code/current/backup-lock.sh" "$root" &
background_pid=$!
for ((attempt=0; attempt<100; attempt++)); do
  [[ ! -f "$root/state/ready" ]] || break
  sleep 0.05
done
[[ -f "$root/state/ready" ]]
current=$(readlink "$root/code/current")
status=0
bash "$runner" > "$root/output" 2>&1 || status=$?
[[ $status -eq 75 && $(readlink "$root/code/current") == "$current" ]]
touch "$root/release"
wait "$background_pid"
background_pid=''
echo 'PASS: 실제 백업 사용자 실행 중 root 설치기의 코드 교체 차단'

mv "$lock_file" "$root/original.lock"
printf 'untouched\n' > "$root/victim"
ln -s "$root/victim" "$lock_file"
status=0
bash "$runner" > "$root/output" 2>&1 || status=$?
[[ $status -ne 0 && $(cat "$root/victim") == untouched ]]
rm "$lock_file"
mv "$root/original.lock" "$lock_file"
echo 'PASS: 잠금 심볼릭 링크 거부, 대상 파일 유지'

ln "$lock_file" "$root/other-link"
status=0
bash "$runner" > "$root/output" 2>&1 || status=$?
[[ $status -ne 0 ]]
rm "$root/other-link"
chmod 770 "$root/locks"
status=0
bash "$runner" > "$root/output" 2>&1 || status=$?
[[ $status -ne 0 && $(stat -c %a "$root/locks") == 770 ]]
echo 'PASS: 하드 링크·백업 사용자가 변경 가능한 잠금 디렉터리 거부'
