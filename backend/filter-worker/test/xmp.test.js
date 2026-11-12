import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { performance } from 'node:perf_hooks';
import sharp from 'sharp';
import { INVALID_PROBE } from '../src/failure.js';
import { planFromXmp, readXmp } from '../src/xmp.js';

const NO_XMP_MESSAGE = 'Lightroom 보정 기록(메타데이터)이 없어요. Lightroom에서 메타데이터를 포함해 JPG로 내보내 주세요.';

const XMP_HEADER = Buffer.from('http://ns.adobe.com/xap/1.0/\0', 'latin1');
const EXTENSION_HEADER = Buffer.from('http://ns.adobe.com/xmp/extension/\0', 'latin1');
const SEGMENT_PAYLOAD_LIMIT = 60000;

function segment(marker, payload) {
  const header = Buffer.from([0xff, marker, 0, 0]);
  header.writeUInt16BE(payload.length + 2, 2);
  return Buffer.concat([header, payload]);
}

// SOI + XMP APP1 세그먼트(65KB 제한에 맞춰 나눈다) + SOS + 사진 데이터 자리 + EOI
function jpegWithPacket(packet, { before = [], after = Buffer.alloc(0) } = {}) {
  const bytes = Buffer.from(packet, 'latin1');
  const segments = [];
  for (let offset = 0; offset < bytes.length; offset += SEGMENT_PAYLOAD_LIMIT) {
    segments.push(segment(0xe1, Buffer.concat([XMP_HEADER, bytes.subarray(offset, offset + SEGMENT_PAYLOAD_LIMIT)])));
  }
  return Buffer.concat([Buffer.from([0xff, 0xd8]), ...before, ...segments, segment(0xda, Buffer.alloc(4)), after, Buffer.from([0xff, 0xd9])]);
}

const wrapPacket = (body) => `<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">${body}</rdf:RDF></x:xmpmeta>`;
const jpegWithXmp = (body) => jpegWithPacket(wrapPacket(body));

function attrs(values) {
  const list = Object.entries(values).map(([k, v]) => `crs:${k}="${v}"`).join(' ');
  return jpegWithXmp(`<rdf:Description xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/" ${list}/>`);
}

const planOf = (values) => planFromXmp(readXmp(attrs(values)));

function assertInvalidProbe(fn) {
  assert.throws(fn, (error) => {
    assert.equal(error.type, INVALID_PROBE);
    assert.equal(error.message, NO_XMP_MESSAGE);
    return true;
  });
}

