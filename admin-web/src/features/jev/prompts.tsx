"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchAdminPosts } from "@/features/posts/api/post-api";
import { fetchAdminTopics } from "@/features/topics/api/topic-api";
import { classificationExample, photoExample } from "@/mocks/jev-fixtures";
import { ConfirmDialog } from "@/shared/ui/confirm-dialog";
import { StatusBadge } from "@/shared/ui/status-badge";
import common from "@/shared/ui/management.module.css";
import { jevApi, jevModel, type PromptConfig, type Role } from "./api";
import { displayDate, errorText, roleLabels } from "./display";
import { Heading, QuestionResults, Recommendation, State } from "./ui";
import styles from "./jev.module.css";

const classification: Role = "JEV_TOPIC_CLASSIFICATION";
const photo: Role = "JEV_PHOTO_MODERATION";

function parseConfig(value: FormDataEntryValue | null): PromptConfig {
  try {
    const candidate = JSON.parse(String(value)) as PromptConfig;
    if (candidate && typeof candidate === "object" && !Array.isArray(candidate) &&
        candidate.questions && typeof candidate.questions === "object" && !Array.isArray(candidate.questions)) return candidate;
  } catch {
    // Keep the invalid JSON in the editor so it can be corrected.
  }
  throw new Error("프롬프트를 questions 객체가 포함된 JSON으로 입력해 주세요.");
}

export function JevPrompts() {
  const [mode, setMode] = useState<"versions" | "preview">("versions");
  return <>
    <Heading title="제브 프롬프트" subtitle="주제 분류와 사진 검수 기준을 관리하고 초안으로 검수 결과를 시험하세요." />
    <div className={styles.actions}>
      <button className={mode === "versions" ? common.primaryButton : common.secondaryButton} onClick={() => setMode("versions")}>저장 버전</button>
      <button className={mode === "preview" ? common.primaryButton : common.secondaryButton} onClick={() => setMode("preview")}>초안 시험</button>
    </div>
    {mode === "versions" ? <Versions /> : <Preview />}
  </>;
}

