"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import type { AdminReminder, AdminReminderInput, AdminReminderRepeat, AdminReminderTarget } from "@/shared/api/contracts";
import { ApiError } from "@/shared/api/errors";
import { ConfirmDialog } from "@/shared/ui/confirm-dialog";
import { EmptyState, ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import { Pagination } from "@/shared/ui/pagination";
import { useToast } from "@/shared/ui/toast";
import { formatNoticeTime, fromKstInput, noticeEnvironment, toKstInput } from "@/features/notices/model/notice-display";
import { useReminderMutations, useReminders } from "../api/reminder-hooks";
import { canEditReminder, reminderSchedule, reminderStatus, repeatLabels, targetLabels, validateReminder } from "../model/reminder-display";
import common from "@/shared/ui/management.module.css";
import styles from "./reminders.module.css";

const repeats = Object.keys(repeatLabels) as AdminReminderRepeat[];
const targets = Object.keys(targetLabels) as AdminReminderTarget[];
const targetHelp: Record<AdminReminderTarget, string> = {
  ALL: "주제와 관계없이 보냅니다.",
  TOPIC_PARTICIPANTS: "발송 날짜의 주제에 사진을 올린 회원에게만 보냅니다. 그날 주제가 없으면 보내지 않습니다.",
  TOPIC_NON_PARTICIPANTS: "발송 날짜의 주제에 아직 사진을 올리지 않은 회원에게 보냅니다. 그날 주제가 없으면 보내지 않습니다.",
};

function errorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback;
}

