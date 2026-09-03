"""평가 실행기 자체 검사. 실제 AI·GitHub·외부 네트워크를 호출하지 않는다."""

from contextlib import ExitStack, redirect_stderr, redirect_stdout
import importlib.util
import io
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest import mock


HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("harness_eval_runner", HERE / "run.py")
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


class HarnessEvaluationTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="harness-eval-test-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source = self.root / "source"
        self.sequence = 0
        source_files = {
            "backend/AGENTS.md": "# Backend instructions\n",
            "backend/CLAUDE.md": "# Backend instructions\n",
            "backend/.agents/skills/example/SKILL.md": "---\nname: example\ndescription: Example\n---\nBody\n",
            "backend/.claude/skills/example/SKILL.md": "---\nname: example\ndescription: Example\n---\nBody\n",
            "backend/.claude/rules/main-code.md": "---\npaths: [src/main/java/**/*.java]\n---\nRule\n",
            "backend/.claude/settings.json": "{}\n",
            ".github/ISSUE_TEMPLATE/docs.md": "# Document issue\n",
            ".github/pull_request_template.md": "# Pull request\n",
        }
        for relative, text in source_files.items():
            path = self.source / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
        runner.git(self.source, "init", "-b", "be/develop")
        runner.git(self.source, "add", ".")
        runner.git(self.source, "commit", "-qm", "source fixture")

    def new_destination(self):
        self.sequence += 1
        return self.root / f"result-{self.sequence}"

    def prepare_case(self, name):
        destination = self.new_destination()
        destination.mkdir()
        repo = self.root / f"actor-{self.sequence}"
        repo.mkdir()
        criteria, before = runner.prepare(HERE / "cases" / name, destination, repo, self.source)
        return repo, destination, criteria, before

    @staticmethod
    def events(destination, records=None):
        records = records if records is not None else [
            {"type": "item.completed", "item": {"type": "agent_message", "text": "테스트용 응답"}},
            {"type": "turn.completed", "usage": {"input_tokens": 1, "output_tokens": 1}},
        ]
        (destination / "events.jsonl").write_text("\n".join(json.dumps(item) for item in records) + "\n")

    def native_result(self, name="issue-draft", mutation=None, preflight_error=None, action="run",
                      platform="codex", records=None):
        """Git·파일 처리는 실제 실행하고 네이티브 AI 접점만 대체한다."""
        destination = self.new_destination()
        original_prepare = runner.prepare
        original_run = subprocess.run
        original_build = runner.claude_adapter.build_command
        binary = "/fixture/" + platform

        def prepare(case, output, repo):
            return original_prepare(case, output, repo, source=self.source)

        def version_or_local_command(command, *args, **kwargs):
            if command[:2] == [binary, "--version"]:
                return subprocess.CompletedProcess(command, 0, "fixture-cli\n", "")
            if command[:2] == [binary, "--help"]:
                return subprocess.CompletedProcess(command, 0,
                    "\n".join(runner.claude_adapter.REQUIRED_FLAGS) + "\ndontAsk", "")
            return original_run(command, *args, **kwargs)

        def execute(command, cwd, output, prompt, timeout):
            if mutation:
                mutation(cwd.parent)
            self.events(output, records)
            (output / "stderr.log").write_text("")
            return 0, 0.01

        with ExitStack() as stack:
            stack.enter_context(mock.patch.object(runner, "prepare", side_effect=prepare))
            stack.enter_context(mock.patch.object(runner.shutil, "which", return_value=binary))
            stack.enter_context(mock.patch.object(runner, "model_settings", return_value={}))
            stack.enter_context(mock.patch.object(runner, "preflight", return_value=preflight_error))
            stack.enter_context(mock.patch.object(runner.subprocess, "run", side_effect=version_or_local_command))
            if platform == "claude":
                stack.enter_context(mock.patch.object(runner.claude_adapter, "build_command",
                    side_effect=lambda executable, repo, output: original_build(
                        executable, repo, output, config_home=self.root / "claude-config")))
            execution = stack.enter_context(mock.patch.object(runner, "execute", side_effect=execute))
            report = runner.run_case(HERE / "cases" / name, platform, action, 1, destination)
        return destination, report, execution

    @staticmethod
    def reviewed(destination):
        review = json.loads((destination / "review.json").read_text())
        for item in review.values():
            item.update(status="PASS", evidence="단위 테스트용 events.jsonl·answer.md·changes.diff 근거")
        runner.write_json(destination / "review.json", review)
        return review

    def test_prepare_separates_grading_material_and_preserves_source(self):
        source_before = runner.snapshot(self.source)
        repo, destination, criteria, before = self.prepare_case("issue-draft")
        self.assertEqual(source_before, runner.snapshot(self.source))
        for relative, text in source_before["files"].items():
            self.assertEqual(text, (repo / relative).read_text(), relative)
        self.assertEqual(criteria, json.loads((destination / "criteria.json").read_text()))
        self.assertTrue((destination / "prompt.md").is_file())
        self.assertFalse(list(repo.rglob("criteria.json")))
        self.assertFalse(list(repo.rglob("prompt.md")))
        self.assertNotIn('"forbidden_mutations"', "\n".join(before["files"].values()))
        self.assertEqual("be/develop", runner.git(repo, "branch", "--show-current").strip())
        self.assertEqual("https://example.invalid/team/fixture.git", runner.git(repo, "remote", "get-url", "origin").strip())

    def test_resume_compares_against_dirty_start_and_detects_lost_user_change(self):
        repo, _, criteria, before = self.prepare_case("resume-work")
        path = repo / "backend/README.md"
        self.assertEqual("backend/README.md", runner.git(repo, "diff", "--name-only").strip())
        self.assertNotIn("8080", runner.git(repo, "show", "HEAD:backend/README.md"))
        self.assertIn("8080", before["files"]["backend/README.md"])
        errors, _ = runner.compare(before, runner.snapshot(repo), criteria)
        self.assertTrue(any("요청한 변경 없음" in error for error in errors))
        path.write_text(path.read_text().replace("## 로컬 실행", "## 로컬 실행\n\n- JDK 25가 필요합니다."))
        errors, diff = runner.compare(before, runner.snapshot(repo), criteria)
        self.assertEqual([], errors)
        self.assertIn("+- JDK 25가 필요합니다.", diff)
        self.assertNotIn("+> 주의:", diff)
        path.write_text("\n".join(line for line in path.read_text().splitlines() if "8080" not in line) + "\n")
        errors, _ = runner.compare(before, runner.snapshot(repo), criteria)
        self.assertTrue(any("preserved_text" in error and "8080" in error for error in errors))

    def test_extra_file_and_git_ref_changes_fail_even_after_positive_review(self):
        def mutation(repo):
            (repo / "backend/EXTRA.md").write_text("요청하지 않은 문서\n")
            runner.git(repo, "branch", "unexpected-branch")

        destination, report, execution = self.native_result(mutation=mutation)
        execution.assert_called_once()
        self.assertEqual("FAIL", report["status"])
        self.assertTrue(any("범위 밖 변경" in error for error in report["mechanical_errors"]))
        self.assertTrue(any("Git 상태 변경: refs" in error for error in report["mechanical_errors"]))
        self.reviewed(destination)
        self.assertEqual("FAIL", runner.grade(destination)["status"])

    def test_native_exit_zero_requires_separate_review_before_pass(self):
        def mutation(repo):
            path = repo / "backend/README.md"
            path.write_text(path.read_text().replace("## 로컬 실행", "## 로컬 실행\n\n- JDK 25가 필요합니다."))

        destination, report, _ = self.native_result("resume-work", mutation)
        self.assertEqual(0, report["exit_code"])
        self.assertEqual([], report["mechanical_errors"])
        self.assertEqual("REVIEW_REQUIRED", report["status"])
        with self.assertRaises(ValueError):
            runner.grade(destination)
        self.reviewed(destination)
        self.assertEqual("PASS", runner.grade(destination)["status"])

    def test_codex_host_error_with_completed_turn_and_missing_edit_is_inconclusive(self):
        records = [
            {"type": "item.completed", "item": {"type": "error", "message": "Code-mode host is unavailable"}},
            {"type": "item.completed", "item": {"type": "agent_message", "text": "도구를 실행할 수 없습니다."}},
            {"type": "turn.completed", "usage": {"input_tokens": 1, "output_tokens": 1}},
        ]
        destination, report, execution = self.native_result("resume-work", records=records)
        execution.assert_called_once()
        self.assertEqual(0, report["exit_code"])
        self.assertTrue(any("요청한 변경 없음" in error for error in report["mechanical_errors"]))
        self.assertTrue(any("Code-mode host" in error for error in report["event_errors"]))
        self.assertEqual("INCONCLUSIVE", report["status"])
        self.reviewed(destination)
        self.assertEqual("INCONCLUSIVE", runner.grade(destination)["status"])

    def test_claude_adapter_collects_real_fixture_changes_and_grades_summary(self):
        source_before = runner.snapshot(self.source)

        def mutation(repo):
            path = repo / "backend/README.md"
            path.write_text(path.read_text().replace("## 로컬 실행", "## 로컬 실행\n\n- JDK 25가 필요합니다."))

        records = [
            {"type": "system", "subtype": "init", "model": "fixture-claude"},
            {"type": "assistant", "message": {"content": [{"type": "text", "text": "README에 JDK 25 준비 조건을 추가했습니다."}]}},
            {"type": "result", "subtype": "success", "is_error": False,
             "usage": {"input_tokens": 1, "output_tokens": 1}, "total_cost_usd": 0},
        ]
        destination, report, execution = self.native_result(
            "resume-work", mutation, platform="claude", records=records)
        execution.assert_called_once()
        command, cwd = execution.call_args.args[:2]
        self.assertEqual("/fixture/claude", command[0])
        self.assertEqual("Skill", command[command.index("--tools") + 1])
        self.assertFalse(cwd.exists(), "평가용 임시 작업 폴더가 정리되어야 한다")
        self.assertEqual(source_before, runner.snapshot(self.source))
        before = json.loads((destination / "before.json").read_text())
        after = json.loads((destination / "after.json").read_text())
        self.assertEqual(before["refs"], after["refs"])
        self.assertIn("8080", after["files"]["backend/README.md"])
        self.assertIn("+- JDK 25가 필요합니다.", (destination / "changes.diff").read_text())
        self.assertIn("JDK 25 준비 조건", (destination / "answer.md").read_text())
        self.assertEqual(["fixture-claude"], report["usage"][0]["models"])
        self.assertEqual("REVIEW_REQUIRED", report["status"])
        self.reviewed(destination)
        self.assertEqual("PASS", runner.grade(destination)["status"])
        self.assertIn("| claude | resume-work | PASS |", (destination.parent / "summary.md").read_text())

    def test_grade_rejects_missing_evidence_and_missing_review_item(self):
        destination, _, _ = self.native_result()
        complete = self.reviewed(destination)
        first = next(iter(complete))
        for kind in ("blank-evidence", "missing-item"):
            with self.subTest(kind=kind):
                review = json.loads(json.dumps(complete))
                if kind == "blank-evidence":
                    review[first]["evidence"] = " \n"
                else:
                    del review[first]
                runner.write_json(destination / "review.json", review)
                with self.assertRaises(ValueError):
                    runner.grade(destination)
                self.assertEqual("REVIEW_REQUIRED", json.loads((destination / "report.json").read_text())["status"])

    def test_unrun_or_failed_preflight_cannot_execute_or_be_graded_pass(self):
        for action, reason, status in (("prepare", None, "NOT_RUN"), ("run", "격리 검사 실패", "BLOCKED")):
            with self.subTest(action=action):
                destination, report, execution = self.native_result(action=action, preflight_error=reason)
                execution.assert_not_called()
                self.assertEqual(status, report["status"])
                self.assertEqual(0, report["model_sessions"])
                self.reviewed(destination)
                with self.assertRaises(ValueError):
                    runner.grade(destination)

    def test_invalid_or_incomplete_events_cannot_be_graded_pass(self):
        for contents in ("{broken\n", "[]\n", "null\n", '{"type":"turn.completed"}\n'):
            with self.subTest(contents=contents):
                destination, _, _ = self.native_result()
                self.reviewed(destination)
                (destination / "events.jsonl").write_text(contents)
                self.assertEqual("INCONCLUSIVE", runner.grade(destination)["status"])

    def test_missing_diff_evidence_cannot_be_graded_pass(self):
        for name in ("before.json", "after.json", "changes.diff"):
            with self.subTest(name=name):
                destination, _, _ = self.native_result()
                self.reviewed(destination)
                (destination / name).unlink()
                self.assertEqual("INCONCLUSIVE", runner.grade(destination)["status"])

    def test_invalid_output_preserves_execution_and_collection_evidence(self):
        def mutation(repo):
            (repo / "backend/invalid.bin").write_bytes(b"\xff")

        destination, report, _ = self.native_result(mutation=mutation)
        self.assertEqual("FAIL", report["status"])
        self.assertEqual(1, report["model_sessions"])
        self.assertEqual(0, report["exit_code"])
        self.assertTrue((destination / "events.jsonl").is_file())
        self.assertTrue((destination / "collection-error.txt").is_file())
        self.reviewed(destination)
        self.assertEqual("FAIL", runner.grade(destination)["status"])

    def test_keyboard_interrupt_kills_process_group_and_is_reraised(self):
        destination = self.new_destination()
        destination.mkdir()
        process = mock.Mock(pid=12345)
        process.communicate.side_effect = KeyboardInterrupt
        with mock.patch.object(runner.subprocess, "Popen", return_value=process), \
                mock.patch.object(runner.os, "killpg") as killpg:
            with self.assertRaises(KeyboardInterrupt):
                runner.execute(["/fixture/codex"], self.root, destination)
        killpg.assert_called_once_with(process.pid, signal.SIGKILL)
        process.wait.assert_called_once()

    def test_keyboard_interrupt_stops_batch_and_preserves_interrupted_report(self):
        original_prepare, original_run = runner.prepare, subprocess.run

        def version_or_local_command(command, *args, **kwargs):
            if command[:2] == ["/fixture/codex", "--version"]:
                return subprocess.CompletedProcess(command, 0, "fixture-cli\n", "")
            return original_run(command, *args, **kwargs)

        with ExitStack() as stack:
            stack.enter_context(mock.patch.object(runner, "BACKEND", self.source / "backend"))
            stack.enter_context(mock.patch.object(runner.Path, "home", return_value=self.root))
            stack.enter_context(mock.patch.object(runner, "prepare", side_effect=lambda case, output, repo:
                original_prepare(case, output, repo, source=self.source)))
            stack.enter_context(mock.patch.object(runner.shutil, "which", return_value="/fixture/codex"))
            stack.enter_context(mock.patch.object(runner, "model_settings", return_value={}))
            stack.enter_context(mock.patch.object(runner, "preflight", return_value=None))
            stack.enter_context(mock.patch.object(runner.subprocess, "run", side_effect=version_or_local_command))
            execution = stack.enter_context(mock.patch.object(runner, "execute", side_effect=KeyboardInterrupt))
            stack.enter_context(redirect_stdout(io.StringIO()))
            stack.enter_context(redirect_stderr(io.StringIO()))
            code = runner.main(["run", "--platform", "codex", "--case", "resume-work", "--case", "issue-draft"])
        self.assertEqual(130, code)
        execution.assert_called_once()
        reports = list((self.source / "backend/build/harness-eval").glob("*/*/report.json"))
        self.assertEqual(1, len(reports))
        report = json.loads(reports[0].read_text())
        self.assertEqual("resume-work", report["case"])
        self.assertEqual("INCONCLUSIVE", report["status"])
        self.assertEqual(1, report["model_sessions"])
        self.assertIn("후속 사례도 실행하지 않음", report["reason"])
        self.assertFalse(execution.call_args.args[1].exists(), "중단된 actor 폴더도 정리되어야 한다")
        self.assertIn("| codex | resume-work | INCONCLUSIVE |", (reports[0].parent.parent / "summary.md").read_text())

    @unittest.skipUnless(os.name == "posix", "프로세스 그룹 종료는 POSIX에서 확인한다")
    def test_timeout_stops_spawned_child_process_too(self):
        destination = self.new_destination()
        destination.mkdir()
        heartbeat = destination / "heartbeat"
        pidfile = destination / "child.pid"
        child_code = (
            "import pathlib, sys, time\n"
            "path = pathlib.Path(sys.argv[1])\n"
            "for counter in range(600):\n"
            "    path.write_text(str(counter))\n"
            "    time.sleep(0.05)\n"
        )
        parent_code = (
            "import pathlib, subprocess, sys, time\n"
            "child = subprocess.Popen([sys.executable, '-c', sys.argv[1], sys.argv[2]])\n"
            "pathlib.Path(sys.argv[3]).write_text(str(child.pid))\n"
            "time.sleep(30)\n"
        )
        try:
            code, elapsed = runner.execute(
                [sys.executable, "-c", parent_code, child_code, str(heartbeat), str(pidfile)],
                self.root, destination, timeout=0.8,
            )
            self.assertIsNone(code)
            self.assertLess(elapsed, 5)
            self.assertTrue(heartbeat.exists(), "자식 프로세스가 실행된 뒤 종료되어야 한다")
            stopped = heartbeat.read_text()
            time.sleep(0.2)
            self.assertEqual(stopped, heartbeat.read_text(), "timeout 이후 자식 프로세스가 계속 실행됨")
        finally:
            if pidfile.exists():
                try:
                    os.kill(int(pidfile.read_text()), signal.SIGKILL)
                except ProcessLookupError:
                    pass


if __name__ == "__main__":
    unittest.main()