function Versions() {
  const [role, setRole] = useState<Role>(classification);
  const [page, setPage] = useState(1);
  const [selected, setSelected] = useState("");
  const [editing, setEditing] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const [message, setMessage] = useState("");
  const [formError, setFormError] = useState("");
  const client = useQueryClient();
  const list = useQuery({ queryKey: ["jev", "versions", role, page], queryFn: ({ signal }) => jevApi.prompts(role, page, signal) });
  const detail = useQuery({ queryKey: ["jev", "prompt", selected], queryFn: ({ signal }) => jevApi.prompt(selected, signal), enabled: !!selected });
  const prompt = detail.data;
  const save = useMutation({
    mutationFn: jevApi.createPrompt,
    onSuccess: async (saved) => {
      setSelected(saved.id);
      setEditing(false);
      setPage(1);
      setMessage(`v${saved.version}을 저장했어요. 운영 적용은 버전 상세에서 할 수 있어요.`);
      await client.invalidateQueries({ queryKey: ["jev", "versions"] });
    },
  });
  const activate = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) => jevApi.activate(id, active),
    onSuccess: async (version) => {
      client.setQueryData(["jev", "prompt", version.id], version);
      await Promise.all([
        client.invalidateQueries({ queryKey: ["jev", "versions"] }),
        client.invalidateQueries({ queryKey: ["jev", "prompt"] }),
      ]);
      setConfirm(false);
      setMessage(version.active ? `v${version.version}을 운영에 적용했어요.` : "이 역할의 운영 적용을 중단했어요.");
    },
  });
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const fields = new FormData(event.currentTarget);
    try {
      save.mutate({ role, model: jevModel, configuration: parseConfig(fields.get("configuration")), memo: String(fields.get("memo") ?? "").trim() || null });
      setFormError("");
    } catch (error) { setFormError(errorText(error)); }
  }
  return <>
    <div className={styles.actions}>
      <label className={styles.inlineLabel}>프롬프트 용도 <select value={role} onChange={(event) => {
        setRole(event.target.value as Role); setPage(1); setSelected(""); setEditing(false); setMessage(""); save.reset();
      }}><option value={classification}>주제 분류</option><option value={photo}>사진 검수</option></select></label>
      <button className={common.primaryButton} onClick={() => { setSelected(""); setMessage(""); save.reset(); setEditing(true); }}>새 버전 작성</button>
      {message && <p role="status" className={styles.success}>{message}</p>}
    </div>
    <div className={styles.detailGrid}>
      <section className={styles.panel}>
        <h2>{roleLabels[role]} 버전</h2>
        {list.isPending || list.isError || !list.data?.promptVersions.length ?
          <State busy={list.isPending} error={list.error} retry={() => list.refetch()} empty="저장된 버전이 없어요." /> :
          <div className={styles.versionList}>{list.data.promptVersions.map((version) => <button type="button" key={version.id}
            aria-pressed={selected === version.id} onClick={() => { setSelected(version.id); setEditing(false); }}>
            <span><strong>v{version.version}</strong><small>{version.memo ?? displayDate(version.createdAt)}</small></span>
            <StatusBadge tone={version.active ? "success" : "neutral"}>{version.active ? "운영 적용 중" : "저장됨"}</StatusBadge>
          </button>)}</div>}
        <div className={styles.pager}><button className={common.secondaryButton} disabled={page === 1} onClick={() => setPage(page - 1)}>이전</button>
          <span>{page} 페이지</span><button className={common.secondaryButton} disabled={!list.data?.hasNext} onClick={() => setPage(page + 1)}>다음</button></div>
      </section>
      <section className={styles.panel}>
        {editing ? <>
          <h2>새 {roleLabels[role]} 프롬프트</h2>
          <form className={styles.form} onSubmit={submit}>
            <label>변경 메모<input name="memo" maxLength={500} /></label>
            <label>프롬프트 JSON<textarea name="configuration" rows={15} spellCheck={false} required key={selected}
              defaultValue={prompt ? JSON.stringify(prompt.configuration, null, 2) : ""} /></label>
            {(formError || save.isError) && <p className={styles.error} role="alert">{formError || errorText(save.error)}</p>}
            <div className={styles.actions}><button className={common.primaryButton} disabled={save.isPending}>버전 저장</button>
              <button className={common.secondaryButton} type="button" onClick={() => setEditing(false)}>취소</button></div>
          </form>
        </> : selected && !prompt ? <State busy={detail.isPending} error={detail.error} retry={() => detail.refetch()} /> : prompt ? <>
          <h2>v{prompt.version} · {prompt.model}</h2>
          <div className={styles.actions}>
            <StatusBadge tone={prompt.active ? "success" : "neutral"}>{prompt.active ? "운영 적용 중" : "저장됨"}</StatusBadge>
            <button className={common.secondaryButton} onClick={() => { activate.reset(); setConfirm(true); }}>{prompt.active ? "운영 적용 중단" : "운영에 적용"}</button>
            <button className={common.secondaryButton} onClick={() => { save.reset(); setFormError(""); setEditing(true); }}>복사해 새 버전 작성</button>
          </div>
          <pre className={styles.raw}>{JSON.stringify(prompt.configuration, null, 2)}</pre>
          <ConfirmDialog open={confirm} title={prompt.active ? "운영 적용 중단" : "운영 프롬프트 적용"}
            description={prompt.active ? "이 역할을 중단하면 진행 중이던 자동 검수가 관리자 검토로 넘어갈 수 있어요." : `v${prompt.version}을 이후 신규 검수에 적용해요.`}
            confirmLabel={prompt.active ? "중단" : "적용"} pending={activate.isPending} destructive={prompt.active}
            error={activate.error ? errorText(activate.error) : null} onCancel={() => setConfirm(false)}
            onConfirm={() => activate.mutate({ id: prompt.id, active: !prompt.active })} />
        </> : <State empty="버전을 선택하면 프롬프트 원문과 적용 상태를 볼 수 있어요." />}
      </section>
    </div>
  </>;
}