function ReminderForm({ reminder, onClose }: { reminder: AdminReminder | null; onClose: () => void }) {
  const { create, update } = useReminderMutations();
  const { showToast } = useToast();
  const [title, setTitle] = useState(reminder?.title ?? "");
  const [body, setBody] = useState(reminder?.body ?? "");
  const [repeat, setRepeat] = useState<AdminReminderRepeat>(reminder?.repeat ?? "ONCE");
  const [target, setTarget] = useState<AdminReminderTarget>(reminder?.target ?? "ALL");
  const [scheduledAt, setScheduledAt] = useState(reminder ? toKstInput(reminder.scheduledAt).slice(0, 16) : "");
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const titleInput = useRef<HTMLInputElement>(null);
  const busy = create.isPending || update.isPending;
  const titleCount = [...title.trim()].length;
  const bodyCount = [...body.trim()].length;

  useEffect(() => { titleInput.current?.focus(); }, []);

  function input(): AdminReminderInput {
    // Keep an unchanged server timestamp intact so a started daily reminder is not treated as rescheduled.
    const unchanged = reminder && scheduledAt === toKstInput(reminder.scheduledAt).slice(0, 16);
    return { title: title.trim(), body: body.trim(), repeat, target, scheduledAt: unchanged ? reminder.scheduledAt : fromKstInput(scheduledAt) ?? "" };
  }

  function review(event: FormEvent) {
    event.preventDefault();
    if (busy || uncertain) return;
    const validation = validateReminder(input(), reminder ?? undefined);
    setError(validation);
    if (!validation) setConfirming(true);
  }

  async function save() {
    if (busy || uncertain) return;
    const value = input();
    const validation = validateReminder(value, reminder ?? undefined);
    if (validation) { setError(validation); setConfirming(false); return; }
    setError(null);
    try {
      if (reminder) await update.mutateAsync({ id: reminder.reminderId, body: value });
      else await create.mutateAsync(value);
      showToast(reminder ? "리마인더를 수정했습니다." : "리마인더를 등록했습니다.", "success");
      onClose();
    } catch (caught) {
      // A timed-out POST can already have committed. Never automatically resend it.
      const unknownResult = !reminder && (!(caught instanceof ApiError) || caught.kind !== "api" || (caught.status ?? 0) >= 500);
      setConfirming(false);
      if (unknownResult) {
        setUncertain(true);
        setError("서버의 등록 결과를 확인하지 못했습니다. 중복 등록을 막기 위해 리마인더 목록에서 등록 여부를 먼저 확인해 주세요.");
      } else setError(errorMessage(caught, "리마인더를 저장하지 못했습니다. 다시 시도해 주세요."));
    }
  }

  const schedulePreview = fromKstInput(scheduledAt) ? reminderSchedule({ repeat, scheduledAt: input().scheduledAt }) : "발송 시각을 선택해 주세요";
  return <form aria-label={reminder ? "리마인더 수정" : "리마인더 추가"} className={common.form} onSubmit={review}>
    <h3>{reminder ? "리마인더 수정" : "새 리마인더"}</h3>
    <label><span>제목</span><input ref={titleInput} disabled={busy || uncertain} required value={title} onChange={(event) => setTitle(event.target.value)} aria-describedby="reminder-title-count" placeholder="예: 오늘이 얼마 남지 않았어요" /></label>
    <span className={styles.count} data-invalid={titleCount > 50} id="reminder-title-count">{titleCount} / 50자</span>
    <label><span>본문</span><textarea className={styles.textArea} disabled={busy || uncertain} required rows={4} value={body} onChange={(event) => setBody(event.target.value)} aria-describedby="reminder-body-count" placeholder="예: 사진을 자랑해 주세요!" /></label>
    <span className={styles.count} data-invalid={bodyCount > 500} id="reminder-body-count">{bodyCount} / 500자 · 앞뒤 공백 제외</span>
    <fieldset className={styles.choices} disabled={busy || uncertain}><legend>반복</legend>
      {repeats.map((value) => <label className={styles.option} key={value}><input type="radio" name="repeat" checked={repeat === value} onChange={() => setRepeat(value)} />{repeatLabels[value]}</label>)}
    </fieldset>
    <fieldset className={styles.choices} disabled={busy || uncertain}><legend>발송 대상</legend>
      {targets.map((value) => <label className={styles.option} key={value}><input type="radio" name="target" checked={target === value} onChange={() => setTarget(value)} />{targetLabels[value]}</label>)}
      <p className={styles.helper}>{targetHelp[target]} 주제 알림 수신을 끈 회원과 정지·탈퇴 회원에게는 보내지 않습니다.</p>
    </fieldset>
    <label><span>{repeat === "DAILY" ? "시작 날짜와 매일 보낼 시각 (KST)" : "발송 시각 (KST)"}</span><input aria-describedby="reminder-time-help" disabled={busy || uncertain} required step="60" type="datetime-local" value={scheduledAt} onChange={(event) => setScheduledAt(event.target.value)} /></label>
    <p className={styles.helper} id="reminder-time-help">{repeat === "DAILY" ? "이 날짜부터 매일 같은 시각에 보냅니다. " : ""}현재보다 미래 시각만 선택할 수 있습니다. 지금 보내려면 다음 분을 선택해 주세요. 발송 시각부터 30분 안에만 보냅니다.</p>
    <section className={styles.preview} aria-label="리마인더 미리보기"><h3>회원에게 보일 푸시</h3><strong>{title.trim() || "리마인더 제목"}</strong><p className={styles.body}>{body.trim() || "리마인더 본문이 여기에 표시됩니다."}</p><span className={styles.helper}>{schedulePreview} · {targetLabels[target]}</span></section>
    {error && !confirming ? <p className={common.formError} role="alert">{error}</p> : null}
    <div className={common.formActions}>
      {uncertain ? <button className={common.primaryButton} onClick={onClose} type="button">리마인더 목록에서 확인</button> : <><button className={common.secondaryButton} disabled={busy} onClick={onClose} type="button">취소</button><button className={common.primaryButton} disabled={busy} type="submit">저장 내용 확인</button></>}
    </div>
    <ConfirmDialog open={confirming} title={reminder ? "리마인더를 수정할까요?" : "리마인더를 등록할까요?"} description={`${noticeEnvironment()} · ${targetLabels[target]}\n제목: ${title.trim()}\n본문: ${body.trim()}\n일정: ${schedulePreview}\n저장해도 바로 보내지 않고 발송 시각에 보냅니다.`} confirmLabel={reminder ? "수정 저장" : "리마인더 등록"} pending={busy} error={error} onCancel={() => { setConfirming(false); setError(null); }} onConfirm={() => { void save(); }} />
  </form>;
}