describe('readXmp', () => {
  test('XMP가 없으면 INVALID_PROBE', () => {
    assertInvalidProbe(() => readXmp(Buffer.from([0xff, 0xd8, 0xff, 0xd9])));
  });

  test('crs 값이 하나도 없는 XMP도 INVALID_PROBE', () => {
    assertInvalidProbe(() => readXmp(jpegWithXmp('<rdf:Description xmlns:dc="http://purl.org/dc/elements/1.1/" dc:title="a"/>')));
  });

  test('속성 형식을 읽는다', () => {
    const xmp = readXmp(attrs({ Exposure2012: '0.50', ToneCurveName2012: 'Linear' }));
    assert.equal(xmp.values.Exposure2012, 0.5);
    assert.equal(xmp.values.ToneCurveName2012, 'Linear');
  });

  test('요소 형식의 스칼라 값을 읽는다', () => {
    const xmp = readXmp(jpegWithXmp('<rdf:Description xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/"><crs:Highlights2012>-30</crs:Highlights2012><crs:CameraProfile> Adobe Standard </crs:CameraProfile></rdf:Description>'));
    assert.equal(xmp.values.Highlights2012, -30);
    assert.equal(xmp.values.CameraProfile, 'Adobe Standard');
  });

  test('부호가 붙은 숫자를 숫자로 읽는다', () => {
    const xmp = readXmp(attrs({ Texture: '+15', Exposure2012: '-0.50', Sharpness: '40', Version: '15.0', Name: '+abc' }));
    assert.equal(xmp.values.Texture, 15);
    assert.equal(xmp.values.Exposure2012, -0.5);
    assert.equal(xmp.values.Sharpness, 40);
    assert.equal(xmp.values.Version, 15);
    assert.equal(xmp.values.Name, '+abc');
  });

  test('곡선 같은 중첩 요소는 스칼라로 읽지 않는다', () => {
    const xmp = readXmp(jpegWithXmp('<rdf:Description xmlns:crs="x" crs:Dehaze="0"><crs:ToneCurvePV2012><rdf:Seq><rdf:li>0, 0</rdf:li></rdf:Seq></crs:ToneCurvePV2012></rdf:Description>'));
    assert.equal('ToneCurvePV2012' in xmp.values, false);
    assert.equal(xmp.hasMasks, false);
  });

  test('Look 블록 안의 crs 값은 읽지 않는다', () => {
    const xmp = readXmp(jpegWithXmp('<rdf:Description xmlns:crs="x" crs:Texture="5"><crs:Look><rdf:Description crs:Texture="99" crs:Name="Look"><crs:Parameters crs:Dehaze="50"/></rdf:Description></crs:Look></rdf:Description>'));
    assert.equal(xmp.values.Texture, 5);
    assert.equal('Dehaze' in xmp.values, false);
  });

  test('마스크 컨테이너에 내용이 있으면 hasMasks', () => {
    for (const name of ['MaskGroupBasedCorrections', 'GradientBasedCorrections', 'CircularGradientBasedCorrections', 'PaintBasedCorrections']) {
      const buffer = jpegWithXmp(`<rdf:Description xmlns:crs="x" crs:Exposure2012="0"><crs:${name}><rdf:Seq><rdf:li crs:What="Correction"/></rdf:Seq></crs:${name}></rdf:Description>`);
      assert.equal(readXmp(buffer).hasMasks, true, name);
    }
  });

  test('비어 있는 마스크 컨테이너는 무시한다', () => {
    const buffer = jpegWithXmp('<rdf:Description xmlns:crs="x" crs:Exposure2012="0"><crs:MaskGroupBasedCorrections><rdf:Seq/></crs:MaskGroupBasedCorrections><crs:PaintBasedCorrections/></rdf:Description>');
    assert.equal(readXmp(buffer).hasMasks, false);
  });

  test('sharp가 쓴 XMP도 읽는다', async () => {
    const jpeg = await sharp({ create: { width: 8, height: 8, channels: 3, background: '#888' } })
      .withXmp('<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"><rdf:Description xmlns:crs="x" crs:GrainAmount="+25"/></rdf:RDF></x:xmpmeta>')
      .jpeg().toBuffer();
    assert.equal(readXmp(jpeg).values.GrainAmount, 25);
  });
});

