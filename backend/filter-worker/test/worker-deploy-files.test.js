// 배포 파일이 코드와 어긋나면 조용히 깨지므로 핵심 둘만 확인한다.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { CONFIG_VARIABLES } from '../src/worker/config.js';

const root = fileURLToPath(new URL('..', import.meta.url));
const read = (relative) => readFileSync(`${root}${relative}`, 'utf8');

test('환경 파일 예시의 변수가 설정이 읽는 변수와 같다', () => {
  const names = new Set();
  for (const line of read('deploy/filter-worker.env.example').split('\n')) {
    const match = /^#?\s*([A-Z][A-Z0-9_]+)=/.exec(line);
    if (match) names.add(match[1]);
  }
  assert.deepEqual([...names].sort(), [...CONFIG_VARIABLES].sort());
});

test('유닛의 실행 파일과 작업 폴더가 저장소에 있다', () => {
  const unit = read('deploy/chalkak-filter-worker.service');
  const exec = /^ExecStart=\S+ (\S+)$/m.exec(unit);
  assert.ok(exec, 'ExecStart를 찾지 못했습니다.');
  assert.ok(existsSync(`${root}${exec[1]}`), `${exec[1]}가 없습니다.`);
  const workingDirectory = /^WorkingDirectory=.*\/(backend\/filter-worker)$/m.exec(unit);
  assert.ok(workingDirectory, 'WorkingDirectory가 backend/filter-worker를 가리키지 않습니다.');
  assert.match(unit, /^RestartPreventExitStatus=78$/m);
});
