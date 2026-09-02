import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";

import { userDetailFixtures, userIds } from "@/mocks/fixtures";
import { server } from "@/mocks/server";
import type { AdminUserDetailResponse, AdminUserListResponse } from "@/shared/api/contracts";

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

  it("accepts the backend APPLE social provider in lists and details", async () => {
    const detail = {
      ...userDetailFixtures[userIds.active],
      socialProvider: "APPLE",
    } satisfies AdminUserDetailResponse;
    const { signature, ...summary } = detail;
    void signature;
    const list = {
      currentPage: 1,
      pageSize: 20,
      hasNext: false,
      users: [summary],
    } satisfies AdminUserListResponse;
    server.use(
      http.get("*/api/v1/admin/users", () => HttpResponse.json(list)),
      http.get("*/api/v1/admin/users/:userId", () => HttpResponse.json(detail)),
    );

    const users = await fetchAdminUsers({ status: "ACTIVE", sort: "createdAtDesc", page: 1, pageSize: 20 });

    expect(users.users[0]?.socialProvider).toBe("APPLE");
    expect((await fetchAdminUser(userIds.active)).socialProvider).toBe("APPLE");
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
