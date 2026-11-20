"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/shared/query/query-client";
import { ConfirmDialog } from "@/shared/ui/confirm-dialog";
import common from "@/shared/ui/management.module.css";
import { jevApi, type Call, type GoldInput, type RunDetail } from "./api";
import { displayDate, errorText, goldLabels, questionLabels, runStatus, slackLabel, statusLabels } from "./display";
import { Actual, Heading, QuestionResults, Recommendation, State } from "./ui";
import styles from "./jev.module.css";

export function JevDetail({ jobId }: { jobId: string }) {
  const detail = useQuery({
    queryKey: ["jev", "detail", jobId],
    queryFn: ({ signal }) => jevApi.detail(jobId, signal),
    refetchInterval: ({ state }) => state.data && !state.data.result.finishedAt ? 3000 : false,
  });
  return <>
    <Link className={common.back} href="/posts/jev">← 제브 처리 내역</Link>
    {detail.data ? <DetailBody key={jobId} detail={detail.data} /> : <State busy={detail.isPending} error={detail.error} retry={() => detail.refetch()} />}
  </>;
}

function DetailBody({ detail }: { detail: RunDetail }) {
  const run = detail.result;
  const queryClient = useQueryClient();
  const [confirm, setConfirm] = useState(false);
  const [correctionMessage, setCorrectionMessage] = useState("");
  const targetStatus = detail.postStatus === "APPROVED" ? "REJECTED" : "APPROVED";
  const targetLabel = targetStatus === "APPROVED" ? "승인" : "반려";
  const correctionAction = targetStatus === "APPROVED" ? "승인으로 정정" : "반려로 정정";
  const correction = useMutation({
    mutationFn: (reason: string) => jevApi.correct(run.postId, { status: targetStatus, reason }),
    onSuccess: async (result) => {
      await Promise.all([queryClient.invalidateQueries({ queryKey: ["jev"] }), queryClient.invalidateQueries({ queryKey: queryKeys.posts.all })]);
      setConfirm(false);
      setCorrectionMessage(result.moderatedBy ? "실제 판정을 정정했어요." : "이미 같은 판정이 적용돼 있어요.");
    },
  });
  const canCorrect = !run.preview && !detail.postDeletedAt && (detail.postStatus === "APPROVED" || detail.postStatus === "REJECTED");
  return <>
    <Heading title={run.topic} subtitle={`${run.preview ? "시험 검수" : "운영 검수"} · ${displayDate(run.createdAt)} 접수 · ${detail.model}`}
      actions={!run.preview && !detail.postDeletedAt && <Link className={common.secondaryButton} href={`/posts/${run.postId}`}>{detail.postStatus === "PENDING" ? "게시물 검수 열기" : "게시물 상세 열기"}</Link>} />
    <div className={styles.detailGrid}>
      <section className={styles.panel}>
        <h2>검수 사진</h2>
        {detail.originalImageUrl ? <a href={detail.originalImageUrl} target="_blank" rel="noreferrer" className={styles.photo}>
          {/* Original URLs are short-lived and may use a deployment-specific host. */}
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={detail.originalImageUrl} alt={`${run.topic} 검수 사진`} referrerPolicy="no-referrer" />
        </a> : <p className={styles.state}>열 수 있는 사진이 없어요.</p>}
        <dl className={styles.meta}>
          <div><dt>주제 분류</dt><dd>{run.category ?? "–"}</dd></div>
          <div><dt>검수 완료</dt><dd>{displayDate(run.finishedAt)}</dd></div>
          <div><dt>작업 ID</dt><dd className={styles.mono}>{run.jobId}</dd></div>
        </dl>
      </section>
      <section className={styles.panel}>
        <h2>판정 결과</h2>
        <dl className={styles.meta}>
          <div><dt>제브 추천</dt><dd><Recommendation value={run.evaluation?.recommendation} /></dd></div>
          <div><dt>서버 처리</dt><dd>{runStatus(run.preview, detail.disposition, run.finishedAt)}</dd></div>
          <div><dt>실제 게시물 상태</dt><dd><Actual status={detail.postDeletedAt ? "삭제됨" : detail.postStatus} /></dd></div>
          <div><dt>관리자 정답</dt><dd>{detail.goldLabel ? goldLabels[detail.goldLabel.decision] : "미평가"}</dd></div>
          {detail.applicationReason && <div><dt>처리 사유</dt><dd>{statusLabels[detail.applicationReason] ?? detail.applicationReason}</dd></div>}
        </dl>
        {Object.entries(detail.rejectionSuggestions).map(([name, reason]) => <p className={styles.warning} key={name}>{questionLabels[name] ?? name} · 반려 문구 제안<br />{reason}</p>)}
        {canCorrect && <button className={common.secondaryButton} onClick={() => { correction.reset(); setCorrectionMessage(""); setConfirm(true); }}>실제 판정을 {correctionAction}</button>}
        {correctionMessage && <p className={styles.success} role="status">{correctionMessage}</p>}
      </section>
    </div>
    <section className={styles.panel}>
      <h2>질문별 판정</h2>
      <QuestionResults evaluation={run.evaluation} />
      {run.classificationEvaluation && <details className={styles.disclosure}><summary>주제 분류 응답 보기</summary><QuestionResults evaluation={run.classificationEvaluation} /></details>}
    </section>
    {run.finishedAt && <EvaluationForm detail={detail} />}
    <section className={styles.panel}>
      <h2>호출 기록</h2>
      <div className={styles.detailGrid}>
        <CallHistory label="공유 주제 분류" calls={run.classificationCalls?.calls ?? []} reference={run.classificationId} />
        <CallHistory label="사진 검수" calls={run.photoCalls?.calls ?? []} />
      </div>
      <p className={styles.muted}>큐 전달 {run.dispatchAttempts}회{run.dispatchError ? ` · ${run.dispatchError}` : ""}</p>
    </section>
    <section className={styles.panel}>
      <h2>Slack 전달</h2>
      {detail.slackDelivery ? <div className={styles.stack}>
        <p>{slackLabel(detail.slackDelivery.status)} · {detail.slackDelivery.attempts}회 시도</p>
        {detail.slackDelivery.availableAt && <p className={styles.muted}>다음 전송 가능 시각 {displayDate(detail.slackDelivery.availableAt)}</p>}
        {detail.slackDelivery.history.map((attempt) => <p key={attempt.token} className={styles.muted}>{displayDate(attempt.startedAt)} · {attempt.error ?? "성공"}{attempt.httpStatus ? ` · HTTP ${attempt.httpStatus}` : ""}</p>)}
      </div> : <p className={styles.muted}>예약된 Slack 알림이 없어요.</p>}
    </section>
    <ConfirmDialog open={confirm} title="실제 게시물 판정 정정" description={`실제 게시물이 ${targetLabel} 상태로 바뀌고 작성자에게 정정 알림이 전달돼요.`}
      confirmLabel={correctionAction} reasonField={{ label: "정정 사유", required: true, maxLength: 500 }}
      pending={correction.isPending} error={correction.error ? errorText(correction.error) : null} destructive={targetStatus === "REJECTED"}
      onCancel={() => setConfirm(false)} onConfirm={(reason) => correction.mutate(reason)} />
  </>;
}

