// 방문자끼리 공유하는 체험 작품. 사진 대신 풍경 위 9:16 프레임 좌표(풍경 대비 비율)와 제목만 주고받는다.
// 클라이언트의 입력 확인과 서버의 저장 전 검증이 같은 규칙을 쓴다.

import { MAX_ZOOM, VIEWFINDER_ASPECT, type Rect } from "./camera";
import { onggiScene } from "./scene";

/**
 * 방문자끼리 작품을 공유할지 정한다. 끄면 결과 화면에 내 작품과 예시 작품만 보이고,
 * 서버로 아무것도 보내지 않으며 /api/shots도 응답하지 않는다. 안내 문구도 이 값을 따른다.
 */
export const SHARING_ENABLED = false;

export type SharedShot = {
  id: string;
  rect: Rect;
  title: string | null;
  /** ISO 8601 */
  createdAt: string;
};

/** 앱의 게시물 제목과 같은 길이(POST-005). Unicode code point 기준이다. */
export const TITLE_MAX_LENGTH = 10;

const sceneAspect = onggiScene.size.width / onggiScene.size.height;
const EPSILON = 0.002;
// 1.0x에서는 풍경 높이 전체, 최대 배율에서는 그 1/MAX_ZOOM을 담는다.
const MIN_HEIGHT = 1 / MAX_ZOOM - EPSILON;
const ASPECT_TOLERANCE = 0.02;

export type TitleCheck = { ok: true; title: string | null } | { ok: false; reason: string };

// 걸러 낼 표현. 공백·기호를 지운 뒤 포함 여부로 본다. 완전한 검열이 아니라 흔한 욕설과 광고를 막는 1차 장치다.
// "새끼 고양이", "바라보지", "불이 꺼져" 같은 평범한 제목이 걸리지 않도록 다른 말의 일부가 되기 쉬운 단어는 넣지 않는다.
const blockedWords = ["시발", "씨발", "씨빨", "ㅅㅂ", "ㅆㅂ", "병신", "ㅂㅅ", "븅신", "좆", "존나", "ㅈㄴ", "개새끼", "지랄", "ㅈㄹ", "닥쳐", "미친놈", "미친년", "섹스", "야동", "fuck", "shit"];
const urlPattern = /(https?:|www\.|\.com|\.kr|\.net|\.io|\.me)/i;
// 제어 문자와 보이지 않는 서식 문자
const invisiblePattern = /[\p{Cc}\p{Cf}]/u;

export function checkTitle(input: unknown): TitleCheck {
  if (input === null || input === undefined) return { ok: true, title: null };
  if (typeof input !== "string") return { ok: false, reason: "제목을 다시 입력해 주세요." };
  if (invisiblePattern.test(input)) return { ok: false, reason: "제목에 쓸 수 없는 문자가 있어요." };
  const title = input.trim().replace(/\s+/g, " ");
  if (title === "") return { ok: true, title: null };
  if ([...title].length > TITLE_MAX_LENGTH) return { ok: false, reason: `제목은 ${TITLE_MAX_LENGTH}자까지 쓸 수 있어요.` };
  const compact = title.toLowerCase().replace(/[\s\p{P}\p{S}]/gu, "");
  if (urlPattern.test(title) || blockedWords.some((word) => compact.includes(word))) return { ok: false, reason: "제목에 쓸 수 없는 표현이 있어요." };
  return { ok: true, title };
}

/** 체험 카메라로 찍을 수 있는 프레임인지 확인한다. 풍경 밖, 9:16이 아닌 비율, 최대 배율보다 작은 프레임은 받지 않는다. */
export function isValidShotRect(value: unknown): value is Rect {
  if (!value || typeof value !== "object") return false;
  const { x, y, width, height } = value as Record<string, unknown>;
  if (![x, y, width, height].every((n) => typeof n === "number" && Number.isFinite(n))) return false;
  const rect = { x, y, width, height } as Rect;
  if (rect.x < -EPSILON || rect.y < -EPSILON || rect.x + rect.width > 1 + EPSILON || rect.y + rect.height > 1 + EPSILON) return false;
  if (rect.height < MIN_HEIGHT || rect.height > 1 + EPSILON) return false;
  const aspect = (rect.width * sceneAspect) / rect.height;
  return Math.abs(aspect - VIEWFINDER_ASPECT) <= VIEWFINDER_ASPECT * ASPECT_TOLERANCE;
}

/** 저장 크기를 줄이고 비교를 안정적으로 하려고 소수 넷째 자리까지만 남긴다. */
export function roundRect(rect: Rect): Rect {
  const round = (n: number) => Math.round(n * 10000) / 10000;
  return { x: round(rect.x), y: round(rect.y), width: round(rect.width), height: round(rect.height) };
}