describe('readXmp JPEG 세그먼트', () => {
  const CRS_TEXT = '<rdf:Description xmlns:crs="x" crs:Dehaze="30"/>';

  test('XMP 없이 EXIF APP1만 있는 JPEG는 INVALID_PROBE', () => {
    const exif = segment(0xe1, Buffer.concat([Buffer.from('Exif\0\0', 'latin1'), Buffer.from(CRS_TEXT)]));
    const jpeg = Buffer.concat([Buffer.from([0xff, 0xd8]), exif, segment(0xda, Buffer.alloc(4)), Buffer.from([0xff, 0xd9])]);
    assertInvalidProbe(() => readXmp(jpeg));
  });

  test('JPEG가 아닌 바이트는 INVALID_PROBE', () => {
    assertInvalidProbe(() => readXmp(Buffer.from(wrapPacket(CRS_TEXT))));
  });

  test('COM 세그먼트의 crs 글자는 무시한다', () => {
    const jpeg = jpegWithPacket(wrapPacket('<rdf:Description xmlns:crs="x" crs:Texture="5"/>'), { before: [segment(0xfe, Buffer.from('crs:Dehaze="99"'))] });
    assert.deepEqual(readXmp(jpeg).values, { Texture: 5 });
  });

  test('SOS 뒤의 crs 글자는 무시한다', () => {
    const jpeg = jpegWithPacket(wrapPacket('<rdf:Description xmlns:crs="x" crs:Texture="5"/>'), { after: Buffer.from('crs:Dehaze="99"') });
    assert.deepEqual(readXmp(jpeg).values, { Texture: 5 });
  });

  const GUID = 'A1B2C3D4'.repeat(4);

  function extensionChunk(guid, fullLength, offset, data) {
    const meta = Buffer.alloc(8);
    meta.writeUInt32BE(fullLength, 0);
    meta.writeUInt32BE(offset, 4);
    return segment(0xe1, Buffer.concat([EXTENSION_HEADER, Buffer.from(guid, 'latin1'), meta, data]));
  }

  function jpegWithExtension(mainBody, chunks) {
    const main = segment(0xe1, Buffer.concat([XMP_HEADER, Buffer.from(wrapPacket(mainBody))]));
    return Buffer.concat([Buffer.from([0xff, 0xd8]), main, ...chunks, segment(0xda, Buffer.alloc(4)), Buffer.from([0xff, 0xd9])]);
  }

  const EXTENDED = Buffer.from('<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:Description xmlns:crs="x" crs:Dehaze="30" crs:Clarity2012="-5"/></x:xmpmeta>', 'latin1');
  const HALF = Math.floor(EXTENDED.length / 2);
  const mainWith = (guid) => `<rdf:Description xmlns:crs="x" xmlns:xmpNote="n" xmpNote:HasExtendedXMP="${guid}" crs:Texture="5"/>`;

  test('HasExtendedXMP가 가리키는 확장 XMP 조각을 위치대로 이어 붙여 읽는다', () => {
    const jpeg = jpegWithExtension(mainWith(GUID), [
      extensionChunk(GUID, EXTENDED.length, HALF, EXTENDED.subarray(HALF)),
      extensionChunk(GUID, EXTENDED.length, 0, EXTENDED.subarray(0, HALF)),
    ]);
    assert.deepEqual(readXmp(jpeg).values, { Texture: 5, Dehaze: 30, Clarity2012: -5 });
  });

  test('요소 형식의 HasExtendedXMP도 따른다', () => {
    const body = `<rdf:Description xmlns:crs="x" xmlns:xmpNote="n" crs:Texture="5"><xmpNote:HasExtendedXMP>${GUID.toLowerCase()}</xmpNote:HasExtendedXMP></rdf:Description>`;
    const jpeg = jpegWithExtension(body, [extensionChunk(GUID, EXTENDED.length, 0, EXTENDED)]);
    assert.equal(readXmp(jpeg).values.Dehaze, 30);
  });

  test('가리키지 않은 GUID의 확장 XMP는 무시한다', () => {
    const other = 'F'.repeat(32);
    const jpeg = jpegWithExtension(mainWith(GUID), [extensionChunk(other, EXTENDED.length, 0, EXTENDED)]);
    assert.deepEqual(readXmp(jpeg).values, { Texture: 5 });
  });

  test('HasExtendedXMP가 없으면 확장 XMP는 모두 무시한다', () => {
    const jpeg = jpegWithExtension('<rdf:Description xmlns:crs="x" crs:Texture="5"/>', [extensionChunk(GUID, EXTENDED.length, 0, EXTENDED)]);
    assert.deepEqual(readXmp(jpeg).values, { Texture: 5 });
  });

  test('큰 길이를 선언한 서로 다른 GUID의 확장 조각이 많아도 빠르고 메모리를 쓰지 않는다', () => {
    const chunks = [];
    for (let i = 0; i < 2000; i += 1) {
      chunks.push(extensionChunk(i.toString(16).padStart(32, '0'), 16 * 1024 * 1024, 0, Buffer.from('x')));
    }
    const jpeg = jpegWithExtension(mainWith(GUID), chunks);
    const rssBefore = process.memoryUsage().rss;
    const started = performance.now();
    const xmp = readXmp(jpeg);
    assert.ok(performance.now() - started < 200);
    assert.ok(process.memoryUsage().rss - rssBefore < 100 * 1024 * 1024);
    assert.deepEqual(xmp.values, { Texture: 5 });
  });

  test('선언 길이가 상한을 넘는 참조 GUID의 확장 조각은 무시한다', () => {
    const jpeg = jpegWithExtension(mainWith(GUID), [extensionChunk(GUID, 0xffffffff, 0, Buffer.from('x'))]);
    assert.deepEqual(readXmp(jpeg).values, { Texture: 5 });
  });

  test('요소 속성 값에 >가 있어도 값을 잘못 읽지 않는다', () => {
    const xmp = readXmp(jpegWithXmp('<rdf:Description xmlns:crs="x" crs:Dehaze="1"><crs:Sharpness a="b>c">40</crs:Sharpness></rdf:Description>'));
    assert.equal(xmp.values.Sharpness === undefined || xmp.values.Sharpness === 40, true);
    assert.deepEqual(Object.values(xmp.values).filter((v) => typeof v === 'string'), []);
  });
});

