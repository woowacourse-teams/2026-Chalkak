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
        for provider in a.PROVIDERS:
            for source in ("resume", "compact"):
                result = self.event(provider=provider, source=source)
                self.assertNotIn("continue", result)
                self.assertIn("새 세션", result["systemMessage"])
                self.assertIn("고정 버전이 없습니다", result["hookSpecificOutput"]["additionalContext"])
        self.assertFalse((self.project / ".chalkak-harness/sessions").exists())

    def test_non_git_directory_is_ignored_for_both_providers(self):
        self.setup_auto()
        other = self.root / "not a repository"
        other.mkdir()
        for provider in a.PROVIDERS:
            self.assertEqual({}, self.event(provider=provider, project=other))
        self.assertEqual([], list(other.iterdir()))

    def test_unavailable_git_and_timeout_do_not_block_global_hook(self):
        self.setup_auto()
        for error in (FileNotFoundError("git"), subprocess.TimeoutExpired("git", 60)):
            with self.subTest(error=error), patch.object(a, "identity", side_effect=error):
                self.assertEqual({}, self.event())

    def test_documented_provider_payloads_work_through_installed_cli(self):
        # Official schema fixtures, not a claim of desktop app event capture.
        self.setup_auto()
        root = a.runtime(self.home)
        for provider in a.PROVIDERS:
            event = json.loads((Path(__file__).parent / "fixtures" / (provider + "-session-start.json")).read_text())
            event["cwd"] = str(self.project)
            process = subprocess.run([sys.executable, str(root / "manage.py"), "session",
                                      "--runtime", str(root), "--provider", provider],
                                     input=json.dumps(event), text=True, capture_output=True, check=True)
            result = json.loads(process.stdout)
            self.assertNotIn("continue", result)
            self.assertIn(self.pin(event["session_id"], provider)["version"],
                          result["hookSpecificOutput"]["additionalContext"])

    def test_missing_source_is_not_silently_treated_as_new_session(self):
        self.setup_auto()
        with self.assertRaises(ValueError):
            a.session(a.runtime(self.home), "codex", {"cwd": str(self.project), "session_id": "missing-source"})
        self.assertFalse((self.project / ".chalkak-harness/sessions").exists())

    def test_python_path_change_replaces_owned_agent_and_hooks(self):
        self.setup_auto()
        with patch.object(a.sys, "executable", "/new/python3"), \
             patch.object(a, "unregister_agent", return_value=True) as stop:
            self.setup_auto()
        stop.assert_called_once()
        definition = plistlib.loads(a.agent_path(self.home).read_bytes())
        self.assertEqual("/new/python3", definition["ProgramArguments"][0])
        self.assertEqual(definition, m.read_json(a.runtime(self.home) / "agent-owned.json"))
        self.assertIn("/new/python3", m.read_json(a.config_path(self.home, "codex"))["hooks"]["SessionStart"][0]["hooks"][0]["command"])

    def test_legacy_agent_migration_uses_old_recorded_interpreter(self):
        self.setup_auto()
        (a.runtime(self.home) / "agent-owned.json").unlink()
        with patch.object(a.sys, "executable", "/new/python3"), patch.object(a, "unregister_agent", return_value=True):
            self.setup_auto()
        self.assertEqual("/new/python3", m.read_json(a.runtime(self.home) / "agent-owned.json")["ProgramArguments"][0])

    def test_reinstall_recovers_deleted_plist_with_or_without_loaded_service(self):
        self.setup_auto()
        path = a.agent_path(self.home)
        previous = plistlib.loads(path.read_bytes())
        for loaded in (True, False):
            with self.subTest(loaded=loaded):
                path.unlink()
                with patch.object(a, "unregister_agent", return_value=loaded) as stop:
                    self.setup_auto()
                stop.assert_called_once_with(path, previous)
                self.assertEqual(previous, plistlib.loads(path.read_bytes()))
                for provider in a.PROVIDERS:
                    self.assertEqual(1, len(m.read_json(a.config_path(self.home, provider))["hooks"]["SessionStart"]))

    def test_uninstall_removes_hooks_when_plist_was_deleted(self):
        for loaded in (True, False):
            with self.subTest(loaded=loaded):
                self.setup_auto()
                path = a.agent_path(self.home)
                previous = plistlib.loads(path.read_bytes())
                path.unlink()
                with patch.object(a, "unregister_agent", return_value=loaded) as stop:
                    a.uninstall(self.project, self.home)
                stop.assert_called_once_with(path, previous)
                self.assertFalse(path.exists())
                self.assertFalse((a.runtime(self.home) / "agent-owned.json").exists())
                self.assertFalse((self.project / ".chalkak-harness/automatic.json").exists())
                self.assertEqual({}, a.registry(a.runtime(self.home))["repositories"])
                for provider in a.PROVIDERS:
                    self.assertEqual([], m.read_json(a.config_path(self.home, provider))["hooks"]["SessionStart"])

    def test_deleted_legacy_plist_can_be_reinstalled_from_recorded_hook(self):
        self.setup_auto()
        a.agent_path(self.home).unlink()
        (a.runtime(self.home) / "agent-owned.json").unlink()
        with patch.object(a, "unregister_agent", return_value=True):
            self.setup_auto()
        self.assertEqual(a.agent_definition(a.runtime(self.home)), plistlib.loads(a.agent_path(self.home).read_bytes()))

    def test_deleted_plist_with_invalid_ownership_does_not_stop_other_service(self):
        self.setup_auto()
        a.agent_path(self.home).unlink()
        owned = a.runtime(self.home) / "agent-owned.json"
        definition = m.read_json(owned)
        definition["Label"] = "other.service"
        m.write(owned, m.json_bytes(definition))
        with patch.object(a, "unregister_agent") as stop:
            with self.assertRaisesRegex(ValueError, "소유권"):
                self.setup_auto()
        stop.assert_not_called()

    def test_unregister_deleted_plist_uses_service_target(self):
        path = self.root / "missing.plist"
        definition = a.agent_definition(a.runtime(self.root))
        target = f"gui/{a.os.getuid()}/{definition['Label']}"
        for loaded in (True, False):
            with self.subTest(loaded=loaded), patch.object(a.subprocess, "run") as run:
                run.return_value.returncode = 0 if loaded else 113
                self.assertEqual(loaded, a.unregister_agent(path, definition))
                self.assertEqual(["launchctl", "print", target], run.call_args_list[0].args[0])
                self.assertEqual(2 if loaded else 1, run.call_count)
                if loaded:
                    self.assertEqual(["launchctl", "bootout", target], run.call_args_list[1].args[0])

    def test_failed_reinstall_restores_loaded_service_without_recreating_deleted_plist(self):
        self.setup_auto()
        path = a.agent_path(self.home)
        previous = plistlib.loads(path.read_bytes())
        path.unlink()
        owned = a.runtime(self.home) / "agent-owned.json"
        before = owned.read_bytes()
        registrations = []

        def register(plist):
            registrations.append(plistlib.loads(plist.read_bytes()))
            if len(registrations) == 1:
                raise OSError("bootstrap failed")
            return True

        with patch.object(a.sys, "platform", "darwin"), patch.object(a.sys, "executable", "/new/python3"), \
             patch.object(a, "unregister_agent", return_value=True), \
             patch.object(a, "register_agent", side_effect=register):
            with self.assertRaisesRegex(OSError, "bootstrap failed"):
                a.setup(self.project, self.home, str(self.remote), "main")
        self.assertEqual("/new/python3", registrations[0]["ProgramArguments"][0])
        self.assertEqual(previous, registrations[1])
        self.assertFalse(path.exists())
        self.assertEqual(before, owned.read_bytes())

    def test_failed_uninstall_restores_hooks_and_service_with_deleted_plist(self):
        self.setup_auto()
        path = a.agent_path(self.home)
        previous = plistlib.loads(path.read_bytes())
        path.unlink()
        registry = a.runtime(self.home) / "projects.json"
        originals = {p: p.read_bytes() for p in (registry, *(a.config_path(self.home, p) for p in a.PROVIDERS))}
        write = a.FileChanges.write

        def fail_registry(changes, target, contents):
            if target == registry:
                raise OSError("registry write failed")
            return write(changes, target, contents)

        restored = []
        with patch.object(a.FileChanges, "write", fail_registry), \
             patch.object(a, "unregister_agent", return_value=True), \
             patch.object(a, "register_agent", side_effect=lambda p: restored.append(plistlib.loads(p.read_bytes()))):
            with self.assertRaisesRegex(OSError, "registry write failed"):
                a.uninstall(self.project, self.home)
        self.assertEqual([previous], restored)
        self.assertFalse(path.exists())
        self.assertTrue((self.project / ".chalkak-harness/automatic.json").exists())
        for p, content in originals.items():
            self.assertEqual(content, p.read_bytes())

    def test_edited_owned_agent_is_not_overwritten(self):
        self.setup_auto()
        path = a.agent_path(self.home)
        definition = plistlib.loads(path.read_bytes())
        definition["StartInterval"] = 1
        path.write_bytes(plistlib.dumps(definition))
        with self.assertRaisesRegex(ValueError, "자동 실행 설정이 수정"):
            self.setup_auto()
        self.assertEqual(definition, plistlib.loads(path.read_bytes()))

    def test_failed_agent_replacement_restores_files_and_old_registration(self):
        self.setup_auto()
        paths = [a.agent_path(self.home), a.runtime(self.home) / "agent-owned.json",
                 a.runtime(self.home) / "hooks-owned.json", a.config_path(self.home, "codex")]
        before = {p: p.read_bytes() for p in paths}
        with patch.object(a.sys, "platform", "darwin"), patch.object(a.sys, "executable", "/new/python3"), \
             patch.object(a, "unregister_agent", return_value=True), \
             patch.object(a, "register_agent", side_effect=[OSError("bootstrap failed"), True]) as register:
            with self.assertRaisesRegex(OSError, "bootstrap failed"):
                a.setup(self.project, self.home, str(self.remote), "main")
        self.assertEqual(2, register.call_count)
        for path, value in before.items():
            self.assertEqual(value, path.read_bytes())

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
