export type Instant = string;
export type LocalDate = string;

export interface ApiErrorResponse {
  errorCode: string;
  message: string;
}

export type ModerationStatus =
  | "VALIDATING"
  | "PENDING"
  | "APPROVED"
  | "REJECTED";

export type UserStatus = "ACTIVE" | "BANNED" | "WITHDRAWN";
export type TopicStatus = "BEFORE_OPEN" | "OPEN" | "CLOSED";
export type SocialProvider = "GOOGLE" | "KAKAO";
export type ImageUploadStatus =
  | "PENDING"
  | "PROCESSING"
  | "COMPLETED"
  | "FAILED";

export interface AdminPostAuthor {
  userId: string;
  email: string | null;
  status: UserStatus;
  deletedAt: Instant | null;
}

export interface AdminPostTopicSummary {
  topicId: string;
  title: string;
  topicDate: LocalDate;
}

export interface AdminPostPhotoSummary {
  photoId: string;
  originalImageUrl: string;
  thumbnailImageUrl: string | null;
}

export interface AdminPostListItem {
  postId: string;
  title: string | null;
  moderationStatus: ModerationStatus;
  author: AdminPostAuthor | null;
  topic: AdminPostTopicSummary | null;
  photo: AdminPostPhotoSummary | null;
  likeCount: number;
  createdAt: Instant;
  moderatedAt: Instant | null;
  deletedAt: Instant | null;
}

export interface AdminPostListResponse {
  currentPage: number;
  pageSize: number;
  hasNext: boolean;
  posts: AdminPostListItem[];
}

export interface AdminPostTopicDetail extends AdminPostTopicSummary {
  startsAt: Instant;
  endsAt: Instant;
  deletedAt: Instant | null;
}

export interface AdminPhotoMetadata {
  width?: number;
  height?: number;
  byteSize?: number;
}

export interface AdminPostPhotoDetail extends AdminPostPhotoSummary {
  metadata: AdminPhotoMetadata;
  createdAt: Instant;
  updatedAt: Instant;
  deletedAt: Instant | null;
}

export interface AdminPostImageUpload {
  uploadId: string;
  status: ImageUploadStatus;
  rejectionReason: string | null;
  createdAt: Instant;
  updatedAt: Instant;
}

export interface AdminPostDetailResponse {
  postId: string;
  title: string | null;
  moderationStatus: ModerationStatus;
  author: AdminPostAuthor | null;
  topic: AdminPostTopicDetail | null;
  photo: AdminPostPhotoDetail | null;
  imageUpload: AdminPostImageUpload | null;
  likeCount: number;
  createdAt: Instant;
  updatedAt: Instant;
  moderatedAt: Instant | null;
  moderatedBy: string | null;
  rejectionReason: string | null;
  deletedAt: Instant | null;
}

export interface AdminPostModerationResponse {
  postId: string;
  moderationStatus: "APPROVED" | "REJECTED";
  moderatedBy: string;
  moderatedAt: Instant;
  rejectionReason: string | null;
}

export interface AdminPostCounts {
  total: number;
  validating: number;
  pending: number;
  approved: number;
  rejected: number;
}

export interface AdminUserListItem {
  userId: string;
  email: string | null;
  status: UserStatus;
  appVersion: string | null;
  socialProvider: SocialProvider | null;
  postCounts: AdminPostCounts;
  createdAt: Instant;
  updatedAt: Instant;
  deletedAt: Instant | null;
}

export interface AdminUserListResponse {
  currentPage: number;
  pageSize: number;
  hasNext: boolean;
  users: AdminUserListItem[];
}

export interface AdminUserDetailResponse extends AdminUserListItem {
  signature: {
    originalImageUrl: string | null;
    thumbnailImageUrl: string | null;
  };
}

export interface AdminUserStatusResponse {
  userId: string;
  status: "ACTIVE" | "BANNED";
}

export interface AdminTopicDetailResponse {
  topicId: string;
  title: string;
  topicDate: LocalDate;
  startsAt: Instant;
  endsAt: Instant;
  phase: TopicStatus;
  postCounts: AdminPostCounts;
  createdAt: Instant;
  updatedAt: Instant;
}

export interface AdminTopicListResponse {
  currentPage: number;
  pageSize: number;
  hasNext: boolean;
  topics: AdminTopicDetailResponse[];
}

export interface AdminDashboardResponse {
  pendingPostCount: number;
  failedImageCount: number;
  activeUserCount: number;
  notificationFailureCount: number;
  generatedAt: Instant;
}

export interface AdminAuditLogResponse {
  auditLogId: string;
  action: string;
  targetType: string;
  targetId: string;
  actorId: string;
  reason: string | null;
  createdAt: Instant;
}

export type PushStatus = "PENDING" | "SENDING" | "SUCCEEDED" | "FAILED";

export interface AdminPushResponse {
  pushId: string;
  title: string;
  body: string;
  targetType: "ALL" | "USER";
  targetUserId: string | null;
  status: PushStatus;
  requestedAt: Instant;
  completedAt: Instant | null;
}
