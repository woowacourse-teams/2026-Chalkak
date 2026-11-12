import { ExtractionFailure } from './failure.js';

const NO_XMP_MESSAGE = 'Lightroom 보정 기록(메타데이터)이 없어요. Lightroom에서 메타데이터를 포함해 JPG로 내보내 주세요.';

const NUMBER_PATTERN = /^[+-]?(\d+\.?\d*|\.\d+)$/;
// Lightroom은 "+15", "-0.50"처럼 부호가 붙은 숫자를 쓴다. 이름 뒤 공백·값 따옴표 모두 닫는 쪽을 만나면 멈춰서 입력 길이에 비례해서만 돈다
const ATTRIBUTE_PATTERN = /\bcrs:([A-Za-z0-9_]+)\s*=\s*(?:"([^"]*)"|'([^']*)')/g;
const MASK_CONTAINERS = ['MaskGroupBasedCorrections', 'GradientBasedCorrections', 'CircularGradientBasedCorrections', 'PaintBasedCorrections'];

const XMP_HEADER = Buffer.from('http://ns.adobe.com/xap/1.0/\0', 'latin1');
const XMP_EXTENSION_HEADER = Buffer.from('http://ns.adobe.com/xmp/extension/\0', 'latin1');
const EXTENSION_GUID_LENGTH = 32;
const MAX_EXTENDED_XMP_BYTES = 16 * 1024 * 1024;

const MASK_MESSAGE = '마스크와 부분 보정은 필터로 옮길 수 없어요. 마스크·부분 보정을 끄고 다시 내보내 주세요.';
const DEHAZE_CLARITY_WARNING = '디헤이즈·명료도는 사진에 따라 다르게 보일 수 있어요.';

function parseValue(raw) {
  const value = raw.trim();
  return NUMBER_PATTERN.test(value) ? Number(value) : value;
}

const isNameChar = (code) => (code >= 48 && code <= 57) || (code >= 65 && code <= 90) || (code >= 97 && code <= 122) || code === 95;

function startsWithBytes(buffer, offset, header) {
  return buffer.length >= offset + header.length && buffer.compare(header, 0, header.length, offset, offset + header.length) === 0;
}

// SOI부터 SOS 전까지 마커를 걸으며 XMP APP1만 모은다. 사진 데이터와 다른 세그먼트(COM·EXIF 등)의 글자는 보지 않는다
function extractXmpText(buffer) {
  if (buffer.length < 4 || buffer[0] !== 0xff || buffer[1] !== 0xd8) return '';
  const main = [];
  const extensionPayloads = [];
  let offset = 2;
  while (offset + 4 <= buffer.length) {
    if (buffer[offset] !== 0xff) break;
    const marker = buffer[offset + 1];
    if (marker === 0xff) { offset += 1; continue; }
    if (marker === 0x01 || (marker >= 0xd0 && marker <= 0xd8)) { offset += 2; continue; }
    if (marker === 0xd9 || marker === 0xda) break;
    const length = buffer.readUInt16BE(offset + 2);
    if (length < 2) break;
    const start = offset + 4;
    const end = Math.min(offset + 2 + length, buffer.length);
    if (marker === 0xe1) {
      if (startsWithBytes(buffer, start, XMP_HEADER)) {
        main.push(buffer.subarray(start + XMP_HEADER.length, end));
      } else if (startsWithBytes(buffer, start, XMP_EXTENSION_HEADER)) {
        extensionPayloads.push(buffer.subarray(start + XMP_EXTENSION_HEADER.length, end));
      }
    }
    offset += 2 + length;
  }
  const mainText = Buffer.concat(main).toString('latin1');
  const extendedGuid = findExtendedGuid(mainText);
  if (extendedGuid === null) return mainText;
  const extended = assembleExtension(extensionPayloads, extendedGuid);
  return extended === null ? mainText : mainText + extended.toString('latin1');
}

