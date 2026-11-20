// 추출을 작업마다 새 자식 프로세스에서 실행하고 결과를 판정한다(README 설계 3-3절).
// 부모 이벤트 루프가 몇 분씩 막히면 숨김 연장과 종료 신호 처리가 멈추고, 자식이 끝나면 메모리가 OS로 돌아간다.
import { spawn } from 'node:child_process';
import { readFile, stat } from 'node:fs/promises';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { INVALID_PROBE } from '../failure.js';

export const DEFAULT_CHILD_PATH = fileURLToPath(new URL('./extract-child.js', import.meta.url));
export const DEFAULT_TIMEOUT_MS = 480_000;
const STDERR_COLLECT_BYTES = 64 * 1024;
const CAUSE_MAX_BYTES = 4 * 1024;
const MESSAGE_MAX_LENGTH = 200;

const MAX_RECIPE_BYTES = 8 * 1024 * 1024;
const MAX_META_BYTES = 1024 * 1024;
const BRANCHES = ['M1', 'A2', 'G1'];

const isPlainObject = (value) => value !== null && typeof value === 'object' && !Array.isArray(value);

// complete 본문과 로그가 기대는 모양. 어긋나면 던진다
function assertMetaShape(meta) {
  const valid =
    isPlainObject(meta) &&
    typeof meta.extractorVersion === 'string' && meta.extractorVersion !== '' &&
    Number.isInteger(meta.probeVersion) &&
    BRANCHES.includes(meta.branch) &&
    Array.isArray(meta.warnings) && meta.warnings.every((w) => typeof w === 'string') &&
    isPlainObject(meta.xmpSummary) &&
    isPlainObject(meta.holdout);
  if (!valid) throw new Error('meta.json의 모양이 올바르지 않습니다.');
}

async function readCapped(path, maxBytes) {
  const { size } = await stat(path);
  if (size > maxBytes) throw new Error(`${path.split('/').pop()}가 상한(${maxBytes}바이트)을 넘습니다.`);
  return readFile(path);
}

// 자식이 쓴 두 파일을 읽는다. 파일이 없거나 너무 크거나 JSON·모양이 이상하면 throw한다
async function readResultFiles(outDir) {
  const recipeBytes = await readCapped(join(outDir, 'recipe.json'), MAX_RECIPE_BYTES);
  JSON.parse(recipeBytes.toString('utf8'));
  const meta = JSON.parse((await readCapped(join(outDir, 'meta.json'), MAX_META_BYTES)).toString('utf8'));
  assertMetaShape(meta);
  return { recipeBytes, meta };
}

// stderr 첫 줄이 {type: INVALID_PROBE, message: 1~200자 문자열}이면 message, 아니면 null
function parseInvalidMessage(stderr) {
  try {
    const parsed = JSON.parse(stderr.split('\n', 1)[0]);
    const ok = parsed?.type === INVALID_PROBE && typeof parsed.message === 'string' && parsed.message.length >= 1 && parsed.message.length <= MESSAGE_MAX_LENGTH;
    return ok ? parsed.message : null;
  } catch {
    return null;
  }
}

/**
 * @param {{ probePath: string, outDir: string, timeoutMs?: number, signal?: AbortSignal, childPath?: string }} options
 * @returns {Promise<
 *   { kind: 'ok', recipeBytes: Buffer, meta: object, durationMs: number } |
 *   { kind: 'invalid', message: string, durationMs: number } |
 *   { kind: 'error' | 'crash', exitCode: number | null, signal: string | null, cause: string, durationMs: number } |
 *   { kind: 'timeout' | 'aborted', durationMs: number }>}
 *   항상 자식이 끝난 뒤에 돌려준다. 자식에게 시크릿을 넘기지 않는다(환경 변수는 PATH만).
 */
export function runExtraction({ probePath, outDir, timeoutMs = DEFAULT_TIMEOUT_MS, signal, childPath = DEFAULT_CHILD_PATH }) {
  const startedAt = Date.now();
  const elapsed = () => Date.now() - startedAt;
  if (signal?.aborted) return Promise.resolve({ kind: 'aborted', durationMs: 0 });

  return new Promise((resolve) => {
    const child = spawn(process.execPath, [childPath, probePath, outDir], {
      env: { PATH: process.env.PATH ?? '' },
      stdio: ['ignore', 'ignore', 'pipe'],
    });
    const chunks = [];
    let collected = 0;
    let forced = null; // 부모가 죽인 이유: 'timeout' | 'aborted'
    let settled = false;

    const kill = (reason) => {
      if (forced === null) forced = reason;
      child.kill('SIGKILL');
    };
    const timer = setTimeout(() => kill('timeout'), timeoutMs);
    const onAbort = () => kill('aborted');
    signal?.addEventListener('abort', onAbort, { once: true });

    const finish = async (exitCode, exitSignal, spawnError) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      signal?.removeEventListener('abort', onAbort);
      const stderr = Buffer.concat(chunks);
      const cause = spawnError ? String(spawnError.stack ?? spawnError) : stderr.subarray(0, CAUSE_MAX_BYTES).toString('utf8');
      const failure = (kind) => ({ kind, exitCode, signal: exitSignal, cause, durationMs: elapsed() });

      if (forced !== null) return resolve({ kind: forced, durationMs: elapsed() });
      if (spawnError) return resolve(failure('error'));
      if (exitCode === null) return resolve(failure('crash'));
      if (exitCode === 0) {
        try {
          return resolve({ kind: 'ok', ...(await readResultFiles(outDir)), durationMs: elapsed() });
        } catch (error) {
          return resolve({ ...failure('error'), cause: `결과 파일을 읽을 수 없습니다: ${error.message}` });
        }
      }
      if (exitCode === 2) {
        const message = parseInvalidMessage(stderr.toString('utf8'));
        if (message !== null) return resolve({ kind: 'invalid', message, durationMs: elapsed() });
      }
      return resolve(failure('error'));
    };

    child.stderr.on('data', (chunk) => {
      if (collected >= STDERR_COLLECT_BYTES) return;
      const part = chunk.subarray(0, STDERR_COLLECT_BYTES - collected);
      chunks.push(part);
      collected += part.length;
    });
    // 실행 파일을 띄우지 못한 경우 close가 오지 않을 수 있다
    child.on('error', (error) => {
      if (child.pid === undefined) finish(null, null, error);
    });
    child.on('close', (exitCode, exitSignal) => finish(exitCode, exitSignal, null));
  });
}
