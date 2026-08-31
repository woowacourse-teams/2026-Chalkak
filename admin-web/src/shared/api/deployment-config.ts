export function assertDeploymentConfig(env: NodeJS.ProcessEnv) {
  if (env.NODE_ENV === "production" && env.NEXT_PUBLIC_API_MODE === "mock") {
    throw new Error("운영 빌드에서는 NEXT_PUBLIC_API_MODE=mock을 사용할 수 없습니다.");
  }

  const isDeployment = env.NODE_ENV === "production" ||
    env.VERCEL_ENV === "preview" || env.VERCEL_ENV === "production";
  if (!isDeployment) return;

  if (env.NEXT_PUBLIC_API_MODE !== "real") {
    throw new Error("배포 빌드에는 NEXT_PUBLIC_API_MODE=real 설정이 필요합니다.");
  }

  try {
    const url = new URL(env.NEXT_PUBLIC_ADMIN_API_BASE_URL?.trim() ?? "");
    // Match the relay's production URL restrictions before public values are bundled.
    if (url.protocol !== "https:" || url.username || url.password || url.search || url.hash ||
      !url.pathname.replace(/\/+$/, "").endsWith("/api/v1/admin")) {
      throw new Error("Invalid admin API URL");
    }
  } catch {
    throw new Error("배포 빌드의 NEXT_PUBLIC_ADMIN_API_BASE_URL은 인증 정보, 쿼리, 프래그먼트 없이 /api/v1/admin으로 끝나는 HTTPS URL이어야 합니다.");
  }
}
