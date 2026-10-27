"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import common from "@/shared/ui/management.module.css";

export function AiTabs() {
  const pathname = usePathname();
  const reviews = pathname.startsWith("/ai-reviews");
  return <nav aria-label="AI 관리" className={common.statusTabs}>
    <Link aria-current={reviews ? undefined : "page"} href="/ai-prompts">프롬프트</Link>
    <Link aria-current={reviews ? "page" : undefined} href="/ai-reviews">리뷰</Link>
  </nav>;
}
