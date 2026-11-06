#!/usr/bin/env bash
set -euo pipefail
root=$(mktemp -d)
trap 'rm -rf -- "$root"' EXIT
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
export TEST_ROOT="$root"
mkdir -p "$root/bin" "$root/deploy/scripts" "$root/deploy/backup" "$root/deploy/systemd" \
  "$root/etc/chalkak-backup" "$root/opt/chalkak-backup" "$root/state" "$root/units"
# Linux의 실제 flock·심볼릭 링크 교체를 사용한다. 서비스·사용자·설치 대상은 임시 환경으로 격리한다.
# 테스트용 경로와 root 검사만 바꾸고 나머지는 운영 스크립트 그대로 실행한다.
sed -e "s|/var/lib/chalkak-backup-lock|$root/locks|g" \
  -e "s|/etc/chalkak-backup|$root/etc/chalkak-backup|g" \
  -e "s|/opt/chalkak-backup|$root/opt/chalkak-backup|g" \
  -e "s|/var/lib/chalkak-backup|$root/state|g" \
  -e "s|/etc/systemd/system|$root/units|g" \
  -e "s|/var/log/chalkak-backup|$root/logs|g" \
  -e 's/\[\[ $EUID -eq 0 \]\]/true/' \
  "$script_dir/install_backup.sh" > "$root/deploy/scripts/install_backup.sh"
cat > "$root/bin/install" <<'MOCK'
#!/usr/bin/env bash
args=()
while (( $# )); do
  case "$1" in -o|-g) shift 2;; *) args+=("$1"); shift;; esac
done
/usr/bin/install "${args[@]}"
MOCK
cat > "$root/bin/runuser" <<'MOCK'
#!/usr/bin/env bash
shift 3
exec "$@"
MOCK
cat > "$root/bin/id" <<'MOCK'
#!/usr/bin/env bash
exit 0
MOCK
printf '#!/bin/bash\nexit 0\n' > "$root/bin/chown"
cat > "$root/bin/systemctl" <<'MOCK'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$TEST_ROOT/systemctl.log"
[[ "$*" == daemon-reload ]] || exit 99
[[ ! -e "$TEST_ROOT/fail-reload" ]]
MOCK
for tool in docker rclone pg_dump pg_restore; do
  cat > "$root/bin/$tool" <<'MOCK'
#!/usr/bin/env bash
echo '백업·복구 실행 금지' >> "$TEST_ROOT/forbidden"
exit 99
MOCK
done
chmod +x "$root/bin/"*
export PATH="$root/bin:$PATH"
runner="$root/deploy/scripts/install_backup.sh"
config="$root/etc/chalkak-backup/backup.env"
current="$root/opt/chalkak-backup/current"
for file in backup.sh backup-lock.sh db-backup.sh image-backup.sh scheduled-backup.sh report-backup-status.sh; do
  printf '#!/bin/bash\nprintf "실행 금지\\n" >> "$TEST_ROOT/forbidden"\n' > "$root/deploy/backup/$file"
