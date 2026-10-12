import { afterEach, describe, expect, it } from "vitest";
import { add24HoursToKstInput } from "./topic-display";

const previousTimezone = process.env.TZ;
afterEach(() => {
  if (previousTimezone === undefined) delete process.env.TZ;
  else process.env.TZ = previousTimezone;
});

describe("KST participation end default", () => {
  it.each([
    ["2026-10-12T09:30", "2026-10-13T09:30"],
    ["2026-10-31T23:59", "2026-11-01T23:59"],
    ["2026-12-31T00:00", "2027-01-01T00:00"],
    ["2028-02-28T12:15", "2028-02-29T12:15"],
    ["2028-02-29T12:15", "2028-03-01T12:15"],
    ["2027-02-28T12:15", "2027-03-01T12:15"],
  ])("adds exactly 24 hours across calendar boundaries: %s", (start, end) => {
    expect(add24HoursToKstInput(start)).toBe(end);
  });

  it.each(["Asia/Seoul", "UTC", "America/New_York"])("does not use the browser zone: %s", (zone) => {
    process.env.TZ = zone;
    // This date crosses a US daylight-saving transition, but the input is KST.
    expect(add24HoursToKstInput("2026-03-08T01:30")).toBe("2026-03-09T01:30");
  });

  it.each(["", "2026-10", "invalid", "2026-13-12T09:30"])("handles empty or incomplete input without throwing: %s", (value) => {
    expect(add24HoursToKstInput(value)).toBe("");
  });
});