describe('readXmp 악의적인 입력', () => {
  const MEGABYTE = 1024 * 1024;
  const LIMIT_MS = 200;

  for (const [name, unit] of [
    ['속성이 열린 채 반복되는 요소', '<crs:Foo a '],
    ['닫히지 않고 반복되는 마스크 컨테이너', '<crs:MaskGroupBasedCorrections>'],
    ['닫히지 않고 반복되는 Look', '<crs:Look>'],
    ['따옴표가 열린 채 반복되는 속성', 'crs:A="'],
    ['이름만 반복되는 요소', '<crs:A'],
  ]) {
    test(`${name}는 200ms 안에 끝난다`, () => {
      const jpeg = jpegWithXmp(`<rdf:Description xmlns:crs="x" crs:Texture="5"/>${unit.repeat(Math.ceil(MEGABYTE / unit.length))}`);
      const started = performance.now();
      try {
        readXmp(jpeg);
      } finally {
        assert.ok(performance.now() - started < LIMIT_MS, `${name}: ${performance.now() - started}ms`);
      }
    });
  }
});

describe('planFromXmp 연산자 스위치', () => {
  test('보정이 없으면 모두 꺼진다', () => {
    assert.deepEqual(planOf({ Exposure2012: '0' }).on, { V: false, C: false, K: false, grain: false });
  });

  test('V: 비네팅 양이 0이 아니면 켜진다', () => {
    assert.equal(planOf({ PostCropVignetteAmount: '-25' }).on.V, true);
    assert.equal(planOf({ PostCropVignetteAmount: '+10' }).on.V, true);
    assert.equal(planOf({ PostCropVignetteAmount: '0' }).on.V, false);
  });

  test('C: 밝은 영역이 0이 아니거나 명료도가 음수면 켜진다', () => {
    assert.equal(planOf({ Highlights2012: '-30' }).on.C, true);
    assert.equal(planOf({ Highlights2012: '+30' }).on.C, true);
    assert.equal(planOf({ Clarity2012: '-20' }).on.C, true);
    assert.equal(planOf({ Clarity2012: '+20' }).on.C, false);
    assert.equal(planOf({ Highlights2012: '0', Clarity2012: '0' }).on.C, false);
  });

  test('K: 텍스처·선명하게·휘도 노이즈 감소 중 하나라도 0이 아니면 켜진다', () => {
    assert.equal(planOf({ Texture: '-10' }).on.K, true);
    assert.equal(planOf({ Sharpness: '40' }).on.K, true);
    assert.equal(planOf({ LuminanceSmoothing: '25' }).on.K, true);
    assert.equal(planOf({ Texture: '0', Sharpness: '0', LuminanceSmoothing: '0' }).on.K, false);
  });

  test('grain: 그레인 양이 0보다 커야 켜진다', () => {
    assert.equal(planOf({ GrainAmount: '50' }).on.grain, true);
    assert.equal(planOf({ GrainAmount: '0' }).on.grain, false);
    assert.equal(planOf({ GrainAmount: '-5' }).on.grain, false);
  });

  test('숫자가 아닌 값은 0으로 본다', () => {
    assert.deepEqual(planOf({ Highlights2012: 'abc' }).on, { V: false, C: false, K: false, grain: false });
  });
});