export function CallHistory({ label, calls, reference }: { label: string; calls: Call[]; reference?: string | null }) {
  return <div className={styles.callGroup}>
    <h3>{label} <span className={styles.muted}>· {calls.length}회</span></h3>
    {reference && <p className={styles.mono}>분류 ID {reference}</p>}
    {calls.length === 0 && <p className={styles.muted}>호출 기록 없음</p>}
    {calls.map((call, position) => <details className={styles.disclosure} key={call.callId}>
      <summary>{position + 1}번째 호출 · {call.httpStatus ? `HTTP ${call.httpStatus}` : "HTTP 미확인"} · {call.latencyMs != null ? `${call.latencyMs.toLocaleString()} ms` : "시간 미확인"}{call.errorCode && ` · ${call.errorCode}`}</summary>
      <p className={styles.muted}>입력 {call.inputTokens?.toLocaleString() ?? "미확인"} · 출력 {call.outputTokens?.toLocaleString() ?? "미확인"} · 총 {call.inputTokens != null && call.outputTokens != null ? (call.inputTokens + call.outputTokens).toLocaleString() : "미확인"} 토큰</p>
      <p className={styles.mono}>{call.callId}</p>
      {call.rawResponse && <pre className={styles.raw}>{call.rawResponse}</pre>}
    </details>)}
  </div>;
}

function EvaluationForm({ detail }: { detail: RunDetail }) {
  const queryClient = useQueryClient();
  const evaluation = useMutation({
    mutationFn: (value: GoldInput) => jevApi.evaluate(detail.result.jobId, value),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["jev"] }),
  });
  const gold = detail.goldLabel;
  const questions = Object.keys(questionLabels).filter((name) => name !== "topic_category");
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const reason = String(data.get("reason") ?? "").trim();
    if (!reason) return;
    evaluation.mutate({
      decision: data.get("decision") as GoldInput["decision"],
      questions: Object.fromEntries(questions.flatMap((name) => data.get(name) ? [[name, data.get(name) as GoldInput["decision"]]] : [])),
      reason, sampling: data.get("sampling") as GoldInput["sampling"],
    });
  }
  return <section className={styles.panel}>
    <h2>관리자 정답 평가</h2>
    <p className={styles.muted}>모델 성능을 재는 데 사용하는 평가예요. 이 평가를 저장해도 게시물 공개 상태는 유지돼요.</p>
    {gold && <p className={styles.muted}>최근 평가 {displayDate(gold.evaluatedAt)} · {gold.evaluatedBy}</p>}
    <form className={styles.form} onSubmit={submit}>
      <div className={styles.detailGrid}>
        <label>최종 정답<select name="decision" required defaultValue={gold?.decision ?? ""}>
          <option value="" disabled>선택</option>{Object.entries(goldLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
        </select></label>
        <label>표본 유형<select name="sampling" defaultValue={gold?.sampling ?? "UNSPECIFIED"}>
          <option value="UNSPECIFIED">미분류</option><option value="RANDOM">무작위 표본</option><option value="ERROR_ANALYSIS">오류 분석</option>
        </select></label>
      </div>
      <details className={styles.disclosure}><summary>질문별 정답 입력 · 선택</summary><div className={styles.questionFields}>
        {questions.map((name) => <label key={name}>{questionLabels[name]}<select name={name} defaultValue={gold?.questions[name] ?? ""}>
          <option value="">미입력</option>{Object.entries(goldLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
        </select></label>)}
      </div></details>
      <label>평가 근거<textarea name="reason" required maxLength={500} rows={3} defaultValue={gold?.reason ?? ""} placeholder="사진에서 확인한 근거를 입력해 주세요." /></label>
      <div className={styles.actions}><button className={common.primaryButton} disabled={evaluation.isPending}>정답 평가 저장</button>
        {evaluation.isSuccess && <p className={styles.success} role="status">평가를 저장했어요.</p>}</div>
      {evaluation.isError && <p className={styles.error} role="alert">{errorText(evaluation.error)}</p>}
    </form>
  </section>;
}
