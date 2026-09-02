"use client";

import type { PropsWithChildren } from "react";
import { AdminSessionProvider } from "@/features/auth/admin-session-provider";

import { MockApiBoundary } from "@/shared/api/mock-api-boundary";
import { QueryProvider } from "@/shared/query/query-provider";

export function AppProviders({ children }: PropsWithChildren) {
  return (
    <MockApiBoundary>
      <QueryProvider><AdminSessionProvider>{children}</AdminSessionProvider></QueryProvider>
    </MockApiBoundary>
  );
}
