#!/usr/bin/env python3
"""격리 평가 전용 gh 대역. 네트워크·인증·실제 GitHub에 접근하지 않는다."""
import json
from pathlib import Path
import sys


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
        output([{'name':'Server'},{'name':'docs'}]);return
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
