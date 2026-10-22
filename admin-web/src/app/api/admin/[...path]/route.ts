import type { NextRequest } from "next/server";

import { relayAdminRequest } from "@/features/auth/server/admin-api-relay";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";
// AI 프롬프트 미리보기 중계가 최대 70초까지 기다리므로 명시한다. 플랜 한도가 이보다 낮으면 미리보기가 조용히 끊기지 않고 배포가 눈에 띄게 실패한다.
export const maxDuration = 90;

async function handle(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  return relayAdminRequest(request, path);
}

export const GET = handle;
export const POST = handle;
export const PUT = handle;
export const PATCH = handle;
export const DELETE = handle;
export const HEAD = handle;
export const OPTIONS = handle;
