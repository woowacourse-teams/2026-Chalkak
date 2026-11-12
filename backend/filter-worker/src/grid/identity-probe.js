// 배포 자산: Lightroom에서 아무 편집 없이 내보낸 견본(3072×4096). 연구가 격자 맞춤 입력으로 쓴 것과 같은 파일이다.
// 한 번만 디코드해 재사용한다(RGBA 50MB). 호출한 쪽이 배열을 고치지 않는다.
// 자산이 깨진 것은 작가 잘못이 아니므로 어떤 실패든 내부 오류(PROCESSING_FAILED)로 돌려준다.
import { readFile } from 'node:fs/promises';
import { ExtractionFailure } from '../failure.js';
import { decodeProbe } from '../probe-input.js';

const ASSET = new URL('../../assets/identity-probe-v2.jpg', import.meta.url);

const read = (source) => readFile(source).then(decodeProbe).catch(() => {
  throw ExtractionFailure.processingFailed('내장 무편집 견본을 읽을 수 없어요.');
});

let cached = null;
// source를 주면(시험용) 캐시를 쓰지 않고 그 파일을 읽는다
export function loadIdentityProbe(source = null) {
  if (source) return read(source);
  cached ??= read(ASSET).catch((e) => { cached = null; throw e; });
  return cached;
}
