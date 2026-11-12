// 추출 실패 유형: INVALID_PROBE는 작가 파일 문제(재시도 없음), PROCESSING_FAILED는 내부 오류(재시도 가능)
export const INVALID_PROBE = 'INVALID_PROBE';
export const PROCESSING_FAILED = 'PROCESSING_FAILED';

export class ExtractionFailure extends Error {
  constructor(type, message) {
    super(message);
    this.name = 'ExtractionFailure';
    this.type = type;
  }

  static invalidProbe(message) {
    return new ExtractionFailure(INVALID_PROBE, message);
  }

  static processingFailed(message) {
    return new ExtractionFailure(PROCESSING_FAILED, message);
  }
}
