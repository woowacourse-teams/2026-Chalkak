#!/usr/bin/env python3
"""Exercise the management commands without real accounts or launchd changes."""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("manage.sh")


class ManageTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="pr-review-manage-test-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.home = self.root / "home"
        self.bin = self.root / "bin"
        self.home.mkdir()
        self.bin.mkdir()
        (self.bin / "python3").symlink_to(Path(sys.executable).resolve())
        self.write_command("gh", '''
if [ "$1" = "auth" ]; then exit 0; fi
if [ "$1" = "repo" ]; then echo "team/repo"; exit 0; fi
exit 1
''')
        self.write_command("codex", 'exit 0\n')
        self.write_command("claude", 'exit 0\n')
        self.write_command("launchctl", '''
case "$1" in
  bootstrap) touch "${FAKE_LAUNCH_STATE}" ;;
  print) [ -f "${FAKE_LAUNCH_STATE}" ] ;;
  bootout) rm -f "${FAKE_LAUNCH_STATE}" ;;
  enable|kickstart) ;;
  *) exit 1 ;;
esac
''')
        self.environment = dict(os.environ)
        self.environment.update(
            HOME=str(self.home),
            PATH=str(self.bin) + os.pathsep + self.environment["PATH"],
            FAKE_LAUNCH_STATE=str(self.root / "launch-state"),
        )

    def write_command(self, name, body):
        path = self.bin / name
        path.write_text("#!/bin/sh\n" + body, encoding="utf-8")
        path.chmod(0o755)

    def run_manage(self, action, *, input_text=None):
        return subprocess.run(
            [str(SCRIPT), action],
            input=input_text,
            text=True,
            capture_output=True,
            env=self.environment,
            check=False,
        )

    def test_install_configure_stop_start_and_uninstall(self):
        installed = self.run_manage("install", input_text="1\n")
        self.assertEqual(0, installed.returncode, installed.stderr)
        support = self.home / "Library/Application Support/Chalkak PR Review"
        config_path = support / "config.json"
        plist_path = self.home / "Library/LaunchAgents/com.chalkak.backend-pr-review.plist"
        self.assertTrue(config_path.is_file())
        self.assertTrue(plist_path.is_file())
        self.assertEqual("codex", json.loads(config_path.read_text())["provider"])

        status = self.run_manage("status")
        self.assertEqual(0, status.returncode, status.stderr)
        self.assertIn("자동 감시: 실행 중", status.stdout)

        stopped = self.run_manage("stop")
        self.assertEqual(0, stopped.returncode, stopped.stderr)
        self.assertIn("중지했습니다", stopped.stdout)

        started = self.run_manage("start")
        self.assertEqual(0, started.returncode, started.stderr)
        self.assertIn("시작했습니다", started.stdout)

        configured = self.run_manage("configure", input_text="2\n")
        self.assertEqual(0, configured.returncode, configured.stderr)
        self.assertEqual("claude", json.loads(config_path.read_text())["provider"])

        uninstalled = self.run_manage("uninstall")
        self.assertEqual(0, uninstalled.returncode, uninstalled.stderr)
        self.assertFalse(support.exists())
        self.assertFalse(plist_path.exists())


if __name__ == "__main__":
    unittest.main()
