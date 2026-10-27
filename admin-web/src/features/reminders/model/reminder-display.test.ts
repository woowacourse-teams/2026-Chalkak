import { describe, expect, it } from "vitest";
import { reminderFixtures } from "@/mocks/reminder-handlers";
import { canEditReminder, reminderSchedule, reminderStatus, validateReminder } from "./reminder-display";

const [daily, once] = reminderFixtures;
const now = Date.parse("2026-10-22T10:00:00Z");

describe("reminder policy", () => {
  it("shows one-time and daily schedules in KST", () => {
    expect(reminderSchedule(daily)).toBe("매일 21:00 KST · 2026. 10. 20.부터");
    expect(reminderSchedule({ repeat: "ONCE", scheduledAt: "2026-10-22T15:30:00Z" })).toBe("2026. 10. 23. 00:30 KST");
  });
  it("distinguishes scheduled, passed, not-started, and running reminders", () => {
    expect(reminderStatus(once, now)).toBe("발송 예정");
    expect(reminderStatus({ ...once, scheduledAt: "2026-10-22T10:00:00Z" }, now)).toBe("발송 시각 지남");
    expect(reminderStatus(daily, now)).toBe("매일 발송 중");
    expect(reminderStatus({ ...daily, scheduledAt: "2026-10-22T10:00:01Z" }, now)).toBe("시작 전");
  });
  it("allows editing a one-time reminder only before its time and a daily reminder at any time", () => {
    expect(canEditReminder({ ...once, scheduledAt: "2026-10-22T10:00:01Z" }, now)).toBe(true);
    expect(canEditReminder({ ...once, scheduledAt: "2026-10-22T10:00:00Z" }, now)).toBe(false);
    expect(canEditReminder(daily, now)).toBe(true);
  });
  it("counts trimmed Unicode code points and requires a choice for repeat and target", () => {
    const input = { title: ` ${"⏰".repeat(50)} `, body: "⏰".repeat(500), repeat: "ONCE" as const, target: "ALL" as const, scheduledAt: "2026-10-22T10:00:00Z" };
    expect(validateReminder(input, undefined, now)).toBeNull();
    expect(validateReminder({ ...input, title: "⏰".repeat(51) }, undefined, now)).toContain("제목");
    expect(validateReminder({ ...input, body: "   " }, undefined, now)).toContain("본문");
    expect(validateReminder({ ...input, repeat: "WEEKLY" as never }, undefined, now)).toContain("반복 방식");
    expect(validateReminder({ ...input, target: "" as never }, undefined, now)).toContain("발송 대상");
    expect(validateReminder({ ...input, scheduledAt: "" }, undefined, now)).toContain("KST");
  });
  it("rejects any past time when creating or changing the schedule", () => {
    const input = { title: "제목", body: "본문", repeat: "ONCE" as const, target: "ALL" as const, scheduledAt: "2026-10-22T09:59:59.999Z" };
    expect(validateReminder(input, undefined, now)).toContain("과거");
    expect(validateReminder({ ...input, scheduledAt: "2026-10-22T10:00:00Z" }, undefined, now)).toBeNull();
    expect(validateReminder({ ...input, repeat: "DAILY", scheduledAt: "2026-10-22T09:00:00Z" }, daily, now)).toContain("과거");
  });
  it("keeps a started daily reminder editable when only the text or target changes", () => {
    const input = { title: "고친 제목", body: "고친 본문", repeat: "DAILY" as const, target: "ALL" as const, scheduledAt: daily.scheduledAt };
    expect(validateReminder(input, daily, now)).toBeNull();
    expect(validateReminder({ ...input, repeat: "ONCE" }, daily, now)).toContain("과거");
  });
});
