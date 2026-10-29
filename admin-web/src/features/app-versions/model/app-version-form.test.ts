import { describe, expect, it } from "vitest";
import { isStoreUrl, isSupportedVersion, isVersionIncrease, normalizeAppVersion, validateAppVersion } from "./app-version-form";

const android = "https://play.google.com/store/apps/details?id=com.example.chalkak";
const ios = "https://apps.apple.com/kr/app/chalkak/id123456789";
const input = { revision: null, minimumSupportedVersion: "1.10.0", storeUrl: android, reason: "출시 확인" };

describe("app version inputs", () => {
  it.each(["0.0.0", "1.10.0", "2147483647.2147483647.2147483647"])("accepts bounded numeric version %s", (value) => expect(isSupportedVersion(value)).toBe(true));
  it.each(["01.2.0", "1.2", "1.2.3.4", "1.2.3-beta", "-1.0.0", "1.2147483648.0", " 1.2.3", "1.2.3 ", "1.2.3\n"])("rejects malformed version %s", (value) => expect(isSupportedVersion(value)).toBe(false));
  it("compares segments numerically and allows downgrades", () => {
    expect(isVersionIncrease("1.9.0", "1.10.0")).toBe(true);
    expect(isVersionIncrease("2.0.0", "1.99.0")).toBe(false);
    expect(validateAppVersion("ANDROID", { ...input, minimumSupportedVersion: "0.0.0" })).toBeNull();
  });
  it.each([android, android + "&hl=ko&gl=KR", android.replace(".com/", ".com:443/"), android.replace("https:", "HTTPS:")])("accepts Android store URL %s", (value) => expect(isStoreUrl("ANDROID", value)).toBe(true));
  it.each([ios, "https://apps.apple.com/app/id123", ios + "?l=ko&mt=8", "https://apps.apple.com/kr/app/%EC%B0%B0%EC%B9%B5/id123"])("accepts iOS store URL %s", (value) => expect(isStoreUrl("IOS", value)).toBe(true));
  it.each([
    android.replace("https:", "http:"), android.replace(".com/", ".com.evil.test/"),
    android.replace(".com/", ".com:0443/"), android.replace(".com/", ".com:/"),
    android.replace(".com/", ".com:444/"), android.replace("//play", "//user@play"),
    android + "#details", android + "&id=com.other.app", android + "&%69d=com.other.app", android + "&utm_source=link",
    android.replace("/store/", "/%73tore/"), android.replace("/store/", "/a/../store/"),
    android.replace("/store/", "/%2e%2e/store/"), android.replace("/store/", "/store%2f"),
    android.replace("com.example.chalkak", "invalid"), android + "&hl=", android + "&hl=%zz", android + "&",
  ])("rejects unsafe or unsupported Android URL %s", (value) => expect(isStoreUrl("ANDROID", value)).toBe(false));
  it.each([ios.replace("id123456789", "id0"), ios + "?mt=9", ios + "?l=ko&l=en", ios.replace("/app/", "/app/%2F"), ios.replace("/app/", "/app/%5c"), ios.replace("/app/", "/%61pp/")])("rejects unsafe or unsupported iOS URL %s", (value) => expect(isStoreUrl("IOS", value)).toBe(false));
  it("rejects the wrong platform and counts Unicode code points after Java-compatible stripping", () => {
    expect(isStoreUrl("ANDROID", ios)).toBe(false);
    expect(isStoreUrl("IOS", android)).toBe(false);
    expect(validateAppVersion("ANDROID", { ...input, reason: " \t" + "😀".repeat(500) + "\n " })).toBeNull();
    expect(validateAppVersion("ANDROID", { ...input, reason: "😀".repeat(501) })).toContain("1~500자");
    expect(validateAppVersion("ANDROID", { ...input, reason: "\t \n" })).toContain("1~500자");
    expect(normalizeAppVersion({ ...input, storeUrl: "\t" + android + "\n", reason: " 출시 확인 " })).toEqual(input);
  });
});
