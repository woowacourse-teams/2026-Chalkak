// @vitest-environment node
import { describe, expect, it, vi } from "vitest";

import { frameRect } from "@/features/experience/camera";
import { onggiScene } from "@/features/experience/scene";

import { GET, POST } from "./route";

// 공유를 켠 상태의 동작을 검증한다. 꺼진 상태는 route.disabled.test.ts에서 본다.
vi.mock("@/features/experience/shared-shot", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/experience/shared-shot")>()),
  SHARING_ENABLED: true,
}));

const rect = frameRect({ cx: 0.4, cy: 0.5, h: 0.5 }, onggiScene.size.width / onggiScene.size.height);

function post(body: unknown, ip = "10.0.0.1") {
  return POST(new Request("http://localhost/api/shots", { method: "POST", headers: { "x-real-ip": ip }, body: JSON.stringify(body) }));
}

describe("/api/shots", () => {
  it("작품을 저장하고 내 작품을 뺀 최근 작품을 돌려준다", async () => {
    const first = await post({ rect, title: "첫 장" }, "10.0.0.2");
    expect(first.status).toBe(201);
    const second = await post({ rect, title: null }, "10.0.0.3");
    const data = await second.json();
    expect(data.shot.title).toBeNull();
    expect(data.shots.map((item: { id: string }) => item.id)).not.toContain(data.shot.id);
    expect(data.shots[0].title).toBe("첫 장");

    const list = await (await GET()).json();
    expect(list.shots[0].id).toBe(data.shot.id);
  });

  it("체험 카메라로 찍을 수 없는 프레임과 쓸 수 없는 제목은 거절한다", async () => {
    expect((await post({ rect: { ...rect, x: 0.95 } })).status).toBe(400);
    const rejected = await post({ rect, title: "가나다라마바사아자차카" });
    expect(rejected.status).toBe(400);
    expect((await rejected.json()).error).toContain("10자");
  });

  it("같은 방문자가 1분에 5장보다 많이 보내면 막는다", async () => {
    const statuses = [];
    for (let i = 0; i < 6; i++) statuses.push((await post({ rect }, "10.0.0.9")).status);
    expect(statuses.slice(0, 5).every((status) => status === 201)).toBe(true);
    expect(statuses[5]).toBe(429);
  });
});
