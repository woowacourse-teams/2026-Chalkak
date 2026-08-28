import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/shared/api/errors";
import { postIds } from "@/mocks/fixtures";

import {
  deleteAdminPost,
  fetchAdminPost,
  fetchAdminPosts,
  moderateAdminPost,
} from "./post-api";

describe("admin post API", () => {
  beforeEach(() => {
    vi.stubEnv(
      "NEXT_PUBLIC_ADMIN_API_BASE_URL",
      "http://localhost:8080/api/v1/admin",
    );
    vi.stubEnv("NEXT_PUBLIC_API_MODE", "mock");
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("filters, sorts, and paginates with the real API contract", async () => {
    const response = await fetchAdminPosts({
      status: "PENDING",
      sort: "createdAtAsc",
      page: 1,
      pageSize: 20,
    });

    expect(response.posts).toHaveLength(1);
    expect(response.posts[0]?.postId).toBe(postIds.pending);
  });

  it("approves a pending post and rejects a duplicate decision", async () => {
    await expect(
      moderateAdminPost(postIds.pending, "APPROVED"),
    ).resolves.toMatchObject({
      postId: postIds.pending,
      moderationStatus: "APPROVED",
    });

    await expect(
      moderateAdminPost(postIds.pending, "REJECTED", "중복 처리"),
    ).rejects.toMatchObject({
      kind: "api",
      status: 400,
      errorCode: "RESOURCE_STATE_CHANGED",
    } satisfies Partial<ApiError>);

    await expect(fetchAdminPost(postIds.pending)).resolves.toMatchObject({
      moderationStatus: "APPROVED",
      rejectionReason: null,
    });
  });

  it("soft deletes a post but rejects a validating post", async () => {
    await deleteAdminPost(postIds.approved, "운영 정책 위반");

    const deleted = await fetchAdminPost(postIds.approved);
    expect(deleted.deletedAt).toMatch(/Z$/);
    expect(deleted.photo?.originalImageUrl).toContain("https://");

    await expect(
      deleteAdminPost(postIds.validating, "잘못된 이미지"),
    ).rejects.toMatchObject({
      errorCode: "RESOURCE_STATE_CHANGED",
    } satisfies Partial<ApiError>);
  });

  it("explains a missing post with a 404 error", async () => {
    await expect(fetchAdminPost("missing")).rejects.toMatchObject({
      status: 404,
      errorCode: "POST_NOT_FOUND",
    } satisfies Partial<ApiError>);
  });
});
