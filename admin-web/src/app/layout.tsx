import type { Metadata } from "next";
import { Suspense, type ReactNode } from "react";

import { AdminAccessBoundary } from "@/features/auth/admin-access-boundary";
import { ToastProvider } from "@/shared/ui/toast";

import { AppProviders } from "./providers";
import "./globals.css";

export const metadata: Metadata = {
  title: "Chalkak Admin",
  description: "Chalkak 서비스 운영을 위한 관리자 웹",
  robots: {
    index: false,
    follow: false,
  },
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <AppProviders>
          <ToastProvider>
            <Suspense fallback={<main className="session-loading" role="status">관리자 화면을 준비하고 있습니다.</main>}>
              <AdminAccessBoundary>{children}</AdminAccessBoundary>
            </Suspense>
          </ToastProvider>
        </AppProviders>
      </body>
    </html>
  );
}
