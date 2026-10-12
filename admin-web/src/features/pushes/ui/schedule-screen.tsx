"use client";

import Link from "next/link";
import { useEffect, useRef, useState, type FormEvent } from "react";
import type { TopicPushSchedule } from "@/shared/api/contracts";
import { ApiError } from "@/shared/api/errors";
import { ConfirmDialog } from "@/shared/ui/confirm-dialog";
import { EmptyState, ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import { useToast } from "@/shared/ui/toast";
import { useScheduleMutations, useSchedules } from "../api/schedule-hooks";
import common from "@/shared/ui/management.module.css";
import styles from "./schedules.module.css";

function errorMessage(error: unknown) {
  return error instanceof ApiError ? error.message : "발송 시간 설정을 처리하지 못했습니다. 다시 시도해 주세요.";
}

export function ScheduleScreen() {
  const query = useSchedules();
  const { create, update, remove } = useScheduleMutations();
  const { showToast } = useToast();
  const [editor, setEditor] = useState<{ schedule: TopicPushSchedule | null } | null>(null);
  const [sendTime, setSendTime] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<TopicPushSchedule | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const busy = create.isPending || update.isPending || remove.isPending;

  useEffect(() => { if (editor) input.current?.focus(); }, [editor]);

  function edit(schedule: TopicPushSchedule | null) {
    setEditor({ schedule });
    setSendTime(schedule?.sendTime.slice(0, 5) ?? "");
    setFormError(null);
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    if (busy || !editor) return;
    if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(sendTime)) {
      setFormError("00:00부터 23:59까지 분 단위로 입력해 주세요.");
      return;
    }
    setFormError(null);
    try {
      const value = `${sendTime}:00`;
      if (editor.schedule) await update.mutateAsync({ scheduleId: editor.schedule.scheduleId, sendTime: value });
      else await create.mutateAsync(value);
      setEditor(null);
      showToast("발송 시간을 저장했습니다.", "success");
    } catch (error) { setFormError(errorMessage(error)); }
  }

  async function confirmDelete() {
    if (busy || !deleting) return;
    setDeleteError(null);
    try {
      await remove.mutateAsync(deleting.scheduleId);
      setDeleting(null);
      showToast("발송 시간을 삭제했습니다.", "success");
    } catch (error) { setDeleteError(errorMessage(error)); }
  }

  return <div className={common.page}>
    <div className={common.toolbar}>
      <h2 className={common.pageTitle}>주제 알림 설정</h2>
      <button className={common.primaryButton} disabled={busy} onClick={() => edit(null)} type="button">발송 시간 추가</button>
    </div>
    <div className={styles.intro}>
      <p>오늘의 주제를 알릴 시간을 설정합니다. 모든 발송 시간은 한국 시간(KST) 기준입니다.</p>
      <p>저장한 이후 도래하는 시간부터 매일 적용됩니다. 지난 시간은 다음 날부터 적용하며, 저장 즉시 발송하지 않습니다.</p>
      <p>오늘의 주제가 있고 주제 알림 수신을 켠 정상 회원 중, 유효한 로그인과 등록 기기가 있는 사용자에게 발송됩니다.</p>
      <p>실제 발송은 해당 서버의 주제 푸시 기능이 활성화되어 있어야 합니다.</p>
    </div>
    {editor ? <form aria-label={editor.schedule ? "발송 시간 수정" : "발송 시간 추가"} className={common.form} onSubmit={save}>
      <h3>{editor.schedule ? "발송 시간 수정" : "새 발송 시간"}</h3>
      <label><span>발송 시간 (KST)</span><input aria-describedby="schedule-time-help" disabled={busy} onChange={(event) => setSendTime(event.target.value)} ref={input} required step="60" type="time" value={sendTime} /></label>
      <p className={styles.help} id="schedule-time-help">분 단위로 입력해 주세요. 같은 시간은 중복 등록할 수 없습니다.</p>
      {formError ? <p className={common.formError} role="alert">{formError}</p> : null}
      <div className={common.formActions}>
        <button className={common.secondaryButton} disabled={busy} onClick={() => setEditor(null)} type="button">취소</button>
        <button className={common.primaryButton} disabled={busy || !sendTime} type="submit">{busy ? "저장 중…" : "저장"}</button>
      </div>
    </form> : null}
    <div className={styles.listHeading}><h3>매일 발송 시간{query.data ? ` · ${query.data.schedules.length}개` : ""}</h3><Link href="/audit-logs?targetType=TOPIC_PUSH_SCHEDULE">설정 변경 이력 →</Link></div>
    {query.isPending ? <LoadingSkeleton rows={3} /> : null}
    {query.isError ? <ErrorState description={errorMessage(query.error)} onRetry={() => { void query.refetch(); }} /> : null}
    {!query.isError && query.data?.schedules.length === 0 ? <EmptyState title="등록된 발송 시간이 없습니다" description="발송 시간을 추가하면 매일 해당 시간에 오늘의 주제를 알릴 수 있습니다." /> : null}
    {!query.isError && query.data?.schedules.length ? <ul aria-label="주제 알림 발송 시간" className={styles.list}>
      {query.data.schedules.map((schedule) => <li className={styles.schedule} id={`schedule-${schedule.scheduleId}`} key={schedule.scheduleId}>
        <div><strong>{schedule.sendTime.slice(0, 5)}</strong><span>매일 · KST</span></div>
        <div className={styles.actions}>
          <button aria-label={`${schedule.sendTime.slice(0, 5)} 수정`} className={common.secondaryButton} disabled={busy} onClick={() => edit(schedule)} type="button">수정</button>
          <button aria-label={`${schedule.sendTime.slice(0, 5)} 삭제`} className={common.dangerButton} disabled={busy} onClick={() => { setDeleting(schedule); setDeleteError(null); }} type="button">삭제</button>
        </div>
      </li>)}
    </ul> : null}
    <ConfirmDialog confirmLabel="발송 시간 삭제" description={`${deleting?.sendTime.slice(0, 5) ?? ""} (KST) 발송 시간을 삭제하면 앞으로 이 시간에 발송하지 않습니다. 이미 발송을 준비한 알림은 취소되지 않습니다.`} destructive error={deleteError} onCancel={() => setDeleting(null)} onConfirm={() => { void confirmDelete(); }} open={deleting !== null} pending={remove.isPending} title="발송 시간을 삭제할까요?" />
  </div>;
}
