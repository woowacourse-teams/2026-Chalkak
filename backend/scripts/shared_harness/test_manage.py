"""Integration tests using disposable local Git repositories; no AI/network calls."""
import importlib.util
import json
import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("harness_manage", Path(__file__).with_name("manage.py"))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


def command(root, *args):
    return subprocess.run(["git", *args], cwd=root, capture_output=True, check=True).stdout


def repository(root):
    root.mkdir()
    command(root, "init", "-b", "main")
    command(root, "config", "user.name", "Harness Fixture")
    command(root, "config", "user.email", "fixture@example.invalid")
    command(root, "config", "commit.gpgsign", "false")


def commit(root):
    command(root, "add", ".")
    command(root, "commit", "-m", "fixture")


class SharedHarnessTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name).resolve()
        self.addCleanup(self.tmp.cleanup)
        self.source = self.root / "source"
        repository(self.source)
        real = Path(__file__).resolve().parents[3]
        for name in m.ENTRY:
            shutil.copyfile(real / name, self.source / name)
        for name in m.REFERENCES:
            m.write(self.source / name, (real / name).read_bytes())
        for prefix in m.PREFIXES:
            shutil.copytree(real / prefix, self.source / prefix)
        m.write(self.source / "docs/business-rules/obsolete.md", b"old unlinked note\n")
        commit(self.source)
        self.remote = self.root / "remote"
        repository(self.remote)
        self.cache = self.root / "cache"
        self.project = self.root / "android"
        repository(self.project)
        m.write(self.project / "client/android/AGENTS.md", b"Android original\n")
        m.write(self.project / "app.txt", b"client code\n")
        commit(self.project)
        self.publish()
        m.sync(self.cache, str(self.remote), "main")

    def publish(self):
        output = self.root / "export"
        m.export(self.source, output)
        for child in self.remote.iterdir():
            if child.name == ".git":
                continue
            shutil.rmtree(child) if child.is_dir() else child.unlink()
        shutil.copytree(output, self.remote, dirs_exist_ok=True)
        shutil.rmtree(output)
        commit(self.remote)

    def update_policy(self):
        p = self.source / "docs/business-rules/rules/like.md"
        p.write_text(p.read_text() + "\n테스트용 정책 변경\n")
        deleted = self.source / "docs/business-rules/obsolete.md"
        deleted.unlink()
        m.write(self.source / "docs/business-rules/new.md", b"new reference\n")
        commit(self.source)
        self.publish()

    def test_install_version_switch_and_remove_preserve_project(self):
        original = b"Custom root instructions\n"
        (self.project / "AGENTS.md").write_bytes(original)
        head = command(self.project, "rev-parse", "HEAD")
        first = m.activate(self.project, self.cache)["installed"]
        for prefix in m.PREFIXES[:2]:
            skill = self.project / prefix / "SKILL.md"
            self.assertTrue(skill.is_file())
            self.assertTrue((skill.resolve().parent / "../../../docs/business-rules/README.md").resolve().is_file())
        self.update_policy()
        m.sync(self.cache)
        status = m.status(self.project)
        self.assertEqual(first, status["installed"])
        self.assertNotEqual(first, status["available"])
        self.assertIn("docs/business-rules/rules/like.md", status["changed_files"])
        self.assertIn("docs/business-rules/obsolete.md", status["changed_files"])
        m.activate(self.project)
        self.assertFalse((self.project / ".chalkak-harness/active/docs/business-rules/obsolete.md").exists())
        self.assertTrue((self.project / ".chalkak-harness/active/docs/business-rules/new.md").exists())
        self.assertEqual(head, command(self.project, "rev-parse", "HEAD"))
        self.assertEqual(b"", command(self.project, "diff"))
        with (self.project / "AGENTS.md").open("ab") as stream:
            stream.write(b"Extra user instruction\n")
        m.uninstall(self.project)
        self.assertEqual(original + b"Extra user instruction\n", (self.project / "AGENTS.md").read_bytes())
        self.assertFalse((self.project / "CLAUDE.md").exists())
        self.assertEqual(b"Android original\n", (self.project / "client/android/AGENTS.md").read_bytes())

    def test_existing_skill_conflict_changes_no_instructions(self):
        p = self.project / ".agents/skills/business-rules/SKILL.md"
        m.write(p, b"User skill")
        with self.assertRaisesRegex(ValueError, "기존 business-rules"):
            m.activate(self.project, self.cache)
        self.assertFalse((self.project / "AGENTS.md").exists())
        self.assertEqual(b"User skill", p.read_bytes())

    def test_offline_preserves_installed_and_reports_failure(self):
        first = m.activate(self.project, self.cache)["installed"]
        self.remote.rename(self.root / "unreachable")
        result = m.status(self.project, check=True)
        self.assertEqual(first, result["installed"])
        self.assertFalse(result["remote_check"]["ok"])
        self.assertEqual(first, result["available"])

    def test_local_policy_edit_blocks_apply_and_remove(self):
        m.activate(self.project, self.cache)
        p = self.project / ".chalkak-harness/active/docs/business-rules/rules/like.md"
        p.chmod(0o644)  # Owner can override permissions; integrity verification must still reject edits.
        p.write_text("local edits")
        with self.assertRaisesRegex(ValueError, "해시"):
            m.activate(self.project)
        with self.assertRaisesRegex(ValueError, "해시"):
            m.uninstall(self.project)
        self.assertEqual("local edits", p.read_text())

    def test_invalid_release_preserves_previous_cache(self):
        previous = m.read_json(self.cache / "latest.json")
        m.write(self.remote / "application.py", b"not shared")
        commit(self.remote)
        with self.assertRaisesRegex(ValueError, "허용되지"):
            m.sync(self.cache)
        self.assertEqual(previous, m.read_json(self.cache / "latest.json"))
        self.assertFalse(m.read_json(self.cache / "check.json")["ok"])

    def test_symlink_release_rejected(self):
        (self.remote / "docs/business-rules/evil").symlink_to("/tmp")
        commit(self.remote)
        with self.assertRaisesRegex(ValueError, "허용되지"):
            m.sync(self.cache)

    def test_local_symlink_parent_rejected(self):
        outside = self.root / "outside"
        outside.mkdir()
        (self.project / ".agents").symlink_to(outside)
        with self.assertRaisesRegex(ValueError, "심볼릭 링크"):
            m.activate(self.project, self.cache)
        self.assertEqual([], list(outside.iterdir()))

    def test_dirty_source_not_exported(self):
        (self.source / "AGENTS.md").write_text("unreviewed")
        with self.assertRaisesRegex(ValueError, "미커밋"):
            m.export(self.source, self.root / "dirty")
        self.assertFalse((self.root / "dirty").exists())

    def test_install_io_failure_restores_entry_files(self):
        (self.project / "AGENTS.md").write_bytes(b"original")
        original_write = m.write
        def fail(path, data):
            if path.name == "manage.py":
                raise OSError("simulated disk failure")
            return original_write(path, data)
        with patch.object(m, "write", side_effect=fail):
            with self.assertRaises(OSError):
                m.activate(self.project, self.cache)
        self.assertEqual(b"original", (self.project / "AGENTS.md").read_bytes())
        self.assertFalse((self.project / "CLAUDE.md").exists())
        self.assertFalse((self.project / ".agents/skills/business-rules").is_symlink())

    def test_login_plist_only_downloads_no_project_apply(self):
        output = self.root / "login.plist"
        m.login_plist(self.cache, output)
        value = plistlib.loads(output.read_bytes())
        self.assertEqual("sync", value["ProgramArguments"][2])
        self.assertNotIn("--project", value["ProgramArguments"])
        self.assertTrue(value["RunAtLoad"])
        self.assertFalse((self.project / ".chalkak-harness").exists())

    def test_missing_reference_blocks_publication(self):
        (self.source / "docs/business-rules/NOTION_SYNC.md").unlink()
        commit(self.source)
        with self.assertRaisesRegex(ValueError, "로컬 문서 참조"):
            m.export(self.source, self.root / "missing-link")

    def test_status_detects_disconnected_skill(self):
        m.activate(self.project, self.cache)
        (self.project / ".agents/skills/business-rules").unlink()
        with self.assertRaisesRegex(ValueError, "스킬 연결"):
            m.status(self.project)

    def test_activation_failure_restores_first_install(self):
        original_replace = os.replace
        def fail(source, target):
            if Path(target).name == "active":
                raise OSError("simulated activation failure")
            return original_replace(source, target)
        with patch.object(m.os, "replace", side_effect=fail):
            with self.assertRaises(OSError):
                m.activate(self.project, self.cache)
        self.assertFalse((self.project / "AGENTS.md").exists())
        self.assertFalse((self.project / ".chalkak-harness/install.json").exists())
        self.assertFalse((self.project / ".agents/skills/business-rules").is_symlink())

    def test_export_excludes_ignored_untracked_files(self):
        (self.source / ".git/info/exclude").write_text("secret.md\n")
        (self.source / "docs/business-rules/secret.md").write_text("local secret")
        output = self.root / "safe-export"
        m.export(self.source, output)
        self.assertFalse((output / "docs/business-rules/secret.md").exists())

    def test_busy_cache_does_not_report_old_success_as_current(self):
        m.activate(self.project, self.cache)
        with m.lock(self.cache):
            result = m.status(self.project, check=True)
        self.assertFalse(result["remote_check"]["ok"])
        self.assertIn("다른 하네스", result["remote_check"]["error"])

    def test_malformed_manifest_rejected_without_replacing_cache(self):
        previous = m.read_json(self.cache / "latest.json")
        m.write(self.remote / "manifest.json", b"[]")
        commit(self.remote)
        with self.assertRaisesRegex(ValueError, "manifest"):
            m.sync(self.cache)
        self.assertEqual(previous, m.read_json(self.cache / "latest.json"))

    def test_snapshot_rejects_writes_but_updates_remain_possible(self):
        m.activate(self.project, self.cache)
        active = self.project / ".chalkak-harness/active"
        policy = active / "docs/business-rules/rules/like.md"
        self.assertEqual(0, policy.stat().st_mode & 0o222)
        self.assertEqual(0, policy.parent.stat().st_mode & 0o222)
        if os.geteuid() != 0:
            with self.assertRaises(PermissionError):
                policy.write_text("unauthorized edit")
            with self.assertRaises(PermissionError):
                policy.unlink()
            with self.assertRaises(PermissionError):
                m.write(policy, b"replace")
        self.update_policy()
        m.sync(self.cache)
        m.activate(self.project)
        self.assertIn("테스트용", policy.read_text())
        self.assertEqual(0, policy.stat().st_mode & 0o222)
        m.uninstall(self.project)

    def test_status_cli_failure_has_nonzero_exit(self):
        result = subprocess.run([os.sys.executable, m.__file__, "status", "--project", str(self.project)], capture_output=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("하네스 작업 실패".encode(), result.stderr)


if __name__ == "__main__":
    unittest.main()