// 표준 패킷의 xmpNote:HasExtendedXMP(속성 또는 요소)가 가리키는 GUID. 없으면 확장 XMP는 모두 무시한다
function findExtendedGuid(mainText) {
  const name = 'HasExtendedXMP';
  let from = 0;
  for (;;) {
    const index = mainText.indexOf(name, from);
    if (index === -1) return null;
    from = index + name.length;
    const match = /^\s*(?:=\s*["']|>\s*)([0-9A-Fa-f]{32})/.exec(mainText.slice(from, from + 80));
    if (match) return match[1].toUpperCase();
  }
}

// 확장 XMP: GUID(32) + 전체 길이(4) + 조각 위치(4) + 데이터. 가리킨 GUID의 조각만 위치대로 이어 붙인다.
// 할당은 상한 안의 길이로 한 번만 하고, 가리키지 않은 GUID의 선언 길이는 쓰지 않는다
function assembleExtension(payloads, wantedGuid) {
  let assembled = null;
  for (const payload of payloads) {
    if (payload.length < EXTENSION_GUID_LENGTH + 8) continue;
    if (payload.toString('latin1', 0, EXTENSION_GUID_LENGTH).toUpperCase() !== wantedGuid) continue;
    const fullLength = payload.readUInt32BE(EXTENSION_GUID_LENGTH);
    const chunkOffset = payload.readUInt32BE(EXTENSION_GUID_LENGTH + 4);
    const data = payload.subarray(EXTENSION_GUID_LENGTH + 8);
    if (fullLength > MAX_EXTENDED_XMP_BYTES || chunkOffset + data.length > fullLength) continue;
    if (assembled === null) assembled = Buffer.alloc(fullLength);
    if (fullLength !== assembled.length) continue;
    data.copy(assembled, chunkOffset);
  }
  return assembled;
}

// 프리셋 프로필(Look) 블록 안의 crs 값은 작가가 조정한 슬라이더가 아니라서 뺀다. 닫는 태그가 없으면 뒤에도 없으니 거기서 멈춘다
function stripLook(text) {
  const open = '<crs:Look';
  const close = '</crs:Look>';
  const pieces = [];
  let copied = 0;
  let from = 0;
  for (;;) {
    const index = text.indexOf(open, from);
    if (index === -1) break;
    from = index + open.length;
    if (isNameChar(text.charCodeAt(from))) continue;
    const closeIndex = text.indexOf(close, from);
    if (closeIndex === -1) break;
    pieces.push(text.slice(copied, index));
    copied = closeIndex + close.length;
    from = copied;
  }
  pieces.push(text.slice(copied));
  return pieces.join('');
}

// 속성 없는 <crs:이름>값</crs:이름>만 스칼라로 읽는다. 곡선·목록은 값 안에 자식 요소가 있어 건너뛴다
function collectScalarElements(text, values) {
  const open = '<crs:';
  let from = 0;
  for (;;) {
    const index = text.indexOf(open, from);
    if (index === -1) return;
    const nameStart = index + open.length;
    let nameEnd = nameStart;
    while (nameEnd < text.length && isNameChar(text.charCodeAt(nameEnd))) nameEnd += 1;
    from = nameEnd;
    if (nameEnd === nameStart || text.charCodeAt(nameEnd) !== 62) continue;
    const valueEnd = text.indexOf('<', nameEnd + 1);
    if (valueEnd === -1) return;
    const name = text.slice(nameStart, nameEnd);
    if (text.startsWith(`</crs:${name}>`, valueEnd)) {
      values[name] = parseValue(text.slice(nameEnd + 1, valueEnd));
    }
  }
}

// 컨테이너가 있고 안에 <rdf:li> 항목이 하나라도 있으면 실제 마스크·부분 보정이 있는 것이다
function hasContainerContent(text, name) {
  const open = `<crs:${name}`;
  const close = `</crs:${name}>`;
  let from = 0;
  for (;;) {
    const index = text.indexOf(open, from);
    if (index === -1) return false;
    const afterName = index + open.length;
    if (isNameChar(text.charCodeAt(afterName))) { from = afterName; continue; }
    const tagEnd = text.indexOf('>', afterName);
    if (tagEnd === -1) return false;
    if (text.charCodeAt(tagEnd - 1) === 47) { from = tagEnd; continue; }
    const closeIndex = text.indexOf(close, tagEnd);
    if (closeIndex === -1) return false;
    if (text.slice(tagEnd, closeIndex).includes('<rdf:li')) return true;
    from = closeIndex + close.length;
  }
}

/**
 * JPEG의 XMP 세그먼트에서 Lightroom 보정 값(crs:)을 읽는다. 속성 형식과 요소 형식의 스칼라를 모두 읽고,
 * 마스크·부분 보정 컨테이너에 내용이 있는지도 함께 알려 준다.
 * @returns {{ values: Record<string, number | string>, hasMasks: boolean }}
 */
export function readXmp(buffer) {
  let text;
  try {
    text = stripLook(extractXmpText(buffer));
  } catch (error) {
    if (error instanceof RangeError) throw ExtractionFailure.invalidProbe(NO_XMP_MESSAGE);
    throw error;
  }
  const values = {};
  for (const match of text.matchAll(ATTRIBUTE_PATTERN)) {
    values[match[1]] = parseValue(match[2] ?? match[3]);
  }
  collectScalarElements(text, values);
  const hasMasks = MASK_CONTAINERS.some((name) => hasContainerContent(text, name));
  if (Object.keys(values).length === 0 && !hasMasks) {
    throw ExtractionFailure.invalidProbe(NO_XMP_MESSAGE);
  }
  return { values, hasMasks };
}

function number(values, key) {
  return typeof values[key] === 'number' ? values[key] : 0;
}

/**
 * 어떤 공간 연산자(비네팅 V, 지역 톤 C, 디테일 K, 그레인)를 추출할지 정하고 작가에게 보일 경고를 모은다.
 * summary는 판정에 쓴 값의 요약으로, 운영 분석용으로 저장한다.
 */
export function planFromXmp({ values, hasMasks }) {
  if (hasMasks) throw ExtractionFailure.invalidProbe(MASK_MESSAGE);
  const vignette = number(values, 'PostCropVignetteAmount');
  const highlights = number(values, 'Highlights2012');
  const clarity = number(values, 'Clarity2012');
  const texture = number(values, 'Texture');
  const sharpness = number(values, 'Sharpness');
  const luminanceSmoothing = number(values, 'LuminanceSmoothing');
  const grain = number(values, 'GrainAmount');
  const dehaze = number(values, 'Dehaze');

  const on = Object.freeze({
    V: vignette !== 0,
    C: highlights !== 0 || clarity < 0,
    K: texture !== 0 || sharpness !== 0 || luminanceSmoothing !== 0,
    grain: grain > 0,
  });

  const warnings = [];
  if (dehaze !== 0 || clarity !== 0) warnings.push(DEHAZE_CLARITY_WARNING);

  const summary = {
    PostCropVignetteAmount: vignette,
    Highlights2012: highlights,
    Clarity2012: clarity,
    Texture: texture,
    Sharpness: sharpness,
    LuminanceSmoothing: luminanceSmoothing,
    GrainAmount: grain,
    Dehaze: dehaze,
    masks: hasMasks,
  };

  return Object.freeze({ on, warnings: Object.freeze(warnings), summary: Object.freeze(summary) });
}
