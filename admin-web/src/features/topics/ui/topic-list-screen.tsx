"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useMemo } from "react";

import type { AdminTopicDetailResponse } from "@/shared/api/contracts";
import { formatInstant } from "@/features/posts/model/post-display";
import { PostCountLinks } from "@/features/posts/ui/post-count-links";
import { EmptyState, ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import { Pagination } from "@/shared/ui/pagination";
import { StatusBadge } from "@/shared/ui/status-badge";
import { Table, type TableColumn } from "@/shared/ui/table";

import { useAdminTopics } from "../api/topic-hooks";
import { readAdminTopicFilters, withQueryPatch } from "../model/topic-filter-query";
import { getTopicErrorMessage, topicPhaseDisplay } from "../model/topic-display";
import styles from "@/shared/ui/management.module.css";

export function TopicListScreen() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const serialized = params.toString();
  const filters = useMemo(() => readAdminTopicFilters(new URLSearchParams(serialized)), [serialized]);
  const query = useAdminTopics(filters);
  const update = (patch: Record<string, string | number | undefined>) => router.push(pathname + withQueryPatch(params, patch));
  const returnTo = pathname + (serialized ? "?" + serialized : "");
  const href = (id: string) => "/topics/" + id + "?" + new URLSearchParams({ returnTo });
  const columns: TableColumn<AdminTopicDetailResponse>[] = [
    { id: "topic", header: "주제", render: (topic) => <Link className={styles.primaryLink} href={href(topic.topicId)}><strong>{topic.title}</strong><span>{topic.topicDate}</span></Link> },
    { id: "phase", header: "단계", render: (topic) => { const display = topicPhaseDisplay[topic.phase]; return <StatusBadge tone={display.tone}>{display.label}</StatusBadge>; } },
    { id: "period", header: "참여 기간", render: (topic) => <span className={styles.countsInline}>{formatInstant(topic.startsAt)} → {formatInstant(topic.endsAt)}</span> },
    { id: "posts", header: "게시물", render: (topic) => <PostCountLinks compact counts={topic.postCounts} scope={{ topicId: topic.topicId }} returnTo={returnTo} /> },
  ];

  return (
    <div className={styles.page}>
      <div className={styles.toolbar}>
        <h2 className={styles.pageTitle}>주제</h2>
        <Link className={styles.primaryButton} href="/topics/new">새 주제</Link>
      </div>
      <div className={styles.filters}>
        <label><span>단계</span><select aria-label="주제 단계" onChange={(e) => update({ phase: e.target.value || undefined, page: 1 })} value={filters.phase ?? ""}><option value="">전체</option><option value="BEFORE_OPEN">공개 전</option><option value="OPEN">참여 중</option><option value="CLOSED">종료</option></select></label>
        <label><span>시작 날짜</span><input aria-label="시작 날짜" onChange={(e) => update({ dateFrom: e.target.value || undefined, page: 1 })} type="date" value={filters.dateFrom ?? ""} /></label>
        <label><span>종료 날짜</span><input aria-label="종료 날짜" onChange={(e) => update({ dateTo: e.target.value || undefined, page: 1 })} type="date" value={filters.dateTo ?? ""} /></label>
        <label><span>정렬</span><select aria-label="주제 정렬" onChange={(e) => update({ sort: e.target.value, page: 1 })} value={filters.sort}><option value="topicDateDesc">최근 주제 날짜</option><option value="topicDateAsc">오래된 주제 날짜</option><option value="createdAtDesc">최근 등록</option><option value="createdAtAsc">오래된 등록</option></select></label>
      </div>
      {query.isPending ? <LoadingSkeleton rows={5} /> : null}
      {query.isError ? <ErrorState description={getTopicErrorMessage(query.error)} onRetry={() => query.refetch()} /> : null}
      {query.data?.topics.length === 0 ? <EmptyState title="조건에 맞는 주제가 없습니다" description="필터를 변경하거나 새 주제를 등록해 주세요." /> : null}
      {query.data?.topics.length ? (
        <>
          <div className={styles.desktop}><Table caption="관리자 주제 목록" columns={columns} getRowKey={(topic) => topic.topicId} rows={query.data.topics} /></div>
          <div className={styles.mobileCards}>
            {query.data.topics.map((topic) => {
              const display = topicPhaseDisplay[topic.phase];
              return (
                <article className={styles.mobileCard} key={topic.topicId}>
                  <div className={styles.cardHeading}>
                    <Link className={styles.primaryLink} href={href(topic.topicId)}><strong>{topic.title}</strong></Link>
                    <StatusBadge tone={display.tone}>{display.label}</StatusBadge>
                  </div>
                  <span>{topic.topicDate} · {formatInstant(topic.startsAt)}</span>
                  <PostCountLinks compact counts={topic.postCounts} scope={{ topicId: topic.topicId }} returnTo={returnTo} />
                </article>
              );
            })}
          </div>
          <Pagination currentPage={query.data.currentPage} hasNext={query.data.hasNext} onPageChange={(page) => update({ page })} />
        </>
      ) : null}
    </div>
  );
}
