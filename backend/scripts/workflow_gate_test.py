"""네트워크·GitHub 변경 없이 실제 상태의 전이 경계를 검사한다."""
from contextlib import redirect_stdout
from copy import deepcopy
import io
import json
import subprocess
import unittest
from unittest.mock import patch

import workflow_gate as gate


class WorkflowGateTests(unittest.TestCase):
    def setUp(self):
        self.issue = {"number": 7, "html_url": "https://github.com/team/repo/issues/7", "state": "closed", "state_reason": "completed", "body": "검증 가능한 완료 조건"}
        self.pr = {"number": 8, "html_url": "https://github.com/team/repo/pull/8", "state": "closed", "merged": True, "merged_at": "2026-09-17T00:00:00Z", "base": {"ref": "be/develop", "repo": {"full_name": "team/repo"}}, "body": "## 🔗 연관된 이슈\n\n- #7\n\n## 📋 작업 내용\n완료"}

    def evaluate(self):
        return gate.evaluate("team/repo", 7, 8, "be/develop", self.issue, self.pr)

    def test_closed_and_open_issue_require_separate_completion_review(self):
        result, code = self.evaluate()
        self.assertEqual(0, code)
        self.assertTrue(result["completion_review_required"])
        self.issue.update(state="open", state_reason=None)
        self.assertEqual(2, self.evaluate()[1])
        self.issue.update(state="closed", state_reason="completed")
        self.assertEqual(0, self.evaluate()[1])

    def test_unmerged_and_closed_unmerged_are_blocked(self):
        for fields in ({"merged": False}, {"merged": "true"}, {"merged_at": None}, {"merged_at": "yes"}, {"merged_at": 123}, {"merged_at": "2026-01-01"}, {"state": "open"}):
            with self.subTest(fields=fields):
                old = deepcopy(self.pr)
                self.pr.update(fields)
                self.assertEqual(1, self.evaluate()[1])
                self.pr = old

    def test_wrong_repository_number_base_and_pr_as_issue_are_blocked(self):
        for field, value in (("number", 99), ("number", True), ("html_url", "https://github.com/other/repo/issues/7"), ("state", "unknown"), ("state_reason", "not_planned"), ("body", None), ("pull_request", {})):
            with self.subTest(field=field):
                old = deepcopy(self.issue)
                self.issue[field] = value
                self.assertEqual(1, self.evaluate()[1])
                self.issue = old
        for base in ({"ref": "feature", "repo": {"full_name": "team/repo"}}, {"ref": "be/develop", "repo": {"full_name": "other/repo"}}, None):
            self.pr["base"] = base
            self.assertEqual(1, self.evaluate()[1])

    def test_reference_in_wrong_section_code_or_comment_does_not_link_issue(self):
        for body in ("## 참고 자료\n- #7", "## 연관된 이슈\n- #70", "## 연관된 이슈\n<!--\n- #7\n-->", "## 연관된 이슈\n```\n- #7\n```", "## 연관된 이슈\n- #9\n## 작업 내용\n- #7", "## 연관된 이슈\n- #7\n## 연관된 이슈\n- #7"):
            with self.subTest(body=body):
                self.pr["body"] = body
                self.assertEqual(1, self.evaluate()[1])

    def test_cli_queries_only_reads_and_does_not_close(self):
        outputs = [subprocess.CompletedProcess([], 0, json.dumps(self.issue), ""), subprocess.CompletedProcess([], 0, json.dumps(self.pr), "")]
        with patch.object(gate.subprocess, "run", side_effect=outputs) as run, redirect_stdout(io.StringIO()):
            self.assertEqual(0, gate.main(["--repo", "team/repo", "--issue", "7", "--pr", "8", "--base", "be/develop"]))
        self.assertEqual(2, run.call_count)
        for call in run.call_args_list:
            self.assertEqual(["gh", "api", "--method", "GET"], call.args[0][:4])
            self.assertNotIn("shell", call.kwargs)

    def test_query_failure_or_malformed_response_cannot_pass(self):
        for error in (FileNotFoundError(), subprocess.TimeoutExpired("gh", 30), subprocess.CalledProcessError(1, "gh"), ValueError("json")):
            with self.subTest(error=error), patch.object(gate, "read_api", side_effect=error), redirect_stdout(io.StringIO()) as out:
                self.assertEqual(3, gate.main(["--repo", "team/repo", "--issue", "7", "--pr", "8", "--base", "be/develop"]))
                self.assertEqual("UNAVAILABLE", json.loads(out.getvalue())["status"])
