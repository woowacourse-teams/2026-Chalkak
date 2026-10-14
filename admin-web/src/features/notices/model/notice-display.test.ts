import { describe, expect, it } from "vitest";
import { noticeFixtures } from "@/mocks/notice-handlers";
import { canEditNotice, fromKstInput, noticeStatus, toKstInput, validateNotice } from "./notice-display";

describe("notice policy", () => {
  it("distinguishes inbox distribution, cancellation, and hiding", () => {
    const [scheduled, distributed] = noticeFixtures;
    expect(noticeStatus(scheduled)).toBe("예약 대기");
    expect(noticeStatus(distributed)).toBe("알림함 배포 완료");
    expect(noticeStatus({ ...scheduled, deletedAt: scheduled.createdAt })).toBe("발송 취소");
    expect(noticeStatus({ ...distributed, deletedAt: distributed.createdAt })).toBe("배포 후 숨김");
    expect(canEditNotice(distributed)).toBe(false);
    expect(canEditNotice({ ...scheduled, deletedAt: scheduled.createdAt })).toBe(false);
  });
  it("converts KST across date boundaries without using the browser time zone", () => {
    expect(fromKstInput("2026-10-20T02:00")).toBe("2026-10-19T17:00:00.000Z");
    expect(toKstInput("2026-10-19T17:00:01Z")).toBe("2026-10-20T02:00:01");
    expect(fromKstInput("2026-02-30T12:00")).toBeNull();
    expect(fromKstInput("")).toBeNull();
  });
  it("counts trimmed Unicode code points and enforces the five-minute boundary", () => {
    const now = Date.parse("2026-10-14T10:00:00Z");
    const input = { title: ` ${"📢".repeat(50)} `, body: "📢".repeat(500), scheduledAt: "2026-10-14T09:55:00Z" };
    expect(validateNotice(input, now)).toBeNull();
    expect(validateNotice({ ...input, title: "📢".repeat(51) }, now)).toContain("제목");
    expect(validateNotice({ ...input, body: "📢".repeat(501) }, now)).toContain("본문");
    expect(validateNotice({ ...input, title: "   " }, now)).toContain("제목");
    expect(validateNotice({ ...input, scheduledAt: "2026-10-14T09:54:59.999Z" }, now)).toContain("5분");
  });
});
