"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { getSafeReturnTo } from "@/features/auth/model/return-to";
import { ApiError } from "@/shared/api/errors";
import { ConfirmDialog } from "@/shared/ui/confirm-dialog";
import { ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import { useToast } from "@/shared/ui/toast";
import { useNotice, useNoticeMutations } from "../api/notice-hooks";
import { canEditNotice, formatNoticeTime, noticeEnvironment, noticeStatus } from "../model/notice-display";
import common from "@/shared/ui/management.module.css";
import styles from "./notices.module.css";

export function NoticeDetailScreen({ noticeId }: { noticeId: string }) {
  const query = useNotice(noticeId);
  const { remove } = useNoticeMutations();
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { showToast } = useToast();
  const params = useSearchParams();
  const back = getSafeReturnTo(params.get("returnTo") ?? "/notices");
  const notice = query.data;

  async function confirmDelete(reason: string) {
    if (remove.isPending) return;
    setError(null);
    try {
      await remove.mutateAsync({ id: noticeId, reason });
      setDeleting(false);
      showToast("공지를 삭제했습니다.", "success");
    } catch (caught) { setError(caught instanceof ApiError ? caught.message : "공지를 삭제하지 못했습니다. 다시 시도해 주세요."); }
  }

  return <div className={common.page}>
    <Link className={common.back} href={back}>← 목록으로</Link>
    {query.isPending ? <LoadingSkeleton rows={4} /> : null}
    {query.isError ? <ErrorState description={query.error instanceof ApiError ? query.error.message : "공지를 불러오지 못했습니다."} onRetry={() => { void query.refetch(); }} /> : null}
    {!query.isError && notice ? <>
      <div className={styles.heading}><h2 className={common.pageTitle}>공지 상세</h2><span className={styles.environment}>{noticeEnvironment()}</span></div>
      <section className={common.panel}>
        <div className={styles.heading}><span className={styles.badge} data-state={notice.status} data-deleted={Boolean(notice.deletedAt)}>{noticeStatus(notice)}</span><Link href="/audit-logs?targetType=NOTICE">공지 처리 이력 →</Link></div>
        <h3 className={styles.titleText}>{notice.title}</h3><p className={styles.body}>{notice.body}</p>
      </section>
      <section className={common.panel}><h3>발송 정보</h3><dl className={common.meta}>
        <div><dt>대상</dt><dd>전체 회원</dd></div>
        <div><dt>예약 시각 (KST)</dt><dd>{formatNoticeTime(notice.scheduledAt)}</dd></div>
        <div><dt>알림함 배포 (KST)</dt><dd>{formatNoticeTime(notice.distributedAt)}</dd></div>
        <div><dt>등록 시각 (KST)</dt><dd>{formatNoticeTime(notice.createdAt)}</dd></div>
        <div><dt>수정 시각 (KST)</dt><dd>{formatNoticeTime(notice.updatedAt)}</dd></div>
        {notice.deletedAt ? <div><dt>삭제 시각 (KST)</dt><dd>{formatNoticeTime(notice.deletedAt)}</dd></div> : null}
        <div><dt>공지 ID</dt><dd>{notice.noticeId}</dd></div>
      </dl></section>
      <p className={common.readOnly}>{notice.deletedAt ? "삭제된 공지는 수정하거나 복구할 수 없습니다. 이미 기기에 표시된 푸시는 회수되지 않습니다." : notice.status === "DISTRIBUTED" ? "배포된 공지는 수정할 수 없습니다. 삭제하면 회원 알림함에서 숨겨지고, 아직 보내지 않은 푸시를 중단합니다. 이미 표시된 푸시는 회수되지 않습니다." : "배포 전까지 수정할 수 있습니다. 삭제하면 예약 발송이 취소됩니다."} 알림함 배포 상태는 실제 푸시 수신 결과와 다릅니다.</p>
      <div className={common.actions}>
        {canEditNotice(notice) ? <Link className={common.secondaryButton} href={`/notices/${noticeId}/edit`}>공지 수정</Link> : null}
        {!notice.deletedAt ? <button className={common.dangerButton} onClick={() => { setDeleting(true); setError(null); }} type="button">공지 삭제</button> : null}
      </div>
      <ConfirmDialog open={deleting} title="공지를 삭제할까요?" description="배포 전이면 발송이 취소됩니다. 이미 배포됐다면 회원 알림함에서 숨겨지고 아직 보내지 않은 푸시를 중단합니다. 이미 표시된 푸시는 회수할 수 없으며 삭제는 되돌릴 수 없습니다." confirmLabel="공지 삭제" reasonField={{ label: "삭제 사유", required: true, maxLength: 500, placeholder: "삭제 사유를 입력해 주세요 (최대 500자)" }} destructive pending={remove.isPending} error={error} onCancel={() => setDeleting(false)} onConfirm={(reason) => { void confirmDelete(reason); }} />
    </> : null}
  </div>;
}
