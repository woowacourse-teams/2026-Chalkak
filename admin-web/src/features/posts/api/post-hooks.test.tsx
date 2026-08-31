import { act, renderHook } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import type { PropsWithChildren } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { postDetailFixtures, postIds, topicIds, userIds } from "@/mocks/fixtures";
import { createQueryClient, queryKeys } from "@/shared/query/query-client";
import { useDeleteAdminPost, useModerateAdminPost } from "./post-hooks";

describe("post mutation cache integration", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", "http://localhost:8080/api/v1/admin");
    vi.stubEnv("NEXT_PUBLIC_API_MODE", "mock");
  });
  afterEach(() => vi.unstubAllEnvs());

  function setup() {
    const client = createQueryClient();
    const keys = [
      queryKeys.posts.list({ status: "PENDING" }),
      queryKeys.users.detail(userIds.active),
      queryKeys.topics.detail(topicIds.open),
      queryKeys.auditLogs,
    ];
    keys.forEach((key) => client.setQueryData(key, { existing: true }));
    const wrapper = ({ children }: PropsWithChildren) => <QueryClientProvider client={client}>{children}</QueryClientProvider>;
    return { client, keys, wrapper };
  }

  it("refreshes counts and audit entries after moderation", async () => {
    const { client, keys, wrapper } = setup();
    client.setQueryData(queryKeys.posts.detail(postIds.pending), postDetailFixtures[postIds.pending]);
    const { result } = renderHook(() => useModerateAdminPost(), { wrapper });
    await act(async () => { await result.current.mutateAsync({ postId: postIds.pending, status: "APPROVED" }); });
    expect(client.getQueryData(queryKeys.posts.detail(postIds.pending))).toMatchObject({ moderationStatus: "APPROVED" });
    keys.forEach((key) => expect(client.getQueryState(key)?.isInvalidated).toBe(true));
  });

  it("refreshes counts and audit entries after deletion", async () => {
    const { client, keys, wrapper } = setup();
    const { result } = renderHook(() => useDeleteAdminPost(), { wrapper });
    await act(async () => { await result.current.mutateAsync({ postId: postIds.approved, reason: "운영 정책 위반" }); });
    keys.forEach((key) => expect(client.getQueryState(key)?.isInvalidated).toBe(true));
  });
});
