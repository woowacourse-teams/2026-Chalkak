"use client";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useRef, useState, type FormEvent } from "react";
import type { AiPromptPreviewResponse } from "@/shared/api/contracts";
import { ApiError } from "@/shared/api/errors";
import { isUuid } from "@/shared/api/uuid";
import { ConfirmDialog } from "@/shared/ui/confirm-dialog";
import { ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import { useToast } from "@/shared/ui/toast";
import { useAiPromptPreview, useAiPromptVersion, useAiPromptVersionMutations } from "../api/ai-prompt-hooks";
import {
  AI_PROMPT_TYPE_LABEL, DEFAULTS, LATEST_VERSION_APPLY_CAVEAT, MAX_OUTPUT_TOKENS_MAX, MEMO_MAX, SYSTEM_PROMPT_MAX, THRESHOLD_MAX,
  USER_PROMPT_TEMPLATE_MAX, TOPIC_TITLE_VARIABLE, checkPostId, codePointLength, draftFingerprint, draftFromVersion,
  emptyDraft, formatVersion, imageDetails, reasoningEfforts, toCreateRequest, toVersionInput, validateDraft,
  type AiPromptDraft, type AiPromptDraftErrors,
} from "../model/ai-prompt";
import { AiPromptPreviewResult } from "./ai-prompt-preview-result";
import common from "@/shared/ui/management.module.css";
import styles from "./ai-prompts.module.css";

export function AiPromptFormScreen() {
  const from = useSearchParams().get("from");
  const sourceId = from !== null && isUuid(from) ? from : null;
  const source = useAiPromptVersion(sourceId);
  if (sourceId !== null) {
    if (source.isPending) return <LoadingSkeleton rows={6} />;
    if (source.isError) {
      const notFound = source.error instanceof ApiError && source.error.status === 404;
      return <div className={common.page}>
        <Link className={common.back} href="/ai-prompts">← 목록으로</Link>
        <ErrorState title={notFound ? "불러올 버전을 찾을 수 없습니다" : undefined}
          description={source.error instanceof ApiError ? source.error.message : "불러올 버전을 가져오지 못했습니다."}
          onRetry={notFound ? undefined : () => { void source.refetch(); }} />
        <div className={common.actions}><Link className={common.secondaryButton} href="/ai-prompts/new">빈 양식으로 시작</Link></div>
      </div>;
    }
    return <AiPromptEditor key={sourceId} initial={draftFromVersion(source.data)} loadedVersion={source.data.version} />;
  }
  return <AiPromptEditor initial={emptyDraft()} invalidSource={from !== null} />;
}

function AiPromptEditor({ initial, loadedVersion, invalidSource = false }: { initial: AiPromptDraft; loadedVersion?: number; invalidSource?: boolean }) {
  const router = useRouter();
  const { showToast } = useToast();
  const preview = useAiPromptPreview();
  const { create } = useAiPromptVersionMutations();
  const [draft, setDraft] = useState(initial);
  const [errors, setErrors] = useState<AiPromptDraftErrors>({});
  const [postId, setPostId] = useState("");
  const [postIdError, setPostIdError] = useState<string | null>(null);
  // The fingerprint is taken when the preview starts, so edits made while waiting still invalidate it.
  const [previewed, setPreviewed] = useState<{ fingerprint: string; response: AiPromptPreviewResponse } | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [uncertain, setUncertain] = useState(false);
  const previewLock = useRef(false);
  const createLock = useRef(false);

  const stale = previewed !== null && previewed.fingerprint !== draftFingerprint(draft);
  const verified = previewed !== null && previewed.response.succeeded && !stale;
  const canCreate = verified && !uncertain && !create.isPending;

  function update<K extends keyof AiPromptDraft>(key: K, value: AiPromptDraft[K]) {
    setDraft((current) => ({ ...current, [key]: value }));
    setErrors((current) => (current[key] ? { ...current, [key]: undefined } : current));
  }
  function focusFirst(nextErrors: AiPromptDraftErrors, nextPostIdError: string | null) {
    const first = (Object.keys(nextErrors) as (keyof AiPromptDraft)[]).find((key) => nextErrors[key]);
    document.getElementById(first ? `ai-prompt-${first}` : "ai-prompt-postId")?.focus();
    return first !== undefined || nextPostIdError !== null;
  }

  async function runPreview(event: FormEvent) {
    event.preventDefault();
    if (previewLock.current) return;
    const nextErrors = validateDraft(draft);
    const nextPostIdError = checkPostId(postId);
    setErrors(nextErrors); setPostIdError(nextPostIdError);
    if (focusFirst(nextErrors, nextPostIdError)) return;
    previewLock.current = true;
    setPreviewed(null); setPreviewError(null);
    const fingerprint = draftFingerprint(draft);
    try {
      const response = await preview.mutateAsync({ ...toVersionInput(draft), postId: postId.trim() });
      setPreviewed({ fingerprint, response });
    } catch (cause) {
      setPreviewError(cause instanceof ApiError ? cause.message : "미리보기를 실행하지 못했습니다. 다시 시도해 주세요.");
    } finally { previewLock.current = false; }
  }

  function review() {
    if (!canCreate) return;
    const nextErrors = validateDraft(draft);
    setErrors(nextErrors); setCreateError(null);
    if (focusFirst(nextErrors, null)) return;
    setConfirming(true);
  }

  async function save() {
    if (createLock.current || !canCreate) return;
    createLock.current = true;
    setCreateError(null);
    try {
      const version = await create.mutateAsync(toCreateRequest(draft));
      showToast(`프롬프트 ${formatVersion(version.version)}을(를) 만들었습니다.`, "success");
      router.push(`/ai-prompts/${version.promptVersionId}`);
    } catch (cause) {
      createLock.current = false;
      // A timed-out or failed-upstream POST can already have committed. Never resend it automatically.
      if (cause instanceof ApiError && cause.kind === "api" && (cause.status ?? 0) < 500) setCreateError(cause.message);
      else { setUncertain(true); setConfirming(false); }
    }
  }

  const memoCount = codePointLength(draft.memo.trim());
  const counter = (id: string, length: number, max: number, suffix = "") =>
    <span className={styles.count} data-invalid={length > max} id={id}>{length.toLocaleString()} / {max.toLocaleString()}자{suffix}</span>;
  const error = (key: keyof AiPromptDraft) => errors[key] ? <span className={common.formError} role="alert">{errors[key]}</span> : null;
  const busy = create.isPending;

  return <div className={common.page}>
    <Link className={common.back} href="/ai-prompts">← 목록으로</Link>
    <h2 className={common.pageTitle}>새 프롬프트 버전 만들기</h2>
    <div className={styles.notice}>
      <p>먼저 아래에서 게시물로 미리보기를 실행해 결과를 확인한 뒤 버전을 만들 수 있습니다.</p>
      <p>만든 버전은 {AI_PROMPT_TYPE_LABEL}의 가장 최신 버전이 되어 바로 적용되며({LATEST_VERSION_APPLY_CAVEAT}), 수정하거나 삭제할 수 없습니다.</p>
    </div>
    {loadedVersion !== undefined ? <p className={common.readOnly} role="status">{formatVersion(loadedVersion)} 내용을 불러왔습니다. 메모는 새로 입력해 주세요.</p> : null}
    {invalidSource ? <p className={common.readOnly} role="status">불러올 버전 ID가 올바르지 않아 빈 양식으로 시작합니다.</p> : null}
    <form className={common.form} aria-label="프롬프트 내용" noValidate onSubmit={(event) => event.preventDefault()}>
      <fieldset className={styles.fields} disabled={busy}>
        <legend className={styles.srOnly}>프롬프트 내용</legend>
        <label><span>시스템 프롬프트</span>
          <textarea id="ai-prompt-systemPrompt" className={`${styles.textArea} ${styles.large}`} value={draft.systemPrompt} rows={14} aria-invalid={Boolean(errors.systemPrompt)} aria-describedby="ai-prompt-systemPrompt-count"
            placeholder="사진을 평가하는 방식과 어조를 설명해 주세요. {{...}} 변수는 사용할 수 없습니다." onChange={(event) => update("systemPrompt", event.target.value)} /></label>
        {counter("ai-prompt-systemPrompt-count", draft.systemPrompt.length, SYSTEM_PROMPT_MAX)}
        {error("systemPrompt")}
        <label><span>사용자 프롬프트 템플릿</span>
          <textarea id="ai-prompt-userPromptTemplate" className={styles.textArea} value={draft.userPromptTemplate} rows={4} aria-invalid={Boolean(errors.userPromptTemplate)} aria-describedby="ai-prompt-userPromptTemplate-count ai-prompt-userPromptTemplate-help"
            onChange={(event) => update("userPromptTemplate", event.target.value)} /></label>
        {counter("ai-prompt-userPromptTemplate-count", draft.userPromptTemplate.length, USER_PROMPT_TEMPLATE_MAX)}
        <p className={styles.helper} id="ai-prompt-userPromptTemplate-help">사용할 수 있는 변수는 {TOPIC_TITLE_VARIABLE} 하나뿐이며 주제 제목으로 바뀝니다. 값은 JSON 문자열로 이스케이프되어 들어가므로 {`{"주제": "${TOPIC_TITLE_VARIABLE}"}`}처럼 JSON 안에 써도 됩니다.</p>
        {error("userPromptTemplate")}
        <label><span>모델</span>
          <input id="ai-prompt-model" type="text" value={draft.model} placeholder="예: gpt-6.1-sol" aria-invalid={Boolean(errors.model)} aria-describedby="ai-prompt-model-help"
            onChange={(event) => update("model", event.target.value)} /></label>
        <p className={styles.helper} id="ai-prompt-model-help">서버에서 허용한 모델만 사용할 수 있습니다. 허용되지 않은 모델이면 실행할 때 안내됩니다.</p>
        {error("model")}
        <div className={styles.paramGrid}>
          <label><span>이미지 상세도</span>
            <select id="ai-prompt-imageDetail" className={styles.select} value={draft.imageDetail} onChange={(event) => update("imageDetail", event.target.value as AiPromptDraft["imageDetail"])}>
              {imageDetails.map((value) => <option key={value} value={value}>{value}{value === DEFAULTS.imageDetail ? " (기본값)" : ""}</option>)}
            </select></label>
          <label><span>추론 강도</span>
            <select id="ai-prompt-reasoningEffort" className={styles.select} value={draft.reasoningEffort} onChange={(event) => update("reasoningEffort", event.target.value as AiPromptDraft["reasoningEffort"])}>
              <option value="">모델 기본값</option>
              {reasoningEfforts.map((value) => <option key={value} value={value}>{value}</option>)}
            </select></label>
          <div>
            <label><span>최대 출력 토큰</span>
              <input id="ai-prompt-maxOutputTokens" type="text" inputMode="numeric" value={draft.maxOutputTokens} aria-invalid={Boolean(errors.maxOutputTokens)} aria-describedby="ai-prompt-maxOutputTokens-help"
                onChange={(event) => update("maxOutputTokens", event.target.value)} /></label>
            <p className={styles.helper} id="ai-prompt-maxOutputTokens-help">1~{MAX_OUTPUT_TOKENS_MAX.toLocaleString()} · 추론 토큰을 포함합니다. 비우면 기본값 {DEFAULTS.maxOutputTokens.toLocaleString()}입니다.</p>
            {error("maxOutputTokens")}
          </div>
          <div>
            <label><span>독특한 시각 기준 점수</span>
              <input id="ai-prompt-distinctivePerspectiveThreshold" type="text" inputMode="decimal" value={draft.distinctivePerspectiveThreshold} aria-invalid={Boolean(errors.distinctivePerspectiveThreshold)} aria-describedby="ai-prompt-distinctivePerspectiveThreshold-help"
                onChange={(event) => update("distinctivePerspectiveThreshold", event.target.value)} /></label>
            <p className={styles.helper} id="ai-prompt-distinctivePerspectiveThreshold-help">0~{THRESHOLD_MAX} · 주제 표현 점수가 이 값 이상이면 독특한 시각으로 봅니다. 비우면 기본값 {DEFAULTS.distinctivePerspectiveThreshold.toFixed(1)}입니다.</p>
            {error("distinctivePerspectiveThreshold")}
          </div>
        </div>
        <label><span>메모 (선택)</span>
          <textarea id="ai-prompt-memo" className={styles.textArea} value={draft.memo} rows={2} aria-invalid={Boolean(errors.memo)} aria-describedby="ai-prompt-memo-count"
            placeholder="이 버전에서 바꾼 점을 적어 두세요" onChange={(event) => update("memo", event.target.value)} /></label>
        {counter("ai-prompt-memo-count", memoCount, MEMO_MAX, " · 앞뒤 공백 제외")}
        {error("memo")}
      </fieldset>
    </form>
    <form className={styles.previewPanel} aria-label="미리보기" noValidate onSubmit={(event) => { void runPreview(event); }}>
      <h3>미리보기</h3>
      <p className={styles.helper}>공개된(승인된) 게시물 한 건으로 현재 입력한 프롬프트를 실행해 봅니다. 결과는 저장되지 않으며 버전을 만들지 않습니다.</p>
      <div className={styles.previewRow}>
        <label><span>게시물 ID</span>
          <input id="ai-prompt-postId" type="text" value={postId} placeholder="게시물 UUID" autoComplete="off" spellCheck={false} aria-invalid={postIdError !== null} aria-describedby="ai-prompt-postId-help"
            onChange={(event) => { setPostId(event.target.value); setPostIdError(null); }} /></label>
        <button className={common.primaryButton} type="submit" disabled={preview.isPending}>{preview.isPending ? "미리보기 실행 중…" : "미리보기 실행"}</button>
      </div>
      <p className={styles.helper} id="ai-prompt-postId-help"><a href="/posts?status=APPROVED" target="_blank" rel="noopener noreferrer">승인된 게시물 목록 ↗</a>에서 게시물을 열면 주소 끝의 값이 게시물 ID입니다.</p>
      {postIdError ? <span className={common.formError} role="alert">{postIdError}</span> : null}
      {preview.isPending ? <p className={styles.progress} role="status">AI 응답을 기다리고 있습니다. 최대 1분 정도 걸릴 수 있으니 이 화면을 닫지 말아 주세요.</p> : null}
      {previewError ? <p className={common.formError} role="alert">{previewError}</p> : null}
      {previewed ? <AiPromptPreviewResult response={previewed.response} /> : null}
      {stale ? <p className={styles.stale} role="status">미리보기 이후 프롬프트 내용이 바뀌었습니다. 다시 미리보기를 실행해 주세요.</p> : null}
    </form>
    {uncertain ? <div role="alert" className={styles.failure}>
      <p>버전 생성 결과를 확인하지 못했습니다.</p>
      <span>응답을 받지 못했더라도 만들어졌을 수 있습니다. 중복 생성을 막기 위해 자동으로 다시 시도하지 않으니 <Link href="/ai-prompts">버전 목록을 확인해 주세요.</Link></span>
    </div> : null}
    <div className={common.formActions}>
      <Link className={common.secondaryButton} href="/ai-prompts">취소</Link>
      <button className={common.primaryButton} type="button" disabled={!canCreate} aria-describedby="ai-prompt-gate" onClick={review}>버전 만들기</button>
    </div>
    <p className={styles.helper} id="ai-prompt-gate">{verified
      ? "현재 내용으로 성공한 미리보기를 확인했습니다. 메모와 게시물 ID는 바꿔도 다시 실행할 필요가 없습니다."
      : stale ? "미리보기 이후 내용이 바뀌어 다시 미리보기를 실행해야 합니다."
        : "성공한 미리보기를 실행한 뒤에 버전을 만들 수 있습니다."}</p>
    <ConfirmDialog open={confirming} title="새 버전을 만들까요?"
      description={`만든 버전은 ${AI_PROMPT_TYPE_LABEL}의 가장 최신 버전이 되어 바로 적용되며(${LATEST_VERSION_APPLY_CAVEAT}), 수정하거나 삭제할 수 없습니다.\n모델: ${draft.model.trim()}\n메모: ${draft.memo.trim() || "-"}`}
      confirmLabel="만들기" pending={create.isPending} error={createError}
      onCancel={() => { setConfirming(false); setCreateError(null); }} onConfirm={() => { void save(); }} />
  </div>;
}
