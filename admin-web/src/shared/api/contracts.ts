export type Instant = string;
export type LocalDate = string;

export interface ApiErrorResponse {
  errorCode: string;
  message: string;
}

export type ModerationStatus =
  | "PENDING"
  | "APPROVED"
  | "REJECTED";

export type UserStatus = "ACTIVE" | "BANNED" | "WITHDRAWN";
export type TopicStatus = "BEFORE_OPEN" | "OPEN" | "CLOSED";
export type SocialProvider = "GOOGLE" | "KAKAO" | "APPLE";
export type ImageUploadStatus =
  | "ISSUED"
  | "READY"
  | "REJECTED";

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
  actorAdminId: string;
  actorUsername: string;
  action: string;
  targetType: "POST" | "USER" | "TOPIC" | "TOPIC_PUSH_SCHEDULE" | "NOTICE" | "BANNER" | "REMINDER" | "AI_PROMPT_VERSION";
  targetId: string;
  reason: string | null;
  beforeState: Record<string, unknown>;
  afterState: Record<string, unknown>;
  occurredAt: Instant;
  requestId: string;
}

export interface AdminAuditLogListResponse {
  currentPage: number;
  pageSize: number;
  hasNext: boolean;
  auditLogs: AdminAuditLogResponse[];
}

export interface AdminFeedbackAuthor {
  userId: string;
  email: string | null;
  status: UserStatus;
  appVersion: string | null;
}

export interface AdminFeedbackListItem {
  feedbackId: string;
  content: string;
  createdAt: Instant;
  author: AdminFeedbackAuthor;
}

