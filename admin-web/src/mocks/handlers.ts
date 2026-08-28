import { delay, http, HttpResponse } from "msw";

import type {
  AdminPostDetailResponse,
  AdminPostListItem,
  AdminPostListResponse,
} from "@/shared/api/contracts";

import {
  emptyPostListFixture,
  errorFixtures,
  postDetailFixtures,
} from "./fixtures";

const adminApi = "*/api/v1/admin";
let postStore = structuredClone(postDetailFixtures);

export function resetMockData() {
  postStore = structuredClone(postDetailFixtures);
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
    if (post.moderationStatus === "VALIDATING") {
      return HttpResponse.json(
        {
          errorCode: "RESOURCE_STATE_CHANGED",
          message: "이미지 처리 중인 게시물은 삭제할 수 없습니다.",
        },
        { status: 400 },
      );
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
    }

    return new HttpResponse(null, { status: 204 });
  }),
];