describe('planFromXmp 경고와 요약', () => {
  test('경고가 없으면 빈 배열', () => {
    assert.deepEqual(planOf({ Highlights2012: '-30', Texture: '10' }).warnings, []);
  });

  test('마스크가 있으면 디코드 전에 INVALID_PROBE', () => {
    const buffer = jpegWithXmp('<rdf:Description xmlns:crs="x" crs:Exposure2012="0"><crs:MaskGroupBasedCorrections><rdf:Seq><rdf:li crs:What="Correction"/></rdf:Seq></crs:MaskGroupBasedCorrections></rdf:Description>');
    assert.throws(() => planFromXmp(readXmp(buffer)), {
      type: 'INVALID_PROBE',
      message: '마스크와 부분 보정은 필터로 옮길 수 없어요. 마스크·부분 보정을 끄고 다시 내보내 주세요.',
    });
  });

  test('디헤이즈가 0이 아니면 경고', () => {
    assert.deepEqual(planOf({ Dehaze: '-10' }).warnings, ['디헤이즈·명료도는 사진에 따라 다르게 보일 수 있어요.']);
  });

  test('명료도가 0이 아니면 경고', () => {
    assert.deepEqual(planOf({ Clarity2012: '+15' }).warnings, ['디헤이즈·명료도는 사진에 따라 다르게 보일 수 있어요.']);
    assert.deepEqual(planOf({ Clarity2012: '-15' }).warnings, ['디헤이즈·명료도는 사진에 따라 다르게 보일 수 있어요.']);
  });

  test('디헤이즈와 명료도가 함께 있어도 경고는 한 번만', () => {
    assert.equal(planOf({ Dehaze: '10', Clarity2012: '10' }).warnings.length, 1);
  });

  test('요약에는 판정에 쓴 값과 마스크 여부가 들어간다', () => {
    const { summary } = planOf({
      PostCropVignetteAmount: '-25', Highlights2012: '-30', Clarity2012: '+5', Texture: '10', Sharpness: '40', LuminanceSmoothing: '0', GrainAmount: '50', Dehaze: '-10', Exposure2012: '1',
    });
    assert.deepEqual(summary, {
      PostCropVignetteAmount: -25, Highlights2012: -30, Clarity2012: 5, Texture: 10, Sharpness: 40, LuminanceSmoothing: 0, GrainAmount: 50, Dehaze: -10, masks: false,
    });
    assert.deepEqual(JSON.parse(JSON.stringify(summary)), summary);
  });

  test('없는 값은 요약에서 0', () => {
    const { summary } = planOf({ Exposure2012: '0' });
    assert.equal(summary.Dehaze, 0);
    assert.equal(summary.masks, false);
  });

  test('결과는 변경할 수 없다', () => {
    const plan = planOf({ Dehaze: '10' });
    assert.ok(Object.isFrozen(plan) && Object.isFrozen(plan.on) && Object.isFrozen(plan.warnings) && Object.isFrozen(plan.summary));
  });
});
