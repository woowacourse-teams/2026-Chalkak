"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useMemo } from "react";

import { withQueryPatch } from "@/features/posts/model/post-filter-query";
import { formatInstant } from "@/features/posts/model/post-display";
import { userStatusDisplay } from "@/features/users/model/user-display";
import { ApiError } from "@/shared/api/errors";
import { EmptyState, ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import { Pagination } from "@/shared/ui/pagination";
import { StatusBadge } from "@/shared/ui/status-badge";

import { useAdminFeedbacks } from "../api/feedback-hooks";
import { inquiryStatusLabels, readAdminFeedbackFilters } from "../model/feedback-filter-query";
import { FeedbackAnswerBadge } from "./feedback-answer-badge";
import styles from "./feedbacks.module.css";

function authorHref(userId: string, returnTo: string) {
  return "/users/" + encodeURIComponent(userId) + "?" + new URLSearchParams({ returnTo });
}

export function FeedbackListScreen() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const serialized = searchParams.toString();
  const filters = useMemo(
    () => readAdminFeedbackFilters(new URLSearchParams(serialized)),
    [serialized],
  );
  const query = useAdminFeedbacks(filters);
  const hasFilters = filters.inquiryStatus !== "ALL" || filters.bannedAuthorOnly;
  const returnTo = pathname + (serialized ? "?" + serialized : "");
  const update = (patch: Record<string, string | number | undefined>) =>
    router.push(pathname + withQueryPatch(searchParams, patch));

  return <div className={styles.page}>
    <h2 className={styles.pageTitle}>고객센터</h2>
    <p className={styles.description}>접수된 문의를 확인합니다. 작성자 이메일을 누르면 사용자 상세에서 문의 내역을 볼 수 있습니다.</p>
    <div className={styles.filterPanel}>
      <div className={styles.statusFilters} role="group" aria-label="문의 상태">
        {Object.entries(inquiryStatusLabels).map(([status, label]) => (
          <button key={status} type="button" aria-pressed={filters.inquiryStatus === status} onClick={() => {
            update({ inquiryStatus: status === "ALL" ? undefined : status, page: 1 });
          }}>{label}</button>
        ))}
      </div>
      <div className={styles.filters}>
        <label className={styles.bannedFilter}>
          <input type="checkbox" checked={filters.bannedAuthorOnly} onChange={(event) => {
            update({ bannedAuthorOnly: event.target.checked ? "true" : undefined, page: 1 });
          }} />
          차단 회원만
        </label>
        <label className={styles.sortFilter}>
          정렬
          <select onChange={(event) => update({ sort: event.target.value, page: 1 })} value={filters.sort}>
            <option value="createdAtDesc">최신순</option>
            <option value="createdAtAsc">오래된 순</option>
          </select>
        </label>
      </div>
    </div>
    {query.isPending ? <LoadingSkeleton /> : null}
    {query.isError ? <ErrorState description={query.error instanceof ApiError ? query.error.message : "피드백을 불러오지 못했습니다."} onRetry={() => { void query.refetch(); }} /> : null}
    {query.data?.feedbacks.length === 0 ? <EmptyState
      title={hasFilters ? "조건에 맞는 문의가 없습니다" : "접수된 피드백이 없습니다"}
      description={hasFilters ? "문의 상태나 차단 회원 조건을 바꿔 다시 확인해 주세요." : "사용자가 앱에서 의견을 보내면 이곳에 표시됩니다."}
    /> : null}
    {query.data?.feedbacks.length ? <ol aria-label="사용자 피드백" className={styles.list}>
      {query.data.feedbacks.map((feedback) => {
        const status = userStatusDisplay[feedback.author.status];
        return <li className={styles.entry} key={feedback.feedbackId}>
          <div className={styles.entryHeading}>
            <Link className={styles.author} href={authorHref(feedback.author.userId, returnTo)}>{feedback.author.email ?? "이메일 정보 없음"}</Link>
            <time dateTime={feedback.createdAt}>{formatInstant(feedback.createdAt)}</time>
          </div>
          <div className={styles.entryMeta}>
            <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
            <span>앱 {feedback.author.appVersion ?? "—"}</span>
            <FeedbackAnswerBadge answerState={feedback.answerState} />
            {feedback.deletedAt ? <StatusBadge tone="danger">삭제된 문의</StatusBadge> : null}
          </div>
          <p className={styles.content}>{feedback.content}</p>
          <div className={styles.rowActions}>
            <Link className={styles.textButton} href={"/feedbacks/" + encodeURIComponent(feedback.feedbackId) + "?" + new URLSearchParams({ returnTo })}>문의 상세</Link>
          </div>
        </li>;
      })}
    </ol> : null}
    {query.data ? <Pagination currentPage={query.data.currentPage} hasNext={query.data.hasNext} onPageChange={(page) => update({ page })} /> : null}
  </div>;
}
