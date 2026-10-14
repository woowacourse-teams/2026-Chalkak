"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ApiError } from "@/shared/api/errors";
import { EmptyState, ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import { Pagination } from "@/shared/ui/pagination";
import { useNotices } from "../api/notice-hooks";
import { formatNoticeTime, noticeEnvironment, noticeStatus } from "../model/notice-display";
import common from "@/shared/ui/management.module.css";
import styles from "./notices.module.css";

export function NoticeListScreen() {
  const router = useRouter();
  const params = useSearchParams();
  const rawPage = Number(params.get("page") ?? 1);
  const page = Number.isSafeInteger(rawPage) && rawPage > 0 ? rawPage : 1;
  const query = useNotices(page);
  const returnTo = `/notices?page=${page}`;
  return <div className={common.page}>
    <div className={common.toolbar}><h2 className={common.pageTitle}>공지 관리</h2><Link className={common.primaryButton} href="/notices/new">공지 작성</Link></div>
    <div className={styles.intro}>
      <span className={styles.environment}>{noticeEnvironment()}</span>
      <p>전체 회원에게 서비스 공지를 전달합니다. 예약 시각은 한국 시간(KST) 기준입니다.</p>
      <p>즉시 발송도 알림함 반영까지 최대 1분이 걸릴 수 있습니다. 알림함 배포 완료는 기기의 푸시 수신 완료를 의미하지 않습니다.</p>
    </div>
    <div className={styles.heading}><span className={styles.helper}>등록 최신순 · 삭제된 공지 포함</span><Link href="/audit-logs?targetType=NOTICE">공지 처리 이력 →</Link></div>
    {query.isPending ? <LoadingSkeleton rows={3} /> : null}
    {query.isError ? <ErrorState description={query.error instanceof ApiError ? query.error.message : "공지를 불러오지 못했습니다."} onRetry={() => { void query.refetch(); }} /> : null}
    {!query.isError && query.data?.notices.length === 0 ? <EmptyState title="등록된 공지가 없습니다" description="새 공지를 작성해 서비스 소식을 전해 주세요." /> : null}
    {!query.isError && query.data?.notices.length ? <ul className={styles.list} aria-label="공지 목록">
      {query.data.notices.map((notice) => <li className={styles.card} key={notice.noticeId}>
        <div className={styles.cardTop}>
          <Link className={styles.title} href={`/notices/${notice.noticeId}?${new URLSearchParams({ returnTo })}`}>{notice.title}</Link>
          <span className={styles.badge} data-state={notice.status} data-deleted={Boolean(notice.deletedAt)}>{noticeStatus(notice)}</span>
        </div>
        <p className={styles.excerpt}>{notice.body}</p>
        <div className={styles.metaLine}><span>예약 {formatNoticeTime(notice.scheduledAt)} KST</span>{notice.distributedAt ? <span>배포 {formatNoticeTime(notice.distributedAt)} KST</span> : null}{notice.deletedAt ? <span>삭제 {formatNoticeTime(notice.deletedAt)} KST</span> : null}</div>
      </li>)}
    </ul> : null}
    {!query.isError && query.data ? <Pagination currentPage={query.data.currentPage} hasNext={query.data.hasNext} onPageChange={(next) => router.push(`/notices?page=${next}`)} /> : null}
  </div>;
}
