"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState, type PropsWithChildren } from "react";

import { useAdminSession } from "@/features/auth/admin-session-provider";
import { adminNavigation, getCurrentNavigation } from "@/shared/navigation/admin-navigation";
import { AdminIcon } from "@/shared/ui/admin-icon";

import styles from "./admin-shell.module.css";

export function AdminShell({ children }: PropsWithChildren) {
  const pathname = usePathname();
  const router = useRouter();
  const { admin, isMock, logout } = useAdminSession();
  const current = getCurrentNavigation(pathname);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const username = admin?.username ?? "관리자";

  async function handleLogout() {
    if (isLoggingOut) return;
    setIsLoggingOut(true);
    try {
      await logout();
      router.replace("/login");
    } catch {
      // The provider clears local state even if the server request fails.
      router.replace("/login?logout=failed");
      setIsLoggingOut(false);
    }
  }

  return (
    <div className={styles.shell}>
      <a className={styles.skipLink} href="#admin-main">본문으로 바로가기</a>
      <header className={styles.header}>
        <Link aria-label="Chalkak 관리자 홈" className={styles.brand} href="/">
          Chalkak <span>관리자</span>
        </Link>
        <nav aria-label="관리자 메뉴" className={styles.navigation}>
          {adminNavigation.map((item) => {
            const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
            return (
              <Link aria-current={active ? "page" : undefined} className={active ? styles.activeLink : styles.navLink} href={item.href} key={item.href}>
                <AdminIcon name={item.icon} />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>
        <div className={styles.account}>
          <span className={styles.username} title={username}>{username}</span>
          <button className={styles.logoutButton} disabled={isLoggingOut} onClick={handleLogout} type="button">
            {isLoggingOut ? "로그아웃 중" : "로그아웃"}
          </button>
        </div>
      </header>
      {isMock ? <p className={styles.demoNotice}>데모 환경 · 실제 서비스에 영향을 주지 않습니다.</p> : null}
      <main className={styles.mainContent} id="admin-main" tabIndex={-1}>
        <h1 className={styles.screenReaderOnly}>{current.label} 관리</h1>
        {children}
      </main>
    </div>
  );
}