export function ReminderScreen() {
  const router = useRouter();
  const params = useSearchParams();
  const rawPage = Number(params.get("page") ?? 1);
  const page = Number.isSafeInteger(rawPage) && rawPage > 0 ? rawPage : 1;
  const query = useReminders(page);
  const { remove } = useReminderMutations();
  const { showToast } = useToast();
  const [editor, setEditor] = useState<{ reminder: AdminReminder | null; key: number } | null>(null);
  const [deleting, setDeleting] = useState<AdminReminder | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  async function confirmDelete() {
    if (remove.isPending || !deleting) return;
    setDeleteError(null);
    try {
      await remove.mutateAsync(deleting.reminderId);
      if (editor?.reminder?.reminderId === deleting.reminderId) setEditor(null);
      setDeleting(null);
      showToast("리마인더를 삭제했습니다.", "success");
      if (query.data?.reminders.length === 1 && page > 1) router.push(`/reminders?page=${page - 1}`);
    } catch (caught) { setDeleteError(errorMessage(caught, "리마인더를 삭제하지 못했습니다. 다시 시도해 주세요.")); }
  }

  return <div className={common.page}>
    <div className={common.toolbar}><h2 className={common.pageTitle}>리마인더</h2><button className={common.primaryButton} disabled={editor !== null} onClick={() => setEditor({ reminder: null, key: Date.now() })} type="button">리마인더 추가</button></div>
    <div className={styles.intro}>
      <span className={styles.environment}>{noticeEnvironment()}</span>
      <p>정한 시각에 대상 회원에게 참여를 이끄는 푸시를 보냅니다. 모든 시각은 한국 시간(KST) 기준이며, 회원 알림함에는 남지 않습니다.</p>
      <p>주제 알림 수신을 켠 정상 회원에게만 보냅니다. 발송 시각부터 30분 안에만 보내며, 보내기 직전에 삭제·수신 해제·참여 여부를 다시 확인합니다.</p>
      <p>참여자·미참여자 대상도 주제 참여 시간이 끝난 뒤에 보낼 수 있습니다. 마감 후에는 “사진을 올려 주세요” 같은 문구가 맞지 않을 수 있으니 확인해 주세요.</p>
      <p>21시부터 다음 날 8시 사이의 참여 유도 발송은 광고성 정보의 야간 전송 규정에 해당할 수 있습니다. 실제 발송은 해당 서버의 리마인더 발송 기능이 켜져 있어야 합니다.</p>
    </div>
    {editor ? <ReminderForm key={editor.key} reminder={editor.reminder} onClose={() => setEditor(null)} /> : null}
    <div className={styles.heading}><span className={styles.helper}>등록 최신순 · 삭제한 리마인더는 표시하지 않음</span><Link href="/audit-logs?targetType=REMINDER">리마인더 처리 이력 →</Link></div>
    {query.isPending ? <LoadingSkeleton rows={3} /> : null}
    {query.isError ? <ErrorState description={errorMessage(query.error, "리마인더를 불러오지 못했습니다.")} onRetry={() => { void query.refetch(); }} /> : null}
    {!query.isError && query.data?.reminders.length === 0 ? <EmptyState title="등록된 리마인더가 없습니다" description="리마인더를 추가하면 정한 시각에 대상 회원에게 푸시를 보냅니다." /> : null}
    {!query.isError && query.data?.reminders.length ? <ul className={styles.list} aria-label="리마인더 목록">
      {query.data.reminders.map((reminder) => <li className={styles.card} key={reminder.reminderId}>
        <div className={styles.cardTop}>
          <strong className={styles.title}>{reminder.title}</strong>
          <span className={styles.badge} data-repeat={reminder.repeat} data-passed={reminder.repeat === "ONCE" && !canEditReminder(reminder)}>{reminderStatus(reminder)}</span>
        </div>
        <p className={styles.excerpt}>{reminder.body}</p>
        <div className={styles.metaLine}><span>{reminderSchedule(reminder)}</span><span>{targetLabels[reminder.target]}</span><span>수정 {formatNoticeTime(reminder.updatedAt)} KST</span></div>
        <div className={styles.actions}>
          {canEditReminder(reminder) ? <button aria-label={`${reminder.title} 수정`} className={common.secondaryButton} disabled={remove.isPending} onClick={() => setEditor({ reminder, key: Date.now() })} type="button">수정</button> : <span className={styles.helper}>발송 시각이 지나 수정할 수 없습니다.</span>}
          <button aria-label={`${reminder.title} 삭제`} className={common.dangerButton} disabled={remove.isPending} onClick={() => { setDeleting(reminder); setDeleteError(null); }} type="button">삭제</button>
        </div>
      </li>)}
    </ul> : null}
    {!query.isError && query.data ? <Pagination currentPage={query.data.currentPage} hasNext={query.data.hasNext} onPageChange={(next) => router.push(`/reminders?page=${next}`)} /> : null}
    <ConfirmDialog open={deleting !== null} title="리마인더를 삭제할까요?" description={`“${deleting?.title ?? ""}” 리마인더를 삭제하면 아직 보내지 않은 회차는 보내지 않습니다. 이미 기기에 표시된 푸시는 회수할 수 없으며 삭제는 되돌릴 수 없습니다.`} confirmLabel="리마인더 삭제" destructive pending={remove.isPending} error={deleteError} onCancel={() => setDeleting(null)} onConfirm={() => { void confirmDelete(); }} />
  </div>;
}
