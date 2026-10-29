#!/usr/bin/env bash
set -euo pipefail

for command_name in docker curl; do
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Required command is missing: ${command_name}" >&2
    exit 1
  fi
done

# shellcheck source=blue_green.sh
source /opt/chalkak/bin/blue_green.sh

deployment_id="${DEPLOYMENT_ID:-}"
if [[ ! "${deployment_id}" =~ ^d-[A-Z0-9]+$ ]]; then
  echo "DEPLOYMENT_ID is missing or invalid: '${deployment_id}'" >&2
  exit 1
fi

incoming_jar=/opt/chalkak/incoming/application.jar
releases_dir="${RELEASES_DIR}"
release_dir="${releases_dir}/${deployment_id}"

if [[ ! -f "${incoming_jar}" ]]; then
  echo "Staged jar is missing: ${incoming_jar}" >&2
  exit 1
fi

# 이미지를 미리 받아 두어 ApplicationStart의 docker run이 pull로 지연되지 않게 한다.
docker image inspect "${IMAGE}" >/dev/null 2>&1 || docker pull "${IMAGE}"

# 실행 중인 컨테이너가 쓰는 이전 release는 건드리지 않고 배포별 경로에 새로 둔다.
install -d -m 0750 -o root -g chalkak "${releases_dir}"
install -d -m 0750 -o root -g chalkak "${release_dir}"
mv -f "${incoming_jar}" "${release_dir}/application.jar"
chown root:chalkak "${release_dir}/application.jar"
chmod 0640 "${release_dir}/application.jar"

chown root:root /opt/chalkak/bin/*.sh /opt/chalkak/db/compose.dev.yml
chmod 0700 /opt/chalkak/bin/*.sh
chmod 0644 /opt/chalkak/db/compose.dev.yml

systemctl disable --now chalkak-config.service 2>/dev/null || true
rm -f /run/chalkak/application.env /run/chalkak/postgres.env
