import { describe, expect, it } from "vitest";
import { buildMetricsRange } from "./metrics";

describe("Jev metrics dates", () => {
  it("includes the last KST date using its next midnight as the exclusive UTC bound", () => {
    expect(buildMetricsRange("2026-11-19", "2026-11-20")).toEqual({
      from: "2026-11-18T15:00:00.000Z",
      to: "2026-11-20T15:00:00.000Z",
    });
  });

  it("allows 31 dates and rejects ranges that exceed the server's limit", () => {
    expect(buildMetricsRange("2026-10-01", "2026-10-31").to).toBe("2026-10-31T15:00:00.000Z");
    expect(() => buildMetricsRange("2026-10-01", "2026-11-01")).toThrow("최대 31일");
    expect(() => buildMetricsRange("2026-11-20", "2026-11-19")).toThrow();
  });
});