done
printf '비밀 예제\n' > "$root/deploy/backup/backup.env.example"
printf '#!/bin/bash\nexit 99\n' > "$root/deploy/backup/db-restore.sh"
cp "$script_dir/../systemd/chalkak-backup.service" "$root/deploy/systemd/"
bash "$runner" > "$root/output"
[[ ! -e "$current" && ! -e "$root/systemctl.log" ]]
echo 'PASS: 미구성 서버는 변경 없이 통과'
printf 'BACKUP_LOCK_FILE=%s/locks/backup.lock\nBACKUP_STATE_DIRECTORY=%s/state\n' "$root" "$root" > "$config"
printf '비밀 DB 암호\n' > "$root/etc/chalkak-backup/pgpass"
printf '비밀 S3 인증\n' > "$root/etc/chalkak-backup/rclone.conf"
cp -Rp "$root/etc/chalkak-backup" "$root/secret-before"
mkdir "$root/locks"
touch "$root/locks/backup.lock"
chmod 660 "$root/locks/backup.lock"
exec 8<> "$root/locks/backup.lock"
flock -x 8
rc=0
bash "$runner" > "$root/output" 2>&1 || rc=$?
[[ $rc -eq 75 && ! -e "$current" && ! -e "$root/systemctl.log" ]]
flock -u 8
echo 'PASS: 백업 잠금이 잡혀 있으면 파일 교체와 서비스 변경 거부'
bash "$runner" > "$root/output"
first=$(readlink "$current")
[[ -d "$first" && -f "$first/backup.sh" && ! -e "$first/db-restore.sh" && ! -e "$first/backup.env.example" ]]
[[ $(grep -c '^daemon-reload$' "$root/systemctl.log") -eq 1 ]]
diff -r "$root/secret-before" "$root/etc/chalkak-backup"
[[ ! -e "$root/forbidden" ]]
[[ -f "$first/report-backup-status.sh" && $(stat -c '%a' "$root/logs/status.log") == 644 ]]
printf '기존 종료 기록\n' > "$root/logs/status.log"
cp "$root/logs/status.log" "$root/status-before"
echo 'PASS: 실행 파일만 설치하고 비밀 설정 유지, 백업·복구·예약 시작 없음'
# 이미 시작된 프로세스의 작업 디렉터리는 current 교체 뒤에도 이전 release에 고정돼야 한다.
(cd "$current"; pwd -P > "$root/pinned"; touch "$root/ready"; while [[ ! -e "$root/continue" ]]; do sleep 0.05; done; cmp backup.sh "$first/backup.sh") &
reader=$!
while [[ ! -e "$root/ready" ]]; do sleep 0.05; done
printf '#!/bin/bash\nexit 0\n' > "$root/deploy/backup/backup.sh"
bash "$runner" > "$root/output"
second=$(readlink "$current")
cmp "$root/status-before" "$root/logs/status.log"
[[ "$first" != "$second" && -d "$first" && $(cat "$root/pinned") == "$first" ]]
touch "$root/continue"
wait "$reader"
echo 'PASS: 회차별 실행 파일 고정, 교체 전 release 보존'
cp "$root/units/chalkak-backup.service" "$root/unit-before"
touch "$root/fail-reload"
rc=0
bash "$runner" > "$root/output" 2>&1 || rc=$?
[[ $rc -ne 0 && $(readlink "$current") == "$second" ]]
cmp "$root/unit-before" "$root/units/chalkak-backup.service"
rm "$root/fail-reload"
echo 'PASS: 반영 실패 시 이전 코드와 서비스 파일 복구'
printf '#!/bin/bash\nif\n' > "$root/deploy/backup/backup.sh"
rc=0
bash "$runner" > "$root/output" 2>&1 || rc=$?
[[ $rc -ne 0 && $(readlink "$current") == "$second" ]]
echo 'PASS: 문법 오류가 있는 코드는 교체 전에 거부'
[[ ! -e "$root/forbidden" ]]
diff -r "$root/secret-before" "$root/etc/chalkak-backup"
[[ $(grep -vc '^daemon-reload$' "$root/systemctl.log") -eq 0 ]]
echo 'PASS: 모든 성공·실패 경로에서 설정 보존 및 실행 금지 유지'

# 이 테스트는 설치기 실패 전파를 검증한다. 배포 전체 차단은 deployment_backup_guard_test에서 검증한다.
printf '#!/bin/bash\n[[ "$1" == check ]]\n' > "$root/deploy/scripts/deployment_backup_guard.sh"
# 실제 AfterInstall도 백업 갱신 실패를 무시하지 않고 뒤 단계를 중단해야 한다.
mkdir -p "$root/app/bin" "$root/app/db" "$root/app/incoming" "$root/run"
printf 'RELEASES_DIR=%q\nIMAGE=fixture\n' "$root/app/releases" > "$root/app/bin/blue_green.sh"
touch "$root/app/bin/example.sh" "$root/app/db/compose.dev.yml"
sed -e "s|/opt/chalkak/|$root/app/|g" -e "s|/run/chalkak/|$root/run/|g" \
  "$script_dir/after_install.sh" > "$root/deploy/scripts/after_install.sh"
printf '\ntouch "$TEST_ROOT/hook-complete"\n' >> "$root/deploy/scripts/after_install.sh"
cat > "$root/bin/docker" <<'MOCK'
#!/usr/bin/env bash
[[ "$*" == 'image inspect fixture' ]]
MOCK
for tool in chown curl; do
  printf '#!/bin/bash\nexit 0\n' > "$root/bin/$tool"
done
chmod +x "$root/bin/"*
export DEPLOYMENT_ID=d-FIXTURE
flock -x 8
touch "$root/app/incoming/application.jar"
rc=0
bash "$root/deploy/scripts/after_install.sh" > "$root/output" 2>&1 || rc=$?
[[ $rc -eq 75 && ! -e "$root/hook-complete" && $(readlink "$current") == "$second" ]]
flock -u 8
echo 'PASS: 백업 실행 중이면 실제 AfterInstall도 실패하여 후속 단계 중단'
touch "$root/app/incoming/application.jar"
rc=0
bash "$root/deploy/scripts/after_install.sh" > "$root/output" 2>&1 || rc=$?
[[ $rc -ne 0 && ! -e "$root/hook-complete" && $(readlink "$current") == "$second" ]]
diff -r "$root/secret-before" "$root/etc/chalkak-backup"
[[ ! -e "$root/forbidden" ]]
echo 'PASS: 백업 코드 문법 오류도 실제 AfterInstall 실패로 전달'
