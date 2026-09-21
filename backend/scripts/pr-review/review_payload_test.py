"""Review locations and output validation without GitHub or AI."""

import json
import unittest

from review_payload import diff_locations, make_payload


DIFF = """diff --git a/backend/a.py b/backend/a.py
--- a/backend/a.py
+++ b/backend/a.py
@@ -10,3 +10,4 @@
 context
-old
+new
+added
 context
@@ -30 +31 @@
-old2
+new2
"""


def finding():
    return {"severity": "수정 필수", "title": "입력 검증 누락", "condition": "빈 입력일 때",
            "impact": "잘못된 값이 저장됩니다.", "suggestion": "저장 전에 검증하세요.",
            "location": {"path": "backend/a.py", "side": "RIGHT", "line": 11},
            "unanchored_reason": None}


class PayloadTests(unittest.TestCase):
    def payload(self, findings, *, limitations=None, diff=DIFF, paths=None):
        return make_payload(json.dumps({"findings": findings, "limitations": limitations or []}),
                            diff, paths or {"backend/a.py"}, "a" * 40, "<!-- watcher -->")

    def test_line_numbers_use_each_side_and_hunk(self):
        locations, _ = diff_locations(DIFF)
        self.assertEqual({("backend/a.py", "LEFT", 11), ("backend/a.py", "RIGHT", 11),
                          ("backend/a.py", "RIGHT", 12), ("backend/a.py", "LEFT", 30),
                          ("backend/a.py", "RIGHT", 31)}, locations)

    def test_inline_payload_pins_commit_and_does_not_repeat_findings_in_body(self):
        payload = self.payload([finding()])
        self.assertEqual("COMMENT", payload["event"])
        self.assertEqual("a" * 40, payload["commit_id"])
        self.assertEqual(11, payload["comments"][0]["line"])
        self.assertIn("입력 검증 누락", payload["comments"][0]["body"])
        self.assertNotIn("입력 검증 누락", payload["body"])

    def test_common_problem_requires_reason_and_stays_in_body(self):
        item = finding()
        item.update(location=None, unanchored_reason="변경 파일 전체에 누락된 계약으로 연결할 변경 줄이 없습니다.")
        payload = self.payload([item])
        self.assertEqual([], payload["comments"])
        self.assertIn("입력 검증 누락", payload["body"])
        item["unanchored_reason"] = ""
        with self.assertRaises(ValueError):
            self.payload([item])

    def test_no_findings_is_explicit_and_keeps_limitations(self):
        payload = self.payload([], limitations=["CI가 실행되지 않았습니다."])
        self.assertIn("발견된 문제 없음", payload["body"])
        self.assertIn("CI가 실행되지 않았습니다.", payload["body"])

    def test_invalid_location_rejects_entire_result_instead_of_body_fallback(self):
        for change in ({"line": 10}, {"line": 999}, {"line": True}, {"line": "11"},
                       {"path": "other.py"}, {"path": []}, {"side": "BOTH"}, {"side": []},
                       {"line": 12, "side": "LEFT"}):
            with self.subTest(change=change):
                item = finding()
                item["location"].update(change)
                with self.assertRaises(ValueError):
                    self.payload([finding(), item])

    def test_schema_rejects_markdown_unknown_fields_and_bad_severity(self):
        for output in ('```json\n{}\n```', '{}', '[]', '{"findings": [], "limitations": [], "extra": 1}'):
            with self.subTest(output=output), self.assertRaises(ValueError):
                make_payload(output, DIFF, {"backend/a.py"}, "a" * 40, "marker")
        for change in ({"severity": "칭찬"}, {"impact": ""}, {"title": "<!-- forged -->"},
                       {"unanchored_reason": "inline"}, {"extra": 1}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.payload([dict(finding(), **change)])

    def test_max_five_and_duplicate_findings(self):
        items = []
        for index in range(5):
            item = finding()
            item["title"] = f"문제 {index}"
            items.append(item)
        self.assertEqual(5, len(self.payload(items)["comments"]))
        with self.assertRaises(ValueError):
            self.payload(items + [finding()])
        with self.assertRaises(ValueError):
            self.payload([finding(), finding()])

    def test_added_deleted_and_renamed_files(self):
        fixtures = [
            ("/dev/null", "b/backend/a.py", "@@ -0,0 +1 @@\n+new\n", "RIGHT"),
            ("a/backend/a.py", "/dev/null", "@@ -1 +0,0 @@\n-old\n", "LEFT"),
            ("a/old.py", "b/backend/a.py", "@@ -1 +1 @@\n-old\n+new\n", "LEFT"),
        ]
        for old, new, hunk, side in fixtures:
            with self.subTest(old=old, new=new):
                diff = f"diff --git a/old b/new\n--- {old}\n+++ {new}\n{hunk}"
                item = finding()
                item["location"].update(line=1, side=side)
                self.assertEqual(1, len(self.payload([item], diff=diff)["comments"]))

    def test_unicode_quoted_path_and_source_lines_that_look_like_headers(self):
        diff = ('diff --git a/x b/x\n--- "a/\\355\\225\\234.py"\n'
                '+++ "b/\\355\\225\\234.py"\n@@ -1 +1 @@\n---old\n+++new\n'
                '\\ No newline at end of file\n')
        locations, _ = diff_locations(diff)
        self.assertEqual({("한.py", "LEFT", 1), ("한.py", "RIGHT", 1)}, locations)

    def test_truncated_or_malformed_diff_is_rejected(self):
        for diff in (DIFF.rsplit("+new2", 1)[0], DIFF.replace("+new2", "unexpected")):
            with self.subTest(diff=diff), self.assertRaises(ValueError):
                diff_locations(diff)


if __name__ == "__main__":
    unittest.main()
