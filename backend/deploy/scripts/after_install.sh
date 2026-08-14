#!/usr/bin/env bash
set -euo pipefail

for command_name in java curl; do
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Required command is missing: ${command_name}" >&2
    exit 1
  fi
done

chown chalcak:chalcak /opt/chalcak/app/application.jar
chmod 0640 /opt/chalcak/app/application.jar
chown root:root /opt/chalcak/bin/*.sh /opt/chalcak/db/compose.dev.yml /etc/systemd/system/chalcak-backend.service
chmod 0700 /opt/chalcak/bin/*.sh
chmod 0644 /opt/chalcak/db/compose.dev.yml /etc/systemd/system/chalcak-backend.service

systemctl disable --now chalcak-config.service 2>/dev/null || true
rm -f /run/chalcak/application.env /run/chalcak/postgres.env
systemctl daemon-reload
systemctl enable chalcak-backend.service
