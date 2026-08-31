"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, type PropsWithChildren } from "react";

import { AdminShell } from "@/shared/layout/admin-shell";
import { ErrorState } from "@/shared/ui/feedback-states";

import { useAdminSession } from "./admin-session-provider";
import { getLoginUrl } from "./model/return-to";

export function AdminAccessBoundary({ children }: PropsWithChildren) {
  const pathname = usePathname();
  const params = useSearchParams();
  const router = useRouter();
  const { status, error, refresh, logout } = useAdminSession();
  const isLogin = pathname === "/login";
  const search = params.toString();

  useEffect(() => {
    if (status === "anonymous" && !isLogin) {
      router.replace(error ? "/login?logout=failed" : getLoginUrl(pathname + (search ? "?" + search : "")));
    }
  }, [error, isLogin, pathname, router, search, status]);

  if (isLogin) return children;

  if (status === "loading" || status === "logging-out" || status === "anonymous") {
    return <main className="session-loading" role="status">{status === "logging-out" ? "로그아웃하고 있습니다." : "로그인 상태를 확인하고 있습니다."}</main>;
  }

  if (status === "forbidden" || status === "error") {
    return (
      <main className="session-loading">
        <ErrorState
          title={status === "forbidden" ? "관리자 권한이 필요합니다" : "로그인 상태를 확인하지 못했습니다"}
          description={error ?? "잠시 후 다시 시도해 주세요."}
          onRetry={() => { void refresh(); }}
        />
        <button type="button" onClick={() => { void logout().catch(() => undefined); }}>로그인 화면으로</button>
      </main>
    );
  }

  return <AdminShell>{children}</AdminShell>;
}
