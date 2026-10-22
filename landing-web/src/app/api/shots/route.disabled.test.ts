// @vitest-environment node
import { describe, expect, it } from "vitest";

import { frameRect } from "@/features/experience/camera";
import { onggiScene } from "@/features/experience/scene";
import { SHARING_ENABLED } from "@/features/experience/shared-shot";

import { GET, POST } from "./route";

const rect = frameRect({ cx: 0.4, cy: 0.5, h: 0.5 }, onggiScene.size.width / onggiScene.size.height);

describe.runIf(!SHARING_ENABLED)("/api/shots (공유 꺼짐)", () => {
  it("저장도 조회도 하지 않는다", async () => {
    const posted = await POST(new Request("http://localhost/api/shots", { method: "POST", body: JSON.stringify({ rect, title: "온기" }) }));
    expect(posted.status).toBe(404);
    expect((await GET()).status).toBe(404);
  });
});
