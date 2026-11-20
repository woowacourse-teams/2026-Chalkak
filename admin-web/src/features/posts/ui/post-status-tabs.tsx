import Link from "next/link";

import type { ModerationStatus } from "@/shared/api/contracts";

import { withQueryPatch } from "../model/post-filter-query";
import styles from "./posts.module.css";

const statuses = [
  ["PENDING", "검수 대기"],
  ["APPROVED", "승인"],
  ["REJECTED", "거절"],
] as const;

export function PostStatusTabs({
  active,
  searchParams = new URLSearchParams(),
}: {
  active: ModerationStatus | "JEV";
  searchParams?: Pick<URLSearchParams, "toString">;
}) {
  return (
    <nav aria-label="검수 상태" className={styles.statusTabs}>
      {statuses.map(([status, label]) => (
        <Link
          aria-current={active === status ? "page" : undefined}
          href={"/posts" + withQueryPatch(searchParams, { status, page: 1 })}
          key={status}
        >
          {label}
        </Link>
      ))}
      <Link aria-current={active === "JEV" ? "page" : undefined} href="/posts/jev">Jev</Link>
      <Link className={styles.auditHistoryLink} href="/audit-logs">전체 처리 이력 ↗</Link>
    </nav>
  );
}
