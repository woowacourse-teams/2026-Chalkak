import type { Metadata } from "next";
import type { ReactNode } from "react";

import { AdminAccessBoundary } from "@/features/auth/admin-access-boundary";
import { AdminShell } from "@/shared/layout/admin-shell";
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
          <AdminAccessBoundary>
            <ToastProvider>
              <AdminShell>{children}</AdminShell>
            </ToastProvider>
          </AdminAccessBoundary>
        </AppProviders>
      </body>
    </html>
  );
}
