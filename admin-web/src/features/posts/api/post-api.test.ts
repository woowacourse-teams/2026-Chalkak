import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";

import { ApiError } from "@/shared/api/errors";
import type { AdminPostDetailResponse } from "@/shared/api/contracts";
import { postDetailFixtures, postIds } from "@/mocks/fixtures";
import { server } from "@/mocks/server";

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

  it("uses the backend READY image-upload status in the review fixture", async () => {
    const response = await fetchAdminPost(postIds.pending);

    expect(response.imageUpload?.status).toBe("READY");
  });

  it("accepts absent photo metadata fields and a missing image-upload record", async () => {
    const fixture = postDetailFixtures[postIds.pending];
    const response = {
      ...fixture,
      photo: fixture.photo ? { ...fixture.photo, metadata: {} } : null,
      imageUpload: null,
    } satisfies AdminPostDetailResponse;
    server.use(http.get("*/api/v1/admin/posts/:postId", () => HttpResponse.json(response)));

    const post = await fetchAdminPost(postIds.pending);

    expect(post.photo?.metadata).toEqual({});
    expect(post.photo?.metadata.byteSize).toBeUndefined();
    expect(post.imageUpload).toBeNull();
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

  it("soft deletes a post and allows idempotent repeated deletion", async () => {
    await deleteAdminPost(postIds.approved, "운영 정책 위반");

    const deleted = await fetchAdminPost(postIds.approved);
    expect(deleted.deletedAt).toMatch(/Z$/);
    expect(deleted.photo?.originalImageUrl).toContain("https://");

    await expect(deleteAdminPost(postIds.approved, "운영 정책 위반")).resolves.toBeUndefined();
    expect((await fetchAdminPost(postIds.approved)).deletedAt).toBe(deleted.deletedAt);
  });

  it("explains a missing post with a 404 error", async () => {
    await expect(fetchAdminPost("missing")).rejects.toMatchObject({
      status: 404,
      errorCode: "BUSINESS_ERROR",
    } satisfies Partial<ApiError>);
  });
});
