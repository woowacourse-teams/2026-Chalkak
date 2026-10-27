import { moderationDisplay, formatInstant } from "@/features/posts/model/post-display";
import type { AdminAiReviewSummary } from "@/shared/api/contracts";
import { StatusBadge } from "@/shared/ui/status-badge";
import { aiReviewStatusDisplay, formatAuthorStatus } from "../model/ai-review";
import styles from "./ai-reviews.module.css";

export function AiReviewStatusBadge({ status }: { status: AdminAiReviewSummary["status"] }) {
  const { label, tone } = aiReviewStatusDisplay[status];
  return <StatusBadge tone={tone}>{label}</StatusBadge>;
}

// Shows the raw post and author values next to the hidden flag; no policy is derived here.
export function AiReviewFlags({ review }: { review: Pick<AdminAiReviewSummary, "visibleToAuthor" | "post" | "author"> }) {
  const { post, author } = review;
  return <div className={styles.rawNote}>
    {review.visibleToAuthor ? null : <span className={styles.flags}><StatusBadge tone="warning">앱에서 숨김</StatusBadge></span>}
    <span>게시물 {moderationDisplay[post.moderationStatus].label}({post.moderationStatus})</span>
    {post.deletedAt ? <span>게시물 삭제됨 <time dateTime={post.deletedAt}>{formatInstant(post.deletedAt)}</time></span> : null}
    <span>작성자 {formatAuthorStatus(author.status)}</span>
    {author.deletedAt ? <span>작성자 삭제됨 <time dateTime={author.deletedAt}>{formatInstant(author.deletedAt)}</time></span> : null}
  </div>;
}
