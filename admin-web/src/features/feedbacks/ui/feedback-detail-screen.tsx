"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";

import { getSafeReturnTo } from "@/features/auth/model/return-to";
import { formatInstant } from "@/features/posts/model/post-display";
import { userStatusDisplay } from "@/features/users/model/user-display";
import type { AdminFeedbackDetail } from "@/shared/api/contracts";
import { ApiError } from "@/shared/api/errors";
import { ConfirmDialog } from "@/shared/ui/confirm-dialog";
import { ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import management from "@/shared/ui/management.module.css";
import { StatusBadge } from "@/shared/ui/status-badge";

import { useAdminFeedback, useAdminFeedbackChange } from "../api/feedback-hooks";
import { FeedbackAnswerBadge } from "./feedback-answer-badge";
import styles from "./feedbacks.module.css";

type Deletion =
  | { kind: "delete-answer"; answerVersion: string }
  | { kind: "delete-inquiry" };

function cannotAnswer(feedback: AdminFeedbackDetail) {
  if (feedback.deletedAt) return "삭제된 문의에는 답변을 등록할 수 없습니다.";
  if (feedback.author.status === "WITHDRAWN") return "탈퇴한 사용자의 문의에는 답변을 등록할 수 없습니다.";
  return null;
}

export function FeedbackDetailScreen({ feedbackId }: { feedbackId: string }) {
  const requestedReturn = getSafeReturnTo(useSearchParams().get("returnTo"));
  const returnPath = requestedReturn.split(/[?#]/)[0];
  const fromUserHistory = /^\/users\/[^/]+$/.test(returnPath);
  const returnTo = returnPath === "/feedbacks" || fromUserHistory ? requestedReturn : "/feedbacks";
  const query = useAdminFeedback(feedbackId);
  const mutation = useAdminFeedbackChange(feedbackId);
  const [draft, setDraft] = useState("");
  const [sendPush, setSendPush] = useState(false);
  const [deletion, setDeletion] = useState<Deletion | null>(null);
  const [notice, setNotice] = useState<{ text: string; error?: boolean } | null>(null);
  const answer = draft.trim();
  const count = Array.from(answer).length;
  const busy = mutation.isPending;

  function announceError(error: unknown, changingAnswer = false) {
    if (error instanceof ApiError && error.status === 400 && changingAnswer) {
      setNotice({ text: "답변을 삭제할 수 없습니다. 갱신된 문의 상세에서 현재 상태를 확인해 주세요.", error: true });
      return;
    }
    setNotice({ text: error instanceof ApiError ? error.message : "요청을 처리하지 못했습니다.", error: true });
  }

  async function submitAnswer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const feedback = query.data;
    if (!feedback || cannotAnswer(feedback) || feedback.answerState === "ANSWERED" || count < 1 || count > 3000 || busy) return;
    setNotice(null);
    try {
      await mutation.mutateAsync({
        kind: "register-answer",
        content: answer,
        sendPush: feedback.answerState === "DELETED" && sendPush ? true : undefined,
      });
      setDraft("");
      setSendPush(false);
      setNotice({ text: "답변을 등록했습니다." });
    } catch (error) {
      announceError(error);
    }
  }

  async function confirmDeletion() {
    if (!deletion || busy) return;
    setNotice(null);
    const deletingAnswer = deletion.kind === "delete-answer";
    try {
      await mutation.mutateAsync(deletion);
      setNotice({ text: deletingAnswer ? "답변을 삭제했습니다." : "문의를 삭제했습니다." });
      setSendPush(false);
    } catch (error) {
      announceError(error, deletingAnswer);
    } finally {
      setDeletion(null);
    }
  }

  const feedback = query.data;
  const answerRestriction = feedback ? cannotAnswer(feedback) : null;
  const answerVersion = feedback?.answerVersion;
  const authorPath = feedback ? "/users/" + encodeURIComponent(feedback.author.userId) : "";
  const detailPath = "/feedbacks/" + encodeURIComponent(feedbackId);
  const authorHref = returnPath === authorPath ? returnTo :
    authorPath + "?" + new URLSearchParams({
      returnTo: detailPath + "?" + new URLSearchParams({ returnTo }),
    });
  return (
    <div className={styles.page}>
      <Link className={management.back} href={returnTo}>← {fromUserHistory ? "사용자 문의 내역으로 돌아가기" : "문의 목록으로 돌아가기"}</Link>
      {query.isPending ? <LoadingSkeleton /> : null}
      {query.isError ? <ErrorState
        description={query.error instanceof ApiError ? query.error.message : "문의를 불러오지 못했습니다."}
        onRetry={() => { void query.refetch(); }}
      /> : null}
      {feedback && !query.isError ? <>
        <header className={styles.detailHeader}>
          <div>
            <h2 className={styles.pageTitle}>문의 상세</h2>
            <p className={styles.description}>문의 내용과 현재 답변을 확인합니다.</p>
          </div>
          {feedback.deletedAt ? <StatusBadge tone="danger">삭제된 문의</StatusBadge> :
            <button className={management.dangerButton} disabled={busy} type="button" onClick={() => setDeletion({ kind: "delete-inquiry" })}>문의 삭제</button>}
        </header>
        <div className={styles.detailGrid}>
          <section className={management.panel} aria-label="접수된 문의">
            <h3>접수된 문의</h3>
            <div className={styles.entryMeta}>
              <Link className={styles.textButton} href={authorHref}>{feedback.author.email ?? "이메일 정보 없음"}</Link>
              <StatusBadge tone={userStatusDisplay[feedback.author.status].tone}>{userStatusDisplay[feedback.author.status].label}</StatusBadge>
              <time dateTime={feedback.createdAt}>{formatInstant(feedback.createdAt)}</time>
            </div>
            <p className={styles.content}>{feedback.content}</p>
            {feedback.deletedAt ? <p className={management.readOnly}>삭제 시각 {formatInstant(feedback.deletedAt)}</p> : null}
          </section>
          <section className={management.panel} aria-label="관리자 답변">
            <div className={styles.answerHeader}>
              <h3>관리자 답변</h3>
              <FeedbackAnswerBadge answerState={feedback.answerState} />
            </div>
            {feedback.answerState === "ANSWERED" ? <>
              {feedback.answeredAt ? <time className={styles.description} dateTime={feedback.answeredAt}>{formatInstant(feedback.answeredAt)}</time> : null}
              <p className={styles.content}>{feedback.answerContent}</p>
              {answerVersion && !feedback.deletedAt ? <div className={styles.rowActions}>
                <button className={management.dangerButton} disabled={busy} type="button" onClick={() => setDeletion({ kind: "delete-answer", answerVersion })}>답변 삭제</button>
              </div> : null}
            </> : null}
            {answerRestriction ? <p className={management.readOnly}>{answerRestriction}</p> : null}
            {feedback.answerState !== "ANSWERED" && !answerRestriction ? <form className={styles.answerForm} onSubmit={submitAnswer}>
              <label htmlFor="feedback-answer">답변 내용</label>
              <textarea id="feedback-answer" rows={6} disabled={busy} placeholder="문의에 대한 답변을 입력하세요." value={draft} onChange={(event) => setDraft(event.target.value)} aria-describedby="feedback-answer-count" />
              <p id="feedback-answer-count" className={count > 3000 ? management.formError : styles.description}>{count} / 3,000자</p>
              {feedback.answerState === "DELETED" ? <label className={styles.pushOption}>
                <input type="checkbox" checked={sendPush} disabled={busy} onChange={(event) => setSendPush(event.target.checked)} />
                재답변 푸시 보내기
              </label> : <p className={styles.description}>최초 답변은 사용자 알림함에 기록되며 기존 푸시 정책을 따릅니다.</p>}
              <div className={management.formActions}>
                <button className={management.primaryButton} disabled={busy || count < 1 || count > 3000} type="submit">
                  {feedback.answerState === "DELETED" ? "재답변 등록" : "답변 등록"}
                </button>
              </div>
            </form> : null}
            {notice ? <p className={notice.error ? management.formError : management.readOnly} role={notice.error ? "alert" : "status"}>{notice.text}</p> : null}
          </section>
        </div>
      </> : null}
      <ConfirmDialog
        open={deletion !== null}
        title={deletion?.kind === "delete-answer" ? "답변을 삭제할까요?" : "문의를 삭제할까요?"}
        description={deletion?.kind === "delete-answer"
          ? "문의는 남고 사용자의 답변 알림이 사라집니다. 삭제한 뒤 새 답변을 등록할 수 있습니다."
          : "사용자의 문의 목록과 상세에서 질문과 답변이 함께 숨겨지고 관련 알림이 사라집니다. 관리자 기록은 남습니다."}
        confirmLabel={deletion?.kind === "delete-answer" ? "답변 삭제" : "문의 삭제"}
        pending={busy}
        destructive
        onCancel={() => setDeletion(null)}
        onConfirm={() => { void confirmDeletion(); }}
      />
    </div>
  );
}
