import type { NextConfig } from "next";

if (process.env.VERCEL_ENV === "production") {
  throw new Error(
    "관리자 인증·인가가 완료되기 전에는 Vercel Production 배포를 허용하지 않습니다.",
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
