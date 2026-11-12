#!/usr/bin/env node
// 사용: node src/cli.js <probe.jpg> [--out recipe.json] [--meta meta.json]
// 실패하면 stderr에 {"type","message"} JSON을 쓰고 INVALID_PROBE는 종료 코드 2, 그 밖은 1로 끝낸다.
import { readFile, writeFile } from 'node:fs/promises';
import { parseArgs } from 'node:util';
import { ExtractionFailure, INVALID_PROBE, failureLines } from './failure.js';
import { extractRecipe } from './extract-recipe.js';

const fail = (failure) => {
  process.stderr.write(`${failureLines(failure).join('\n')}\n`);
  process.exit(failure.type === INVALID_PROBE ? 2 : 1);
};

let args;
try {
  args = parseArgs({ allowPositionals: true, options: { out: { type: 'string' }, meta: { type: 'string' } } });
  if (args.positionals.length !== 1) throw new Error('견본 파일 하나가 필요해요.');
} catch (error) {
  fail(ExtractionFailure.processingFailed(`사용법: extract <probe.jpg> [--out recipe.json] [--meta meta.json] (${error.message})`));
}

try {
  const buffer = await readFile(args.positionals[0]).catch(() => {
    throw ExtractionFailure.invalidProbe('견본 파일을 읽을 수 없어요.');
  });
  const { recipe, meta } = await extractRecipe(buffer);
  const write = (file, value) => (file ? writeFile(file, JSON.stringify(value)) : null);
  await write(args.values.out, recipe);
  await write(args.values.meta, meta);
  if (!args.values.out) process.stdout.write(`${JSON.stringify(recipe)}\n`);
} catch (error) {
  if (error instanceof ExtractionFailure) fail(error);
  const failure = ExtractionFailure.processingFailed(error.message);
  failure.cause = error;
  fail(failure);
}
