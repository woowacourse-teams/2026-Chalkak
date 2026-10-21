"use client";
import Link from "next/link";
import { formatInstant } from "@/features/posts/model/post-display";
import { ApiError } from "@/shared/api/errors";
import { ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import type { AiPromptVersionDetail } from "@/shared/api/contracts";
import { useAiPromptVersion } from "../api/ai-prompt-hooks";
import { AI_PROMPT_TYPE_LABEL, DEFAULTS, formatVersion, toPlainDecimal } from "../model/ai-prompt";
import common from "@/shared/ui/management.module.css";
import styles from "./ai-prompts.module.css";

function isDefault(value: unknown, defaultValue: unknown) {
  return value === defaultValue ? <span className={styles.defaultNote}> (기본값)</span> : null;
}

function Parameters({ version }: { version: AiPromptVersionDetail }) {
  const { parameters } = version;
  return <dl className={common.meta}>
    <div><dt>이미지 상세도</dt><dd>{parameters.imageDetail}{isDefault(parameters.imageDetail, DEFAULTS.imageDetail)}</dd></div>
    <div><dt>최대 출력 토큰</dt><dd>{parameters.maxOutputTokens.toLocaleString()}{isDefault(parameters.maxOutputTokens, DEFAULTS.maxOutputTokens)}</dd></div>
    <div><dt>추론 강도</dt><dd>{parameters.reasoningEffort ?? "모델 기본값"}</dd></div>
    <div><dt>독특한 시각 기준 점수</dt><dd>{toPlainDecimal(parameters.distinctivePerspectiveThreshold)}{isDefault(parameters.distinctivePerspectiveThreshold, DEFAULTS.distinctivePerspectiveThreshold)}</dd></div>
  </dl>;
}

export function AiPromptDetailScreen({ promptVersionId }: { promptVersionId: string }) {
  const query = useAiPromptVersion(promptVersionId);
  const version = query.data;
  const notFound = query.error instanceof ApiError && query.error.status === 404;
  return <div className={common.page}>
    <Link className={common.back} href="/ai-prompts">← 목록으로</Link>
    {query.isPending ? <LoadingSkeleton rows={4} /> : null}
    {query.isError ? notFound
      ? <ErrorState title="프롬프트 버전을 찾을 수 없습니다" description="삭제되었거나 존재하지 않는 버전입니다. 목록에서 다시 선택해 주세요." />
      : <ErrorState description={query.error instanceof ApiError ? query.error.message : "프롬프트 버전을 불러오지 못했습니다."} onRetry={() => { void query.refetch(); }} /> : null}
    {!query.isError && version ? <>
      <div className={common.toolbar}>
        <h2 className={common.pageTitle}>프롬프트 {formatVersion(version.version)}</h2>
        <span className={styles.typeLabel}>{AI_PROMPT_TYPE_LABEL} · {version.type}</span>
      </div>
      <section className={common.panel}><h3>버전 정보</h3><dl className={common.meta}>
        <div><dt>모델</dt><dd>{version.model}</dd></div>
        <div><dt>출력 스키마</dt><dd>v{version.outputSchemaVersion}</dd></div>
        <div><dt>메모</dt><dd>{version.memo ?? "-"}</dd></div>
        <div><dt>작성 시각 (KST)</dt><dd><time dateTime={version.createdAt}>{formatInstant(version.createdAt)}</time></dd></div>
        <div><dt>작성 관리자 ID</dt><dd>{version.createdByAdminId}</dd></div>
        <div><dt>버전 ID</dt><dd>{version.promptVersionId}</dd></div>
      </dl></section>
      <section className={common.panel}><h3>파라미터</h3><Parameters version={version} /></section>
      <section className={common.panel}>
        <div className={styles.promptHeading}><h3>시스템 프롬프트</h3><span>{version.systemPrompt.length.toLocaleString()}자</span></div>
        <pre className={styles.prompt} tabIndex={0} aria-label="시스템 프롬프트 내용">{version.systemPrompt}</pre>
      </section>
      <section className={common.panel}>
        <div className={styles.promptHeading}><h3>사용자 프롬프트 템플릿</h3><span>{version.userPromptTemplate.length.toLocaleString()}자</span></div>
        <pre className={styles.prompt} tabIndex={0} aria-label="사용자 프롬프트 템플릿 내용">{version.userPromptTemplate}</pre>
      </section>
      <p className={common.readOnly}>버전은 수정하거나 삭제할 수 없습니다. 내용을 바꾸려면 이 내용을 바탕으로 새 버전을 만들어 주세요.</p>
      <div className={common.actions}><Link className={common.primaryButton} href={`/ai-prompts/new?from=${encodeURIComponent(version.promptVersionId)}`}>이 내용으로 새 버전 만들기</Link></div>
    </> : null}
  </div>;
}
