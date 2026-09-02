import type { NextRequest } from "next/server";

import { relayAdminRequest } from "@/features/auth/server/admin-api-relay";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

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
