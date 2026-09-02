import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { topicIds } from "@/mocks/fixtures";

import { createAdminTopic, deleteAdminTopic, fetchAdminTopic, fetchAdminTopics, updateAdminTopic } from "./topic-api";

const mutation = {
  title: "새로운 시선",
  topicDate: "2099-10-01",
  startsAt: "2099-09-30T15:00:00.000Z",
  endsAt: "2099-10-01T14:59:59.000Z",
};

describe("admin topic API", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", "http://localhost:8080/api/v1/admin");
    vi.stubEnv("NEXT_PUBLIC_API_MODE", "mock");
  });
  afterEach(() => vi.unstubAllEnvs());

  it("filters topics by phase and date", async () => {
    const response = await fetchAdminTopics({
      phase: "BEFORE_OPEN",
      dateFrom: "2099-01-01",
      dateTo: "2099-12-31",
      sort: "topicDateAsc",
      page: 1,
      pageSize: 20,
    });
    expect(response.topics.map((topic) => topic.topicId)).toEqual([topicIds.beforeOpen]);
  });

  it("creates, updates, and deletes a before-open topic", async () => {
    const created = await createAdminTopic(mutation);
    expect(created).toMatchObject({ title: mutation.title, phase: "BEFORE_OPEN" });
    await expect(updateAdminTopic(created.topicId, { ...mutation, title: "수정한 주제" })).resolves.toMatchObject({ title: "수정한 주제" });
    await expect(deleteAdminTopic(created.topicId, "편성 변경")).resolves.toBeUndefined();
    await expect(fetchAdminTopic(created.topicId)).rejects.toMatchObject({ status: 404, errorCode: "BUSINESS_ERROR" });
  });

  it("rejects changes after a topic has opened", async () => {
    await expect(updateAdminTopic(topicIds.open, mutation)).rejects.toMatchObject({
      status: 400,
      errorCode: "RESOURCE_STATE_CHANGED",
    });
  });
});
