"use client";

import { useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import common from "@/shared/ui/management.module.css";
import { jevApi, type Agreement } from "./api";
import { questionLabels } from "./display";
import { Heading, State } from "./ui";
import styles from "./jev.module.css";

const day = 86_400_000;
const kstDate = (date: Date) => new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul" }).format(date);
export function buildMetricsRange(from: string, until: string) {
  const start = Date.parse(from + "T00:00:00+09:00");
  const end = Date.parse(until + "T00:00:00+09:00") + day;
  if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start || end - start > 31 * day) {
    throw new Error("시작일과 종료일을 확인해 주세요. 한 번에 최대 31일을 조회할 수 있어요.");
  }
  return { from: new Date(start).toISOString(), to: new Date(end).toISOString() };
}

export function JevMetrics() {
  const [initial] = useState(() => ({ start: kstDate(new Date(Date.now() - 6 * day)), end: kstDate(new Date()) }));
  const [query, setQuery] = useState(() => new URLSearchParams({ ...buildMetricsRange(initial.start, initial.end), preview: "false" }).toString());
  const [formError, setFormError] = useState("");
  const metrics = useQuery({ queryKey: ["jev", "metrics", query], queryFn: ({ signal }) => jevApi.metrics(new URLSearchParams(query), signal) });
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    try {
      setQuery(new URLSearchParams({ ...buildMetricsRange(String(values.get("start")), String(values.get("end"))), preview: String(values.get("preview")) }).toString());
      setFormError("");
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "조회 날짜를 확인해 주세요.");
    }
  }
  const result = metrics.data;
  return <>
    <Heading title="검수 성능 지표" subtitle="관리자 정답이 있는 표본의 일치율과 개별 제브 요청의 왕복 시간을 확인하세요." />
    <form className={common.filters} onSubmit={submit}>
      <label><span>시작일 · KST</span><input type="date" name="start" defaultValue={initial.start} required /></label>
      <label><span>종료일 · KST</span><input type="date" name="end" defaultValue={initial.end} required /></label>
      <label><span>검수 범위</span><select name="preview"><option value="false">운영</option><option value="true">시험</option></select></label>
      <button type="submit">조회</button>
    </form>
    {formError && <p className={styles.error} role="alert">{formError}</p>}
    {result ? <>
      {result.truncated && <p className={styles.warning} role="status">최대 조회 건수에 도달했어요. 더 짧은 기간으로 다시 조회해 주세요.</p>}
      <div className={styles.statGrid}>
        <Stat title="최종 추천 일치율" value={percent(result.finalAgreement.matchPercent)} note={`${result.finalAgreement.rated}건 중 ${result.finalAgreement.matched}건 일치`} />
        <Stat title="자동 오승인" value={`${result.falseAutoApprovals}건`} note={`정답이 있는 자동 승인 ${result.ratedAutoApprovals}건`} />
        <Stat title="반복 판정 일관성" value={percent(result.consistencyPercent)} note={`5회 이상 반복한 ${result.repeatedGroups}그룹 중 ${result.stableGroups}그룹 동일`} />
        <Stat title="제브 HTTP p99" value={result.usage.httpP99Ms == null ? "–" : `${result.usage.httpP99Ms.toLocaleString()} ms`} note={`시간이 확인된 물리 호출 ${result.usage.timedHttpCalls}건`} />
      </div>
      <section className={styles.panel}>
        <h2>검수 처리 현황</h2>
        <p className={styles.muted}>접수 {result.runs}건 · 완료 {result.completed}건 · 자동 승인 {result.autoApproved}건 · 관리자 검토 {result.adminReview}건 · 미반영 {result.skipped}건 · 기술 오류 {result.technicalErrors}건</p>
      </section>
      <section className={styles.panel}><h2>질문별 일치율</h2>
        <MetricRows rows={Object.entries(result.questions).map(([key, value]) => [questionLabels[key] ?? key, value])} />
      </section>
      <section className={styles.panel}><h2>표본별 최종 일치율</h2>
        <MetricRows rows={[["무작위 표본", result.bySampling.RANDOM], ["오류 분석", result.bySampling.ERROR_ANALYSIS], ["미분류", result.bySampling.UNSPECIFIED]]} />
      </section>
      <section className={styles.panel}>
        <h2>확인된 토큰 사용량</h2>
        <dl className={styles.meta}>
          <div><dt>입력 토큰</dt><dd>{result.usage.knownInputTokens.toLocaleString()} · 미확인 {result.usage.unknownInputUsageCalls}회</dd></div>
          <div><dt>출력 토큰</dt><dd>{result.usage.knownOutputTokens.toLocaleString()} · 미확인 {result.usage.unknownOutputUsageCalls}회</dd></div>
          <div><dt>HTTP가 확인된 호출</dt><dd>{result.usage.observedHttpCalls}회 / 호출 예약 {result.usage.reservedAttempts}회</dd></div>
        </dl>
      </section>
    </> : <State busy={metrics.isPending} error={metrics.error} retry={() => metrics.refetch()} />}
  </>;
}

const percent = (value: number | null) => value == null ? "–" : `${value.toLocaleString("ko-KR", { maximumFractionDigits: 1 })}%`;

function Stat({ title, value, note }: { title: string; value: string; note: string }) {
  return <article className={styles.stat}><span>{title}</span><strong>{value}</strong><small>{note}</small></article>;
}
function MetricRows({ rows }: { rows: [string, Agreement][] }) {
  return <div className={styles.tableWrap}><table className={styles.table}>
    <thead><tr><th scope="col">항목</th><th scope="col">일치율</th><th scope="col">일치 / 평가</th><th scope="col">오승인</th><th scope="col">오반려</th><th scope="col">모델 답변 누락</th></tr></thead>
    <tbody>{rows.map(([title, row]) => <tr key={title}><th scope="row">{title}</th><td>{percent(row.matchPercent)}</td><td>{row.matched} / {row.rated}</td><td>{row.falseApprovals}</td><td>{row.falseRejections}</td><td>{row.missingPredictions}</td></tr>)}</tbody>
  </table></div>;
}
