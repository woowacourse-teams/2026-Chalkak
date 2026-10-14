"""필수 검사에서 실패·누락을 숨기지 않는지 확인한다."""
import contextlib
import io
import subprocess
import unittest
from unittest import mock
import verify_harness


class VerifyHarnessTests(unittest.TestCase):
    def test_all_automatic_suites_and_case_preparation_without_ai(self):
        commands = verify_harness.commands("python-fixture")
        self.assertEqual(6, len(commands))
        self.assertIn("scripts/check_harness.py", commands[0])
        self.assertEqual({"scripts", "scripts/harness_eval", "scripts/shared_harness", "scripts/pr-review"},
                         {c[c.index("-s") + 1] for c in commands if "-s" in c})
        self.assertEqual("prepare", commands[-1][2])
        self.assertNotIn("run", [arg for c in commands for arg in c])

    def test_registered_suites_cover_existing_unittest_modules_once(self):
        import ast
        import fnmatch

        suites = [c for c in verify_harness.commands() if "-s" in c]
        scripts = verify_harness.BACKEND / "scripts"
        for path in scripts.rglob("*.py"):
            tree = ast.parse(path.read_text())
            if not any(isinstance(node, ast.ClassDef) and any(
                    isinstance(base, ast.Attribute) and base.attr == "TestCase"
                    or isinstance(base, ast.Name) and base.id == "TestCase"
                    for base in node.bases) for node in ast.walk(tree)):
                continue
            matches = [c for c in suites
                       if path.parent == verify_harness.BACKEND / c[c.index("-s") + 1]
                       and fnmatch.fnmatch(path.name, c[c.index("-p") + 1])]
            self.assertEqual(1, len(matches), f"검사 누락 또는 중복: {path.relative_to(scripts)}")

    def test_failure_runs_remaining_checks_and_returns_nonzero(self):
        for failure in (1, 2):
            with self.subTest(failure=failure), mock.patch.object(verify_harness.subprocess, "run") as run:
                run.side_effect = [subprocess.CompletedProcess([], code) for code in (0, failure, 0, 0, 0, 0)]
                with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                    self.assertEqual(1, verify_harness.main())
                self.assertEqual(6, run.call_count)

    def test_success_requires_every_command(self):
        with mock.patch.object(verify_harness.subprocess, "run", return_value=subprocess.CompletedProcess([], 0)) as run:
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(0, verify_harness.main())
            self.assertEqual(6, run.call_count)


if __name__ == "__main__":
    unittest.main()
