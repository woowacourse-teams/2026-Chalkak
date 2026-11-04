"""고정 Java 검사의 실제 형식 회귀와 OS 격리 경계를 모델 없이 확인한다."""
import json
from pathlib import Path
import sys
import tempfile
import time
import unittest
from unittest import mock

import claude_adapter
import fixture_tools
import java_fixture
import native_java
import run


class JavaEvaluationTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="java-eval-test-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.repo, self.output = self.root / "repo", self.root / "output"
        self.repo.mkdir()
        self.output.mkdir()
        run.prepare(run.HERE / "cases/combined-java-skills", self.output, self.repo)
        self.tools = fixture_tools.FixtureTools(self.repo, self.output / "audit.jsonl", enable_java=True)

    def test_java_writes_and_checker_are_opt_in_and_do_not_expand_document_mode(self):
        relative = "backend/src/test/java/Example.java"
        ordinary = fixture_tools.FixtureTools(self.repo, self.output / "ordinary.jsonl")
        self.assertTrue(ordinary.call("write", {"path": relative, "content": "class Example {}"})["isError"])
        self.assertTrue(ordinary.call("java_check", {})["isError"])
        self.assertNotIn("java_check", [tool["name"] for tool in ordinary.tools])
        self.assertNotIn("isError", self.tools.call("write", {"path": relative, "content": "class Example {}"}))
        for path in ["backend/scripts/Escape.java", ".eval/runtime/CheckJavaLayout.java", "backend/CLAUDE.md",
                     "backend/.agents/Escape.java", "../Escape.java", "backend/src/main/java/../../Escape.java"]:
            with self.subTest(path=path):
                self.assertTrue(self.tools.call("write", {"path": path, "content": "escape"})["isError"])
        (self.repo / "backend/src/main/java/link").symlink_to(self.output, target_is_directory=True)
        self.assertTrue(self.tools.call("write", {"path": "backend/src/main/java/link/Escape.java", "content": "x"})["isError"])

    def test_java_check_rejects_commands_missing_grant_and_stale_grant(self):
        self.assertTrue(self.tools.call("java_check", {"command": "touch /tmp/escape"})["isError"])
        self.assertTrue(self.tools.call("java_check", {})["isError"])
        (self.output / "java-preflight.json").write_text(json.dumps({"status": "PASS", "repo": str(self.repo),
                                                                   "fingerprint": "stale"}))
        self.assertTrue(self.tools.call("java_check", {})["isError"])

    def test_runtime_rejects_tampered_dependencies_and_symlink_output(self):
        runtime = self.repo / ".eval/runtime"
        jar = next((runtime / "jars").glob("*.jar"))
        original = jar.read_bytes()
        jar.write_bytes(b"tampered")
        with self.assertRaisesRegex(ValueError, "의존성"):
            java_fixture.commands(self.repo / "backend")
        jar.write_bytes(original)
        classes = self.repo / "backend/build/fixture-classes"
        classes.parent.mkdir(parents=True)
        classes.symlink_to(self.output, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "심볼릭"):
            java_fixture.commands(self.repo / "backend")
        self.assertTrue((self.output / "criteria.json").exists())

    def test_adapter_stops_before_model_session_if_java_isolation_is_unavailable(self):
        with mock.patch.object(claude_adapter, "inspect_material", return_value=["example"]), \
             mock.patch.object(claude_adapter, "probe_helper"), \
             mock.patch.object(claude_adapter.subprocess, "run", return_value=mock.Mock(
                 returncode=0, stdout=" ".join(claude_adapter.REQUIRED_FLAGS) + " dontAsk")), \
             mock.patch.object(native_java, "preflight", side_effect=ValueError("Java sandbox unavailable")), \
             self.assertRaisesRegex(ValueError, "sandbox unavailable"):
            claude_adapter.build_command("claude", self.repo, self.output, self.root / "user", enable_java=True)

    def test_unavailable_os_and_non_java_executable_fail_closed(self):
        with mock.patch.object(native_java.sys, "platform", "unsupported"), self.assertRaises(ValueError):
            native_java.execute(self.repo, ["/bin/sh"], time.monotonic() + 5, bytearray())
        with self.assertRaises(ValueError):
            native_java.execute(self.repo, ["/bin/sh"], time.monotonic() + 5, bytearray())

    def test_actual_sandbox_and_shared_checks_catch_layout_and_junit_failures(self):
        # 의존성/격리가 없는 호스트는 skip 또는 가짜 성공 대신 명시적으로 실패한다.
        with mock.patch.dict(native_java.os.environ, {"JAVA_TOOL_OPTIONS": "-javaagent:/missing-host-agent.jar"}):
            native_java.preflight(self.repo, self.output)
        self.assertIn("JAVA_SANDBOX_BLOCKED=7", (self.output / "java-preflight.log").read_text())
        self.assertFalse((self.output / "outside-write.txt").exists())
        result = native_java.check(self.repo, self.output)
        self.assertEqual(0, result["exit_code"], result["output"])
        self.assertIn("JavaLayout: 6 files passed", result["output"])
        self.assertIn("1 tests successful", result["output"])
        test = self.repo / "backend/src/test/java/com/chalkak/backend/feedback/domain/FeedbackQueryTest.java"
        original = test.read_text()
        test.write_text(original.replace("LocalDate.of(\n        2026,\n        10,\n        7\n    )", "LocalDate.of(2026, 10, 7)"))
        failed = self.tools.call("java_check", {})
        self.assertTrue(failed["isError"])
        self.assertIn("JavaLayout", failed["content"][0]["text"])
        test.write_text(original.replace("isSameAs(queryDate)", "isNull()"))
        failed = native_java.check(self.repo, self.output)
        self.assertNotEqual(0, failed["exit_code"])
        self.assertIn("1 tests failed", failed["output"])
        test.write_text(original)
        # 이전 class 잔류물이 새 실행 결과에 섞이지 않는다.
        stale = self.repo / "backend/build/fixture-classes/Stale.class"
        stale.write_bytes(b"stale")
        passed = native_java.check(self.repo, self.output)
        self.assertEqual(0, passed["exit_code"], passed["output"])
        self.assertFalse(stale.exists())

    def test_actual_java_deadline_and_output_limit_terminate_execution(self):
        native_java.preflight(self.repo, self.output)
        command = java_fixture.commands(self.repo / "backend")[1]
        with self.assertRaisesRegex(ValueError, "시간 상한"):
            native_java.execute(self.repo, command, time.monotonic() - 1, bytearray())
        with mock.patch.object(native_java, "LIMIT", 10), self.assertRaisesRegex(ValueError, "출력 상한"):
            native_java.execute(self.repo, [command[0], "-version"], time.monotonic() + 5, bytearray())


if __name__ == "__main__":
    unittest.main()
