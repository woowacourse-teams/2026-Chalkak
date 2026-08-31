import type { NextConfig } from "next";

if (process.env.VERCEL_ENV === "production") {
  throw new Error(
    "실제 웹 로그인·운영 연결 검증과 승인 전에는 Vercel Production 배포를 허용하지 않습니다.",
  );
}

if (
  process.env.NODE_ENV === "production" &&
  process.env.NEXT_PUBLIC_API_MODE === "mock"
) {
  throw new Error("운영 빌드에서는 NEXT_PUBLIC_API_MODE=mock을 사용할 수 없습니다.");
}

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Keep local mobile action bars unobstructed; runtime error overlays still work.
  devIndicators: false,
  images: {
    remotePatterns: [
      {
        protocol: "https",
        hostname: "**",
      },
      {
        protocol: "http",
        hostname: "localhost",
      },
    ],
  },
};

export default nextConfig;
