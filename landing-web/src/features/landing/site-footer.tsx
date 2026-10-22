import { PRIVACY_URL, TERMS_URL } from "@/shared/store-links";
import { Logo } from "@/shared/ui/logo";

import styles from "./landing.module.css";

export function SiteFooter() {
  return (
    <footer className={styles.footer}>
      <div className={`${styles.container} ${styles.footerInner}`}>
        <div>
          <Logo />
          <p className={styles.footerTagline}>매일 하나의 주제, 각자의 한 장</p>
        </div>
        <nav aria-label="약관" className={styles.footerLinks}>
          <a href={TERMS_URL} target="_blank" rel="noopener noreferrer">
            이용약관
          </a>
          <a href={PRIVACY_URL} target="_blank" rel="noopener noreferrer">
            개인정보 처리방침
          </a>
        </nav>
        <p className={styles.copyright}>© 2026 Chalkak</p>
      </div>
    </footer>
  );
}
