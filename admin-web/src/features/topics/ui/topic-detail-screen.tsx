"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useRef, useState } from "react";

import { getSafeReturnTo } from "@/features/auth/model/return-to";
import { formatInstant } from "@/features/posts/model/post-display";
import { PostCountLinks } from "@/features/posts/ui/post-count-links";
import { ConfirmDialog } from "@/shared/ui/confirm-dialog";
import { ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import { StatusBadge } from "@/shared/ui/status-badge";
import { useToast } from "@/shared/ui/toast";

import { useAdminTopic, useDeleteAdminTopic, useUpdateAdminTopic } from "../api/topic-hooks";
import { getTopicErrorMessage, topicPhaseDisplay } from "../model/topic-display";
import { TopicForm } from "./topic-form";
import styles from "@/shared/ui/management.module.css";

export function TopicDetailScreen({ topicId }: { topicId: string }) {
  const params = useSearchParams();
  const router = useRouter();
  const query = useAdminTopic(topicId);
  const update = useUpdateAdminTopic();
  const deletion = useDeleteAdminTopic();
  const { showToast } = useToast();
  const [editing, setEditing] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const lock = useRef(false);
  const returnTo = params.get("returnTo") ? getSafeReturnTo(params.get("returnTo")) : "/topics";
  const detailUrl = "/topics/" + topicId + "?" + new URLSearchParams({ returnTo });

  if (query.isPending) return <LoadingSkeleton rows={6} />;
  if (query.isError || !query.data) return <ErrorState title="주제를 찾을 수 없습니다" description={getTopicErrorMessage(query.error)} onRetry={() => query.refetch()} />;
  const topic = query.data;
  const display = topicPhaseDisplay[topic.phase];
  const editable = topic.phase === "BEFORE_OPEN";
  const save = async (body: Parameters<typeof update.mutateAsync>[0]["body"]) => {
    if (lock.current || update.isPending) return;
    lock.current = true;
    try {
      await update.mutateAsync({ topicId, body });
      showToast("주제를 수정했습니다.", "success");
      setEditing(false);
    } catch (error) {
      showToast(getTopicErrorMessage(error), "error");
      await query.refetch();
    } finally { lock.current = false; }
  };
  const remove = async (reason: string) => {
    if (lock.current || deletion.isPending) return;
    lock.current = true;
    try {
      await deletion.mutateAsync({ topicId, reason });
      showToast("주제를 삭제했습니다.", "success");
      router.push(returnTo);
    } catch (error) {
      showToast(getTopicErrorMessage(error), "error");
      await query.refetch();
    } finally { lock.current = false; }
  };

  return (
    <div className={styles.page}>
      <Link className={styles.back} href={returnTo}>← 목록과 필터로 돌아가기</Link>
      <section className={styles.detailHero}><div><h2>{topic.title}</h2><span>{topic.topicDate}</span></div><StatusBadge tone={display.tone}>{display.label}</StatusBadge></section>
      <details className={styles.extraInfo}><summary>주제 ID</summary><p>{topic.topicId}</p></details>
      {editing ? (
        <TopicForm initial={{ title: topic.title, topicDate: topic.topicDate, startsAt: topic.startsAt, endsAt: topic.endsAt }} onCancel={() => setEditing(false)} onSubmit={save} pending={update.isPending} submitLabel="수정 저장" />
      ) : (
        <div className={styles.detailGrid}>
          <section className={styles.panel}><h3>운영 시간</h3><dl className={styles.meta}><div><dt>참여 시작</dt><dd>{formatInstant(topic.startsAt)}</dd></div><div><dt>참여 종료</dt><dd>{formatInstant(topic.endsAt)}</dd></div><div><dt>등록 시각</dt><dd>{formatInstant(topic.createdAt)}</dd></div><div><dt>최근 수정</dt><dd>{formatInstant(topic.updatedAt)}</dd></div></dl></section>
          <section className={styles.panel}><h3>게시물 현황</h3><PostCountLinks counts={topic.postCounts} scope={{ topicId }} returnTo={detailUrl} /></section>
        </div>
      )}
      {editable && !editing ? <div className={styles.actions}><button className={styles.secondaryButton} onClick={() => setEditing(true)} type="button">주제 수정</button><button className={styles.dangerButton} onClick={() => setConfirmDelete(true)} type="button">주제 삭제</button></div> : null}
      {!editable ? <p className={styles.readOnly}>공개가 시작된 주제는 API 정책에 따라 수정하거나 삭제할 수 없습니다.</p> : null}
      <ConfirmDialog open={confirmDelete} title="이 주제를 삭제할까요?" description="삭제하면 사용자 화면과 관리자 목록에서 숨겨집니다." confirmLabel="삭제" destructive pending={deletion.isPending} reasonField={{ label: "삭제 사유", required: true, maxLength: 500, placeholder: "삭제 사유를 입력해 주세요." }} onCancel={() => setConfirmDelete(false)} onConfirm={remove} />
    </div>
  );
}