export interface AdminFeedbackListResponse {
  currentPage: number;
  pageSize: number;
  hasNext: boolean;
  feedbacks: AdminFeedbackListItem[];
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

export interface TopicPushSchedule {
  scheduleId: string;
  /** KST wall-clock time; never convert using the browser time zone. */
  sendTime: string;
  createdAt: Instant;
  updatedAt: Instant;
}

export interface TopicPushScheduleListResponse {
  schedules: TopicPushSchedule[];
}

export interface AdminNotice {
  noticeId: string;
  title: string;
  body: string;
  scheduledAt: Instant;
  status: "SCHEDULED" | "DISTRIBUTED";
  distributedAt: Instant | null;
  deletedAt: Instant | null;
  createdAt: Instant;
  updatedAt: Instant;
}

export interface AdminNoticeListResponse {
  currentPage: number;
  pageSize: number;
  hasNext: boolean;
  notices: AdminNotice[];
}

export type AdminNoticeInput = Pick<AdminNotice, "title" | "body" | "scheduledAt">;

export type AdminReminderRepeat = "ONCE" | "DAILY";
export type AdminReminderTarget = "ALL" | "TOPIC_PARTICIPANTS" | "TOPIC_NON_PARTICIPANTS";

export interface AdminReminder {
  reminderId: string;
  title: string;
  body: string;
  repeat: AdminReminderRepeat;
  target: AdminReminderTarget;
  scheduledAt: Instant;
  createdAt: Instant;
  updatedAt: Instant;
}

export interface AdminReminderListResponse {
  currentPage: number;
  pageSize: number;
  hasNext: boolean;
  reminders: AdminReminder[];
}

export type AdminReminderInput = Pick<AdminReminder, "title" | "body" | "repeat" | "target" | "scheduledAt">;

export type AiPromptType = "PHOTO_REVIEW";
export type AiImageDetail = "LOW" | "HIGH" | "AUTO";
export type AiReasoningEffort = "LOW" | "MEDIUM" | "HIGH";

export interface AiPromptParametersInput {
  imageDetail?: AiImageDetail;
  maxOutputTokens?: number;
  reasoningEffort?: AiReasoningEffort;
  distinctivePerspectiveThreshold?: number;
}

export interface AiPromptVersionInput {
  type: AiPromptType;
  systemPrompt: string;
  userPromptTemplate: string;
  model: string;
  parameters?: AiPromptParametersInput;
}

export interface AiPromptPreviewRequest extends AiPromptVersionInput {
  postId: string;
}

export interface AiPromptVersionCreateRequest extends AiPromptVersionInput {
  memo?: string;
}

export type AiPromptFailureType =
  | "TIMEOUT" | "RATE_LIMITED" | "QUOTA_EXCEEDED" | "UPSTREAM_ERROR"
  | "BAD_REQUEST" | "REFUSED" | "INCOMPLETE" | "INVALID_OUTPUT";

export interface AiPromptPreviewFeedback {
  summary: string;
  strength: string;
  weakness: string;
  suggestion: string;
}

export interface AiPromptPreviewResponse {
  succeeded: boolean;
  failureType: AiPromptFailureType | null;
  retryable: boolean | null;
  result: {
    overallScore: number;
    criteria: { key: string; label: string; score: number }[];
    distinctivePerspective: boolean;
    // 백엔드 #731 배포 전 응답은 문자열이다. 배포 후 `| string`과 화면의 문자열 분기를 제거한다.
    feedback: AiPromptPreviewFeedback | string;
  } | null;
  // Raw model scores, shown for tuning the distinctive-perspective threshold.
  output: { compositionScore: number; themeExpressionScore: number } | null;
  // A null token count means the model did not report it.
  usage: {
    inputTokens: number | null;
    cachedInputTokens: number | null;
    outputTokens: number | null;
    reasoningTokens: number | null;
  } | null;
  latencyMs: number | null;
  responseId: string | null;
}

export interface AiPromptVersionSummary {
  promptVersionId: string;
  type: AiPromptType;
  version: number;
  model: string;
  outputSchemaVersion: string;
  memo: string | null;
  createdByAdminId: string;
  createdAt: Instant;
}

export interface AiPromptVersionListResponse {
  currentPage: number;
  pageSize: number;
  hasNext: boolean;
  promptVersions: AiPromptVersionSummary[];
}

export interface AiPromptVersionDetail extends AiPromptVersionSummary {
  systemPrompt: string;
  userPromptTemplate: string;
  parameters: {
    imageDetail: AiImageDetail;
    maxOutputTokens: number;
    reasoningEffort: AiReasoningEffort | null;
    distinctivePerspectiveThreshold: number;
  };
}

export type AiReviewStatus = "QUEUED" | "PROCESSING" | "COMPLETED" | "FAILED";

export interface AiReviewUsage {
  inputTokens: number | null;
  cachedInputTokens: number | null;
  outputTokens: number | null;
  reasoningTokens: number | null;
}

export interface AiReviewPromptVersion {
  promptVersionId: string;
  version: number;
  model: string;
}

export interface AiReviewAuthor {
  userId: string;
  email: string | null;
  status: UserStatus;
  deletedAt: Instant | null;
}

export interface AdminAiReviewSummary {
  aiReviewId: string;
  status: AiReviewStatus;
  // Kept after a failed attempt, so a QUEUED review waiting for a retry can still have one.
  errorCode: string | null;
  requestCount: number;
  overallScore: number | null;
  promptVersion: AiReviewPromptVersion;
  post: {
    postId: string;
    thumbnailUrl: string | null;
    moderationStatus: ModerationStatus;
    deletedAt: Instant | null;
  };
  author: AiReviewAuthor;
  usage: AiReviewUsage;
  latencyMs: number | null;
  requestedAt: Instant;
  lastRequestedAt: Instant;
  completedAt: Instant | null;
  visibleToAuthor: boolean;
}

export interface AdminAiReviewListResponse {
  currentPage: number;
  pageSize: number;
  hasNext: boolean;
  aiReviews: AdminAiReviewSummary[];
}

export interface AdminAiReviewDetail extends Omit<AdminAiReviewSummary, "post"> {
  post: AdminAiReviewSummary["post"] & { imageUrl: string | null };
  startedAt: Instant | null;
  attempt: number;
  openaiResponseId: string | null;
  result: {
    overallScore: number;
    criteria: { key: string; label: string; score: number }[];
    distinctivePerspective: boolean;
    feedback: AiPromptPreviewFeedback;
  } | null;
  // Raw model output; only completed reviews have it.
  output: Record<string, unknown> | null;
}
