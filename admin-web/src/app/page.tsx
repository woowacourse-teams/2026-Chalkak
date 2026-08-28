import Link from "next/link";

import { adminNavigation } from "@/shared/navigation/admin-navigation";
import { StatusBadge } from "@/shared/ui/status-badge";

import styles from "./page.module.css";

export default function Home() {
  return (
    <div className={styles.dashboard}>
      <section className={styles.hero}>
        <div>
          <p className={styles.eyebrow}>DEVELOPMENT WORKSPACE</p>
          <h2>운영 흐름을 한눈에 확인하세요.</h2>
          <p>
            현재는 개발 관리자 모드입니다. 인증 정보나 관리자 ID를 만들지
            않고 API와 화면을 검증할 수 있습니다.
          </p>
        </div>
        <StatusBadge tone="warning">개발 환경</StatusBadge>
      </section>

      <section aria-labelledby="menu-overview-title">
        <div className={styles.sectionTitle}>
          <div>
            <p>ADMIN MENU</p>
            <h2 id="menu-overview-title">관리 메뉴</h2>
          </div>
          <span>{adminNavigation.length}개 메뉴</span>
        </div>
        <div className={styles.menuGrid}>
          {adminNavigation.slice(1).map((item, index) => (
            <Link href={item.href} key={item.href}>
              <span>{String(index + 1).padStart(2, "0")}</span>
              <strong>{item.label}</strong>
              <p>{item.description}</p>
              <i aria-hidden="true">→</i>
            </Link>
          ))}
        </div>
      </section>
    </div>
  );
}
