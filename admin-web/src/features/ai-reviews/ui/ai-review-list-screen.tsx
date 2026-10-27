"use client";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useAiPromptVersions } from "@/features/ai-prompts/api/ai-prompt-hooks";
import { formatLatency, formatVersion } from "@/features/ai-prompts/model/ai-prompt";
import { formatInstant } from "@/features/posts/model/post-display";
import { PostThumbnail } from "@/features/posts/ui/post-thumbnail";
import type { AdminAiReviewSummary, AiReviewStatus } from "@/shared/api/contracts";
import { ApiError } from "@/shared/api/errors";
import { EmptyState, ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import { Pagination } from "@/shared/ui/pagination";
import { Table, type TableColumn } from "@/shared/ui/table";
import { useAiReviews } from "../api/ai-review-hooks";
import { aiReviewStatuses, aiReviewStatusDisplay, buildAiReviewListHref, formatAuthorStatus, formatCount, formatUnlistedVersion, hasAiReviewFilters, readAiReviewQuery } from "../model/ai-review";
import { AiReviewFlags, AiReviewStatusBadge } from "./ai-review-flags";
import common from "@/shared/ui/management.module.css";
import styles from "./ai-reviews.module.css";

export function AiReviewListScreen() {
  const router = useRouter();
  const params = useSearchParams();
  const query = readAiReviewQuery(params);
  const reviews = useAiReviews(query);
  const versions = useAiPromptVersions(1);
  const options = versions.data?.promptVersions ?? [];
  const unlisted = query.promptVersionId && !versions.isPending && !options.some((row) => row.promptVersionId === query.promptVersionId);
  const filtered = hasAiReviewFilters(query);
  const detailHref = (row: AdminAiReviewSummary) => `/ai-reviews/${row.aiReviewId}`;
  const thumbnail = (row: AdminAiReviewSummary) => <Link aria-label="AI 리뷰 상세 보기(썸네일)" className={styles.thumbLink} href={detailHref(row)}>
    <PostThumbnail alt="게시물 썸네일" src={row.post.thumbnailUrl} />
  </Link>;
  const statusCell = (row: AdminAiReviewSummary) => <span className={styles.cell}>
    <AiReviewStatusBadge status={row.status} />
    <small className={styles.nowrap}>요청 {row.requestCount}회</small>
    {row.errorCode ? <small>{row.errorCode}</small> : null}
  </span>;
  const promptCell = (row: AdminAiReviewSummary) => <span className={styles.cell}>
    <Link className={styles.nowrap} href={`/ai-prompts/${row.promptVersion.promptVersionId}`}>{formatVersion(row.promptVersion.version)}</Link>
    <small className={styles.nowrap}>{row.promptVersion.model}</small>
  </span>;
  const usageCell = (row: AdminAiReviewSummary) => <span className={`${styles.cell} ${styles.nowrap}`}>
    <span>입력 {formatCount(row.usage.inputTokens)}</span><span>출력 {formatCount(row.usage.outputTokens)}</span><small>응답 {formatLatency(row.latencyMs)}</small>
  </span>;
  const time = (row: AdminAiReviewSummary) => <time className={styles.nowrap} dateTime={row.lastRequestedAt}>{formatInstant(row.lastRequestedAt)}</time>;
  const columns: TableColumn<AdminAiReviewSummary>[] = [
    { id: "post", header: "게시물", render: (row) => <span className={styles.cell}>{thumbnail(row)}
      <Link className={`${styles.detailLink} ${styles.nowrap}`} href={detailHref(row)}>상세 보기</Link></span> },
    { id: "author", header: "작성자", render: (row) => <span className={styles.cell}>
      <span className={styles.email}>{row.author.email ?? "-"}</span><small>{formatAuthorStatus(row.author.status)}</small>
    </span> },
    { id: "status", header: "상태", render: statusCell },
    { id: "score", header: "점수", render: (row) => <span className={styles.nowrap}>{row.overallScore ?? "-"}</span> },
    { id: "prompt", header: "프롬프트", render: promptCell },
    { id: "usage", header: "사용량", render: usageCell },
    { id: "lastRequestedAt", header: "마지막 요청 (KST)", render: time },
    { id: "visibility", header: "노출·원본 값", render: (row) => <AiReviewFlags review={row} /> },
  ];
  const go = (patch: Partial<typeof query>) => router.push(buildAiReviewListHref({ ...query, page: 1, ...patch }));
  return <div className={common.page}>
    <div className={common.toolbar}><h2 className={common.pageTitle}>AI 리뷰</h2></div>
    <div className={styles.filterRow}>
      <label><span>상태</span>
        <select value={query.status ?? ""} onChange={(event) => go({ status: (event.target.value || undefined) as AiReviewStatus | undefined })}>
          <option value="">전체</option>
          {aiReviewStatuses.map((status) => <option key={status} value={status}>{aiReviewStatusDisplay[status].label}</option>)}
        </select>
      </label>
      <label><span>프롬프트 버전</span>
        <select value={query.promptVersionId ?? ""} onChange={(event) => go({ promptVersionId: event.target.value || undefined })}>
          <option value="">전체</option>
          {unlisted ? <option value={query.promptVersionId}>{formatUnlistedVersion(query.promptVersionId!)}</option> : null}
          {options.map((row) => <option key={row.promptVersionId} value={row.promptVersionId}>{formatVersion(row.version)} · {row.model}</option>)}
        </select>
      </label>
    </div>
    {reviews.isPending ? <LoadingSkeleton rows={4} /> : null}
    {reviews.isError ? <ErrorState description={reviews.error instanceof ApiError ? reviews.error.message : "AI 리뷰를 불러오지 못했습니다."} onRetry={() => { void reviews.refetch(); }} /> : null}
    {!reviews.isError && reviews.data?.aiReviews.length === 0 ? <EmptyState
      title={filtered ? "조건에 맞는 AI 리뷰가 없습니다" : "AI 리뷰가 없습니다"}
      description={filtered ? "필터를 바꾸거나 전체로 되돌려 확인해 주세요." : query.page > 1 ? "이전 페이지를 확인해 주세요." : "사용자가 AI 리뷰를 요청하면 여기에 표시됩니다."} /> : null}
    {!reviews.isError && reviews.data?.aiReviews.length ? <>
      <div className={common.desktop}><Table caption="AI 리뷰 목록" columns={columns} getRowKey={(row) => row.aiReviewId} rows={reviews.data.aiReviews} /></div>
      <div className={common.mobileCards}>
        {reviews.data.aiReviews.map((row) => <article className={common.mobileCard} key={row.aiReviewId}>
          <div className={common.cardHeading}>{thumbnail(row)}{statusCell(row)}</div>
          <span className={styles.email}>{row.author.email ?? "-"} · {formatAuthorStatus(row.author.status)}</span>
          <span>점수 {row.overallScore ?? "-"} · {formatVersion(row.promptVersion.version)} {row.promptVersion.model}</span>
          {usageCell(row)}
          <span>마지막 요청 {time(row)}</span>
          <AiReviewFlags review={row} />
          <Link className={styles.detailLink} href={detailHref(row)}>상세 보기</Link>
        </article>)}
      </div>
    </> : null}
    {!reviews.isError && reviews.data ? <Pagination currentPage={reviews.data.currentPage} hasNext={reviews.data.hasNext} onPageChange={(page) => router.push(buildAiReviewListHref({ ...query, page }))} /> : null}
  </div>;
}
