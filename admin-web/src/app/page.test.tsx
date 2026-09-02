import { describe, expect, it, vi } from "vitest";

import Home from "./page";

const { redirect } = vi.hoisted(() => ({
  redirect: vi.fn(() => { throw new Error("NEXT_REDIRECT"); }),
}));

vi.mock("next/navigation", () => ({ redirect }));

describe("Home", () => {
  it("opens the pending post review queue", () => {
    expect(() => Home()).toThrow("NEXT_REDIRECT");
    expect(redirect).toHaveBeenCalledWith("/posts?status=PENDING");
  });
});
