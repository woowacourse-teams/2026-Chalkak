#!/usr/bin/env python3
"""Tests for the local PR watcher without GitHub or AI calls."""

import importlib.util
import json
from pathlib import Path
import tempfile
import sys
import unittest
from unittest import mock


SCRIPT = Path(__file__).with_name("watcher.py")
sys.path.insert(0, str(SCRIPT.parent))
SPEC = importlib.util.spec_from_file_location("pr_review_watcher", SCRIPT)
watcher = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(watcher)


DIFF = """diff --git a/backend/src/main/java/Fixture.java b/backend/src/main/java/Fixture.java
--- a/backend/src/main/java/Fixture.java
+++ b/backend/src/main/java/Fixture.java
@@ -1,2 +1,2 @@
 context
-old
+new
"""
NO_FINDINGS = json.dumps({"findings": [], "limitations": []})


class FakeGitHub:
    def __init__(self, *, reviewed=False, post_error=None):
        self.reviewed = reviewed
        self.post_error = post_error
        self.posts = []
        self.details_calls = 0

    def already_reviewed(self, _number, _viewer):
        return self.reviewed

    def details(self, number):
        self.details_calls += 1
        return {
            "number": number,
            "title": "fixture",
            "state": "OPEN",
            "isDraft": False,
            "author": {"login": "teammate"},
            "baseRefName": "be/develop",
            "labels": [{"name": "Server"}],
            "body": "- #1",
            "headRefOid": "a" * 40,
            "changedFiles": 2,
            "additions": 20,
            "deletions": 5,
            "files": [{"path": "backend/src/main/java/Fixture.java"}],
        }

    def diff(self, _number):
        return DIFF

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
            "version": watcher.CONFIG_VERSION,
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
        reviews = json.dumps([[
            {
                "body": watcher.marker("claude", "a" * 40),
                "user": {"login": "reviewer"},
            }
        ]])
        with mock.patch.object(watcher, "run_command", return_value=reviews) as command:
            self.assertTrue(github.already_reviewed(7, "reviewer"))
        arguments = command.call_args.args[0]
        self.assertEqual("api", arguments[1])
        self.assertNotIn("--repo", arguments)

        with mock.patch.object(watcher, "run_command", return_value="{}") as command:
            github.post_review(7, {"event": "COMMENT", "body": "검토 결과", "commit_id": "a" * 40, "comments": []})
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
        self.assertEqual("reviewed", watcher.load_json(path, {})["runs"][watcher.run_key(self.config, "reviewer", 7)]["status"])

    def test_review_is_generated_and_posted_once(self):
        github = FakeGitHub()
        path, state = self.state()
        with mock.patch.object(watcher, "generate_review", return_value=NO_FINDINGS) as generate:
            watcher.process_pull_request(self.config, github, "reviewer", self.pull_request, path, state)
        generate.assert_called_once()
        self.assertEqual(1, len(github.posts))
        self.assertIn(watcher.marker("codex", "a" * 40), github.posts[0][1]["body"])
        self.assertEqual("reviewed", watcher.load_json(path, {})["runs"][watcher.run_key(self.config, "reviewer", 7)]["status"])

    def test_post_failure_reuses_review_without_second_ai_call(self):
        first = FakeGitHub(post_error=RuntimeError("offline"))
        path, state = self.state()
        with mock.patch.object(watcher, "generate_review", return_value=NO_FINDINGS) as generate:
            with self.assertRaises(RuntimeError):
                watcher.process_pull_request(self.config, first, "reviewer", self.pull_request, path, state)
        self.assertEqual(1, generate.call_count)
        saved = watcher.load_json(path, {})
        self.assertEqual("post_failed", saved["runs"][watcher.run_key(self.config, "reviewer", 7)]["status"])

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
        self.assertIn("자동 리뷰 보류", github.posts[0][1]["body"])

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
            if github.details_calls > 2:
                details["headRefOid"] = "b" * 40
            return details

        github.details = changed
        path, state = self.state()
        with mock.patch.object(watcher, "generate_review", return_value=NO_FINDINGS):
            result = watcher.process_pull_request(
                self.config, github, "reviewer", self.pull_request, path, state
            )
        self.assertTrue(result)
        self.assertEqual([], github.posts)
        self.assertEqual("stale", watcher.load_json(path, {})["runs"][watcher.run_key(self.config, "reviewer", 7)]["status"])

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

    def test_new_push_provider_switch_and_restart_do_not_spend_again(self):
        path, state = self.state()
        github = FakeGitHub()
        with mock.patch.object(watcher, "generate_review", return_value=NO_FINDINGS) as generate:
            watcher.process_pull_request(self.config, github, "reviewer", self.pull_request, path, state)
            path, state = self.state()
            watcher.process_pull_request(dict(self.config, provider="claude"), github, "reviewer",
                                         dict(self.pull_request, headRefOid="b" * 40), path, state)
        generate.assert_called_once()
        self.assertEqual(1, len(github.posts))

    def test_other_reviewer_gets_own_attempt(self):
        path, state = self.state()
        github = FakeGitHub()
        with mock.patch.object(watcher, "generate_review", return_value=NO_FINDINGS) as generate:
            for viewer in ("reviewer", "other-reviewer"):
                watcher.process_pull_request(self.config, github, viewer, self.pull_request, path, state)
        self.assertEqual(2, generate.call_count)

    def test_remote_old_sha_marker_from_other_provider_blocks_only_its_author(self):
        github = watcher.GitHub(self.config)
        pages = [[], [{"body": watcher.marker("claude", "b" * 40), "user": {"login": "REVIEWER"},
                       "state": "COMMENTED"}]]
        with mock.patch.object(watcher, "run_command", return_value=json.dumps(pages)) as command:
            self.assertTrue(github.already_reviewed(7, "reviewer"))
            self.assertFalse(github.already_reviewed(7, "another"))
        self.assertIn("--paginate", command.call_args.args[0])
        pages[1][0]["state"] = "PENDING"
        with mock.patch.object(watcher, "run_command", return_value=json.dumps(pages)):
            self.assertFalse(github.already_reviewed(7, "reviewer"))

    def test_generation_failure_and_invalid_output_are_not_retried(self):
        for outcome in (RuntimeError("AI offline"), "Markdown instead of JSON"):
            with self.subTest(outcome=outcome):
                path = Path(self.config["state_file"])
                state = {"version": watcher.STATE_VERSION, "runs": {}}
                github = FakeGitHub()
                options = {"side_effect": outcome} if isinstance(outcome, Exception) else {"return_value": outcome}
                with mock.patch.object(watcher, "generate_review", **options) as generate:
                    with self.assertRaises((RuntimeError, ValueError)):
                        watcher.process_pull_request(self.config, github, "reviewer", self.pull_request, path, state)
                    path, state = self.state()
                    watcher.process_pull_request(self.config, github, "reviewer",
                                                 dict(self.pull_request, headRefOid="b" * 40), path, state)
                generate.assert_called_once()
                self.assertEqual([], github.posts)

    def test_killed_generation_is_consumed_before_model_call(self):
        path, state = self.state()
        github = FakeGitHub()

        def interrupted(*_):
            saved = watcher.load_json(path, {})
            self.assertEqual("generating", saved["runs"][watcher.run_key(self.config, "reviewer", 7)]["status"])
            raise SystemExit(143)

        with mock.patch.object(watcher, "generate_review", side_effect=interrupted):
            with self.assertRaises(SystemExit):
                watcher.process_pull_request(self.config, github, "reviewer", self.pull_request, path, state)
        path, state = self.state()
        with mock.patch.object(watcher, "generate_review") as generate:
            watcher.process_pull_request(self.config, github, "reviewer", self.pull_request, path, state)
        generate.assert_not_called()

    def test_post_retries_stop_after_three_and_reconcile_uncertain_success(self):
        path, state = self.state()
        github = FakeGitHub(post_error=RuntimeError("timeout"))
        with mock.patch.object(watcher, "generate_review", return_value=NO_FINDINGS) as generate:
            for _ in range(3):
                with self.assertRaises(RuntimeError):
                    watcher.process_pull_request(self.config, github, "reviewer", self.pull_request, path, state)
            self.assertFalse(watcher.process_pull_request(self.config, github, "reviewer", self.pull_request, path, state))
        generate.assert_called_once()
        record = state["runs"][watcher.run_key(self.config, "reviewer", 7)]
        self.assertEqual(3, record["post_attempts"])
        github.reviewed = True
        watcher.process_pull_request(self.config, github, "reviewer", self.pull_request, path, state)
        self.assertEqual("reviewed", state["runs"][watcher.run_key(self.config, "reviewer", 7)]["status"])

    def test_cached_payload_is_not_retargeted_to_new_push(self):
        path, state = self.state()
        github = FakeGitHub(post_error=RuntimeError("offline"))
        with mock.patch.object(watcher, "generate_review", return_value=NO_FINDINGS):
            with self.assertRaises(RuntimeError):
                watcher.process_pull_request(self.config, github, "reviewer", self.pull_request, path, state)
        changed = FakeGitHub()
        original = changed.details
        changed.details = lambda number: dict(original(number), headRefOid="b" * 40)
        with mock.patch.object(watcher, "generate_review") as generate:
            watcher.process_pull_request(self.config, changed, "reviewer",
                                         dict(self.pull_request, headRefOid="b" * 40), path, state)
        generate.assert_not_called()
        self.assertEqual([], changed.posts)
        self.assertEqual("stale", state["runs"][watcher.run_key(self.config, "reviewer", 7)]["status"])

    def test_head_changed_before_ai_does_not_consume_attempt(self):
        path, state = self.state()
        github = FakeGitHub()
        original = github.details
        def changing(number):
            details = original(number)
            if github.details_calls == 2:
                details["headRefOid"] = "b" * 40
            return details
        github.details = changing
        with mock.patch.object(watcher, "generate_review") as generate:
            watcher.process_pull_request(self.config, github, "reviewer", self.pull_request, path, state)
        generate.assert_not_called()
        self.assertEqual({}, state["runs"])

    def test_closed_draft_and_retargeted_prs_do_not_receive_generated_review(self):
        for changes in ({"state": "CLOSED"}, {"isDraft": True}, {"labels": []}, {"baseRefName": "main"}):
            with self.subTest(changes=changes):
                path = Path(self.config["state_file"])
                state = {"version": watcher.STATE_VERSION, "runs": {}}
                github = FakeGitHub()
                original = github.details
                def changing(number):
                    details = original(number)
                    return dict(details, **changes) if github.details_calls > 2 else details
                github.details = changing
                with mock.patch.object(watcher, "generate_review", return_value=NO_FINDINGS):
                    watcher.process_pull_request(self.config, github, "reviewer", self.pull_request, path, state)
                self.assertEqual([], github.posts)
                self.assertEqual("stale", state["runs"][watcher.run_key(self.config, "reviewer", 7)]["status"])

    def test_exact_size_limits_are_allowed_and_excess_skips_ai(self):
        for files, lines, diff_limit, expected in ((30, 1200, len(DIFF), 1),
                                                  (31, 1200, len(DIFF), 0),
                                                  (30, 1201, len(DIFF), 0),
                                                  (30, 1200, len(DIFF) - 1, 0)):
            with self.subTest(files=files, lines=lines, diff_limit=diff_limit):
                path = Path(self.config["state_file"])
                state = {"version": watcher.STATE_VERSION, "runs": {}}
                github = FakeGitHub()
                original = github.details
                github.details = lambda number: dict(original(number), changedFiles=files, additions=lines, deletions=0)
                config = dict(self.config, max_diff_characters=diff_limit)
                with mock.patch.object(watcher, "generate_review", return_value=NO_FINDINGS) as generate:
                    watcher.process_pull_request(config, github, "reviewer", self.pull_request, path, state)
                self.assertEqual(expected, generate.call_count)

    def test_legacy_failure_and_body_cache_do_not_trigger_ai_or_bulk_post(self):
        for status in ("failed", "generated", "post_failed"):
            with self.subTest(status=status):
                path = Path(self.config["state_file"])
                watcher.write_json(path, {"version": 1, "runs": {"7:" + "a" * 40:
                                   {"status": status, "review_body": "old body"}}})
                path, state = self.state()
                github = FakeGitHub()
                with mock.patch.object(watcher, "generate_review") as generate:
                    watcher.process_pull_request(self.config, github, "reviewer", self.pull_request, path, state)
                generate.assert_not_called()
                self.assertEqual([], github.posts)
                self.assertEqual("legacy_stopped", state["runs"][watcher.run_key(self.config, "reviewer", 7)]["status"])

    def test_old_attempts_are_not_pruned_and_old_config_cannot_activate_new_code(self):
        path, state = self.state()
        state["runs"] = {str(index): {"status": "failed"} for index in range(501)}
        watcher.save_state(path, state)
        self.assertEqual(501, len(watcher.load_json(path, {})["runs"]))
        with self.assertRaisesRegex(RuntimeError, "install"):
            watcher.validate_config(dict(self.config, version=1))

    def test_api_reads_can_retry_without_treating_them_as_ai_attempts(self):
        path, state = self.state()
        github = FakeGitHub()
        with mock.patch.object(github, "details", side_effect=RuntimeError("offline")):
            with self.assertRaises(RuntimeError):
                watcher.process_pull_request(self.config, github, "reviewer", self.pull_request, path, state)
        self.assertEqual({}, state["runs"])

    def test_generated_inline_result_is_posted_with_reviewed_commit(self):
        path, state = self.state()
        github = FakeGitHub()
        output = json.dumps({"findings": [{"severity": "수정 필수", "title": "검증 누락",
                            "condition": "빈 입력", "impact": "실패", "suggestion": "검증 추가",
                            "location": {"path": "backend/src/main/java/Fixture.java", "line": 2, "side": "RIGHT"},
                            "unanchored_reason": None}], "limitations": []})
        with mock.patch.object(watcher, "generate_review", return_value=output):
            watcher.process_pull_request(self.config, github, "reviewer", self.pull_request, path, state)
        payload = github.posts[0][1]
        self.assertEqual("a" * 40, payload["commit_id"])
        self.assertEqual(2, payload["comments"][0]["line"])
        self.assertNotIn("검증 누락", payload["body"])

    def test_review_classification_uses_labels_not_title_branch_or_size(self):
        for labels, expected in (([], "general"), (["feat"], "general"),
                                 (["refactor"], "refactor"),
                                 (["refactor", "docs", "test", "style"], "refactor"),
                                 (["refactor", "feat"], "mixed"),
                                 (["refactor", "fix"], "mixed"),
                                 (["refactor", "chore"], "mixed")):
            with self.subTest(labels=labels):
                details = {"title": "refactor: restructure", "headRefName": "be/refactor/large",
                           "changedFiles": 35, "body": "리팩터링입니다.",
                           "labels": [{"name": name} for name in ["Server", *labels]]}
                self.assertEqual(expected, watcher.review_kind(details))
                limits = (40, 1800) if expected == "refactor" else (30, 1200)
                self.assertEqual(limits, watcher.review_limits(self.config, details))

    def test_review_prompt_has_distinct_focus_and_mixed_checks_both(self):
        for labels, expected in (([], "일반 변경"), (["refactor"], "리팩터링"),
                                 (["refactor", "feat"], "일반 변경(리팩터링 혼합)")):
            details = {"labels": [{"name": name} for name in labels]}
            focus = watcher.review_focus(details)
            self.assertIn("검토 유형: " + expected, focus)
            if not labels or "feat" in labels:
                self.assertIn("요구사항 충족", focus)
            if "refactor" in labels:
                self.assertIn("기존 동작·API 계약 유지", focus)
            self.assertIn(focus, watcher.build_prompt(details, [], DIFF))

    def test_refactor_size_limits_and_mixed_fallback_apply_before_ai(self):
        fixtures = [(["refactor"], 40, 1800, len(DIFF), True),
                    (["refactor"], 41, 1800, len(DIFF), False),
                    (["refactor"], 40, 1801, len(DIFF), False),
                    (["refactor"], 40, 1800, len(DIFF) - 1, False),
                    (["refactor", "test"], 35, 1500, len(DIFF), True),
                    (["refactor", "feat"], 35, 1500, len(DIFF), False),
                    (["refactor", "fix"], 35, 1500, len(DIFF), False),
                    (["refactor", "chore"], 35, 1500, len(DIFF), False),
                    ([], 35, 1500, len(DIFF), False)]
        for labels, files, lines, char_limit, allowed in fixtures:
            with self.subTest(labels=labels, files=files, lines=lines, char_limit=char_limit):
                path = Path(self.config["state_file"])
                state = {"version": watcher.STATE_VERSION, "runs": {}}
                github = FakeGitHub()
                original = github.details
                github.details = lambda number: dict(original(number), changedFiles=files, additions=lines,
                    deletions=0, labels=[{"name": name} for name in ["Server", *labels]])
                config = dict(self.config, max_diff_characters=char_limit)
                with mock.patch.object(watcher, "generate_review", return_value=NO_FINDINGS) as generate:
                    watcher.process_pull_request(config, github, "reviewer", self.pull_request, path, state)
                self.assertEqual(int(allowed), generate.call_count)
                if not allowed:
                    self.assertIn("자동 리뷰 보류", github.posts[0][1]["body"])
                    self.assertIn("40개" if labels == ["refactor"] else "30개", github.posts[0][1]["body"])

    def test_label_change_during_generation_stops_post_and_does_not_regenerate(self):
        for change_at in (2, 3):
            with self.subTest(change_at=change_at):
                path = Path(self.config["state_file"])
                state = {"version": watcher.STATE_VERSION, "runs": {}}
                github = FakeGitHub()
                original = github.details
                def changing(number):
                    details = original(number)
                    details["labels"].append({"name": "refactor"})
                    if github.details_calls >= change_at:
                        details["labels"].append({"name": "feat"})
                    return details
                github.details = changing
                with mock.patch.object(watcher, "generate_review", return_value=NO_FINDINGS) as generate:
                    watcher.process_pull_request(self.config, github, "reviewer", self.pull_request, path, state)
                    self.assertEqual(int(change_at == 3), generate.call_count)
                    if change_at == 3:
                        watcher.process_pull_request(self.config, github, "reviewer", self.pull_request, path, state)
                        generate.assert_called_once()
                        self.assertEqual("stale", state["runs"][watcher.run_key(self.config, "reviewer", 7)]["status"])
                self.assertEqual([], github.posts)

    def test_label_change_after_deferral_does_not_grant_another_attempt(self):
        path, state = self.state()
        github = FakeGitHub()
        original = github.details
        github.details = lambda number: dict(original(number), changedFiles=35)
        with mock.patch.object(watcher, "generate_review") as generate:
            watcher.process_pull_request(self.config, github, "reviewer", self.pull_request, path, state)
            github.details = lambda number: dict(original(number), changedFiles=35,
                labels=[{"name": "Server"}, {"name": "refactor"}])
            watcher.process_pull_request(self.config, github, "reviewer", self.pull_request, path, state)
        generate.assert_not_called()
        self.assertEqual(1, len(github.posts))

    def test_custom_refactor_limits_and_invalid_config(self):
        config = dict(self.config, refactor_max_changed_files=35, refactor_max_changed_lines=1600)
        watcher.validate_config(config)
        self.assertEqual((35, 1600), watcher.review_limits(config, {"labels": [{"name": "refactor"}]}))
        for key in ("max_changed_files", "max_changed_lines", "max_diff_characters",
                    "refactor_max_changed_files", "refactor_max_changed_lines"):
            for value in (0, -1, True, "40"):
                with self.subTest(key=key, value=value), self.assertRaises(RuntimeError):
                    watcher.validate_config(dict(config, **{key: value}))


if __name__ == "__main__":
    unittest.main()
