import type {
  AdminPostDetailResponse,
  AdminPostListResponse,
  ApiErrorResponse,
} from "@/shared/api/contracts";

export const postIds = {
  validating: "00000000-0000-4000-8000-000000000000",
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
  topicId: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
  title: "여름의 한 장면",
  topicDate: "2026-08-28",
} as const;

export const postListFixture = {
  currentPage: 1,
  pageSize: 20,
  hasNext: false,
  posts: [
    {
      postId: postIds.validating,
      title: "이미지 처리 중",
      moderationStatus: "VALIDATING",
      author,
      topic,
      photo: {
        photoId: "c0000000-0000-4000-8000-000000000000",
        originalImageUrl: "https://images.unsplash.com/photo-1497250681960-ef046c08a56e",
        thumbnailImageUrl: null,
      },
      likeCount: 0,
      createdAt: "2026-08-28T05:00:00Z",
      moderatedAt: null,
      deletedAt: null,
    },
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
          status: "COMPLETED",
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
    errorCode: "INVALID_REQUEST",
    message: "조회 조건이 올바르지 않습니다.",
  },
  forbidden: {
    errorCode: "ADMIN_FORBIDDEN",
    message: "관리자 API에 접근할 수 없습니다.",
  },
  notFound: {
    errorCode: "POST_NOT_FOUND",
    message: "게시물을 찾을 수 없습니다.",
  },
} satisfies Record<string, ApiErrorResponse>;
