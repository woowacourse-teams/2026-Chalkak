"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  useEffect,
  useRef,
  useState,
  type PropsWithChildren,
} from "react";

import {
  adminNavigation,
  getCurrentNavigation,
} from "@/shared/navigation/admin-navigation";

import styles from "./admin-shell.module.css";

function isActivePath(pathname: string, href: string) {
  return href === "/" ? pathname === "/" : pathname.startsWith(href);
}

export function AdminShell({ children }: PropsWithChildren) {
  const pathname = usePathname();
  const current = getCurrentNavigation(pathname);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const firstDrawerLinkRef = useRef<HTMLAnchorElement>(null);
  const appEnvironment = process.env.NEXT_PUBLIC_APP_ENV || "local";

  useEffect(() => {
    if (!isDrawerOpen) {
      return;
    }

    firstDrawerLinkRef.current?.focus();
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setIsDrawerOpen(false);
        menuButtonRef.current?.focus();
      }
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [isDrawerOpen]);

  const navigation = (isDrawer: boolean) => (
    <nav aria-label="관리자 메뉴" className={styles.navigation}>
      {adminNavigation.map((item, index) => {
        const active = isActivePath(pathname, item.href);
        return (
          <Link
            aria-current={active ? "page" : undefined}
            className={active ? styles.activeLink : styles.navLink}
            href={item.href}
            key={item.href}
            onClick={() => setIsDrawerOpen(false)}
            ref={isDrawer && index === 0 ? firstDrawerLinkRef : undefined}
          >
            <span aria-hidden="true" className={styles.navMark}>
              {item.shortLabel}
            </span>
            <span>
              <strong>{item.label}</strong>
              <small>{item.description}</small>
            </span>
          </Link>
        );
      })}
    </nav>
  );

  return (
    <div className={styles.shell}>
      <aside className={styles.sidebar}>
        <Link className={styles.brand} href="/">
          <span className={styles.brandMark}>C</span>
          <span>
            <strong>Chalkak</strong>
            <small>ADMIN CONSOLE</small>
          </span>
        </Link>
        {navigation(false)}
        <div className={styles.developerCard}>
          <span className={styles.onlineDot} />
          <span>
            <strong>개발 관리자</strong>
            <small>인증 연결 전 개발 모드</small>
          </span>
        </div>
      </aside>

      <div className={styles.content}>
        <header className={styles.header}>
          <button
            aria-controls="mobile-admin-navigation"
            aria-expanded={isDrawerOpen}
            aria-label="관리자 메뉴 열기"
            className={styles.menuButton}
            onClick={() => setIsDrawerOpen(true)}
            ref={menuButtonRef}
            type="button"
          >
            <span />
            <span />
            <span />
          </button>
          <div>
            <p className={styles.breadcrumb}>관리자 / {current.label}</p>
            <h1>{current.label}</h1>
          </div>
          <div className={styles.environment}>
            <span>DEV</span>
            {appEnvironment}
          </div>
        </header>
        <div className={styles.mainContent}>{children}</div>
      </div>

      {isDrawerOpen ? (
        <div className={styles.drawerLayer}>
          <button
            aria-label="관리자 메뉴 닫기"
            className={styles.backdrop}
            onClick={() => setIsDrawerOpen(false)}
            type="button"
          />
          <aside
            aria-label="모바일 관리자 메뉴"
            className={styles.drawer}
            id="mobile-admin-navigation"
          >
            <div className={styles.drawerHeader}>
              <span className={styles.brandMark}>C</span>
              <strong>Chalkak Admin</strong>
              <button
                aria-label="관리자 메뉴 닫기"
                onClick={() => {
                  setIsDrawerOpen(false);
                  menuButtonRef.current?.focus();
                }}
                type="button"
              >
                닫기
              </button>
            </div>
            {navigation(true)}
            <p className={styles.drawerNotice}>
              개발 관리자 · 인증 연결 전 개발 환경
            </p>
          </aside>
        </div>
      ) : null}
    </div>
  );
}
