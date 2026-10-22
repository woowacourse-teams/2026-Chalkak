import { Logo } from "@/shared/ui/logo";

import styles from "./landing-v2.module.css";

const links = [
  { href: "#how", label: "사용법" },
  { href: "#experience", label: "체험하기" },
  { href: "#faq", label: "자주 묻는 질문" },
];

export function V2Header() {
  return (
    <header className={styles.header}>
      <div className={`${styles.container} ${styles.headerBar}`}>
        <a href="#top" className={styles.homeLink} aria-label="찰캌 처음으로">
          <Logo />
        </a>
        <nav aria-label="주 메뉴" className={styles.nav}>
          {links.map((link) => (
            <a key={link.href} href={link.href}>
              {link.label}
            </a>
          ))}
        </nav>
        <a href="#start" className={styles.headerCta}>
          앱 받기
        </a>
      </div>
    </header>
  );
}
