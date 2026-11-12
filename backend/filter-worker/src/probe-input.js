import sharp from 'sharp';
import { ExtractionFailure } from './failure.js';

export const PROBE_WIDTH = 3072;
export const PROBE_HEIGHT = 4096;
export const DEFAULT_MAX_BYTES = 30 * 1024 * 1024;

const JPEG_SIGNATURE = [0xff, 0xd8, 0xff];

export function checkProbeBytes(buffer, { maxBytes = DEFAULT_MAX_BYTES } = {}) {
  if (buffer.length > maxBytes) {
    throw ExtractionFailure.invalidProbe('파일이 너무 커요. 전체 크기 JPG로 다시 내보내 주세요.');
  }
  if (buffer.length < JPEG_SIGNATURE.length || !JPEG_SIGNATURE.every((byte, i) => buffer[i] === byte)) {
    throw ExtractionFailure.invalidProbe('JPG 파일이 아니에요. Lightroom에서 JPG로 다시 내보내 주세요.');
  }
}

export async function decodeProbe(buffer) {
  let decoded;
  try {
    // EXIF 방향 적용 → sRGB 변환 → 8bit RGBA 원시 픽셀
    // sharp는 기본적으로 내장 ICC 프로필을 sRGB로 변환한다. keepIccProfile()은 절대 호출하지 않는다.
    decoded = await sharp(buffer, { failOn: 'error' })
      .rotate()
      .toColorspace('srgb')
      .ensureAlpha()
      .raw()
      .toBuffer({ resolveWithObject: true });
  } catch {
    throw ExtractionFailure.invalidProbe('사진을 읽을 수 없어요. 전체 크기 JPG로 다시 내보내 주세요.');
  }

  const { data, info } = decoded;
  if (info.width !== PROBE_WIDTH || info.height !== PROBE_HEIGHT) {
    throw ExtractionFailure.invalidProbe('자르기·크기 조정 없이 내보낸 견본을 올려 주세요.');
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
