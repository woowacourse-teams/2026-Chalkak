import { checkTitle, isValidShotRect, roundRect, SHARING_ENABLED, type SharedShot } from "@/features/experience/shared-shot";
import { shotStore } from "@/features/experience/shot-store";

export const dynamic = "force-dynamic";

const LIST_LIMIT = 40;
const MAX_BODY_BYTES = 2048;
// 같은 방문자는 1분에 5장, 전체로는 1분에 60장까지 받는다.
const PER_CLIENT_LIMIT = 5;
const GLOBAL_LIMIT = 60;
const WINDOW_SECONDS = 60;

// 인스턴스 메모리에만 두는 짧은 기록이다. IP는 저장소에 쓰지 않는다.
const recentByClient = new Map<string, number[]>();

function clientKey(request: Request) {
  return request.headers.get("x-real-ip") ?? request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? "unknown";
}

function allowClient(key: string, now = Date.now()) {
  const since = now - WINDOW_SECONDS * 1000;
  const times = (recentByClient.get(key) ?? []).filter((time) => time > since);
  if (times.length >= PER_CLIENT_LIMIT) return false;
  times.push(now);
  recentByClient.set(key, times);
  if (recentByClient.size > 5000) recentByClient.clear();
  return true;
}

function error(status: number, message: string) {
  return Response.json({ error: message }, { status, headers: { "Cache-Control": "no-store" } });
}

// 공유를 끄면 저장도 조회도 하지 않는다.
const disabled = () => new Response(null, { status: 404 });

export async function GET() {
  if (!SHARING_ENABLED) return disabled();
  const store = shotStore();
  if (!store) return error(503, "지금은 다른 방문자의 작품을 불러올 수 없어요.");
  try {
    const shots = await store.latest(LIST_LIMIT);
    return Response.json({ shots }, { headers: { "Cache-Control": "no-store" } });
  } catch {
    return error(503, "지금은 다른 방문자의 작품을 불러올 수 없어요.");
  }
}

/** 내 작품을 저장하고, 내 작품을 뺀 최근 작품 목록을 함께 돌려준다. */
export async function POST(request: Request) {
  if (!SHARING_ENABLED) return disabled();
  const store = shotStore();
  if (!store) return error(503, "지금은 작품을 공유할 수 없어요.");

  const text = await request.text();
  if (text.length > MAX_BODY_BYTES) return error(413, "요청이 너무 커요.");
  let body: { rect?: unknown; title?: unknown };
  try {
    body = JSON.parse(text);
  } catch {
    return error(400, "잘못된 요청이에요.");
  }

  if (!isValidShotRect(body.rect)) return error(400, "체험 카메라로 찍은 장면만 공유할 수 있어요.");
  const title = checkTitle(body.title);
  if (!title.ok) return error(400, title.reason);
  if (!allowClient(clientKey(request))) return error(429, "잠시 후에 다시 공유해 주세요.");

  try {
    if ((await store.countSince(WINDOW_SECONDS)) >= GLOBAL_LIMIT) return error(429, "지금 공유가 몰리고 있어요. 잠시 후에 다시 시도해 주세요.");
    const shot = await store.add(roundRect(body.rect), title.title);
    const others: SharedShot[] = (await store.latest(LIST_LIMIT + 1)).filter((item) => item.id !== shot.id).slice(0, LIST_LIMIT);
    return Response.json({ shot, shots: others }, { status: 201, headers: { "Cache-Control": "no-store" } });
  } catch {
    return error(503, "지금은 작품을 공유할 수 없어요.");
  }
}
