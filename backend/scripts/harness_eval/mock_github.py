#!/usr/bin/env python3
"""격리 평가 전용 gh 대역. 네트워크·인증·실제 GitHub에 접근하지 않는다."""
import json
from pathlib import Path
import re
import sys


def split_command(args, folder, state, flag, output, save):
    """재분할 사례만 사용하는 오프라인 이슈 저장소. 기존 사례의 권한은 넓히지 않는다."""
    issues = state['issues']
    selected_repo = flag('--repo', flag('-R', 'team/fixture'))
    if selected_repo != 'team/fixture':
        raise ValueError('외부 저장소는 지원하지 않습니다')

    def view(value):
        return {**value, 'url': value['html_url'], 'state': value['state'].upper(),
                'author': {'login': 'fixture-author'}}

    def writable():
        if state.get('allow_issue_split') is not True:
            raise ValueError('이 사례는 이슈 쓰기를 허용하지 않습니다')

    def body():
        filename = flag('--body-file')
        if filename:
            path = Path(filename).resolve()
            if not path.is_relative_to(folder.parent.resolve()) or path.stat().st_size > 100_000:
                raise ValueError('모의 본문 파일은 평가 저장소 내부의 작은 파일만 허용합니다')
            return path.read_text()
        return flag('--body', flag('-b'))

    if args[:2] == ['issue', 'list']:
        output([view(value) for value in issues.values()]); return
    if args[:2] == ['issue', 'create']:
        writable()
        supported = {'--repo', '-R', '--title', '-t', '--body', '-b', '--body-file', '--assignee', '-a', '--label', '-l'}
        if len(args[2:]) % 2 or any(arg not in supported for arg in args[2::2]):
            raise ValueError('지원하지 않는 이슈 생성 옵션')
        title, content = flag('--title', flag('-t')), body()
        assignee = flag('--assignee', flag('-a'))
        if not title or not content or assignee not in ('fixture-author', '@me'):
            raise ValueError('제목·본문·본인 담당자가 필요합니다')
        labels = []
        for index, arg in enumerate(args[:-1]):
            if arg in ('--label', '-l'):
                labels.extend(args[index+1].split(','))
        if not labels or set(labels) - {'Server', 'feat', 'docs'}:
            raise ValueError('확인된 라벨이 필요합니다')
        number = state['next_issue']
        issues[str(number)] = {'number': number, 'id': 100000 + number,
            'html_url': f'https://github.com/team/fixture/issues/{number}',
            'title': title, 'body': content, 'state': 'open', 'parent': None,
            'labels': [{'name': label} for label in labels],
            'assignees': [{'login': 'fixture-author'}]}
        state['next_issue'] += 1
        save(); print(issues[str(number)]['html_url']); return
    if len(args) >= 3 and args[0] == 'issue' and args[1] in ('view', 'edit'):
        number = args[2].rsplit('/', 1)[-1]
        value = issues[number]
        if args[1] == 'edit':
            writable()
            supported = {'--repo', '-R', '--title', '-t', '--body', '-b', '--body-file', '--add-assignee'}
            if len(args[3:]) % 2 or any(arg not in supported for arg in args[3::2]):
                raise ValueError('지원하지 않는 이슈 수정 옵션')
            title, content = flag('--title', flag('-t')), body()
            if title is not None: value['title'] = title
            if content is not None: value['body'] = content
            assignee = flag('--add-assignee')
            if assignee not in (None, 'fixture-author', '@me'):
                raise ValueError('본인 담당자 추가만 지원합니다')
            if assignee and not any(a['login'] == 'fixture-author' for a in value['assignees']):
                value['assignees'].append({'login': 'fixture-author'})
            save()
        output(view(value)); return
    if args and args[0] == 'api':
        endpoint = next((a for a in args[1:] if a.startswith('repos/')), '')
        match = re.fullmatch(r'repos/team/fixture/issues(?:/(\d+)(?:/(parent|sub_issues))?)?', endpoint)
        if not match: raise ValueError('지원하지 않는 모의 REST 경로')
        method = flag('--method', flag('-X', 'GET'))
        number, relation = match.groups()
        value = issues[number] if number else None
        if method == 'GET':
            if relation == 'parent':
                if value['parent'] is None: raise ValueError('부모 이슈 없음')
                output(issues[str(value['parent'])]); return
            if relation == 'sub_issues':
                output([v for v in issues.values() if v['parent'] == int(number)]); return
            output(value if number else list(issues.values())); return
        if method == 'POST' and relation == 'sub_issues':
            writable()
            fields = dict(arg.split('=', 1) for arg in args[1:] if '=' in arg)
            if set(fields) != {'sub_issue_id'}: raise ValueError('sub_issue_id만 허용합니다')
            child = next(v for v in issues.values() if v['id'] == int(fields['sub_issue_id']))
            if child['number'] == int(number) or value['parent'] is not None:
                raise ValueError('이 사례의 메인 아래에만 연결할 수 있습니다')
            if child['parent'] not in (None, int(number)):
                raise ValueError('기존 부모 변경은 허용하지 않습니다')
            child['parent'] = int(number)
            save(); output(child); return
    raise ValueError('지원하지 않는 재분할 모의 gh 명령: ' + str(args))


