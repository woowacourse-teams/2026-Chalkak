import Link from "next/link";

import type { AdminPostCounts } from "@/shared/api/contracts";
import styles from "@/shared/ui/management.module.css";

const statuses = [
  { key: "pending", value: "PENDING", label: "검수 대기" },
  { key: "approved", value: "APPROVED", label: "승인" },
  { key: "rejected", value: "REJECTED", label: "거절" },
] as const;

interface PostCountLinksProps {
  counts: AdminPostCounts;
  scope: { userId: string } | { topicId: string };
  returnTo: string;
  compact?: boolean;
}

export function PostCountLinks({ counts, scope, returnTo, compact = false }: PostCountLinksProps) {
  return (
    <div className={compact ? styles.countLinks : styles.statGrid}>
      {statuses.map(({ key, value, label }) => (
        <Link
          aria-label={`${label} 게시물 ${counts[key]}개 보기`}
          href={"/posts?" + new URLSearchParams({ status: value, ...scope, returnTo })}
          key={key}
        >
          <span>{label}</span>
          <strong>{counts[key].toLocaleString("ko-KR")}</strong>
        </Link>
      ))}
    </div>
  );
}
