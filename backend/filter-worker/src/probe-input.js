import sharp from 'sharp';
import { ExtractionFailure } from './failure.js';

export const PROBE_WIDTH = 3072;
export const PROBE_HEIGHT = 4096;
export const DEFAULT_MAX_BYTES = 30 * 1024 * 1024;

// 크기 상한 초과 때 작가에게 보이는 문구(워커 job.js도 같은 문구를 쓴다)
export const TOO_LARGE_MESSAGE = '파일이 너무 커요. 전체 크기 JPG로 다시 내보내 주세요.';

const JPEG_SIGNATURE = [0xff, 0xd8, 0xff];

export function checkProbeBytes(buffer, { maxBytes = DEFAULT_MAX_BYTES } = {}) {
  if (buffer.length > maxBytes) {
    throw ExtractionFailure.invalidProbe(TOO_LARGE_MESSAGE);
  }
  if (buffer.length < JPEG_SIGNATURE.length || !JPEG_SIGNATURE.every((byte, i) => buffer[i] === byte)) {
    throw ExtractionFailure.invalidProbe('JPG 파일이 아니에요. Lightroom에서 JPG로 다시 내보내 주세요.');
  }
}

const SIZE_MESSAGE = '자르기·크기 조정 없이 내보낸 견본을 올려 주세요.';
const UNREADABLE_MESSAGE = '사진을 읽을 수 없어요. 전체 크기 JPG로 다시 내보내 주세요.';

// 픽셀 한도를 견본 크기로 걸어, 큰 이미지는 디코드를 시작하기 전에 sharp가 막는다(워커 메모리 1GB 보호)
const probeSharp = (buffer) => sharp(buffer, { failOn: 'error', limitInputPixels: PROBE_WIDTH * PROBE_HEIGHT });
// 크기 확인용: 헤더만 읽으므로 한도를 풀어 둔다(한도에 걸려도 "크기가 다르다"는 안내를 주기 위해)
const headerSharp = (buffer) => sharp(buffer, { failOn: 'error', limitInputPixels: false });

export async function decodeProbe(buffer) {
  // 메타데이터만 읽어 방향을 반영한 크기를 먼저 확인한다. EXIF 방향 5~8은 가로·세로가 바뀐다
  let metadata;
  try {
    metadata = await headerSharp(buffer).metadata();
  } catch {
    throw ExtractionFailure.invalidProbe(UNREADABLE_MESSAGE);
  }
  const swapped = metadata.orientation >= 5 && metadata.orientation <= 8;
  const width = swapped ? metadata.height : metadata.width;
  const height = swapped ? metadata.width : metadata.height;
  if (width !== PROBE_WIDTH || height !== PROBE_HEIGHT) {
    throw ExtractionFailure.invalidProbe(SIZE_MESSAGE);
  }

  let decoded;
  try {
    decoded = await probeSharp(buffer)
      .rotate()
      .toColorspace('srgb')
      .ensureAlpha()
      .raw()
      .toBuffer({ resolveWithObject: true });
  } catch {
    throw ExtractionFailure.invalidProbe(UNREADABLE_MESSAGE);
  }

  const { data, info } = decoded;
  if (info.width !== PROBE_WIDTH || info.height !== PROBE_HEIGHT) {
    throw ExtractionFailure.invalidProbe(SIZE_MESSAGE);
  }
  return {
    rgba: new Uint8ClampedArray(data.buffer, data.byteOffset, data.length),
    width: info.width,
    height: info.height,
  };
}

export async function readProbe(buffer, opts) {
  checkProbeBytes(buffer, opts);
  return decodeProbe(buffer);
}
