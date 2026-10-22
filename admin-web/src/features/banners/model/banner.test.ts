import { describe, expect, it } from "vitest";
import { normalizeBanner, validateBanner, type BannerInput } from "./banner";
const input: BannerInput = { title: "제목", subtitle: "부제목", webViewUrl: "https://example.com/event?id=1#info", thumbnailUrl: "https://example.com/image.png" };
describe("banner input", () => {
  it.each(["title", "subtitle", "webViewUrl", "thumbnailUrl"] as const)("requires %s", (key) => {
    expect(validateBanner({ ...input, [key]: " \u2003 " })[key]).toBeTruthy();
  });
  it.each([["title", 50], ["subtitle", 100]] as const)("counts %s in Unicode code points", (key, limit) => {
    expect(validateBanner({ ...input, [key]: "📷".repeat(limit - 1) })).toEqual({});
    expect(validateBanner({ ...input, [key]: "📷".repeat(limit) })).toEqual({});
    expect(validateBanner({ ...input, [key]: "📷".repeat(limit + 1) })[key]).toBeTruthy();
  });
  it.each(["webViewUrl", "thumbnailUrl"] as const)("checks %s length and HTTPS host", (key) => {
    for (const length of [1999, 2000]) expect(validateBanner({ ...input, [key]: "https://example.com/" + "a".repeat(length - 20) })).toEqual({});
    for (const value of ["https://example.com/" + "a".repeat(2001 - 20), "http://example.com", "/event", "javascript:alert(1)", "https://example.com:65536/"]) expect(validateBanner({ ...input, [key]: value })[key]).toBeTruthy();
  });
  it("normalizes whitespace without rewriting query, fragment or URL casing", () => {
    expect(normalizeBanner({ ...input, title: "\u2003 제목 \t", webViewUrl: `  ${input.webViewUrl}  ` })).toEqual(input);
  });
});
