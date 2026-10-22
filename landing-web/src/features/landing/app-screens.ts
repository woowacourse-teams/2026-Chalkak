/** 캡처를 교체할 때 이 버전도 갱신한다. 동일 파일명의 Next/브라우저 이미지 캐시를 구분한다. */
export const APP_SCREEN_VERSION = "2026-10-15-e39d3294";
export const SCREEN_WIDTH = 642;
export const SCREEN_HEIGHT = 1396;

export function appScreenSource(file: string) {
  return `/app-screens/${file}?v=${APP_SCREEN_VERSION}`;
}
