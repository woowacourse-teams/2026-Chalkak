"""모의 GitHub가 허용된 상태만 바꾸고 실제 네트워크를 사용하지 않는지 검사한다."""
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


class MockGithubTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.bin = self.root / 'bin/gh'
        self.bin.parent.mkdir()
        shutil.copyfile(Path(__file__).with_name('mock_github.py'), self.bin)
        source = Path(__file__).parent / 'cases/assignee-repair/fixture/.eval/state.json'
        shutil.copyfile(source, self.root / 'state.json')
        self.initial = (self.root / 'state.json').read_text()

    def run_gh(self, *args):
        return subprocess.run([sys.executable, str(self.bin), *args], capture_output=True, text=True, timeout=5)

    def test_queries_are_audited_without_mutation(self):
        result = self.run_gh('api', '--method', 'GET', 'repos/team/fixture/issues/901')
        self.assertEqual(0, result.returncode)
        self.assertEqual(901, json.loads(result.stdout)['number'])
        self.assertEqual(self.initial, (self.root / 'state.json').read_text())
        self.assertEqual(1, len((self.root / 'calls.jsonl').read_text().splitlines()))

    def test_assignment_preserves_others_and_is_idempotent(self):
        for kind, number in (('issue','901'),('pr','911')):
            for _ in range(2):
                self.assertEqual(0,self.run_gh(kind,'edit',number,'--add-assignee','fixture-author').returncode)
            view=self.run_gh(kind,'view',number,'--json','assignees')
            self.assertEqual(['teammate','fixture-author'],[a['login'] for a in json.loads(view.stdout)['assignees']])

    def test_close_and_reread_produce_completed_state(self):
        self.assertEqual(0,self.run_gh('issue','close','901','--reason','completed').returncode)
        view=self.run_gh('api','repos/team/fixture/issues/901')
        self.assertEqual('closed',json.loads(view.stdout)['state'])
        self.assertEqual('completed',json.loads(view.stdout)['state_reason'])

    def test_unknown_and_external_actions_fail_without_mutation(self):
        for args in (('api','repos/other/repo/issues/901'),('issue','create'),('pr','merge','911'),('issue','edit','901','--remove-assignee','teammate'),('api','--method','POST','repos/team/fixture/issues')):
            self.assertNotEqual(0,self.run_gh(*args).returncode)
        self.assertEqual(self.initial,(self.root/'state.json').read_text())
