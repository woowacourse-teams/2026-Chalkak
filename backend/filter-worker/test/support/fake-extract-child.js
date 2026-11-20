// 추출 실행기 시험용 가짜 자식. extract-child.js와 같은 인자(<probe> <outDir>)를 받고,
// 동작은 probe 파일 내용(JSON {mode, ...})으로 정한다. 환경 변수는 PATH만 받으므로 환경으로는 정할 수 없다.
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const VALID_META = { extractorVersion: '1.0.0', probeVersion: 2, branch: 'A2', holdout: { M1: 1, A2: 0.5, G1: 2 }, warnings: [], xmpSummary: { masks: false } };

const [probePath, outDir] = process.argv.slice(2);
const spec = JSON.parse(readFileSync(probePath, 'utf8'));
// 시험이 자식 pid와 받은 환경 변수를 확인할 수 있게 남긴다
writeFileSync(join(outDir, 'child-info.json'), JSON.stringify({ pid: process.pid, envKeys: Object.keys(process.env) }));

switch (spec.mode) {
  case 'ok':
    writeFileSync(join(outDir, 'recipe.json'), spec.recipeText ?? '{"schemaVersion":1}'.padEnd(spec.recipePad ?? 0, ' '));
    writeFileSync(join(outDir, 'meta.json'), spec.metaText ?? JSON.stringify(VALID_META).padEnd(spec.metaPad ?? 0, ' '));
    process.exit(0);
    break;
  case 'exit':
    process.stderr.write(spec.stderr ?? '', () => process.exit(spec.code));
    break;
  case 'selfkill':
    process.kill(process.pid, 'SIGKILL');
    break;
  case 'sleep':
    setInterval(() => {}, 1000);
    break;
  default:
    process.exit(1);
}
