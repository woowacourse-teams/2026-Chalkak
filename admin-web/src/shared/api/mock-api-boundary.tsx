"use client";

import { useEffect, useState, type PropsWithChildren } from "react";

import { resolveApiMode } from "./config";
import { startBrowserMocking } from "./mock-mode";

type MockStatus = "ready" | "starting" | "failed";

export function MockApiBoundary({ children }: PropsWithChildren) {
  const [status, setStatus] = useState<MockStatus>(() =>
    resolveApiMode() === "mock" ? "starting" : "ready",
  );

  useEffect(() => {
    if (status !== "starting") {
      return;
    }

    startBrowserMocking()
      .then(() => setStatus("ready"))
      .catch(() => setStatus("failed"));
  }, [status]);

  if (status === "starting") {
    return <p role="status">Mock API 환경을 준비하고 있습니다.</p>;
  }

  if (status === "failed") {
    return (
      <p role="alert">
        Mock API 환경을 시작하지 못했습니다. 환경 설정을 확인해 주세요.
      </p>
    );
  }

  return children;
}
