#!/usr/bin/env bash
set -euo pipefail

for command_name in java curl; do
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Required command is missing: ${command_name}" >&2
    exit 1
  fi
done

chown chalkak:chalkak /opt/chalkak/app/application.jar
chmod 0640 /opt/chalkak/app/application.jar
chown root:root /opt/chalkak/bin/*.sh /opt/chalkak/db/compose.dev.yml /etc/systemd/system/chalkak-backend.service
chmod 0700 /opt/chalkak/bin/*.sh
chmod 0644 /opt/chalkak/db/compose.dev.yml /etc/systemd/system/chalkak-backend.service

systemctl disable --now chalkak-config.service 2>/dev/null || true
rm -f /run/chalkak/application.env /run/chalkak/postgres.env
systemctl daemon-reload
systemctl enable chalkak-backend.service
