import type {
  AdminPostDetailResponse,
  AdminPostListResponse,
  AdminTopicDetailResponse,
  AdminUserDetailResponse,
  ApiErrorResponse,
} from "@/shared/api/contracts";

export const postIds = {
  pending: "11111111-1111-4111-8111-111111111111",
  approved: "22222222-2222-4222-8222-222222222222",
  rejected: "33333333-3333-4333-8333-333333333333",
  deleted: "44444444-4444-4444-8444-444444444444",
} as const;

const author = {
  userId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  email: "creator@example.com",
  status: "ACTIVE",
  deletedAt: null,
} as const;

const topic = {
  topicId: "bcbcbcbc-bcbc-4bcb-8bcb-bcbcbcbcbcbc",
  title: "오늘의 빛",
  topicDate: "2026-08-28",
} as const;

export const postListFixture = {
  currentPage: 1,
  pageSize: 20,
  hasNext: false,
  posts: [
    {
      postId: postIds.pending,
      title: "한강의 노을",
      moderationStatus: "PENDING",
      author,
      topic,
      photo: {
        photoId: "c1111111-1111-4111-8111-111111111111",
        originalImageUrl: "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee",
        thumbnailImageUrl:
          "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?w=640",
      },
      likeCount: 12,
      createdAt: "2026-08-28T04:10:00Z",
      moderatedAt: null,
      deletedAt: null,
    },
    {
      postId: postIds.approved,
      title: "초록의 오후",
      moderationStatus: "APPROVED",
      author,
      topic,
      photo: {
        photoId: "c2222222-2222-4222-8222-222222222222",
        originalImageUrl: "https://images.unsplash.com/photo-1441974231531-c6227db76b6e",
        thumbnailImageUrl:
          "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=640",
      },
      likeCount: 34,
      createdAt: "2026-08-27T11:20:00Z",
      moderatedAt: "2026-08-27T12:00:00Z",
      deletedAt: null,
    },
    {
      postId: postIds.rejected,
      title: "흐린 골목",
      moderationStatus: "REJECTED",
      author,
      topic,
      photo: {
        photoId: "c3333333-3333-4333-8333-333333333333",
        originalImageUrl: "https://images.unsplash.com/photo-1477959858617-67f85cf4f1df",
        thumbnailImageUrl:
          "https://images.unsplash.com/photo-1477959858617-67f85cf4f1df?w=640",
      },
      likeCount: 2,
      createdAt: "2026-08-26T08:30:00Z",
      moderatedAt: "2026-08-26T09:00:00Z",
      deletedAt: null,
    },
    {
      postId: postIds.deleted,
      title: "삭제된 게시물",
      moderationStatus: "APPROVED",
      author,
      topic,
      photo: {
        photoId: "c4444444-4444-4444-8444-444444444444",
        originalImageUrl: "https://images.unsplash.com/photo-1470770841072-f978cf4d019e",
        thumbnailImageUrl:
          "https://images.unsplash.com/photo-1470770841072-f978cf4d019e?w=640",
      },
      likeCount: 0,
      createdAt: "2026-08-25T01:20:00Z",
      moderatedAt: "2026-08-25T02:00:00Z",
      deletedAt: "2026-08-25T03:00:00Z",
    },
  ],
} satisfies AdminPostListResponse;

export const emptyPostListFixture = {
  currentPage: 1,
  pageSize: 20,
  hasNext: false,
  posts: [],
} satisfies AdminPostListResponse;

export const postDetailFixtures: Record<string, AdminPostDetailResponse> =
  Object.fromEntries(
    postListFixture.posts.map((post) => [
      post.postId,
      {
        ...post,
        topic: post.topic
          ? {
              ...post.topic,
              startsAt: "2026-08-28T00:00:00Z",
              endsAt: "2026-08-28T23:59:59Z",
              deletedAt: null,
            }
          : null,
        photo: post.photo
          ? {
              ...post.photo,
              metadata: {
                width: 1920,
                height: 1280,
                byteSize: 845_120,
              },
              createdAt: post.createdAt,
              updatedAt: post.moderatedAt ?? post.createdAt,
              deletedAt: post.deletedAt,
            }
          : null,
        imageUpload: {
          uploadId: "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
          status: "READY",
          rejectionReason: null,
          createdAt: post.createdAt,
          updatedAt: post.createdAt,
        },
        updatedAt: post.moderatedAt ?? post.createdAt,
        moderatedBy: post.moderatedAt
          ? "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
          : null,
        rejectionReason:
          post.moderationStatus === "REJECTED" ? "운영 정책 위반" : null,
      },
    ]),
  );

