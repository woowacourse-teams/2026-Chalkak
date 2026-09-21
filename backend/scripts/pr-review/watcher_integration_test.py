"""Exercise the actual watcher CLI with local stand-ins, never real credentials."""

import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("watcher.py")


class WatcherIntegrationTests(unittest.TestCase):
    def test_first_inline_review_then_new_push_and_provider_switch_spend_nothing(self):
        with tempfile.TemporaryDirectory(prefix="review-cli-test-") as directory:
            root = Path(directory)
            fixture = root / "fixture.json"
            fixture.write_text(json.dumps({"sha": "a" * 40, "viewer": "reviewer",
                                           "ai_calls": 0, "posts": [], "api_calls": []}))
            prefix = f"#!{sys.executable}\nimport json, sys\nfrom pathlib import Path\np = Path({str(fixture)!r})\ns = json.loads(p.read_text())\n"
            gh = root / "fake-gh"
            gh.write_text(prefix + '''
a = sys.argv[1:]
s["api_calls"].append(a)
details = {"number": 7, "title": "fixture", "body": "", "author": {"login": "teammate"},
           "state": "OPEN", "isDraft": False, "baseRefName": "be/develop", "headRefOid": s["sha"],
           "labels": [{"name": "Server"}], "changedFiles": 1, "additions": 1, "deletions": 1,
           "files": [{"path": "backend/a.py"}], "closingIssuesReferences": []}
if a[:2] == ["api", "user"]:
    print(s["viewer"])
elif a[:2] == ["pr", "list"]:
    print(json.dumps([details]))
elif a[:2] == ["pr", "view"]:
    print(json.dumps(details))
elif a[:2] == ["pr", "diff"]:
    print("diff --git a/backend/a.py b/backend/a.py\\n--- a/backend/a.py\\n+++ b/backend/a.py\\n@@ -1 +1 @@\\n-old\\n+new")
elif a[:2] == ["pr", "checks"]:
    print('[{"name":"test","bucket":"pass"}]')
elif a[:2] == ["api", "repos/team/repo/pulls/7/reviews"]:
    if a[a.index("--method") + 1] == "GET":
        print(json.dumps([[{"body": item["body"], "state": "COMMENTED", "user": {"login": "reviewer"}}
                           for item in s["posts"]]]))
    else:
        s["posts"].append(json.load(sys.stdin))
        print('{}')
else:
    raise SystemExit("Unexpected fake API request: " + repr(a))
p.write_text(json.dumps(s))
''')
            ai = root / "fake-ai"
            ai.write_text(prefix + '''
prompt = sys.stdin.read()
assert "findings" in prompt and "diff --git" in prompt
s["ai_calls"] += 1
p.write_text(json.dumps(s))
print(json.dumps({"findings": [{"severity": "수정 필수", "title": "입력 검증 누락",
  "condition": "빈 입력일 때", "impact": "잘못된 값 저장", "suggestion": "검증 추가",
  "location": {"path": "backend/a.py", "line": 1, "side": "RIGHT"}, "unanchored_reason": None}],
  "limitations": []}))
''')
            gh.chmod(0o755)
            ai.chmod(0o755)
            backend = root / "repo/backend"
            backend.mkdir(parents=True)
            config = {"version": 2, "repo_dir": str(backend), "repository": "team/repo",
                      "gh_command": str(gh), "provider": "codex", "provider_command": str(ai),
                      "path": "/usr/bin:/bin", "home": str(root), "base_branch": "be/develop",
                      "label": "Server", "interval_seconds": 300, "review_timeout_seconds": 10,
                      "max_changed_files": 30, "max_changed_lines": 1200, "max_diff_characters": 200000,
                      "max_reviews_per_poll": 1, "state_file": str(root / "state.json"),
                      "lock_file": str(root / "lock")}
            config_path = root / "config.json"
            config_path.write_text(json.dumps(config))
            command = [sys.executable, "-B", str(SCRIPT), "run", "--config", str(config_path), "--once"]
            first = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(0, first.returncode, first.stderr)
            state = json.loads(fixture.read_text())
            self.assertEqual(1, state["ai_calls"])
            self.assertEqual(1, len(state["posts"]))
            review = state["posts"][0]
            self.assertEqual("a" * 40, review["commit_id"])
            self.assertEqual("backend/a.py", review["comments"][0]["path"])
            self.assertNotIn("입력 검증 누락", review["body"])

            state["sha"] = "b" * 40
            fixture.write_text(json.dumps(state))
            config["provider"] = "claude"
            config_path.write_text(json.dumps(config))
            # Simulate a fresh install: published GitHub history still prevents reruns.
            (root / "state.json").unlink()
            second = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(0, second.returncode, second.stderr)
            state = json.loads(fixture.read_text())
            self.assertEqual(1, state["ai_calls"])
            self.assertEqual(1, len(state["posts"]))


if __name__ == "__main__":
    unittest.main()
