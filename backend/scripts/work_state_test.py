"""실제 임시 Git 저장소로 로컬 기록의 병합·검증 시점·동시 저장을 확인한다."""

from concurrent.futures import ThreadPoolExecutor
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock


SCRIPT = Path(__file__).with_name("work_state.py")
SPEC = importlib.util.spec_from_file_location("work_state", SCRIPT)
state = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(state)


@unittest.skipUnless(state.fcntl is not None, "macOS/Linux 파일 잠금 검사")
class WorkStateTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="work-state-test-")
        self.addCleanup(temporary.cleanup)
        self.repo = Path(temporary.name).resolve()
        self.root = self.repo / "backend"
        self.root.mkdir()
        self.code = self.root / "example.txt"
        self.code.write_text("original\n")
        (self.root / ".gitignore").write_text("ignored.txt\n")
        self.git("init", "-b", "be/fix/#7-example")
        self.git("add", ".")
        self.git("commit", "-qm", "fixture")
        self.work = {"goal": "README 안내 보완", "done": [], "remaining": ["문구 수정"], "next": "README 수정",
                     "stack": {"child": {"parent_sha": "old-parent", "remote_sha": "old-remote"}}}

    def git(self, *arguments):
        environment = {key: value for key, value in os.environ.items() if not key.startswith("GIT_")}
        environment.update(GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_NOSYSTEM="1")
        return subprocess.check_output(["git", "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
                                        "-c", "commit.gpgSign=false", *arguments], cwd=self.repo,
                                       env=environment, stderr=subprocess.PIPE).decode().strip()

    def create(self):
        return state.save(self.root, 7, "missing", {"work": self.work})

    def cli(self, action, *arguments, payload=None):
        return subprocess.run([sys.executable, "-B", str(SCRIPT), action, "--root", str(self.root), *arguments],
                              input=None if payload is None else json.dumps(payload), capture_output=True, text=True, timeout=15)

    def test_cli_missing_create_and_partial_overwrite_keep_one_latest_record(self):
        missing = self.cli("load", "--issue", "7")
        self.assertEqual(0, missing.returncode)
        self.assertEqual("missing", json.loads(missing.stdout)["revision"])
        created = self.cli("save", "--issue", "7", "--expected-revision", "missing", "--input", "-", payload={"work": self.work})
        self.assertEqual(0, created.returncode, created.stdout)
        first = json.loads(created.stdout)
        updated = state.save(self.root, 7, first["revision"], {"work": {"done": ["초안 작성"], "stack": {"child": {"remote_sha": "new-remote"}}}})
        self.assertNotEqual(first["revision"], updated["revision"])
        self.assertEqual("old-parent", updated["record"]["work"]["stack"]["child"]["parent_sha"])
        self.assertEqual("new-remote", updated["record"]["work"]["stack"]["child"]["remote_sha"])
        self.assertEqual(self.work["goal"], updated["record"]["work"]["goal"])
        loaded = state.load(self.root, 7)
        self.assertTrue(loaded["matches"]["record"])
        self.assertFalse(loaded["matches"]["verification"])
        self.assertEqual(["issue-7.json"], [path.name for path in state.directory(self.root).glob("*.json")])

    def test_fingerprint_detects_code_index_head_and_untracked_but_not_generated_or_external_symlink_content(self):
        fingerprint = lambda: state.snapshot(self.root)["fingerprint"]
        original = fingerprint()
        self.code.write_text("edited\n")
        unstaged = fingerprint()
        self.assertNotEqual(original, unstaged)
        self.git("add", "backend/example.txt")
        staged = fingerprint()
        self.assertNotEqual(unstaged, staged)
        self.git("commit", "-qm", "change")
        committed = fingerprint()
        self.assertNotEqual(staged, committed)
        (self.root / "new.txt").write_text("new\n")
        untracked = fingerprint()
        self.assertNotEqual(committed, untracked)
        (self.root / "ignored.txt").write_text("ignored\n")
        (self.root / "build").mkdir()
        (self.root / "build/result.txt").write_text("generated\n")
        self.assertEqual(untracked, fingerprint())
        self.create()
        self.assertEqual(untracked, fingerprint())
        outside = self.repo / "outside.txt"
        outside.write_text("private original\n")
        (self.root / "link.txt").symlink_to(outside)
        linked = fingerprint()
        outside.write_text("private changed\n")
        self.assertEqual(linked, fingerprint())
        self.assertNotIn("private changed", json.dumps(state.snapshot(self.root)))
        self.code.unlink()
        self.assertNotEqual(linked, fingerprint())

    def test_verification_stays_at_checked_fingerprint_and_is_not_refreshed_by_save(self):
        created = self.create()
        checked = state.snapshot(self.root)["fingerprint"]
        payload = {"checks": [{"command": "python3 -m unittest", "result": "8 tests passed, exit 0", "log": "build/checks.log"}]}
        verified = state.save(self.root, 7, created["revision"], payload, True, checked)
        self.assertTrue(state.load(self.root, 7)["matches"]["verification"])
        verification = verified["record"]["verification"]
        self.code.write_text("changed after checks\n")
        path = state.issue_path(self.root, 7)
        previous_bytes = path.read_bytes()
        with self.assertRaises(ValueError):
            state.save(self.root, 7, verified["revision"], payload, True, checked)
        self.assertEqual(previous_bytes, path.read_bytes())
        saved = state.save(self.root, 7, verified["revision"], {"work": {"done": ["문구 수정"]}})
        self.assertEqual(verification, saved["record"]["verification"])
        self.assertTrue(state.load(self.root, 7)["matches"]["record"])
        self.assertFalse(state.load(self.root, 7)["matches"]["verification"])
        with self.assertRaises(ValueError):
            state.save(self.root, 7, saved["revision"], payload)
        with self.assertRaises(ValueError):
            state.save(self.root, 7, saved["revision"], payload, True)

    def test_concurrent_stale_saves_and_failed_replace_preserve_record(self):
        created = self.create()

        def update(next_action):
            return self.cli("save", "--issue", "7", "--expected-revision", created["revision"], "--input", "-", payload={"work": {"next": next_action}})

        with ThreadPoolExecutor(max_workers=2) as executor:
            results = list(executor.map(update, ("first action", "second action")))
        self.assertEqual([0, 1], sorted(result.returncode for result in results))
        loaded = state.load(self.root, 7)
        self.assertIn(loaded["record"]["work"]["next"], ("first action", "second action"))
        path = state.issue_path(self.root, 7)
        previous = path.read_bytes()
        with self.assertRaises(ValueError):
            state.save(self.root, 7, created["revision"], {"work": {"next": "stale"}})
        with mock.patch.object(state.os, "replace", side_effect=OSError("replacement failed")):
            with self.assertRaises(OSError):
                state.save(self.root, 7, loaded["revision"], {"work": {"next": "must not replace"}})
        self.assertEqual(previous, path.read_bytes())
        self.assertFalse(list(path.parent.glob(".write-*")))

    def test_branch_issue_and_backend_mismatch_cannot_overwrite_record(self):
        created = self.create()
        path = state.issue_path(self.root, 7)
        previous = path.read_bytes()
        self.git("checkout", "-b", "be/fix/#8-other")
        self.assertFalse(state.load(self.root, 7)["matches"]["record"])
        with self.assertRaises(ValueError):
            state.save(self.root, 7, created["revision"], {"work": {"next": "wrong issue"}})
        self.git("checkout", "-b", "be/fix/#7-different-branch")
        with self.assertRaises(ValueError):
            state.save(self.root, 7, created["revision"], {"work": {"next": "wrong branch"}})
        other_backend = self.repo / "other-backend"
        other_backend.mkdir()
        other_folder = state.directory(other_backend, create=True)
        (other_folder / "issue-7.json").write_bytes(previous)
        with self.assertRaises(ValueError):
            state.load(other_backend, 7)
        self.assertEqual(previous, path.read_bytes())

    def test_invalid_size_issue_and_symlink_state_paths_leave_data_intact(self):
        created = self.create()
        path = state.issue_path(self.root, 7)
        previous = path.read_bytes()
        for patch in ({"work": {"goal": "x" * state.LIMIT}}, {"work": {"unknown": "not allowed"}}, {"work": {"done": "not an array"}}):
            with self.subTest(patch=list(patch["work"])):
                with self.assertRaises(ValueError):
                    state.save(self.root, 7, created["revision"], patch)
                self.assertEqual(previous, path.read_bytes())
        for issue in (0, -1, "../outside"):
            with self.assertRaises(ValueError):
                state.issue_path(self.root, issue)
        for field, malformed in (("verification", "wrong"), ("verification", []), ("code_state", []),
                                 ("code_state", {}), ("verification", {"code_state": "wrong"})):
            with self.subTest(field=field, malformed=malformed):
                value = json.loads(previous)
                value[field] = malformed
                path.write_text(json.dumps(value))
                with self.assertRaises(ValueError):
                    state.load(self.root, 7)
        path.write_bytes(previous)
        outside = self.repo / "outside-record.json"
        outside.write_bytes(previous)
        path.unlink()
        path.symlink_to(outside)
        with self.assertRaises(OSError):
            state.save(self.root, 7, created["revision"], {"work": {"next": "must not follow"}})
        self.assertEqual(previous, outside.read_bytes())
        path.unlink()
        folder = path.parent
        folder.rename(folder.with_name("original-state"))
        folder.symlink_to(self.repo, target_is_directory=True)
        with self.assertRaises(ValueError):
            state.load(self.root, 7)

    def test_remove_requires_current_revision_and_removes_only_selected_issue(self):
        first = self.create()
        self.git("checkout", "-b", "be/fix/#8-other")
        second = state.save(self.root, 8, "missing", {"work": self.work})
        with self.assertRaises(ValueError):
            state.remove(self.root, 7, "stale")
        removed = state.remove(self.root, 7, first["revision"])
        self.assertTrue(removed["removed"])
        self.assertEqual("missing", state.load(self.root, 7)["revision"])
        self.assertEqual(second["revision"], state.load(self.root, 8)["revision"])
        with self.assertRaises(ValueError):
            state.remove(self.root, 7, first["revision"])


if __name__ == "__main__":
    unittest.main()