export const errorFixtures = {
  badRequest: {
    errorCode: "BUSINESS_ERROR",
    message: "조회 조건이 올바르지 않습니다.",
  },
  forbidden: {
    errorCode: "FORBIDDEN",
    message: "관리자 API에 접근할 수 없습니다.",
  },
  notFound: {
    errorCode: "BUSINESS_ERROR",
    message: "게시물을 찾을 수 없습니다.",
  },
} satisfies Record<string, ApiErrorResponse>;

export const userIds = {
  active: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  banned: "abababab-abab-4bab-8bab-abababababab",
  withdrawn: "acacacac-acac-4cac-8cac-acacacacacac",
} as const;

export const userDetailFixtures: Record<string, AdminUserDetailResponse> = {
  [userIds.active]: {
    userId: userIds.active,
    email: "creator@example.com",
    status: "ACTIVE",
    appVersion: "1.4.0",
    socialProvider: "GOOGLE",
    postCounts: { pending: 2, approved: 10, rejected: 1 },
    signature: {
      originalImageUrl: null,
      thumbnailImageUrl: null,
    },
    createdAt: "2026-07-02T04:20:00Z",
    updatedAt: "2026-08-27T14:10:00Z",
    deletedAt: null,
  },
  [userIds.banned]: {
    userId: userIds.banned,
    email: "paused@example.com",
    status: "BANNED",
    appVersion: "1.3.2",
    socialProvider: "KAKAO",
    postCounts: { pending: 0, approved: 3, rejected: 2 },
    signature: {
      originalImageUrl: null,
      thumbnailImageUrl: null,
    },
    createdAt: "2026-06-11T02:00:00Z",
    updatedAt: "2026-08-20T09:15:00Z",
    deletedAt: null,
  },
  [userIds.withdrawn]: {
    userId: userIds.withdrawn,
    email: null,
    status: "WITHDRAWN",
    appVersion: null,
    socialProvider: null,
    postCounts: { pending: 0, approved: 0, rejected: 0 },
    signature: {
      originalImageUrl: null,
      thumbnailImageUrl: null,
    },
    createdAt: "2026-05-01T00:00:00Z",
    updatedAt: "2026-08-01T00:00:00Z",
    deletedAt: "2026-08-01T00:00:00Z",
  },
};

export const topicIds = {
  beforeOpen: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
  open: "bcbcbcbc-bcbc-4bcb-8bcb-bcbcbcbcbcbc",
  closed: "bdbdbdbd-bdbd-4dbd-8dbd-bdbdbdbdbdbd",
} as const;

export const topicDetailFixtures: Record<string, AdminTopicDetailResponse> = {
  [topicIds.beforeOpen]: {
    topicId: topicIds.beforeOpen,
    title: "가을을 기다리는 마음",
    topicDate: "2099-09-01",
    startsAt: "2099-08-31T15:00:00Z",
    endsAt: "2099-09-01T14:59:59Z",
    phase: "BEFORE_OPEN",
    postCounts: { pending: 0, approved: 0, rejected: 0 },
    createdAt: "2026-08-20T03:00:00Z",
    updatedAt: "2026-08-20T03:00:00Z",
  },
  [topicIds.open]: {
    topicId: topicIds.open,
    title: "오늘의 빛",
    topicDate: "2026-08-28",
    startsAt: "2026-08-27T15:00:00Z",
    endsAt: "2099-08-28T14:59:59Z",
    phase: "OPEN",
    postCounts: { pending: 4, approved: 11, rejected: 2 },
    createdAt: "2026-08-01T02:00:00Z",
    updatedAt: "2026-08-27T01:00:00Z",
  },
  [topicIds.closed]: {
    topicId: topicIds.closed,
    title: "비 오는 거리",
    topicDate: "2026-07-10",
    startsAt: "2026-07-09T15:00:00Z",
    endsAt: "2026-07-10T14:59:59Z",
    phase: "CLOSED",
    postCounts: { pending: 0, approved: 19, rejected: 4 },
    createdAt: "2026-07-01T01:00:00Z",
    updatedAt: "2026-07-10T15:00:00Z",
  },
};
