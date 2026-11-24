"""Regression tests for Codex transcript boundaries and child aggregation."""

import json
from pathlib import Path
import tempfile
import unittest

from codex_metrics import parse_session


def row(at, kind, payload):
    return json.dumps({"timestamp": f"2026-11-18T00:00:{at:02d}Z", "type": kind, "payload": payload})


def usage(response, tokens):
    return {"response_id": response, "usage": {"input_tokens": tokens, "cached_input_tokens": 0,
                                               "output_tokens": 0}}


class CodexMetricsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.day = Path(self.temp.name, "sessions/2026/11/18")
        self.day.mkdir(parents=True)
        self.parent = "11111111-1111-1111-1111-111111111111"
        self.child = "22222222-2222-2222-2222-222222222222"
        self.main = self.day / f"rollout-2026-11-18T00-00-00-{self.parent}.jsonl"

    def write(self, path, identity, parent, *events):
        meta = row(0, "session_meta", {"id": identity, "parent_thread_id": parent})
        path.write_text("\n".join((meta, *events)) + "\n")

    def flow(self):
        return row(1, "response_item", {"type": "message", "role": "user",
                                        "content": [{"type": "input_text", "text": "/client-flow #1054"}],
                                        "internal_chat_message_metadata_passthrough": {
                                            "content_item_kinds": ["user.text"]}})

    def test_only_create_call_output_marks_pr_boundary(self):
        url = "https://github.com/woowacourse-teams/2026-Chalkak/pull/2000"
        self.write(self.main, self.parent, None, self.flow(),
                   row(2, "token_usage_record", usage("a", 10)),
                   row(3, "response_item", {"type": "function_call", "call_id": "read",
                                            "arguments": '{"cmd":"gh pr view 2000"}'}),
                   row(4, "response_item", {"type": "function_call_output", "call_id": "read", "output": url}),
                   row(4, "response_item", {"type": "function_call", "call_id": "search",
                                            "arguments": '{"cmd":"rg \'gh pr create\' docs"}'}),
                   row(4, "response_item", {"type": "function_call_output", "call_id": "search", "output": url}),
                   row(5, "token_usage_record", usage("b", 20)),
                   row(6, "response_item", {"type": "function_call", "call_id": "create",
                                            "arguments": '{"cmd":"gh pr create --draft"}'}),
                   row(7, "response_item", {"type": "function_call_output", "call_id": "create", "output": url}),
                   row(8, "token_usage_record", usage("c", 30)))
        result = parse_session(self.main)
        self.assertEqual(result["pr"], 2000)
        self.assertEqual(result["pre"]["weighted_tokens"], 30)
        self.assertEqual(result["post"]["weighted_tokens"], 30)

    def test_pr_boundary_reads_exec_tool_list_output(self):
        # 실제 Codex의 exec 도구는 JS 코드를 input으로 받고, 결과를 글 조각 목록으로 남긴다.
        url = "https://github.com/woowacourse-teams/2026-Chalkak/pull/2000"
        self.write(self.main, self.parent, None, self.flow(),
                   row(2, "token_usage_record", usage("a", 10)),
                   row(3, "response_item", {"type": "custom_tool_call", "call_id": "create", "name": "exec",
                                            "input": 'const r=await tools.exec_command({cmd:"gh pr create --draft"})'}),
                   row(4, "response_item", {"type": "custom_tool_call_output", "call_id": "create",
                                            "output": [{"type": "input_text", "text": "Script completed"},
                                                       {"type": "input_text", "text": url}]}),
                   row(5, "token_usage_record", usage("b", 20)))
        result = parse_session(self.main)
        self.assertEqual(result["pr"], 2000)
        self.assertEqual(result["pre"]["weighted_tokens"], 10)
        self.assertEqual(result["post"]["weighted_tokens"], 20)

    def test_child_and_grandchild_usage_included_but_unrelated_excluded(self):
        self.write(self.main, self.parent, None, self.flow(),
                   row(2, "token_usage_record", usage("a", 10)))
        child_path = self.day / f"rollout-2026-11-18T00-00-00-{self.child}.jsonl"
        self.write(child_path, self.child, self.parent,
                   row(3, "token_usage_record", usage("b", 20)))
        grandchild = "33333333-3333-3333-3333-333333333333"
        self.write(self.day / f"rollout-2026-11-18T00-00-00-{grandchild}.jsonl", grandchild, self.child,
                   row(4, "token_usage_record", usage("c", 30)))
        unrelated = "44444444-4444-4444-4444-444444444444"
        self.write(self.day / f"rollout-2026-11-18T00-00-00-{unrelated}.jsonl", unrelated, None,
                   row(5, "token_usage_record", usage("d", 40)))
        result = parse_session(self.main)
        self.assertEqual(result["pre"]["weighted_tokens"], 60)
        self.assertEqual(result["pre"]["calls"], 3)

    def user_message(self, at, text, kind="user.text"):
        """실제 Codex 기록 모양의 사용자 줄. kind는 사람 글(user.text)이나 스킬 주입 종류다."""
        return row(at, "response_item", {"type": "message", "role": "user",
                                         "content": [{"type": "input_text", "text": text}],
                                         "internal_chat_message_metadata_passthrough": {
                                             "content_item_kinds": [kind]}})

    def skill_injection(self, at, name="client-flow"):
        return self.user_message(at, f"<skill>\n<name>{name}</name>\n<path>/skills/{name}/SKILL.md</path>",
                                 "skills.selected_skill_instructions")

    def test_dollar_invocation_without_number_uses_branch_issue(self):
        self.write(self.main, self.parent, None, self.user_message(1, "$client-flow 로 진행해"),
                   self.skill_injection(1), row(2, "token_usage_record", usage("a", 10)))
        self.assertEqual(parse_session(self.main, 1054)["issue"], 1054)

    def test_dollar_invocation_number_wins_over_branch_issue(self):
        self.write(self.main, self.parent, None, self.user_message(1, "$client-flow #2000"),
                   self.skill_injection(1), row(2, "token_usage_record", usage("a", 10)))
        self.assertEqual(parse_session(self.main, 1054)["issue"], 2000)

    def test_selected_skill_counts_when_invoked_mid_sentence(self):
        self.write(self.main, self.parent, None, self.user_message(1, "이 작업은 $client-flow로 진행해"),
                   self.skill_injection(1), row(2, "token_usage_record", usage("a", 10)))
        self.assertEqual(parse_session(self.main, 1054)["issue"], 1054)

    def test_handoff_prompt_counts(self):
        text = "client-flow 스킬(.agents/skills/client-flow/SKILL.md)로 #1054를 이어 가 주세요."
        self.write(self.main, self.parent, None, self.user_message(1, text),
                   row(2, "token_usage_record", usage("a", 10)))
        self.assertEqual(parse_session(self.main)["issue"], 1054)

    def test_mentions_and_other_skills_do_not_count(self):
        for text in ("`$client-flow`는 어떤 스킬이야?", "client-flow 스킬은 어떻게 써?", "$client-flow가 뭐야?"):
            with self.subTest(text=text):
                self.write(self.main, self.parent, None, self.user_message(1, text),
                           self.skill_injection(1, "client-pr"), row(2, "token_usage_record", usage("a", 10)))
                self.assertIsNone(parse_session(self.main, 1054))

    def test_forked_child_is_not_collected_as_its_own_flow(self):
        child_path = self.day / f"rollout-2026-11-18T00-00-00-{self.child}.jsonl"
        self.write(child_path, self.child, self.parent, self.user_message(1, "$client-flow 로 진행해"),
                   self.skill_injection(1), row(2, "token_usage_record", usage("a", 10)))
        self.assertIsNone(parse_session(child_path, 1054))


if __name__ == "__main__":
    unittest.main()
