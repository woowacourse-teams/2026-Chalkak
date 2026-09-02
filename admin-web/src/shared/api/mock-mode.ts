import { assertMockModeAllowed, resolveApiMode } from "./config";

export async function startBrowserMocking() {
  const mode = resolveApiMode();
  assertMockModeAllowed(mode);

  if (mode !== "mock" || typeof window === "undefined") {
    return;
  }

  const { worker } = await import("@/mocks/browser");
  await worker.start({
    onUnhandledRequest: "bypass",
  });
}
