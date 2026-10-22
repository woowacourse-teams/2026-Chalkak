import { describe, expect, it } from "vitest";

import { APP_STORE_URL, detectPlatform, PLAY_STORE_URL, siteUrl, storeUrlFor } from "./store-links";

const UA = {
  iphone: "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148",
  ipad: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Version/18.0 Safari/605.1.15",
  android: "Mozilla/5.0 (Linux; Android 15; SM-S928N) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36",
  kakaoAndroid: "Mozilla/5.0 (Linux; Android 14; SM-G991N; wv) AppleWebKit/537.36 Chrome/129.0 Mobile Safari/537.36 KAKAOTALK 10.8.0",
  mac: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/130.0 Safari/537.36",
};

describe("detectPlatform", () => {
  it("iPhone과 터치 가능한 iPad를 iOS로 본다", () => {
    expect(detectPlatform(UA.iphone)).toBe("ios");
    expect(detectPlatform(UA.ipad, 5)).toBe("ios");
  });

  it("Android와 카카오톡 인앱 브라우저를 Android로 본다", () => {
    expect(detectPlatform(UA.android)).toBe("android");
    expect(detectPlatform(UA.kakaoAndroid)).toBe("android");
  });

  it("터치가 없는 Mac은 데스크톱으로 본다", () => {
    expect(detectPlatform(UA.mac, 0)).toBe("other");
  });
});

describe("storeUrlFor", () => {
  it("플랫폼에 맞는 스토어 주소를 돌려준다", () => {
    expect(storeUrlFor("ios")).toBe(APP_STORE_URL);
    expect(storeUrlFor("android")).toBe(PLAY_STORE_URL);
    expect(storeUrlFor("other")).toBeNull();
  });
});

describe("siteUrl", () => {
  it("직접 지정한 주소를 가장 먼저 쓴다", () => {
    expect(siteUrl({ NEXT_PUBLIC_SITE_URL: "https://chalkak.example/", VERCEL_PROJECT_PRODUCTION_URL: "x.vercel.app" })).toBe(
      "https://chalkak.example",
    );
  });

  it("Vercel 프로덕션 주소를 https로 쓴다", () => {
    expect(siteUrl({ VERCEL_PROJECT_PRODUCTION_URL: "chalkak.vercel.app" })).toBe("https://chalkak.vercel.app");
  });

  it("아무것도 없으면 로컬 주소를 쓴다", () => {
    expect(siteUrl({})).toBe("http://localhost:3000");
  });
});
