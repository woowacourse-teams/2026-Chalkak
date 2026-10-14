"""복수 스킬·압축 평가의 누락·격리·거짓 통과를 검증한다. AI 호출 없음."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

import context_adapter
import java_fixture
import run_test as fixtures

runner = fixtures.runner


def completed(turn_id, thread="one"):
    return {"method": "turn/completed", "params": {"threadId": thread,
            "turn": {"id": turn_id, "status": "completed"}}}


class ExtendedEvaluationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def protocol(self, records):
        (self.root / "protocol.jsonl").write_text("".join(json.dumps(r) + "\n" for r in records))

    def test_no_compaction_cannot_pass_even_with_completed_turns(self):
        self.protocol([completed(str(i)) for i in range(5)])
        criteria = {"setup": {"conversation": [{}, {}, {}, {}]}}
        self.assertFalse(runner.valid_context_evidence(self.root, criteria))

    def test_started_compaction_or_missing_post_compaction_turn_is_not_pass(self):
        for method in ["item/started", "item/completed"]:
            with self.subTest(method=method):
                self.protocol([completed("a"), completed("b"), {"method": method,
                    "params": {"threadId": "one", "turnId": "compact", "item": {"type": "contextCompaction"}}},
                    completed("compact")])
                self.assertFalse(runner.valid_context_evidence(self.root, {"setup": {"conversation": [{}, {}, {}, {}]}}))

    def test_real_event_shape_same_thread_and_followup_required(self):
        records = [completed("a"), completed("b"), {"method": "item/completed",
            "params": {"threadId": "one", "turnId": "compact", "item": {"type": "contextCompaction"}}},
            completed("compact"), completed("after")]
        criteria = {"setup": {"conversation": [{}, {}, {}, {}]}}
        self.protocol(records)
        self.assertTrue(runner.valid_context_evidence(self.root, criteria))
        records[-1] = completed("after", "another")
        self.protocol(records)
        self.assertFalse(runner.valid_context_evidence(self.root, criteria))

    def test_conversation_files_cannot_escape_case_or_omit_resume(self):
        for steps in [[{"prompt": "../outside.md"}, {"compact": True}, {"prompt": "prompt.md"}],
                      [{"prompt": "prompt.md"}, {"prompt": "prompt.md"}, {"compact": True}],
                      [{"compact": True}, {"prompt": "prompt.md"}, {"prompt": "prompt.md"}]]:
            with self.subTest(steps=steps), self.assertRaises(ValueError):
                runner.conversation_inputs(self.root, {"setup": {"conversation": steps}})

    def test_disabled_mcp_preserves_transport_only_for_app_server(self):
        args = runner.disabled_server_definitions(["docs"], app_server=True)
        self.assertEqual(['-c', 'mcp_servers.docs.enabled=false'], args)
        self.assertEqual(['-c', 'mcp_servers.docs.enabled=false', '-c',
                          'mcp_servers.docs.command="/usr/bin/false"'], runner.disabled_server_definitions(["docs"]))
        for name in ["../other", "docs;run", "docs.enabled"]:
            with self.subTest(name=name), self.assertRaises(ValueError):
                runner.disabled_server_definitions([name])

    def test_long_inputs_and_real_compaction_are_separate_from_actor_files(self):
        inputs = runner.conversation_inputs(runner.HERE / "cases/long-context-compacted-resume",
            json.loads((runner.HERE / "cases/long-context-compacted-resume/criteria.json").read_text()))
        self.assertGreater(sum(len(p) for p in inputs if isinstance(p, str)), 20000)
        self.assertEqual({"compact": True}, inputs[2])
        self.assertNotIn("criteria", "\n".join(p for p in inputs if isinstance(p, str)))

    def test_binary_runtime_is_immutable_and_only_new_compiled_classes_allowed(self):
        old = {"files": {".eval/runtime/jars/example.jar": "binary-sha256:old"}, "refs": "r", "head": "h", "config": "c"}
        new = {**old, "files": {**old["files"], "backend/build/fixture-classes/X.class": "binary-sha256:new"}}
        mechanical = {"allowed_changed_paths": [], "required_changed_paths": [], "required_text": {},
                      "preserved_text": {}, "allowed_new_binary_dirs": ["backend/build/fixture-classes"]}
        self.assertEqual([], runner.compare(old, new, {"mechanical": mechanical})[0])
        new["files"][".eval/runtime/jars/example.jar"] = "binary-sha256:changed"
        self.assertTrue(runner.compare(old, new, {"mechanical": mechanical})[0])

    def test_missing_java_dependencies_are_not_downloaded_or_reported_available(self):
        with mock.patch.object(java_fixture.Path, "home", return_value=self.root):
            result = java_fixture.prepare(self.root / "actor")
        self.assertFalse(result["available"])
        self.assertIn("junit-jupiter-api:5.12.2", result["missing"])
        self.assertEqual([], result["jars"])

    def test_native_protocol_uses_same_thread_and_compact_before_followup(self):
        script = self.root / "server.py"
        script.write_text('''import json,sys
def emit(value): print(json.dumps(value), flush=True)
count=0
for line in sys.stdin:
 r=json.loads(line);m=r['method'];p=r.get('params',{})
 if m=='initialized': continue
 if m=='initialize': result={}
 elif m=='thread/start': result={'thread':{'id':'one'},'cwd':p['cwd'],'approvalPolicy':'never'}
 elif m=='turn/start':
  count+=1;tid=str(count);result={'turn':{'id':tid}}
 elif m=='thread/compact/start': result={}
 else: raise RuntimeError(m)
 emit({'id':r['id'],'result':result})
 if m=='turn/start':
  emit({'method':'item/completed','params':{'threadId':'one','turnId':tid,'item':{'type':'agentMessage','text':'fixture answer'}}})
  emit({'method':'turn/completed','params':{'threadId':'one','turn':{'id':tid,'status':'completed'}}})
 if m=='thread/compact/start':
  emit({'method':'item/completed','params':{'threadId':'one','turnId':'compact','item':{'type':'contextCompaction'}}})
''')
        stages = []
        code, _ = context_adapter.execute([sys.executable, str(script)], self.root, self.root,
                         ["before", {"compact": True}, "after"], 5, stages.append)
        self.assertEqual(0, code)
        self.assertEqual([0, 1, 2], stages)
        evidence = context_adapter.evidence(self.root)
        self.assertEqual(1, evidence["completed_compactions"])
        self.assertEqual(1, evidence["resumed_turns"])
        self.assertTrue(evidence["same_thread"])


if __name__ == "__main__":
    unittest.main()
