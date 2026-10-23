import { createAiPromptHandlers, resetAiPromptMocks } from "./ai-prompt-handlers";
import { createBannerHandlers, resetBannerMocks } from "./banner-handlers";
import { delay, http, HttpResponse } from "msw";
import { noticeHandlers, resetNoticeMocks } from "./notice-handlers";
import { createReminderHandlers, resetReminderMocks } from "./reminder-handlers";

import type {
  AdminAuditLogResponse,
  TopicPushSchedule,
  AdminFeedbackListResponse,
  AdminPostCounts,
  AdminPostDetailResponse,
  AdminPostListItem,
  AdminPostListResponse,
  AdminTopicDetailResponse,
  AdminTopicListResponse,
  AdminUserDetailResponse,
  AdminUserListResponse,
} from "@/shared/api/contracts";

import {
  emptyPostListFixture,
  errorFixtures,
  feedbackFixtures,
  postDetailFixtures,
  topicDetailFixtures,
  userDetailFixtures,
} from "./fixtures";

const adminApi = "*/api/v1/admin";
// Local-only demonstration state. It is never used by the real server relay.
let mockAdminSignedIn = false;
const mockAdmin = { adminId: "dddddddd-dddd-4ddd-8ddd-dddddddddddd", username: "demo-admin" };
let postStore = structuredClone(postDetailFixtures);
let userStore = structuredClone(userDetailFixtures);
let topicStore = structuredClone(topicDetailFixtures);
let auditStore: AdminAuditLogResponse[] = [];
const scheduleFixtures: TopicPushSchedule[] = [
  { scheduleId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1", sendTime: "09:00:00", createdAt: "2026-10-08T00:00:00Z", updatedAt: "2026-10-08T00:00:00Z" },
  { scheduleId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2", sendTime: "18:30:00", createdAt: "2026-10-08T00:00:00Z", updatedAt: "2026-10-08T00:00:00Z" },
];
let scheduleStore = structuredClone(scheduleFixtures);

export function resetMockData() {
  resetNoticeMocks();
  resetBannerMocks();
  resetReminderMocks();
  resetAiPromptMocks();
  mockAdminSignedIn = false;
  auditStore = [];
  scheduleStore = structuredClone(scheduleFixtures);
  postStore = structuredClone(postDetailFixtures);
  userStore = structuredClone(userDetailFixtures);
  topicStore = structuredClone(topicDetailFixtures);
}

function recordAudit(
  action: string,
  targetType: AdminAuditLogResponse["targetType"],
  targetId: string,
  reason: string | null,
  beforeState: Record<string, unknown>,
  afterState: Record<string, unknown>,
) {
  auditStore.unshift({
    auditLogId: crypto.randomUUID(),
    actorAdminId: mockAdmin.adminId,
    actorUsername: mockAdmin.username,
    action, targetType, targetId, reason, beforeState, afterState,
    occurredAt: new Date().toISOString(),
    requestId: crypto.randomUUID(),
  });
}

function updatePostCounts(before: AdminPostDetailResponse, after: AdminPostDetailResponse) {
  const update = (counts: AdminPostCounts): AdminPostCounts => {
    const next = { ...counts };
    if (!before.deletedAt) {
      const key = before.moderationStatus.toLowerCase() as keyof AdminPostCounts;
      next[key] = Math.max(0, next[key] - 1);
    }
    if (!after.deletedAt) {
      const key = after.moderationStatus.toLowerCase() as keyof AdminPostCounts;
      next[key] += 1;
    }
    return next;
  };
  const user = before.author ? userStore[before.author.userId] : undefined;
  if (user) userStore[user.userId] = { ...user, postCounts: update(user.postCounts) };
  const topic = before.topic ? topicStore[before.topic.topicId] : undefined;
  if (topic) topicStore[topic.topicId] = { ...topic, postCounts: update(topic.postCounts) };
}

function withoutSignature(user: AdminUserDetailResponse) {
  const { signature: _signature, ...summary } = user;
  void _signature;
  return summary;
}

function listFeedbacks(request: Request): AdminFeedbackListResponse {
  const params = new URL(request.url).searchParams;
  const sort = params.get("sort") ?? "createdAtDesc";
  const page = Math.max(1, Number(params.get("page") ?? 1));
  const pageSize = Math.min(100, Math.max(1, Number(params.get("pageSize") ?? 20)));
  const feedbacks = [...feedbackFixtures]
    .sort((left, right) => sort === "createdAtAsc"
      ? left.createdAt.localeCompare(right.createdAt)
      : right.createdAt.localeCompare(left.createdAt))
    .map(({ userId, ...feedback }) => {
      const user = userStore[userId];
      return {
        ...feedback,
        author: { userId, email: user.email, status: user.status, appVersion: user.appVersion },
      };
    });
  const start = (page - 1) * pageSize;
  return {
    currentPage: page,
    pageSize,
    hasNext: start + pageSize < feedbacks.length,
    feedbacks: feedbacks.slice(start, start + pageSize),
  };
}

function listUsers(request: Request): AdminUserListResponse {
  const params = new URL(request.url).searchParams;
  const email = params.get("email")?.toLowerCase();
  const status = params.get("status") || "ACTIVE";
  const sort = params.get("sort") ?? "createdAtDesc";
  const page = Math.max(1, Number(params.get("page") ?? 1));
  const pageSize = Math.min(100, Math.max(1, Number(params.get("pageSize") ?? 20)));
  const users = Object.values(userStore)
    .filter((user) => !email || user.email?.toLowerCase().includes(email))
    .filter((user) => !status || user.status === status)
    .sort((left, right) => sort === "createdAtAsc"
      ? left.createdAt.localeCompare(right.createdAt)
      : right.createdAt.localeCompare(left.createdAt))
    .map(withoutSignature);
  const start = (page - 1) * pageSize;
  return {
    currentPage: page,
    pageSize,
    hasNext: start + pageSize < users.length,
    users: users.slice(start, start + pageSize),
  };
}

function listTopics(request: Request): AdminTopicListResponse {
  const params = new URL(request.url).searchParams;
  const phase = params.get("phase");
  const dateFrom = params.get("dateFrom");
  const dateTo = params.get("dateTo");
  const sort = params.get("sort") ?? "topicDateDesc";
  const page = Math.max(1, Number(params.get("page") ?? 1));
  const pageSize = Math.min(100, Math.max(1, Number(params.get("pageSize") ?? 20)));
  const field = sort.startsWith("createdAt") ? "createdAt" : "topicDate";
  const ascending = sort.endsWith("Asc");
  const topics = Object.values(topicStore)
    .filter((topic) => !phase || topic.phase === phase)
    .filter((topic) => !dateFrom || topic.topicDate >= dateFrom)
    .filter((topic) => !dateTo || topic.topicDate <= dateTo)
    .sort((left, right) => ascending
      ? left[field].localeCompare(right[field])
      : right[field].localeCompare(left[field]));
  const start = (page - 1) * pageSize;
  return {
    currentPage: page,
    pageSize,
    hasNext: start + pageSize < topics.length,
    topics: topics.slice(start, start + pageSize),
  };
}

function isTopicMutation(value: unknown): value is Pick<AdminTopicDetailResponse, "title" | "topicDate" | "startsAt" | "endsAt"> {
  if (!value || typeof value !== "object") return false;
  const body = value as Record<string, unknown>;
  return typeof body.title === "string" && Boolean(body.title.trim())
    && typeof body.topicDate === "string" && Boolean(body.topicDate)
    && typeof body.startsAt === "string" && Boolean(body.startsAt)
    && typeof body.endsAt === "string" && body.endsAt > body.startsAt;
}

function toListItem(post: AdminPostDetailResponse): AdminPostListItem {
  return {
    postId: post.postId,
    title: post.title,
    moderationStatus: post.moderationStatus,
    author: post.author,
    topic: post.topic
      ? {
          topicId: post.topic.topicId,
          title: post.topic.title,
          topicDate: post.topic.topicDate,
        }
      : null,
    photo: post.photo
      ? {
          photoId: post.photo.photoId,
          originalImageUrl: post.photo.originalImageUrl,
          thumbnailImageUrl: post.photo.thumbnailImageUrl,
        }
      : null,
    likeCount: post.likeCount,
    createdAt: post.createdAt,
    moderatedAt: post.moderatedAt,
    deletedAt: post.deletedAt,
  };
}

function listPosts(request: Request): AdminPostListResponse {
  const params = new URL(request.url).searchParams;
  const status = params.get("status");
  const topicId = params.get("topicId");
  const topicDate = params.get("topicDate");
  const userId = params.get("userId");
  const createdAtFrom = params.get("createdAtFrom");
  const createdAtTo = params.get("createdAtTo");
  const sort = params.get("sort") ?? "createdAtDesc";
  const page = Math.max(1, Number(params.get("page") ?? 1));
  const pageSize = Math.min(
    100,
    Math.max(1, Number(params.get("pageSize") ?? 20)),
  );

  const filtered = Object.values(postStore)
    .filter((post) => !status || post.moderationStatus === status)
    .filter((post) => !topicId || post.topic?.topicId === topicId)
    .filter((post) => !topicDate || post.topic?.topicDate === topicDate)
    .filter((post) => !userId || post.author?.userId === userId)
    .filter((post) => !createdAtFrom || post.createdAt >= createdAtFrom)
    .filter((post) => !createdAtTo || post.createdAt <= createdAtTo)
    .sort((left, right) =>
      sort === "createdAtAsc"
        ? left.createdAt.localeCompare(right.createdAt)
        : right.createdAt.localeCompare(left.createdAt),
    )
    .map(toListItem);

  const start = (page - 1) * pageSize;
  return {
    currentPage: page,
    pageSize,
    hasNext: start + pageSize < filtered.length,
    posts: filtered.slice(start, start + pageSize),
  };
}

function scheduleError(message: string, status = 400) {
  return HttpResponse.json({ errorCode: "BUSINESS_ERROR", message }, { status });
}

async function saveSchedule(request: Request, scheduleId?: string) {
  const existing = scheduleId ? scheduleStore.find((row) => row.scheduleId === scheduleId) : undefined;
  if (scheduleId && !existing) return scheduleError("주제 푸시 설정을 찾을 수 없습니다.", 404);
  const body = await request.json() as { sendTime?: unknown };
  if (typeof body?.sendTime !== "string" || !/^([01]\d|2[0-3]):[0-5]\d:00$/.test(body.sendTime)) {
    return scheduleError("KST 발송 시각을 분 단위로 입력해 주세요.");
  }
  if (scheduleStore.some((row) => row.scheduleId !== scheduleId && row.sendTime === body.sendTime)) {
    return scheduleError("같은 발송 시각의 설정이 이미 존재합니다.");
  }
  if (existing?.sendTime === body.sendTime) return HttpResponse.json(existing);
  const now = new Date().toISOString();
  const result: TopicPushSchedule = { scheduleId: scheduleId ?? crypto.randomUUID(), sendTime: body.sendTime, createdAt: existing?.createdAt ?? now, updatedAt: now };
  scheduleStore = scheduleStore.filter((row) => row.scheduleId !== result.scheduleId).concat(result);
  recordAudit(existing ? "TOPIC_PUSH_SCHEDULE_UPDATED" : "TOPIC_PUSH_SCHEDULE_CREATED", "TOPIC_PUSH_SCHEDULE", result.scheduleId, null, existing ? { sendTime: existing.sendTime } : {}, { sendTime: result.sendTime });
  return HttpResponse.json(result, { status: existing ? 200 : 201 });
}

export const handlers = [
  ...noticeHandlers,
  ...createBannerHandlers((action, id, before, after) => recordAudit(action, "BANNER", id, null, before, after)),
  ...createReminderHandlers((action, id, before, after) => recordAudit(action, "REMINDER", id, null, before, after)),
  ...createAiPromptHandlers(
    (action, id, before, after) => recordAudit(action, "AI_PROMPT_VERSION", id, null, before, after),
    (postId) => { const post = postStore[postId]; return post?.moderationStatus === "APPROVED" && !post.deletedAt; },
  ),
  http.get(adminApi + "/topic-push-schedules", () => HttpResponse.json({ schedules: [...scheduleStore].sort((left, right) => left.sendTime.localeCompare(right.sendTime)) })),
  http.post(adminApi + "/topic-push-schedules", ({ request }) => saveSchedule(request)),
  http.put(adminApi + "/topic-push-schedules/:scheduleId", ({ request, params }) => saveSchedule(request, String(params.scheduleId))),
  http.delete(adminApi + "/topic-push-schedules/:scheduleId", async ({ request, params }) => {
    if (await request.text()) return scheduleError("삭제에는 요청 본문을 보내지 않습니다.");
    const existing = scheduleStore.find((row) => row.scheduleId === params.scheduleId);
    if (!existing) return scheduleError("주제 푸시 설정을 찾을 수 없습니다.", 404);
    scheduleStore = scheduleStore.filter((row) => row.scheduleId !== existing.scheduleId);
    recordAudit("TOPIC_PUSH_SCHEDULE_DELETED", "TOPIC_PUSH_SCHEDULE", existing.scheduleId, null, { sendTime: existing.sendTime }, {});
    return new HttpResponse(null, { status: 204 });
  }),
  http.get(adminApi + "/audit-logs", ({ request }) => {
    const params = new URL(request.url).searchParams;
    const page = Math.max(1, Number(params.get("page") ?? 1));
    const pageSize = Math.min(100, Math.max(1, Number(params.get("pageSize") ?? 20)));
    const rows = auditStore
      .filter((log) => !params.get("action") || log.action === params.get("action"))
      .filter((log) => !params.get("targetType") || log.targetType === params.get("targetType"));
    const start = (page - 1) * pageSize;
    return HttpResponse.json({ currentPage: page, pageSize, hasNext: start + pageSize < rows.length, auditLogs: rows.slice(start, start + pageSize) });
  }),
  http.post(adminApi + "/auth/login", async ({ request }) => {
    const body = await request.json() as { username?: string; password?: string };
    if (body.username !== "demo-admin" || body.password !== "demo-only") {
      return HttpResponse.json({ errorCode: "UNAUTHORIZED", message: "아이디 또는 비밀번호가 올바르지 않습니다." }, { status: 401 });
    }
    mockAdminSignedIn = true;
    return HttpResponse.json({ ...mockAdmin, expiresIn: 3600 });
  }),
  http.get(adminApi + "/auth/me", () => mockAdminSignedIn
    ? HttpResponse.json(mockAdmin)
    : HttpResponse.json({ errorCode: "UNAUTHORIZED", message: "로그인이 필요합니다." }, { status: 401 })),
  http.post(adminApi + "/auth/logout", () => {
    mockAdminSignedIn = false;
    return new HttpResponse(null, { status: 204 });
  }),
  http.get(adminApi + "/feedbacks", ({ request }) => HttpResponse.json(listFeedbacks(request))),
  http.get(adminApi + "/users", ({ request }) => HttpResponse.json(listUsers(request))),
  http.get(adminApi + "/users/:userId", ({ params }) => {
    const user = userStore[String(params.userId)];
    return user
      ? HttpResponse.json(user)
      : HttpResponse.json({ errorCode: "BUSINESS_ERROR", message: "사용자를 찾을 수 없습니다." }, { status: 404 });
  }),
  http.patch(adminApi + "/users/:userId/status", async ({ params, request }) => {
    const userId = String(params.userId);
    const user = userStore[userId];
    if (!user) {
      return HttpResponse.json({ errorCode: "BUSINESS_ERROR", message: "사용자를 찾을 수 없습니다." }, { status: 404 });
    }
    const body = await request.json() as { status?: "ACTIVE" | "BANNED"; reason?: string };
    if (user.status === "WITHDRAWN" || user.status === body.status) {
      return HttpResponse.json({ errorCode: "RESOURCE_STATE_CHANGED", message: "현재 상태에서는 변경할 수 없습니다." }, { status: 400 });
    }
    if ((body.status !== "ACTIVE" && body.status !== "BANNED") || !body.reason?.trim()) {
      return HttpResponse.json(errorFixtures.badRequest, { status: 400 });
    }
    userStore[userId] = { ...user, status: body.status, updatedAt: new Date().toISOString() };
    recordAudit(body.status === "BANNED" ? "USER_BANNED" : "USER_UNBANNED", "USER", userId, body.reason.trim(), { status: user.status }, { status: body.status });
    return HttpResponse.json({ userId, status: body.status });
  }),
  http.get(adminApi + "/topics", ({ request }) => HttpResponse.json(listTopics(request))),
  http.get(adminApi + "/topics/:topicId", ({ params }) => {
    const topic = topicStore[String(params.topicId)];
    return topic
      ? HttpResponse.json(topic)
      : HttpResponse.json({ errorCode: "BUSINESS_ERROR", message: "주제를 찾을 수 없습니다." }, { status: 404 });
  }),
  http.post(adminApi + "/topics", async ({ request }) => {
    const body = await request.json();
    if (!isTopicMutation(body)) return HttpResponse.json(errorFixtures.badRequest, { status: 400 });
    const topicId = crypto.randomUUID();
    const now = new Date().toISOString();
    const topic: AdminTopicDetailResponse = {
      topicId,
      ...body,
      phase: "BEFORE_OPEN",
      postCounts: { pending: 0, approved: 0, rejected: 0 },
      createdAt: now,
      updatedAt: now,
    };
    topicStore[topicId] = topic;
    recordAudit("TOPIC_CREATED", "TOPIC", topicId, null, {}, { title: topic.title, topicDate: topic.topicDate });
    return HttpResponse.json(topic, { status: 201 });
  }),
  http.put(adminApi + "/topics/:topicId", async ({ params, request }) => {
    const topicId = String(params.topicId);
    const topic = topicStore[topicId];
    if (!topic) return HttpResponse.json({ errorCode: "BUSINESS_ERROR", message: "주제를 찾을 수 없습니다." }, { status: 404 });
    if (topic.phase !== "BEFORE_OPEN") return HttpResponse.json({ errorCode: "RESOURCE_STATE_CHANGED", message: "공개 전 주제만 변경할 수 있습니다." }, { status: 400 });
    const body = await request.json();
    if (!isTopicMutation(body)) return HttpResponse.json(errorFixtures.badRequest, { status: 400 });
    topicStore[topicId] = { ...topic, ...body, updatedAt: new Date().toISOString() };
    recordAudit("TOPIC_UPDATED", "TOPIC", topicId, null, { title: topic.title, topicDate: topic.topicDate }, { title: body.title, topicDate: body.topicDate });
    return HttpResponse.json(topicStore[topicId]);
  }),
  http.delete(adminApi + "/topics/:topicId", async ({ params, request }) => {
    const topicId = String(params.topicId);
    const topic = topicStore[topicId];
    if (!topic) return HttpResponse.json({ errorCode: "BUSINESS_ERROR", message: "주제를 찾을 수 없습니다." }, { status: 404 });
    if (topic.phase !== "BEFORE_OPEN") return HttpResponse.json({ errorCode: "RESOURCE_STATE_CHANGED", message: "공개 전 주제만 삭제할 수 있습니다." }, { status: 400 });
    const body = await request.json() as { reason?: string };
    if (!body.reason?.trim()) return HttpResponse.json(errorFixtures.badRequest, { status: 400 });
    delete topicStore[topicId];
    recordAudit("TOPIC_DELETED", "TOPIC", topicId, body.reason.trim(), { deletedAt: null }, { deletedAt: new Date().toISOString() });
    return new HttpResponse(null, { status: 204 });
  }),
  http.get(adminApi + "/posts", async ({ request }) => {
    const scenario = new URL(request.url).searchParams.get("scenario");

    if (scenario === "delay") {
      await delay(800);
    }
    if (scenario === "empty") {
      return HttpResponse.json(emptyPostListFixture);
    }
    if (scenario === "bad-request") {
      return HttpResponse.json(errorFixtures.badRequest, { status: 400 });
    }
    if (scenario === "forbidden") {
      return HttpResponse.json(errorFixtures.forbidden, { status: 403 });
    }
    if (scenario === "not-found") {
      return HttpResponse.json(errorFixtures.notFound, { status: 404 });
    }

    return HttpResponse.json(listPosts(request));
  }),
  http.get(adminApi + "/posts/:postId", ({ params }) => {
    const post = postStore[String(params.postId)];
    if (!post) {
      return HttpResponse.json(errorFixtures.notFound, { status: 404 });
    }
    return HttpResponse.json(post);
  }),
  http.put(
    adminApi + "/posts/:postId/moderation",
    async ({ params, request }) => {
      const postId = String(params.postId);
      const post = postStore[postId];
      if (!post) {
        return HttpResponse.json(errorFixtures.notFound, { status: 404 });
      }
      if (post.moderationStatus !== "PENDING" || post.deletedAt) {
        return HttpResponse.json(
          {
            errorCode: "RESOURCE_STATE_CHANGED",
            message: "대기 중인 게시물만 검수할 수 있습니다.",
          },
          { status: 400 },
        );
      }

      const body = (await request.json()) as {
        status?: "APPROVED" | "REJECTED";
        rejectionReason?: string;
      };
      if (
        (body.status !== "APPROVED" && body.status !== "REJECTED") ||
        (body.status === "REJECTED" && !body.rejectionReason?.trim())
      ) {
        return HttpResponse.json(errorFixtures.badRequest, { status: 400 });
      }

      const moderatedAt = new Date().toISOString();
      const moderatedBy = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee";
      postStore[postId] = {
        ...post,
        moderationStatus: body.status,
        moderatedAt,
        moderatedBy,
        rejectionReason:
          body.status === "REJECTED" ? body.rejectionReason?.trim() ?? null : null,
        updatedAt: moderatedAt,
      };
      updatePostCounts(post, postStore[postId]);
      recordAudit(body.status === "APPROVED" ? "POST_APPROVED" : "POST_REJECTED", "POST", postId,
        body.status === "REJECTED" ? body.rejectionReason?.trim() ?? null : null,
        { moderationStatus: post.moderationStatus }, { moderationStatus: body.status });

      return HttpResponse.json({
        postId,
        moderationStatus: body.status,
        moderatedBy,
        moderatedAt,
        rejectionReason: postStore[postId].rejectionReason,
      });
    },
  ),
  http.delete(adminApi + "/posts/:postId", async ({ params, request }) => {
    const postId = String(params.postId);
    const post = postStore[postId];
    if (!post) {
      return HttpResponse.json(errorFixtures.notFound, { status: 404 });
    }
    const body = (await request.json()) as { reason?: string };
    if (!body.reason?.trim()) {
      return HttpResponse.json(errorFixtures.badRequest, { status: 400 });
    }
    if (!post.deletedAt) {
      const deletedAt = new Date().toISOString();
      postStore[postId] = {
        ...post,
        deletedAt,
        updatedAt: deletedAt,
        photo: post.photo ? { ...post.photo, deletedAt } : null,
      };
      updatePostCounts(post, postStore[postId]);
      recordAudit("POST_DELETED", "POST", postId, body.reason.trim(), { deletedAt: null }, { deletedAt });
    }

    return new HttpResponse(null, { status: 204 });
  }),
];
