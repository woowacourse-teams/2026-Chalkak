"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";

import { formatInstant } from "@/features/posts/model/post-display";
import { withQueryPatch } from "@/features/posts/model/post-filter-query";
import { ApiError } from "@/shared/api/errors";
import { ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import management from "@/shared/ui/management.module.css";
import { Pagination } from "@/shared/ui/pagination";
import { StatusBadge } from "@/shared/ui/status-badge";

import { useAdminFeedbacks } from "../api/feedback-hooks";
import { FeedbackAnswerBadge } from "./feedback-answer-badge";
import styles from "./feedbacks.module.css";

export function UserFeedbackHistory({ userId }: { userId: string }) {
  const router = useRouter();
  const params = useSearchParams();
  const requestedPage = Number(params.get("feedbackPage"));
  const page = Number.isSafeInteger(requestedPage) && requestedPage > 0 ? requestedPage : 1;
  const query = useAdminFeedbacks({ userId, sort: "createdAtDesc", page, pageSize: 5 });
  const userPath = "/users/" + encodeURIComponent(userId);
  const queryString = params.toString();
  const returnTo = userPath + (queryString ? "?" + queryString : "") + "#feedback-history";

  function showPage(nextPage: number) {
    const search = withQueryPatch(params, { feedbackPage: nextPage === 1 ? undefined : nextPage });
    router.push(userPath + search + "#feedback-history", { scroll: false });
  }

  return (
    <section aria-labelledby="feedback-history-title" className={management.panel + " " + styles.history} id="feedback-history">
      <h3 id="feedback-history-title">문의 내역</h3>
      {query.isPending ? <LoadingSkeleton rows={2} /> : null}
      {query.isError ? <ErrorState
        description={query.error instanceof ApiError ? query.error.message : "문의 내역을 불러오지 못했습니다."}
        onRetry={() => { void query.refetch(); }}
      /> : null}
      {query.data && !query.isError ? <>
        {query.data.feedbacks.length ? <ol className={styles.historyList}>
          {query.data.feedbacks.map((feedback) => <li className={styles.historyItem} key={feedback.feedbackId}>
            <Link href={"/feedbacks/" + encodeURIComponent(feedback.feedbackId) + "?" + new URLSearchParams({ returnTo })}>
              <span className={styles.entryMeta}>
                <time dateTime={feedback.createdAt}>{formatInstant(feedback.createdAt)}</time>
                <FeedbackAnswerBadge answerState={feedback.answerState} />
                {feedback.deletedAt ? <StatusBadge tone="danger">삭제된 문의</StatusBadge> : null}
                <span className={styles.historyLinkLabel} aria-hidden="true">상세 보기 →</span>
              </span>
              <span className={styles.historyExcerpt}>{feedback.content}</span>
            </Link>
          </li>)}
        </ol> : <p className={styles.description}>{page === 1 ? "아직 접수된 문의가 없습니다." : "이 페이지에는 문의가 없습니다."}</p>}
        {page > 1 || query.data.hasNext ? <Pagination currentPage={query.data.currentPage} hasNext={query.data.hasNext} onPageChange={showPage} /> : null}
      </> : null}
    </section>
  );
}
