"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useRef, useState } from "react";

import { formatInstant } from "@/features/posts/model/post-display";
import { PostCountLinks } from "@/features/posts/ui/post-count-links";
import { getSafeReturnTo } from "@/features/auth/model/return-to";
import { ConfirmDialog } from "@/shared/ui/confirm-dialog";
import { ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import { StatusBadge } from "@/shared/ui/status-badge";
import { useToast } from "@/shared/ui/toast";

import { useAdminUser, useUpdateAdminUserStatus } from "../api/user-hooks";
import { getUserErrorMessage, userStatusDisplay } from "../model/user-display";
import styles from "@/shared/ui/management.module.css";

export function UserDetailScreen({ userId }: { userId: string }) {
  const params = useSearchParams();
  const query = useAdminUser(userId);
  const mutation = useUpdateAdminUserStatus();
  const { showToast } = useToast();
  const [target, setTarget] = useState<"ACTIVE" | "BANNED" | null>(null);
  const lock = useRef(false);
  const returnTo = params.get("returnTo") ? getSafeReturnTo(params.get("returnTo")) : "/users";
  const detailUrl = "/users/" + userId + "?" + new URLSearchParams({ returnTo });
  if (query.isPending) return <LoadingSkeleton rows={6} />;
  if (query.isError || !query.data) return <ErrorState title="사용자를 찾을 수 없습니다" description={getUserErrorMessage(query.error)} onRetry={() => query.refetch()} />;
  const user = query.data;
  const display = userStatusDisplay[user.status];
  const confirm = async (reason: string) => {
    if (!target || mutation.isPending || lock.current) return;
    lock.current = true;
    try {
      await mutation.mutateAsync({ userId, status: target, reason });
      showToast(target === "BANNED" ? "사용자를 차단했습니다." : "차단을 해제했습니다.", "success");
      setTarget(null);
    } catch (error) {
      showToast(getUserErrorMessage(error), "error");
      await query.refetch();
    } finally { lock.current = false; }
  };
  return <div className={styles.page}>
    <Link className={styles.back} href={returnTo}>← 목록과 필터로 돌아가기</Link>
    <section className={styles.detailHero}><h2>{user.email ?? "이메일 정보 없음"}</h2><StatusBadge tone={display.tone}>{display.label}</StatusBadge></section>
    <details className={styles.extraInfo}><summary>사용자 ID</summary><p>{user.userId}</p></details>
    <div className={styles.detailGrid}>
      <section className={styles.panel}><h3>계정 정보</h3><dl className={styles.meta}><div><dt>가입일</dt><dd>{formatInstant(user.createdAt)}</dd></div><div><dt>최근 갱신</dt><dd>{formatInstant(user.updatedAt)}</dd></div><div><dt>앱 버전</dt><dd>{user.appVersion ?? "—"}</dd></div><div><dt>소셜 제공자</dt><dd>{user.socialProvider ?? "—"}</dd></div></dl></section>
      <section className={styles.panel}><h3>게시물 현황</h3><PostCountLinks counts={user.postCounts} scope={{ userId }} returnTo={detailUrl} /></section>
    </div>
    {user.status === "WITHDRAWN" ? <p className={styles.readOnly}>탈퇴한 사용자는 기록 확인만 가능하며 상태를 변경할 수 없습니다.</p> : <div className={styles.actions}><button className={user.status === "ACTIVE" ? styles.dangerButton : styles.primaryButton} disabled={mutation.isPending} onClick={() => setTarget(user.status === "ACTIVE" ? "BANNED" : "ACTIVE")} type="button">{user.status === "ACTIVE" ? "사용자 차단" : "차단 해제"}</button></div>}
    <ConfirmDialog open={target !== null} title={target === "BANNED" ? "이 사용자를 차단할까요?" : "차단을 해제할까요?"} description="사유는 관리자 감사 로그에 기록됩니다." confirmLabel={target === "BANNED" ? "차단" : "해제"} destructive={target === "BANNED"} pending={mutation.isPending} reasonField={{label: target === "BANNED" ? "차단 사유" : "해제 사유",required:true,maxLength:500,placeholder:"운영 사유를 입력해 주세요."}} onCancel={() => setTarget(null)} onConfirm={confirm} />
  </div>;
}
