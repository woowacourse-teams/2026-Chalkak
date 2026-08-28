"use client";

import type { PropsWithChildren } from "react";

import { MockApiBoundary } from "@/shared/api/mock-api-boundary";
import { QueryProvider } from "@/shared/query/query-provider";

export function AppProviders({ children }: PropsWithChildren) {
  return (
    <MockApiBoundary>
      <QueryProvider>{children}</QueryProvider>
    </MockApiBoundary>
  );
}
