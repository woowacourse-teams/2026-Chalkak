"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import common from "@/shared/ui/management.module.css";
import styles from "./notices.module.css";

export function NotificationTabs() {
  const pathname = usePathname();
  return <nav aria-label="알림 관리" className={`${common.statusTabs} ${styles.tabs}`}>
    <Link aria-current={pathname.startsWith("/pushes") ? "page" : undefined} href="/pushes">주제 알림 설정</Link>
    <Link aria-current={pathname.startsWith("/notices") ? "page" : undefined} href="/notices">공지 관리</Link>
  </nav>;
}
