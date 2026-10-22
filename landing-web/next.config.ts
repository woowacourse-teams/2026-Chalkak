import type { NextConfig } from "next";

import { APP_SCREEN_VERSION } from "./src/features/landing/app-screens";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  devIndicators: false,
  // 2안으로 확정해 시안 비교 주소는 첫 페이지로 보낸다. 다시 바꿀 수 있게 임시 리다이렉트(307)로 둔다.
  redirects() {
    return [
      { source: "/v2", destination: "/", permanent: false },
      { source: "/v3", destination: "/", permanent: false },
    ];
  },
  images: {
    localPatterns: [
      { pathname: "/**", search: "" },
      { pathname: "/app-screens/*.webp", search: `?v=${APP_SCREEN_VERSION}` },
    ],
    formats: ["image/avif", "image/webp"],
  },
};

export default nextConfig;
