"use client";
import Link from "next/link";
import { PreviewFeedback } from "@/features/ai-prompts/ui/ai-prompt-preview-result";
import { formatLatency, formatVersion } from "@/features/ai-prompts/model/ai-prompt";
import { formatInstant } from "@/features/posts/model/post-display";
import { PostThumbnail } from "@/features/posts/ui/post-thumbnail";
import { ApiError } from "@/shared/api/errors";
import { isUuid } from "@/shared/api/uuid";
import { ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import { useAiReview } from "../api/ai-review-hooks";
import { formatCount } from "../model/ai-review";
import { AiReviewFlags, AiReviewStatusBadge } from "./ai-review-flags";
import common from "@/shared/ui/management.module.css";
import promptStyles from "@/features/ai-prompts/ui/ai-prompts.module.css";
import styles from "./ai-reviews.module.css";

function Time({ value }: { value: string | null }) {
  return value ? <time dateTime={value}>{formatInstant(value)}</time> : <>-</>;
}

export function AiReviewDetailScreen({ aiReviewId }: { aiReviewId: string }) {
  const valid = isUuid(aiReviewId);
  const query = useAiReview(valid ? aiReviewId : null);
  const review = query.data;
  const notFound = !valid || (query.error instanceof ApiError && query.error.status === 404);
  return <div className={common.page}>
    <Link className={common.back} href="/ai-reviews">← 목록으로</Link>
    {valid && query.isPending ? <LoadingSkeleton rows={4} /> : null}
    {notFound
      ? <ErrorState title="AI 리뷰를 찾을 수 없습니다" description="존재하지 않는 AI 리뷰입니다. 목록에서 다시 선택해 주세요." />
      : query.isError ? <ErrorState description={query.error instanceof ApiError ? query.error.message : "AI 리뷰를 불러오지 못했습니다."} onRetry={() => { void query.refetch(); }} /> : null}
    {!query.isError && review ? <>
      <div className={common.toolbar}>
        <h2 className={common.pageTitle}>AI 리뷰 상세</h2>
        <AiReviewStatusBadge status={review.status} />
      </div>
      <section className={common.panel}>
        <h3>상태</h3>
        <dl className={common.meta}>
          <div><dt>오류 코드</dt><dd>{review.errorCode ?? "-"}</dd></div>
          <div><dt>AI 리뷰 ID</dt><dd>{review.aiReviewId}</dd></div>
          <div><dt>작성자 이메일</dt><dd>{review.author.email ?? "-"}</dd></div>
          <div><dt>프롬프트 버전</dt><dd>{formatVersion(review.promptVersion.version)} · {review.promptVersion.model}</dd></div>
        </dl>
        <AiReviewFlags review={review} />
        <div className={styles.links}>
          <Link href={`/posts/${review.post.postId}`}>게시물 상세</Link>
          <Link href={`/users/${review.author.userId}`}>작성자 상세</Link>
          <Link href={`/ai-prompts/${review.promptVersion.promptVersionId}`}>프롬프트 버전 상세</Link>
        </div>
      </section>
      <section className={common.panel}>
        <h3>원본 사진</h3>
        <div className={styles.imageBox}><PostThumbnail alt="원본 사진" detail src={review.post.imageUrl ?? review.post.thumbnailUrl} /></div>
      </section>
      <section className={common.panel}>
        <h3>리뷰 결과</h3>
        {review.result ? <div className={promptStyles.stack}>
          <div><span className={promptStyles.helper}>종합 점수</span><div className={promptStyles.score}>{review.result.overallScore}</div></div>
          <ul className={promptStyles.criteria} aria-label="평가 항목별 점수">
            {review.result.criteria.map((criterion) => <li key={criterion.key}><span>{criterion.label}</span><strong>{criterion.score}</strong></li>)}
          </ul>
          <dl className={common.meta}><div><dt>독특한 시각</dt><dd>{review.result.distinctivePerspective ? "예" : "아니오"}</dd></div></dl>
          <PreviewFeedback feedback={review.result.feedback} />
        </div> : <p className={promptStyles.helper}>리뷰 결과는 완료된 리뷰에만 있습니다.</p>}
      </section>
      <section className={common.panel}>
        <h3>호출 정보</h3>
        <dl className={common.meta}>
          <div><dt>요청 횟수</dt><dd>{review.requestCount}</dd></div>
          <div><dt>워커 가져간 횟수</dt><dd>{review.attempt}</dd></div>
          <div><dt>처음 요청 (KST)</dt><dd><Time value={review.requestedAt} /></dd></div>
          <div><dt>마지막 요청 (KST)</dt><dd><Time value={review.lastRequestedAt} /></dd></div>
          <div><dt>처리 시작 (KST)</dt><dd><Time value={review.startedAt} /></dd></div>
          <div><dt>완료 (KST)</dt><dd><Time value={review.completedAt} /></dd></div>
          <div><dt>입력 토큰</dt><dd>{formatCount(review.usage.inputTokens)}</dd></div>
          <div><dt>캐시된 입력 토큰</dt><dd>{formatCount(review.usage.cachedInputTokens)}</dd></div>
          <div><dt>출력 토큰</dt><dd>{formatCount(review.usage.outputTokens)}</dd></div>
          <div><dt>추론 토큰</dt><dd>{formatCount(review.usage.reasoningTokens)}</dd></div>
          <div><dt>응답 시간</dt><dd>{formatLatency(review.latencyMs)}</dd></div>
          <div><dt>OpenAI 응답 ID</dt><dd>{review.openaiResponseId ?? "-"}</dd></div>
        </dl>
      </section>
      <section className={common.panel}>
        <h3>원출력</h3>
        {review.output
          ? <details className={styles.rawOutput}><summary>원출력 펼쳐 보기</summary><pre>{JSON.stringify(review.output, null, 2)}</pre></details>
          : <p className={promptStyles.helper}>원출력은 완료된 리뷰에만 있습니다.</p>}
      </section>
    </> : null}
  </div>;
}
