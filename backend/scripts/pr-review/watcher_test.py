#!/usr/bin/env python3
"""Tests for the local PR watcher without GitHub or AI calls."""

import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock


SCRIPT = Path(__file__).with_name("watcher.py")
SPEC = importlib.util.spec_from_file_location("pr_review_watcher", SCRIPT)
watcher = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(watcher)


class FakeGitHub:
    def __init__(self, *, reviewed=False, post_error=None):
        self.reviewed = reviewed
        self.post_error = post_error
        self.posts = []
        self.details_calls = 0

    def already_reviewed(self, _number, _sha, _viewer):
        return self.reviewed

    def details(self, number):
        self.details_calls += 1
        return {
            "number": number,
            "title": "fixture",
            "body": "- #1",
            "headRefOid": "a" * 40,
            "changedFiles": 2,
            "additions": 20,
            "deletions": 5,
            "files": [{"path": "backend/src/main/java/Fixture.java"}],
        }

    def diff(self, _number):
        return "diff --git a/a b/a\n+changed\n"

    def checks(self, _number):
        return [{"name": "test", "bucket": "pass"}]

    def post_review(self, number, body):
        if self.post_error:
            raise self.post_error
        self.posts.append((number, body))


class WatcherTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="pr-review-watcher-test-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.config = {
            "version": 1,
            "repo_dir": str(self.root),
            "repository": "team/repo",
            "gh_command": "/fixture/gh",
            "provider": "codex",
            "provider_command": "/fixture/codex",
            "path": "/usr/bin:/bin",
            "home": str(self.root),
            "base_branch": "be/develop",
            "label": "Server",
            "interval_seconds": 300,
            "review_timeout_seconds": 10,
            "state_file": str(self.root / "state.json"),
            "lock_file": str(self.root / "lock"),
            "max_changed_files": 30,
            "max_changed_lines": 1200,
            "max_diff_characters": 200000,
            "max_reviews_per_poll": 1,
        }
        self.pull_request = {
            "number": 7,
            "author": {"login": "teammate"},
            "isDraft": False,
            "baseRefName": "be/develop",
            "headRefOid": "a" * 40,
            "labels": [{"name": "Server"}],
        }

    def state(self):
        return watcher.load_state(self.config)

    def test_only_other_backend_server_pr_is_eligible(self):
        self.assertTrue(watcher.eligible(self.pull_request, "reviewer", self.config))
        for field, value in (
            ("isDraft", True),
            ("baseRefName", "develop"),
            ("author", {"login": "reviewer"}),
            ("labels", [{"name": "Client"}]),
        ):
            with self.subTest(field=field):
                candidate = dict(self.pull_request, **{field: value})
                self.assertFalse(watcher.eligible(candidate, "reviewer", self.config))

    def test_related_issue_numbers_use_template_order_without_duplicates(self):
        details = {
            "body": "## 연관된 이슈\n- #12\n- #7\n",
            "closingIssuesReferences": [{"number": 7}, {"number": 3}],
        }
        self.assertEqual([12, 7, 3], watcher.related_issue_numbers(details))

    def test_github_api_calls_do_not_pass_pr_command_repo_flag(self):
        github = watcher.GitHub(self.config)
        reviews = json.dumps([
            {
                "body": watcher.marker("claude", "a" * 40),
                "user": {"login": "reviewer"},
            }
        ])
        with mock.patch.object(watcher, "run_command", return_value=reviews) as command:
            self.assertTrue(github.already_reviewed(7, "a" * 40, "reviewer"))
        arguments = command.call_args.args[0]
        self.assertEqual("api", arguments[1])
        self.assertNotIn("--repo", arguments)

        with mock.patch.object(watcher, "run_command", return_value="{}") as command:
            github.post_review(7, "검토 결과")
        arguments = command.call_args.args[0]
        self.assertEqual("POST", arguments[arguments.index("--method") + 1])
        self.assertNotIn("--repo", arguments)

    def test_github_marker_skips_ai_and_post(self):
        github = FakeGitHub(reviewed=True)
        path, state = self.state()
        with mock.patch.object(watcher, "generate_review") as generate:
            watcher.process_pull_request(self.config, github, "reviewer", self.pull_request, path, state)
        generate.assert_not_called()
        self.assertEqual([], github.posts)
        self.assertEqual("reviewed", watcher.load_json(path, {})["runs"]["7:" + "a" * 40]["status"])

    def test_review_is_generated_and_posted_once(self):
        github = FakeGitHub()
        path, state = self.state()
        with mock.patch.object(watcher, "generate_review", return_value="발견된 문제 없음") as generate:
            watcher.process_pull_request(self.config, github, "reviewer", self.pull_request, path, state)
        generate.assert_called_once()
        self.assertEqual(1, len(github.posts))
        self.assertIn(watcher.marker("codex", "a" * 40), github.posts[0][1])
        self.assertEqual("reviewed", watcher.load_json(path, {})["runs"]["7:" + "a" * 40]["status"])

    def test_post_failure_reuses_review_without_second_ai_call(self):
        first = FakeGitHub(post_error=RuntimeError("offline"))
        path, state = self.state()
        with mock.patch.object(watcher, "generate_review", return_value="검토 결과") as generate:
            with self.assertRaises(RuntimeError):
                watcher.process_pull_request(self.config, first, "reviewer", self.pull_request, path, state)
        self.assertEqual(1, generate.call_count)
        saved = watcher.load_json(path, {})
        self.assertEqual("post_failed", saved["runs"]["7:" + "a" * 40]["status"])

        second = FakeGitHub()
        _, state = self.state()
        with mock.patch.object(watcher, "generate_review") as generate_again:
            watcher.process_pull_request(self.config, second, "reviewer", self.pull_request, path, state)
        generate_again.assert_not_called()
        self.assertEqual(1, len(second.posts))

    def test_large_pr_is_deferred_without_ai_tokens(self):
        github = FakeGitHub()
        original = github.details

        def large(number):
            details = original(number)
            details["changedFiles"] = 31
            return details

        github.details = large
        path, state = self.state()
        with mock.patch.object(watcher, "generate_review") as generate:
            watcher.process_pull_request(self.config, github, "reviewer", self.pull_request, path, state)
        generate.assert_not_called()
        self.assertIn("자동 리뷰 보류", github.posts[0][1])

    def test_provider_commands_are_noninteractive_and_restricted(self):
        codex = dict(self.config)
        with mock.patch.object(watcher, "run_command", return_value="ok") as command:
            watcher.generate_review(codex, "prompt")
        arguments = command.call_args.args[0]
        self.assertIn("--ephemeral", arguments)
        self.assertEqual("read-only", arguments[arguments.index("--sandbox") + 1])

        claude = dict(self.config, provider="claude", provider_command="/fixture/claude")
        with mock.patch.object(watcher, "run_command", return_value="ok") as command:
            watcher.generate_review(claude, "prompt")
        arguments = command.call_args.args[0]
        self.assertIn("--no-session-persistence", arguments)
        self.assertEqual("", arguments[arguments.index("--tools") + 1])
        self.assertNotIn("Bash", arguments)

    def test_plist_starts_on_login_and_keeps_service_alive(self):
        config_path = self.root / "config.json"
        watcher.write_json(config_path, self.config)
        arguments = mock.Mock(
            config=str(config_path),
            python="/usr/bin/python3",
            output=str(self.root / "service.plist"),
            log=str(self.root / "out.log"),
            error_log=str(self.root / "err.log"),
        )
        watcher.write_plist(arguments)
        import plistlib
        with Path(arguments.output).open("rb") as source:
            data = plistlib.load(source)
        self.assertTrue(data["RunAtLoad"])
        self.assertEqual(300, data["StartInterval"])
        self.assertNotIn("KeepAlive", data)
        self.assertEqual(watcher.SERVICE_LABEL, data["Label"])
        self.assertEqual(self.config["path"], data["EnvironmentVariables"]["PATH"])

    def test_changed_head_is_not_posted(self):
        github = FakeGitHub()
        original = github.details

        def changed(number):
            details = original(number)
            if github.details_calls > 1:
                details["headRefOid"] = "b" * 40
            return details

        github.details = changed
        path, state = self.state()
        with mock.patch.object(watcher, "generate_review", return_value="검토 결과"):
            result = watcher.process_pull_request(
                self.config, github, "reviewer", self.pull_request, path, state
            )
        self.assertFalse(result)
        self.assertEqual([], github.posts)
        self.assertEqual({}, watcher.load_json(path, {})["runs"])

    def test_each_poll_attempts_at_most_one_new_review(self):
        github = mock.Mock()
        github.viewer.return_value = "reviewer"
        github.candidates.return_value = [
            self.pull_request,
            dict(self.pull_request, number=8, headRefOid="b" * 40),
        ]
        with mock.patch.object(watcher, "GitHub", return_value=github), \
                mock.patch.object(watcher, "process_pull_request", return_value=True) as process:
            watcher.run_once(self.config)
        self.assertEqual(1, process.call_count)
        last_check = watcher.load_json(Path(self.config["state_file"]), {})["last_check"]
        self.assertEqual("ok", last_check["status"])
        self.assertEqual(2, last_check["eligible"])
        self.assertEqual(1, last_check["processed"])

    def test_failed_poll_records_last_error_without_ai_call(self):
        github = mock.Mock()
        github.viewer.side_effect = RuntimeError("offline")
        with mock.patch.object(watcher, "GitHub", return_value=github):
            with self.assertRaises(RuntimeError):
                watcher.run_once(self.config)
        last_check = watcher.load_json(Path(self.config["state_file"]), {})["last_check"]
        self.assertEqual("failed", last_check["status"])
        self.assertIn("offline", last_check["error"])


if __name__ == "__main__":
    unittest.main()
