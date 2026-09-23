"""Automatic setup and session isolation with local Git remotes and fake HOME."""
import json
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import plistlib
import subprocess
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parent))
import automatic as a
import test_manage as fixtures

command, repository, m = fixtures.command, fixtures.repository, fixtures.m


class AutomaticHarnessTests(unittest.TestCase):
    setUp = fixtures.SharedHarnessTests.setUp
    publish = fixtures.SharedHarnessTests.publish
    update_policy = fixtures.SharedHarnessTests.update_policy

    def setup_auto(self):
        self.home = self.root / "fake home"
        self.home.mkdir(exist_ok=True)
        with patch.object(a.sys, "platform", "darwin"), patch.object(a, "register_agent", return_value=True):
            return a.setup(self.project, self.home, str(self.remote), "main")

    def event(self, session_id="first", provider="codex", project=None, source="startup"):
        return a.session(a.runtime(self.home), provider, {"session_id": session_id, "cwd": str(project or self.project), "source": source})

    def pin(self, session_id="first", provider="codex", project=None):
        key = m.digest((provider + "\0" + session_id).encode())
        return m.read_json((project or self.project) / ".chalkak-harness/sessions" / (key + ".json"))

    def test_setup_preserves_existing_hooks_and_registers_download_only(self):
        self.home = self.root / "fake home"
        original = {"hooks": {"SessionStart": [{"hooks": [{"type": "command", "command": "echo user hook"}]}]}, "unrelated": 123}
        for provider in a.PROVIDERS:
            m.write(a.config_path(self.home, provider), m.json_bytes(original))
        self.setup_auto()
        self.setup_auto()
        for provider in a.PROVIDERS:
            value = m.read_json(a.config_path(self.home, provider))
            self.assertEqual(123, value["unrelated"])
            self.assertEqual(original["hooks"]["SessionStart"][0], value["hooks"]["SessionStart"][0])
            self.assertEqual(2, len(value["hooks"]["SessionStart"]))
        agent = plistlib.loads(a.agent_path(self.home).read_bytes())
        self.assertEqual(300, agent["StartInterval"])
        self.assertTrue(agent["RunAtLoad"])
        self.assertEqual("sync", agent["ProgramArguments"][2])
        self.assertNotIn("apply", agent["ProgramArguments"])
        self.assertFalse((self.project / ".chalkak-harness/active").exists())

    def test_new_sessions_get_updates_while_resumed_and_other_provider_stay_pinned(self):
        self.setup_auto()
        first = self.event("same", "codex")
        old = self.pin("same", "codex")["version"]
        old_document = self.project / ".chalkak-harness/versions" / old / "docs/business-rules/obsolete.md"
        self.assertTrue(old_document.exists())
        self.update_policy()
        second = self.event("same", "claude")
        new = self.pin("same", "claude")["version"]
        self.assertNotEqual(old, new)
        self.assertIn(new, second["hookSpecificOutput"]["additionalContext"])
        for source in ("resume", "compact", "startup"):
            resumed = self.event("same", "codex", source=source)
            self.assertIn(old, resumed["hookSpecificOutput"]["additionalContext"])
            self.assertEqual(old, self.pin("same", "codex")["version"])
        self.assertTrue(old_document.exists())
        self.assertFalse((self.project / ".chalkak-harness/versions" / new / "docs/business-rules/obsolete.md").exists())
        self.assertEqual(b"", command(self.project, "diff"))

    def test_worktree_first_session_installs_without_manual_setup(self):
        self.setup_auto()
        worktree = self.root / "android task"
        command(self.project, "worktree", "add", "-b", "client/task", str(worktree))
        result = self.event("worktree", project=worktree)
        self.assertIn(str(worktree), result["hookSpecificOutput"]["additionalContext"])
        self.assertTrue((worktree / ".agents/skills/business-rules/SKILL.md").is_file())
        self.assertTrue((worktree / ".claude/skills/business-rules/SKILL.md").is_file())
        self.assertEqual(b"Android original\n", (worktree / "client/android/AGENTS.md").read_bytes())

    def test_simultaneous_codex_claude_sessions_do_not_fail_on_registry_lock(self):
        self.setup_auto()
        with ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(lambda provider: self.event("parallel", provider), a.PROVIDERS))
        self.assertEqual(2, len(results))
        self.assertEqual(self.pin("parallel", "codex")["version"], self.pin("parallel", "claude")["version"])

    def test_generated_files_are_locally_ignored_without_changing_tracked_files(self):
        self.setup_auto()
        self.event()
        self.assertEqual(b"", command(self.project, "status", "--porcelain"))
        exclude = self.project / ".git/info/exclude"
        with exclude.open("ab") as stream:
            stream.write(b"user-notes.txt\n")
        with patch.object(a, "unregister_agent", return_value=True):
            a.uninstall(self.project, self.home)
        self.assertIn(b"user-notes.txt", exclude.read_bytes())
        self.assertNotIn(a.exclude_block(), exclude.read_bytes())

    def test_unrelated_repository_has_no_effect(self):
        self.setup_auto()
        other = self.root / "other"
        repository(other)
        self.assertEqual({}, self.event("unrelated", project=other))
        self.assertFalse((other / ".chalkak-harness").exists())

    def test_offline_new_session_reports_failure_and_uses_valid_previous_package(self):
        self.setup_auto()
        self.event()
        old = self.pin()["version"]
        self.remote.rename(self.root / "offline")
        result = self.event("offline")
        self.assertIn("최신 정책 확인 실패", result["systemMessage"])
        self.assertEqual(old, self.pin("offline")["version"])

    def test_corrupt_existing_snapshot_is_rejected(self):
        self.setup_auto()
        self.event()
        doc = self.project / ".chalkak-harness/versions" / self.pin()["version"] / "AGENTS.md"
        doc.chmod(0o644)
        doc.write_text("tampered")
        with self.assertRaisesRegex(ValueError, "해시"):
            self.event(source="resume")

    def test_preexisting_session_requires_new_session_instead_of_changing_its_rules(self):
        self.setup_auto()
        with self.assertRaisesRegex(ValueError, "새 세션"):
            self.event(source="resume")
        self.assertFalse((self.project / ".chalkak-harness/sessions").exists())

    def test_setup_launchd_failure_restores_existing_settings_and_project(self):
        self.home = self.root / "fake home"
        original = b'{"custom": true}\n'
        m.write(a.config_path(self.home, "codex"), original)
        m.write(self.project / "AGENTS.md", b"User instructions\n")
        with patch.object(a.sys, "platform", "darwin"), patch.object(a, "register_agent", side_effect=OSError("registration failed")):
            with self.assertRaisesRegex(OSError, "registration failed"):
                a.setup(self.project, self.home, str(self.remote), "main")
        self.assertEqual(original, a.config_path(self.home, "codex").read_bytes())
        self.assertEqual(b"User instructions\n", (self.project / "AGENTS.md").read_bytes())
        self.assertFalse((self.project / ".agents/skills/business-rules").exists())
        self.assertFalse((self.project / ".chalkak-harness/automatic.json").exists())
        self.setup_auto()  # Rolled back setup is retryable.

    def test_migrate_legacy_install_preserves_user_instructions_and_old_versions(self):
        (self.project / "AGENTS.md").write_bytes(b"User instructions\n")
        old = m.activate(self.project, self.cache)["installed"]
        self.setup_auto()
        self.assertTrue((self.project / "AGENTS.md").read_bytes().startswith(b"User instructions\n"))
        self.assertNotIn(m.MARKER, (self.project / "AGENTS.md").read_text())
        self.assertTrue((self.project / ".chalkak-harness/versions" / old).is_dir())
        self.event()

    def test_failed_legacy_migration_restores_symlinks_and_manual_install(self):
        old = m.activate(self.project, self.cache)["installed"]
        self.home = self.root / "fake home"
        with patch.object(a.sys, "platform", "darwin"), patch.object(a, "register_agent", side_effect=OSError("registration failed")):
            with self.assertRaises(OSError):
                a.setup(self.project, self.home, str(self.remote), "main")
        self.assertEqual(old, m.status(self.project)["installed"])
        self.assertTrue((self.project / ".agents/skills/business-rules").is_symlink())

    def test_existing_unmanaged_skill_is_not_replaced(self):
        p = self.project / ".agents/skills/business-rules/SKILL.md"
        m.write(p, b"user skill")
        with self.assertRaisesRegex(ValueError, "기존 business-rules"):
            self.setup_auto()
        self.assertEqual(b"user skill", p.read_bytes())
        self.assertFalse(a.config_path(self.home, "codex").exists())

    def test_uninstall_preserves_user_additions_and_other_hooks(self):
        self.setup_auto()
        self.event()
        with (self.project / "AGENTS.md").open("ab") as stream:
            stream.write(b"Added user instructions\n")
        path = a.config_path(self.home, "codex")
        config = m.read_json(path)
        custom = {"hooks": [{"type": "command", "command": "echo user"}]}
        config["hooks"]["SessionStart"].append(custom)
        m.write(path, m.json_bytes(config))
        with patch.object(a, "unregister_agent", return_value=True):
            result = a.uninstall(self.project, self.home)
        self.assertTrue(result["removed"])
        self.assertEqual(b"Added user instructions\n", (self.project / "AGENTS.md").read_bytes())
        self.assertEqual([custom], m.read_json(path)["hooks"]["SessionStart"])
        self.assertTrue((self.project / ".chalkak-harness/sessions").is_dir())
        self.assertFalse((self.project / ".agents/skills/business-rules").exists())
        self.setup_auto()

    def test_edited_hook_blocks_uninstall_and_restores_project(self):
        self.setup_auto()
        before = (self.project / "AGENTS.md").read_bytes()
        path = a.config_path(self.home, "codex")
        config = m.read_json(path)
        config["hooks"]["SessionStart"][0]["hooks"][0]["command"] = "echo edited"
        m.write(path, m.json_bytes(config))
        with self.assertRaisesRegex(ValueError, "hook이 수정"):
            a.uninstall(self.project, self.home)
        self.assertEqual(before, (self.project / "AGENTS.md").read_bytes())
        self.assertTrue((self.project / ".agents/skills/business-rules").is_symlink())

    def test_cli_session_uses_copied_runtime_and_json_input(self):
        self.setup_auto()
        root = a.runtime(self.home)
        p = subprocess.run([sys.executable, str(root / "manage.py"), "session", "--runtime", str(root), "--provider", "codex"],
                           input=json.dumps({"cwd": str(self.project), "session_id": "cli", "source": "startup"}), capture_output=True, text=True)
        self.assertEqual(0, p.returncode, p.stderr)
        self.assertEqual("SessionStart", json.loads(p.stdout)["hookSpecificOutput"]["hookEventName"])
        self.assertNotIn("continue", json.loads(p.stdout))

    def test_cli_hook_failure_is_visible_and_not_successful_policy_context(self):
        self.setup_auto()
        root = a.runtime(self.home)
        p = subprocess.run([sys.executable, str(root / "manage.py"), "session", "--runtime", str(root), "--provider", "claude"],
                           input=json.dumps({"cwd": str(self.project)}), capture_output=True, text=True)
        result = json.loads(p.stdout)
        self.assertFalse(result["continue"])
        self.assertIn("연결 실패", result["systemMessage"])


if __name__ == "__main__":
    unittest.main()
