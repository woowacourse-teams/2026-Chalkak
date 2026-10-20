"use client";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { formatInstant } from "@/features/posts/model/post-display";
import { ApiError } from "@/shared/api/errors";
import { EmptyState, ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import { Pagination } from "@/shared/ui/pagination";
import { StatusBadge } from "@/shared/ui/status-badge";
import { Table, type TableColumn } from "@/shared/ui/table";
import type { AiPromptVersionSummary } from "@/shared/api/contracts";
import { useAiPromptVersions } from "../api/ai-prompt-hooks";
import { AI_PROMPT_TYPE, AI_PROMPT_TYPE_LABEL, formatVersion } from "../model/ai-prompt";
import common from "@/shared/ui/management.module.css";
import styles from "./ai-prompts.module.css";

export function AiPromptListScreen() {
  const router = useRouter();
  const params = useSearchParams();
  const rawPage = Number(params.get("page") ?? 1);
  const page = Number.isSafeInteger(rawPage) && rawPage > 0 && rawPage <= 107374183 ? rawPage : 1;
  const query = useAiPromptVersions(page);
  const columns: TableColumn<AiPromptVersionSummary>[] = [
    { id: "version", header: "버전", render: (row) => <span className={styles.versionCell}>
      <Link href={`/ai-prompts/${row.promptVersionId}`}>{formatVersion(row.version)}</Link>
      {page === 1 && row.promptVersionId === query.data?.promptVersions[0]?.promptVersionId ? <StatusBadge tone="success">최신</StatusBadge> : null}
    </span> },
    { id: "model", header: "모델", render: (row) => row.model },
    { id: "schema", header: "출력 스키마", render: (row) => `v${row.outputSchemaVersion}` },
    { id: "memo", header: "메모", render: (row) => row.memo ? <span className={styles.memo}>{row.memo}</span> : <span className={styles.muted}>-</span> },
    { id: "createdAt", header: "작성 시각 (KST)", render: (row) => <time dateTime={row.createdAt}>{formatInstant(row.createdAt)}</time> },
  ];
  return <div className={common.page}>
    <div className={common.toolbar}><h2 className={common.pageTitle}>AI 프롬프트</h2><Link className={common.primaryButton} href="/ai-prompts/new">새 버전 만들기</Link></div>
    <div className={styles.intro}>
      <span className={styles.typeLabel}>{AI_PROMPT_TYPE_LABEL} · {AI_PROMPT_TYPE}</span>
      <p>사진 AI 리뷰에 사용하는 프롬프트를 버전으로 관리합니다.</p>
      <span>버전을 만들면 가장 최신 버전이 바로 사용자 기능에 적용됩니다(백엔드 #689 적용 이후). 버전은 수정하거나 삭제할 수 없으므로 미리보기로 확인한 내용만 만들어 주세요.</span>
    </div>
    <div className={styles.heading}><span>버전 최신순</span><Link href="/audit-logs?targetType=AI_PROMPT_VERSION">AI 프롬프트 처리 이력 →</Link></div>
    {query.isPending ? <LoadingSkeleton rows={3} /> : null}
    {query.isError ? <ErrorState description={query.error instanceof ApiError ? query.error.message : "AI 프롬프트 버전을 불러오지 못했습니다."} onRetry={() => { void query.refetch(); }} /> : null}
    {!query.isError && query.data?.promptVersions.length === 0 ? <EmptyState title="만든 프롬프트 버전이 없습니다" description={page === 1 ? "버전이 하나도 없으면 사진 AI 리뷰 기능은 꺼진 상태입니다." : "이전 페이지를 확인해 주세요."} /> : null}
    {!query.isError && query.data?.promptVersions.length ? <Table caption="AI 프롬프트 버전 목록" columns={columns} getRowKey={(row) => row.promptVersionId} rows={query.data.promptVersions} /> : null}
    {!query.isError && query.data ? <Pagination currentPage={query.data.currentPage} hasNext={query.data.hasNext} onPageChange={(next) => router.push(`/ai-prompts?page=${next}`)} /> : null}
  </div>;
}
