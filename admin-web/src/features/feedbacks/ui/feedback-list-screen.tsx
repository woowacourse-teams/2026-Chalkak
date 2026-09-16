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
import { readAdminFeedbackFilters } from "../model/feedback-filter-query";
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
  const returnTo = pathname + (serialized ? "?" + serialized : "");
  const update = (patch: Record<string, string | number | undefined>) =>
    router.push(pathname + withQueryPatch(searchParams, patch));

  return <div className={styles.page}>
    <h2 className={styles.pageTitle}>피드백</h2>
    <p className={styles.description}>앱 마이페이지에서 사용자가 보낸 의견을 확인합니다. 정지·탈퇴한 사용자가 남긴 피드백도 함께 표시합니다.</p>
    <div className={styles.filters}>
      <label>정렬<select onChange={(event) => update({ sort: event.target.value, page: 1 })} value={filters.sort}>
        <option value="createdAtDesc">최신순</option>
        <option value="createdAtAsc">오래된 순</option>
      </select></label>
    </div>
    {query.isPending ? <LoadingSkeleton /> : null}
    {query.isError ? <ErrorState description={query.error instanceof ApiError ? query.error.message : "피드백을 불러오지 못했습니다."} onRetry={() => { void query.refetch(); }} /> : null}
    {query.data?.feedbacks.length === 0 ? <EmptyState title="접수된 피드백이 없습니다" description="사용자가 앱에서 의견을 보내면 이곳에 표시됩니다." /> : null}
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
          </div>
          <p className={styles.content}>{feedback.content}</p>
        </li>;
      })}
    </ol> : null}
    {query.data ? <Pagination currentPage={query.data.currentPage} hasNext={query.data.hasNext} onPageChange={(page) => update({ page })} /> : null}
  </div>;
}
