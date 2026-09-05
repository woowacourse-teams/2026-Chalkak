"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";

import { formatInstant } from "@/features/posts/model/post-display";
import { ApiError } from "@/shared/api/errors";
import type { AdminAuditLogResponse } from "@/shared/api/contracts";
import { queryKeys } from "@/shared/query/query-client";
import { EmptyState, ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import { Pagination } from "@/shared/ui/pagination";

import { fetchAdminAuditLogs } from "../api/audit-log-api";
import styles from "./audit-logs.module.css";

const actions: Record<string, string> = {
  POST_APPROVED: "게시물 승인", POST_REJECTED: "게시물 거절", POST_DELETED: "게시물 삭제",
  USER_BANNED: "사용자 차단", USER_UNBANNED: "사용자 차단 해제",
  TOPIC_CREATED: "주제 등록", TOPIC_UPDATED: "주제 수정", TOPIC_DELETED: "주제 삭제",
};
const targetTypes = { POST: "게시물", USER: "사용자", TOPIC: "주제" };
const targetPaths = { POST: "posts", USER: "users", TOPIC: "topics" };

function targetHref(log: AdminAuditLogResponse, returnTo: string) {
  return "/" + targetPaths[log.targetType] + "/" + encodeURIComponent(log.targetId) + "?" + new URLSearchParams({ returnTo });
}

function AuditFilters({ action, targetType, onApply }: {
  action: string; targetType: string; onApply: (action: string, targetType: string) => void;
}) {
  const [selectedAction, setSelectedAction] = useState(action);
  const [selectedTarget, setSelectedTarget] = useState(targetType);
  const submit = (event: FormEvent) => {
    event.preventDefault();
    onApply(selectedAction, selectedTarget);
  };
  return <form className={styles.filters} onSubmit={submit}>
    <label>작업<select value={selectedAction} onChange={(event) => setSelectedAction(event.target.value)}>
      <option value="">전체 작업</option>
      {Object.entries(actions).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
    </select></label>
    <label>대상<select value={selectedTarget} onChange={(event) => setSelectedTarget(event.target.value)}>
      <option value="">전체 대상</option>
      {Object.entries(targetTypes).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
    </select></label>
    <button type="submit">조회</button>
  </form>;
}

export function AuditLogScreen() {
  const router = useRouter();
  const params = useSearchParams();
  const rawAction = params.get("action") ?? "";
  const action = Object.hasOwn(actions, rawAction) ? rawAction : "";
  const rawTarget = params.get("targetType") ?? "";
  const targetType = Object.hasOwn(targetTypes, rawTarget) ? rawTarget : "";
  const rawPage = Number(params.get("page") ?? 1);
  const page = Number.isSafeInteger(rawPage) && rawPage >= 1 ? rawPage : 1;
  const filters = { action: action || undefined, targetType: targetType || undefined, page };
  const query = useQuery({
    queryKey: [...queryKeys.auditLogs, filters],
    queryFn: ({ signal }) => fetchAdminAuditLogs(filters, signal),
  });
  const returnTo = "/audit-logs" + (params.size ? "?" + params.toString() : "");
  const navigate = (nextPage: number, nextAction = action, nextTarget = targetType) => {
    const next = new URLSearchParams({ page: String(nextPage) });
    if (nextAction) next.set("action", nextAction);
    if (nextTarget) next.set("targetType", nextTarget);
    router.push("/audit-logs?" + next);
  };

  return <div className={styles.page}>
    <h2 className={styles.pageTitle}>처리 이력</h2>
    <p className={styles.description}>관리자가 처리한 작업과 사유를 최신순으로 확인합니다.</p>
    <AuditFilters key={`${action}:${targetType}`} action={action} targetType={targetType} onApply={(nextAction, nextTarget) => navigate(1, nextAction, nextTarget)} />
    {query.isPending ? <LoadingSkeleton /> : null}
    {query.isError ? <ErrorState description={query.error instanceof ApiError ? query.error.message : "처리 이력을 불러오지 못했습니다."} onRetry={() => { void query.refetch(); }} /> : null}
    {query.data?.auditLogs.length === 0 ? <EmptyState title="처리 이력이 없습니다" description="선택한 조건에 해당하는 작업이 없습니다." /> : null}
    {query.data?.auditLogs.length ? <ol aria-label="관리자 처리 이력" className={styles.list}>
      {query.data.auditLogs.map((log) => <li className={styles.entry} key={log.auditLogId}>
        <div className={styles.entryHeading}>
          <strong>{actions[log.action] ?? log.action}</strong>
          <time dateTime={log.occurredAt}>{formatInstant(log.occurredAt)}</time>
        </div>
        <div className={styles.entryMeta}>
          <span>처리자 {log.actorUsername}</span>
          <Link href={targetHref(log, returnTo)}>{targetTypes[log.targetType]} 보기 →</Link>
        </div>
        {log.reason ? <p className={styles.reason}>{log.reason}</p> : null}
        <details className={styles.states}>
          <summary>변경 내용</summary>
          <div><section><h2>변경 전</h2><pre>{JSON.stringify(log.beforeState, null, 2)}</pre></section>
            <section><h2>변경 후</h2><pre>{JSON.stringify(log.afterState, null, 2)}</pre></section></div>
        </details>
      </li>)}
    </ol> : null}
    {query.data ? <Pagination currentPage={query.data.currentPage} hasNext={query.data.hasNext} onPageChange={(nextPage) => navigate(nextPage)} /> : null}
  </div>;
}
