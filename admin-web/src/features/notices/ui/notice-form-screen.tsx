"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import type { AdminNotice, AdminNoticeInput } from "@/shared/api/contracts";
import { ApiError } from "@/shared/api/errors";
import { ConfirmDialog } from "@/shared/ui/confirm-dialog";
import { ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import { useToast } from "@/shared/ui/toast";
import { useNotice, useNoticeMutations } from "../api/notice-hooks";
import { canEditNotice, formatNoticeTime, fromKstInput, noticeEnvironment, noticeStatus, toKstInput, validateNotice } from "../model/notice-display";
import common from "@/shared/ui/management.module.css";
import styles from "./notices.module.css";

export function NoticeEditScreen({ noticeId }: { noticeId: string }) {
  const query = useNotice(noticeId);
  if (query.isPending) return <LoadingSkeleton rows={4} />;
  if (query.isError) return <ErrorState description={query.error instanceof ApiError ? query.error.message : "공지를 불러오지 못했습니다."} onRetry={() => { void query.refetch(); }} />;
  if (!canEditNotice(query.data)) return <div className={common.page}><Link className={common.back} href={`/notices/${noticeId}`}>← 공지 상세로</Link><p className={common.readOnly}>현재 상태: {noticeStatus(query.data)}. 배포되었거나 삭제된 공지는 수정할 수 없습니다.</p></div>;
  return <NoticeFormScreen notice={query.data} />;
}

export function NoticeFormScreen({ notice }: { notice?: AdminNotice }) {
  const router = useRouter();
  const { create, update } = useNoticeMutations();
  const { showToast } = useToast();
  const [title, setTitle] = useState(notice?.title ?? "");
  const [body, setBody] = useState(notice?.body ?? "");
  const [delivery, setDelivery] = useState<"now" | "scheduled">(notice ? "scheduled" : "now");
  const [scheduledAt, setScheduledAt] = useState(notice ? toKstInput(notice.scheduledAt) : "");
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const busy = create.isPending || update.isPending;
  const back = notice ? `/notices/${notice.noticeId}` : "/notices";
  const titleCount = [...title.trim()].length;
  const bodyCount = [...body.trim()].length;

  function input(): AdminNoticeInput {
    // Keep an unchanged server timestamp intact, including fractional seconds.
    const date = notice && scheduledAt === toKstInput(notice.scheduledAt) ? notice.scheduledAt : fromKstInput(scheduledAt);
    return { title: title.trim(), body: body.trim(), scheduledAt: delivery === "now" ? new Date().toISOString() : date ?? "" };
  }

  function review(event: FormEvent) {
    event.preventDefault();
    if (busy || uncertain) return;
    const validation = validateNotice(input());
    setError(validation);
    if (!validation) setConfirming(true);
  }

  async function save() {
    if (busy || uncertain) return;
    const value = input();
    const validation = validateNotice(value);
    if (validation) { setError(validation); setConfirming(false); return; }
    setError(null);
    try {
      const result = notice ? await update.mutateAsync({ id: notice.noticeId, body: value }) : await create.mutateAsync(value);
      showToast(notice ? "공지를 수정했습니다." : "공지를 등록했습니다.", "success");
      router.push(`/notices/${result.noticeId}`);
    } catch (caught) {
      // A timed-out POST can already have committed. Never automatically resend it.
      const unknownResult = !notice && (!(caught instanceof ApiError) || caught.kind !== "api" || (caught.status ?? 0) >= 500);
      if (unknownResult) {
        setUncertain(true);
        setConfirming(false);
        setError("서버의 등록 결과를 확인하지 못했습니다. 중복 등록을 막기 위해 공지 목록에서 등록 여부를 먼저 확인해 주세요.");
      } else setError(caught instanceof ApiError ? caught.message : "공지를 저장하지 못했습니다. 다시 시도해 주세요.");
    }
  }

  return <div className={common.page}>
    <Link className={common.back} href={back}>← {notice ? "공지 상세로" : "공지 목록으로"}</Link>
    <div className={styles.heading}><h2 className={common.pageTitle}>{notice ? "공지 수정" : "공지 작성"}</h2><span className={styles.environment}>{noticeEnvironment()}</span></div>
    <p className={common.readOnly}>공지는 전체 회원에게 전달됩니다. 별도 수신 거부 설정이 없으므로 서비스 안내만 작성하고 광고성 정보는 포함하지 마세요.</p>
    <form className={common.form} aria-label={notice ? "공지 수정" : "공지 작성"} onSubmit={review}>
      <label><span>제목</span><input autoFocus disabled={busy || uncertain} required value={title} onChange={(event) => setTitle(event.target.value)} aria-describedby="notice-title-count" placeholder="공지 제목을 입력해 주세요" /></label>
      <span className={styles.count} data-invalid={titleCount > 50} id="notice-title-count">{titleCount} / 50자</span>
      <label><span>본문</span><textarea className={styles.textArea} disabled={busy || uncertain} required rows={7} value={body} onChange={(event) => setBody(event.target.value)} aria-describedby="notice-body-count" placeholder="회원에게 전달할 내용을 입력해 주세요" /></label>
      <span className={styles.count} data-invalid={bodyCount > 500} id="notice-body-count">{bodyCount} / 500자 · 앞뒤 공백 제외</span>
      <fieldset className={styles.delivery} disabled={busy || uncertain}><legend>발송 시점</legend>
        <label className={styles.option}><input type="radio" name="delivery" checked={delivery === "now"} onChange={() => setDelivery("now")} />즉시 발송</label>
        <label className={styles.option}><input type="radio" name="delivery" checked={delivery === "scheduled"} onChange={() => setDelivery("scheduled")} />예약 발송</label>
        {delivery === "scheduled" ? <label><span>예약 시각 (KST)</span><input type="datetime-local" step="1" required value={scheduledAt} onChange={(event) => setScheduledAt(event.target.value)} /></label> : null}
        <p className={styles.helper}>한국 시간(KST) 기준입니다. 즉시 발송도 알림함 반영까지 최대 1분이 걸릴 수 있습니다.</p>
      </fieldset>
      <section className={styles.preview} aria-label="공지 미리보기"><h3>회원에게 전달할 내용</h3><strong>{title.trim() || "공지 제목"}</strong><p className={styles.body}>{body.trim() || "공지 본문이 여기에 표시됩니다."}</p></section>
      {error && !confirming ? <p className={common.formError} role="alert">{error}</p> : null}
      <div className={common.formActions}>
        {uncertain ? <Link className={common.primaryButton} href="/notices">공지 목록에서 확인</Link> : <><Link className={common.secondaryButton} href={back}>취소</Link><button className={common.primaryButton} disabled={busy} type="submit">{notice ? "수정 내용 확인" : "발송 내용 확인"}</button></>}
      </div>
    </form>
    <ConfirmDialog open={confirming} title={notice ? "공지 내용을 수정할까요?" : "전체 회원에게 공지를 전달할까요?"} description={`${noticeEnvironment()} · 전체 회원 대상\n제목: ${title.trim()}\n본문: ${body.trim()}\n발송: ${delivery === "now" ? "즉시 (최대 1분 후 알림함 반영)" : `${formatNoticeTime(input().scheduledAt)} KST`}\n배포 이후에는 내용을 수정할 수 없습니다.`} confirmLabel={notice ? "수정 저장" : delivery === "now" ? "공지 등록 및 즉시 발송" : "공지 예약 등록"} pending={busy} error={error} onCancel={() => { setConfirming(false); setError(null); }} onConfirm={() => { void save(); }} />
  </div>;
}
