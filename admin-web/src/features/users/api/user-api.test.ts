import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { userIds } from "@/mocks/fixtures";

import { fetchAdminUser, fetchAdminUsers, updateAdminUserStatus } from "./user-api";

describe("admin user API", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", "http://localhost:8080/api/v1/admin");
    vi.stubEnv("NEXT_PUBLIC_API_MODE", "mock");
  });
  afterEach(() => vi.unstubAllEnvs());

  it("filters users by email and status", async () => {
    const response = await fetchAdminUsers({
      email: "creator",
      status: "ACTIVE",
      sort: "createdAtDesc",
      page: 1,
      pageSize: 20,
    });
    expect(response.users.map((user) => user.userId)).toEqual([userIds.active]);
  });

  it("changes a status with a reason and returns the updated detail", async () => {
    await expect(updateAdminUserStatus(userIds.active, "BANNED", "운영 정책 위반")).resolves.toEqual({
      userId: userIds.active,
      status: "BANNED",
    });
    await expect(fetchAdminUser(userIds.active)).resolves.toMatchObject({ status: "BANNED" });
  });

  it("keeps withdrawn users read-only", async () => {
    await expect(updateAdminUserStatus(userIds.withdrawn, "ACTIVE", "복구 요청")).rejects.toMatchObject({
      status: 400,
      errorCode: "RESOURCE_STATE_CHANGED",
    });
  });
});
