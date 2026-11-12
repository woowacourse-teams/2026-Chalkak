// 추출 자식 프로세스 진입점: node extract-child.js <probe.jpg> <outDir>
//   성공: <outDir>/recipe.json, <outDir>/meta.json을 쓰고 종료 0
//   INVALID_PROBE: stderr 첫 줄에 {"type","message"} JSON, 종료 2
//   그 밖(파일 읽기 실패 포함): stderr에 failureLines(JSON + cause:), 종료 1
// cli.js와 달리 파일을 못 읽는 것은 부모가 쓴 파일의 문제이므로 작가 탓(INVALID_PROBE)이 아니라 내부 오류다.
import { readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { ExtractionFailure, INVALID_PROBE, PROCESSING_FAILED_MESSAGE, failureLines } from '../failure.js';
import { extractRecipe } from '../extract-recipe.js';

function fail(failure) {
  process.stderr.write(`${failureLines(failure).join('\n')}\n`, () => process.exit(failure.type === INVALID_PROBE ? 2 : 1));
}

function internalFailure(cause) {
  const failure = ExtractionFailure.processingFailed(PROCESSING_FAILED_MESSAGE);
  failure.cause = cause;
  return failure;
}

const [probePath, outDir, ...extra] = process.argv.slice(2);
if (!probePath || !outDir || extra.length > 0) {
  fail(internalFailure(new Error('사용법: extract-child <probe.jpg> <outDir>')));
} else {
  try {
    const buffer = await readFile(probePath);
    const { recipe, meta } = await extractRecipe(buffer);
    await writeFile(join(outDir, 'recipe.json'), JSON.stringify(recipe));
    await writeFile(join(outDir, 'meta.json'), JSON.stringify(meta));
  } catch (error) {
    fail(error instanceof ExtractionFailure ? error : internalFailure(error));
  }
}
