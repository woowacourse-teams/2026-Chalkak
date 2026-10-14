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


class SplitGithubTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.repo = Path(temporary.name)
        self.folder = self.repo / '.eval'
        self.binary = self.folder / 'bin/gh'
        self.binary.parent.mkdir(parents=True)
        shutil.copyfile(Path(__file__).with_name('mock_github.py'), self.binary)
        source = Path(__file__).parent / 'cases/commit-plan-split-approved/fixture/.eval/state.json'
        self.state_path = self.folder / 'state.json'
        shutil.copyfile(source, self.state_path)

    def run_gh(self, *args):
        return subprocess.run([sys.executable, str(self.binary), *args], cwd=self.repo,
                              capture_output=True, text=True, timeout=5)

    def state(self):
        return json.loads(self.state_path.read_text())

    def create(self, *extra):
        return self.run_gh('issue', 'create', '--repo', 'team/fixture', '--title', 'feat: Slack 알림',
                           '--body', '저장 성공 후 알림; 실패는 접수를 취소하지 않는다.',
                           '--assignee', 'fixture-author', '--label', 'Server,feat', *extra)

    def test_creates_updates_links_and_rereads_without_losing_existing_issue(self):
        self.assertEqual(0, self.create().returncode)
        self.assertEqual(0, self.run_gh('issue', 'edit', '901', '--body', '접수만 완성한다.').returncode)
        for _ in range(2):
            link = self.run_gh('api', '-X', 'POST', 'repos/team/fixture/issues/900/sub_issues',
                               '-F', 'sub_issue_id=100902')
            self.assertEqual(0, link.returncode, link.stderr)
        parent = self.run_gh('api', 'repos/team/fixture/issues/902/parent')
        children = self.run_gh('api', 'repos/team/fixture/issues/900/sub_issues')
        self.assertEqual(900, json.loads(parent.stdout)['number'])
        self.assertEqual({901, 902}, {v['number'] for v in json.loads(children.stdout)})
        state = self.state()
        self.assertEqual({'900', '901', '902'}, set(state['issues']))
        self.assertEqual(903, state['next_issue'])
        self.assertEqual('접수만 완성한다.', state['issues']['901']['body'])
        self.assertEqual(['teammate', 'fixture-author'], [a['login'] for a in state['issues']['901']['assignees']])
        self.assertEqual(['Server', 'feat'], [v['name'] for v in state['issues']['902']['labels']])

    def test_readonly_proposal_cannot_create_edit_or_link(self):
        state = self.state(); state['allow_issue_split'] = False
        self.state_path.write_text(json.dumps(state))
        before = self.state_path.read_text()
        self.assertEqual(0, self.run_gh('issue', 'view', '901', '--json', 'body').returncode)
        for result in (self.create(), self.run_gh('issue', 'edit', '901', '--body', '수정'),
                       self.run_gh('api', '-X', 'POST', 'repos/team/fixture/issues/900/sub_issues',
                                   '-F', 'sub_issue_id=100901')):
            self.assertNotEqual(0, result.returncode)
        self.assertEqual(before, self.state_path.read_text())

    def test_external_repo_parent_replacement_and_unknown_actions_are_rejected(self):
        before = self.state_path.read_text()
        invalid = [('issue', 'view', '901', '--repo', 'other/repo'),
                   ('api', 'repos/other/repo/issues/901'),
                   ('api', '-X', 'DELETE', 'repos/team/fixture/issues/901'),
                   ('api', '-X', 'POST', 'repos/team/fixture/issues/900/sub_issues',
                    '-F', 'sub_issue_id=100901', '-F', 'replace_parent=true'),
                   ('issue', 'edit', '901', '--remove-assignee', 'teammate')]
        for args in invalid:
            with self.subTest(args=args): self.assertNotEqual(0, self.run_gh(*args).returncode)
        self.assertNotEqual(0, self.create('--web', 'yes').returncode)
        self.assertEqual(before, self.state_path.read_text())

    def test_body_file_must_resolve_inside_actor_repo(self):
        body = self.folder / 'body.md'; body.write_text('본문 전체\n')
        result = self.run_gh('issue', 'edit', '901', '--body-file', str(body))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('본문 전체\n', self.state()['issues']['901']['body'])
        outside = self.repo.parent / (self.repo.name + '-outside.md')
        outside.write_text('평가 기준\n'); self.addCleanup(outside.unlink)
        body.unlink(); body.symlink_to(outside)
        before = self.state_path.read_text()
        self.assertNotEqual(0, self.run_gh('issue', 'edit', '901', '--body-file', str(body)).returncode)
        self.assertEqual(before, self.state_path.read_text())

    def test_existing_parent_cannot_be_replaced(self):
        state = self.state(); state['issues']['901']['parent'] = 999
        self.state_path.write_text(json.dumps(state)); before = self.state_path.read_text()
        result = self.run_gh('api', '-X', 'POST', 'repos/team/fixture/issues/900/sub_issues',
                             '-F', 'sub_issue_id=100901')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(before, self.state_path.read_text())
