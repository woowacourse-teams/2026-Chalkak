"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useMemo, useState, type FormEvent } from "react";

import type { AdminUserListItem } from "@/shared/api/contracts";
import { formatInstant } from "@/features/posts/model/post-display";
import { EmptyState, ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import { Pagination } from "@/shared/ui/pagination";
import { StatusBadge } from "@/shared/ui/status-badge";
import { Table, type TableColumn } from "@/shared/ui/table";

import { useAdminUsers } from "../api/user-hooks";
import { readAdminUserFilters, withQueryPatch } from "../model/user-filter-query";
import { getUserErrorMessage, userStatusDisplay } from "../model/user-display";
import styles from "@/shared/ui/management.module.css";

function detailHref(userId: string, returnTo: string) {
  return "/users/" + userId + "?" + new URLSearchParams({ returnTo });
}

export function UserListScreen() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const serialized = searchParams.toString();
  const filters = useMemo(
    () => readAdminUserFilters(new URLSearchParams(serialized)),
    [serialized],
  );
  const [email, setEmail] = useState(filters.email ?? "");
  const [status, setStatus] = useState(filters.status ?? "");
  const query = useAdminUsers(filters);
  const returnTo = pathname + (serialized ? "?" + serialized : "");
  const update = (patch: Record<string, string | number | undefined>) =>
    router.push(pathname + withQueryPatch(searchParams, patch));

  const apply = (event: FormEvent) => {
    event.preventDefault();
    update({ email: email.trim() || undefined, status: status || undefined, page: 1 });
  };

  const columns: TableColumn<AdminUserListItem>[] = [
    {
      id: "user",
      header: "사용자",
      render: (user) => (
        <Link className={styles.primaryLink} href={detailHref(user.userId, returnTo)}>
          <strong>{user.email ?? "이메일 정보 없음"}</strong>
          <span>{user.socialProvider ?? "연결 정보 없음"}</span>
        </Link>
      ),
    },
    {
      id: "status",
      header: "상태",
      render: (user) => {
        const display = userStatusDisplay[user.status];
        return <StatusBadge tone={display.tone}>{display.label}</StatusBadge>;
      },
    },
    { id: "app", header: "앱 버전", render: (user) => user.appVersion ?? "—" },
    {
      id: "posts",
      header: "게시물",
      render: (user) => (
        <span className={styles.countsInline}>
          전체 {user.postCounts.total} · 대기 {user.postCounts.pending} · 승인 {user.postCounts.approved}
        </span>
      ),
    },
    { id: "created", header: "가입일", align: "right", render: (user) => formatInstant(user.createdAt) },
  ];

  return (
    <div className={styles.page}>
      <section className={styles.intro}>
        <div><p>USER MANAGEMENT</p><h2>이메일로 찾고, 상태는 안전하게 변경하세요.</h2><span>목록에는 운영에 필요한 최소 정보만 표시합니다.</span></div>
        <strong>{query.data?.users.length ?? 0}</strong>
      </section>
      <form className={styles.filters} onSubmit={apply}>
        <label><span>이메일 검색</span><input aria-label="이메일 검색" onChange={(e) => setEmail(e.target.value)} placeholder="이메일 일부 입력" value={email} /></label>
        <label><span>사용자 상태</span><select aria-label="사용자 상태" onChange={(e) => setStatus(e.target.value)} value={status}><option value="">전체</option><option value="ACTIVE">활성</option><option value="BANNED">차단</option><option value="WITHDRAWN">탈퇴</option></select></label>
        <label><span>정렬</span><select aria-label="정렬" onChange={(e) => update({ sort: e.target.value, page: 1 })} value={filters.sort}><option value="createdAtDesc">최근 가입순</option><option value="createdAtAsc">오래된 가입순</option></select></label>
        <button type="submit">검색 적용</button>
      </form>
      {query.isPending ? <LoadingSkeleton rows={5} /> : null}
      {query.isError ? <ErrorState description={getUserErrorMessage(query.error)} onRetry={() => query.refetch()} /> : null}
      {query.data?.users.length === 0 ? <EmptyState title="조건에 맞는 사용자가 없습니다" description="검색어나 상태를 변경해 주세요." /> : null}
      {query.data?.users.length ? <><div className={styles.desktop}><Table caption="관리자 사용자 목록" columns={columns} getRowKey={(user) => user.userId} rows={query.data.users} /></div><div className={styles.mobileCards}>{query.data.users.map((user) => { const display = userStatusDisplay[user.status]; return <Link className={styles.mobileCard} href={detailHref(user.userId, returnTo)} key={user.userId}><div><strong>{user.email ?? "이메일 정보 없음"}</strong><StatusBadge tone={display.tone}>{display.label}</StatusBadge></div><span>{user.socialProvider ?? "소셜 정보 없음"} · 앱 {user.appVersion ?? "—"}</span><small>게시물 {user.postCounts.total} · 검수 대기 {user.postCounts.pending}</small></Link>; })}</div><Pagination currentPage={query.data.currentPage} hasNext={query.data.hasNext} onPageChange={(page) => update({ page })} /></> : null}
    </div>
  );
}