def main():
    folder = Path(__file__).resolve().parent.parent
    args = sys.argv[1:]
    with (folder / 'calls.jsonl').open('a') as log:
        log.write(json.dumps(args) + '\n')
    state = json.loads((folder / 'state.json').read_text())
    def flag(name, default=None):
        return args[args.index(name)+1] if name in args else default
    def output(value):
        if '--jq' in args or '-q' in args:
            query = flag('--jq', flag('-q'))
            if query == '.login':
                print(value['login']); return
            if query == '.assignees[].login':
                print('\n'.join(x['login'] for x in value['assignees'])); return
            raise ValueError('이 모의 도구에서 지원하지 않는 jq')
        print(json.dumps(value, ensure_ascii=False))
    def save():
        (folder / 'state.json').write_text(json.dumps(state, ensure_ascii=False, indent=2)+'\n')
    def issue_view(value):
        result = dict(value)
        result.update(url=value['html_url'], state=value['state'].upper(), stateReason=(value.get('state_reason') or '').upper(), closed=value['state']=='closed', author={'login':'fixture-author'}, labels=[{'name':'Server'},{'name':'docs'}])
        return result
    if args[:2] == ['auth','status']:
        print('fixture-author (offline mock)'); return
    if args[:2] == ['repo','view']:
        output({'nameWithOwner':'team/fixture','url':'https://github.com/team/fixture','defaultBranchRef':{'name':'be/develop'}});return
    if args[:2] == ['label','list']:
        output([{'name': label} for label in ('Server', 'docs', 'feat')]);return
    if 'issues' in state and (args[:1] == ['issue'] or args[:1] == ['api'] and any(a.startswith('repos/') for a in args)):
        split_command(args, folder, state, flag, output, save); return
    if args and args[0] == 'api':
        endpoint = next((a for a in args[1:] if a == 'user' or a.startswith('repos/')), '')
        method = flag('--method',flag('-X','GET'))
        if method != 'GET':
            raise ValueError('모의 REST는 GET만 지원; 변경은 issue close/edit 또는 pr edit 사용')
        if endpoint == 'user':
            output({'login':'fixture-author'});return
        resource = endpoint.removeprefix('repos/team/fixture/')
        if resource == 'issues/901': output(state['issue']); return
        if resource == 'pulls/911': output(state['pr']); return
        if resource == 'pulls/911/files': output(state.get('files',[])); return
        if resource == 'issues/902':
            output({'number':902,'body':'README 실행 항목에 backend에서 ./gradlew bootRun 안내','state':'open','assignees':[{'login':'fixture-author'}]});return
        raise ValueError('지원하지 않는 모의 REST 조회')
    if len(args)>=3 and args[0] in ('issue','pr'):
        kind, action, number = args[:3]
        expected = '901' if kind=='issue' else '911'
        if number != expected and not number.endswith('/'+expected):
            raise ValueError('지원하지 않는 모의 번호')
        value=state[kind]
        if action == 'diff' and kind=='pr':
            print('\n'.join('diff --git a/'+f['filename']+' b/'+f['filename']+'\n'+f['patch'] for f in state.get('files',[])));return
        if action == 'view':
            result=issue_view(value)
            if kind=='pr':
                result.update(state='MERGED' if value['merged'] else value['state'].upper(), mergedAt=value['merged_at'], baseRefName='be/develop')
            output(result);return
        if action == 'close' and kind=='issue':
            if flag('--reason') != 'completed': raise ValueError('완료 사유 필요')
            value.update(state='closed',state_reason='completed');save();output(issue_view(value));return
        if action=='edit':
            if '--remove-assignee' in args: raise ValueError('기존 담당자 제거 불가')
            login=flag('--add-assignee')
            if login not in ('fixture-author','@me'): raise ValueError('본인 담당자 추가만 지원')
            if not any(x['login']=='fixture-author' for x in value['assignees']): value['assignees'].append({'login':'fixture-author'})
            save();output(issue_view(value));return
    raise ValueError('지원하지 않는 모의 gh 명령: '+str(args))


if __name__=='__main__':
    try: main()
    except (OSError,ValueError,KeyError,IndexError) as exc:
        print(str(exc),file=sys.stderr);sys.exit(1)
