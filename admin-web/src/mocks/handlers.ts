import { delay, http, HttpResponse } from "msw";

import type {
  AdminAuditLogResponse,
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

export function resetMockData() {
  mockAdminSignedIn = false;
  auditStore = [];
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

export const handlers = [
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
