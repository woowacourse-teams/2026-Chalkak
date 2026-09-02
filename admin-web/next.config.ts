import type { NextConfig } from "next";

import { assertDeploymentConfig } from "./src/shared/api/deployment-config";

assertDeploymentConfig(process.env);

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
