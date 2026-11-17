import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("collect_metrics", Path(__file__).with_name("collect_metrics.py"))
metrics = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(metrics)


class CollectorTests(unittest.TestCase):
    def test_mention_is_not_an_invocation(self):
        with tempfile.TemporaryDirectory() as temp:
            fp = Path(temp, "a" * 32 + ".jsonl")
            for message in ("이 명령 /client-flow 설명해 줘", "오늘 /client-flow를 써야 할까?"):
                fp.write_text("\n".join(json.dumps(row, ensure_ascii=False) for row in [
                    {"type": "user", "timestamp": "2026-01-01T00:00:00Z", "message": {"content": message}},
                    {"type": "assistant", "timestamp": "2026-01-01T00:01:00Z", "message": {"id": "m1", "usage": {"input_tokens": 10}}},
                ]))
                self.assertIsNone(metrics.parse_session(fp, 42))

    def test_session_segments_and_private_output(self):
        with tempfile.TemporaryDirectory() as temp:
            fp = Path(temp, "a" * 32 + ".jsonl")
            rows = [
                {"type": "user", "timestamp": "2026-01-01T00:00:00Z", "message": {"content": "/client-flow #42"}},
                {"type": "assistant", "timestamp": "2026-01-01T00:01:00Z", "message": {"id": "m1", "usage": {"input_tokens": 10, "output_tokens": 2}}},
                {"type": "assistant", "timestamp": "2026-01-01T00:01:01Z", "message": {"id": "m1", "usage": {"input_tokens": 10, "output_tokens": 2}}},
                {"type": "pr-link", "timestamp": "2026-01-01T00:02:00Z", "prNumber": 43},
                {"type": "user", "timestamp": "2026-01-01T00:03:00Z", "message": {"content": "수정해줘"}},
                {"type": "assistant", "timestamp": "2026-01-01T00:04:00Z", "message": {"id": "m2", "usage": {"input_tokens": 5, "output_tokens": 1}}},
            ]
            fp.write_text("\n".join(json.dumps(x) for x in rows))
            result = metrics.parse_session(fp)
            self.assertEqual(result["issue"], 42)
            self.assertEqual(result["pr"], 43)
            self.assertEqual(result["pre"]["weighted_tokens"], 20)
            self.assertEqual(result["pre"]["calls"], 1)
            self.assertEqual(result["post"]["weighted_tokens"], 10)
            self.assertEqual(result["post"]["humans"], 1)
            self.assertNotIn("수정해줘", json.dumps(result, ensure_ascii=False))
            fp.write_text(json.dumps({"type": "user", "timestamp": "2026-01-01T00:00:00Z", "message": {"content": "질문만"}}))
            self.assertIsNone(metrics.parse_session(fp, 42))

    def test_empty_remote_and_update_keep_other_file(self):
        with tempfile.TemporaryDirectory() as temp:
            remote = str(Path(temp, "remote.git"))
            subprocess.run(["git", "init", "--bare", "-q", remote], check=True)
            self.assertTrue(metrics.put(remote, "person-a/a.json", b'{"issue":1}'))
            self.assertTrue(metrics.put(remote, "person-b/b.json", b'{"issue":2}'))
            self.assertTrue(metrics.put(remote, "person-a/a.json", b'{"issue":3}'))
            a = subprocess.check_output(["git", f"--git-dir={remote}", "show", "client/metrics:person-a/a.json"])
            b = subprocess.check_output(["git", f"--git-dir={remote}", "show", "client/metrics:person-b/b.json"])
            self.assertEqual(a, b'{"issue":3}')
            self.assertEqual(b, b'{"issue":2}')

    def test_retry_after_racing_push(self):
        with tempfile.TemporaryDirectory() as temp:
            remote = str(Path(temp, "remote.git"))
            subprocess.run(["git", "init", "--bare", "-q", remote], check=True)
            original = metrics.run
            fired = False
            def racing_run(*args, **kwargs):
                nonlocal fired
                if not fired and "push" in args and "person-a/a.json" not in args:
                    fired = True
                    with patch.object(metrics, "run", original):
                        self.assertTrue(metrics.put(remote, "person-b/b.json", b'{}'))
                return original(*args, **kwargs)
            with patch.object(metrics, "run", racing_run):
                self.assertTrue(metrics.put(remote, "person-a/a.json", b'{}'))
            self.assertTrue(fired)
            files = subprocess.check_output(["git", f"--git-dir={remote}", "ls-tree", "-r", "--name-only", "client/metrics"]).decode()
            self.assertIn("person-a/a.json", files)
            self.assertIn("person-b/b.json", files)

    def test_unreachable_remote_does_not_raise(self):
        with tempfile.TemporaryDirectory() as temp:
            self.assertFalse(metrics.put(str(Path(temp, "missing.git")), "person/a.json", b'{}'))

    def test_failed_push_is_retried_on_next_collection(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            local = root / "local"
            local.mkdir()
            subprocess.run(["git", "init", "-q", str(local)], check=True)
            fp = root / ("a" * 32 + ".jsonl")
            fp.write_text("\n".join(json.dumps(row) for row in [
                {"type": "user", "timestamp": "2026-01-01T00:00:00Z", "message": {"content": "/client-flow #42"}},
                {"type": "assistant", "timestamp": "2026-01-01T00:01:00Z", "message": {"id": "m1", "usage": {"input_tokens": 10}}},
            ]))
            hook = {"transcript_path": str(fp), "cwd": str(local), "session_id": "a" * 32}
            home = root / "home"
            remote = str(root / "remote.git")
            with patch.object(metrics, "fingerprint", return_value="a" * 40):
                metrics.collect(hook, home, remote)
                queued = list((home / "outbox").glob("*.json"))
                self.assertEqual(len(queued), 1)
                subprocess.run(["git", "init", "--bare", "-q", remote], check=True)
                metrics.collect(hook, home, remote)
            self.assertEqual(list((home / "outbox").glob("*.json")), [])
            files = subprocess.check_output(["git", f"--git-dir={remote}", "ls-tree", "-r", "--name-only", "client/metrics"]).decode()
            self.assertIn("a" * 32 + ".json", files)


if __name__ == "__main__":
    unittest.main()