function Preview() {
  const [jobId, setJobId] = useState("");
  const [formError, setFormError] = useState("");
  const posts = useQuery({ queryKey: ["jev", "preview-posts"], queryFn: ({ signal }) => fetchAdminPosts({status:"APPROVED", sort:"createdAtDesc", page:1, pageSize:30}, signal) });
  const topics = useQuery({ queryKey: ["jev", "preview-topics"], queryFn: ({ signal }) => fetchAdminTopics({sort:"topicDateDesc", page:1, pageSize:30}, signal) });
  const start = useMutation({ mutationFn: jevApi.createPreview, onSuccess: (run) => setJobId(run.jobId) });
  const run = useQuery({ queryKey: ["jev", "preview", jobId], queryFn: ({ signal }) => jevApi.preview(jobId, signal), enabled: !!jobId,
    refetchInterval: ({ state }) => state.data?.finishedAt ? false : 3000 });
  const busy = start.isPending || (!!jobId && !run.data?.finishedAt && !run.isError);
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const fields = new FormData(event.currentTarget);
    try {
      const draft = { model: jevModel, classification: parseConfig(fields.get("classification")), photo: parseConfig(fields.get("photo")) };
      setFormError("");
      setJobId("");
      start.mutate({ postId: String(fields.get("postId")), topicId: String(fields.get("topicId")), draft });
    } catch (error) { setFormError(errorText(error)); }
  }
  return <>
    <section className={styles.panel}>
      <h2>초안 시험 실행</h2>
      <p className={styles.muted}>공개 사진과 주제를 선택하고 두 프롬프트의 JSON을 붙여 넣으세요. 실제 게시물 상태와 운영 프롬프트는 유지돼요.</p>
      <form className={styles.form} onSubmit={submit}>
        <div className={styles.detailGrid}>
          <label>시험할 공개 게시물<select name="postId" required defaultValue=""><option value="">공개 게시물 선택</option>
            {posts.data?.posts.filter((post) => post.moderationStatus === "APPROVED" && !post.deletedAt).map((post) => <option value={post.postId} key={post.postId}>{post.title || post.postId}</option>)}
          </select></label>
          <label>시험 주제<select name="topicId" required defaultValue=""><option value="">주제 선택</option>
            {topics.data?.topics.map((topic) => <option value={topic.topicId} key={topic.topicId}>{topic.title}</option>)}
          </select></label>
        </div>
        {(posts.isError || topics.isError) && <p role="alert" className={styles.error}>{errorText(posts.error ?? topics.error)}</p>}
        <div className={styles.detailGrid}>
          <label>주제 분류 초안<textarea name="classification" required rows={10} spellCheck={false} defaultValue={process.env.NEXT_PUBLIC_API_MODE === "mock" ? JSON.stringify(classificationExample, null, 2) : ""} /></label>
          <label>사진 검수 초안<textarea name="photo" required rows={10} spellCheck={false} defaultValue={process.env.NEXT_PUBLIC_API_MODE === "mock" ? JSON.stringify(photoExample, null, 2) : ""} /></label>
        </div>
        <div className={styles.actions}><button className={common.primaryButton} disabled={busy}>{busy ? "시험 실행 중…" : "시험 시작"}</button></div>
        {(formError || start.isError) && <p className={styles.error} role="alert">{formError || errorText(start.error)}</p>}
      </form>
    </section>
    {jobId && <section className={styles.panel}>
      <h2>시험 결과</h2>
      {run.isError ? <State error={run.error} retry={() => run.refetch()} /> : !run.data?.finishedAt ? <State busy /> : <>
        <Recommendation value={run.data.evaluation?.recommendation} />
        <QuestionResults evaluation={run.data.evaluation} />
        <Link className={styles.textLink} href={`/posts/jev/${jobId}`}>호출 기록과 시험 상세 보기 →</Link>
      </>}
    </section>}
  </>;
}
